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

    /**
     * Migrate tracks from old genre systems to current 3-genre system.
     * Safe to call multiple times (idempotent).
     * Old genre → New genre mapping:
     *   EDM / Upbeat, Pop / Energetic     → Exercise Music (HIGH_ACTIVITY)
     *   Lo-fi / Calming, Ambient / Soft,
     *   Stress Relief                     → Focus Music    (LOW_ACTIVITY)
     *   Jazz / Acoustic, Classical / Sleep → Relax Music   (RELAX)
     */
    fun migrateGenres(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("genre_migrated_v3", false)) return  // already done

        val oldToNew = mapOf(
            "EDM / Upbeat"       to ActivityClassifier.GENRE_HIGH_ACTIVITY,
            "Pop / Energetic"    to ActivityClassifier.GENRE_HIGH_ACTIVITY,
            "Lo-fi / Calming"    to ActivityClassifier.GENRE_LOW_ACTIVITY,
            "Ambient / Soft"     to ActivityClassifier.GENRE_LOW_ACTIVITY,
            "Stress Relief"      to ActivityClassifier.GENRE_LOW_ACTIVITY,
            "Jazz / Acoustic"    to ActivityClassifier.GENRE_RELAX,
            "Classical / Sleep"  to ActivityClassifier.GENRE_RELAX,
        )

        val migrated = getAllTracks(context).map { track ->
            val newGenre = oldToNew[track.genre]
            if (newGenre != null) track.copy(genre = newGenre) else track
        }
        saveTracks(context, migrated)
        prefs.edit().putBoolean("genre_migrated_v3", true).apply()
    }

    private fun saveTracks(context: Context, tracks: List<MusicTrack>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(tracks)
        prefs.edit().putString(KEY_TRACKS, json).apply()
    }
}
