package com.adaptify.mobile

import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

class AdaptifyMobileReceiver : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WearableListenerService onCreate — package=$packageName, listening path=$PAYLOAD_PATH")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(
            TAG,
            "onMessageReceived from node=${messageEvent.sourceNodeId} path=${messageEvent.path} bytes=${messageEvent.data?.size ?: 0}"
        )

        if (messageEvent.path != PAYLOAD_PATH) {
            Log.d(TAG, "Ignoring message with path: ${messageEvent.path}")
            return
        }

        try {
            val jsonString = String(messageEvent.data, Charsets.UTF_8)
            Log.d(TAG, "Received JSON: $jsonString")

            val json = JSONObject(jsonString)
            val heartRate = json.optInt(EXTRA_HEART_RATE, 0)
            val steps = json.optInt(EXTRA_STEPS, 0)
            val stressIndex = json.optInt("stress", 0)
            val activityMode = json.optString(EXTRA_ACTIVITY_MODE, "")
            val activityConfidence = json.optDouble("activityConfidence", 0.0).toFloat()
            val rmssd = json.optDouble("rmssd", 0.0)
            val batteryLevel = json.optInt("batteryLevel", -1)
            val monitoring = json.optBoolean("monitoring", true)

            Log.d(
                TAG,
                "Parsed: hr=$heartRate steps=$steps stress=$stressIndex watchMode=$activityMode " +
                    "conf=$activityConfidence rmssd=$rmssd battery=$batteryLevel monitoring=$monitoring"
            )

            // Allow heartRate=0 only when the watch explicitly signalled monitoring=false
            // (e.g. user just toggled the watch switch off — UI still needs the state update).
            if (heartRate <= 0 && monitoring) {
                Log.w(TAG, "Dropping payload — heartRate=$heartRate (watch sensor warming up or not worn)")
                return
            }

            val mode = ActivityClassifier.classify(heartRate, stressIndex)
            val genre = ActivityClassifier.getMusicGenre(mode)

            val snapshot = AdaptifyRealtimeSnapshot(
                heartRate = heartRate,
                steps = steps,
                stressIndex = stressIndex,
                mode = mode,
                genre = genre,
                updatedAtEpochMillis = System.currentTimeMillis(),
                rmssd = rmssd,
                batteryLevel = batteryLevel,
                monitoring = monitoring,
            )

            AdaptifyRealtimeStore.save(this, snapshot)

            val intent = AdaptifyRealtimeStore.toBroadcastIntent(packageName, snapshot)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

            Log.d(
                TAG,
                "Processed: HR=$heartRate mode=$mode genre=$genre rmssd=${"%.1f".format(rmssd)} " +
                    "battery=$batteryLevel monitoring=$monitoring — LocalBroadcast action=${intent.action}"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message", e)
        }
    }

    companion object {
        const val TAG = "AdaptifyMobileReceiver"
        const val PAYLOAD_PATH = "/adaptify/realtime_sensor_payload"
        const val ACTION_ADAPTIFY_DATA_RECEIVED = "com.adaptify.mobile.ACTION_ADAPTIFY_DATA_RECEIVED"
        const val ACTION_DATA_UPDATED = "com.adaptify.mobile.ACTION_DATA_UPDATED"
        const val EXTRA_HEART_RATE = "heartRate"
        const val EXTRA_STEPS = "steps"
        const val EXTRA_STRESS_INDEX = "stressIndex"
        const val EXTRA_RMSSD = "rmssd"
        const val EXTRA_ACTIVITY_MODE = "activityMode"
        const val EXTRA_ACTIVITY_CONFIDENCE = "activityConfidence"
        const val EXTRA_MODE = "mode"
        const val EXTRA_GENRE = "genre"
        const val EXTRA_UPDATED_AT = "updatedAt"
        const val EXTRA_BATTERY_LEVEL = "batteryLevel"
        const val EXTRA_MONITORING = "monitoring"
    }
}
