package com.minew.sensormanager.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.minew.sensormanager.utils.AppDownloadTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive permission management system that handles all permission scenarios
 * including first app launch, permission denial, runtime revocation, and more.
 */
@Singleton
class PermissionManager @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "PermissionManager"
        private const val PREFS_NAME = "permission_preferences"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_PERMISSION_DENIED_COUNT = "permission_denied_count_"
        private const val KEY_DONT_SHOW_AGAIN = "dont_show_again_"
        private const val KEY_LAST_PERMISSION_CHECK = "last_permission_check"
        private const val MAX_DENIAL_COUNT = 3
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // State flows for reactive permission updates
    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()
    
    private val _permissionRequests = MutableStateFlow<List<PermissionRequest>>(emptyList())
    val permissionRequests: StateFlow<List<PermissionRequest>> = _permissionRequests.asStateFlow()
    
    // Activity result launchers for permission requests
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var individualPermissionLauncher: ActivityResultLauncher<String>? = null
    private var settingsLauncher: ActivityResultLauncher<Intent>? = null
    
    // Callbacks
    private var onPermissionGranted: ((PermissionType) -> Unit)? = null
    private var onPermissionDenied: ((PermissionType, Boolean) -> Unit)? = null
    private var onAllPermissionsGranted: (() -> Unit)? = null
    private var onCriticalPermissionMissing: ((List<PermissionType>) -> Unit)? = null
    
    /**
     * Initialize the permission manager with activity context
     */
    fun initialize(activity: FragmentActivity) {
        setupActivityResultLaunchers(activity)
        refreshPermissionState()
    }
    
    /**
     * Setup activity result launchers for permission requests
     */
    private fun setupActivityResultLaunchers(activity: FragmentActivity) {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handlePermissionResults(permissions)
        }
        
        // Individual permission launcher for ordered requests
        individualPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handleIndividualPermissionResult(isGranted)
        }
        
        settingsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handleSettingsReturn()
        }
    }
    
    /**
     * Check if this is the first app launch
     */
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }
    
    /**
     * Mark first launch as completed
     */
    fun markFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
    
    /**
     * Get current permission state
     */
    fun getCurrentPermissionState(): PermissionState {
        return _permissionState.value
    }
    
    /**
     * Refresh permission state and update state flows
     */
    fun refreshPermissionState() {
        val state = calculatePermissionState()
        _permissionState.value = state
        
        // Update permission requests
        val requests = buildPermissionRequests(state)
        _permissionRequests.value = requests
        
        // Trigger appropriate callbacks
        if (state.hasAllCriticalPermissions) {
            onAllPermissionsGranted?.invoke()
        } else if (state.missingCriticalPermissions.isNotEmpty()) {
            onCriticalPermissionMissing?.invoke(state.missingCriticalPermissions)
        }
    }
    
    /**
     * Calculate current permission state
     */
    private fun calculatePermissionState(): PermissionState {
        val grantedPermissions = mutableListOf<PermissionType>()
        val deniedPermissions = mutableListOf<PermissionType>()
        val permanentlyDeniedPermissions = mutableListOf<PermissionType>()
        
        PermissionType.values().forEach { permissionType ->
            val isGranted = permissionType.permissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            
            if (isGranted) {
                grantedPermissions.add(permissionType)
            } else {
                val isPermanentlyDenied = permissionType.permissions.any { permission ->
                    isPermissionPermanentlyDenied(permission)
                }
                
                if (isPermanentlyDenied) {
                    permanentlyDeniedPermissions.add(permissionType)
                } else {
                    deniedPermissions.add(permissionType)
                }
            }
        }
        
        val missingCriticalPermissions = (deniedPermissions + permanentlyDeniedPermissions)
            .filter { it.isCritical }
        
        return PermissionState(
            grantedPermissions = grantedPermissions,
            deniedPermissions = deniedPermissions,
            permanentlyDeniedPermissions = permanentlyDeniedPermissions,
            missingCriticalPermissions = missingCriticalPermissions
        )
    }
    
    /**
     * Build permission requests based on current state
     */
    private fun buildPermissionRequests(state: PermissionState): List<PermissionRequest> {
        val requests = mutableListOf<PermissionRequest>()
        
        // Add denied permissions that can be requested again
        state.deniedPermissions.forEach { permissionType ->
            val shouldShowRationale = permissionType.permissions.any { permission ->
                if (context is Activity) {
                    context.shouldShowRequestPermissionRationale(permission)
                } else {
                    false
                }
            }
            
            requests.add(
                PermissionRequest(
                    permissionType = permissionType,
                    isPermanentlyDenied = false,
                    shouldShowRationale = shouldShowRationale
                )
            )
        }
        
        // Add permanently denied permissions
        state.permanentlyDeniedPermissions.forEach { permissionType ->
            requests.add(
                PermissionRequest(
                    permissionType = permissionType,
                    isPermanentlyDenied = true,
                    shouldShowRationale = false
                )
            )
        }
        
        // Sort requests according to recommended order
        val recommendedOrder = getRecommendedPermissionOrder()
        return requests.sortedBy { request ->
            recommendedOrder.indexOf(request.permissionType)
        }
    }
    
    /**
     * Check if a permission is permanently denied
     */
    private fun isPermissionPermanentlyDenied(permission: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        
        val deniedCount = prefs.getInt(KEY_PERMISSION_DENIED_COUNT + permission, 0)
        return deniedCount >= MAX_DENIAL_COUNT || 
               (context is Activity && !context.shouldShowRequestPermissionRationale(permission))
    }
    
    /**
     * Request permissions for a specific permission type
     */
    fun requestPermission(permissionType: PermissionType) {
        val request = _permissionRequests.value.find { it.permissionType == permissionType }
        
        when {
            request?.isPermanentlyDenied == true -> {
                showSettingsDialog(permissionType)
            }
            request?.shouldShowRationale == true -> {
                showRationaleDialog(permissionType)
            }
            else -> {
                // Request permissions one by one in the order they appear in the permission type
                if (permissionType.permissions.size == 1) {
                    requestSinglePermission(permissionType.permissions.first())
                } else {
                    // For multiple permissions, request them individually in order
                    requestPermissionsIndividually(permissionType.permissions)
                }
            }
        }
    }
    
    /**
     * Request multiple permissions
     */
    fun requestPermissions(permissions: Array<String>) {
        permissionLauncher?.launch(permissions)
    }
    
    /**
     * Request a single permission
     */
    fun requestSinglePermission(permission: String) {
        individualPermissionLauncher?.launch(permission)
    }
    
    /**
     * Request multiple permissions individually in order
     */
    private fun requestPermissionsIndividually(permissions: List<String>) {
        // For now, just request the first permission
        // The coordinator will handle requesting the next one after this one is resolved
        if (permissions.isNotEmpty()) {
            requestSinglePermission(permissions.first())
        }
    }
    
    /**
     * Handle permission request results
     */
    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val grantedPermissions = mutableListOf<PermissionType>()
        val deniedPermissions = mutableListOf<PermissionType>()
        
        permissions.forEach { (permission, isGranted) ->
            val permissionType = PermissionType.fromPermission(permission)
            if (permissionType != null) {
                if (isGranted) {
                    grantedPermissions.add(permissionType)
                    onPermissionGranted?.invoke(permissionType)
                } else {
                    deniedPermissions.add(permissionType)
                    incrementDenialCount(permission)
                    onPermissionDenied?.invoke(permissionType, isPermissionPermanentlyDenied(permission))
                }
            }
        }
        
        // Update permission state
        refreshPermissionState()
        
        // Show appropriate dialogs for denied permissions
        deniedPermissions.forEach { permissionType ->
            if (isPermissionPermanentlyDenied(permissionType.permissions.first())) {
                showSettingsDialog(permissionType)
            } else {
                showRationaleDialog(permissionType)
            }
        }
    }
    
    /**
     * Handle individual permission request result
     */
    private fun handleIndividualPermissionResult(isGranted: Boolean) {
        // This will be handled by the coordinator when requesting individual permissions
        refreshPermissionState()
    }
    
    /**
     * Increment denial count for a permission
     */
    private fun incrementDenialCount(permission: String) {
        val currentCount = prefs.getInt(KEY_PERMISSION_DENIED_COUNT + permission, 0)
        prefs.edit().putInt(KEY_PERMISSION_DENIED_COUNT + permission, currentCount + 1).apply()
    }
    
    /**
     * Show rationale dialog for a permission
     */
    private fun showRationaleDialog(permissionType: PermissionType) {
        // This will be handled by the UI layer
        // The dialog should be shown by the calling activity/fragment
    }
    
    /**
     * Show settings dialog for permanently denied permissions
     */
    private fun showSettingsDialog(permissionType: PermissionType) {
        // This will be handled by the UI layer
        // The dialog should be shown by the calling activity/fragment
    }
    
    /**
     * Open app settings
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        settingsLauncher?.launch(intent)
    }
    
    /**
     * Handle return from settings
     */
    private fun handleSettingsReturn() {
        refreshPermissionState()
        
        val currentState = _permissionState.value
        if (currentState.hasAllCriticalPermissions) {
            onAllPermissionsGranted?.invoke()
        }
    }
    
    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
    }
    
    /**
     * Check if Location services are enabled
     */
    fun isLocationEnabled(): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            false
        }
    }
    
    /**
     * Get service status for device-level services
     */
    fun getServiceStatus(): Map<String, Boolean> {
        return mapOf(
            "bluetooth" to isBluetoothEnabled(),
            "location" to isLocationEnabled()
        )
    }
    
    /**
     * Set callbacks for permission events
     */
    fun setCallbacks(
        onPermissionGranted: ((PermissionType) -> Unit)? = null,
        onPermissionDenied: ((PermissionType, Boolean) -> Unit)? = null,
        onAllPermissionsGranted: (() -> Unit)? = null,
        onCriticalPermissionMissing: ((List<PermissionType>) -> Unit)? = null
    ) {
        this.onPermissionGranted = onPermissionGranted
        this.onPermissionDenied = onPermissionDenied
        this.onAllPermissionsGranted = onAllPermissionsGranted
        this.onCriticalPermissionMissing = onCriticalPermissionMissing
    }
    
    /**
     * Check if user has selected "Don't show again" for a permission
     */
    fun isDontShowAgainSelected(permissionType: PermissionType): Boolean {
        return prefs.getBoolean(KEY_DONT_SHOW_AGAIN + permissionType.name, false)
    }
    
    /**
     * Mark "Don't show again" as selected for a permission
     */
    fun setDontShowAgainSelected(permissionType: PermissionType, selected: Boolean) {
        prefs.edit().putBoolean(KEY_DONT_SHOW_AGAIN + permissionType.name, selected).apply()
    }
    
    /**
     * Reset all permission preferences (useful for testing)
     */
    fun resetPreferences() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Get permission denial count for a specific permission
     */
    fun getDenialCount(permission: String): Int {
        return prefs.getInt(KEY_PERMISSION_DENIED_COUNT + permission, 0)
    }
    
    /**
     * Check if app should show permission onboarding
     */
    fun shouldShowPermissionOnboarding(): Boolean {
        return isFirstLaunch() && _permissionState.value.totalMissingPermissions > 0
    }
    
    /**
     * Get recommended permission request order
     */
    fun getRecommendedPermissionOrder(): List<PermissionType> {
        return listOf(
            PermissionType.CAMERA,
            PermissionType.STORAGE,
            PermissionType.BLUETOOTH,
            PermissionType.LOCATION,
        )
    }
}
