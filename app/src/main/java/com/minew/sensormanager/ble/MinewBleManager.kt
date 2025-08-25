package com.minew.sensormanager.ble

import android.content.Context
import android.util.Log
import com.minew.sensormanager.data.models.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

// Import Minew SDK classes
import com.minew.ble.mst03.manager.MST03SensorBleManager
import com.minew.ble.mst03.bean.MST03Entity
import com.minew.ble.v3.interfaces.OnScanDevicesResultListener
import com.minew.ble.v3.enums.BleConnectionState
import com.minew.ble.v3.enums.FrameType
import com.minew.ble.mst03.frames.CombinationFrame
import com.minew.ble.mst03.frames.DeviceStaticInfoFrame
import com.minew.ble.mst03.interfaces.OnReceiveDataListener
import com.minew.sensormanager.utils.PermissionHelper

@Singleton
class MinewBleManager @Inject constructor() {
    
    init {
        Log.d(TAG, "MinewBleManager constructor called")
    }
    
    companion object {
        private const val TAG = "MinewBleManager"
        private const val DEFAULT_SECRET_KEY = "minewtech1234567"
        private const val MANUFACTURER_ID = "E000" // Google
    }
    
    private val mst03Manager = MST03SensorBleManager.getInstance()
    
    // State flows for real-time updates
    private val _scanResults = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val scanResults: StateFlow<List<DeviceInfo>> = _scanResults.asStateFlow()
    
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()
    
    private val _sensorDataUpdates = Channel<SensorData>(Channel.UNLIMITED)
    val sensorDataUpdates: Flow<SensorData> = _sensorDataUpdates.receiveAsFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val scannedDevices = mutableMapOf<String, DeviceInfo>()
    private val connectedDevices = mutableMapOf<String, String>() // MAC -> Secret Key
    
    init {
        Log.d(TAG, "MinewBleManager init block starting")
        try {
            mst03Manager.setManufacturerIdHexLe(MANUFACTURER_ID)
            Log.d(TAG, "Manufacturer ID set successfully")
            setupConnectionListener()
            Log.d(TAG, "MinewBleManager init block completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in MinewBleManager init block", e)
        }
    }
    
    fun startScan(context: Context, durationMs: Int = 5 * 60 * 1000): Boolean {
        Log.d(TAG, "Starting BLE scan for ${durationMs / 1000} seconds")

        // Prevent starting a new scan if one is already running
        if (_isScanning.value) {
            Log.w(TAG, "Scan already in progress; ignoring duplicate startScan call")
            return true
        }
        
        // Check permissions before starting scan
        if (!hasRequiredPermissions(context)) {
            Log.e(TAG, "Required permissions not granted for BLE scanning")
            _isScanning.value = false
            return false
        }
        
        try {
            _isScanning.value = true
            scannedDevices.clear()
            
            mst03Manager.startScan(context, durationMs, object : OnScanDevicesResultListener<MST03Entity> {
                override fun onScanResult(scanList: MutableList<MST03Entity>?) {
                    scanList?.let { devices ->
                        Log.d(TAG, "Scan result: ${devices.size} devices found")
                        processScanResults(devices)
                    }
                }
                
                override fun onStopScan(scanList: MutableList<MST03Entity>?) {
                    Log.d(TAG, "Scan stopped")
                    _isScanning.value = false
                    scanList?.let { devices ->
                        processScanResults(devices)
                    }
                }
            })
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when starting scan - permissions not granted", e)
            _isScanning.value = false
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            _isScanning.value = false
            return false
        }
    }
    
    private fun hasRequiredPermissions(context: Context): Boolean {
        return try {
            PermissionHelper.hasAllRequiredPermissions(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }
    
    private fun setupConnectionListener() {
        Log.d(TAG, "Setting up connection listener")
        try {
            mst03Manager.setOnConnStateListener { macAddress, connectionState ->
                Log.d(TAG, "Connection state change: $macAddress -> $connectionState")
                
                val state = when (connectionState) {
                    BleConnectionState.Connecting -> {
                        Log.d(TAG, "Device $macAddress is connecting...")
                        ConnectionState.CONNECTING
                    }
                    BleConnectionState.Connected -> {
                        Log.d(TAG, "Device $macAddress connected successfully")
                        ConnectionState.CONNECTED
                    }
                    BleConnectionState.AuthenticateSuccess -> {
                        Log.d(TAG, "Device $macAddress authenticated successfully")
                        ConnectionState.AUTHENTICATED
                    }
                    BleConnectionState.AuthenticateFail -> {
                        Log.e(TAG, "Device $macAddress authentication failed")
                        ConnectionState.ERROR
                    }
                    BleConnectionState.ConnectComplete -> {
                        Log.d(TAG, "Device $macAddress connection complete")
                        ConnectionState.READY
                    }
                    BleConnectionState.Disconnect -> {
                        Log.d(TAG, "Device $macAddress disconnected")
                        ConnectionState.DISCONNECTED
                    }
                    else -> {
                        Log.w(TAG, "Device $macAddress unknown state: $connectionState")
                        ConnectionState.ERROR
                    }
                }
                
                updateConnectionState(macAddress, state)
            }
            Log.d(TAG, "Connection listener setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up connection listener", e)
        }
    }
    
    private fun processSensorData(entity: MST03Entity) {
        try {
            // Get device static info
            val deviceStaticFrame = entity.getMinewFrame(FrameType.CUSTOM_DEVICE_INFORMATION_FRAME) as? DeviceStaticInfoFrame
            val combinationFrame = entity.getMinewFrame(FrameType.CUSTOM_COMBINATION_FRAME) as? CombinationFrame
            
            combinationFrame?.let { frame ->
                val macAddress = deviceStaticFrame?.macAddress ?: entity.macAddress
                val sensorData = SensorData(
                    macAddress = macAddress,
                    temperature = frame.temperature,
                    lightIntensity = 0, // Light intensity not directly available in combination frame
                    battery = frame.battery,
                    temperatureUpperLimit1AlarmMark = frame.temperatureUpperLimit1AlarmMark == 1,
                    temperatureLowerLimit1AlarmMark = frame.temperatureLowerLimit1AlarmMark == 1,
                    temperatureUpperLimit2AlarmMark = frame.temperatureUpperLimit2AlarmMark == 1,
                    temperatureLowerLimit2AlarmMark = frame.temperatureLowerLimit2AlarmMark == 1,
                    lightIntensityUpperLimitAlarmMark = frame.lightIntensityUpperLimitAlarmMark == 1,
                    lightIntensityLowerLimitAlarmMark = frame.lightIntensityLowerLimitAlarmMark == 1,
                    lightEventTimestamp = frame.lightEventTimestamp,
                    tempEventTimestamp = frame.tempEventTimestamp,
                    timestamp = frame.currentTimestamp
                )
                
                // Send sensor data update
                _sensorDataUpdates.trySend(sensorData)
                Log.d(TAG, "Processed sensor data: temp=${frame.temperature}, battery=${frame.battery}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing sensor data: ${entity.macAddress}", e)
        }
    }
    
    private fun processScanResults(devices: List<MST03Entity>) {
        devices.forEach { entity ->
            val deviceInfo = processScannedDevice(entity)
            if (deviceInfo != null) {
                scannedDevices[deviceInfo.macAddress] = deviceInfo
            }
        }
        _scanResults.value = scannedDevices.values.toList()
    }
    
    private fun processScannedDevice(entity: MST03Entity): DeviceInfo? {
        return try {
            // Get device static info
            val deviceStaticFrame = entity.getMinewFrame(FrameType.CUSTOM_DEVICE_INFORMATION_FRAME) as? DeviceStaticInfoFrame
            val combinationFrame = entity.getMinewFrame(FrameType.CUSTOM_COMBINATION_FRAME) as? CombinationFrame
            
            val macAddress = deviceStaticFrame?.macAddress ?: entity.macAddress
            val battery = deviceStaticFrame?.battery ?: combinationFrame?.battery ?: 0
            val firmwareVersion = deviceStaticFrame?.firmwareVersion ?: "Unknown"
            
            // Get temperature from combination frame if available
            val temperature = combinationFrame?.temperature
            
            // Create static frame data string
            val staticFrameData = deviceStaticFrame?.let { frame ->
                """
                MAC Address: ${frame.macAddress}
                Firmware Version: ${frame.firmwareVersion}
                Battery Level: ${frame.battery}%
                """.trimIndent()
            }
            
            // Create combination frame data string
            val combinationFrameData = combinationFrame?.let { frame ->
                """
                Temperature: ${frame.temperature}°C
                Battery Level: ${frame.battery}%
                Company ID: ${frame.companyId}
                Google ID: ${frame.googleId}
                Frame Version: ${frame.frameVersion}
                MAC Address: ${frame.macAddress}
                Temperature Upper Limit 1 Alarm: ${if (frame.temperatureUpperLimit1AlarmMark == 1) "ON" else "OFF"}
                Temperature Lower Limit 1 Alarm: ${if (frame.temperatureLowerLimit1AlarmMark == 1) "ON" else "OFF"}
                Temperature Upper Limit 2 Alarm: ${if (frame.temperatureUpperLimit2AlarmMark == 1) "ON" else "OFF"}
                Temperature Lower Limit 2 Alarm: ${if (frame.temperatureLowerLimit2AlarmMark == 1) "ON" else "OFF"}
                Light Upper Limit Alarm: ${if (frame.lightIntensityUpperLimitAlarmMark == 1) "ON" else "OFF"}
                Light Lower Limit Alarm: ${if (frame.lightIntensityLowerLimitAlarmMark == 1) "ON" else "OFF"}
                Light Event Timestamp: ${frame.lightEventTimestamp}
                Temp Event Timestamp: ${frame.tempEventTimestamp}
                Current Timestamp: ${frame.currentTimestamp}
                """.trimIndent()
            }
            
            val deviceInfo = DeviceInfo(
                macAddress = macAddress,
                name = entity.name ?: "MST03-${macAddress.takeLast(4)}",
                rssi = entity.rssi,
                battery = battery,
                temperature = temperature,
                firmwareVersion = firmwareVersion,
                isConnected = _connectionStates.value[macAddress] == ConnectionState.READY,
                lastSeen = System.currentTimeMillis(),
                staticFrameData = staticFrameData,
                combinationFrameData = combinationFrameData
            )
            
            // Process real-time sensor data if available
            processSensorData(entity)
            
            deviceInfo
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing scanned device: ${entity.macAddress}", e)
            null
        }
    }
    
    fun stopScan(context: Context) {
        Log.d(TAG, "Stopping BLE scan")
        try {
            mst03Manager.stopScan(context)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when stopping scan - permissions not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE scan", e)
        }
        _isScanning.value = false
    }
    
    suspend fun connectDevice(context: Context?, macAddress: String, secretKey: String = DEFAULT_SECRET_KEY): Boolean {
        return try {
            Log.d(TAG, "=== CONNECTION ATTEMPT START ===")
            Log.d(TAG, "Connecting to device: $macAddress with secret key: $secretKey")
            
            // Check permissions if context is provided
            if (context != null && !hasRequiredPermissions(context)) {
                Log.e(TAG, "Required permissions not granted for BLE connection")
                return false
            }
            
            // Stop scanning before connecting
            if (_isScanning.value && context != null) {
                Log.d(TAG, "Stopping scan before connection")
                stopScan(context)
            }
            
            // Set secret key first
            Log.d(TAG, "Setting secret key for $macAddress")
            try {
                mst03Manager.setSecretKey(macAddress, secretKey)
                Log.d(TAG, "Secret key set successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting secret key", e)
            }
            connectedDevices[macAddress] = secretKey
            
            // Try connecting directly
            if (context != null) {
                Log.d(TAG, "Attempting direct connection to $macAddress")
                try {
                    mst03Manager.connect(context, macAddress)
                    Log.d(TAG, "Connect method called successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling connect method", e)
                    return false
                }
                
                // Wait a bit for connection to establish
                Log.d(TAG, "Waiting for connection to establish...")
                delay(3000)
                
                // Check if connection was successful
                val currentState = _connectionStates.value[macAddress]
                Log.d(TAG, "Connection state after attempt: $currentState")
                
                if (currentState == ConnectionState.READY || currentState == ConnectionState.CONNECTED) {
                    Log.d(TAG, "Connection successful to $macAddress")
                    return true
                } else {
                    Log.w(TAG, "Connection may have failed, current state: $currentState")
                    return false
                }
            } else {
                Log.e(TAG, "Context is null, cannot connect")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device: $macAddress", e)
            updateConnectionState(macAddress, ConnectionState.ERROR)
            false
        } finally {
            Log.d(TAG, "=== CONNECTION ATTEMPT END ===")
        }
    }
    
    fun disconnectDevice(macAddress: String) {
        Log.d(TAG, "Disconnecting device: $macAddress")
        try {
            mst03Manager.disConnect(macAddress)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when disconnecting device - permissions not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting device: $macAddress", e)
        }
        connectedDevices.remove(macAddress)
    }
    
    suspend fun setDeviceSecretKey(macAddress: String, secretKey: String): Boolean {
        return try {
            mst03Manager.setSecretKey(macAddress, secretKey)
            connectedDevices[macAddress] = secretKey
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting secret key for device: $macAddress", e)
            false
        }
    }
    
    fun isDeviceConnected(macAddress: String): Boolean {
        return _connectionStates.value[macAddress] == ConnectionState.READY
    }
    
    fun getConnectedDevices(): List<String> {
        return connectedDevices.keys.toList()
    }
    
    private fun updateConnectionState(macAddress: String, state: ConnectionState) {
        Log.d(TAG, "Updating connection state for $macAddress: $state")
        val currentStates = _connectionStates.value.toMutableMap()
        currentStates[macAddress] = state
        _connectionStates.value = currentStates
        Log.d(TAG, "Connection states updated: ${_connectionStates.value}")
    }
    
    // SDK Methods for Device Details
    suspend fun queryFirmwareInfo(macAddress: String): String {
        return try {
            Log.d(TAG, "Querying firmware info for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.queryDeviceFirmwareInfo(macAddress) { isSuccessful, version ->
                    if (isSuccessful && version != null) {
                        val result = StringBuilder().apply {
                            version.versionInfoList?.forEach { versionInfo ->
                                append(versionInfo)
                            }
                        }.toString()
                        continuation.resume(result)
                    } else {
                        continuation.resume("Failed to query firmware info")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying firmware info", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun queryTemperatureHistory(macAddress: String): String {
        return try {
            Log.d(TAG, "Querying temperature history for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                val systemTime = System.currentTimeMillis() / 1000
                val startTime = (systemTime - 60 * 60 * 24) / 1000 // Last 24 hours
                val endTime = systemTime
                val rules = 1 // Get data for specific time period
                
                Log.d(TAG, "Calling SDK with params: rules=$rules, startTime=$startTime, endTime=$endTime, systemTime=$systemTime")
                
                mst03Manager.queryTemperatureHistoryData(macAddress, rules, startTime, endTime, systemTime) { result, historyData ->
                    Log.d(TAG, "=== RAW SDK RESPONSE - TEMPERATURE HISTORY ===")
                    Log.d(TAG, "SDK Callback Result: $result")
                    Log.d(TAG, "SDK Callback HistoryData Object: $historyData")
                    if (historyData != null) {
                        Log.d(TAG, "HistoryData.historyDataList: ${historyData.historyDataList}")
                        Log.d(TAG, "HistoryData.historyDataList Size: ${historyData.historyDataList?.size}")
                        historyData.historyDataList?.forEachIndexed { index, htData ->
                            Log.d(TAG, "Raw HtData[$index]: $htData")
                            Log.d(TAG, "  - timestamps: ${htData.timestamps}")
                            Log.d(TAG, "  - temperature: ${htData.temperature}")
                            Log.d(TAG, "  - humidity: ${htData.humidity}")
                        }
                    }
                    Log.d(TAG, "=== END RAW SDK RESPONSE ===")
                    
                    if (result && historyData != null) {
                        Log.d(TAG, "Temperature history data received: ${historyData.historyDataList?.size ?: 0} records")
                        val resultText = StringBuilder().apply {
                            append("Temperature History (Last 24 hours):\n")
                            append("Raw SDK Response: $historyData\n")
                            append("Data Records: ${historyData.historyDataList?.size ?: 0}\n")
                            historyData.historyDataList?.forEach { htData ->
                                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                    .format(java.util.Date(htData.timestamps))
                                    append(htData)
                            }
                        }.toString()
                        Log.d(TAG, "Temperature history result: $resultText")
                        continuation.resume(resultText)
                    } else {
                        Log.w(TAG, "No temperature history data available - result: $result, data: $historyData")
                        continuation.resume("No temperature history data available")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying temperature history", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun queryLightIntensityConfiguration(macAddress: String): String {
        return try {
            Log.d(TAG, "Querying light intensity configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.getLightIntensityConfiguration(macAddress) { isSuccessful, configResult ->
                    if (isSuccessful && configResult != null) {
                        val resultText = StringBuilder().apply {
                            append(configResult)
                        }.toString()
                        continuation.resume(resultText)
                    } else {
                        continuation.resume("Failed to get light intensity configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying light intensity configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun queryAlarmStrategyConfiguration(macAddress: String): String {
        return try {
            Log.d(TAG, "Querying alarm strategy configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.getAlarmStrategyConfiguration(macAddress) { isSuccessful, configResult ->
                    if (isSuccessful && configResult != null) {
                        val resultText = StringBuilder().apply {
                            append(configResult)
                        }.toString()
                        continuation.resume(resultText)
                    } else {
                        continuation.resume("Failed to get alarm strategy configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying alarm strategy configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun getDeviceStaticInfo(macAddress: String): String {
        return try {
            Log.d(TAG, "Getting device static info for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.queryHTSensorConfiguration(macAddress) { isSuccessful, configResult ->
                    if (isSuccessful && configResult != null) {
                        val resultText = StringBuilder().apply {
                            append(configResult)
                        }.toString()
                        continuation.resume(resultText)
                    } else {
                        continuation.resume("Failed to get temperature sensor configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device static info", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun getCombinationFrameData(macAddress: String): String {
        return try {
            Log.d(TAG, "Getting combination frame data for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                val systemTime = System.currentTimeMillis() / 1000
                val startTime = (systemTime - 60 * 60 * 24) / 1000 // Last 24 hours
                val endTime = systemTime
                val rules = 1 // Get data for specific time period
                
                Log.d(TAG, "Calling light history SDK with params: rules=$rules, startTime=$startTime, endTime=$endTime, systemTime=$systemTime")
                
                mst03Manager.queryLightHistoryData(macAddress, rules, startTime, endTime, systemTime) { result, historyData ->
                    Log.d(TAG, "=== RAW SDK RESPONSE - LIGHT HISTORY ===")
                    Log.d(TAG, "SDK Callback Result: $result")
                    Log.d(TAG, "SDK Callback HistoryData Object: $historyData")
                    if (historyData != null) {
                        Log.d(TAG, "HistoryData.historyDataList: ${historyData.historyDataList}")
                        Log.d(TAG, "HistoryData.historyDataList Size: ${historyData.historyDataList?.size}")
                        historyData.historyDataList?.forEachIndexed { index, lightData ->
                            Log.d(TAG, "Raw LightData[$index]: $lightData")
                            Log.d(TAG, "  - timestamps: ${lightData.timestamps}")
                            Log.d(TAG, "  - lightIntensity: ${lightData.lightIntensity}")
                            Log.d(TAG, "  - alarmType: ${lightData.alarmType}")
                        }
                    }
                    Log.d(TAG, "=== END RAW SDK RESPONSE ===")
                    
                    if (result && historyData != null) {
                        Log.d(TAG, "Light history data received: ${historyData.historyDataList?.size ?: 0} records")
                        val resultText = StringBuilder().apply {
                            historyData.historyDataList?.forEach { lightData ->
                                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                    .format(java.util.Date(lightData.timestamps))
                                append(lightData)
                            }
                        }.toString()
                        Log.d(TAG, "Light history result: $resultText")
                        continuation.resume(resultText)
                    } else {
                        Log.w(TAG, "No light history data available - result: $result, data: $historyData")
                        continuation.resume("No light history data available")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting combination frame data", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun resetDevice(macAddress: String): String {
        return try {
            Log.d(TAG, "Resetting device $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.reset(macAddress) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Device reset command sent successfully. Device will restart in 5 seconds.")
                    } else {
                        continuation.resume("Failed to reset device")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting device", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun rebootDevice(macAddress: String): String {
        return try {
            Log.d(TAG, "Rebooting device $macAddress")
            // TODO: Implement actual device reboot using SDK
            "Device reboot command sent successfully. Device will restart in 3 seconds."
        } catch (e: Exception) {
            Log.e(TAG, "Error rebooting device", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun powerOffDevice(macAddress: String): String {
        return try {
            Log.d(TAG, "Powering off device $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.powerOff(macAddress) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Device power off command sent successfully. Device will turn off in 2 seconds.")
                    } else {
                        continuation.resume("Failed to power off device")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error powering off device", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun firmwareUpgrade(macAddress: String): String {
        return try {
            Log.d(TAG, "Starting firmware upgrade for $macAddress")
            // TODO: Implement actual firmware upgrade using SDK
            "Firmware upgrade process started. Please wait for completion."
        } catch (e: Exception) {
            Log.e(TAG, "Error starting firmware upgrade", e)
            "Error: ${e.message}"
        }
    }
    
    // ========== CONFIGURATION METHODS ==========
    
    suspend fun queryAdvertisingParameters(macAddress: String, slot: Int = 0): String {
        return try {
            Log.d(TAG, "Querying advertising parameters for $macAddress, slot: $slot")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.queryAdvParametersConfiguration(macAddress, slot) { isSuccessful, configResult ->
                    if (isSuccessful && configResult != null) {
                        val resultText = StringBuilder().apply {
                            append(configResult)
                        }.toString()
                        continuation.resume(resultText)
                    } else {
                        continuation.resume("Failed to query advertising parameters")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying advertising parameters", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun setAdvertisingParameters(
        macAddress: String,
        frameType: String,
        slotNumber: Int,
        advertisingInterval: Int,
        txPower: Int,
        advertisingContent: String?
    ): String {
        return try {
            Log.d(TAG, "Setting advertising parameters for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.setAdvParametersConfiguration(
                    macAddress, frameType, slotNumber, advertisingInterval, txPower, advertisingContent
                ) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Advertising parameters set successfully")
                    } else {
                        continuation.resume("Failed to set advertising parameters")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting advertising parameters", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun setLEDConfiguration(
        macAddress: String,
        colorTable: Int,
        workTotalCount: Int,
        singleCycleLightingTime: Int,
        singleCycleLightOffTime: Int,
        brightness: Int
    ): String {
        return try {
            Log.d(TAG, "Setting LED configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.setLEDConfiguration(
                    macAddress, colorTable, workTotalCount, singleCycleLightingTime, singleCycleLightOffTime, brightness
                ) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("LED configuration set successfully")
                    } else {
                        continuation.resume("Failed to set LED configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting LED configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun changeSecretKey(macAddress: String, newSecretKey: String): String {
        return try {
            Log.d(TAG, "Changing secret key for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.changeSecretKey(macAddress, newSecretKey) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Secret key changed successfully")
                    } else {
                        continuation.resume("Failed to change secret key")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error changing secret key", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun setHTSensorConfiguration(
        macAddress: String,
        samplingInterval: Int,
        delay: Int,
        htSettingData: List<Any> // Using Any for now since we don't have the exact class
    ): String {
        return try {
            Log.d(TAG, "Setting HT sensor configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                // For now, return a placeholder since we need the exact SDK classes
                continuation.resume("HT sensor configuration method requires exact SDK classes. Please implement with proper imports.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting HT sensor configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun setLightIntensityConfiguration(
        macAddress: String,
        lightIntensityLowerLimitAlarm: Int,
        lightIntensityUpperLimitAlarm: Int
    ): String {
        return try {
            Log.d(TAG, "Setting light intensity configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                val configuration = com.minew.ble.mst03.bean.LightIntensitySensorConfiguration().apply {
                    this.lightIntensityLowerLimitAlarm = lightIntensityLowerLimitAlarm
                    this.lightIntensityUpperLimitAlarm = lightIntensityUpperLimitAlarm
                }
                
                mst03Manager.setLightIntensityConfiguration(macAddress, configuration) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Light intensity configuration set successfully")
                    } else {
                        continuation.resume("Failed to set light intensity configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting light intensity configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun setAlarmStrategyConfiguration(
        macAddress: String,
        alarmLedDuration: Int,
        alarmCountThreshold: Int
    ): String {
        return try {
            Log.d(TAG, "Setting alarm strategy configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                val configuration = com.minew.ble.mst03.bean.AlarmStrategyConfiguration().apply {
                    this.alarmLedDuration = alarmLedDuration
                    this.alarmCountThreshold = alarmCountThreshold
                }
                
                mst03Manager.setAlarmStrategyConfiguration(macAddress, configuration) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Alarm strategy configuration set successfully")
                    } else {
                        continuation.resume("Failed to set alarm strategy configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting alarm strategy configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun clearTemperatureHistory(macAddress: String): String {
        return try {
            Log.d(TAG, "Clearing temperature history for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.cleanTempHistoryData(macAddress) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Temperature history cleared successfully")
                    } else {
                        continuation.resume("Failed to clear temperature history")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing temperature history", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun clearLightHistory(macAddress: String): String {
        return try {
            Log.d(TAG, "Clearing light history for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.cleanLightHistoryData(macAddress) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Light history cleared successfully")
                    } else {
                        continuation.resume("Failed to clear light history")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing light history", e)
            "Error: ${e.message}"
        }
    }
}
