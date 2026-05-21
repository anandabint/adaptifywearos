package com.adaptify.adaptifywearos.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Vector3Sample(
    val x: Float,
    val y: Float,
    val z: Float,
)

data class SensorSnapshot(
    val accelerometer: Vector3Sample? = null,
    val gyroscope: Vector3Sample? = null,
    val steps: Int? = null,
    val accelerometerDelta: Float = 0f,
    val accelerometerAvailable: Boolean = false,
    val gyroscopeAvailable: Boolean = false,
    val stepCounterAvailable: Boolean = false,
    val stepPermissionGranted: Boolean = false,
)

class SensorHandler(context: Context) : SensorEventListener {

    companion object {
        private const val SENSOR_SAMPLING_PERIOD_US = 250_000
        private const val SENSOR_BATCH_LATENCY_US = 1_000_000
        private const val UI_EMIT_INTERVAL_MS = 1_000L
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _sensorState = MutableStateFlow(
        SensorSnapshot(
            accelerometerAvailable = accelerometer != null,
            gyroscopeAvailable = gyroscope != null,
            stepCounterAvailable = stepCounter != null,
        )
    )
    val sensorState: StateFlow<SensorSnapshot> = _sensorState.asStateFlow()

    private var latestAccelerometer: Vector3Sample? = null
    private var latestGyroscope: Vector3Sample? = null
    private var latestStepCounterValue: Float? = null
    private var stepBaseline: Float? = null
    private var latestAccelerometerDelta: Float = 0f
    private var emitJob: Job? = null
    private var isRunning = false
    private var stepPermissionGranted = false

    fun start(scope: CoroutineScope, canReadSteps: Boolean) {
        if (sensorManager == null) return
        if (isRunning) return

        stepPermissionGranted = canReadSteps

        registerMotionSensor(accelerometer)
        registerMotionSensor(gyroscope)

        if (canReadSteps) {
            stepCounter?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        isRunning = true
        pushSnapshot()
        emitJob = scope.launch {
            while (isActive) {
                pushSnapshot()
                delay(UI_EMIT_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        if (!isRunning) return

        sensorManager?.unregisterListener(this)
        emitJob?.cancel()
        emitJob = null
        isRunning = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val nextSample = Vector3Sample(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                )
                latestAccelerometerDelta = latestAccelerometer?.distanceTo(nextSample) ?: 0f
                latestAccelerometer = nextSample
            }

            Sensor.TYPE_GYROSCOPE -> {
                latestGyroscope = Vector3Sample(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                )
            }

            Sensor.TYPE_STEP_COUNTER -> {
                val currentValue = event.values.firstOrNull() ?: return
                if (stepBaseline == null) {
                    stepBaseline = currentValue
                }
                latestStepCounterValue = currentValue
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerMotionSensor(sensor: Sensor?) {
        if (sensor == null) return
        sensorManager?.registerListener(
            this,
            sensor,
            SENSOR_SAMPLING_PERIOD_US,
            SENSOR_BATCH_LATENCY_US,
        )
    }

    private fun pushSnapshot() {
        _sensorState.value = _sensorState.value.copy(
            accelerometer = latestAccelerometer,
            gyroscope = latestGyroscope,
            steps = currentSessionSteps(),
            accelerometerDelta = latestAccelerometerDelta,
            stepPermissionGranted = stepPermissionGranted,
        )
    }

    private fun currentSessionSteps(): Int? {
        if (!stepPermissionGranted) return null

        val latestValue = latestStepCounterValue ?: return null
        val baseline = stepBaseline ?: latestValue
        return (latestValue - baseline).coerceAtLeast(0f).roundToInt()
    }

    private fun Vector3Sample.distanceTo(other: Vector3Sample): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt((dx * dx) + (dy * dy) + (dz * dz))
    }
}
