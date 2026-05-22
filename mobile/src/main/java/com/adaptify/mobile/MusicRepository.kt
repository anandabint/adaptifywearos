package com.adaptify.mobile

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class MusicTrack(
    val uri: String,
    val title: String,
    val genre: String
)

object MusicRepository {
    private const val PREFS_NAME = "music_prefs"
    private const val KEY_TRACKS = "tracks"
    private val gson = Gson()

    fun addTrack(context: Context, track: MusicTrack) {
        val tracks = getAllTracks(context).toMutableList()
        tracks.add(track)
        saveTracks(context, tracks)
    }

    fun removeTrack(context: Context, uri: String) {
        val tracks = getAllTracks(context).filter { it.uri != uri }
        saveTracks(context, tracks)
    }

    fun getTracksForGenre(context: Context, genre: String): List<MusicTrack> {
        return getAllTracks(context).filter { it.genre == genre }
    }

    fun getAllTracks(context: Context): List<MusicTrack> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TRACKS, "[]") ?: "[]"
        val type = object : TypeToken<List<MusicTrack>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun clear(context: Context) {
        saveTracks(context, emptyList())
    }

    private fun saveTracks(context: Context, tracks: List<MusicTrack>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(tracks)
        prefs.edit().putString(KEY_TRACKS, json).apply()
    }
}
