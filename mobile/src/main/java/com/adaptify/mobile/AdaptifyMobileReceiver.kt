package com.adaptify.mobile

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class AdaptifyMobileReceiver : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/adaptify/realtime"
            ) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val intent = Intent(ACTION_DATA_UPDATED).apply {
                    putExtra(EXTRA_HEART_RATE, map.getInt("heartRate", 0))
                    putExtra(EXTRA_STEPS, map.getInt("steps", 0))
                    putExtra(EXTRA_STRESS_INDEX, map.getInt("stressIndex", 0))
                    putExtra(EXTRA_RMSSD, map.getDouble("rmssd", 0.0))
                    putExtra(EXTRA_ACTIVITY_MODE, map.getString("activityMode", ""))
                    putExtra(EXTRA_GENRE, map.getString("genre", ""))
                    putExtra(EXTRA_UPDATED_AT, map.getLong("updatedAt", 0L))
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                Log.d(TAG, "Data received: HR=${map.getInt("heartRate")} mode=${map.getString("activityMode")}")
            }
        }
    }

    companion object {
        private const val TAG = "AdaptifyReceiver"
        const val ACTION_DATA_UPDATED = "com.adaptify.mobile.DATA_UPDATED"
        const val EXTRA_HEART_RATE = "heartRate"
        const val EXTRA_STEPS = "steps"
        const val EXTRA_STRESS_INDEX = "stressIndex"
        const val EXTRA_RMSSD = "rmssd"
        const val EXTRA_ACTIVITY_MODE = "activityMode"
        const val EXTRA_GENRE = "genre"
        const val EXTRA_UPDATED_AT = "updatedAt"
    }
}
