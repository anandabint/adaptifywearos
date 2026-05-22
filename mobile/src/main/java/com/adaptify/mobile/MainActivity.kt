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
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.adaptify.mobile.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var musicService: AdaptiveMusicService? = null
    private var isBound = false
    private var lastGenre = ""

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
            val stressIndex = intent.getIntExtra(AdaptifyMobileReceiver.EXTRA_STRESS_INDEX, 0)
            val rmssd = intent.getDoubleExtra(AdaptifyMobileReceiver.EXTRA_RMSSD, 0.0)
            val mode = intent.getStringExtra(AdaptifyMobileReceiver.EXTRA_ACTIVITY_MODE) ?: ""
            val genre = intent.getStringExtra(AdaptifyMobileReceiver.EXTRA_GENRE) ?: ""
            updateSensorUI(hr, steps, stressIndex, rmssd, mode, genre)
            if (genre.isNotEmpty() && genre != lastGenre) {
                lastGenre = genre
                musicService?.playForGenre(genre)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestRuntimePermissions()
        setupButtons()
        startAndBindMusicService()
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            dataReceiver, IntentFilter(AdaptifyMobileReceiver.ACTION_DATA_UPDATED)
        )
    }

    override fun onStop() {
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
        hr: Int, steps: Int, stressIndex: Int, rmssd: Double, mode: String, genre: String
    ) {
        binding.tvHeartRate.text = "Heart Rate: $hr BPM"
        binding.tvSteps.text = "Steps: $steps"
        binding.tvStressIndex.text = "Stress Index: $stressIndex / 100"
        binding.tvRmssd.text = "RMSSD: ${"%.1f".format(rmssd)} ms"
        binding.tvActivityMode.text = "Mode: ${mode.ifEmpty { "--" }}"
        binding.tvGenre.text = "Genre: ${genre.ifEmpty { "--" }}"
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        binding.tvLastUpdated.text = "Updated: $time"
    }

    private fun updateTrackUI(track: MusicTrack?) {
        if (track != null) {
            binding.tvCurrentTrack.text = track.title
            binding.btnPlayPause.text =
                if (musicService?.isPlaying == true) "Pause" else "Play"
        } else {
            binding.tvCurrentTrack.text = "No track playing"
            binding.btnPlayPause.text = "Play"
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 100
    }
}
