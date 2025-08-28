package com.minew.sensormanager.ui.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import androidx.appcompat.widget.PopupMenu
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.minew.sensormanager.ui.base.BasePermissionActivity
import com.minew.sensormanager.permissions.PermissionType
import com.minew.sensormanager.permissions.PermissionDialogType
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.minew.sensormanager.BuildConfig
import com.minew.sensormanager.R
import com.minew.sensormanager.auth.AuthManager
import com.minew.sensormanager.ui.adapters.DeviceListAdapter
import com.minew.sensormanager.ui.viewmodels.MainViewModel
import com.minew.sensormanager.data.models.DeviceInfo
import com.minew.sensormanager.utils.AppIdUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanActivity : BasePermissionActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceListAdapter
    private var isConnectingToSettings: Boolean = false
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchEditText: EditText
    private lateinit var clearSearchButton: ImageView
    private lateinit var appIdTextView: android.widget.TextView
    
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == RESULT_OK) {
                val scanResult = result.data?.getStringExtra(QRScannerActivity.EXTRA_SCAN_RESULT)
                scanResult?.let { qrCode ->
                    Log.d("ScanActivity", "QR Code scanned: $qrCode")
                    handleQRCodeResult(qrCode)
                }
            } else if (result.resultCode == RESULT_CANCELED) {
                Log.d("ScanActivity", "QR scanner cancelled")
            }
        } catch (e: Exception) {
            Log.e("ScanActivity", "Error handling QR scanner result", e)
            Toast.makeText(this, "Error processing QR scan result", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_devices)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }
    
    override fun onAllPermissionsGranted() {
        super.onAllPermissionsGranted()
        Log.d("ScanActivity", "All permissions granted - starting scan")
        // Start scanning only when permissions are granted
        startScan()
    }
    
    override fun onPermissionFlowComplete() {
        super.onPermissionFlowComplete()
        Log.d("ScanActivity", "Permission flow completed - starting scan")
        // Start scanning even if some permissions are missing (reduced functionality)
        startScan()
    }
    
    override fun onCriticalPermissionMissing(missingPermissions: List<PermissionType>) {
        super.onCriticalPermissionMissing(missingPermissions)
        Log.d("ScanActivity", "Critical permissions missing: $missingPermissions")
        // You can show a custom message here if needed
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.scan_overflow_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val authItem = menu.findItem(R.id.action_auth)
        val isLoggedIn = AuthManager.getInstance(this).isLoggedIn()
        authItem.title = if (isLoggedIn) "Logout" else "Login"
        return super.onPrepareOptionsMenu(menu)
    }
    
    private fun setupUI() {
        recyclerView = findViewById(R.id.recyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener { startScan() }
        
        searchEditText = findViewById(R.id.et_search)
        clearSearchButton = findViewById(R.id.btn_clear_search)
        
        val footerLayout = findViewById<android.view.View>(R.id.scan_app_id_footer)
        if (footerLayout != null) {
            appIdTextView = footerLayout.findViewById(R.id.tv_scan_app_id)
            if (appIdTextView != null) {
                Log.d("ScanActivity", "App ID TextView found successfully")
                updateAppIdFooter()
            } else {
                Log.e("ScanActivity", "Could not find tv_scan_app_id TextView")
            }
        } else {
            Log.e("ScanActivity", "Could not find scan_app_id_footer layout")
        }
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
            },
            onSettingsClick = { device ->
                openDeviceSettings(device)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this@ScanActivity)
        recyclerView.adapter = deviceAdapter
        (recyclerView.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.apply {
            supportsChangeAnimations = false
            changeDuration = 0
            addDuration = 0
            removeDuration = 0
            moveDuration = 0
        }
    }
    
    private fun setupClickListeners() {
        setupSearchFunctionality()
        
        findViewById<android.view.View>(R.id.btn_scan_qr).setOnClickListener {
            launchQRScanner()
        }
        
        // Test permission dialog - remove this after testing
        findViewById<android.view.View>(R.id.btn_scan_qr)?.setOnLongClickListener {
            testPermissionDialog()
            true
        }
        
        // Kebab menu above the scan button
        findViewById<ImageButton>(R.id.btn_kebab_menu)?.setOnClickListener { anchor ->
            try {
                val popup = PopupMenu(this, anchor)
                popup.menuInflater.inflate(R.menu.scan_overflow_menu, popup.menu)
                val isLoggedIn = AuthManager.getInstance(this).isLoggedIn()
                popup.menu.findItem(R.id.action_auth)?.title = if (isLoggedIn) "Logout" else "Login"
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_auth -> {
                            val authManager = AuthManager.getInstance(this)
                            if (authManager.isLoggedIn()) {
                                authManager.logout()
                                popup.dismiss()
                                anchor.post {
                                    val intent = Intent(this, EntryActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finishAffinity()
                                }
                            } else {
                                startActivity(Intent(this, SignInActivity::class.java))
                            }
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            } catch (e: Exception) {
                Log.e("ScanActivity", "Error showing popup menu", e)
                showError("Unable to open menu")
            }
        }
    }
    
    private fun launchQRScanner() {
        try {
            Log.d("ScanActivity", "Launching QR scanner")
            val intent = Intent(this, QRScannerActivity::class.java)
            qrScannerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("ScanActivity", "Error launching QR scanner", e)
            Toast.makeText(this, "Error launching QR scanner", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleQRCodeResult(qrCode: String) {
        Log.d("ScanActivity", "Handling QR code result: $qrCode")
        
        searchEditText.setText(qrCode)
        viewModel.setSearchQuery(qrCode)
        updateClearButtonVisibility(true)
        
        parseQRCodeForDeviceInfo(qrCode)
    }
    
    private fun parseQRCodeForDeviceInfo(qrCode: String) {
        when {
            qrCode.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")) -> {
                Log.d("ScanActivity", "QR code contains MAC address: $qrCode")
            }
            qrCode.length > 0 -> {
                Log.d("ScanActivity", "QR code contains device info: $qrCode")
            }
        }
    }
    
    private fun setupSearchFunctionality() {
        Log.d("ScanActivity", "Setting up search functionality")
        
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                Log.d("ScanActivity", "beforeTextChanged: '$s'")
            }
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                Log.d("ScanActivity", "onTextChanged: '$s'")
            }
            
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                Log.d("ScanActivity", "afterTextChanged: '$query'")
                viewModel.setSearchQuery(query)
                updateClearButtonVisibility(query.isNotEmpty())
            }
        })
        
        clearSearchButton.setOnClickListener {
            Log.d("ScanActivity", "Clear search clicked")
            searchEditText.setText("")
            viewModel.clearSearch()
            updateClearButtonVisibility(false)
        }
        
        updateClearButtonVisibility(false)
    }
    
    private fun updateClearButtonVisibility(show: Boolean) {
        clearSearchButton.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun observeViewModel() {
        viewModel.filteredDevices.observe(this) { devices ->
            Log.d("ScanActivity", "Filtered devices updated: ${devices.size} devices")
            deviceAdapter.updateDevices(devices)
            val shouldShowRefreshing = devices.isEmpty()
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
        connectToDevice(device)
    }
    
    private fun hideDeviceDetails() {
    }
    
    private fun connectToDevice(device: DeviceInfo) {
        val intent = Intent(this@ScanActivity, DeviceDetailsActivity::class.java)
        intent.putExtra(DeviceDetailsActivity.EXTRA_DEVICE_MAC, device.macAddress)
        startActivity(intent)
    }
    
    private fun disconnectDevice(device: DeviceInfo) {
        viewModel.disconnectDevice(device.macAddress)
    }
    
    private fun openDeviceSettings(device: DeviceInfo) {
        if (isConnectingToSettings) return
        isConnectingToSettings = true
        // Pre-connect with themed loader overlay, then navigate only on success
        val mac = device.macAddress
        val overlay = findViewById<android.view.View>(R.id.fl_connect_overlay)
        overlay?.visibility = View.VISIBLE
        // Stop scanning before connecting as per SDK docs
        viewModel.stopScanning(this)
        lifecycleScope.launch {
            try {
                val connected = kotlinx.coroutines.withTimeout(30000) {
                    viewModel.connectToDevice(this@ScanActivity, mac, "minewtech1234567")
                }
                if (connected) {
                    val intent = Intent(this@ScanActivity, DeviceSettingsActivity::class.java)
                    intent.putExtra(DeviceSettingsActivity.EXTRA_DEVICE_MAC, mac)
                    intent.putExtra(DeviceSettingsActivity.EXTRA_DEVICE_INFO, device)
                    startActivity(intent)
                } else {
                    Toast.makeText(this@ScanActivity, "Failed to connect to device", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ScanActivity, "Connection timed out or failed", Toast.LENGTH_LONG).show()
            } finally {
                overlay?.visibility = View.GONE
                isConnectingToSettings = false
            }
        }
    }
    
    private fun showMenu() {
        showError("Menu not implemented yet")
    }
    
    private fun showBeaconLogger() {
        showError("Beacon logger not implemented yet")
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_auth -> {
                val authManager = AuthManager.getInstance(this)
                if (authManager.isLoggedIn()) {
                    authManager.logout()
                    val intent = Intent(this, EntryActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finishAffinity()
                } else {
                    startActivity(Intent(this, SignInActivity::class.java))
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
    
    // Test function to manually show permission dialog
    private fun testPermissionDialog() {
        Log.d("ScanActivity", "Showing test permission dialog for BLUETOOTH")
        val dialog = com.minew.sensormanager.ui.dialogs.PermissionDialogFragment.newInstance(
            PermissionType.BLUETOOTH,
            PermissionDialogType.INITIAL_REQUEST
        )
        
        dialog.setOnPrimaryButtonClickListener {
            Log.d("ScanActivity", "Primary button clicked")
            android.widget.Toast.makeText(this, "Primary button clicked", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        dialog.setOnSecondaryButtonClickListener {
            Log.d("ScanActivity", "Secondary button clicked")
            android.widget.Toast.makeText(this, "Secondary button clicked", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        dialog.show(supportFragmentManager, "test_permission_dialog")
    }
    
    /**
     * Updates the app ID footer with the actual installation ID.
     */
    private fun updateAppIdFooter() {
        try {
            val installationId = AppIdUtils.getInstallationId(this)
            Log.d("ScanActivity", "Updating app ID footer with: $installationId")
            appIdTextView.text = "App ID: $installationId"
            Log.d("ScanActivity", "App ID footer updated successfully")
        } catch (e: Exception) {
            Log.e("ScanActivity", "Error updating app ID footer", e)
            appIdTextView.text = "App ID: Error"
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d("ScanActivity", "onResume: Resuming scanning")
        startScan()
        invalidateOptionsMenu()
    }
    
    override fun onPause() {
        super.onPause()
        Log.d("ScanActivity", "onPause: Stopping scanning")
        stopScan()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopScan()
    }
}
