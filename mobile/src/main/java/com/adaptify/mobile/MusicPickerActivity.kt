package com.adaptify.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adaptify.mobile.databinding.ActivityMusicPickerBinding
import com.adaptify.mobile.databinding.ItemGenreSectionBinding
import com.adaptify.mobile.databinding.ItemMusicTrackBinding

class MusicPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMusicPickerBinding
    private lateinit var repository: MusicRepository
    private val adapters = mutableMapOf<String, MusicTrackAdapter>()
    private var currentPickingGenre = ""

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Some providers don't support persistable permissions
                }
                val title = resolveTrackTitle(uri)
                val track = MusicTrack(uri.toString(), title, currentPickingGenre)
                repository.addTrack(track)
                adapters[currentPickingGenre]?.addTrack(track)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = MusicRepository(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Music"

        setupGenreSections()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupGenreSections() {
        GENRE_MAPPING.forEach { (mode, genre) ->
            val section = ItemGenreSectionBinding.inflate(layoutInflater, binding.llSections, false)
            section.tvGenreTitle.text = "$mode — $genre"

            val tracks = repository.getTracksForGenre(genre).toMutableList()
            val adapter = MusicTrackAdapter(tracks) { track ->
                repository.removeTrack(track.uri)
            }
            adapters[genre] = adapter

            section.rvTracks.apply {
                this.adapter = adapter
                layoutManager = LinearLayoutManager(this@MusicPickerActivity)
                isNestedScrollingEnabled = false
            }

            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            ) {
                override fun onMove(
                    rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder
                ) = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val pos = viewHolder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        repository.removeTrack(adapter.getTrack(pos).uri)
                        adapter.removeAt(pos)
                    }
                }
            }).attachToRecyclerView(section.rvTracks)

            section.fabAddTrack.setOnClickListener {
                currentPickingGenre = genre
                filePickerLauncher.launch(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/*"
                    }
                )
            }

            binding.llSections.addView(section.root)
        }
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

    companion object {
        val GENRE_MAPPING = listOf(
            "Workout Mode" to "EDM / Upbeat",
            "High Stress Mode" to "Lo-fi / Calming",
            "Active Mode" to "Pop / Energetic",
            "Elevated Stress Mode" to "Ambient / Soft",
            "Relax Mode" to "Jazz / Acoustic",
            "Rest Mode" to "Classical / Sleep"
        )
    }
}

class MusicTrackAdapter(
    private val tracks: MutableList<MusicTrack>,
    private val onDelete: (MusicTrack) -> Unit
) : RecyclerView.Adapter<MusicTrackAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemMusicTrackBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemMusicTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks[position]
        holder.binding.tvTrackTitle.text = track.title
        holder.binding.btnDelete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onDelete(tracks[pos])
                removeAt(pos)
            }
        }
    }

    override fun getItemCount() = tracks.size

    fun addTrack(track: MusicTrack) {
        tracks.add(track)
        notifyItemInserted(tracks.size - 1)
    }

    fun removeAt(position: Int) {
        if (position in tracks.indices) {
            tracks.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getTrack(position: Int): MusicTrack = tracks[position]
}
