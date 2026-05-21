package com.adaptify.mobile

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class MusicTrack(
    val uri: String,
    val title: String,
    val genre: String
)

class MusicRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("music_repo", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<MusicTrack>>() {}.type

    private fun loadAll(): MutableList<MusicTrack> {
        val json = prefs.getString(KEY_TRACKS, null) ?: return mutableListOf()
        return gson.fromJson(json, listType) ?: mutableListOf()
    }

    private fun saveAll(tracks: List<MusicTrack>) {
        prefs.edit().putString(KEY_TRACKS, gson.toJson(tracks)).apply()
    }

    fun addTrack(track: MusicTrack) {
        val tracks = loadAll()
        tracks.add(track)
        saveAll(tracks)
    }

    fun removeTrack(uri: String) {
        saveAll(loadAll().filter { it.uri != uri })
    }

    fun getTracksForGenre(genre: String): List<MusicTrack> =
        loadAll().filter { it.genre == genre }

    fun getAllTracks(): List<MusicTrack> = loadAll()

    fun clear() {
        prefs.edit().remove(KEY_TRACKS).apply()
    }

    companion object {
        private const val KEY_TRACKS = "tracks"
    }
}
