package com.choruscoldchain.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SensorData(
    val macAddress: String,
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
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {
    
    val hasTemperatureAlarm: Boolean
        get() = temperatureUpperLimit1AlarmMark || temperatureLowerLimit1AlarmMark ||
               temperatureUpperLimit2AlarmMark || temperatureLowerLimit2AlarmMark
    
    val hasLightAlarm: Boolean
        get() = lightIntensityUpperLimitAlarmMark || lightIntensityLowerLimitAlarmMark
    
    val hasAnyAlarm: Boolean
        get() = hasTemperatureAlarm || hasLightAlarm
}
