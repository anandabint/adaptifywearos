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
    }

    private var lastStepCount: Int? = null
    private var lastStepTimestampMs: Long = 0L
    private var smoothedStepsPerMin: Float = 0f

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
    ): ActivityReading = when {
        isHighActivity(heartRateBpm, stepsPerMinute, accelMagnitudeMs2, gyroMagnitudeRads) ->
            buildHighActivity(heartRateBpm, stepsPerMinute, accelMagnitudeMs2, gyroMagnitudeRads)
        isLowActivity(stepsPerMinute) ->
            buildLowActivity(heartRateBpm, stepsPerMinute)
        else ->
            buildRelax(heartRateBpm, stepsPerMinute)
    }

    // HIGH_ACTIVITY requires elevated HR *with* at least one physical movement signal.
    // Rationale:
    //   HR >100 BPM alone can result from anxiety or autonomic response while sedentary.
    //   Ho et al. (2022) measured HR during physical activity — movement is an implicit context.
    //   Tudor-Locke et al. (2011) explicitly uses step cadence ≥100/min as the MVPA marker.
    //   Therefore: HR elevation is necessary but not sufficient without corroborating movement.
    private fun isHighActivity(hr: Int?, steps: Int, accel: Float?, gyro: Float?): Boolean {
        if (hr == null || hr <= HR_EXERCISE_MIN) return false
        // HR > 100 confirmed — now require at least one movement signal
        return steps >= STEPS_EXERCISE_MIN_PER_MIN
            || (accel != null && accel > ACCEL_EXERCISE_MS2)
            || (gyro != null && gyro > GYRO_EXERCISE_RADS)
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
