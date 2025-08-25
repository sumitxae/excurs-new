package com.minew.sensormanager.ui.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.minew.sensormanager.ble.MinewBleManager
import com.minew.sensormanager.data.repository.DeviceRepository
import com.minew.sensormanager.data.models.DeviceInfo
import com.minew.sensormanager.data.models.ConnectionState
import com.minew.sensormanager.services.AlertService
import com.minew.sensormanager.utils.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val bleManager: MinewBleManager,
    private val alertService: AlertService
) : ViewModel() {
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: LiveData<Map<String, ConnectionState>> = _connectionStates.asLiveData()
    
    // Real-time device data from scanning
    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val discoveredDevices: LiveData<List<DeviceInfo>> = _discoveredDevices.asLiveData()
    
    // Connected devices for reference
    val connectedDevices: LiveData<List<DeviceInfo>> = deviceRepository.getConnectedDevices().asLiveData()
    
    // Job to manage continuous scanning lifecycle
    private var continuousScanJob: Job? = null
    
    init {
        observeBleUpdates()
    }
    
    private fun observeBleUpdates() {
        // Observe scan results (real-time discovered devices)
        viewModelScope.launch {
            bleManager.scanResults.collect { devices ->
                _discoveredDevices.value = devices
                Log.d("MainViewModel", "Discovered ${devices.size} devices")
            }
        }
        
        // Observe connection state changes
        viewModelScope.launch {
            bleManager.connectionStates.collect { states ->
                _connectionStates.value = states
                // Update repository with connection states
                states.forEach { (macAddress, state) ->
                    deviceRepository.updateConnectionState(macAddress, state)
                }
            }
        }
        
        // Observe sensor data updates
        viewModelScope.launch {
            bleManager.sensorDataUpdates.collect { sensorData ->
                try {
                    // Ensure DB write off main thread
                    withContext(Dispatchers.IO) {
                        deviceRepository.insertSensorData(sensorData)
                    }
                    // Alerts can run on main; adjust if heavy
                    alertService.checkForAlerts(sensorData)
                } catch (e: Exception) {
                    _errorMessage.value = "Sensor data handling error: ${e.message}"
                }
            }
        }
    }
    
    suspend fun initializeApp() {
        _isLoading.value = true
        try {
            // Initialize alert service
            alertService.initialize()
            
            // Any other initialization tasks
            
        } catch (e: Exception) {
            _errorMessage.value = "Failed to initialize app: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
    
    suspend fun connectToDevice(context: android.content.Context, macAddress: String, secretKey: String = "minewtech1234567"): Boolean {
        return try {
            _isLoading.value = true
            
            if (!PermissionHelper.hasAllRequiredPermissions(context)) {
                val errorMsg = PermissionHelper.getPermissionErrorMessage(context)
                _errorMessage.value = errorMsg ?: "Required permissions not granted. Please grant Bluetooth and Location permissions."
                return false
            }
            
            val success = bleManager.connectDevice(context, macAddress, secretKey)
            if (!success) {
                _errorMessage.value = "Failed to connect to device"
            }
            success
        } catch (e: Exception) {
            _errorMessage.value = "Connection error: ${e.message}"
            false
        } finally {
            _isLoading.value = false
        }
    }
    
    fun disconnectDevice(macAddress: String) {
        bleManager.disconnectDevice(macAddress)
    }
    
    fun startContinuousScanning(context: android.content.Context): Boolean {
        Log.d("MainViewModel", "Starting continuous scanning loop")
        if (!PermissionHelper.hasAllRequiredPermissions(context)) {
            val errorMsg = PermissionHelper.getPermissionErrorMessage(context)
            _errorMessage.value = errorMsg ?: "Required permissions not granted. Please grant Bluetooth and Location permissions."
            return false
        }
        
        if (continuousScanJob?.isActive == true) {
            Log.d("MainViewModel", "Continuous scanning already running")
            return true
        }
        
        continuousScanJob = viewModelScope.launch {
            while (isActive) {
                try {
                    // Attempt to start a 5-minute scan window
                    val started = bleManager.startScan(context, 300000)
                    if (!started) {
                        // If start failed (likely permissions toggled), back off briefly
                        delay(2000)
                        continue
                    }
                    // Wait until current scan session stops (SDK triggers onStopScan after duration)
                    bleManager.isScanning.first { isScanning -> !isScanning }
                    // Small gap before restarting
                    delay(300)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Continuous scanning loop error: ${e.message}")
                    // Brief delay to avoid tight failure loop
                    delay(1000)
                }
            }
        }
        return true
    }
    
    fun stopScanning(context: android.content.Context) {
        Log.d("MainViewModel", "Stopping scanning and cancelling continuous loop")
        continuousScanJob?.cancel()
        continuousScanJob = null
        bleManager.stopScan(context)
    }
    
    suspend fun refreshDevices() {
        // This is now handled by real-time scanning
    }
    
    suspend fun cleanup() {
        // Clean up any resources
        bleManager.getConnectedDevices().forEach { macAddress ->
            bleManager.disconnectDevice(macAddress)
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
