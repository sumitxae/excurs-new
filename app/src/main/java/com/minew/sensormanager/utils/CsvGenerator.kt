package com.minew.sensormanager.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.minew.sensormanager.data.models.TemperatureDataPoint
import com.minew.sensormanager.utils.PermissionHelper
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvGenerator {
    private const val TAG = "CsvGenerator"
    
    /**
     * Generates a CSV file with temperature historical data
     * @param context Application context
     * @param deviceMac Device MAC address
     * @param dataPoints List of temperature data points
     * @return File object if successful, null otherwise
     */
    fun generateTemperatureCsv(
        context: Context,
        deviceMac: String,
        dataPoints: List<TemperatureDataPoint>
    ): File? {
        return try {
            Log.d(TAG, "Generating CSV for device: $deviceMac with ${dataPoints.size} data points")
            
            // Check storage permissions
            if (!PermissionHelper.hasAllRequiredPermissions(context)) {
                Log.e(TAG, "Storage permissions not granted")
                return null
            }
            
            // Create filename with MAC address
            val fileName = "trips_data_${deviceMac.replace(":", "_")}.csv"
            
            // Get downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val csvFile = File(downloadsDir, fileName)
            
            // Write CSV content
            FileWriter(csvFile).use { writer ->
                // Write header
                writer.append("Date&Time,Temperature,Temperature Status,Device MAC,Alarm\n")
                
                // Write data rows
                dataPoints.forEach { dataPoint ->
                    val dateTime = formatTimestamp(dataPoint.timestamp)
                    val temperature = dataPoint.temperature.toString()
                    val temperatureStatus = getTemperatureStatus(dataPoint.temperature)
                    val macAddress = dataPoint.macAddress ?: deviceMac
                    val alarm = getAlarmStatus(dataPoint.temperature)
                    
                    val csvLine = "$dateTime,$temperature,$temperatureStatus,$macAddress,$alarm"
                    writer.append("$csvLine\n")
                    
                    Log.d(TAG, "CSV Line: $csvLine")
                }
            }
            
            Log.d(TAG, "CSV file generated successfully: ${csvFile.absolutePath}")
            csvFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating CSV file", e)
            null
        }
    }
    
    /**
     * Formats timestamp to MM-DD-YYYY format
     */
    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val dateFormat = SimpleDateFormat("MM-dd-yyyy HH:mm:ss", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting timestamp: $timestamp", e)
            "Invalid Date"
        }
    }
    
    /**
     * Determines temperature status based on temperature value
     */
    private fun getTemperatureStatus(temperature: Float): String {
        return when {
            temperature > 8.0f -> "HIGH TEMPERATURE"
            temperature < 2.0f -> "LOW TEMPERATURE"
            else -> "NORMAL"
        }
    }
    
    /**
     * Determines alarm status based on temperature value
     */
    private fun getAlarmStatus(temperature: Float): String {
        return when {
            temperature > 8.0f -> "High"
            temperature < 2.0f -> "Low"
            else -> ""
        }
    }
}
