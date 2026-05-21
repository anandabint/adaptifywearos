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
            stressIndex = 40,
            accelMagnitudeMs2 = 10f,
            gyroMagnitudeRads = 0.5f,
        )
        assertEquals(ActivityMode.EXERCISE, result.mode)
    }

    @Test
    fun `exercise - high HR and accel above 1_5g returns EXERCISE`() {
        // 16 m/s² ≈ 1.63g > 1.5g threshold
        val result = classifier.classifyInternal(
            heartRateBpm = 110,
            stepsPerMinute = 20,
            stressIndex = 40,
            accelMagnitudeMs2 = 16f,
            gyroMagnitudeRads = 0.2f,
        )
        assertEquals(ActivityMode.EXERCISE, result.mode)
    }

    @Test
    fun `exercise - high HR and gyro above 1 rad_s returns EXERCISE`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 105,
            stepsPerMinute = 20,
            stressIndex = 40,
            accelMagnitudeMs2 = 10f,
            gyroMagnitudeRads = 2.0f,
        )
        assertEquals(ActivityMode.EXERCISE, result.mode)
    }

    @Test
    fun `exercise - takes priority over STRESS even with high stress index`() {
        // HR=130, steps=150 → EXERCISE must win over stressIndex=80
        val result = classifier.classifyInternal(
            heartRateBpm = 130,
            stepsPerMinute = 150,
            stressIndex = 80,
            accelMagnitudeMs2 = 20f,
            gyroMagnitudeRads = 2.5f,
        )
        assertEquals(ActivityMode.EXERCISE, result.mode)
    }

    @Test
    fun `exercise - HR exactly 100 BPM is NOT exercise (boundary condition)`() {
        // Threshold is strictly > 100
        val result = classifier.classifyInternal(
            heartRateBpm = 100,
            stepsPerMinute = 150,
            stressIndex = 40,
            accelMagnitudeMs2 = 20f,
            gyroMagnitudeRads = 2.0f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `exercise - HR just above 101 BPM with 100 steps_min returns EXERCISE`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 101,
            stepsPerMinute = 100,
            stressIndex = 40,
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
            stressIndex = 40,
            accelMagnitudeMs2 = 5f,
            gyroMagnitudeRads = 0.2f,
        )
        // HR=110 is outside STRESS range 60-100, so falls through to RELAX
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    // --- STRESS ---

    @Test
    fun `stress - mid HR, low steps, high stress index returns STRESS`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 80,
            stepsPerMinute = 10,
            stressIndex = 75,
            accelMagnitudeMs2 = 2f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.STRESS, result.mode)
    }

    @Test
    fun `stress - exact boundary stressIndex 60 returns STRESS`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 70,
            stepsPerMinute = 5,
            stressIndex = 60,
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.STRESS, result.mode)
    }

    @Test
    fun `stress - stressIndex 59 falls below threshold returns RELAX`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 70,
            stepsPerMinute = 5,
            stressIndex = 59,
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `stress - HR 110 is outside 60-100 range so falls to RELAX despite high stress index`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 110,
            stepsPerMinute = 5,
            stressIndex = 80,
            accelMagnitudeMs2 = 2f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    // --- RELAX ---

    @Test
    fun `relax - low HR, low steps, low stress index returns RELAX`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 60,
            stepsPerMinute = 5,
            stressIndex = 10,
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
            stressIndex = 90,
            accelMagnitudeMs2 = 20f,
            gyroMagnitudeRads = 3f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `relax - sedentary HR with moderate stress but low stress index returns RELAX`() {
        // HR in range, steps low, but stressIndex < 60 → not enough for STRESS
        val result = classifier.classifyInternal(
            heartRateBpm = 80,
            stepsPerMinute = 10,
            stressIndex = 30,
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    // --- Confidence & Reason ---

    @Test
    fun `confidence - always within valid range 0 to 1 for all modes`() {
        val cases = listOf(
            classifier.classifyInternal(120, 120, 40, 20f, 2f) to "EXERCISE high signals",
            classifier.classifyInternal(101, 100, 40, 14.73f, 1.01f) to "EXERCISE boundary",
            classifier.classifyInternal(80, 10, 75, 2f, 0.1f) to "STRESS typical",
            classifier.classifyInternal(70, 5, 60, 1f, 0.1f) to "STRESS boundary",
            classifier.classifyInternal(60, 5, 10, 1f, 0.05f) to "RELAX low stress",
            classifier.classifyInternal(null, 0, 90, 20f, 3f) to "RELAX null HR",
            classifier.classifyInternal(100, 150, 40, 20f, 2f) to "RELAX HR boundary",
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
        val exercise = classifier.classifyInternal(120, 120, 40, 20f, 2f)
        val stress = classifier.classifyInternal(80, 10, 75, 2f, 0.1f)
        val relax = classifier.classifyInternal(60, 5, 10, 1f, 0.05f)

        assertTrue("Expected EXERCISE in reason", exercise.reason.startsWith("EXERCISE"))
        assertTrue("Expected STRESS in reason", stress.reason.startsWith("STRESS"))
        assertTrue("Expected RELAX in reason", relax.reason.startsWith("RELAX"))
    }
}
