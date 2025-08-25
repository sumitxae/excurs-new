package com.minew.sensormanager.ui.activities

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.minew.sensormanager.R
import com.minew.sensormanager.databinding.ActivityMainBinding
import com.minew.sensormanager.ui.fragments.DeviceListFragment
import com.minew.sensormanager.ui.fragments.RealTimeDataFragment
import com.minew.sensormanager.ui.fragments.HistoryChartFragment
import com.minew.sensormanager.ui.fragments.AlertsFragment
import com.minew.sensormanager.ui.viewmodels.MainViewModel
import com.minew.sensormanager.utils.PermissionHelper
import com.minew.sensormanager.utils.BluetoothHelper
import com.permissionx.guolindev.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    private val bluetoothEnableResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            initializeApp()
        } else {
            showBluetoothRequiredMessage()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupBottomNavigation()
        checkBluetoothAndPermissions()
        observeViewModel()
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_devices -> {
                    showFragment(DeviceListFragment())
                    true
                }
                R.id.nav_realtime -> {
                    showFragment(RealTimeDataFragment())
                    true
                }
                R.id.nav_history -> {
                    showFragment(HistoryChartFragment())
                    true
                }
                R.id.nav_alerts -> {
                    showFragment(AlertsFragment())
                    true
                }
                else -> false
            }
        }
        
        // Show default fragment
        binding.bottomNavigation.selectedItemId = R.id.nav_devices
    }
    
    private fun showFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    private fun checkBluetoothAndPermissions() {
        // First request permissions, then check Bluetooth
        checkPermissionsAndStartApp()
    }
    
    private fun checkPermissionsAndStartApp() {
        val permissions = PermissionHelper.getRequiredPermissions()
        
        PermissionX.init(this)
            .permissions(permissions)
            .explainReasonBeforeRequest()
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    "These permissions are required for Bluetooth scanning and device management",
                    "Grant Permissions",
                    "Cancel"
                )
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(
                    deniedList,
                    "Please grant the required permissions in Settings to use this app",
                    "Go to Settings",
                    "Cancel"
                )
            }
            .request { allGranted, _, _ ->
                if (allGranted) {
                    checkBluetoothAfterPermissions()
                } else {
                    showPermissionRequiredMessage()
                }
            }
    }
    
    private fun checkBluetoothAfterPermissions() {
        when (BluetoothHelper.checkBluetoothState(this)) {
            BluetoothHelper.BluetoothState.NOT_SUPPORTED -> {
                showError("Bluetooth Low Energy is not supported on this device")
                return
            }
            BluetoothHelper.BluetoothState.DISABLED -> {
                val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothEnableResult.launch(enableIntent)
                return
            }
            BluetoothHelper.BluetoothState.ENABLED -> {
                initializeApp()
            }
        }
    }
    
    private fun initializeApp() {
        // Start background services
        lifecycleScope.launch {
            viewModel.initializeApp()
        }
    }
    
    private fun observeViewModel() {
        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                showError(it)
                viewModel.clearError()
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            // Show/hide loading indicator
            binding.progressBar.visibility = if (isLoading) 
                android.view.View.VISIBLE else android.view.View.GONE
        }
    }
    
    private fun showBluetoothRequiredMessage() {
        showError("Bluetooth is required for this app to function properly")
    }
    
    private fun showPermissionRequiredMessage() {
        showError("Required permissions must be granted to use this app. Please go to Settings > Apps > ${getString(R.string.app_name)} > Permissions and grant Bluetooth and Location permissions.")
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        lifecycleScope.launch {
            viewModel.cleanup()
        }
    }
}
