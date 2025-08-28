package com.choruscoldchain.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.choruscoldchain.data.database.dao.DeviceDao
import com.choruscoldchain.data.database.dao.SensorDataDao
import com.choruscoldchain.data.database.entities.DeviceEntity
import com.choruscoldchain.data.database.entities.SensorDataEntity
import com.choruscoldchain.data.models.DeviceInfo
import com.choruscoldchain.data.models.SensorData
import com.choruscoldchain.data.models.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao,
    private val sensorDataDao: SensorDataDao
) {
    
    fun getAllDevices(): Flow<List<DeviceInfo>> = deviceDao.getAllDevices().map { entities ->
        entities.map { it.toDeviceInfo() }
    }
    
    fun getDevice(macAddress: String): LiveData<DeviceInfo?> = 
        deviceDao.getDeviceLiveData(macAddress).map { it?.toDeviceInfo() }
    
    fun getConnectedDevices(): Flow<List<DeviceInfo>> = 
        deviceDao.getConnectedDevices().map { entities ->
            entities.map { it.toDeviceInfo() }
        }
    
    suspend fun insertOrUpdateDevice(deviceInfo: DeviceInfo) {
        deviceDao.insertDevice(deviceInfo.toEntity())
    }
    
    suspend fun updateConnectionState(macAddress: String, connectionState: ConnectionState) {
        val isConnected = connectionState == ConnectionState.READY
        deviceDao.updateConnectionState(macAddress, isConnected, connectionState.name)
    }
    
    suspend fun updateDeviceRssi(macAddress: String, rssi: Int) {
        deviceDao.updateDeviceRssi(macAddress, rssi, System.currentTimeMillis())
    }
    
    suspend fun deleteDevice(macAddress: String) {
        deviceDao.deleteDeviceByMacAddress(macAddress)
    }
    
    suspend fun insertSensorData(sensorData: SensorData) {
        // Ensure parent device exists to satisfy foreign key constraint
        val mac = sensorData.macAddress
        val existing = deviceDao.getDevice(mac)
        if (existing == null) {
            val placeholder = DeviceEntity(
                macAddress = mac,
                name = "MST03-${mac.takeLast(4)}",
                rssi = 0,
                battery = sensorData.battery,
                firmwareVersion = "Unknown",
                isConnected = false,
                lastSeen = System.currentTimeMillis(),
                connectionState = ConnectionState.DISCONNECTED.name,
                alertsEnabled = true,
                groupId = null,
                secretKey = null
            )
            deviceDao.insertDevice(placeholder)
        }
        sensorDataDao.insertSensorData(sensorData.toEntity())
    }
    
    fun getLatestSensorData(macAddress: String): LiveData<SensorData?> =
        sensorDataDao.getLatestSensorData(macAddress).map { it?.toSensorData() }
    
    fun getSensorDataHistory(macAddress: String, startTime: Long, endTime: Long): Flow<List<SensorData>> =
        sensorDataDao.getSensorDataInRange(macAddress, startTime, endTime).map { entities ->
            entities.map { it.toSensorData() }
        }
    
    suspend fun getDevicesWithAlertsEnabled(): List<DeviceInfo> =
        deviceDao.getDevicesWithAlertsEnabled().map { it.toDeviceInfo() }
}

// Extension functions for entity conversion
private fun DeviceEntity.toDeviceInfo(): DeviceInfo = DeviceInfo(
    macAddress = macAddress,
    name = name,
    rssi = rssi,
    battery = battery,
    firmwareVersion = firmwareVersion,
    isConnected = isConnected,
    lastSeen = lastSeen,
    connectionState = ConnectionState.valueOf(connectionState),
    alertsEnabled = alertsEnabled,
    groupId = groupId
)

private fun DeviceInfo.toEntity(): DeviceEntity = DeviceEntity(
    macAddress = macAddress,
    name = name,
    rssi = rssi,
    battery = battery,
    firmwareVersion = firmwareVersion,
    isConnected = isConnected,
    lastSeen = lastSeen,
    connectionState = connectionState.name,
    alertsEnabled = alertsEnabled,
    groupId = groupId
)

private fun SensorDataEntity.toSensorData(): SensorData = SensorData(
    macAddress = deviceMacAddress,
    temperature = temperature,
    lightIntensity = lightIntensity,
    battery = battery,
    temperatureUpperLimit1AlarmMark = temperatureUpperLimit1AlarmMark,
    temperatureLowerLimit1AlarmMark = temperatureLowerLimit1AlarmMark,
    temperatureUpperLimit2AlarmMark = temperatureUpperLimit2AlarmMark,
    temperatureLowerLimit2AlarmMark = temperatureLowerLimit2AlarmMark,
    lightIntensityUpperLimitAlarmMark = lightIntensityUpperLimitAlarmMark,
    lightIntensityLowerLimitAlarmMark = lightIntensityLowerLimitAlarmMark,
    lightEventTimestamp = lightEventTimestamp,
    tempEventTimestamp = tempEventTimestamp,
    timestamp = timestamp
)

private fun SensorData.toEntity(): SensorDataEntity = SensorDataEntity(
    id = "${macAddress}_$timestamp",
    deviceMacAddress = macAddress,
    temperature = temperature,
    lightIntensity = lightIntensity,
    battery = battery,
    temperatureUpperLimit1AlarmMark = temperatureUpperLimit1AlarmMark,
    temperatureLowerLimit1AlarmMark = temperatureLowerLimit1AlarmMark,
    temperatureUpperLimit2AlarmMark = temperatureUpperLimit2AlarmMark,
    temperatureLowerLimit2AlarmMark = temperatureLowerLimit2AlarmMark,
    lightIntensityUpperLimitAlarmMark = lightIntensityUpperLimitAlarmMark,
    lightIntensityLowerLimitAlarmMark = lightIntensityLowerLimitAlarmMark,
    lightEventTimestamp = lightEventTimestamp,
    tempEventTimestamp = tempEventTimestamp,
    timestamp = timestamp
)
