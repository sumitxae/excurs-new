package com.choruscoldchain.di

import android.content.Context
import androidx.room.Room
import com.choruscoldchain.data.database.AppDatabase
import com.choruscoldchain.data.database.dao.DeviceDao
import com.choruscoldchain.data.database.dao.SensorDataDao
import com.choruscoldchain.data.database.dao.HistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "minew_sensor_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    fun provideDeviceDao(database: AppDatabase): DeviceDao {
        return database.deviceDao()
    }
    
    @Provides
    fun provideSensorDataDao(database: AppDatabase): SensorDataDao {
        return database.sensorDataDao()
    }
    
    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }
}
