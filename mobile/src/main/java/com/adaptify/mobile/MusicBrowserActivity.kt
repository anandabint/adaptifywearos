package com.adaptify.mobile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

class MusicBrowserActivity : AppCompatActivity() {

    private var currentFilter = "ALL"
    private var searchQuery = ""
    private var musicService: AdaptiveMusicService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as AdaptiveMusicService.MusicBinder).getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse_music)

        val rootView = (findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        setupUI()
        bindMusicService()
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }

    private fun bindMusicService() {
        val intent = Intent(this, AdaptiveMusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupUI() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val chips = listOf(
            "ALL" to R.id.chipAll,
            "EXERCISE" to R.id.chipExercise,
            "STRESS" to R.id.chipStress,
            "RELAX" to R.id.chipRelax,
        )
        chips.forEach { (filter, viewId) ->
            findViewById<MaterialButton>(viewId).setOnClickListener {
                setActiveChip(filter)
            }
        }

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                renderTrackList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        renderTrackList()
    }

    private fun setActiveChip(filter: String) {
        currentFilter = filter
        val chipIds = listOf(R.id.chipAll, R.id.chipExercise, R.id.chipStress, R.id.chipRelax)
        chipIds.forEach { id ->
            val chip = findViewById<MaterialButton>(id)
            chip.setTextColor(getColor(R.color.text_gray))
            chip.strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.text_gray))
        }
        val activeId = when (filter) {
            "ALL"      -> R.id.chipAll
            "EXERCISE" -> R.id.chipExercise
            "STRESS"   -> R.id.chipStress
            else       -> R.id.chipRelax
        }
        val active = findViewById<MaterialButton>(activeId)
        active.setTextColor(getColor(R.color.text_dark))
        active.strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.text_dark))
        renderTrackList()
    }

    private fun renderTrackList() {
        val allTracks = when (currentFilter) {
            "ALL"      -> MusicRepository.getAllTracks(this)
            "EXERCISE" -> MusicRepository.getTracksForGenre(this, ActivityClassifier.GENRE_EXERCISE)
            "STRESS"   -> MusicRepository.getTracksForGenre(this, ActivityClassifier.GENRE_STRESS)
            else       -> MusicRepository.getTracksForGenre(this, ActivityClassifier.GENRE_RELAX)
        }

        val filtered = if (searchQuery.isBlank()) allTracks
        else allTracks.filter { it.title.contains(searchQuery, ignoreCase = true) }

        val container = findViewById<LinearLayout>(R.id.musicListContainer)
        container.removeAllViews()

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (searchQuery.isBlank()) "Belum ada musik.\nTambah dulu via KELOLA MUSIK."
                       else "Tidak ada hasil untuk \"$searchQuery\""
                textSize = 13f
                setTextColor(getColor(R.color.text_gray))
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(32), 0, dpToPx(32))
            }
            container.addView(empty)
            return
        }

        for (track in filtered) {
            container.addView(buildTrackItem(track))
        }
    }

    private fun buildTrackItem(track: MusicTrack): android.view.View {
        val (badgeText, badgeColor) = when (track.genre) {
            ActivityClassifier.GENRE_EXERCISE -> "EXERCISE" to getColor(R.color.badge_exercise)
            ActivityClassifier.GENRE_STRESS   -> "STRESS"   to getColor(R.color.badge_stress)
            else                              -> "RELAX"    to getColor(R.color.badge_relax)
        }

        val card = CardView(this).apply {
            radius = dpToPx(16).toFloat()
            cardElevation = dpToPx(1).toFloat()
            setCardBackgroundColor(getColor(R.color.surface_white))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(12) }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
        }

        val iconCard = CardView(this).apply {
            radius = dpToPx(12).toFloat()
            setCardBackgroundColor(0xFFF5F5F5.toInt())
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply {
                marginEnd = dpToPx(12)
            }
        }
        val icon = TextView(this).apply {
            text = "🎵"
            textSize = 24f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        iconCard.addView(icon)

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvTitle = TextView(this).apply {
            text = track.title
            textSize = 14f
            setTextColor(getColor(R.color.text_dark))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
        }
        val badgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }
        }
        val badge = TextView(this).apply {
            text = badgeText
            textSize = 9f
            setTextColor(getColor(R.color.surface_white))
            setBackgroundColor(badgeColor)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dpToPx(6) }
        }
        val tvGenre = TextView(this).apply {
            text = track.genre
            textSize = 11f
            setTextColor(getColor(R.color.text_gray))
        }
        badgeRow.addView(badge)
        badgeRow.addView(tvGenre)
        infoLayout.addView(tvTitle)
        infoLayout.addView(badgeRow)

        val btnSelect = MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "SELECT"
            textSize = 10f
            setTextColor(getColor(R.color.text_dark))
            strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.text_dark))
            cornerRadius = dpToPx(12)
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            setOnClickListener {
                val svc = musicService
                if (isBound && svc != null) {
                    svc.playTrackDirectly(track)
                    Toast.makeText(this@MusicBrowserActivity, "▶ ${track.title}", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@MusicBrowserActivity,
                        "Service belum siap, coba lagi",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        row.addView(iconCard)
        row.addView(infoLayout)
        row.addView(btnSelect)
        card.addView(row)
        return card
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
