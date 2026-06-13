package com.adaptify.adaptifywearos.classifier

import com.adaptify.adaptifywearos.health.HeartRateState
import com.adaptify.adaptifywearos.sensor.SensorSnapshot
import com.adaptify.adaptifywearos.sensor.Vector3Sample
import kotlin.math.sqrt

enum class ActivityMode { HIGH_ACTIVITY, LOW_ACTIVITY, RELAX }

data class ActivityReading(
    val mode: ActivityMode,
    val confidence: Float,
    val reason: String,
)

class ActivityClassifier {

    companion object {
        // HR threshold — Ho et al., 2022, Sage Journals: "Wrist-worn wearable HR as exercise intensity proxy"
        private const val HR_EXERCISE_MIN = 100

        // Step cadence threshold — Tudor-Locke et al., 2011, Int J Behav Nutr Phys Act
        // ≥100 steps/min = moderate-to-vigorous; <30 steps/min = sedentary
        private const val STEPS_EXERCISE_MIN_PER_MIN = 100
        private const val STEPS_SEDENTARY_MAX_PER_MIN = 30

        // Accelerometer magnitude thresholds in m/s² (1g = 9.81 m/s²)
        // 1.5g exercise / 0.3g sedentary — Tudor-Locke et al., 2011, Int J Behav Nutr Phys Act
        // Net acceleration (gravity-removed): 1.5g above gravity
        // TYPE_ACCELEROMETER includes gravity, so net threshold = (1.5g + 1g) × 9.81
        // Effective: user must generate 1.5g net movement on top of resting 1g
        private const val ACCEL_EXERCISE_MS2 = 24.53f   // (1.5 + 1.0) × 9.81 — Ho et al., 2022
        private const val ACCEL_SEDENTARY_MS2 = 2.94f   // 0.3 × 9.81

        // Gyro 2.0 rad/s = fast deliberate limb rotation (running arm swing, cycling pedal)
        // 1.0 rad/s triggered on casual hand gestures — too sensitive for exercise detection
        // Raised to 2.0 rad/s per Ho et al., 2022 exercise activity context
        private const val GYRO_EXERCISE_RADS = 2.0f

        private const val GRAVITY_MS2 = 9.81f
        private const val STEP_RATE_INTERVAL_MS = 3_000L

        // Debounce for HIGH_ACTIVITY entry — requires N consecutive readings meeting the
        // AND condition (HR>100 AND steps>=100/min) before switching to HIGH_ACTIVITY.
        // This is a design decision to filter transient HR noise (instant HR readings can
        // fluctuate ±3-5bpm around the threshold during sustained walking, causing brief
        // false-positive AND-condition matches). Exiting HIGH_ACTIVITY remains instant.
        private const val HIGH_ACTIVITY_DEBOUNCE_COUNT = 3
    }

    private var lastStepCount: Int? = null
    private var lastStepTimestampMs: Long = 0L
    private var smoothedStepsPerMin: Float = 0f

    // Consecutive count of readings meeting the HIGH_ACTIVITY AND condition.
    // Resets to 0 whenever the condition is not met. See HIGH_ACTIVITY_DEBOUNCE_COUNT.
    private var highActivityConsecutiveCount = 0

    // Mode stability buffer — mode must be consistent for MIN_STABLE_MS
    // before it is reported. Prevents single-sample spikes from changing mode.
    // Note: this stabilizes the reported mode only; music hysteresis (30s) is
    // handled separately in AdaptiveMusicService.
    private var candidateMode: ActivityMode = ActivityMode.RELAX
    private var candidateSince: Long = 0L
    private val MIN_STABLE_MS = 5_000L  // 5 seconds consistent before mode changes
    private var stableMode: ActivityMode = ActivityMode.RELAX

    fun classify(
        sensorSnapshot: SensorSnapshot,
        heartRateState: HeartRateState,
    ): ActivityReading {
        val nowMs = System.currentTimeMillis()
        updateStepRate(sensorSnapshot.steps, nowMs)

        val raw = classifyInternal(
            heartRateBpm = heartRateState.bpm,
            stepsPerMinute = smoothedStepsPerMin.toInt(),
            accelMagnitudeMs2 = sensorSnapshot.accelerometer?.magnitude(),
            gyroMagnitudeRads = sensorSnapshot.gyroscope?.magnitude(),
        )

        // Stability buffer: only commit to new mode after MIN_STABLE_MS
        if (raw.mode != candidateMode) {
            candidateMode = raw.mode
            candidateSince = nowMs
        }
        if (raw.mode == candidateMode && (nowMs - candidateSince) >= MIN_STABLE_MS) {
            stableMode = candidateMode
        }

        // Return stable mode but with raw confidence and reason for transparency
        return if (raw.mode == stableMode) raw
        else raw.copy(
            mode = stableMode,
            reason = "${stableMode.name}(stabilized): ${raw.reason}",
        )
    }

    // internal visibility for JVM unit tests in the same Gradle module (app/src/test/)
    internal fun classifyInternal(
        heartRateBpm: Int?,
        stepsPerMinute: Int,
        accelMagnitudeMs2: Float?,
        gyroMagnitudeRads: Float?,
    ): ActivityReading {
        val highActivityConditionMet =
            isHighActivity(heartRateBpm, stepsPerMinute, accelMagnitudeMs2, gyroMagnitudeRads)

        if (highActivityConditionMet) {
            highActivityConsecutiveCount++
        } else {
            highActivityConsecutiveCount = 0
        }

        return when {
            highActivityConditionMet && highActivityConsecutiveCount >= HIGH_ACTIVITY_DEBOUNCE_COUNT ->
                buildHighActivity(heartRateBpm, stepsPerMinute, accelMagnitudeMs2, gyroMagnitudeRads)
            // During the debounce period (count 1-2) the AND condition is met but not yet
            // confirmed; report LOW_ACTIVITY rather than RELAX for a smoother transition
            // (steps>=100 never matches isLowActivity's 30-until-100 range otherwise).
            isLowActivity(stepsPerMinute) || highActivityConditionMet ->
                buildLowActivity(heartRateBpm, stepsPerMinute)
            else ->
                buildRelax(heartRateBpm, stepsPerMinute)
        }
    }

    /** Resets internal debounce state. For use in unit tests between scenarios. */
    fun resetDebounceState() {
        highActivityConsecutiveCount = 0
    }

    // HIGH_ACTIVITY requires BOTH high cadence (Tudor-Locke et al., 2011 — MVPA threshold
    // >=100 steps/min) AND elevated heart rate (Ho et al., 2022 — HR>100bpm as exercise
    // intensity proxy). Combining both signals is a design decision to reduce false
    // positives from HR alone (e.g. HR rising during sustained light walking) — consistent
    // with multi-sensor fusion approaches shown to improve high-intensity classification
    // accuracy (Mehrang et al.).
    private fun isHighActivity(hr: Int?, steps: Int, accel: Float?, gyro: Float?): Boolean {
        return hr != null && hr > HR_EXERCISE_MIN && steps >= STEPS_EXERCISE_MIN_PER_MIN
    }

    private fun buildHighActivity(
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
            mode = ActivityMode.HIGH_ACTIVITY,
            confidence = confidence,
            reason = "HIGH_ACTIVITY: ${signals.joinToString(", ")}",
        )
    }

    // LOW_ACTIVITY: step cadence dalam rentang "light" menurut Tudor-Locke et al. (2011) —
    // 30-99 steps/min menunjukkan aktivitas ringan (jalan santai, gerak badan ringan)
    // tanpa mencapai ambang MVPA (>=100 steps/min).
    private fun isLowActivity(steps: Int): Boolean {
        return steps in STEPS_SEDENTARY_MAX_PER_MIN until STEPS_EXERCISE_MIN_PER_MIN
    }

    private fun buildLowActivity(hr: Int?, steps: Int): ActivityReading {
        return ActivityReading(
            mode = ActivityMode.LOW_ACTIVITY,
            confidence = 0.7f,
            reason = "LOW_ACTIVITY: HR=${hr}bpm steps=${steps}/min",
        )
    }

    private fun buildRelax(hr: Int?, steps: Int): ActivityReading {
        return ActivityReading(
            mode = ActivityMode.RELAX,
            confidence = 0.6f,
            reason = "RELAX: HR=${hr}bpm steps=${steps}/min",
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
            smoothedStepsPerMin = smoothedStepsPerMin * 0.5f + raw * 0.5f
            lastStepCount = steps; lastStepTimestampMs = nowMs
        }
    }

    private fun Vector3Sample.magnitude(): Float = sqrt(x * x + y * y + z * z)
}
