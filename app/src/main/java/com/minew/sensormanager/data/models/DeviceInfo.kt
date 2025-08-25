package com.minew.sensormanager.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeviceInfo(
    val macAddress: String,
    val name: String,
    val rssi: Int,
    val battery: Int,
    val temperature: Float? = null,
    val firmwareVersion: String,
    val isConnected: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val alertsEnabled: Boolean = true,
    val groupId: String? = null,
    val staticFrameData: String? = null,
    val combinationFrameData: String? = null
) : Parcelable

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATING,
    AUTHENTICATED,
    READY,
    ERROR
}
