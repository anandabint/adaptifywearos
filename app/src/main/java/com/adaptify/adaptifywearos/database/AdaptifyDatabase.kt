package com.adaptify.adaptifywearos.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SensorLog::class], version = 2)
abstract class AdaptifyDatabase : RoomDatabase() {

    abstract fun sensorLogDao(): SensorLogDao

    companion object {
        @Volatile private var INSTANCE: AdaptifyDatabase? = null

        fun getInstance(context: Context): AdaptifyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdaptifyDatabase::class.java,
                    "adaptify_db",
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
