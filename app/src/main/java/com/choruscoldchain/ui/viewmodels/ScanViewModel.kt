package com.choruscoldchain.ui.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.choruscoldchain.ble.MinewBleManager
import com.choruscoldchain.data.repository.DeviceRepository
import com.choruscoldchain.data.models.DeviceInfo
import com.choruscoldchain.data.models.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleManager: MinewBleManager,
    private val deviceRepository: DeviceRepository
) : ViewModel() {
    
    private val _scanResults = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val scanResults: LiveData<List<DeviceInfo>> = _scanResults.asLiveData()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: LiveData<Boolean> = _isScanning.asLiveData()
    
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: LiveData<Map<String, ConnectionState>> = _connectionStates.asLiveData()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage.asLiveData()
    
    init {
        observeBleUpdates()
    }
    
    private fun observeBleUpdates() {
        // Observe scan results
        viewModelScope.launch {
            bleManager.scanResults.collect { devices ->
                _scanResults.value = devices
            }
        }
        
        // Observe scanning state
        viewModelScope.launch {
            bleManager.isScanning.collect { scanning ->
                _isScanning.value = scanning
            }
        }
        
        // Observe connection states
        viewModelScope.launch {
            bleManager.connectionStates.collect { states ->
                _connectionStates.value = states
            }
        }
    }
    
    suspend fun startScan(context: Context, durationMs: Int = 5 * 60 * 1000) {
        val success = bleManager.startScan(context, durationMs)
        if (!success) {
            val errorMsg = com.choruscoldchain.utils.PermissionHelper.getPermissionErrorMessage(context)
            _errorMessage.value = errorMsg ?: "Required permissions not granted. Please grant Bluetooth and Location permissions to scan for devices."
        }
    }
    
    fun stopScan(context: Context) {
        bleManager.stopScan(context)
    }
    
    suspend fun addDevice(device: DeviceInfo) {
        deviceRepository.insertOrUpdateDevice(device)
    }
    
    suspend fun connectToDevice(macAddress: String, secretKey: String = "minewtech1234567"): Boolean {
        return bleManager.connectDevice(null, macAddress, secretKey)
    }
    
    fun disconnectDevice(macAddress: String) {
        bleManager.disconnectDevice(macAddress)
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
