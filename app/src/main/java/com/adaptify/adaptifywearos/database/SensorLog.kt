package com.adaptify.adaptifywearos.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_log")
data class SensorLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val heartRate: Int,
    val steps: Int,
    val accelerometerMagnitude: Float,
    val gyroscopeMagnitude: Float,
    val activityMode: String,
    val activityConfidence: Float,
)
