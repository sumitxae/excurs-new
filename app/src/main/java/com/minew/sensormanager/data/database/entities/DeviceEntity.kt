package com.minew.sensormanager.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.minew.sensormanager.data.models.ConnectionState

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val macAddress: String,
    val name: String,
    val rssi: Int,
    val battery: Int,
    val firmwareVersion: String,
    val isConnected: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val connectionState: String = ConnectionState.DISCONNECTED.name,
    val alertsEnabled: Boolean = true,
    val groupId: String? = null,
    val secretKey: String? = null
)
