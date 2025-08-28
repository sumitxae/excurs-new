package com.choruscoldchain.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager

object BluetoothHelper {
    
    enum class BluetoothState {
        NOT_SUPPORTED,
        DISABLED,
        ENABLED
    }
    
    fun checkBluetoothState(context: Context): BluetoothState {
        // Check if BLE is supported
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return BluetoothState.NOT_SUPPORTED
        }
        
        // Get Bluetooth adapter
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        
        return if (bluetoothAdapter?.isEnabled == true) {
            BluetoothState.ENABLED
        } else {
            BluetoothState.DISABLED
        }
    }
    
    fun isBluetoothEnabled(context: Context): Boolean {
        return checkBluetoothState(context) == BluetoothState.ENABLED
    }
}
