package com.minew.sensormanager.data.models

data class TemperatureDataPoint(
    val timestamp: Long,
    val temperature: Float,
    val humidity: Float
)

data class ExcursionAnalysis(
    val excursionStartTime: Long?,
    val excursionStartTemperature: Float?,
    val excursionDuration: Long?, // in milliseconds
    val excursionDataPoints: List<TemperatureDataPoint>,
    val isInExcursion: Boolean,
    val normalRangeMin: Float = 2.0f,
    val normalRangeMax: Float = 8.0f
)

data class TemperatureHistoryAnalysis(
    val allDataPoints: List<TemperatureDataPoint>,
    val excursionAnalysis: ExcursionAnalysis,
    val totalDataPoints: Int,
    val timeRangeStart: Long?,
    val timeRangeEnd: Long?
)
