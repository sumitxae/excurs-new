package com.choruscoldchain.data.database.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.choruscoldchain.data.database.entities.HistoryDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    
    @Query("SELECT * FROM history_data WHERE deviceMacAddress = :macAddress ORDER BY timestamp DESC")
    fun getHistoryForDevice(macAddress: String): Flow<List<HistoryDataEntity>>
    
    @Query("SELECT * FROM history_data WHERE deviceMacAddress = :macAddress AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getHistoryInRange(macAddress: String, startTime: Long, endTime: Long): Flow<List<HistoryDataEntity>>
    
    @Query("SELECT * FROM history_data WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getAllHistoryInRange(startTime: Long, endTime: Long): Flow<List<HistoryDataEntity>>
    
    @Query("SELECT * FROM history_data WHERE dataQuality = :quality ORDER BY timestamp DESC")
    fun getHistoryByQuality(quality: String): Flow<List<HistoryDataEntity>>
    
    @Query("SELECT * FROM history_data WHERE deviceMacAddress = :macAddress AND dataQuality = :quality ORDER BY timestamp DESC")
    fun getHistoryByQualityForDevice(macAddress: String, quality: String): Flow<List<HistoryDataEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryData(historyData: HistoryDataEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryDataList(historyDataList: List<HistoryDataEntity>)
    
    @Update
    suspend fun updateHistoryData(historyData: HistoryDataEntity)
    
    @Delete
    suspend fun deleteHistoryData(historyData: HistoryDataEntity)
    
    @Query("DELETE FROM history_data WHERE deviceMacAddress = :macAddress")
    suspend fun deleteHistoryForDevice(macAddress: String)
    
    @Query("DELETE FROM history_data WHERE timestamp < :timestamp")
    suspend fun deleteOldHistoryData(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM history_data WHERE deviceMacAddress = :macAddress")
    suspend fun getHistoryCount(macAddress: String): Int
    
    @Query("SELECT AVG(temperature) FROM history_data WHERE deviceMacAddress = :macAddress AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getAverageTemperature(macAddress: String, startTime: Long, endTime: Long): Float?
    
    @Query("SELECT AVG(lightIntensity) FROM history_data WHERE deviceMacAddress = :macAddress AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getAverageLightIntensity(macAddress: String, startTime: Long, endTime: Long): Float?
}
