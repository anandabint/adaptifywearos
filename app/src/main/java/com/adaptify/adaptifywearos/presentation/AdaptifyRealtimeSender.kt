package com.adaptify.adaptifywearos.presentation

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdaptifyRealtimeSender(
    private val context: Context
) {

    fun send(payload: AdaptifyRealtimePayload) {
        val enriched = payload.copy(batteryLevel = getBatteryLevel(context))
        sendPayload(enriched.toJson())
    }

    fun sendPayload(json: String) {

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val nodes = Wearable.getNodeClient(context).connectedNodes.await()

                Log.d("AdaptifyWearSender", "CONNECTED NODES COUNT = ${nodes.size}")

                for (node in nodes) {

                    Log.d("AdaptifyWearSender", "SENDING PAYLOAD = $json")

                    Wearable.getMessageClient(context)
                        .sendMessage(
                            node.id,
                            "/adaptify/realtime_sensor_payload",
                            json.toByteArray()
                        )
                        .addOnSuccessListener {

                            Log.d(
                                "AdaptifyWearSender",
                                "MESSAGE SEND SUCCESS"
                            )
                        }
                        .addOnFailureListener {

                            Log.e(
                                "AdaptifyWearSender",
                                "MESSAGE SEND FAILED",
                                it
                            )
                        }
                }

            } catch (e: Exception) {

                Log.e(
                    "AdaptifyWearSender",
                    "SEND ERROR",
                    e
                )
            }
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (t: Throwable) {
            Log.w("AdaptifyWearSender", "battery read failed", t)
            -1
        }
    }
}
