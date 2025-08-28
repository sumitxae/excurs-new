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
import com.minew.sensormanager.utils.AppIdUtils
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
    private val alertService: AlertService,
    private val application: android.app.Application
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
    
    // Search functionality
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: LiveData<String> = _searchQuery.asLiveData()
    
    // Filtered devices based on search
    private val _filteredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val filteredDevices: LiveData<List<DeviceInfo>> = _filteredDevices.asLiveData()
    
    // Connected devices for reference
    val connectedDevices: LiveData<List<DeviceInfo>> = deviceRepository.getConnectedDevices().asLiveData()
    
    // Job to manage continuous scanning lifecycle
    private var continuousScanJob: Job? = null
    
    init {
        observeBleUpdates()
        // Initialize filtered devices with empty list
        _filteredDevices.value = emptyList()
    }
    
    private fun observeBleUpdates() {
        // Observe scan results (real-time discovered devices)
        viewModelScope.launch {
            bleManager.scanResults.collect { devices ->
                _discoveredDevices.value = devices
                Log.d("MainViewModel", "Discovered ${devices.size} devices")
                // Apply search filter to new devices
                applySearchFilter()
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
            // Log app installation information for tracking
            logAppInstallationInfo()
            
            // Initialize alert service
            alertService.initialize()
            
            // Any other initialization tasks
            
        } catch (e: Exception) {
            _errorMessage.value = "Failed to initialize app: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Logs app installation information for tracking and analytics purposes.
     */
    private fun logAppInstallationInfo() {
        try {
            val installationId = AppIdUtils.getInstallationId(application)
            val isFreshInstall = AppIdUtils.isFreshInstallation(application)
            val appVersion = AppIdUtils.getAppVersion(application)
            val trackingId = AppIdUtils.getTrackingId(application)
            
            Log.i("MainViewModel", "App Installation Tracking:")
            Log.i("MainViewModel", "Installation ID: $installationId")
            Log.i("MainViewModel", "Tracking ID: $trackingId")
            Log.i("MainViewModel", "Is Fresh Installation: $isFreshInstall")
            Log.i("MainViewModel", "App Version: $appVersion")
            
            if (isFreshInstall) {
                Log.i("MainViewModel", "First time app launch detected - new user")
            }
            
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error logging app installation info", e)
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
    
    // Search functionality
    fun setSearchQuery(query: String) {
        Log.d("MainViewModel", "Setting search query: '$query'")
        _searchQuery.value = query
        applySearchFilter()
    }
    
    fun clearSearch() {
        Log.d("MainViewModel", "Clearing search")
        _searchQuery.value = ""
        applySearchFilter()
    }
    
    private fun applySearchFilter() {
        val query = _searchQuery.value.trim()
        val devices = _discoveredDevices.value
        Log.d("MainViewModel", "applySearchFilter called with query: '$query', devices count: ${devices.size}")
        
        val filtered = if (query.isEmpty()) {
            devices
        } else {
            devices.filter { device ->
                val matchesMac = device.macAddress.contains(query, ignoreCase = true)
                val matchesName = device.name.contains(query, ignoreCase = true)
                val matchesTemp = (device.temperature?.toString()?.contains(query) == true)
                
                Log.d("MainViewModel", "Device ${device.macAddress}: mac=$matchesMac, name=$matchesName, temp=$matchesTemp")
                
                matchesMac || matchesName || matchesTemp
            }
        }
        
        // Sort devices: alert state (excursion) devices first, then normal devices
        val sortedDevices = filtered.sortedWith(compareByDescending<DeviceInfo> { device ->
            // Check if device is in excursion state (temperature < 2°C or > 8°C)
            val temp = device.temperature
            if (temp != null && !temp.isNaN()) {
                temp < 2.0f || temp > 8.0f
            } else {
                false
            }
        }.thenBy { device ->
            // Secondary sort by temperature (higher temperatures first within each group)
            device.temperature ?: Float.NEGATIVE_INFINITY
        }.thenBy { device ->
            // Tertiary sort by MAC address for consistent ordering
            device.macAddress
        })
        
        _filteredDevices.value = sortedDevices
        Log.d("MainViewModel", "Search query: '$query', Filtered and sorted ${sortedDevices.size} devices from ${devices.size}")
    }
}
