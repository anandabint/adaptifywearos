package com.adaptify.mobile

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MusicPickerActivity : AppCompatActivity() {

    private val genreContainers = mutableMapOf<String, LinearLayout>()
    private var currentPickingGenre: String = ""

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null || currentPickingGenre.isEmpty()) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val title = resolveTrackTitle(uri)
        val track = MusicTrack(uri.toString(), title, currentPickingGenre)
        MusicRepository.addTrack(this, track)
        refreshGenreSection(currentPickingGenre)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildRootView())
        title = "Manage Music"
    }

    private fun buildRootView(): View {
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#121212"))
        }
        val root = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }

        val pageTitle = TextView(this).apply {
            text = "Manage Music"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(pageTitle)

        GENRE_SECTIONS.forEach { (mode, genre) ->
            root.addView(buildGenreSection(mode, genre))
        }

        scrollView.addView(root)
        return scrollView
    }

    private fun buildGenreSection(mode: String, genre: String): View {
        val section = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(20)
            }
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
        }

        val header = TextView(this).apply {
            text = "$mode — $genre"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 0, 0, dp(8))
        }
        section.addView(header)

        val tracksContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
        }
        genreContainers[genre] = tracksContainer
        section.addView(tracksContainer)
        populateTracks(genre)

        val addBtn = Button(this).apply {
            text = "Add Music"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
            setOnClickListener {
                currentPickingGenre = genre
                pickAudioLauncher.launch(arrayOf("audio/*"))
            }
        }
        section.addView(addBtn)

        return section
    }

    private fun refreshGenreSection(genre: String) {
        populateTracks(genre)
    }

    private fun populateTracks(genre: String) {
        val container = genreContainers[genre] ?: return
        container.removeAllViews()
        val tracks = MusicRepository.getTracksForGenre(this, genre)
        if (tracks.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No tracks added"
                setTextColor(Color.parseColor("#888888"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(4), 0, dp(4))
            }
            container.addView(empty)
            return
        }
        tracks.forEach { track ->
            container.addView(buildTrackRow(track))
        }
    }

    private fun buildTrackRow(track: MusicTrack): View {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = track.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        row.addView(title)

        val deleteBtn = Button(this).apply {
            text = "Delete"
            setOnClickListener {
                MusicRepository.removeTrack(this@MusicPickerActivity, track.uri)
                refreshGenreSection(track.genre)
            }
        }
        row.addView(deleteBtn)

        return row
    }

    private fun resolveTrackTitle(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.substringBeforeLast(".")
                    else null
                } ?: "Unknown Track"
        } catch (_: Exception) {
            "Unknown Track"
        }
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }

    companion object {
        private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

        // 3 sections — one per classifier mode
        // Genre names must match ActivityClassifier.GENRE_*
        val GENRE_SECTIONS = listOf(
            "EXERCISE mode" to ActivityClassifier.GENRE_EXERCISE,
            "STRESS mode"   to ActivityClassifier.GENRE_STRESS,
            "RELAX mode"    to ActivityClassifier.GENRE_RELAX,
        )
    }
}
