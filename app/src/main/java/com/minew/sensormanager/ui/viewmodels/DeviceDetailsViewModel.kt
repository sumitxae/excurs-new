package com.minew.sensormanager.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minew.sensormanager.ble.MinewBleManager
import com.minew.sensormanager.data.models.DeviceInfo
import com.minew.sensormanager.data.models.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    private val bleManager: MinewBleManager
) : ViewModel() {
    
    private val TAG = "DeviceDetailsViewModel"
    private var deviceMac: String? = null
    
    private val _deviceInfo = MutableLiveData<DeviceInfo?>()
    val deviceInfo: LiveData<DeviceInfo?> = _deviceInfo
    
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState
    
    private val _sensorData = MutableLiveData<String>()
    val sensorData: LiveData<String> = _sensorData
    
    private val _configurationData = MutableLiveData<String>()
    val configurationData: LiveData<String> = _configurationData
    
    private val _availableMethods = MutableLiveData<List<String>>()
    val availableMethods: LiveData<List<String>> = _availableMethods
    
    private val _methodResult = MutableLiveData<String>()
    val methodResult: LiveData<String> = _methodResult
    
    fun setDeviceMac(mac: String) {
        deviceMac = mac
        loadAvailableMethods()
        
        // Check if device is connected and show methods
        viewModelScope.launch {
            if (bleManager.isDeviceConnected(mac)) {
                _connectionState.value = ConnectionState.READY
                Log.d(TAG, "Device is connected, showing methods")
            } else {
                Log.d(TAG, "Device is not connected")
            }
        }
    }
    
    fun loadDeviceDetails() {
        deviceMac?.let { mac ->
            // Load device info from BLE manager
            viewModelScope.launch {
                // Get device info from scan results
                bleManager.scanResults.value.find { it.macAddress == mac }?.let { device ->
                    _deviceInfo.value = device
                }
                
                // Get initial connection state
                bleManager.connectionStates.value[mac]?.let { state ->
                    _connectionState.value = state
                    Log.d(TAG, "Initial connection state: $state")
                }
            }
            
            // Observe connection state changes
            viewModelScope.launch {
                bleManager.connectionStates.collect { states ->
                    states[mac]?.let { state ->
                        _connectionState.value = state
                        Log.d(TAG, "Connection state updated: $state")
                    }
                }
            }
        }
    }
    
    private fun loadAvailableMethods() {
        val methods = listOf(
            // Query Methods
            "Query Firmware Info",
            "Query Temperature History", 
            "Query Light History Data",
            "Query Temperature Sensor Configuration",
            "Query Light Intensity Configuration",
            "Query Alarm Strategy Configuration",
            "Query Advertising Parameters",
            
            // Configuration Methods
            "Set Advertising Parameters",
            "Set LED Configuration",
            "Change Secret Key",
            "Set HT Sensor Configuration",
            "Set Light Intensity Configuration",
            "Set Alarm Strategy Configuration",
            
            // Data Management Methods
            "Clear Temperature History",
            "Clear Light History",
            
            // Device Control Methods
            "Reset Device",
            "Power Off Device",
            "Firmware Upgrade"
        )
        Log.d(TAG, "Loading ${methods.size} available methods")
        _availableMethods.value = methods
        Log.d(TAG, "Available methods set: ${_availableMethods.value}")
    }
    
    fun queryFirmwareInfo() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Querying firmware info for $mac")
            _methodResult.value = "Querying firmware info..."
            viewModelScope.launch {
                val result = bleManager.queryFirmwareInfo(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun queryTemperatureHistory() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Querying temperature history for $mac")
            _methodResult.value = "Querying temperature history..."
            viewModelScope.launch {
                val result = bleManager.queryTemperatureHistory(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun queryLightIntensityConfiguration() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Querying light intensity configuration for $mac")
            _methodResult.value = "Querying light intensity configuration..."
            viewModelScope.launch {
                val result = bleManager.queryLightIntensityConfiguration(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun queryAlarmStrategyConfiguration() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Querying alarm strategy configuration for $mac")
            _methodResult.value = "Querying alarm strategy configuration..."
            viewModelScope.launch {
                val result = bleManager.queryAlarmStrategyConfiguration(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun getDeviceStaticInfo() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Getting device static info for $mac")
            _methodResult.value = "Getting device static info..."
            viewModelScope.launch {
                val result = bleManager.getDeviceStaticInfo(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun getCombinationFrameData() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Getting combination frame data for $mac")
            _methodResult.value = "Getting combination frame data..."
            viewModelScope.launch {
                val result = bleManager.getCombinationFrameData(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun queryLightHistoryData() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Querying light history data for $mac")
            _methodResult.value = "Querying light history data..."
            viewModelScope.launch {
                val result = bleManager.getCombinationFrameData(mac) // Using the same method for now
                _methodResult.value = result
            }
        }
    }
    
    fun queryTemperatureSensorConfiguration() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Querying temperature sensor configuration for $mac")
            _methodResult.value = "Querying temperature sensor configuration..."
            viewModelScope.launch {
                val result = bleManager.getDeviceStaticInfo(mac) // Using the same method for now
                _methodResult.value = result
            }
        }
    }
    
    fun queryAdvertisingParameters() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Querying advertising parameters for $mac")
            _methodResult.value = "Querying advertising parameters..."
            viewModelScope.launch {
                val result = bleManager.queryAdvertisingParameters(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun clearTemperatureHistory() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Clearing temperature history for $mac")
            _methodResult.value = "Clearing temperature history..."
            viewModelScope.launch {
                val result = bleManager.clearTemperatureHistory(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun clearLightHistory() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Clearing light history for $mac")
            _methodResult.value = "Clearing light history..."
            viewModelScope.launch {
                val result = bleManager.clearLightHistory(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun resetDevice() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Resetting device $mac")
            _methodResult.value = "Resetting device..."
            viewModelScope.launch {
                val result = bleManager.resetDevice(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun rebootDevice() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Rebooting device $mac")
            _methodResult.value = "Rebooting device..."
            viewModelScope.launch {
                val result = bleManager.rebootDevice(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun powerOffDevice() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Powering off device $mac")
            _methodResult.value = "Powering off device..."
            viewModelScope.launch {
                val result = bleManager.powerOffDevice(mac)
                _methodResult.value = result
            }
        }
    }
    
    fun firmwareUpgrade() {
        deviceMac?.let { mac ->
            Log.d(TAG, "Starting firmware upgrade for $mac")
            _methodResult.value = "Starting firmware upgrade..."
            viewModelScope.launch {
                val result = bleManager.firmwareUpgrade(mac)
                _methodResult.value = result
            }
        }
    }
    
    // ========== CONFIGURATION METHODS ==========
    
    fun setAdvertisingParameters(
        frameType: String,
        slotNumber: Int,
        advertisingInterval: Int,
        txPower: Int,
        advertisingContent: String?
    ) {
        deviceMac?.let { mac ->
            Log.d(TAG, "Setting advertising parameters for $mac")
            _methodResult.value = "Setting advertising parameters..."
            viewModelScope.launch {
                val result = bleManager.setAdvertisingParameters(mac, frameType, slotNumber, advertisingInterval, txPower, advertisingContent)
                _methodResult.value = result
            }
        }
    }
    
    fun setLEDConfiguration(
        colorTable: Int,
        workTotalCount: Int,
        singleCycleLightingTime: Int,
        singleCycleLightOffTime: Int,
        brightness: Int
    ) {
        deviceMac?.let { mac ->
            Log.d(TAG, "Setting LED configuration for $mac")
            _methodResult.value = "Setting LED configuration..."
            viewModelScope.launch {
                val result = bleManager.setLEDConfiguration(mac, colorTable, workTotalCount, singleCycleLightingTime, singleCycleLightOffTime, brightness)
                _methodResult.value = result
            }
        }
    }
    
    fun changeSecretKey(newSecretKey: String) {
        deviceMac?.let { mac ->
            Log.d(TAG, "Changing secret key for $mac")
            _methodResult.value = "Changing secret key..."
            viewModelScope.launch {
                val result = bleManager.changeSecretKey(mac, newSecretKey)
                _methodResult.value = result
            }
        }
    }
    
    fun setHTSensorConfiguration(
        samplingInterval: Int,
        delay: Int,
        htSettingData: List<Any> // Using Any for now since we don't have the exact class
    ) {
        deviceMac?.let { mac ->
            Log.d(TAG, "Setting HT sensor configuration for $mac")
            _methodResult.value = "Setting HT sensor configuration..."
            viewModelScope.launch {
                val result = bleManager.setHTSensorConfiguration(mac, samplingInterval, delay, htSettingData)
                _methodResult.value = result
            }
        }
    }
    
    fun setLightIntensityConfiguration(
        lightIntensityLowerLimitAlarm: Int,
        lightIntensityUpperLimitAlarm: Int
    ) {
        deviceMac?.let { mac ->
            Log.d(TAG, "Setting light intensity configuration for $mac")
            _methodResult.value = "Setting light intensity configuration..."
            viewModelScope.launch {
                val result = bleManager.setLightIntensityConfiguration(mac, lightIntensityLowerLimitAlarm, lightIntensityUpperLimitAlarm)
                _methodResult.value = result
            }
        }
    }
    
    fun setAlarmStrategyConfiguration(
        alarmLedDuration: Int,
        alarmCountThreshold: Int
    ) {
        deviceMac?.let { mac ->
            Log.d(TAG, "Setting alarm strategy configuration for $mac")
            _methodResult.value = "Setting alarm strategy configuration..."
            viewModelScope.launch {
                val result = bleManager.setAlarmStrategyConfiguration(mac, alarmLedDuration, alarmCountThreshold)
                _methodResult.value = result
            }
        }
    }
    
    // Debug method to force show methods for testing
    fun forceShowMethods() {
        _connectionState.value = ConnectionState.READY
        Log.d(TAG, "Forcing show methods for testing")
        loadAvailableMethods() // Reload methods
        Log.d(TAG, "Methods reloaded: ${_availableMethods.value}")
    }
}
