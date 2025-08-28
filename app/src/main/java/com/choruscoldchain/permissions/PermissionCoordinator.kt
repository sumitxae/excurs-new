package com.choruscoldchain.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.choruscoldchain.ui.dialogs.PermissionDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Permission coordinator that orchestrates the permission flow and handles all scenarios
 * including first app launch, permission denial, runtime revocation, and more.
 */
@Singleton
class PermissionCoordinator @Inject constructor(
    private val context: Context,
    private val permissionManager: PermissionManager
) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "PermissionCoordinator"
    }
    
    private var currentActivity: FragmentActivity? = null
    private var isDialogShowing = false
    private var pendingPermissionRequests = mutableListOf<PermissionRequest>()
    
    // Service state monitoring
    private var serviceStateReceiver: ServiceStateReceiver? = null
    private var isServiceMonitoringActive = false
    private var isNavigatingToSystemSettings = false
    
    // Callbacks
    private var onAllPermissionsGranted: (() -> Unit)? = null
    private var onCriticalPermissionMissing: ((List<PermissionType>) -> Unit)? = null
    private var onPermissionFlowComplete: (() -> Unit)? = null
    
    /**
     * Initialize the coordinator with activity lifecycle
     */
    fun initialize(activity: FragmentActivity, lifecycleOwner: LifecycleOwner) {
        currentActivity = activity
        permissionManager.initialize(activity)
        
        // Observe lifecycle
        lifecycleOwner.lifecycle.addObserver(this)
        
        // Setup permission manager callbacks
        setupPermissionManagerCallbacks()
        
        // Setup service state monitoring
        setupServiceStateMonitoring()
        
        // Observe permission state changes
        lifecycleOwner.lifecycleScope.launch {
            permissionManager.permissionState.collectLatest { state ->
                handlePermissionStateChange(state)
            }
        }
        
        // Observe permission requests
        lifecycleOwner.lifecycleScope.launch {
            permissionManager.permissionRequests.collectLatest { requests ->
                handlePermissionRequestsChange(requests)
            }
        }
    }
    
    /**
     * Setup callbacks for permission events
     */
    fun setCallbacks(
        onAllPermissionsGranted: (() -> Unit)? = null,
        onCriticalPermissionMissing: ((List<PermissionType>) -> Unit)? = null,
        onPermissionFlowComplete: (() -> Unit)? = null
    ) {
        this.onAllPermissionsGranted = onAllPermissionsGranted
        this.onCriticalPermissionMissing = onCriticalPermissionMissing
        this.onPermissionFlowComplete = onPermissionFlowComplete
    }
    
    /**
     * Start the permission flow based on current state
     */
    fun startPermissionFlow() {
        val state = permissionManager.getCurrentPermissionState()
        
        when {
            state.hasAllCriticalPermissions -> {
                // All critical permissions granted
                onAllPermissionsGranted?.invoke()
                onPermissionFlowComplete?.invoke()
            }
            permissionManager.isFirstLaunch() -> {
                // First app launch - show onboarding
                handleFirstAppLaunch()
            }
            state.hasAnyDeniedPermissions -> {
                // Handle denied permissions
                handleDeniedPermissions(state)
            }
            else -> {
                // No permissions needed
                onPermissionFlowComplete?.invoke()
            }
        }
    }
    
    /**
     * Handle first app launch scenario
     */
    private fun handleFirstAppLaunch() {
        Log.d(TAG, "Handling first app launch")
        
        val state = permissionManager.getCurrentPermissionState()
        val criticalPermissions = state.missingCriticalPermissions
        
        if (criticalPermissions.isNotEmpty()) {
            // Show permission onboarding
            showPermissionOnboarding(criticalPermissions)
        } else {
            // No critical permissions needed
            permissionManager.markFirstLaunchCompleted()
            onPermissionFlowComplete?.invoke()
        }
    }
    
    /**
     * Handle denied permissions scenario
     */
    private fun handleDeniedPermissions(state: PermissionState) {
        Log.d(TAG, "Handling denied permissions: ${state.totalMissingPermissions} missing")
        
        val requests = permissionManager.permissionRequests.value
        
        if (requests.isNotEmpty()) {
            // Process permission requests
            processPermissionRequests(requests)
        } else {
            // No pending requests
            onPermissionFlowComplete?.invoke()
        }
    }
    
    /**
     * Process permission requests
     */
    private fun processPermissionRequests(requests: List<PermissionRequest>) {
        if (isDialogShowing) {
            // Dialog already showing, queue the request
            pendingPermissionRequests.addAll(requests)
            return
        }
        
        val nextRequest = requests.firstOrNull()
        if (nextRequest != null) {
            showPermissionDialog(nextRequest)
        } else {
            onPermissionFlowComplete?.invoke()
        }
    }
    
    /**
     * Show permission dialog for a specific request
     */
    private fun showPermissionDialog(request: PermissionRequest) {
        val activity = currentActivity ?: return
        
        isDialogShowing = true
        
        val dialogType = when {
            request.isPermanentlyDenied -> PermissionDialogType.SETTINGS_REDIRECT
            request.shouldShowRationale -> PermissionDialogType.RATIONALE
            else -> PermissionDialogType.INITIAL_REQUEST
        }
        
        val dialog = PermissionDialogFragment.newInstance(
            request.permissionType,
            dialogType
        )
        
        // Setup dialog callbacks
        dialog.setOnPrimaryButtonClickListener {
            handlePrimaryButtonClick(request, dialogType)
        }
        
        dialog.setOnSecondaryButtonClickListener {
            handleSecondaryButtonClick(request, dialogType)
        }
        
        dialog.setOnDismissListener {
            isDialogShowing = false
            // After a permission is granted/denied, request the next one
            requestNextPermission()
        }
        
        // Show dialog
        if (!activity.isFinishing && !activity.isDestroyed) {
            dialog.show(activity.supportFragmentManager, "permission_dialog")
        }
    }
    
    /**
     * Handle primary button click in permission dialog
     */
    private fun handlePrimaryButtonClick(request: PermissionRequest, dialogType: PermissionDialogType) {
        when (dialogType) {
            PermissionDialogType.INITIAL_REQUEST,
            PermissionDialogType.RATIONALE -> {
                // Request permission
                permissionManager.requestPermission(request.permissionType)
            }
            PermissionDialogType.SETTINGS_REDIRECT,
            PermissionDialogType.RUNTIME_REVOCATION,
            PermissionDialogType.SERVICE_DISABLED -> {
                // Open settings
                permissionManager.openAppSettings()
            }
            PermissionDialogType.PARTIAL_GRANT -> {
                // Request remaining permissions
                permissionManager.requestPermission(request.permissionType)
            }
        }
    }
    
    /**
     * Handle secondary button click in permission dialog
     */
    private fun handleSecondaryButtonClick(request: PermissionRequest, dialogType: PermissionDialogType) {
        when (dialogType) {
            PermissionDialogType.INITIAL_REQUEST,
            PermissionDialogType.PARTIAL_GRANT -> {
                // User chose "Not Now" - close the app for critical permissions
                Log.d(TAG, "User chose 'Not Now' for ${request.permissionType}")
                if (request.permissionType.isCritical) {
                    closeApplication()
                }
            }
            PermissionDialogType.RATIONALE,
            PermissionDialogType.SETTINGS_REDIRECT,
            PermissionDialogType.RUNTIME_REVOCATION,
            PermissionDialogType.SERVICE_DISABLED -> {
                // User cancelled - close the application for critical permissions
                Log.d(TAG, "User cancelled permission request for ${request.permissionType} - closing app")
                if (request.permissionType.isCritical) {
                    closeApplication()
                }
            }
        }
    }
    
    /**
     * Close the application
     */
    private fun closeApplication() {
        val activity = currentActivity
        if (activity != null) {
            Log.d(TAG, "Closing application due to user cancellation of critical permission")
            activity.finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
    
    /**
     * Process next pending request
     */
    private fun processNextRequest() {
        if (pendingPermissionRequests.isNotEmpty()) {
            val nextRequest = pendingPermissionRequests.removeAt(0)
            showPermissionDialog(nextRequest)
        } else {
            // Check if we need to show more dialogs
            val currentRequests = permissionManager.permissionRequests.value
            if (currentRequests.isNotEmpty()) {
                processPermissionRequests(currentRequests)
            } else {
                onPermissionFlowComplete?.invoke()
            }
        }
    }
    
    /**
     * Request next permission in recommended order
     */
    fun requestNextPermission() {
        val currentRequests = permissionManager.permissionRequests.value
        if (currentRequests.isNotEmpty()) {
            val nextRequest = currentRequests.first()
            showPermissionDialog(nextRequest)
        } else {
            onPermissionFlowComplete?.invoke()
        }
    }
    
    /**
     * Show permission onboarding for first launch
     */
    private fun showPermissionOnboarding(criticalPermissions: List<PermissionType>) {
        val activity = currentActivity ?: return
        
        isDialogShowing = true
        
        // Show onboarding dialog for critical permissions
        val permissionType = criticalPermissions.first()
        val dialog = PermissionDialogFragment.newInstance(
            permissionType,
            PermissionDialogType.INITIAL_REQUEST
        )
        
        dialog.setOnPrimaryButtonClickListener {
            permissionManager.requestPermission(permissionType)
        }
        
        dialog.setOnSecondaryButtonClickListener {
            // User chose to skip - close the app for critical permissions
            Log.d(TAG, "User chose to skip critical permission - closing app")
            closeApplication()
        }
        
        dialog.setOnDismissListener {
            isDialogShowing = false
            processNextRequest()
        }
        
        if (!activity.isFinishing && !activity.isDestroyed) {
            dialog.show(activity.supportFragmentManager, "permission_onboarding")
        }
    }
    
    /**
     * Handle permission state changes
     */
    private fun handlePermissionStateChange(state: PermissionState) {
        Log.d(TAG, "Permission state changed: ${state.totalMissingPermissions} missing permissions")
        
        if (state.hasAllCriticalPermissions) {
            onAllPermissionsGranted?.invoke()
        } else if (state.missingCriticalPermissions.isNotEmpty()) {
            onCriticalPermissionMissing?.invoke(state.missingCriticalPermissions)
        }
    }
    
    /**
     * Handle permission requests changes
     */
    private fun handlePermissionRequestsChange(requests: List<PermissionRequest>) {
        Log.d(TAG, "Permission requests changed: ${requests.size} requests")
        
        if (!isDialogShowing && requests.isNotEmpty()) {
            processPermissionRequests(requests)
        }
    }
    
    /**
     * Setup permission manager callbacks
     */
    private fun setupPermissionManagerCallbacks() {
        permissionManager.setCallbacks(
            onPermissionGranted = { permissionType ->
                Log.d(TAG, "Permission granted: $permissionType")
                // Permission was granted, continue with flow
            },
            onPermissionDenied = { permissionType, isPermanentlyDenied ->
                Log.d(TAG, "Permission denied: $permissionType, permanently: $isPermanentlyDenied")
                // Permission was denied, continue with flow
            },
            onAllPermissionsGranted = {
                Log.d(TAG, "All permissions granted")
                onAllPermissionsGranted?.invoke()
            },
            onCriticalPermissionMissing = { missingPermissions ->
                Log.d(TAG, "Critical permissions missing: $missingPermissions")
                onCriticalPermissionMissing?.invoke(missingPermissions)
            }
        )
    }
    
    /**
     * Setup service state monitoring
     */
    private fun setupServiceStateMonitoring() {
        val activity = currentActivity ?: return
        
        serviceStateReceiver = ServiceStateReceiver(
            onBluetoothStateChanged = { isEnabled ->
                Log.d(TAG, "Bluetooth state changed: $isEnabled")
                if (!isEnabled && !isDialogShowing) {
                    // Show dialog for disabled Bluetooth
                    showServiceDisabledDialog(PermissionType.BLUETOOTH, "bluetooth")
                } else if (isEnabled && isDialogShowing) {
                    // Dismiss dialog when Bluetooth is enabled
                    dismissCurrentDialog()
                }
            },
            onLocationStateChanged = { isEnabled ->
                Log.d(TAG, "Location state changed: $isEnabled")
                if (!isEnabled && !isDialogShowing) {
                    // Show dialog for disabled Location
                    showServiceDisabledDialog(PermissionType.LOCATION, "location")
                } else if (isEnabled && isDialogShowing) {
                    // Dismiss dialog when Location is enabled
                    dismissCurrentDialog()
                }
            }
        )
        
        // Register receiver
        try {
            activity.registerReceiver(serviceStateReceiver, serviceStateReceiver!!.getIntentFilter())
            isServiceMonitoringActive = true
            Log.d(TAG, "Service state monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering service state receiver", e)
        }
    }
    
    /**
     * Stop service state monitoring
     */
    private fun stopServiceStateMonitoring() {
        val activity = currentActivity ?: return
        
        if (isServiceMonitoringActive && serviceStateReceiver != null) {
            try {
                activity.unregisterReceiver(serviceStateReceiver)
                isServiceMonitoringActive = false
                Log.d(TAG, "Service state monitoring stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering service state receiver", e)
            }
        }
    }
    
    /**
     * Check for runtime permission revocation
     */
    fun checkForRuntimeRevocation() {
        val state = permissionManager.getCurrentPermissionState()
        
        if (state.hasAnyDeniedPermissions) {
            Log.d(TAG, "Runtime permission revocation detected")
            
            // Show appropriate dialog for revoked permissions
            val revokedPermissions = state.deniedPermissions + state.permanentlyDeniedPermissions
            
            if (revokedPermissions.isNotEmpty()) {
                val permissionType = revokedPermissions.first()
                showPermissionDialog(
                    PermissionRequest(
                        permissionType = permissionType,
                        isPermanentlyDenied = state.permanentlyDeniedPermissions.contains(permissionType),
                        shouldShowRationale = false
                    )
                )
            }
        }
    }
    
    /**
     * Check for device-level service issues
     */
    fun checkForServiceIssues() {
        val serviceStatus = permissionManager.getServiceStatus().toMutableMap()
        // If Bluetooth runtime permission isn't granted yet, don't treat Bluetooth as disabled
        val state = permissionManager.getCurrentPermissionState()
        if (!state.grantedPermissions.contains(PermissionType.BLUETOOTH)) {
            serviceStatus["bluetooth"] = true
        }
        
        serviceStatus.forEach { (service, isEnabled) ->
            if (!isEnabled) {
                Log.d(TAG, "Service disabled: $service")
                
                // Show service disabled dialog only if not already showing
                if (!isDialogShowing) {
                    val permissionType = when (service) {
                        "bluetooth" -> PermissionType.BLUETOOTH
                        "location" -> PermissionType.LOCATION
                        else -> return@forEach
                    }
                    
                    showServiceDisabledDialog(permissionType, service)
                }
            } else {
                // Service is enabled, dismiss any existing dialog for this service
                if (isDialogShowing) {
                    dismissCurrentDialog()
                }
            }
        }
    }
    
    /**
     * Show dialog for disabled device services
     */
    private fun showServiceDisabledDialog(permissionType: PermissionType, service: String) {
        val activity = currentActivity ?: return
        
        isDialogShowing = true
        
        val dialog = PermissionDialogFragment.newInstance(
            permissionType,
            PermissionDialogType.SERVICE_DISABLED
        )
        
        // Setup dialog callbacks
        dialog.setOnPrimaryButtonClickListener {
            // Open system settings for the specific service
            isNavigatingToSystemSettings = true
            openSystemSettings(service)
        }
        
        dialog.setOnSecondaryButtonClickListener {
            Log.d(TAG, "User cancelled service enable request for $service - closing app")
            closeApplication()
        }
        
        dialog.setOnDismissListener {
            isDialogShowing = false
            // Avoid re-showing while navigating to settings
            if (!isNavigatingToSystemSettings) {
                // Re-check service status after dismiss in case user toggled it
                checkForServiceIssues()
            }
        }
        
        // Show dialog
        if (!activity.isFinishing && !activity.isDestroyed) {
            dialog.show(activity.supportFragmentManager, "service_disabled_dialog")
        }
    }
    
    /**
     * Dismiss the current dialog if one is showing
     */
    private fun dismissCurrentDialog() {
        val activity = currentActivity ?: return
        if (isDialogShowing) {
            try {
                val fragment = activity.supportFragmentManager.findFragmentByTag("service_disabled_dialog")
                if (fragment is PermissionDialogFragment) {
                    fragment.dismiss()
                }
                isDialogShowing = false
                Log.d(TAG, "Service dialog dismissed")
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing dialog", e)
                isDialogShowing = false
            }
        }
    }
    
    /**
     * Open system settings for specific services
     */
    private fun openSystemSettings(service: String) {
        val intent = when (service) {
            "bluetooth" -> {
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            }
            "location" -> {
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            }
            else -> {
                Intent(Settings.ACTION_SETTINGS)
            }
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            currentActivity?.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening system settings for $service", e)
            // Fallback to general settings
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            currentActivity?.startActivity(fallbackIntent)
        }
    }
    
    /**
     * Handle app resume - check for permission changes
     */
    fun onAppResume() {
        // Reset navigation flag when returning from settings
        isNavigatingToSystemSettings = false
        // Refresh permission state
        permissionManager.refreshPermissionState()
        
        // Check for runtime revocation
        checkForRuntimeRevocation()
        
        // Check for service issues
        checkForServiceIssues()
        
        // Restart service monitoring if needed
        if (!isServiceMonitoringActive) {
            setupServiceStateMonitoring()
        }
    }
    
    /**
     * Handle app pause - cleanup
     */
    fun onAppPause() {
        // Stop service monitoring to save resources
        stopServiceStateMonitoring()
    }
    
    /**
     * Get current permission state
     */
    fun getCurrentPermissionState(): PermissionState {
        return permissionManager.getCurrentPermissionState()
    }
    
    /**
     * Check if all critical permissions are granted
     */
    fun hasAllCriticalPermissions(): Boolean {
        return permissionManager.getCurrentPermissionState().hasAllCriticalPermissions
    }
    
    /**
     * Request a specific permission
     */
    fun requestPermission(permissionType: PermissionType) {
        permissionManager.requestPermission(permissionType)
    }
    
    /**
     * Open app settings
     */
    fun openAppSettings() {
        permissionManager.openAppSettings()
    }
    
    /**
     * Reset permission preferences (for testing)
     */
    fun resetPreferences() {
        permissionManager.resetPreferences()
    }
}
