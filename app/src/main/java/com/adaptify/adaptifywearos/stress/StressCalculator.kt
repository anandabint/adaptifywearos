package com.adaptify.adaptifywearos.stress

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class StressReading(
    val index: Int = 0,
    val level: String = "Calm",
    val baselineHeartRate: Int? = null,
    val baselineReady: Boolean = false,
    val rmssd: Double = 0.0,
)

class StressCalculator(
    private val warmupSampleTarget: Int = 12,
) {

    private var baselineHeartRate: Float? = null
    private val warmupSamples = ArrayDeque<Int>()
    private var lastProcessedHeartRateTimestamp: Long? = null

    // RMSSD sliding window — successive RR intervals derived from HR.
    // RR_i = 60000 / HR_i (ms); RMSSD = sqrt(mean((RR_i - RR_{i-1})^2)).
    private val rrIntervalsMs = ArrayDeque<Double>()
    private val rrWindowSize = 10

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
            updateRrWindow(currentHeartRate)
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
            rmssd = computeRmssd(),
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

    private fun updateRrWindow(currentHeartRate: Int) {
        if (currentHeartRate <= 0) return
        val rr = 60_000.0 / currentHeartRate
        if (rrIntervalsMs.size >= rrWindowSize) rrIntervalsMs.removeFirst()
        rrIntervalsMs.addLast(rr)
    }

    private fun computeRmssd(): Double {
        if (rrIntervalsMs.size < 2) return 0.0
        var sumSq = 0.0
        var count = 0
        val iterator = rrIntervalsMs.iterator()
        var prev = iterator.next()
        while (iterator.hasNext()) {
            val curr = iterator.next()
            val d = curr - prev
            sumSq += d * d
            count++
            prev = curr
        }
        if (count == 0) return 0.0
        return sqrt(sumSq / count)
    }

    private fun Int.toStressLabel(): String = when {
        this >= 80 -> "High"
        this >= 60 -> "Elevated"
        this >= 30 -> "Moderate"
        else -> "Calm"
    }
}
