package com.minew.sensormanager.permissions

import android.Manifest
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.minew.sensormanager.R

/**
 * Enum representing different permission types with their associated metadata
 */
enum class PermissionType(
    val permissions: List<String>,
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    @DrawableRes val iconRes: Int,
    val isCritical: Boolean = true
) {
    BLUETOOTH(
        permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        },
        titleRes = R.string.permission_bluetooth_title,
        messageRes = R.string.permission_bluetooth_message,
        iconRes = R.drawable.bluetooth_disabled_24px,
        isCritical = true
    ),
    
    LOCATION(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ),
        titleRes = R.string.permission_location_title,
        messageRes = R.string.permission_location_message,
        iconRes = R.drawable.ic_location,
        isCritical = true
    ),
    
    CAMERA(
        permissions = listOf(Manifest.permission.CAMERA),
        titleRes = R.string.permission_camera_title,
        messageRes = R.string.permission_camera_message,
        iconRes = R.drawable.ic_camera,
        isCritical = true
    ),
    
    // MICROPHONE(
    //     permissions = listOf(Manifest.permission.RECORD_AUDIO),
    //     titleRes = R.string.permission_microphone_title,
    //     messageRes = R.string.permission_microphone_message,
    //     iconRes = R.drawable.ic_microphone,
    //     isCritical = false
    // ),
    
    STORAGE(
        permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        },
        titleRes = R.string.permission_storage_title,
        messageRes = R.string.permission_storage_message,
        iconRes = R.drawable.ic_storage,
        isCritical = false
    );
    
    // NOTIFICATIONS(
    //     permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
    //     titleRes = R.string.permission_notifications_title,
    //     messageRes = R.string.permission_notifications_message,
    //     iconRes = R.drawable.ic_notifications,
    //     isCritical = false
    // );
    
    // PHONE(
    //     permissions = listOf(Manifest.permission.READ_PHONE_STATE),
    //     titleRes = R.string.permission_phone_title,
    //     messageRes = R.string.permission_phone_message,
    //     iconRes = R.drawable.ic_phone,
    //     isCritical = false
    // ),
    
    // CONTACTS(
    //     permissions = listOf(Manifest.permission.READ_CONTACTS),
    //     titleRes = R.string.permission_contacts_title,
    //     messageRes = R.string.permission_contacts_message,
    //     iconRes = R.drawable.ic_contacts,
    //     isCritical = false
    // );

    companion object {
        fun fromPermission(permission: String): PermissionType? {
            return values().find { permissionType ->
                permissionType.permissions.contains(permission)
            }
        }
        
        fun getCriticalPermissions(): List<PermissionType> {
            return values().filter { it.isCritical }
        }
        
        fun getNonCriticalPermissions(): List<PermissionType> {
            return values().filter { !it.isCritical }
        }
    }
}

/**
 * Data class representing a permission request
 */
data class PermissionRequest(
    val permissionType: PermissionType,
    val isPermanentlyDenied: Boolean = false,
    val shouldShowRationale: Boolean = false
)

/**
 * Data class representing the result of a permission check
 */
data class PermissionResult(
    val permissionType: PermissionType,
    val isGranted: Boolean,
    val isPermanentlyDenied: Boolean = false,
    val shouldShowRationale: Boolean = false
)

/**
 * Data class representing the overall permission state
 */
data class PermissionState(
    val grantedPermissions: List<PermissionType> = emptyList(),
    val deniedPermissions: List<PermissionType> = emptyList(),
    val permanentlyDeniedPermissions: List<PermissionType> = emptyList(),
    val missingCriticalPermissions: List<PermissionType> = emptyList()
) {
    val hasAllCriticalPermissions: Boolean
        get() = missingCriticalPermissions.isEmpty()
    
    val hasAnyDeniedPermissions: Boolean
        get() = deniedPermissions.isNotEmpty() || permanentlyDeniedPermissions.isNotEmpty()
    
    val totalMissingPermissions: Int
        get() = deniedPermissions.size + permanentlyDeniedPermissions.size
}

/**
 * Enum representing different permission dialog types
 */
enum class PermissionDialogType {
    INITIAL_REQUEST,      // First time requesting permissions
    RATIONALE,           // Showing rationale for denied permissions
    SETTINGS_REDIRECT,   // Redirecting to settings for permanently denied
    RUNTIME_REVOCATION,  // Permissions revoked while app was running
    PARTIAL_GRANT,       // Some permissions granted, some missing
    SERVICE_DISABLED     // Device-level service disabled (Bluetooth, Location, etc.)
}
