package com.choruscoldchain.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.choruscoldchain.ble.MinewBleManager
import com.choruscoldchain.data.models.ConnectionState
import com.choruscoldchain.data.models.DeviceInfo
import com.choruscoldchain.data.models.TemperatureHistoryAnalysis
import com.choruscoldchain.data.repository.DeviceRepository
import com.choruscoldchain.utils.BluetoothHelper
import com.choruscoldchain.utils.PermissionHelper
import com.choruscoldchain.utils.TemperatureHistoryAnalyzer
import com.choruscoldchain.utils.TimestampConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.minew.ble.mst03.bean.HtData;
@HiltViewModel
class DeviceDetailsViewModel
@Inject
constructor(
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
    val temperatureHistoryAnalysis: LiveData<TemperatureHistoryAnalysis> =
            _temperatureHistoryAnalysis

    private val _recentLightIntensity = MutableLiveData<String>()
    val recentLightIntensity: LiveData<String> = _recentLightIntensity

    private var currentDeviceMac: String? = null
    private var connectionStartTime: Long = 0L
    private var deviceTempEventTimestamp: Long? = null
    private var isQueryingData: Boolean = false

    init {
        observeConnectionStates()
    }

    private fun observeConnectionStates() {
        viewModelScope.launch {
            bleManager.connectionStates.collect { states ->
                currentDeviceMac?.let { mac ->
                    states[mac]?.let { state ->
                        _connectionState.value = state

                        _deviceInfo.value?.let { device ->
                            _deviceInfo.value = device.copy(connectionState = state)

                            if (state == ConnectionState.READY) {
                                // Add delay to allow device to stabilize after connection
                                viewModelScope.launch {
                                    delay(2000) // 2 second delay to ensure device is fully ready
                                    startTemperatureHistoryAnalysis()
                                    // Light intensity will be queried after temperature analysis completes
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startTemperatureHistoryAnalysis() {
        if (isQueryingData) {
            Log.w("DeviceDetailsViewModel", "Already querying data, skipping temperature analysis")
            return
        }
        
        try {
            Log.d("DeviceDetailsViewModel", "Starting temperature history analysis...")
            isQueryingData = true

            _excursionEventTime.value = "Processing..."
            _isAnalyzingTemperature.value = true

            queryTemperatureHistoryData()
        } catch (e: Exception) {
            Log.e("DeviceDetailsViewModel", "Error starting temperature history analysis", e)
            _excursionEventTime.value = "Error starting analysis"
            _isAnalyzingTemperature.value = false
            isQueryingData = false
        }
    }

    private fun queryTemperatureHistoryData() {
        viewModelScope.launch {
            try {
                currentDeviceMac?.let { macAddress ->
                    Log.d("DeviceDetailsViewModel", "Querying all temperature history data...")
                    
                    // Check if device is still connected before querying
                    val currentState = bleManager.connectionStates.value[macAddress]
                    if (currentState != ConnectionState.READY) {
                        Log.w("DeviceDetailsViewModel", "Device not ready, skipping temperature history query. State: $currentState")
                        _temperatureHistoryData.value = "Device not ready"
                        _isAnalyzingTemperature.value = false
                        return@launch
                    }

                    val historyData = bleManager.queryAllTemperatureHistory(macAddress = macAddress)

                    _temperatureHistoryData.value = "Records: ${historyData.size}"
                    Log.d(
                            "DeviceDetailsViewModel",
                            "Temperature history data received: $historyData"
                    )

                    analyzeTemperatureHistory(historyData)
                }
            } catch (e: Exception) {
                Log.e("DeviceDetailsViewModel", "Error querying temperature history data", e)
                _temperatureHistoryData.value = "Error querying temperature history: ${e.message}"
                _isAnalyzingTemperature.value = false
            }
        }
    }

    private fun analyzeTemperatureHistory(rawHistoryData: List<HtData>) {
        try {
            Log.d("DeviceDetailsViewModel", "Analyzing temperature history data...")

            val analysis =
                    TemperatureHistoryAnalyzer.analyzeTemperatureHistory(
                            rawHistoryData = rawHistoryData,
                            deviceTempEventTimestamp = null
                    )

            _temperatureHistoryAnalysis.value = analysis

            updateExcursionInfo(analysis)

            Log.d("DeviceDetailsViewModel", "Temperature history analysis completed")
            
            // Now query light intensity after temperature analysis is complete
            viewModelScope.launch {
                delay(1000) // 1 second delay after temperature analysis
                refreshRecentLightIntensity()
            }
        } catch (e: Exception) {
            Log.e("DeviceDetailsViewModel", "Error analyzing temperature history", e)
        }
    }

    private fun updateExcursionInfo(analysis: TemperatureHistoryAnalysis) {
        val excursion = analysis.excursionAnalysis

        if (excursion.isInExcursion && excursion.excursionStartTime != null) {

            val formattedStartTime =
                    TimestampConverter.formatTimestamp(excursion.excursionStartTime)
            _excursionEventTime.value = formattedStartTime

            if (excursion.excursionDuration != null) {
                val durationInMinutes = excursion.excursionDuration / (1000 * 60)
                val durationText =
                        if (durationInMinutes >= 60) {
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

            Log.d(
                    "DeviceDetailsViewModel",
                    "Excursion detected: Started at $formattedStartTime, Duration: ${_excursionDuration.value}"
            )
        } else {
            _excursionEventTime.value = "No excursion detected"
            _excursionDuration.value = "N/A"
            Log.d("DeviceDetailsViewModel", "No excursion detected in temperature history")
        }

        _isAnalyzingTemperature.value = false
        isQueryingData = false
    }

    fun refreshRecentLightIntensity() {
        if (isQueryingData) {
            Log.w("DeviceDetailsViewModel", "Already querying data, skipping light intensity query")
            return
        }
        
        viewModelScope.launch {
            try {
                currentDeviceMac?.let { mac ->
                    // Double-check connection state before proceeding
                    val currentState = bleManager.connectionStates.value[mac]
                    if (currentState != ConnectionState.READY) {
                        Log.w("DeviceDetailsViewModel", "Device not ready for light intensity query. State: $currentState")
                        _recentLightIntensity.value = "Device not ready"
                        return@launch
                    }

                    Log.d("DeviceDetailsViewModel", "Starting light intensity query for device: $mac")
                    
                    // Try recent data first (last minute)
                    var result = bleManager.queryRecentLightIntensity(mac, 60)
                    
                    if (result == null) {
                        Log.d("DeviceDetailsViewModel", "No recent light data, trying all history")
                        // Fallback to all available history
                        result = bleManager.queryAllLightHistory(mac)
                    }
                    
                    if (result != null) {
                        val (intensity, ts) = result
                        _recentLightIntensity.value = "$intensity nW/cm²"
                        Log.d("DeviceDetailsViewModel", "Light intensity query successful: $intensity nW/cm²")
                        Log.d("DeviceDetailsViewModel", "Light intensity query timestamp: $ts")
                    } else {
                        _recentLightIntensity.value = "N/A"
                        Log.d("DeviceDetailsViewModel", "No light intensity data available")
                    }
                }
            } catch (e: Exception) {
                Log.e("DeviceDetailsViewModel", "Error fetching recent light intensity", e)
                _recentLightIntensity.value = "Error: ${e.message}"
            }
        }
    }

    fun loadDeviceDetails(context: Context, deviceMac: String) {
        currentDeviceMac = deviceMac
        _isLoading.value = true

        viewModelScope.launch {
            try {
                Log.d("DeviceDetailsViewModel", "Loading device details for: $deviceMac")

                if (!PermissionHelper.hasAllRequiredPermissions(context)) {
                    _errorMessage.value =
                            "Missing required permissions. Please grant Bluetooth and Location permissions in Settings."
                    _isLoading.value = false
                    return@launch
                }

                if (!BluetoothHelper.isBluetoothEnabled(context)) {
                    _errorMessage.value =
                            "Bluetooth is disabled. Please enable Bluetooth and try again."
                    _isLoading.value = false
                    return@launch
                }

                val scanResults = bleManager.scanResults.value
                val device = scanResults.find { it.macAddress == deviceMac }
                if (device != null) {
                    Log.d("DeviceDetailsViewModel", "Found device in scan results: ${device.name}")
                    _deviceInfo.value = device
                } else {
                    Log.d(
                            "DeviceDetailsViewModel",
                            "Device not found in scan results, creating placeholder"
                    )

                    val placeholderDevice =
                            DeviceInfo(
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

                _isLoading.value = false

                viewModelScope.launch {
                    try {
                        Log.d("DeviceDetailsViewModel", "Starting connection process...")

                        connectionStartTime = TimestampConverter.getCurrentUTCTime()
                        Log.d(
                                "DeviceDetailsViewModel",
                                "Connection start time captured: $connectionStartTime"
                        )

                        Log.d("DeviceDetailsViewModel", "Stopping scan...")
                        bleManager.stopScan(context)

                        Log.d("DeviceDetailsViewModel", "Attempting connection...")
                        val connected =
                                bleManager.connectDevice(context, deviceMac, "minewtech1234567")
                        Log.d("DeviceDetailsViewModel", "Connection result: $connected")

                        if (!connected) {

                            val currentState = bleManager.connectionStates.value[deviceMac]
                            val errorMessage =
                                    when (currentState) {
                                        ConnectionState.ERROR -> {

                                            if (!PermissionHelper.hasAllRequiredPermissions(context)
                                            ) {
                                                "Connection failed: Missing required permissions. Please grant Bluetooth and Location permissions in Settings."
                                            } else if (!BluetoothHelper.isBluetoothEnabled(context)
                                            ) {
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
                                        ConnectionState.DISCONNECTED ->
                                                "Device disconnected unexpectedly. Please try connecting again."
                                        ConnectionState.READY ->
                                                "Connection appears successful but verification failed. Please try again."
                                        ConnectionState.CONNECTING ->
                                                "Connection attempt timed out. Please check if the device is in range and try again."
                                        else ->
                                                "Connection attempt failed. Device may be out of range or already connected to another device."
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
            bleManager.stopNativeGatt(mac)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getConnectionDiagnostics(context: Context): String {
        val diagnostics = StringBuilder()
        diagnostics.append("=== Connection Diagnostics ===\n\n")

        val hasPermissions = PermissionHelper.hasAllRequiredPermissions(context)
        diagnostics.append("Permissions: ${if (hasPermissions) "✓ Granted" else "✗ Missing"}\n")

        if (!hasPermissions) {
            val missingPermissions = PermissionHelper.getMissingPermissions(context)
            diagnostics.append("Missing: ${missingPermissions.joinToString(", ")}\n")
        }

        val bluetoothState = BluetoothHelper.checkBluetoothState(context)
        diagnostics.append("Bluetooth: ${bluetoothState.name}\n")

        val scanResults = bleManager.scanResults.value
        val deviceInRange = scanResults.any { it.macAddress == currentDeviceMac }
        diagnostics.append("Device in range: ${if (deviceInRange) "✓ Yes" else "✗ No"}\n")

        val currentState = bleManager.connectionStates.value[currentDeviceMac]
        diagnostics.append("Current state: ${currentState?.name ?: "Unknown"}\n")

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

        disconnectDevice()
    }
}
