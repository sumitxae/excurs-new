package com.minew.sensormanager.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.minew.sensormanager.R
import com.minew.sensormanager.data.models.DeviceInfo
import com.minew.sensormanager.data.models.ConnectionState
import com.minew.sensormanager.databinding.ActivityDeviceDetailsBinding
import com.minew.sensormanager.ui.viewmodels.DeviceDetailsViewModel
import com.minew.sensormanager.ui.adapters.AvailableMethodsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DeviceDetailsActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_DEVICE_MAC = "device_mac"
        private const val TAG = "DeviceDetailsActivity"
    }
    
    private lateinit var binding: ActivityDeviceDetailsBinding
    private val viewModel: DeviceDetailsViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        
        // Get device MAC from intent
        val deviceMac = intent.getStringExtra(EXTRA_DEVICE_MAC)
        if (deviceMac != null) {
            viewModel.setDeviceMac(deviceMac)
            viewModel.loadDeviceDetails()
            
            // Setup debug button
            binding.buttonShowMethodsDebug.setOnClickListener {
                viewModel.forceShowMethods()
            }
            
            // Setup test methods button
            binding.buttonTestMethods.setOnClickListener {
                viewModel.forceShowMethods()
                Log.d(TAG, "Test methods button clicked")
                
                // Test if the layout is working by setting text directly
                binding.textMethodResult.text = "Test Methods button clicked! Layout is working."
                
                // Also test directly setting methods
                val testMethods = listOf(
                    "Test Method 1",
                    "Test Method 2", 
                    "Test Method 3"
                )
                Log.d(TAG, "Setting test methods directly: $testMethods")
                binding.recyclerViewMethods.adapter = AvailableMethodsAdapter(testMethods) { method ->
                    Log.d(TAG, "Test method clicked: $method")
                    binding.textMethodResult.text = "Test method '$method' clicked!"
                }
            }
            
            // Setup copy buttons
            binding.buttonCopyResult.setOnClickListener {
                copyMethodResultToClipboard()
            }
            binding.buttonCopySensorData.setOnClickListener {
                copySensorDataToClipboard()
            }
            binding.buttonCopyConfigData.setOnClickListener {
                copyConfigDataToClipboard()
            }
        } else {
            Log.e(TAG, "No device MAC provided")
            finish()
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Device Details"
    }
    
    private fun setupRecyclerView() {
        binding.recyclerViewMethods.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        Log.d(TAG, "RecyclerView setup complete")
    }
    
    private fun setupObservers() {
        // Observe device info
        viewModel.deviceInfo.observe(this) { device ->
            updateDeviceInfo(device)
        }
        
        // Observe connection state
        viewModel.connectionState.observe(this) { state ->
            updateConnectionState(state)
        }
        
        // Always show methods for testing
        viewModel.availableMethods.observe(this) { methods ->
            Log.d(TAG, "Available methods received: ${methods.size} methods")
            Log.d(TAG, "Methods: $methods")
            updateAvailableMethods(methods)
        }
        
        // Observe sensor data
        viewModel.sensorData.observe(this) { data ->
            updateSensorData(data)
        }
        
        // Observe configuration data
        viewModel.configurationData.observe(this) { config ->
            updateConfigurationData(config)
        }
        
        // Observe available methods
        viewModel.availableMethods.observe(this) { methods ->
            updateAvailableMethods(methods)
        }
        
        // Observe method results
        viewModel.methodResult.observe(this) { result ->
            updateMethodResult(result)
        }
    }
    
    private fun updateDeviceInfo(device: DeviceInfo?) {
        device?.let {
            binding.apply {
                textDeviceName.text = it.name
                textMacAddress.text = it.macAddress
                textFirmwareVersion.text = it.firmwareVersion
                textBatteryLevel.text = "${it.battery}%"
                textRssi.text = "${it.rssi} dBm"
                textLastSeen.text = formatTimestamp(it.lastSeen)
            }
        }
    }
    
    private fun updateConnectionState(state: ConnectionState) {
        Log.d(TAG, "updateConnectionState called with state: $state")
        binding.apply {
            when (state) {
                ConnectionState.READY -> {
                    textConnectionStatus.text = "Connected"
                    textConnectionStatus.setTextColor(getColor(R.color.status_connected))
                    layoutConnectedContent.visibility = android.view.View.VISIBLE
                    layoutDisconnectedContent.visibility = android.view.View.GONE
                    Log.d(TAG, "Showing connected content")
                }
                ConnectionState.CONNECTING -> {
                    textConnectionStatus.text = "Connecting..."
                    textConnectionStatus.setTextColor(getColor(R.color.status_connecting))
                    layoutConnectedContent.visibility = android.view.View.GONE
                    layoutDisconnectedContent.visibility = android.view.View.VISIBLE
                    Log.d(TAG, "Showing disconnected content (connecting)")
                }
                ConnectionState.CONNECTED -> {
                    textConnectionStatus.text = "Connected"
                    textConnectionStatus.setTextColor(getColor(R.color.status_connected))
                    layoutConnectedContent.visibility = android.view.View.VISIBLE
                    layoutDisconnectedContent.visibility = android.view.View.GONE
                    Log.d(TAG, "Showing connected content")
                }
                else -> {
                    textConnectionStatus.text = "Disconnected"
                    textConnectionStatus.setTextColor(getColor(R.color.status_disconnected))
                    layoutConnectedContent.visibility = android.view.View.GONE
                    layoutDisconnectedContent.visibility = android.view.View.VISIBLE
                    Log.d(TAG, "Showing disconnected content (disconnected)")
                }
            }
        }
    }
    
    private fun updateSensorData(data: String?) {
        data?.let {
            binding.textSensorData.text = it
            // Show copy button when there's sensor data
            binding.buttonCopySensorData.visibility = android.view.View.VISIBLE
        } ?: run {
            // Hide copy button when there's no sensor data
            binding.buttonCopySensorData.visibility = android.view.View.GONE
        }
    }
    
    private fun updateConfigurationData(config: String?) {
        config?.let {
            binding.textConfigurationData.text = it
            // Show copy button when there's configuration data
            binding.buttonCopyConfigData.visibility = android.view.View.VISIBLE
        } ?: run {
            // Hide copy button when there's no configuration data
            binding.buttonCopyConfigData.visibility = android.view.View.GONE
        }
    }
    
    private fun updateAvailableMethods(methods: List<String>) {
        Log.d(TAG, "updateAvailableMethods called with ${methods.size} methods")
        if (methods.isNotEmpty()) {
            binding.recyclerViewMethods.adapter = AvailableMethodsAdapter(methods) { method ->
                executeMethod(method)
            }
            Log.d(TAG, "Adapter set with ${methods.size} methods")
        } else {
            Log.d(TAG, "No methods to display")
        }
    }
    
    private fun updateMethodResult(result: String?) {
        result?.let {
            binding.textMethodResult.text = it
            // Show copy button when there's a result
            binding.buttonCopyResult.visibility = android.view.View.VISIBLE
        } ?: run {
            // Hide copy button when there's no result
            binding.buttonCopyResult.visibility = android.view.View.GONE
        }
    }
    
    private fun executeMethod(method: String) {
        lifecycleScope.launch {
            when (method) {
                // Query Methods
                "Query Firmware Info" -> viewModel.queryFirmwareInfo()
                "Query Temperature History" -> viewModel.queryTemperatureHistory()
                "Query Light History Data" -> viewModel.queryLightHistoryData()
                "Query Temperature Sensor Configuration" -> viewModel.queryTemperatureSensorConfiguration()
                "Query Light Intensity Configuration" -> viewModel.queryLightIntensityConfiguration()
                "Query Alarm Strategy Configuration" -> viewModel.queryAlarmStrategyConfiguration()
                "Query Advertising Parameters" -> viewModel.queryAdvertisingParameters()
                
                // Configuration Methods
                "Set Advertising Parameters" -> {
                    // For now, use default values - in a real app, you'd show a dialog
                    viewModel.setAdvertisingParameters("CUSTOM_COMBINATION_FRAME", 0, 1000, 0, "MST03")
                }
                "Set LED Configuration" -> {
                    // For now, use default values - in a real app, you'd show a dialog
                    viewModel.setLEDConfiguration(3, 10, 200, 200, 100)
                }
                "Change Secret Key" -> {
                    // For now, use default values - in a real app, you'd show a dialog
                    viewModel.changeSecretKey("newSecretKey123")
                }
                "Set HT Sensor Configuration" -> {
                    // For now, use default values - in a real app, you'd show a dialog
                    val htSettingData = listOf<Any>() // Empty list for now
                    viewModel.setHTSensorConfiguration(60, 0, htSettingData)
                }
                "Set Light Intensity Configuration" -> {
                    // For now, use default values - in a real app, you'd show a dialog
                    viewModel.setLightIntensityConfiguration(130, 13000)
                }
                "Set Alarm Strategy Configuration" -> {
                    // For now, use default values - in a real app, you'd show a dialog
                    viewModel.setAlarmStrategyConfiguration(600, 5)
                }
                
                // Data Management Methods
                "Clear Temperature History" -> viewModel.clearTemperatureHistory()
                "Clear Light History" -> viewModel.clearLightHistory()
                
                // Device Control Methods
                "Reset Device" -> viewModel.resetDevice()
                "Power Off Device" -> viewModel.powerOffDevice()
                "Firmware Upgrade" -> viewModel.firmwareUpgrade()
                
                else -> Log.w(TAG, "Unknown method: $method")
            }
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun copyMethodResultToClipboard() {
        val resultText = binding.textMethodResult.text.toString()
        if (resultText.isNotEmpty()) {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("Method Result", resultText)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(this, "Result copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No result to copy", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun copySensorDataToClipboard() {
        val sensorText = binding.textSensorData.text.toString()
        if (sensorText.isNotEmpty()) {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("Sensor Data", sensorText)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(this, "Sensor data copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No sensor data to copy", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun copyConfigDataToClipboard() {
        val configText = binding.textConfigurationData.text.toString()
        if (configText.isNotEmpty()) {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("Configuration Data", configText)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(this, "Configuration data copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No configuration data to copy", Toast.LENGTH_SHORT).show()
        }
    }
}
