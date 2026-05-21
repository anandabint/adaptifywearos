package com.adaptify.adaptifywearos.stress

import kotlin.math.roundToInt

data class StressReading(
    val index: Int = 0,
    val level: String = "Calm",
    val baselineHeartRate: Int? = null,
    val baselineReady: Boolean = false,
)

class StressCalculator(
    private val warmupSampleTarget: Int = 12,
) {

    private var baselineHeartRate: Float? = null
    private val warmupSamples = ArrayDeque<Int>()
    private var lastProcessedHeartRateTimestamp: Long? = null

    fun calculate(
        currentHeartRate: Int?,
        heartRateTimestamp: Long?,
        accelerometerDelta: Float,
    ): StressReading {
        if (
            currentHeartRate != null &&
            heartRateTimestamp != null &&
            heartRateTimestamp != lastProcessedHeartRateTimestamp
        ) {
            updateBaseline(currentHeartRate)
            lastProcessedHeartRateTimestamp = heartRateTimestamp
        }

        val baseline = baselineHeartRate
        val heartDelta = if (baseline != null && currentHeartRate != null) {
            (currentHeartRate - baseline).coerceAtLeast(0f)
        } else {
            0f
        }

        val heartScore = (heartDelta / 30f * 70f).coerceIn(0f, 70f)
        val motionScore = (accelerometerDelta / 3f * 30f).coerceIn(0f, 30f)
        val totalScore = (heartScore + motionScore).roundToInt().coerceIn(0, 100)

        return StressReading(
            index = totalScore,
            level = totalScore.toStressLabel(),
            baselineHeartRate = baseline?.roundToInt(),
            baselineReady = warmupSamples.size >= warmupSampleTarget,
        )
    }

    private fun updateBaseline(currentHeartRate: Int) {
        if (warmupSamples.size < warmupSampleTarget) {
            warmupSamples.addLast(currentHeartRate)
            baselineHeartRate = warmupSamples.average().toFloat()
            return
        }

        val currentBaseline = baselineHeartRate ?: currentHeartRate.toFloat()
        if (currentHeartRate <= currentBaseline + 8f) {
            baselineHeartRate = (currentBaseline * 0.92f) + (currentHeartRate * 0.08f)
        }
    }

    private fun Int.toStressLabel(): String = when {
        this >= 80 -> "High"
        this >= 60 -> "Elevated"
        this >= 30 -> "Moderate"
        else -> "Calm"
    }
}
