package com.adaptify.mobile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class MusicBrowserActivity : AppCompatActivity() {

    private val genres = listOf(
        ActivityClassifier.GENRE_EXERCISE,
        ActivityClassifier.GENRE_STRESS,
        ActivityClassifier.GENRE_RELAX,
    )

    private var selectedGenre: String = "All"
    private lateinit var trackListContainer: LinearLayout
    private var musicService: AdaptiveMusicService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as AdaptiveMusicService.MusicBinder).getService()
            isBound = true
            refreshTrackList()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Browse Music"
        setContentView(buildRootView())
        bindService(
            Intent(this, AdaptiveMusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        super.onResume()
        refreshTrackList()
    }

    override fun onDestroy() {
        if (isBound) { unbindService(serviceConnection); isBound = false }
        super.onDestroy()
    }

    private fun buildRootView(): View {
        val root = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // ── Chip filter ──
        val chipGroup = ChipGroup(this).apply {
            isSingleSelection = true
            setPadding(dp(12), dp(12), dp(12), dp(4))
        }

        fun addChip(label: String, genre: String) {
            chipGroup.addView(Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = (genre == selectedGenre || (genre == "All" && selectedGenre == "All"))
                setOnClickListener { selectedGenre = genre; refreshTrackList() }
            })
        }

        addChip("All", "All")
        addChip("Exercise", ActivityClassifier.GENRE_EXERCISE)
        addChip("Stress Relief", ActivityClassifier.GENRE_STRESS)
        addChip("Relax", ActivityClassifier.GENRE_RELAX)

        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipGroup)
        })

        // ── Track list ──
        trackListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }

        root.addView(ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            addView(trackListContainer)
        })

        return root
    }

    private fun refreshTrackList() {
        trackListContainer.removeAllViews()

        val tracks = if (selectedGenre == "All")
            MusicRepository.getAllTracks(this)
        else
            MusicRepository.getTracksForGenre(this, selectedGenre)

        if (tracks.isEmpty()) {
            trackListContainer.addView(TextView(this).apply {
                text = if (selectedGenre == "All")
                    "No tracks yet.\nAdd music via Manage Music."
                else
                    "No tracks for this genre.\nAdd via Manage Music."
                setTextColor(Color.parseColor("#888888"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(32), 0, 0)
            })
            return
        }

        tracks.forEach { track ->
            // Row
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(10), dp(4), dp(10))
            }

            // Track info
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            info.addView(TextView(this).apply {
                text = track.title
                setTextColor(Color.WHITE)
                textSize = 15f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            info.addView(TextView(this).apply {
                text = track.genre
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 12f
            })

            // Play button
            val playBtn = MaterialButton(this).apply {
                text = "▶  Play"
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(40))
                setOnClickListener {
                    musicService?.playTrackDirectly(track)
                    // Visual feedback
                    this.text = "▶  Playing"
                    postDelayed({ this.text = "▶  Play" }, 2000)
                }
            }

            row.addView(info)
            row.addView(playBtn)
            trackListContainer.addView(row)

            // Divider
            trackListContainer.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
                setBackgroundColor(Color.parseColor("#2A2A2A"))
            })
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
