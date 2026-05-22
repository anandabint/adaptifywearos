package com.adaptify.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class AdaptiveMusicService : Service() {

    inner class MusicBinder : Binder() {
        fun getService(): AdaptiveMusicService = this@AdaptiveMusicService
    }

    private val binder = MusicBinder()
    private var currentPlayer: MediaPlayer? = null
    private var fadingOutPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSession
    private val handler = Handler(Looper.getMainLooper())

    var currentGenre: String = ""; private set
    var currentTrack: MusicTrack? = null; private set
    var isPlaying: Boolean = false; private set

    var onTrackChanged: ((MusicTrack?) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> togglePlayPause()
            ACTION_SKIP -> skipTrack()
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "AdaptifyMusic")
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { resumePlayback() }
            override fun onPause() { pausePlayback() }
            override fun onSkipToNext() { skipTrack() }
            override fun onStop() { stopPlayback() }
        })
        mediaSession.isActive = true
    }

    fun playForGenre(genre: String) {
        if (genre.isEmpty()) return
        if (genre == currentGenre && isPlaying) return
        currentGenre = genre
        val tracks = MusicRepository.getTracksForGenre(this, genre)
        if (tracks.isEmpty()) {
            Log.d(TAG, "No tracks for genre: $genre")
            return
        }
        val track = tracks.random()
        if (currentPlayer?.isPlaying == true) crossfadeTo(track) else startTrack(track)
    }

    fun togglePlayPause() {
        if (isPlaying) pausePlayback() else resumePlayback()
    }

    private fun startTrack(track: MusicTrack) {
        releaseCurrentPlayer()
        val player = buildPlayer(track) ?: return
        player.start()
        currentPlayer = player
        currentTrack = track
        isPlaying = true
        onTrackChanged?.invoke(track)
        updateNotification()
        Log.d(TAG, "Playing: ${track.title} [${track.genre}]")
    }

    private fun crossfadeTo(track: MusicTrack) {
        val newPlayer = buildPlayer(track) ?: return
        newPlayer.setVolume(0f, 0f)
        newPlayer.start()

        val oldPlayer = currentPlayer
        fadingOutPlayer = oldPlayer
        currentPlayer = newPlayer
        currentTrack = track
        isPlaying = true
        onTrackChanged?.invoke(track)
        updateNotification()

        var step = 0
        handler.post(object : Runnable {
            override fun run() {
                step++
                val vol = step.toFloat() / CROSSFADE_STEPS
                newPlayer.setVolume(vol, vol)
                oldPlayer?.setVolume(1f - vol, 1f - vol)
                if (step < CROSSFADE_STEPS) {
                    handler.postDelayed(this, CROSSFADE_STEP_MS)
                } else {
                    try { oldPlayer?.stop() } catch (_: Exception) {}
                    oldPlayer?.release()
                    if (fadingOutPlayer === oldPlayer) fadingOutPlayer = null
                }
            }
        })
    }

    private fun buildPlayer(track: MusicTrack): MediaPlayer? {
        return try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(applicationContext, Uri.parse(track.uri))
                prepare()
                setOnCompletionListener { onTrackComplete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build player: ${track.title}", e)
            null
        }
    }

    private fun onTrackComplete() {
        val tracks = MusicRepository.getTracksForGenre(this, currentGenre)
        if (tracks.isNotEmpty()) {
            startTrack(tracks.random())
        } else {
            isPlaying = false
            currentTrack = null
            onTrackChanged?.invoke(null)
            updateNotification()
        }
    }

    fun pausePlayback() {
        try { currentPlayer?.pause() } catch (_: Exception) {}
        isPlaying = false
        onTrackChanged?.invoke(currentTrack)
        updateNotification()
    }

    fun resumePlayback() {
        if (currentTrack == null) return
        try { currentPlayer?.start() } catch (_: Exception) {}
        isPlaying = true
        onTrackChanged?.invoke(currentTrack)
        updateNotification()
    }

    fun skipTrack() {
        val tracks = MusicRepository.getTracksForGenre(this, currentGenre)
        if (tracks.isEmpty()) return
        val next = tracks.filter { it.uri != currentTrack?.uri }.randomOrNull() ?: tracks.random()
        if (currentPlayer?.isPlaying == true) crossfadeTo(next) else startTrack(next)
    }

    fun skipToNext() = skipTrack()

    fun stopPlayback() {
        handler.removeCallbacksAndMessages(null)
        try { fadingOutPlayer?.stop() } catch (_: Exception) {}
        fadingOutPlayer?.release()
        fadingOutPlayer = null
        releaseCurrentPlayer()
        currentTrack = null
        isPlaying = false
        onTrackChanged?.invoke(null)
        updateNotification()
    }

    private fun releaseCurrentPlayer() {
        try { currentPlayer?.stop() } catch (_: Exception) {}
        currentPlayer?.release()
        currentPlayer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Adaptify Music", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Adaptive music playback controls" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AdaptiveMusicService::class.java).setAction(ACTION_TOGGLE_PLAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val skipIntent = PendingIntent.getService(
            this, 2,
            Intent(this, AdaptiveMusicService::class.java).setAction(ACTION_SKIP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val playPauseIcon =
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val trackText = currentTrack?.title ?: "Ready"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Adaptify Music")
            .setContentText(trackText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", toggleIntent)
            .addAction(android.R.drawable.ic_media_next, "Skip", skipIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdaptiveMusic"
        const val CHANNEL_ID = "adaptify_music_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_TOGGLE_PLAY = "com.adaptify.mobile.TOGGLE_PLAY"
        const val ACTION_SKIP = "com.adaptify.mobile.SKIP"
        private const val CROSSFADE_STEPS = 20
        private const val CROSSFADE_STEP_MS = 100L
    }
}
