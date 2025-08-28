package com.choruscoldchain.data.database.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.choruscoldchain.data.database.entities.SensorDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDataDao {
    
    @Query("SELECT * FROM sensor_data WHERE deviceMacAddress = :macAddress ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSensorData(macAddress: String): LiveData<SensorDataEntity?>
    
    @Query("SELECT * FROM sensor_data WHERE deviceMacAddress = :macAddress ORDER BY timestamp DESC")
    fun getSensorDataForDevice(macAddress: String): Flow<List<SensorDataEntity>>
    
    @Query("SELECT * FROM sensor_data WHERE deviceMacAddress = :macAddress AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getSensorDataInRange(macAddress: String, startTime: Long, endTime: Long): Flow<List<SensorDataEntity>>
    
    @Query("SELECT * FROM sensor_data WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getAllSensorDataInRange(startTime: Long, endTime: Long): Flow<List<SensorDataEntity>>
    
    @Query("SELECT * FROM sensor_data WHERE temperatureUpperLimit1AlarmMark = 1 OR temperatureLowerLimit1AlarmMark = 1 OR temperatureUpperLimit2AlarmMark = 1 OR temperatureLowerLimit2AlarmMark = 1 OR lightIntensityUpperLimitAlarmMark = 1 OR lightIntensityLowerLimitAlarmMark = 1 ORDER BY timestamp DESC")
    fun getAlarmData(): Flow<List<SensorDataEntity>>
    
    @Query("SELECT * FROM sensor_data WHERE deviceMacAddress = :macAddress AND (temperatureUpperLimit1AlarmMark = 1 OR temperatureLowerLimit1AlarmMark = 1 OR temperatureUpperLimit2AlarmMark = 1 OR temperatureLowerLimit2AlarmMark = 1 OR lightIntensityUpperLimitAlarmMark = 1 OR lightIntensityLowerLimitAlarmMark = 1) ORDER BY timestamp DESC")
    fun getAlarmDataForDevice(macAddress: String): Flow<List<SensorDataEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSensorData(sensorData: SensorDataEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSensorDataList(sensorDataList: List<SensorDataEntity>)
    
    @Update
    suspend fun updateSensorData(sensorData: SensorDataEntity)
    
    @Delete
    suspend fun deleteSensorData(sensorData: SensorDataEntity)
    
    @Query("DELETE FROM sensor_data WHERE deviceMacAddress = :macAddress")
    suspend fun deleteSensorDataForDevice(macAddress: String)
    
    @Query("DELETE FROM sensor_data WHERE timestamp < :timestamp")
    suspend fun deleteOldSensorData(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM sensor_data WHERE deviceMacAddress = :macAddress")
    suspend fun getSensorDataCount(macAddress: String): Int
}
