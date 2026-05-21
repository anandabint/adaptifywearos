package com.adaptify.adaptifywearos.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorLogDao {

    @Insert
    suspend fun insert(log: SensorLog)

    @Query("SELECT * FROM sensor_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SensorLog>

    @Query("SELECT * FROM sensor_log WHERE timestamp >= :startTime")
    suspend fun getAfter(startTime: Long): List<SensorLog>

    @Query("DELETE FROM sensor_log WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)
}
