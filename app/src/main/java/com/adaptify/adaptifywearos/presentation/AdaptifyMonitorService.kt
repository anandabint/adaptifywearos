package com.adaptify.adaptifywearos.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.adaptify.adaptifywearos.classifier.ActivityClassifier
import com.adaptify.adaptifywearos.database.AdaptifyDatabase
import com.adaptify.adaptifywearos.database.SensorLog
import com.adaptify.adaptifywearos.health.HeartRateManager
import com.adaptify.adaptifywearos.sensor.SensorHandler
import com.adaptify.adaptifywearos.stress.StressCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AdaptifyMonitorService : Service() {

    companion object {
        private const val TAG = "AdaptifyMonitorService"
        private const val CHANNEL_ID = "adaptify_monitor"
        private const val NOTIFICATION_ID = 4012
        private const val WAKE_LOCK_TAG = "Adaptify::MonitorWakeLock"

        const val ACTION_START = "com.adaptify.adaptifywearos.action.START_MONITORING"
        const val ACTION_STOP = "com.adaptify.adaptifywearos.action.STOP_MONITORING"

        fun start(context: Context) {
            val intent = Intent(context, AdaptifyMonitorService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdaptifyMonitorService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val sensorHandler by lazy { SensorHandler(applicationContext) }
    private val heartRateManager by lazy { HeartRateManager(applicationContext) }
    private val realtimeSender by lazy { AdaptifyRealtimeSender(applicationContext) }
    private val database by lazy { AdaptifyDatabase.getInstance(applicationContext) }
    private val stressCalculator = StressCalculator()
    private val activityClassifier = ActivityClassifier()

    private var wakeLock: PowerManager.WakeLock? = null
    private var scope: CoroutineScope? = null
    private var pipelineJob: Job? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received")
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (running) {
            Log.d(TAG, "startMonitoring ignored — already running")
            return
        }
        running = true
        AdaptifyMonitorRepository.markRunning(true)

        startForegroundCompat(buildNotification())
        acquireWakeLock()

        val canReadSteps = checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

        val supervisor = SupervisorJob()
        val coroutineScope = CoroutineScope(Dispatchers.Default + supervisor)
        scope = coroutineScope

        sensorHandler.start(scope = coroutineScope, canReadSteps = canReadSteps)
        coroutineScope.launch { heartRateManager.start() }

        pipelineJob = coroutineScope.launch {
            combine(
                sensorHandler.sensorState,
                heartRateManager.heartRateState,
            ) { sensorSnapshot, heartRateState -> sensorSnapshot to heartRateState }
                .collect { (sensorSnapshot, heartRateState) ->
                    try {
                        val stressReading = stressCalculator.calculate(
                            currentHeartRate = heartRateState.bpm,
                            heartRateTimestamp = heartRateState.lastUpdatedEpochMillis,
                            accelerometerDelta = sensorSnapshot.accelerometerDelta,
                        )
                        val activityReading = activityClassifier.classify(
                            sensorSnapshot = sensorSnapshot,
                            heartRateState = heartRateState,
                            stressReading = stressReading,
                        )

                        AdaptifyMonitorRepository.publish(
                            sensor = sensorSnapshot,
                            heartRate = heartRateState,
                            stress = stressReading,
                            activity = activityReading,
                        )

                        val payload = AdaptifyRealtimePayload(
                            heartRate = heartRateState.bpm ?: 0,
                            steps = sensorSnapshot.steps ?: 0,
                            stressIndex = stressReading.index,
                            activityMode = activityReading.mode.name,
                            activityConfidence = activityReading.confidence,
                            rmssd = stressReading.rmssd,
                            monitoring = true,
                        )
                        realtimeSender.send(payload)

                        val accelMag = sensorSnapshot.accelerometer
                            ?.let { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) } ?: 0f
                        val gyroMag = sensorSnapshot.gyroscope
                            ?.let { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) } ?: 0f

                        launch(Dispatchers.IO) {
                            try {
                                database.sensorLogDao().insert(
                                    SensorLog(
                                        timestamp = System.currentTimeMillis(),
                                        heartRate = heartRateState.bpm ?: 0,
                                        steps = sensorSnapshot.steps ?: 0,
                                        stressIndex = stressReading.index,
                                        rmssd = null,
                                        accelerometerMagnitude = accelMag,
                                        gyroscopeMagnitude = gyroMag,
                                        activityMode = activityReading.mode.name,
                                        activityConfidence = activityReading.confidence,
                                    )
                                )
                            } catch (t: Throwable) {
                                Log.e(TAG, "DB insert failed", t)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Pipeline iteration failed", t)
                    }
                }
        }
        Log.d(TAG, "monitoring started")
    }

    private fun stopMonitoring() {
        if (!running) return
        running = false
        AdaptifyMonitorRepository.markRunning(false)

        sendDisconnectPayload()

        pipelineJob?.cancel()
        pipelineJob = null

        sensorHandler.stop()
        // heartRateManager.stop() is a suspend fun — fire-and-forget on a short-lived scope
        CoroutineScope(Dispatchers.Default).launch {
            try { heartRateManager.stop() } catch (t: Throwable) { Log.e(TAG, "HR stop failed", t) }
        }

        scope?.cancel()
        scope = null
        releaseWakeLock()

        AdaptifyMonitorRepository.reset()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.d(TAG, "monitoring stopped")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopMonitoring()
        super.onDestroy()
    }

    private fun sendDisconnectPayload() {
        try {
            val lastSensor = AdaptifyMonitorRepository.sensorSnapshot.value
            val lastHr = AdaptifyMonitorRepository.heartRateState.value
            val lastStress = AdaptifyMonitorRepository.stressReading.value
            val lastActivity = AdaptifyMonitorRepository.activityReading.value
            realtimeSender.send(
                AdaptifyRealtimePayload(
                    heartRate = lastHr.bpm ?: 0,
                    steps = lastSensor.steps ?: 0,
                    stressIndex = lastStress.index,
                    activityMode = lastActivity?.mode?.name ?: "RELAX",
                    activityConfidence = lastActivity?.confidence ?: 0f,
                    rmssd = lastStress.rmssd,
                    monitoring = false,
                )
            )
            Log.d(TAG, "Sent monitoring=false disconnect payload")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to send disconnect payload", t)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
        Log.d(TAG, "WakeLock acquired (held=${wakeLock?.isHeld})")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Adaptify Monitor",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Background sensor monitoring"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, AdaptifyMonitorService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Adaptify monitoring")
            .setContentText("Heart rate + motion tracking active")
            .setContentIntent(contentPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopPi)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
