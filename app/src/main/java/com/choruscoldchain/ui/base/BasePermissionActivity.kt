package com.choruscoldchain.ui.base

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.choruscoldchain.permissions.PermissionCoordinator
import com.choruscoldchain.permissions.PermissionType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Base activity that integrates the permission management system.
 * Extend this activity to automatically handle permission flows.
 */
@AndroidEntryPoint
abstract class BasePermissionActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "BasePermissionActivity"
    }
    
    @Inject
    lateinit var permissionCoordinator: PermissionCoordinator
    
    private var isPermissionFlowStarted = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize permission coordinator
        permissionCoordinator.initialize(this, this)
        
        // Setup permission callbacks
        setupPermissionCallbacks()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check for permission changes when app resumes
        permissionCoordinator.onAppResume()
        
        // Start permission flow if not already started
        if (!isPermissionFlowStarted) {
            startPermissionFlow()
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // Cleanup when app pauses
        permissionCoordinator.onAppPause()
    }
    
    /**
     * Setup permission callbacks
     */
    private fun setupPermissionCallbacks() {
        permissionCoordinator.setCallbacks(
            onAllPermissionsGranted = {
                Log.d(TAG, "All permissions granted")
                onAllPermissionsGranted()
            },
            onCriticalPermissionMissing = { missingPermissions ->
                Log.d(TAG, "Critical permissions missing: $missingPermissions")
                onCriticalPermissionMissing(missingPermissions)
            },
            onPermissionFlowComplete = {
                Log.d(TAG, "Permission flow completed")
                onPermissionFlowComplete()
            }
        )
    }
    
    /**
     * Start the permission flow
     */
    private fun startPermissionFlow() {
        isPermissionFlowStarted = true
        permissionCoordinator.startPermissionFlow()
    }
    
    /**
     * Called when all critical permissions are granted
     */
    protected open fun onAllPermissionsGranted() {
        // Override in subclasses to handle when all permissions are granted
    }
    
    /**
     * Called when critical permissions are missing
     */
    protected open fun onCriticalPermissionMissing(missingPermissions: List<PermissionType>) {
        // Override in subclasses to handle missing critical permissions
        Log.w(TAG, "Critical permissions missing: $missingPermissions")
    }
    
    /**
     * Called when the permission flow is complete
     */
    protected open fun onPermissionFlowComplete() {
        // Override in subclasses to handle permission flow completion
    }
    
    /**
     * Check if all critical permissions are granted
     */
    protected fun hasAllCriticalPermissions(): Boolean {
        return permissionCoordinator.hasAllCriticalPermissions()
    }
    
    /**
     * Get current permission state
     */
    protected fun getCurrentPermissionState() = permissionCoordinator.getCurrentPermissionState()
    
    /**
     * Request a specific permission
     */
    protected fun requestPermission(permissionType: PermissionType) {
        permissionCoordinator.requestPermission(permissionType)
    }
    
    /**
     * Open app settings
     */
    protected fun openAppSettings() {
        permissionCoordinator.openAppSettings()
    }
    
    /**
     * Reset permission preferences (for testing)
     */
    protected fun resetPermissionPreferences() {
        permissionCoordinator.resetPreferences()
    }
}
