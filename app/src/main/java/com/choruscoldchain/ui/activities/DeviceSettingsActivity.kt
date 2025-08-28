package com.choruscoldchain.ui.activities

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.EditText
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.choruscoldchain.R
import com.choruscoldchain.data.models.DeviceInfo
import com.choruscoldchain.ui.viewmodels.DeviceSettingsViewModel
import com.choruscoldchain.utils.AppIdUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

@AndroidEntryPoint
class DeviceSettingsActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_DEVICE_MAC = "device_mac"
        const val EXTRA_DEVICE_INFO = "device_info"
        private const val TAG = "DeviceSettingsActivity"
    }
    
    private val viewModel: DeviceSettingsViewModel by viewModels()
    
    private lateinit var tvDeviceMac: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var inputTemp1Min: EditText
    private lateinit var inputTemp1Max: EditText
    private lateinit var inputTemp2Min: EditText
    private lateinit var inputTemp2Max: EditText
    private lateinit var switchTemp1Enabled: SwitchCompat
    private lateinit var switchTemp2Enabled: SwitchCompat
    private lateinit var btnSaveSettings: Button
    private lateinit var btnUpdateDevice: Button
    private lateinit var tvCurrentFirmware: TextView
    private lateinit var btnResetDefaults: Button
    private val pickOtaZipLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        handleSelectedOtaZip(uri)
    }
    
    private var deviceMac: String = ""
    private var deviceInfo: DeviceInfo? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_settings)
        
        // Force dark status bar icons on light background across APIs
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        
        initializeViews()
        setupClickListeners()
        setupInputs()
        loadDeviceData()
        setupViewModelObservers()
        updateAppIdFooter()
    }
    
    private fun initializeViews() {
        tvDeviceMac = findViewById(R.id.tv_device_mac)
        btnBack = findViewById(R.id.btn_back)
        inputTemp1Min = findViewById(R.id.input_temp1_min)
        inputTemp1Max = findViewById(R.id.input_temp1_max)
        inputTemp2Min = findViewById(R.id.input_temp2_min)
        inputTemp2Max = findViewById(R.id.input_temp2_max)
        switchTemp1Enabled = findViewById(R.id.switch_temp1_enabled)
        switchTemp2Enabled = findViewById(R.id.switch_temp2_enabled)
        btnSaveSettings = findViewById(R.id.btn_save_settings)
        btnUpdateDevice = findViewById(R.id.btn_update_device)
        tvCurrentFirmware = findViewById(R.id.tv_current_firmware)
        btnResetDefaults = findViewById(R.id.btn_reset_defaults)
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }
        
        btnSaveSettings.setOnClickListener {
            // Ensure latest input values are committed even if fields still have focus
            commitAllInputs()
            // Also clear focus to trigger any pending listeners
            currentFocus?.clearFocus()
            viewModel.saveSettings()
        }
        
        btnUpdateDevice.setOnClickListener {
            // Launch file picker for OTA zip
            pickOtaZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
        }
        
        btnResetDefaults.setOnClickListener {
            viewModel.resetToDefaults()
        }
    }
    
    private fun commitAllInputs() {
        fun parse(edit: EditText): Float? = edit.text?.toString()?.toFloatOrNull()
        parse(inputTemp1Min)?.let { viewModel.updateTemp1Min(it) }
        parse(inputTemp1Max)?.let { viewModel.updateTemp1Max(it) }
        parse(inputTemp2Min)?.let { viewModel.updateTemp2Min(it) }
        parse(inputTemp2Max)?.let { viewModel.updateTemp2Max(it) }
    }
    
    private fun setupInputs() {
        switchTemp1Enabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTemp1Enabled(isChecked)
        }
        switchTemp2Enabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTemp2Enabled(isChecked)
        }
        fun commit(edit: EditText, commitFn: (Float) -> Unit) {
            val v = edit.text?.toString()?.toFloatOrNull()
            v?.let(commitFn)
        }
        inputTemp1Min.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commit(inputTemp1Min) { viewModel.updateTemp1Min(it) } }
        inputTemp1Max.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commit(inputTemp1Max) { viewModel.updateTemp1Max(it) } }
        inputTemp2Min.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commit(inputTemp2Min) { viewModel.updateTemp2Min(it) } }
        inputTemp2Max.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commit(inputTemp2Max) { viewModel.updateTemp2Max(it) } }
    }
    
    private fun loadDeviceData() {
        deviceMac = intent.getStringExtra(EXTRA_DEVICE_MAC) ?: ""
        deviceInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DEVICE_INFO, DeviceInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DEVICE_INFO)
        }
        
        if (deviceMac.isEmpty()) {
            Toast.makeText(this, "Device MAC address not provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        tvDeviceMac.text = deviceMac
        Log.d(TAG, "Loading settings for device: $deviceMac")
        
        // Initialize the ViewModel with device info
        deviceInfo?.let { device ->
            viewModel.initializeDevice(device)
        } ?: run {
            // Create a basic DeviceInfo if not provided
            val basicDeviceInfo = DeviceInfo(
                macAddress = deviceMac,
                name = "Device-$deviceMac",
                rssi = 0,
                battery = 0,
                temperature = null,
                firmwareVersion = "Unknown",
                isConnected = false,
                lastSeen = System.currentTimeMillis()
            )
            viewModel.initializeDevice(basicDeviceInfo)
        }
        
        // Ensure device is connected before proceeding
        ensureDeviceConnected()
    }
    
    private fun ensureDeviceConnected() {
        lifecycleScope.launch {
            try {
                // Check if device is already connected
                if (!viewModel.isDeviceConnected()) {
                    // Attempt to connect
                    val connected = viewModel.connectDevice(this@DeviceSettingsActivity)
                    
                    if (!connected) {
                        Toast.makeText(this@DeviceSettingsActivity, 
                            "Failed to connect to device. Please try again.", 
                            Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }
                
                // Device is connected, proceed with loading configuration
                viewModel.loadDeviceConfiguration()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to device", e)
                Toast.makeText(this@DeviceSettingsActivity, 
                    "Error connecting to device: ${e.message}", 
                    Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showLoadingDialog(message: String) {
        // You can implement a proper loading dialog here
        // For now, we'll just show a toast
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun hideLoadingDialog() {
        // Hide loading dialog implementation
    }

    private fun handleSelectedOtaZip(fileUri: Uri) {
        try {
            val fileName = queryDisplayName(fileUri) ?: "firmware.zip"
            val tempZip = copyUriToCache(fileUri, fileName)
            if (tempZip == null) {
                Toast.makeText(this, "File selection error", Toast.LENGTH_SHORT).show()
                return
            }

            val verified = try { viewModel.verifyOtaFile(tempZip.absolutePath) } catch (_: Exception) { false }
            if (!verified) {
                Toast.makeText(this, "Invalid OTA file", Toast.LENGTH_SHORT).show()
                return
            }

            val binData = extractBinFromZip(tempZip)
            if (binData == null) {
                Toast.makeText(this, "Unable to read firmware bin", Toast.LENGTH_SHORT).show()
                return
            }

            // Kick off upgrade using dfuTarget = 0 as per SDK doc when isLinkUpgrade=false
            btnUpdateDevice.isEnabled = false
            viewModel.firmwareUpgrade(
                dfuTarget = 0,
                fileByte = binData,
                progressCallBack = { progress ->
                    btnUpdateDevice.text = "Upgrading... ${progress}%"
                },
                successCallBack = {
                    btnUpdateDevice.isEnabled = true
                    btnUpdateDevice.text = "Upgrade Firmware"
                    Toast.makeText(this, "Firmware upgrade successful", Toast.LENGTH_SHORT).show()
                },
                failCallBack = {
                    btnUpdateDevice.isEnabled = true
                    btnUpdateDevice.text = "Upgrade Firmware"
                    Toast.makeText(this, "Firmware upgrade failed", Toast.LENGTH_SHORT).show()
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error handling selected OTA zip", e)
            Toast.makeText(this, "File selection error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) cursor.getString(index) else null
                } else null
            }
        } catch (_: Exception) { null }
    }

    private fun copyUriToCache(uri: Uri, fileName: String): File? {
        return try {
            val sanitized = fileName.ifBlank { "firmware.zip" }
            val outFile = File(cacheDir, sanitized)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    copyStream(input, output)
                }
            }
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "copyUriToCache error", e)
            null
        }
    }

    private fun copyStream(input: InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
        }
    }

    private fun extractBinFromZip(zipFile: File): ByteArray? {
        return try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".bin", ignoreCase = true)) {
                        val baos = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = zis.read(buffer)
                            if (read == -1) break
                            baos.write(buffer, 0, read)
                        }
                        return baos.toByteArray()
                    }
                    entry = zis.nextEntry
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "extractBinFromZip error", e)
            null
        }
    }
    
    private fun setupViewModelObservers() {
        lifecycleScope.launch {
            viewModel.temp1Enabled.collect { enabled ->
                switchTemp1Enabled.isChecked = enabled
                inputTemp1Min.isEnabled = enabled
                inputTemp1Max.isEnabled = enabled
            }
        }
        lifecycleScope.launch {
            viewModel.temp2Enabled.collect { enabled ->
                switchTemp2Enabled.isChecked = enabled
                inputTemp2Min.isEnabled = enabled
                inputTemp2Max.isEnabled = enabled
            }
        }
        lifecycleScope.launch {
            viewModel.temp1Min.collect { value ->
                inputTemp1Min.setText(String.format("%.1f", value))
            }
        }
        lifecycleScope.launch {
            viewModel.temp1Max.collect { value ->
                inputTemp1Max.setText(String.format("%.1f", value))
            }
        }
        lifecycleScope.launch {
            viewModel.temp2Min.collect { value ->
                inputTemp2Min.setText(String.format("%.1f", value))
            }
        }
        lifecycleScope.launch {
            viewModel.temp2Max.collect { value ->
                inputTemp2Max.setText(String.format("%.1f", value))
            }
        }
        
        lifecycleScope.launch {
            // Observe firmware version
            viewModel.firmwareVersion.collect { version ->
                tvCurrentFirmware.text = "Current Version: $version"
            }
        }
        
        lifecycleScope.launch {
            // Observe message
            viewModel.message.collect { message ->
                message?.let {
                    Toast.makeText(this@DeviceSettingsActivity, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearMessage()
                }
            }
        }
        
        lifecycleScope.launch {
            // Observe loading state
            viewModel.isLoading.collect { isLoading ->
                btnSaveSettings.isEnabled = !isLoading
                btnUpdateDevice.isEnabled = !isLoading
            }
        }
        
        lifecycleScope.launch {
            // Observe saving state
            viewModel.isSaving.collect { isSaving ->
                btnSaveSettings.text = if (isSaving) "Saving..." else "Save Settings"
                btnSaveSettings.isEnabled = !isSaving
            }
        }
        
        lifecycleScope.launch {
            // Observe firmware checking state
            viewModel.isCheckingFirmware.collect { isChecking ->
                btnUpdateDevice.text = if (isChecking) "Upgrading..." else "Upgrade Firmware"
                btnUpdateDevice.isEnabled = !isChecking
            }
        }
        
        lifecycleScope.launch {
            // Observe loading state
            viewModel.isLoading.collect { isLoading ->
                btnSaveSettings.isEnabled = !isLoading
                btnUpdateDevice.isEnabled = !isLoading
                
                if (isLoading) {
                    btnSaveSettings.text = "Loading..."
                } else {
                    btnSaveSettings.text = "Save Settings"
                }
            }
        }
        
        lifecycleScope.launch {
            // Observe saving state
            viewModel.isSaving.collect { isSaving ->
                btnSaveSettings.isEnabled = !isSaving
                btnUpdateDevice.isEnabled = !isSaving
                
                if (isSaving) {
                    btnSaveSettings.text = "Saving..."
                } else {
                    btnSaveSettings.text = "Save Settings"
                }
            }
        }
        
        lifecycleScope.launch {
            // Observe firmware checking state
            viewModel.isCheckingFirmware.collect { isChecking ->
                btnUpdateDevice.isEnabled = !isChecking
                
                if (isChecking) {
                    btnUpdateDevice.text = "Upgrading..."
                } else {
                    btnUpdateDevice.text = "Upgrade Firmware"
                }
            }
        }
        
        lifecycleScope.launch {
            // Observe messages
            viewModel.message.collect { message ->
                message?.let {
                    Toast.makeText(this@DeviceSettingsActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.clearMessage()
                }
            }
        }
        
        // Observe navigation event
        viewModel.navigateToScanDevices.observe(this@DeviceSettingsActivity) { shouldNavigate ->
            if (shouldNavigate) {
                // Navigate to ScanActivity
                val intent = Intent(this@DeviceSettingsActivity, ScanActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish() // Close current activity
                viewModel.clearNavigationEvent()
            }
        }
    }
    
    private fun updateAppIdFooter() {
        try {
            val footerLayout = findViewById<android.view.View>(R.id.scan_app_id_footer)
            if (footerLayout != null) {
                val appIdTextView = footerLayout.findViewById<TextView>(R.id.tv_scan_app_id)
                if (appIdTextView != null) {
                    val installationId = AppIdUtils.getInstallationId(this)
                    appIdTextView.text = "App ID: $installationId"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating app ID footer", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // The ViewModel will handle disconnecting the device
    }
}
