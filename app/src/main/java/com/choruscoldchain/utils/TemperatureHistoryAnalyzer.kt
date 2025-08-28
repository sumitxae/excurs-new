package com.choruscoldchain.utils

import android.util.Log
import com.choruscoldchain.data.models.TemperatureDataPoint
import com.choruscoldchain.data.models.ExcursionAnalysis
import com.choruscoldchain.data.models.TemperatureHistoryAnalysis
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import com.minew.ble.mst03.bean.HtData;

object TemperatureHistoryAnalyzer {
    private const val TAG = "TemperatureHistoryAnalyzer"
    
    // Normal temperature range (2°C to 8°C)
    const val NORMAL_TEMP_MIN = 2.0f
    const val NORMAL_TEMP_MAX = 8.0f
    
    // Date format for parsing and formatting
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * Analyzes temperature history data to detect excursion events
     */
    fun analyzeTemperatureHistory(
        rawHistoryData: List<HtData>,
        deviceTempEventTimestamp: Long?
    ): TemperatureHistoryAnalysis {
        return try {
            if (rawHistoryData.isEmpty()) {
                Log.w(TAG, "No temperature data points found")
                return createEmptyAnalysis()
            }
            val sortedDataPoints = rawHistoryData.sortedBy { it.timestamps }
            val excursionAnalysis = detectExcursionStart(sortedDataPoints, deviceTempEventTimestamp)
            
            TemperatureHistoryAnalysis(
                allDataPoints = sortedDataPoints,
                excursionAnalysis = excursionAnalysis,
                totalDataPoints = sortedDataPoints.size,
                timeRangeStart = sortedDataPoints.first().timestamps,
                timeRangeEnd = sortedDataPoints.last().timestamps
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing temperature history", e)
            createEmptyAnalysis()
        }
    }
    
    /**
     * Detects the excursion start point by analyzing temperature data
     */
    private fun detectExcursionStart(
        dataPoints: List<HtData>,
        deviceTempEventTimestamp: Long?
    ): ExcursionAnalysis {
        return try {
            val excursionStartPoint = findExcursionStartPoint(dataPoints)
            
            excursionStartPoint?.let { startPoint ->
                createExcursionAnalysis(startPoint, dataPoints, deviceTempEventTimestamp)
            } ?: createNormalRangeAnalysis()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting excursion start", e)
            createEmptyExcursionAnalysis()
        }
    }
    
    /**
     * Finds the most recent excursion start point by going backwards from current time
     * and finding the last normal temperature before the current excursion
     */
    private fun findExcursionStartPoint(dataPoints: List<HtData>): HtData? {
        
        if (dataPoints.all { !it.isNormalTemperature() }) {
            return dataPoints.first()
        }
        
        for (i in dataPoints.indices.reversed()) {
            val currentPoint = dataPoints[i]
            
            if (!currentPoint.isNormalTemperature()) {
                
                val lastNormalIndex = (i - 1 downTo 0).find { j ->
                    val previousPoint = dataPoints[j]
                    previousPoint.isNormalTemperature()
                }
                
                return if (lastNormalIndex != null && lastNormalIndex + 1 < dataPoints.size) {
                    dataPoints[lastNormalIndex + 1]
                } else {
                    dataPoints.first()
                }
            }
        }
        
        return null
    }
    
    /**
     * Creates excursion analysis when excursion is detected
     */
    private fun createExcursionAnalysis(
        startPoint: HtData,
        dataPoints: List<HtData>,
        deviceTempEventTimestamp: Long?
    ): ExcursionAnalysis {
        val lastNormalIndex = dataPoints.indexOfFirst { it.timestamps >= startPoint.timestamps } - 1
        val graphStartIndex = maxOf(0, lastNormalIndex)
        val excursionDataPoints = dataPoints.drop(graphStartIndex)
        
        // Calculate excursion duration
        val excursionDuration = (deviceTempEventTimestamp ?: System.currentTimeMillis()) - startPoint.timestamps
        
        return ExcursionAnalysis(
            excursionStartTime = startPoint.timestamps,
            excursionStartTemperature = startPoint.temperature,
            excursionDuration = excursionDuration,
            excursionDataPoints = excursionDataPoints,
            isInExcursion = true,
            normalRangeMin = NORMAL_TEMP_MIN,
            normalRangeMax = NORMAL_TEMP_MAX
        )
    }
    
    /**
     * Creates analysis when no excursion is detected
     */
    private fun createNormalRangeAnalysis(): ExcursionAnalysis {
        return ExcursionAnalysis(
            excursionStartTime = null,
            excursionStartTemperature = null,
            excursionDuration = null,
            excursionDataPoints = emptyList(),
            isInExcursion = false,
            normalRangeMin = NORMAL_TEMP_MIN,
            normalRangeMax = NORMAL_TEMP_MAX
        )
    }
    
    /**
     * Logs analysis start information
     */
    private fun logAnalysisStart(deviceTempEventTimestamp: Long?, rawDataLength: Int) {
        Log.d(TAG, "=== STARTING TEMPERATURE HISTORY ANALYSIS ===")
        Log.d(TAG, "Device tempEventTimestamp: $deviceTempEventTimestamp")
        Log.d(TAG, "Raw history data length: $rawDataLength")
    }
    
    /**
     * Logs historical temperature data
     */
    private fun logHistoricalData(dataPoints: List<HtData>) {
        Log.d(TAG, "Parsed ${dataPoints.size} temperature data points")
        Log.d(TAG, "=== FULL HISTORICAL TEMPERATURE DATA ===")
        
        dataPoints.forEachIndexed { index, point ->
            val status = if (point.isNormalTemperature()) "NORMAL" else "EXCURSION"
            Log.d(TAG, "Point $index: ${point.temperature}°C at ${point.timestamps} [$status]")
        }
        
        Log.d(TAG, "=== END FULL HISTORICAL DATA ===")
    }
    
    /**
     * Logs excursion detection results
     */
    private fun logExcursionDetectionResult(
        excursionStartPoint: HtData?,
        deviceTempEventTimestamp: Long?
    ) {
        Log.d(TAG, "=== EXCURSION DETECTION RESULT ===")
        
        excursionStartPoint?.let { point ->
            Log.d(TAG, "EXCURSION START FOUND:")
            Log.d(TAG, "  - Temperature: ${point.temperature}°C")
            Log.d(TAG, "  - Timestamp: ${point.timestamps}")
            Log.d(TAG, "  - Raw timestamp: ${point.timestamps}")
            
            deviceTempEventTimestamp?.let { deviceTimestamp ->
                Log.d(TAG, "DEVICE TEMP EVENT TIMESTAMP:")
                Log.d(TAG, "  - Device timestamp: ${formatTimestamp(deviceTimestamp)}")
                Log.d(TAG, "  - Raw device timestamp: $deviceTimestamp")
                
                val timeDiff = abs(point.timestamps - deviceTimestamp)
                Log.d(TAG, "  - Time difference: ${timeDiff}ms (${timeDiff / 1000}s)")
            }
        } ?: Log.d(TAG, "NO EXCURSION START FOUND")
        
        Log.d(TAG, "=== END EXCURSION DETECTION ===")
    }
    
    /**
     * Parses date-time string to timestamp
     */
    private fun parseDateTime(dateTime: String): Long? {
        return try {
            dateFormat.parse(dateTime)?.time
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing date time: $dateTime", e)
            null
        }
    }
    
    /**
     * Formats timestamp to readable string
     */
    private fun formatTimestamp(timestamp: Long): String {
        return try {
            dateFormat.format(Date(timestamp))
        } catch (e: Exception) {
            "Invalid timestamp"
        }
    }
    
    private fun createEmptyAnalysis(): TemperatureHistoryAnalysis {
        return TemperatureHistoryAnalysis(
            allDataPoints = emptyList(),
            excursionAnalysis = createEmptyExcursionAnalysis(),
            totalDataPoints = 0,
            timeRangeStart = null,
            timeRangeEnd = null
        )
    }
    
    private fun createEmptyExcursionAnalysis(): ExcursionAnalysis {
        return ExcursionAnalysis(
            excursionStartTime = null,
            excursionStartTemperature = null,
            excursionDuration = null,
            excursionDataPoints = emptyList(),
            isInExcursion = false,
            normalRangeMin = NORMAL_TEMP_MIN,
            normalRangeMax = NORMAL_TEMP_MAX
        )
    }
}

// Extension functions to improve readability and reduce code duplication
private fun HtData.isNormalTemperature(): Boolean {
    return this.temperature >= TemperatureHistoryAnalyzer.NORMAL_TEMP_MIN && 
           this.temperature <= TemperatureHistoryAnalyzer.NORMAL_TEMP_MAX
}

private fun TemperatureDataPoint.formattedTimestamp(): String {
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        "Invalid timestamp"
    }
}

// Make constants accessible to extension functions
private const val NORMAL_TEMP_MIN = 2.0f
private const val NORMAL_TEMP_MAX = 8.0f