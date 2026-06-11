package com.adaptify.adaptifywearos.presentation

data class AdaptifyRealtimePayload(
    val heartRate: Int,
    val steps: Int,
    val activityMode: String,       // ActivityMode.name — "HIGH_ACTIVITY" | "LOW_ACTIVITY" | "RELAX"
    val activityConfidence: Float,  // 0.0–1.0
    val batteryLevel: Int = -1,
    val monitoring: Boolean = true,
) {
    fun toJson(): String = buildString {
        append("{")
        append("\"heartRate\": $heartRate, ")
        append("\"steps\": $steps, ")
        append("\"activityMode\": \"$activityMode\", ")
        append("\"activityConfidence\": $activityConfidence, ")
        append("\"batteryLevel\": $batteryLevel, ")
        append("\"monitoring\": $monitoring")
        append("}")
    }
}
