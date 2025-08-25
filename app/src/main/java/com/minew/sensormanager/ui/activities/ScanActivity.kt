package com.minew.sensormanager.ui.activities

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.minew.sensormanager.databinding.ActivityScanBinding
import com.minew.sensormanager.ui.adapters.DeviceListAdapter
import com.minew.sensormanager.ui.viewmodels.ScanViewModel
import com.minew.sensormanager.data.models.DeviceInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityScanBinding
    private val viewModel: ScanViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceListAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        // Start scanning automatically
        startScan()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Scan for Devices"
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = DeviceListAdapter(
            onDeviceClick = { device ->
                addDevice(device)
            },
            onConnectClick = { device ->
                connectToDevice(device)
            },
            onDisconnectClick = { device ->
                disconnectDevice(device)
            }
        )
        
        binding.recyclerViewDevices.apply {
            layoutManager = LinearLayoutManager(this@ScanActivity)
            adapter = deviceAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.buttonStartScan.setOnClickListener {
            startScan()
        }
        
        binding.buttonStopScan.setOnClickListener {
            stopScan()
        }
    }
    
    private fun observeViewModel() {
        viewModel.scanResults.observe(this) { devices ->
            deviceAdapter.updateDevices(devices)
            
            // Show empty state if no devices
            binding.emptyStateLayout.visibility = if (devices.isEmpty()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
        
        viewModel.isScanning.observe(this) { isScanning ->
            binding.buttonStartScan.isEnabled = !isScanning
            binding.buttonStopScan.isEnabled = isScanning
            binding.progressBar.visibility = if (isScanning) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
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
        lifecycleScope.launch {
            viewModel.startScan(this@ScanActivity)
        }
    }
    
    private fun stopScan() {
        viewModel.stopScan(this)
    }
    
    private fun addDevice(device: DeviceInfo) {
        lifecycleScope.launch {
            viewModel.addDevice(device)
            finish()
        }
    }
    
    private fun connectToDevice(device: DeviceInfo) {
        lifecycleScope.launch {
            viewModel.connectToDevice(device.macAddress)
        }
    }
    
    private fun disconnectDevice(device: DeviceInfo) {
        viewModel.disconnectDevice(device.macAddress)
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
    
    override fun onDestroy() {
        super.onDestroy()
        stopScan()
    }
}
