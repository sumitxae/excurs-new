package com.minew.sensormanager.ui.activities

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.minew.sensormanager.R
import com.minew.sensormanager.databinding.ActivityMainBinding
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
        // Force dark status bar icons on light background across APIs
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        
        checkBluetoothAndPermissions()
        observeViewModel()
    }
    
    private fun checkBluetoothAndPermissions() {
        checkPermissionsAndStartApp()
    }
    
    private fun checkPermissionsAndStartApp() {
        val permissions = PermissionHelper.getRequiredPermissions()
        
        PermissionX.init(this)
            .permissions(permissions)
            .explainReasonBeforeRequest()
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
        lifecycleScope.launch {
            viewModel.initializeApp()
            // Navigate to the Scan screen after initialization
            startActivity(Intent(this@MainActivity, ScanActivity::class.java))
            // Optionally finish MainActivity to avoid back stack white screen
            finish()
        }
    }
    
    private fun observeViewModel() {
        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                showError(it)
                viewModel.clearError()
            }
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
        lifecycleScope.launch {
            viewModel.cleanup()
        }
    }
}
