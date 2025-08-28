package com.minew.sensormanager.utils

import android.Manifest
import android.os.Build

object PermissionHelper {
    
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ))
        } else {
            // Android 11 and below
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ))
        }

        // Storage permissions (request these BEFORE location as per requirement)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.addAll(listOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            ))
        } else {
            permissions.addAll(listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }

        // Location permissions (required for BLE scanning)
        permissions.addAll(listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        
        // Camera permission (required for QR scanning)
        permissions.add(Manifest.permission.CAMERA)
        
        return permissions
    }
    
    fun hasAllRequiredPermissions(context: android.content.Context): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasBluetoothPermissions(context: android.content.Context): Boolean {
        val bluetoothPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            listOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        
        return bluetoothPermissions.all { permission ->
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasLocationPermissions(context: android.content.Context): Boolean {
        val locationPermissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        return locationPermissions.all { permission ->
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasCameraPermission(context: android.content.Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    fun canPerformBleOperations(context: android.content.Context): Boolean {
        return hasBluetoothPermissions(context) && hasLocationPermissions(context)
    }
    
    fun getMissingPermissions(context: android.content.Context): List<String> {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.filter { permission ->
            context.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun getPermissionErrorMessage(context: android.content.Context): String? {
        val missingPermissions = getMissingPermissions(context)
        return if (missingPermissions.isNotEmpty()) {
            val permissionNames = missingPermissions.map { permission ->
                when (permission) {
                    android.Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth Scan"
                    android.Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth Connect"
                    android.Manifest.permission.BLUETOOTH -> "Bluetooth"
                    android.Manifest.permission.BLUETOOTH_ADMIN -> "Bluetooth Admin"
                    android.Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
                    android.Manifest.permission.ACCESS_COARSE_LOCATION -> "Location"
                    android.Manifest.permission.CAMERA -> "Camera"
                    else -> permission
                }
            }.distinct()
            
            "Missing permissions: ${permissionNames.joinToString(", ")}. Please grant these permissions in Settings."
        } else {
            null
        }
    }
}
