package com.minew.sensormanager.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeviceConfiguration(
    val macAddress: String,
    val temperatureThresholds: List<TemperatureThreshold>,
    val lightThresholds: LightThresholds,
    val samplingInterval: Int = 60, // seconds
    val delayRecordTime: Int = 0, // seconds
    val alarmStrategy: AlarmStrategy,
    val advParameters: AdvParameters,
    val lastUpdated: Long = System.currentTimeMillis()
) : Parcelable

@Parcelize
data class TemperatureThreshold(
    val lowTemperature: Float = -128f, // -128 means disabled
    val highTemperature: Float = -128f, // -128 means disabled
    val isEnabled: Boolean = false
) : Parcelable

@Parcelize
data class LightThresholds(
    val lowThreshold: Int = 12, // nW/cm² * 10 (1.2 actual)
    val highThreshold: Int = 130000, // nW/cm² * 10 (13000 actual)
    val isEnabled: Boolean = false
) : Parcelable

@Parcelize
data class AlarmStrategy(
    val alarmLedDuration: Int = 600, // seconds (10 minutes default)
    val alarmCountThreshold: Int = 3, // consecutive threshold violations
    val isEnabled: Boolean = true
) : Parcelable

@Parcelize
data class AdvParameters(
    val slotNumber: Int = 0, // 0 = DeviceStaticInfoFrame, 1 = CombinationFrame
    val frameType: String = "",
    val advertisingInterval: Int = 5000, // milliseconds
    val txPower: Int = 0, // dBm
    val advertisingContent: String = ""
) : Parcelable
