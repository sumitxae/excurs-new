package com.choruscoldchain.permissions

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.util.Log

/**
 * Broadcast receiver to monitor Bluetooth and Location service state changes
 */
class ServiceStateReceiver(
    private val onBluetoothStateChanged: (Boolean) -> Unit,
    private val onLocationStateChanged: (Boolean) -> Unit
) : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ServiceStateReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val isEnabled = state == BluetoothAdapter.STATE_ON
                Log.d(TAG, "Bluetooth state changed: $state, enabled: $isEnabled")
                onBluetoothStateChanged(isEnabled)
            }
            
            LocationManager.PROVIDERS_CHANGED_ACTION -> {
                val locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val isEnabled = locationManager?.let { manager ->
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                } ?: false
                Log.d(TAG, "Location state changed: enabled: $isEnabled")
                onLocationStateChanged(isEnabled)
            }
        }
    }
    
    /**
     * Get intent filter for the services we want to monitor
     */
    fun getIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
    }
}
