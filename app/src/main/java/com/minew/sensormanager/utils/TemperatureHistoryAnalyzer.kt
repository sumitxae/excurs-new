package com.minew.sensormanager.utils

import android.util.Log
import com.minew.sensormanager.data.models.TemperatureDataPoint
import com.minew.sensormanager.data.models.ExcursionAnalysis
import com.minew.sensormanager.data.models.TemperatureHistoryAnalysis
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

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
        rawHistoryData: String,
        deviceTempEventTimestamp: Long?
    ): TemperatureHistoryAnalysis {
        return try {
            logAnalysisStart(deviceTempEventTimestamp, rawHistoryData.length)
            
            val dataPoints = parseTemperatureHistoryData(rawHistoryData).takeIf { it.isNotEmpty() }
                ?: return createEmptyAnalysis().also { Log.w(TAG, "No temperature data points found") }
            
            val sortedDataPoints = dataPoints.sortedBy { it.timestamp }
            logHistoricalData(sortedDataPoints)
            
            val excursionAnalysis = detectExcursionStart(sortedDataPoints, deviceTempEventTimestamp)
            
            TemperatureHistoryAnalysis(
                allDataPoints = sortedDataPoints,
                excursionAnalysis = excursionAnalysis,
                totalDataPoints = sortedDataPoints.size,
                timeRangeStart = sortedDataPoints.first().timestamp,
                timeRangeEnd = sortedDataPoints.last().timestamp
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
        dataPoints: List<TemperatureDataPoint>,
        deviceTempEventTimestamp: Long?
    ): ExcursionAnalysis {
        return try {
            Log.d(TAG, "=== DETECTING EXCURSION START ===")
            Log.d(TAG, "Total data points: ${dataPoints.size}")
            
            val excursionStartPoint = findExcursionStartPoint(dataPoints)
            logExcursionDetectionResult(excursionStartPoint, deviceTempEventTimestamp)
            
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
    private fun findExcursionStartPoint(dataPoints: List<TemperatureDataPoint>): TemperatureDataPoint? {
        Log.d(TAG, "Finding most recent excursion start point from ${dataPoints.size} data points")
        
        // Check if all temperatures are in excursion state
        if (dataPoints.all { !it.isNormalTemperature() }) {
            Log.d(TAG, "ALL temperatures are in excursion state - using oldest record as excursion start")
            return dataPoints.first().also {
                Log.d(TAG, "Excursion start (all excursion case): ${it.temperature}°C at ${it.formattedTimestamp()}")
            }
        }
        
        // Find excursion start by looking backwards from the most recent data
        for (i in dataPoints.indices.reversed()) {
            val currentPoint = dataPoints[i]
            Log.d(TAG, "Checking point $i: ${currentPoint.temperature}°C at ${currentPoint.formattedTimestamp()}")
            
            if (!currentPoint.isNormalTemperature()) {
                Log.d(TAG, "Found excursion temperature: ${currentPoint.temperature}°C at ${currentPoint.formattedTimestamp()}")
                
                // Look backwards to find the last normal temperature before this excursion
                val lastNormalIndex = (i - 1 downTo 0).find { j ->
                    val previousPoint = dataPoints[j]
                    Log.d(TAG, "Looking backwards at point $j: ${previousPoint.temperature}°C at ${previousPoint.formattedTimestamp()}")
                    previousPoint.isNormalTemperature()
                }
                
                return if (lastNormalIndex != null && lastNormalIndex + 1 < dataPoints.size) {
                    dataPoints[lastNormalIndex + 1].also { excursionStart ->
                        Log.d(TAG, "Excursion start point (after last normal): ${excursionStart.temperature}°C at ${excursionStart.formattedTimestamp()}")
                    }
                } else {
                    // No normal temperature found before this excursion point
                    Log.d(TAG, "No normal temperature found before this excursion point, using oldest record as excursion start")
                    dataPoints.first().also {
                        Log.d(TAG, "Excursion start (no normal found): ${it.temperature}°C at ${it.formattedTimestamp()}")
                    }
                }
            }
        }
        
        Log.d(TAG, "No excursion detected - all temperatures are within normal range")
        return null
    }
    
    /**
     * Parses the raw temperature history data string to extract data points
     */
    private fun parseTemperatureHistoryData(rawData: String): List<TemperatureDataPoint> {
        val dataPoints = mutableListOf<TemperatureDataPoint>()
        
        try {
            rawData.lines().forEach { line ->
                parseTemperatureLine(line)?.let { dataPoint ->
                    dataPoints.add(dataPoint)
                }
            }
            
            Log.d(TAG, "Parsed ${dataPoints.size} temperature data points")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing temperature history data", e)
        }
        
        return dataPoints
    }
    
    /**
     * Parses a single temperature line
     */
    private fun parseTemperatureLine(line: String): TemperatureDataPoint? {
        if (!line.contains("°C") || !line.contains(":")) return null
        
        val parts = line.split(":")
        if (parts.size < 4) return null
        
        val dateTime = "${parts[0]}:${parts[1]}:${parts[2]}"
        val temperature = parts[3].trim().removeSuffix("°C").trim().toFloatOrNull()
        
        return temperature?.let { temp ->
            parseDateTime(dateTime)?.let { timestamp ->
                TemperatureDataPoint(
                    timestamp = timestamp,
                    temperature = temp,
                    humidity = -128f // Humidity not available in this data
                )
            }
        }
    }
    
    /**
     * Creates excursion analysis when excursion is detected
     */
    private fun createExcursionAnalysis(
        startPoint: TemperatureDataPoint,
        dataPoints: List<TemperatureDataPoint>,
        deviceTempEventTimestamp: Long?
    ): ExcursionAnalysis {
        Log.d(TAG, "Excursion start point found at: ${startPoint.formattedTimestamp()}")
        Log.d(TAG, "Excursion start temperature: ${startPoint.temperature}°C")
        
        // Get all data points from the last normal temperature point onwards
        val lastNormalIndex = dataPoints.indexOfFirst { it.timestamp >= startPoint.timestamp } - 1
        val graphStartIndex = maxOf(0, lastNormalIndex)
        val excursionDataPoints = dataPoints.drop(graphStartIndex)
        
        Log.d(TAG, "Graph will show data from index $graphStartIndex onwards (${excursionDataPoints.size} points)")
        excursionDataPoints.firstOrNull()?.let { firstPoint ->
            Log.d(TAG, "First point in graph: ${firstPoint.temperature}°C at ${firstPoint.formattedTimestamp()}")
        }
        
        // Calculate excursion duration
        val excursionDuration = (deviceTempEventTimestamp ?: System.currentTimeMillis()) - startPoint.timestamp
        Log.d(TAG, "Excursion duration: ${excursionDuration / 1000} seconds")
        
        return ExcursionAnalysis(
            excursionStartTime = startPoint.timestamp,
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
        Log.d(TAG, "No excursion detected - temperature is within normal range")
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
    private fun logHistoricalData(dataPoints: List<TemperatureDataPoint>) {
        Log.d(TAG, "Parsed ${dataPoints.size} temperature data points")
        Log.d(TAG, "=== FULL HISTORICAL TEMPERATURE DATA ===")
        
        dataPoints.forEachIndexed { index, point ->
            val status = if (point.isNormalTemperature()) "NORMAL" else "EXCURSION"
            Log.d(TAG, "Point $index: ${point.temperature}°C at ${point.formattedTimestamp()} [$status]")
        }
        
        Log.d(TAG, "=== END FULL HISTORICAL DATA ===")
    }
    
    /**
     * Logs excursion detection results
     */
    private fun logExcursionDetectionResult(
        excursionStartPoint: TemperatureDataPoint?,
        deviceTempEventTimestamp: Long?
    ) {
        Log.d(TAG, "=== EXCURSION DETECTION RESULT ===")
        
        excursionStartPoint?.let { point ->
            Log.d(TAG, "EXCURSION START FOUND:")
            Log.d(TAG, "  - Temperature: ${point.temperature}°C")
            Log.d(TAG, "  - Timestamp: ${point.formattedTimestamp()}")
            Log.d(TAG, "  - Raw timestamp: ${point.timestamp}")
            
            deviceTempEventTimestamp?.let { deviceTimestamp ->
                Log.d(TAG, "DEVICE TEMP EVENT TIMESTAMP:")
                Log.d(TAG, "  - Device timestamp: ${formatTimestamp(deviceTimestamp)}")
                Log.d(TAG, "  - Raw device timestamp: $deviceTimestamp")
                
                val timeDiff = abs(point.timestamp - deviceTimestamp)
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
private fun TemperatureDataPoint.isNormalTemperature(): Boolean {
    return temperature >= TemperatureHistoryAnalyzer.NORMAL_TEMP_MIN && 
           temperature <= TemperatureHistoryAnalyzer.NORMAL_TEMP_MAX
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