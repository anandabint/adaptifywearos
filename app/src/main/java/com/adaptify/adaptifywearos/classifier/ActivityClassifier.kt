package com.adaptify.adaptifywearos.classifier

import com.adaptify.adaptifywearos.health.HeartRateState
import com.adaptify.adaptifywearos.sensor.SensorSnapshot
import com.adaptify.adaptifywearos.sensor.Vector3Sample
import com.adaptify.adaptifywearos.stress.StressReading
import kotlin.math.sqrt

enum class ActivityMode { EXERCISE, STRESS, RELAX }

data class ActivityReading(
    val mode: ActivityMode,
    val confidence: Float,
    val reason: String,
)

class ActivityClassifier {

    companion object {
        // HR threshold — Ho et al., 2022, Sage Journals: "Wrist-worn wearable HR as exercise intensity proxy"
        private const val HR_EXERCISE_MIN = 100
        private const val HR_STRESS_LOW = 60
        private const val HR_STRESS_HIGH = 100

        // Step cadence threshold — Tudor-Locke et al., 2021, Int J Behav Nutr Phys Act
        // ≥100 steps/min = moderate-to-vigorous; <30 steps/min = sedentary
        private const val STEPS_EXERCISE_MIN_PER_MIN = 100
        private const val STEPS_SEDENTARY_MAX_PER_MIN = 30

        // RMSSD proxy — Chalmers et al., 2022, Sensors MDPI:
        // "RMSSD <20ms = sympathetic dominance (stress); >40ms = parasympathetic (relax)"
        // StressCalculator.index maps inversely to RMSSD:
        //   index ≥ 60 ≈ estimated RMSSD <20ms (stressed)
        //   index ≤ 30 ≈ estimated RMSSD >40ms (relaxed)
        private const val STRESS_IDX_HIGH = 60
        private const val STRESS_IDX_LOW = 30

        // Accelerometer magnitude thresholds in m/s² (1g = 9.81 m/s²)
        // 1.5g exercise / 0.3g sedentary — Tudor-Locke et al., 2021
        private const val ACCEL_EXERCISE_MS2 = 14.72f   // 1.5 × 9.81
        private const val ACCEL_SEDENTARY_MS2 = 2.94f   // 0.3 × 9.81

        // Gyroscope angular velocity — Ho et al., 2022: >1 rad/s = significant limb rotation
        private const val GYRO_EXERCISE_RADS = 1.0f

        private const val GRAVITY_MS2 = 9.81f
        private const val STEP_RATE_INTERVAL_MS = 5_000L
    }

    private var lastStepCount: Int? = null
    private var lastStepTimestampMs: Long = 0L
    private var smoothedStepsPerMin: Float = 0f

    fun classify(
        sensorSnapshot: SensorSnapshot,
        heartRateState: HeartRateState,
        stressReading: StressReading,
    ): ActivityReading {
        updateStepRate(sensorSnapshot.steps, System.currentTimeMillis())
        return classifyInternal(
            heartRateBpm = heartRateState.bpm,
            stepsPerMinute = smoothedStepsPerMin.toInt(),
            stressIndex = stressReading.index,
            accelMagnitudeMs2 = sensorSnapshot.accelerometer?.magnitude(),
            gyroMagnitudeRads = sensorSnapshot.gyroscope?.magnitude(),
        )
    }

    // internal visibility for JVM unit tests in the same Gradle module (app/src/test/)
    internal fun classifyInternal(
        heartRateBpm: Int?,
        stepsPerMinute: Int,
        stressIndex: Int,
        accelMagnitudeMs2: Float?,
        gyroMagnitudeRads: Float?,
    ): ActivityReading = when {
        isExercise(heartRateBpm, stepsPerMinute, accelMagnitudeMs2, gyroMagnitudeRads) ->
            buildExercise(heartRateBpm, stepsPerMinute, accelMagnitudeMs2, gyroMagnitudeRads)
        isStress(heartRateBpm, stepsPerMinute, stressIndex) ->
            buildStress(heartRateBpm, stepsPerMinute, stressIndex)
        else ->
            buildRelax(heartRateBpm, stepsPerMinute, stressIndex)
    }

    // Priority 1 — EXERCISE: elevated HR with at least one physical movement signal
    private fun isExercise(hr: Int?, steps: Int, accel: Float?, gyro: Float?): Boolean {
        if (hr == null || hr <= HR_EXERCISE_MIN) return false
        return steps >= STEPS_EXERCISE_MIN_PER_MIN
            || (accel != null && accel > ACCEL_EXERCISE_MS2)
            || (gyro != null && gyro > GYRO_EXERCISE_RADS)
    }

    // Priority 2 — STRESS: moderate HR, sedentary, low HRV proxy (high stress index)
    private fun isStress(hr: Int?, steps: Int, stressIndex: Int): Boolean {
        if (hr == null) return false
        return hr in HR_STRESS_LOW..HR_STRESS_HIGH
            && steps < STEPS_SEDENTARY_MAX_PER_MIN
            && stressIndex >= STRESS_IDX_HIGH
    }

    private fun buildExercise(
        hr: Int?,
        steps: Int,
        accel: Float?,
        gyro: Float?,
    ): ActivityReading {
        val accelG = accel?.div(GRAVITY_MS2)
        val accelThrG = ACCEL_EXERCISE_MS2 / GRAVITY_MS2

        val signals = buildList {
            add("HR=${hr}bpm")
            if (steps >= STEPS_EXERCISE_MIN_PER_MIN) add("steps=${steps}/min")
            accelG?.let { g -> if (g > accelThrG) add("accel=${"%.2f".format(g)}g") }
            gyro?.let { r -> if (r > GYRO_EXERCISE_RADS) add("gyro=${"%.2f".format(r)}rad/s") }
        }

        var score = 0f; var n = 0
        hr?.let { score += ((it - HR_EXERCISE_MIN) / 60f).coerceIn(0f, 1f); n++ }
        if (steps >= STEPS_EXERCISE_MIN_PER_MIN) {
            score += ((steps - STEPS_EXERCISE_MIN_PER_MIN) / 100f).coerceIn(0f, 1f); n++
        }
        accelG?.let { g ->
            if (g > accelThrG) { score += ((g - accelThrG) / accelThrG).coerceIn(0f, 1f); n++ }
        }

        val confidence = if (n == 0) 0.6f else (0.6f + score / n * 0.4f).coerceIn(0f, 1f)
        return ActivityReading(
            mode = ActivityMode.EXERCISE,
            confidence = confidence,
            reason = "EXERCISE: ${signals.joinToString(", ")}",
        )
    }

    private fun buildStress(hr: Int?, steps: Int, stressIndex: Int): ActivityReading {
        val confidence = (0.6f + (stressIndex - STRESS_IDX_HIGH) / 40f * 0.4f).coerceIn(0f, 1f)
        return ActivityReading(
            mode = ActivityMode.STRESS,
            confidence = confidence,
            reason = "STRESS: HR=${hr}bpm steps=${steps}/min stressIdx=${stressIndex} (~RMSSD<20ms)",
        )
    }

    private fun buildRelax(hr: Int?, steps: Int, stressIndex: Int): ActivityReading {
        val confidence = (0.6f + (STRESS_IDX_LOW - stressIndex) / 30f * 0.4f).coerceIn(0f, 1f)
        return ActivityReading(
            mode = ActivityMode.RELAX,
            confidence = confidence,
            reason = "RELAX: HR=${hr}bpm steps=${steps}/min stressIdx=${stressIndex} (~RMSSD>40ms)",
        )
    }

    private fun updateStepRate(steps: Int?, nowMs: Long) {
        if (steps == null) return
        val last = lastStepCount
        if (last == null || lastStepTimestampMs == 0L) {
            lastStepCount = steps; lastStepTimestampMs = nowMs; return
        }
        val elapsed = nowMs - lastStepTimestampMs
        if (elapsed >= STEP_RATE_INTERVAL_MS) {
            val delta = (steps - last).coerceAtLeast(0)
            val raw = (delta * 60_000f) / elapsed
            smoothedStepsPerMin = smoothedStepsPerMin * 0.7f + raw * 0.3f
            lastStepCount = steps; lastStepTimestampMs = nowMs
        }
    }

    private fun Vector3Sample.magnitude(): Float = sqrt(x * x + y * y + z * z)
}
