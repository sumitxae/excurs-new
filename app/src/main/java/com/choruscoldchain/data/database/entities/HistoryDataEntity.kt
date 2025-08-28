package com.choruscoldchain.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "history_data",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["macAddress"],
        childColumns = ["deviceMacAddress"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [androidx.room.Index(value = ["deviceMacAddress"])]
)
data class HistoryDataEntity(
    @PrimaryKey val id: String,
    val deviceMacAddress: String,
    val temperature: Float,
    val lightIntensity: Int,
    val battery: Int,
    val temperatureUpperLimit1AlarmMark: Boolean = false,
    val temperatureLowerLimit1AlarmMark: Boolean = false,
    val temperatureUpperLimit2AlarmMark: Boolean = false,
    val temperatureLowerLimit2AlarmMark: Boolean = false,
    val lightIntensityUpperLimitAlarmMark: Boolean = false,
    val lightIntensityLowerLimitAlarmMark: Boolean = false,
    val lightEventTimestamp: Long = 0,
    val tempEventTimestamp: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val dataQuality: String = "GOOD", // GOOD, WARNING, ERROR
    val notes: String? = null
)
