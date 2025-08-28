package com.choruscoldchain.ui.activities

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.choruscoldchain.R
import com.choruscoldchain.utils.PermissionHelper
import com.choruscoldchain.utils.BluetoothHelper
import com.choruscoldchain.utils.AppIdUtils
import com.permissionx.guolindev.PermissionX

class EntryActivity : AppCompatActivity() {
    
    private val bluetoothEnableResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Bluetooth enabled, continue with the selected action
            handleSelectedAction()
        } else {
            showBluetoothRequiredMessage()
        }
    }
    
    private var selectedAction: (() -> Unit)? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry)
        
        // Force dark status bar icons on light background across APIs
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        
        // Request all runtime permissions (including Storage) on startup
        requestAllRuntimePermissionsOnStartup()

        setupClickListeners()
        updateAppIdFooter()
    }
    
    private fun setupClickListeners() {
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUser).setOnClickListener {
            selectedAction = { navigateToUserMode() }
            checkPermissionsAndBluetooth()
        }
        
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAdmin).setOnClickListener {
            selectedAction = { navigateToAdminMode() }
            checkPermissionsAndBluetooth()
        }
    }
    
    private fun checkPermissionsAndBluetooth() {
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

    private fun requestAllRuntimePermissionsOnStartup() {
        val permissions = PermissionHelper.getRequiredPermissions()
        PermissionX.init(this)
            .permissions(permissions)
            .explainReasonBeforeRequest()
            .request { _, _, _ ->
                // No-op: this is just to surface the dialogs at startup
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
                handleSelectedAction()
            }
        }
    }
    
    private fun handleSelectedAction() {
        selectedAction?.invoke()
    }
    
    private fun navigateToUserMode() {
        try {
            // Navigate directly to ScanActivity (main scan screen)
            val intent = Intent(this, ScanActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("EntryActivity", "Error navigating to user mode", e)
            showError("Error starting user mode")
        }
    }
    
    private fun navigateToAdminMode() {
        try {
            // Navigate to SignInActivity for admin login
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("EntryActivity", "Error navigating to admin mode", e)
            showError("Error starting admin mode")
        }
    }
    
    private fun updateAppIdFooter() {
        try {
            val footerLayout = findViewById<android.view.View>(R.id.app_id_footer)
            if (footerLayout != null) {
                val appIdTextView = footerLayout.findViewById<android.widget.TextView>(R.id.tv_scan_app_id)
                if (appIdTextView != null) {
                    val installationId = AppIdUtils.getInstallationId(this)
                    appIdTextView.text = "App ID: $installationId"
                    Log.d("EntryActivity", "App ID footer updated successfully")
                } else {
                    Log.e("EntryActivity", "Could not find tv_scan_app_id TextView")
                }
            } else {
                Log.e("EntryActivity", "Could not find app_id_footer layout")
            }
        } catch (e: Exception) {
            Log.e("EntryActivity", "Error updating app ID footer", e)
        }
    }
    
    private fun showBluetoothRequiredMessage() {
        showError("Bluetooth is required for this app to function properly")
    }
    
    private fun showPermissionRequiredMessage() {
        showError("Required permissions must be granted to use this app. Please go to Settings > Apps > ${getString(R.string.app_name)} > Permissions and grant Bluetooth, Location, and Camera permissions.")
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
