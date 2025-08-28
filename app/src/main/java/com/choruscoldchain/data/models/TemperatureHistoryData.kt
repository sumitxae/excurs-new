package com.choruscoldchain.data.models

import com.minew.ble.mst03.bean.HtData;

data class TemperatureDataPoint(
    val timestamp: Long,
    val temperature: Float,
    val humidity: Float,
    val macAddress: String? = null
)

data class ExcursionAnalysis(
    val excursionStartTime: Long?,
    val excursionStartTemperature: Float?,
    val excursionDuration: Long?, // in milliseconds
    val excursionDataPoints: List<HtData>,
    val isInExcursion: Boolean,
    val normalRangeMin: Float = 2.0f,
    val normalRangeMax: Float = 8.0f
)

data class TemperatureHistoryAnalysis(
    val allDataPoints: List<HtData>,
    val excursionAnalysis: ExcursionAnalysis,
    val totalDataPoints: Int,
    val timeRangeStart: Long?,
    val timeRangeEnd: Long?
)
