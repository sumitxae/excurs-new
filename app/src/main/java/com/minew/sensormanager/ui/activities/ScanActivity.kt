package com.minew.sensormanager.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.minew.sensormanager.BuildConfig
import com.minew.sensormanager.R
import com.minew.sensormanager.ui.adapters.DeviceListAdapter
import com.minew.sensormanager.ui.viewmodels.MainViewModel
import com.minew.sensormanager.data.models.DeviceInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanActivity : AppCompatActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_devices)
        // Force dark status bar icons on light background across APIs
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        // Start scanning automatically
        startScan()
    }
    
    private fun setupUI() {
        recyclerView = findViewById(R.id.recyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener { startScan() }
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = DeviceListAdapter(
            onDeviceClick = { device ->
                showDeviceDetails(device)
            },
            onConnectClick = { device ->
                connectToDevice(device)
            },
            onDisconnectClick = { device ->
                disconnectDevice(device)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this@ScanActivity)
        recyclerView.adapter = deviceAdapter
        // Disable change animations to avoid pulsing/beating effect on frequent updates
        (recyclerView.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.apply {
            supportsChangeAnimations = false
            changeDuration = 0
            addDuration = 0
            removeDuration = 0
            moveDuration = 0
        }
    }
    
    private fun setupClickListeners() {
        // Scan functionality is now handled by floating button and swipe refresh
    }
    
    private fun observeViewModel() {
        viewModel.discoveredDevices.observe(this) { devices ->
            deviceAdapter.updateDevices(devices)
            // Show loader only if there exists any device without a valid temperature
            val shouldShowRefreshing = devices.isNotEmpty() && devices.any { it.temperature == null }
            swipeRefreshLayout.isRefreshing = shouldShowRefreshing
        }
        
        viewModel.connectionStates.observe(this) { states ->
            deviceAdapter.updateConnectionStates(states)
        }
        
        viewModel.errorMessage.observe(this) { errorMessage ->
            errorMessage?.let {
                showError(it)
                viewModel.clearError()
            }
        }
    }
    
    private fun startScan() {
        viewModel.startContinuousScanning(this@ScanActivity)
    }
    
    private fun stopScan() {
        viewModel.stopScanning(this@ScanActivity)
    }
    

    
    private fun showDeviceDetails(device: DeviceInfo) {
        // For now, directly connect or handle as needed
        connectToDevice(device)
    }
    
    private fun hideDeviceDetails() {
        // no-op with simplified UI
    }
    
    private fun connectToDevice(device: DeviceInfo) {
        // Navigate to device details screen
        val intent = Intent(this@ScanActivity, DeviceDetailsActivity::class.java)
        intent.putExtra(DeviceDetailsActivity.EXTRA_DEVICE_MAC, device.macAddress)
        startActivity(intent)
    }
    
    private fun disconnectDevice(device: DeviceInfo) {
        viewModel.disconnectDevice(device.macAddress)
    }
    
    private fun showMenu() {
        // TODO: Implement menu popup
        showError("Menu not implemented yet")
    }
    
    private fun showBeaconLogger() {
        // TODO: Implement beacon logger
        showError("Beacon logger not implemented yet")
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
    
    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
    
    override fun onResume() {
        super.onResume()
        // Resume scanning when returning to this activity
        Log.d("ScanActivity", "onResume: Resuming scanning")
        startScan()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop scanning when leaving this activity
        Log.d("ScanActivity", "onPause: Stopping scanning")
        stopScan()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopScan()
    }
}
