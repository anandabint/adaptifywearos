package com.adaptify.adaptifywearos.presentation

data class AdaptifyRealtimePayload(
    val heartRate: Int,
    val steps: Int,
    val stressIndex: Int,
    val activityMode: String,       // ActivityMode.name — "EXERCISE" | "STRESS" | "RELAX"
    val activityConfidence: Float,  // 0.0–1.0
) {
    fun toJson(): String = buildString {
        append("{")
        append("\"heartRate\": $heartRate, ")
        append("\"steps\": $steps, ")
        append("\"stress\": $stressIndex, ")
        append("\"activityMode\": \"$activityMode\", ")
        append("\"activityConfidence\": $activityConfidence")
        append("}")
    }
}
