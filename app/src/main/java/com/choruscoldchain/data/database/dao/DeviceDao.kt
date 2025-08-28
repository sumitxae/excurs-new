package com.choruscoldchain.data.database.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.choruscoldchain.data.database.entities.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    
    @Query("SELECT * FROM devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>
    
    @Query("SELECT * FROM devices WHERE macAddress = :macAddress")
    suspend fun getDevice(macAddress: String): DeviceEntity?
    
    @Query("SELECT * FROM devices WHERE macAddress = :macAddress")
    fun getDeviceLiveData(macAddress: String): LiveData<DeviceEntity?>
    
    @Query("SELECT * FROM devices WHERE isConnected = 1")
    fun getConnectedDevices(): Flow<List<DeviceEntity>>
    
    @Query("SELECT * FROM devices WHERE alertsEnabled = 1")
    suspend fun getDevicesWithAlertsEnabled(): List<DeviceEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<DeviceEntity>)
    
    @Update
    suspend fun updateDevice(device: DeviceEntity)
    
    @Query("UPDATE devices SET isConnected = :isConnected, connectionState = :connectionState WHERE macAddress = :macAddress")
    suspend fun updateConnectionState(macAddress: String, isConnected: Boolean, connectionState: String)
    
    @Query("UPDATE devices SET rssi = :rssi, lastSeen = :lastSeen WHERE macAddress = :macAddress")
    suspend fun updateDeviceRssi(macAddress: String, rssi: Int, lastSeen: Long)
    
    @Delete
    suspend fun deleteDevice(device: DeviceEntity)
    
    @Query("DELETE FROM devices WHERE macAddress = :macAddress")
    suspend fun deleteDeviceByMacAddress(macAddress: String)
    
    @Query("DELETE FROM devices")
    suspend fun deleteAllDevices()
}
