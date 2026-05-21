package com.adaptify.adaptifywearos.presentation

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdaptifyRealtimeSender(
    private val context: Context
) {

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
}