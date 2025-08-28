package com.choruscoldchain.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.choruscoldchain.ble.MinewBleManager
import com.choruscoldchain.data.models.DeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.minew.ble.mst03.bean.HtSensorConfiguration
import com.minew.ble.v3.bean.HTSensorThresholdConfig
import kotlin.math.max
import kotlin.math.min
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData


@HiltViewModel
class DeviceSettingsViewModel @Inject constructor(
    private val bleManager: MinewBleManager
) : ViewModel() {

    companion object {
        private const val TAG = "DeviceSettingsViewModel"
    }

    // UI State
    private val _uiState = MutableStateFlow(DeviceSettingsUiState())
    val uiState: StateFlow<DeviceSettingsUiState> = _uiState.asStateFlow()

    // Temperature thresholds (two groups per SDK)
    private val _temp1Enabled = MutableStateFlow(true)
    val temp1Enabled: StateFlow<Boolean> = _temp1Enabled.asStateFlow()

    private val _temp1Min = MutableStateFlow(20.0f)
    val temp1Min: StateFlow<Float> = _temp1Min.asStateFlow()

    private val _temp1Max = MutableStateFlow(50.0f)
    val temp1Max: StateFlow<Float> = _temp1Max.asStateFlow()

    private val _temp2Enabled = MutableStateFlow(true)
    val temp2Enabled: StateFlow<Boolean> = _temp2Enabled.asStateFlow()

    private val _temp2Min = MutableStateFlow(2.0f)
    val temp2Min: StateFlow<Float> = _temp2Min.asStateFlow()

    private val _temp2Max = MutableStateFlow(8.0f)
    val temp2Max: StateFlow<Float> = _temp2Max.asStateFlow()

    // Firmware info
    private val _firmwareVersion = MutableStateFlow("Unknown")
    val firmwareVersion: StateFlow<String> = _firmwareVersion.asStateFlow()

    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isCheckingFirmware = MutableStateFlow(false)
    val isCheckingFirmware: StateFlow<Boolean> = _isCheckingFirmware.asStateFlow()

    // Messages
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var currentDeviceMac: String? = null
    private var currentSamplingInterval: Int? = null
    private var currentMeasurementDelay: Int? = null

    private fun clamp(value: Float, minValue: Float, maxValue: Float): Float {
        return max(minValue, min(value, maxValue))
    }

    fun initializeDevice(device: DeviceInfo) {
        currentDeviceMac = device.macAddress
        _firmwareVersion.value = device.firmwareVersion ?: "Unknown"
    }



    private suspend fun loadTemperatureConfiguration(macAddress: String) {
        try {
            Log.d(TAG, "Loading temperature configuration for $macAddress")
            val configResult = bleManager.queryHTSensorConfiguration(macAddress)
            
            if (configResult != null) {
                Log.d(TAG, "Temperature config loaded successfully: $configResult")
                
                // Preserve existing sampling and delay so we don't overwrite them on save
                currentSamplingInterval = try { configResult.samplingInterval } catch (e: Exception) { null }
                currentMeasurementDelay = try { configResult.delay } catch (e: Exception) { null }

                // Parse the configuration result
                val htSettingData = configResult.htSettingData
                if (htSettingData.isNotEmpty()) {
                    // Group 1: Normal (20–50°C)
                    val g1 = htSettingData.getOrNull(0)
                    if (g1 != null) {
                        val enabled = !(g1.lowTemperature == -128f || g1.highTemperature == -128f)
                        _temp1Enabled.value = enabled
                        if (enabled) {
                            _temp1Min.value = clamp(g1.lowTemperature, 20f, 50f)
                            _temp1Max.value = clamp(g1.highTemperature, 20f, 50f)
                        }
                    }
                    // Group 2: Low (−30–15°C)
                    val g2 = htSettingData.getOrNull(1)
                    if (g2 != null) {
                        val enabled = !(g2.lowTemperature == -128f || g2.highTemperature == -128f)
                        _temp2Enabled.value = enabled
                        if (enabled) {
                            _temp2Min.value = clamp(g2.lowTemperature, -30f, 15f)
                            _temp2Max.value = clamp(g2.highTemperature, -30f, 15f)
                        }
                    }
                } else {
                    Log.d(TAG, "No temperature configuration found, keeping defaults")
                }
            } else {
                Log.w(TAG, "No temperature configuration available")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading temperature configuration", e)
            _message.value = "Error loading temperature configuration: ${e.message}"
        }
    }

    fun setTemp1Enabled(enabled: Boolean) { _temp1Enabled.value = enabled }
    fun setTemp2Enabled(enabled: Boolean) { _temp2Enabled.value = enabled }
    fun updateTemp1Min(value: Float) { _temp1Min.value = clamp(value, 20f, 50f); if (_temp1Max.value < _temp1Min.value) _temp1Max.value = _temp1Min.value }
    fun updateTemp1Max(value: Float) { _temp1Max.value = clamp(value, 20f, 50f); if (_temp1Min.value > _temp1Max.value) _temp1Min.value = _temp1Max.value }
    fun updateTemp2Min(value: Float) { _temp2Min.value = clamp(value, -30f, 15f); if (_temp2Max.value < _temp2Min.value) _temp2Max.value = _temp2Min.value }
    fun updateTemp2Max(value: Float) { _temp2Max.value = clamp(value, -30f, 15f); if (_temp2Min.value > _temp2Max.value) _temp2Min.value = _temp2Max.value }

    private val _navigateToScanDevices = MutableLiveData<Boolean>()
    val navigateToScanDevices: LiveData<Boolean> = _navigateToScanDevices

    fun saveSettings() {
        val macAddress = currentDeviceMac ?: return
        
        viewModelScope.launch {
            _isSaving.value = true
            try {
                // Ensure device is connected
                if (!bleManager.isDeviceConnected(macAddress)) {
                    _message.value = "Device not connected. Please ensure device is connected before saving settings."
                    return@launch
                }

                // Save temperature configuration
                val tempSuccess = saveTemperatureConfiguration(macAddress)
                
                if (tempSuccess) {
                    _message.value = "Settings saved successfully!"
                    // Refresh from device to reflect the applied configuration
                    loadTemperatureConfiguration(macAddress)
                    // Navigate to scan devices activity
                    _navigateToScanDevices.value = true
                } else {
                    _message.value = "Some settings failed to save. Please try again."
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving settings", e)
                _message.value = "Error saving settings: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    private suspend fun saveTemperatureConfiguration(macAddress: String): Boolean {
        return try {
            Log.d(TAG, "Saving temperature configuration groups")
            
            // Create temperature threshold configurations using the correct SDK classes
            val htSettingData = mutableListOf<HTSensorThresholdConfig>()
            
            // Group 1: Normal (20–50°C)
            val normalTempConfig = HTSensorThresholdConfig().apply {
                if (_temp1Enabled.value) {
                    this.lowTemperature = clamp(_temp1Min.value, 20f, 50f)
                    this.highTemperature = clamp(_temp1Max.value, 20f, 50f)
                } else {
                    this.lowTemperature = -128f
                    this.highTemperature = -128f
                }
                this.highHumidity = -128f
                this.lowHumidity = -128f
            }
            htSettingData.add(normalTempConfig)
            
            // Group 2: Low (−30–15°C)
            val lowTempConfig = HTSensorThresholdConfig().apply {
                if (_temp2Enabled.value) {
                    this.lowTemperature = clamp(_temp2Min.value, -30f, 15f)
                    this.highTemperature = clamp(_temp2Max.value, -30f, 15f)
                } else {
                    this.lowTemperature = -128f
                    this.highTemperature = -128f
                }
                this.highHumidity = -128f
                this.lowHumidity = -128f
            }
            htSettingData.add(lowTempConfig)
            
            // Ensure we preserve existing samplingInterval and delay; if unknown, fetch once
            var sampling = currentSamplingInterval
            var delay = currentMeasurementDelay
            if (sampling == null || delay == null) {
                try {
                    val existing = bleManager.queryHTSensorConfiguration(macAddress)
                    sampling = existing?.samplingInterval
                    delay = existing?.delay
                    currentSamplingInterval = sampling
                    currentMeasurementDelay = delay
                } catch (_: Exception) {
                }
            }

            // Fallbacks: if still null, use conservative defaults from existing UI (do NOT change behavior)
            val resolvedSampling = sampling ?: 60
            val resolvedDelay = delay ?: 0

            val result = bleManager.setHTSensorConfiguration(macAddress, resolvedSampling, resolvedDelay, htSettingData)
            Log.d(TAG, "Temperature configuration save result: $result")
            
            result.contains("successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving temperature configuration", e)
            false
        }
    }

    fun checkForFirmwareUpdates() {
        val macAddress = currentDeviceMac ?: return
        
        viewModelScope.launch {
            _isCheckingFirmware.value = true
            try {
                // Ensure device is connected
                if (!bleManager.isDeviceConnected(macAddress)) {
                    _message.value = "Device not connected. Please ensure device is connected before checking firmware."
                    return@launch
                }

                val firmwareInfo = bleManager.queryFirmwareInfo(macAddress)
                Log.d(TAG, "Firmware info: $firmwareInfo")
                
                if (firmwareInfo.contains("Failed") || firmwareInfo.contains("Error")) {
                    _message.value = "Failed to check firmware updates: $firmwareInfo"
                } else {
                    _message.value = "Current firmware: $firmwareInfo"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking firmware updates", e)
                _message.value = "Error checking firmware updates: ${e.message}"
            } finally {
                _isCheckingFirmware.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
    
    // ===== Firmware Upgrade (per SDK_Doc.md) =====
    fun verifyOtaFile(zipFilePath: String): Boolean {
        return try {
            bleManager.verifyOtaFile(zipFilePath)
        } catch (e: Exception) {
            Log.e(TAG, "verifyOtaFile error", e)
            false
        }
    }

    fun firmwareUpgrade(
        dfuTarget: Int,
        fileByte: ByteArray,
        progressCallBack: (progress: Int) -> Unit,
        successCallBack: () -> Unit,
        failCallBack: () -> Unit
    ) {
        val mac = currentDeviceMac
        if (mac.isNullOrEmpty()) {
            _message.value = "No device selected for upgrade"
            failCallBack()
            return
        }

        if (!bleManager.isDeviceConnected(mac)) {
            _message.value = "Device not connected. Connect before upgrading."
            failCallBack()
            return
        }

        // Delegate to BLE manager
        bleManager.firmwareUpgrade(
            mac,
            dfuTarget,
            fileByte,
            progressCallBack,
            successCallBack,
            failCallBack
        )
    }

    fun resetToDefaults() {
        _temp1Enabled.value = true
        _temp1Min.value = 23.0f
        _temp1Max.value = 25.0f
        _temp2Enabled.value = true
        _temp2Min.value = 2.0f
        _temp2Max.value = 8.0f
        _message.value = "Settings reset to defaults. Saving..."
        // Persist defaults and navigate back on success
        saveSettings()
    }
    
    fun isDeviceConnected(): Boolean {
        val macAddress = currentDeviceMac ?: return false
        return bleManager.isDeviceConnected(macAddress)
    }
    
    suspend fun connectDevice(context: android.content.Context): Boolean {
        val macAddress = currentDeviceMac ?: return false
        return bleManager.connectDevice(context, macAddress, "minewtech1234567")
    }
    
    fun loadDeviceConfiguration() {
        viewModelScope.launch {
            loadDeviceConfigurationInternal()
        }
    }
    
    private suspend fun loadDeviceConfigurationInternal() {
        val macAddress = currentDeviceMac ?: return
        
        _isLoading.value = true
        try {
            // Load temperature sensor configuration (two groups)
            loadTemperatureConfiguration(macAddress)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading device configuration", e)
            _message.value = "Error loading configuration: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun disconnectDevice() {
        val macAddress = currentDeviceMac ?: return
        bleManager.disconnectDevice(macAddress)
    }

    override fun onCleared() {
        super.onCleared()
        // Disconnect device when ViewModel is cleared
        disconnectDevice()
    }

    fun clearNavigationEvent() {
        _navigateToScanDevices.value = false
    }
}

data class DeviceSettingsUiState(
    val deviceMac: String = "",
    val isConnected: Boolean = false,
    val error: String? = null
)
