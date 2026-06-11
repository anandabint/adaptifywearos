package com.adaptify.mobile

import android.content.Context
import android.content.Intent

data class AdaptifyRealtimeSnapshot(
    val heartRate: Int,
    val steps: Int,
    val mode: String,
    val genre: String,
    val updatedAtEpochMillis: Long,
    val rmssd: Double = 0.0,
    val batteryLevel: Int = -1,
    val monitoring: Boolean = true,
)

object AdaptifyRealtimeStore {
    private const val PREFS_NAME = "adaptify_realtime_store"
    private const val KEY_HEART_RATE = "heartRate"
    private const val KEY_STEPS = "steps"
    private const val KEY_MODE = "mode"
    private const val KEY_GENRE = "genre"
    private const val KEY_UPDATED_AT = "updatedAt"
    private const val KEY_RMSSD = "rmssd"
    private const val KEY_BATTERY_LEVEL = "batteryLevel"
    private const val KEY_MONITORING = "monitoring"

    fun save(context: Context, snapshot: AdaptifyRealtimeSnapshot) {
        prefs(context)
            .edit()
            .putInt(KEY_HEART_RATE, snapshot.heartRate)
            .putInt(KEY_STEPS, snapshot.steps)
            .putString(KEY_MODE, snapshot.mode)
            .putString(KEY_GENRE, snapshot.genre)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAtEpochMillis)
            .putFloat(KEY_RMSSD, snapshot.rmssd.toFloat())
            .putInt(KEY_BATTERY_LEVEL, snapshot.batteryLevel)
            .putBoolean(KEY_MONITORING, snapshot.monitoring)
            .apply()
    }

    fun load(context: Context): AdaptifyRealtimeSnapshot? {
        val preferences = prefs(context)
        if (!preferences.contains(KEY_UPDATED_AT)) return null

        return AdaptifyRealtimeSnapshot(
            heartRate = preferences.getInt(KEY_HEART_RATE, 0),
            steps = preferences.getInt(KEY_STEPS, 0),
            mode = preferences.getString(KEY_MODE, "").orEmpty(),
            genre = preferences.getString(KEY_GENRE, "").orEmpty(),
            updatedAtEpochMillis = preferences.getLong(KEY_UPDATED_AT, 0L),
            rmssd = preferences.getFloat(KEY_RMSSD, 0f).toDouble(),
            batteryLevel = preferences.getInt(KEY_BATTERY_LEVEL, -1),
            monitoring = preferences.getBoolean(KEY_MONITORING, true),
        )
    }

    fun toBroadcastIntent(packageName: String, snapshot: AdaptifyRealtimeSnapshot): Intent =
        Intent(AdaptifyMobileReceiver.ACTION_DATA_UPDATED).apply {
            setPackage(packageName)
            putExtra(AdaptifyMobileReceiver.EXTRA_HEART_RATE, snapshot.heartRate)
            putExtra(AdaptifyMobileReceiver.EXTRA_STEPS, snapshot.steps)
            putExtra(AdaptifyMobileReceiver.EXTRA_RMSSD, snapshot.rmssd)
            putExtra(AdaptifyMobileReceiver.EXTRA_ACTIVITY_MODE, snapshot.mode)
            putExtra(AdaptifyMobileReceiver.EXTRA_GENRE, snapshot.genre)
            putExtra(AdaptifyMobileReceiver.EXTRA_UPDATED_AT, snapshot.updatedAtEpochMillis)
            putExtra(AdaptifyMobileReceiver.EXTRA_BATTERY_LEVEL, snapshot.batteryLevel)
            putExtra(AdaptifyMobileReceiver.EXTRA_MONITORING, snapshot.monitoring)
        }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
