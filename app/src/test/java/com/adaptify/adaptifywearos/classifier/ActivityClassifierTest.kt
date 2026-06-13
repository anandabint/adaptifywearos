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

    // --- HIGH_ACTIVITY ---

    @Test
    fun `high activity - high HR and high step count returns HIGH_ACTIVITY`() {
        // HIGH_ACTIVITY entry is debounced: requires 3 consecutive AND-condition matches.
        classifier.resetDebounceState()
        repeat(2) {
            classifier.classifyInternal(120, 120, 10f, 0.5f)
        }
        val result = classifier.classifyInternal(
            heartRateBpm = 120,
            stepsPerMinute = 120,
            accelMagnitudeMs2 = 10f,
            gyroMagnitudeRads = 0.5f,
        )
        assertEquals(ActivityMode.HIGH_ACTIVITY, result.mode)
    }

    @Test
    fun `high activity - high HR and accel above 1_5g but low steps returns RELAX`() {
        // AND logic: accel above 24.53 m/s² (1.5g net + 1g gravity) no longer promotes to
        // HIGH_ACTIVITY on its own. HIGH_ACTIVITY now requires HR>100 AND steps>=100/min.
        // steps=20 is sedentary (<30/min), so the result falls through to RELAX.
        val result = classifier.classifyInternal(
            heartRateBpm = 110,
            stepsPerMinute = 20,
            accelMagnitudeMs2 = 26f,
            gyroMagnitudeRads = 0.2f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `high activity - high HR and gyro above 2 rad_s but low steps returns RELAX`() {
        // AND logic: gyro above 2 rad/s no longer promotes to HIGH_ACTIVITY on its own.
        // HIGH_ACTIVITY now requires HR>100 AND steps>=100/min. steps=20 is sedentary
        // (<30/min), so the result is RELAX.
        val result = classifier.classifyInternal(
            heartRateBpm = 105,
            stepsPerMinute = 20,
            accelMagnitudeMs2 = 10f,
            gyroMagnitudeRads = 3.0f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `high activity - HR exactly 100 BPM with no movement returns RELAX`() {
        // Threshold is strictly > 100; with no movement signals above threshold, falls to RELAX
        val result = classifier.classifyInternal(
            heartRateBpm = 100,
            stepsPerMinute = 10,
            accelMagnitudeMs2 = 5f,
            gyroMagnitudeRads = 0.5f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `high activity - HR just above 100 BPM with 100 steps_min returns HIGH_ACTIVITY`() {
        // HIGH_ACTIVITY entry is debounced: requires 3 consecutive AND-condition matches.
        classifier.resetDebounceState()
        repeat(2) {
            classifier.classifyInternal(101, 100, 8f, 0.3f)
        }
        val result = classifier.classifyInternal(
            heartRateBpm = 101,
            stepsPerMinute = 100,
            accelMagnitudeMs2 = 8f,
            gyroMagnitudeRads = 0.3f,
        )
        assertEquals(ActivityMode.HIGH_ACTIVITY, result.mode)
    }

    @Test
    fun `high activity - high HR but all movement signals below threshold falls to LOW_ACTIVITY`() {
        // HR=110 but steps=45 (in light range 30-99), accel=5 m/s², gyro=0.2 (below thresholds)
        val result = classifier.classifyInternal(
            heartRateBpm = 110,
            stepsPerMinute = 45,
            accelMagnitudeMs2 = 5f,
            gyroMagnitudeRads = 0.2f,
        )
        assertEquals(ActivityMode.LOW_ACTIVITY, result.mode)
    }

    // --- LOW_ACTIVITY ---

    @Test
    fun `low activity - steps in light range (30-99 per min) returns LOW_ACTIVITY`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 85,
            stepsPerMinute = 50,
            accelMagnitudeMs2 = 3f,
            gyroMagnitudeRads = 0.3f,
        )
        assertEquals(ActivityMode.LOW_ACTIVITY, result.mode)
    }

    @Test
    fun `low activity - steps exactly at 30 per min returns LOW_ACTIVITY`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 70,
            stepsPerMinute = 30,
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.LOW_ACTIVITY, result.mode)
    }

    @Test
    fun `low activity - steps just below 100 per min returns LOW_ACTIVITY`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 95,
            stepsPerMinute = 99,
            accelMagnitudeMs2 = 5f,
            gyroMagnitudeRads = 0.5f,
        )
        assertEquals(ActivityMode.LOW_ACTIVITY, result.mode)
    }

    @Test
    fun `low activity - low HR with light step cadence returns LOW_ACTIVITY not RELAX`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 65,
            stepsPerMinute = 40,
            accelMagnitudeMs2 = 2f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.LOW_ACTIVITY, result.mode)
    }

    // --- RELAX ---

    @Test
    fun `relax - steps below 30 per min returns RELAX`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 65,
            stepsPerMinute = 10,
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.05f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `relax - zero steps and low HR returns RELAX`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 60,
            stepsPerMinute = 0,
            accelMagnitudeMs2 = 0.5f,
            gyroMagnitudeRads = 0.02f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `relax - null HR with steps below 30 returns RELAX as default fallback`() {
        val result = classifier.classifyInternal(
            heartRateBpm = null,
            stepsPerMinute = 5,
            accelMagnitudeMs2 = 20f,
            gyroMagnitudeRads = 3f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    @Test
    fun `relax - steps just below 30 boundary returns RELAX`() {
        val result = classifier.classifyInternal(
            heartRateBpm = 75,
            stepsPerMinute = 29,
            accelMagnitudeMs2 = 1f,
            gyroMagnitudeRads = 0.1f,
        )
        assertEquals(ActivityMode.RELAX, result.mode)
    }

    // --- Confidence & Reason ---

    @Test
    fun `confidence - always within valid range 0 to 1 for all modes`() {
        val cases = listOf(
            classifier.classifyInternal(120, 120, 20f, 2f) to "HIGH_ACTIVITY high signals",
            classifier.classifyInternal(101, 100, 25f, 2.5f) to "HIGH_ACTIVITY boundary",
            classifier.classifyInternal(85, 50, 3f, 0.3f) to "LOW_ACTIVITY typical",
            classifier.classifyInternal(70, 30, 1f, 0.1f) to "LOW_ACTIVITY boundary",
            classifier.classifyInternal(60, 5, 1f, 0.05f) to "RELAX typical",
            classifier.classifyInternal(null, 0, 0.5f, 0.02f) to "RELAX null HR",
            classifier.classifyInternal(100, 150, 20f, 2f) to "RELAX HR boundary",
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
        // HIGH_ACTIVITY entry is debounced: requires 3 consecutive AND-condition matches.
        classifier.resetDebounceState()
        repeat(2) { classifier.classifyInternal(120, 120, 20f, 2f) }
        val highActivity = classifier.classifyInternal(120, 120, 20f, 2f)
        val lowActivity = classifier.classifyInternal(85, 50, 3f, 0.3f)
        val relax = classifier.classifyInternal(60, 5, 1f, 0.05f)

        assertTrue("Expected HIGH_ACTIVITY in reason", highActivity.reason.startsWith("HIGH_ACTIVITY"))
        assertTrue("Expected LOW_ACTIVITY in reason", lowActivity.reason.startsWith("LOW_ACTIVITY"))
        assertTrue("Expected RELAX in reason", relax.reason.startsWith("RELAX"))
    }

    // --- HIGH_ACTIVITY entry debounce ---

    @Test
    fun `debounce - single reading meeting AND condition returns LOW_ACTIVITY not HIGH_ACTIVITY`() {
        classifier.resetDebounceState()
        val result = classifier.classifyInternal(
            heartRateBpm = 105,
            stepsPerMinute = 105,
            accelMagnitudeMs2 = 10f,
            gyroMagnitudeRads = 0.5f,
        )
        assertEquals(ActivityMode.LOW_ACTIVITY, result.mode)
    }

    @Test
    fun `debounce - three consecutive readings meeting AND condition returns HIGH_ACTIVITY`() {
        classifier.resetDebounceState()
        repeat(2) {
            classifier.classifyInternal(105, 105, 10f, 0.5f)
        }
        val result = classifier.classifyInternal(105, 105, 10f, 0.5f)
        assertEquals(ActivityMode.HIGH_ACTIVITY, result.mode)
    }

    @Test
    fun `debounce - exiting HIGH_ACTIVITY is instant, no debounce on exit`() {
        classifier.resetDebounceState()
        repeat(3) {
            classifier.classifyInternal(105, 105, 10f, 0.5f)
        }
        // Now drop steps below threshold — should immediately leave HIGH_ACTIVITY
        val result = classifier.classifyInternal(105, 50, 10f, 0.5f)
        assertEquals(ActivityMode.LOW_ACTIVITY, result.mode)
    }

    @Test
    fun `debounce - brief single-reading spike during sustained walking does not trigger HIGH_ACTIVITY`() {
        classifier.resetDebounceState()
        // Sustained walking: steps in light range, HR fluctuating around 100
        classifier.classifyInternal(96, 50, 5f, 0.3f)
        classifier.classifyInternal(98, 50, 5f, 0.3f)
        // Single transient spike: HR briefly >100 AND steps briefly >=100 (1 reading only)
        val spikeResult = classifier.classifyInternal(105, 102, 5f, 0.3f)
        assertEquals(ActivityMode.LOW_ACTIVITY, spikeResult.mode)
        // Returns to normal walking — consecutive count resets
        val afterResult = classifier.classifyInternal(96, 50, 5f, 0.3f)
        assertEquals(ActivityMode.LOW_ACTIVITY, afterResult.mode)
    }
}
