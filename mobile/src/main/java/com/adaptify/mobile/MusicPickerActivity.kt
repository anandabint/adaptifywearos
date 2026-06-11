package com.adaptify.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

class MusicPickerActivity : AppCompatActivity() {

    private var currentMode = "HIGH_ACTIVITY"

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some pickers don't grant persistable permission; continue anyway
        }
        val title = getFileNameFromUri(uri)
        val genre = getCurrentGenre()
        val track = MusicTrack(uri = uri.toString(), title = title, genre = genre)
        MusicRepository.addTrack(this, track)
        Toast.makeText(this, "Ditambahkan: $title", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // White activity → dark status bar icons so they're visible on white
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_picker)

        // Apply padding to the activity's root view (not android.R.id.content) so its
        // white background extends behind the status bar; padding only pushes the
        // content (header etc.) below the status/nav bars.
        val rootView = (findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        setupBackButton()
        setupTabs()
        setupAddButton()
        updateUI()
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupTabs() {
        findViewById<MaterialButton>(R.id.tabExercise).setOnClickListener { selectTab("HIGH_ACTIVITY") }
        findViewById<MaterialButton>(R.id.tabLowActivity).setOnClickListener { selectTab("LOW_ACTIVITY") }
        findViewById<MaterialButton>(R.id.tabRelax).setOnClickListener { selectTab("RELAX") }
    }

    private fun setupAddButton() {
        findViewById<MaterialButton>(R.id.btnAddMusic).setOnClickListener {
            filePickerLauncher.launch(arrayOf("audio/*"))
        }
    }

    private fun selectTab(mode: String) {
        currentMode = mode
        val tabExercise = findViewById<MaterialButton>(R.id.tabExercise)
        val tabLowActivity = findViewById<MaterialButton>(R.id.tabLowActivity)
        val tabRelax = findViewById<MaterialButton>(R.id.tabRelax)

        listOf(tabExercise, tabLowActivity, tabRelax).forEach { tab ->
            tab.setTextColor(getColor(R.color.text_gray))
            tab.strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.text_gray))
            tab.backgroundTintList = null
        }

        val activeTab = when (mode) {
            "HIGH_ACTIVITY" -> tabExercise
            "LOW_ACTIVITY" -> tabLowActivity
            else -> tabRelax
        }
        activeTab.setTextColor(getColor(R.color.surface_white))
        activeTab.backgroundTintList =
            android.content.res.ColorStateList.valueOf(getColor(R.color.text_dark))

        updateUI()
    }

    private fun updateUI() {
        val genre = getCurrentGenre()
        val tracks = MusicRepository.getTracksForGenre(this, genre)

        findViewById<TextView>(R.id.tvModeHeader).text = "${modeLabelFor(currentMode)} MODE"
        findViewById<TextView>(R.id.tvFileCount).text = "MODE FILES: ${tracks.size}"

        val container = findViewById<LinearLayout>(R.id.musicListContainer)
        container.removeAllViews()

        if (tracks.isEmpty()) {
            container.addView(buildEmptyState())
        } else {
            for (track in tracks) {
                container.addView(buildTrackItem(track))
            }
        }
    }

    private fun buildEmptyState(): View = TextView(this).apply {
        text = "Belum ada musik untuk mode ini.\nTap '+ TAMBAH LAGU' untuk menambahkan."
        textSize = 13f
        setTextColor(getColor(R.color.text_gray))
        gravity = Gravity.CENTER
        setPadding(0, dpToPx(32), 0, dpToPx(32))
    }

    private fun buildTrackItem(track: MusicTrack): View {
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

        val icon = TextView(this).apply {
            text = "🎵"
            textSize = 24f
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                marginEnd = dpToPx(12)
            }
            gravity = Gravity.CENTER
        }

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvTitle = TextView(this).apply {
            text = track.title
            textSize = 14f
            setTextColor(getColor(R.color.text_dark))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val tvGenre = TextView(this).apply {
            text = track.genre
            textSize = 12f
            setTextColor(getColor(R.color.text_gray))
        }
        infoLayout.addView(tvTitle)
        infoLayout.addView(tvGenre)

        val btnDelete = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            background = null
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
            contentDescription = "Hapus"
            setColorFilter(getColor(R.color.text_gray))
            setOnClickListener {
                MusicRepository.removeTrack(this@MusicPickerActivity, track.uri)
                Toast.makeText(this@MusicPickerActivity, "Dihapus: ${track.title}", Toast.LENGTH_SHORT).show()
                updateUI()
            }
        }

        row.addView(icon)
        row.addView(infoLayout)
        row.addView(btnDelete)
        card.addView(row)
        return card
    }

    private fun getCurrentGenre(): String = when (currentMode) {
        "HIGH_ACTIVITY" -> ActivityClassifier.GENRE_HIGH_ACTIVITY
        "LOW_ACTIVITY"  -> ActivityClassifier.GENRE_LOW_ACTIVITY
        else            -> ActivityClassifier.GENRE_RELAX
    }

    private fun modeLabelFor(mode: String): String = when (mode) {
        "HIGH_ACTIVITY" -> "AKTIVITAS TINGGI"
        "LOW_ACTIVITY"  -> "AKTIVITAS RINGAN"
        else            -> "SANTAI"
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "Unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx)
                    .removeSuffix(".mp3").removeSuffix(".wav")
                    .removeSuffix(".flac").removeSuffix(".m4a").removeSuffix(".aac")
            }
        }
        return name
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
