package com.adaptify.mobile

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.adaptify.mobile.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var musicService: AdaptiveMusicService? = null
    private var isBound = false
    private var lastGenre = ""
    private var isAdaptiveMode: Boolean
        get() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ADAPTIVE_MODE, true)  // default ON
        set(value) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ADAPTIVE_MODE, value).apply()

    private val lastDataTime = AtomicLong(0L)
    private val watchMonitoring = AtomicBoolean(true)
    private val watchBatteryLevel = AtomicLong(-1L)
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusTicker = object : Runnable {
        override fun run() {
            refreshConnectionStatus()
            statusHandler.postDelayed(this, STATUS_TICK_MS)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as AdaptiveMusicService.MusicBinder).getService()
            isBound = true
            musicService?.onTrackChanged = { track ->
                runOnUiThread { updateTrackUI(track) }
            }
            runOnUiThread { updateTrackUI(musicService?.currentTrack) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AdaptifyMobileReceiver.ACTION_DATA_UPDATED) return
            val hr = intent.getIntExtra(AdaptifyMobileReceiver.EXTRA_HEART_RATE, 0)
            val steps = intent.getIntExtra(AdaptifyMobileReceiver.EXTRA_STEPS, 0)
            val mode = intent.getStringExtra(AdaptifyMobileReceiver.EXTRA_ACTIVITY_MODE) ?: ""
            val genre = intent.getStringExtra(AdaptifyMobileReceiver.EXTRA_GENRE) ?: ""
            val battery = intent.getIntExtra(AdaptifyMobileReceiver.EXTRA_BATTERY_LEVEL, -1)
            val monitoring = intent.getBooleanExtra(AdaptifyMobileReceiver.EXTRA_MONITORING, true)

            lastDataTime.set(System.currentTimeMillis())
            watchMonitoring.set(monitoring)
            watchBatteryLevel.set(battery.toLong())

            updateSensorUI(hr, steps, mode, genre)
            updateBatteryUI(battery)
            refreshConnectionStatus()

            // Auto-switch genre hanya saat Adaptive Mode ON
            if (isAdaptiveMode && monitoring && genre.isNotEmpty() && genre != lastGenre) {
                lastGenre = genre
                musicService?.playForGenre(genre)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Apply saved night mode
        val prefs = getSharedPreferences("adaptify_prefs", Context.MODE_PRIVATE)
        val nightMode = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(nightMode)

        // Apply saved language
        val lang = prefs.getString("language", "en") ?: "en"
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        // Edge-to-edge: biarkan gradient mengisi sampai status bar dan nav bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding = ActivityMainBinding.inflate(layoutInflater)
        MusicRepository.migrateGenres(this)
        setContentView(binding.root)

        // Padding agar konten tidak masuk ke area status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        requestRuntimePermissions()
        setupButtons()
        startAndBindMusicService()

        // Subtle fade-in for the content on first paint
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.root.getChildAt(0)?.startAnimation(fadeIn)
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            dataReceiver, IntentFilter(AdaptifyMobileReceiver.ACTION_DATA_UPDATED)
        )
        // Re-hydrate from cached snapshot so the UI doesn't show "--" while waiting for first push.
        AdaptifyRealtimeStore.load(this)?.let { snap ->
            lastDataTime.set(snap.updatedAtEpochMillis)
            watchMonitoring.set(snap.monitoring)
            watchBatteryLevel.set(snap.batteryLevel.toLong())
            updateSensorUI(snap.heartRate, snap.steps, snap.mode, snap.genre)
            updateBatteryUI(snap.batteryLevel)
        }
        refreshConnectionStatus()
        statusHandler.post(statusTicker)
        binding.switchAdaptive.isChecked = isAdaptiveMode
    }

    override fun onStop() {
        statusHandler.removeCallbacks(statusTicker)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun setupButtons() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnBrowseMusic.setOnClickListener {
            startActivity(Intent(this, MusicBrowserActivity::class.java))
        }
        binding.btnManageMusic.setOnClickListener {
            startActivity(Intent(this, MusicPickerActivity::class.java))
        }
        binding.btnPlayPause.setOnClickListener {
            musicService?.togglePlayPause()
            updateTrackUI(musicService?.currentTrack)
        }
        binding.btnSkip.setOnClickListener {
            musicService?.skipTrack()
        }
        binding.switchAdaptive.setOnCheckedChangeListener { _, isChecked ->
            isAdaptiveMode = isChecked  // auto-persist via property setter
        }
    }

    private fun startAndBindMusicService() {
        val intent = Intent(this, AdaptiveMusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updateSensorUI(
        hr: Int, steps: Int, mode: String, genre: String
    ) {
        binding.tvHeartRate.text = hr?.toString() ?: "--"
        binding.tvSteps.text = "Steps (session): $steps"

        // Mode badge with emoji + color per activity mode
        val (modeText, modeColor) = when {
            mode.contains("HIGH_ACTIVITY", ignoreCase = true) ->
                "🏃 ${mode.uppercase()}" to ContextCompat.getColor(this, R.color.mode_exercise)
            mode.contains("LOW_ACTIVITY", ignoreCase = true) ->
                "🚶 ${mode.uppercase()}" to ContextCompat.getColor(this, R.color.mode_low_activity)
            mode.contains("RELAX", ignoreCase = true) ->
                "😌 ${mode.uppercase()}" to ContextCompat.getColor(this, R.color.mode_relax)
            else ->
                mode.ifEmpty { "--" } to ContextCompat.getColor(this, R.color.mode_relax)
        }
        binding.tvActivityMode.text = modeText
        binding.tvActivityMode.setTextColor(modeColor)

        binding.tvModeLabel.text = when {
            mode.contains("HIGH_ACTIVITY", ignoreCase = true) -> "AKTIVITAS TINGGI"
            mode.contains("LOW_ACTIVITY", ignoreCase = true)  -> "AKTIVITAS RINGAN"
            else                                               -> "SANTAI"
        }

        binding.tvBodyStatus.text = when {
            mode.contains("HIGH_ACTIVITY", ignoreCase = true) -> "Tubuh Anda sedang aktif berolahraga."
            mode.contains("LOW_ACTIVITY", ignoreCase = true)  -> "Tubuh Anda sedang beraktivitas ringan."
            else                                               -> "Tubuh Anda dalam keadaan santai & rileks."
        }

        binding.tvGenre.text = "Genre: ${genre.ifEmpty { "--" }}"
    }

    private fun updateBatteryUI(batteryLevel: Int) {
        binding.tvBattery.text = if (batteryLevel in 0..100) {
            "Watch Battery: $batteryLevel%"
        } else {
            "Watch Battery: --%"
        }
    }

    private fun refreshConnectionStatus() {
        val last = lastDataTime.get()
        val now = System.currentTimeMillis()
        val ageMs = now - last

        val (statusText, statusColor) = when {
            last == 0L -> "MENCARI..." to ContextCompat.getColor(this, R.color.status_searching)
            ageMs > DATA_TIMEOUT_MS -> "MENCARI..." to ContextCompat.getColor(this, R.color.status_searching)
            !watchMonitoring.get() -> "TERPUTUS" to ContextCompat.getColor(this, R.color.status_offline)
            else -> "JAM TANGAN TERHUBUNG" to ContextCompat.getColor(this, R.color.status_online)
        }
        binding.tvConnectionStatus.text = statusText
        binding.tvConnectionStatus.setTextColor(statusColor)

        binding.tvLastUpdated.text = if (last == 0L) {
            "Menunggu data jam tangan..."
        } else {
            "Pembaruan terakhir: ${formatRelativeAge(ageMs)} (${
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(last))
            })"
        }
    }

    private fun formatRelativeAge(ageMs: Long): String = when {
        ageMs < 2_000 -> "baru saja"
        ageMs < 60_000 -> "${ageMs / 1_000} detik lalu"
        ageMs < 3_600_000 -> "${ageMs / 60_000} menit lalu"
        else -> "${ageMs / 3_600_000} jam lalu"
    }

    private fun updateTrackUI(track: MusicTrack?) {
        if (track != null) {
            binding.tvCurrentTrack.text = track.title
            binding.btnPlayPause.text =
                if (musicService?.isPlaying == true) "Jeda" else "Putar"
        } else {
            binding.tvCurrentTrack.text = "Tidak ada musik diputar"
            binding.btnPlayPause.text = "Putar"
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 100
        private const val STATUS_TICK_MS = 1_000L
        private const val DATA_TIMEOUT_MS = 10_000L
        private const val PREFS_NAME = "adaptify_prefs"
        private const val KEY_ADAPTIVE_MODE = "adaptive_mode"
    }
}
