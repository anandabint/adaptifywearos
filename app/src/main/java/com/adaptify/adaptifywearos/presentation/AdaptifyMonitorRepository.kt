package com.adaptify.adaptifywearos.presentation

import com.adaptify.adaptifywearos.classifier.ActivityReading
import com.adaptify.adaptifywearos.health.HeartRateState
import com.adaptify.adaptifywearos.sensor.SensorSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide singleton that lets the foreground service publish the latest
 * sensor/heart-rate/stress snapshot and lets MainActivity's Compose UI observe
 * it. The service owns the sensor subscriptions; the activity only reads.
 */
object AdaptifyMonitorRepository {

    private val _sensorSnapshot = MutableStateFlow(SensorSnapshot())
    val sensorSnapshot: StateFlow<SensorSnapshot> = _sensorSnapshot.asStateFlow()

    private val _heartRateState = MutableStateFlow(HeartRateState())
    val heartRateState: StateFlow<HeartRateState> = _heartRateState.asStateFlow()

    private val _activityReading = MutableStateFlow<ActivityReading?>(null)
    val activityReading: StateFlow<ActivityReading?> = _activityReading.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    fun publish(
        sensor: SensorSnapshot,
        heartRate: HeartRateState,
        activity: ActivityReading,
    ) {
        _sensorSnapshot.value = sensor
        _heartRateState.value = heartRate
        _activityReading.value = activity
    }

    fun markRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun reset() {
        _sensorSnapshot.value = SensorSnapshot()
        _heartRateState.value = HeartRateState()
        _activityReading.value = null
    }
}
