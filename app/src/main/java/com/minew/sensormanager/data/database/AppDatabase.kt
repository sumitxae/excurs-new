package com.minew.sensormanager.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.minew.sensormanager.data.database.dao.DeviceDao
import com.minew.sensormanager.data.database.dao.SensorDataDao
import com.minew.sensormanager.data.database.dao.HistoryDao
import com.minew.sensormanager.data.database.entities.DeviceEntity
import com.minew.sensormanager.data.database.entities.SensorDataEntity
import com.minew.sensormanager.data.database.entities.HistoryDataEntity

@Database(
    entities = [
        DeviceEntity::class,
        SensorDataEntity::class,
        HistoryDataEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun deviceDao(): DeviceDao
    abstract fun sensorDataDao(): SensorDataDao
    abstract fun historyDao(): HistoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "minew_sensor_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
