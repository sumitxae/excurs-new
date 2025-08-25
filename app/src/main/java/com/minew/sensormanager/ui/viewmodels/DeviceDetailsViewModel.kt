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
import com.minew.sensormanager.data.models.TemperatureHistoryAnalysis
import com.minew.sensormanager.data.repository.DeviceRepository
import com.minew.sensormanager.utils.TimestampConverter
import com.minew.sensormanager.utils.TemperatureHistoryAnalyzer
import com.minew.sensormanager.utils.PermissionHelper
import com.minew.sensormanager.utils.BluetoothHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    private val bleManager: MinewBleManager,
    private val deviceRepository: DeviceRepository
) : ViewModel() {
    
    private val _deviceInfo = MutableLiveData<DeviceInfo?>()
    val deviceInfo: LiveData<DeviceInfo?> = _deviceInfo
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState
    
    private val _excursionEventTime = MutableLiveData<String>()
    val excursionEventTime: LiveData<String> = _excursionEventTime
    
    private val _isAnalyzingTemperature = MutableLiveData<Boolean>()
    val isAnalyzingTemperature: LiveData<Boolean> = _isAnalyzingTemperature
    
    private val _excursionDuration = MutableLiveData<String>()
    val excursionDuration: LiveData<String> = _excursionDuration
    
    private val _temperatureHistoryData = MutableLiveData<String>()
    val temperatureHistoryData: LiveData<String> = _temperatureHistoryData
    
    private val _temperatureHistoryAnalysis = MutableLiveData<TemperatureHistoryAnalysis>()
    val temperatureHistoryAnalysis: LiveData<TemperatureHistoryAnalysis> = _temperatureHistoryAnalysis
    
    private var currentDeviceMac: String? = null
    private var connectionStartTime: Long = 0L
    private var deviceTempEventTimestamp: Long? = null
    
    init {
        observeConnectionStates()
    }
    
    private fun observeConnectionStates() {
        viewModelScope.launch {
            bleManager.connectionStates.collect { states ->
                currentDeviceMac?.let { mac ->
                    states[mac]?.let { state ->
                        _connectionState.value = state
                        // Update device info with new connection state
                        _deviceInfo.value?.let { device ->
                            _deviceInfo.value = device.copy(connectionState = state)
                            
                            // When connection is ready, start temperature history analysis
                            if (state == ConnectionState.READY) {
                                startTemperatureHistoryAnalysis()
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun startTemperatureHistoryAnalysis() {
        try {
            Log.d("DeviceDetailsViewModel", "Starting temperature history analysis...")
            
            // Show processing state
            _excursionEventTime.value = "Processing..."
            _isAnalyzingTemperature.value = true
            
            // Query temperature history data directly
            queryTemperatureHistoryData()
            
        } catch (e: Exception) {
            Log.e("DeviceDetailsViewModel", "Error starting temperature history analysis", e)
            _excursionEventTime.value = "Error starting analysis"
            _isAnalyzingTemperature.value = false
        }
    }
    
    private fun queryTemperatureHistoryData() {
        viewModelScope.launch {
            try {
                currentDeviceMac?.let { macAddress ->
                    Log.d("DeviceDetailsViewModel", "Querying all temperature history data...")
                    
                    val historyData = bleManager.queryAllTemperatureHistory(macAddress = macAddress)
                    
                    _temperatureHistoryData.value = historyData
                    Log.d("DeviceDetailsViewModel", "Temperature history data received: $historyData")
                    
                    // Analyze the temperature history data
                    analyzeTemperatureHistory(historyData)
                }
            } catch (e: Exception) {
                Log.e("DeviceDetailsViewModel", "Error querying temperature history data", e)
                _temperatureHistoryData.value = "Error querying temperature history: ${e.message}"
            }
        }
    }
    
    private fun analyzeTemperatureHistory(rawHistoryData: String) {
        try {
            Log.d("DeviceDetailsViewModel", "Analyzing temperature history data...")
            
            val analysis = TemperatureHistoryAnalyzer.analyzeTemperatureHistory(
                rawHistoryData = rawHistoryData,
                deviceTempEventTimestamp = null // We don't need device's tempEventTimestamp
            )
            
            _temperatureHistoryAnalysis.value = analysis
            
            // Update excursion event time and duration based on analysis
            updateExcursionInfo(analysis)
            
            Log.d("DeviceDetailsViewModel", "Temperature history analysis completed")
            
        } catch (e: Exception) {
            Log.e("DeviceDetailsViewModel", "Error analyzing temperature history", e)
        }
    }
    
    private fun updateExcursionInfo(analysis: TemperatureHistoryAnalysis) {
        val excursion = analysis.excursionAnalysis
        
        if (excursion.isInExcursion && excursion.excursionStartTime != null) {
            // Update excursion event time with detected start time
            val formattedStartTime = TimestampConverter.formatTimestamp(excursion.excursionStartTime)
            _excursionEventTime.value = formattedStartTime
            
            // Update excursion duration
            if (excursion.excursionDuration != null) {
                val durationInMinutes = excursion.excursionDuration / (1000 * 60)
                val durationText = if (durationInMinutes >= 60) {
                    val hours = durationInMinutes / 60
                    val minutes = durationInMinutes % 60
                    "${hours}h ${minutes}m"
                } else {
                    "${durationInMinutes}m"
                }
                _excursionDuration.value = durationText
            } else {
                _excursionDuration.value = "Calculating..."
            }
            
            Log.d("DeviceDetailsViewModel", "Excursion detected: Started at $formattedStartTime, Duration: ${_excursionDuration.value}")
        } else {
            _excursionEventTime.value = "No excursion detected"
            _excursionDuration.value = "N/A"
            Log.d("DeviceDetailsViewModel", "No excursion detected in temperature history")
        }
        
        // Analysis complete
        _isAnalyzingTemperature.value = false
    }
    
    fun loadDeviceDetails(context: Context, deviceMac: String) {
        currentDeviceMac = deviceMac
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                Log.d("DeviceDetailsViewModel", "Loading device details for: $deviceMac")
                
                // Check permissions and Bluetooth state first
                if (!PermissionHelper.hasAllRequiredPermissions(context)) {
                    _errorMessage.value = "Missing required permissions. Please grant Bluetooth and Location permissions in Settings."
                    _isLoading.value = false
                    return@launch
                }
                
                if (!BluetoothHelper.isBluetoothEnabled(context)) {
                    _errorMessage.value = "Bluetooth is disabled. Please enable Bluetooth and try again."
                    _isLoading.value = false
                    return@launch
                }
                
                // Get device info from scan results first
                val scanResults = bleManager.scanResults.value
                val device = scanResults.find { it.macAddress == deviceMac }
                if (device != null) {
                    Log.d("DeviceDetailsViewModel", "Found device in scan results: ${device.name}")
                    _deviceInfo.value = device
                } else {
                    Log.d("DeviceDetailsViewModel", "Device not found in scan results, creating placeholder")
                    // Create placeholder device info
                    val placeholderDevice = DeviceInfo(
                        macAddress = deviceMac,
                        name = "MST03-${deviceMac.takeLast(4)}",
                        rssi = 0,
                        battery = 0,
                        temperature = null,
                        firmwareVersion = "Unknown",
                        isConnected = false,
                        lastSeen = System.currentTimeMillis(),
                        connectionState = ConnectionState.CONNECTING,
                        alertsEnabled = true,
                        groupId = null,
                        tempEventTimestamp = null,
                        currentTimestamp = null
                    )
                    _deviceInfo.value = placeholderDevice
                }
                
                // Show device info immediately, then try to connect
                _isLoading.value = false
                
                // Follow SDK connection process: Stop scanning first, then connect
                viewModelScope.launch {
                    try {
                        Log.d("DeviceDetailsViewModel", "Starting connection process...")
                        
                        // Capture connection start time for timestamp conversion
                        connectionStartTime = TimestampConverter.getCurrentUTCTime()
                        Log.d("DeviceDetailsViewModel", "Connection start time captured: $connectionStartTime")
                        
                        // 1. Stop scanning first (as per SDK docs)
                        Log.d("DeviceDetailsViewModel", "Stopping scan...")
                        bleManager.stopScan(context)
                        
                        // 2. Try to connect to the device (connectDevice handles secret key internally)
                        Log.d("DeviceDetailsViewModel", "Attempting connection...")
                        val connected = bleManager.connectDevice(context, deviceMac, "minewtech1234567")
                        Log.d("DeviceDetailsViewModel", "Connection result: $connected")
                        
                        if (!connected) {
                            // Check the current connection state to provide more specific error message
                            val currentState = bleManager.connectionStates.value[deviceMac]
                            val errorMessage = when (currentState) {
                                ConnectionState.ERROR -> {
                                    // Check if it's a permission issue first
                                    if (!PermissionHelper.hasAllRequiredPermissions(context)) {
                                        "Connection failed: Missing required permissions. Please grant Bluetooth and Location permissions in Settings."
                                    } else if (!BluetoothHelper.isBluetoothEnabled(context)) {
                                        "Connection failed: Bluetooth is disabled. Please enable Bluetooth and try again."
                                    } else {
                                        "Connection failed: Authentication error. Possible causes:\n" +
                                        "• Device is already connected to another app\n" +
                                        "• Device is out of range\n" +
                                        "• Incorrect secret key\n" +
                                        "• Device firmware issue\n\n" +
                                        "Please try:\n" +
                                        "1. Moving closer to the device\n" +
                                        "2. Disconnecting from other apps\n" +
                                        "3. Restarting the device"
                                    }
                                }
                                ConnectionState.DISCONNECTED -> "Device disconnected unexpectedly. Please try connecting again."
                                ConnectionState.READY -> "Connection appears successful but verification failed. Please try again."
                                ConnectionState.CONNECTING -> "Connection attempt timed out. Please check if the device is in range and try again."
                                else -> "Connection attempt failed. Device may be out of range or already connected to another device."
                            }
                            _errorMessage.value = errorMessage
                        }
                    } catch (e: Exception) {
                        Log.e("DeviceDetailsViewModel", "Connection error", e)
                        _errorMessage.value = "Connection error: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e("DeviceDetailsViewModel", "Error loading device details", e)
                _errorMessage.value = "Error loading device details: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun disconnectDevice() {
        currentDeviceMac?.let { mac ->
            bleManager.disconnectDevice(mac)
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun getConnectionDiagnostics(context: Context): String {
        val diagnostics = StringBuilder()
        diagnostics.append("=== Connection Diagnostics ===\n\n")
        
        // Check permissions
        val hasPermissions = PermissionHelper.hasAllRequiredPermissions(context)
        diagnostics.append("Permissions: ${if (hasPermissions) "✓ Granted" else "✗ Missing"}\n")
        
        if (!hasPermissions) {
            val missingPermissions = PermissionHelper.getMissingPermissions(context)
            diagnostics.append("Missing: ${missingPermissions.joinToString(", ")}\n")
        }
        
        // Check Bluetooth state
        val bluetoothState = BluetoothHelper.checkBluetoothState(context)
        diagnostics.append("Bluetooth: ${bluetoothState.name}\n")
        
        // Check if device is in scan results
        val scanResults = bleManager.scanResults.value
        val deviceInRange = scanResults.any { it.macAddress == currentDeviceMac }
        diagnostics.append("Device in range: ${if (deviceInRange) "✓ Yes" else "✗ No"}\n")
        
        // Check current connection state
        val currentState = bleManager.connectionStates.value[currentDeviceMac]
        diagnostics.append("Current state: ${currentState?.name ?: "Unknown"}\n")
        
        // Check if device is connected to other apps
        diagnostics.append("Connected devices: ${bleManager.getConnectedDevices().size}\n")
        
        diagnostics.append("\n=== Troubleshooting Tips ===\n")
        diagnostics.append("1. Ensure device is within 10 meters\n")
        diagnostics.append("2. Check if device is connected to another app\n")
        diagnostics.append("3. Try restarting the device\n")
        diagnostics.append("4. Ensure Bluetooth is enabled\n")
        diagnostics.append("5. Grant all required permissions\n")
        
        return diagnostics.toString()
    }
    
    fun refreshTemperatureHistory() {
        Log.d("DeviceDetailsViewModel", "Manually refreshing temperature history")
        queryTemperatureHistoryData()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Disconnect device when ViewModel is cleared
        disconnectDevice()
    }
}
