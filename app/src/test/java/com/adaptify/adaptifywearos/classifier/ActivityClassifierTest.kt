package com.adaptify.adaptifywearos.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActivityClassifierTest {

    private lateinit var classifier: ActivityClassifier

    @Before
    fun setUp() {
        classifier = ActivityClassifier()
    }

    // --- EXERCISE ---

    @Test
    fun `exercise - high HR and high step count returns EXERCISE`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 120,
            stepsPerMinute = 120,
            rmssd = 35.0,
            accelMagnitudeMs2 = 10f,
            gyroMagnitudeRads = 0.5f,
        )
        assertEquals(ActivityMode.EXERCISE, result.mode)
    }

    @Test
    fun `exercise - high HR and accel above 1_5g returns EXERCISE`() {
        // 26 m/s² > 24.53 (1.5g net + 1g gravity) threshold
        val result = classifier.classifyInternal(
            heartRateBpm = 110,
            stepsPerMinute = 20,
            rmssd = 35.0,
            accelMagnitudeMs2 = 26f,
            gyroMagnitudeRads = 0.2f,
        )
        assertEquals(ActivityMode.EXERCISE, result.mode)
    }

    @Test
    fun `exercise - high HR and gyro above 1 rad_s returns EXERCISE`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 105,
            stepsPerMinute = 20,
            rmssd = 35.0,
            accelMagnitudeMs2 = 10f,
            gyroMagnitudeRads = 3.0f,
        )
        assertEquals(ActivityMode.EXERCISE, result.mode)
    }

    @Test
    fun `exercise - takes priority over STRESS even with low RMSSD`() {
        // HR=130, steps=150, RMSSD=10ms (stress signal) → EXERCISE still wins
        val result = classifier.classifyInternal(
            heartRateBpm = 130,
            stepsPerMinute = 150,
            rmssd = 10.0,
            accelMagnitudeMs2 = 20f,
            gyroMagnitudeRads = 2.5f,
        )
        assertEquals(ActivityMode.EXERCISE, result.mode)
    }

    @Test
    fun `exercise - HR exactly 100 BPM with no movement returns RELAX`() {
        // Threshold is strictly > 100; with no movement signals above threshold, falls to RELAX
        val result = classifier.classifyInternal(
            heartRateBpm = 100,
            stepsPerMinute = 10,
            rmssd = 35.0,
            accelMagnitudeMs2 = 5f,
            gyroMagnitudeRads = 0.5f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `exercise - HR just above 101 BPM with 100 steps_min returns EXERCISE`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 101,
            stepsPerMinute = 100,
            rmssd = 35.0,
            accelMagnitudeMs2 = 8f,
            gyroMagnitudeRads = 0.3f,
        )
        assertEquals(ActivityMode.EXERCISE, result.mode)
    }

    @Test
    fun `exercise - high HR but all movement signals below threshold falls to RELAX`() {
        // HR=110 but steps=0, accel=5 m/s² (0.5g < 1.5g), gyro=0.2 < 1.0
        val result = classifier.classifyInternal(
            heartRateBpm = 110,
            stepsPerMinute = 0,
            rmssd = 35.0,
            accelMagnitudeMs2 = 5f,
            gyroMagnitudeRads = 0.2f,
        )
        // HR=110 is outside STRESS range 60-100, so falls through to RELAX
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    // --- STRESS ---

    @Test
    fun `stress - mid HR, low steps, low RMSSD returns STRESS`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 80,
            stepsPerMinute = 10,
            rmssd = 12.0,
            accelMagnitudeMs2 = 2f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.STRESS, result.mode)
    }

    @Test
    fun `stress - RMSSD just below 20ms boundary returns STRESS`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 70,
            stepsPerMinute = 5,
            rmssd = 19.9,
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.STRESS, result.mode)
    }

    @Test
    fun `stress - RMSSD at 20ms boundary falls to RELAX (not strict less than)`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 70,
            stepsPerMinute = 5,
            rmssd = 20.0,
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `stress - HR 110 is outside 60-100 range so falls to RELAX despite low RMSSD`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 110,
            stepsPerMinute = 5,
            rmssd = 8.0,
            accelMagnitudeMs2 = 2f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `stress - RMSSD 0_0 during warmup does NOT trigger STRESS`() {
        // StressCalculator emits rmssd=0.0 until RR window has >=2 samples
        val result = classifier.classifyInternal(
            heartRateBpm = 75,
            stepsPerMinute = 5,
            rmssd = 0.0,
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    // --- RELAX ---

    @Test
    fun `relax - low HR, low steps, high RMSSD returns RELAX`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 60,
            stepsPerMinute = 5,
            rmssd = 55.0,
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.05f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `relax - null HR returns RELAX as default fallback`() {
        val result = classifier.classifyInternal(
            heartRateBpm = null,
            stepsPerMinute = 0,
            rmssd = 5.0,
            accelMagnitudeMs2 = 20f,
            gyroMagnitudeRads = 3f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `relax - sedentary HR with moderate RMSSD above threshold returns RELAX`() {
        // HR in range, steps low, but RMSSD >= 20ms → not enough for STRESS
        val result = classifier.classifyInternal(
            heartRateBpm = 80,
            stepsPerMinute = 10,
            rmssd = 30.0,
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `relax - low RMSSD but stable HR (low SDHR) returns RELAX not STRESS`() {
        // Duduk santai: RMSSD=10ms (rendah) tapi HR stabil SDHR=1.5 BPM
        // Tanpa fix ini → STRESS; dengan fix → RELAX
        val result = classifier.classifyInternal(
            heartRateBpm = 75,
            stepsPerMinute = 5,
            rmssd = 10.0,
            sdhr = 1.5,   // HR sangat stabil = santai
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `stress - low RMSSD AND unstable HR (high SDHR) returns STRESS`() {
        // Stress nyata: RMSSD=10ms rendah DAN HR tidak stabil SDHR=5 BPM
        val result = classifier.classifyInternal(
            heartRateBpm = 75,
            stepsPerMinute = 5,
            rmssd = 10.0,
            sdhr = 5.0,   // HR tidak stabil = stress
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.STRESS, result.mode)
    }

    // --- Confidence & Reason ---

    @Test
    fun `confidence - always within valid range 0 to 1 for all modes`() {
        val cases = listOf(
            classifier.classifyInternal(120, 120, 35.0, 99.0, 20f, 2f) to "EXERCISE high signals",
            classifier.classifyInternal(101, 100, 35.0, 99.0, 25f, 2.5f) to "EXERCISE boundary",
            classifier.classifyInternal(80, 10, 12.0, 99.0, 2f, 0.1f) to "STRESS typical",
            classifier.classifyInternal(70, 5, 19.9, 99.0, 1f, 0.1f) to "STRESS boundary",
            classifier.classifyInternal(60, 5, 55.0, 99.0, 1f, 0.05f) to "RELAX high RMSSD",
            classifier.classifyInternal(null, 0, 5.0, 99.0, 20f, 3f) to "RELAX null HR",
            classifier.classifyInternal(100, 150, 35.0, 99.0, 20f, 2f) to "RELAX HR boundary",
        )
        cases.forEach { (result, label) ->
            assertTrue(
                "$label — confidence ${result.confidence} out of [0,1]",
                result.confidence in 0f..1f,
            )
        }
    }

    @Test
    fun `reason - contains the mode name as prefix`() {
        val exercise = classifier.classifyInternal(120, 120, 35.0, 99.0, 20f, 2f)
        val stress = classifier.classifyInternal(80, 10, 12.0, 99.0, 2f, 0.1f)
        val relax = classifier.classifyInternal(60, 5, 55.0, 99.0, 1f, 0.05f)

        assertTrue("Expected EXERCISE in reason", exercise.reason.startsWith("EXERCISE"))
        assertTrue("Expected STRESS in reason", stress.reason.startsWith("STRESS"))
        assertTrue("Expected RELAX in reason", relax.reason.startsWith("RELAX"))
    }
}
