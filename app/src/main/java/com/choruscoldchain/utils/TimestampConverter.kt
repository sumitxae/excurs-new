package com.choruscoldchain.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object TimestampConverter {
    private const val TAG = "TimestampConverter"
    
    /**
     * Converts device-relative timestamps to real-world timestamps
     * 
     * @param tempEventTimestamp Device timestamp when excursion event occurred
     * @param currentTimestamp Device's current timestamp at scan time
     * @param connectionTime Real-world Unix timestamp when connection was established
     * @return Converted real-world timestamp in milliseconds, or null if conversion failed
     */
    fun convertDeviceTimestampToRealTime(
        tempEventTimestamp: Long?,
        currentTimestamp: Long?,
        connectionTime: Long
    ): Long? {
        return try {
            if (tempEventTimestamp == null || currentTimestamp == null) {
                Log.w(TAG, "Missing timestamp data: tempEvent=$tempEventTimestamp, current=$currentTimestamp")
                return null
            }
            
            // Calculate offset between real time and device time
            val timeOffset = connectionTime - currentTimestamp
            Log.d(TAG, "Time offset calculation: connectionTime=$connectionTime, currentTimestamp=$currentTimestamp, offset=$timeOffset")
            
            // Apply offset to convert device event timestamp to real timestamp
            val actualEventTimestamp = tempEventTimestamp + timeOffset
            Log.d(TAG, "Converted timestamp: tempEventTimestamp=$tempEventTimestamp + offset=$timeOffset = $actualEventTimestamp")
            
            // Validate the converted timestamp
            if (isValidTimestamp(actualEventTimestamp)) {
                actualEventTimestamp
            } else {
                Log.w(TAG, "Invalid converted timestamp: $actualEventTimestamp")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting device timestamp", e)
            null
        }
    }
    
    /**
     * Formats timestamp to human-readable format
     */
    fun formatTimestamp(timestamp: Long): String {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getDefault()
            dateFormat.format(Date(timestamp))
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting timestamp", e)
            "Invalid timestamp"
        }
    }
    
    /**
     * Validates if a timestamp is reasonable
     */
    private fun isValidTimestamp(timestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val oneYearInMs = 365L * 24 * 60 * 60 * 1000
        
        // Check if timestamp is not too far in the past (more than 1 year)
        if (timestamp < currentTime - oneYearInMs) {
            Log.w(TAG, "Timestamp too far in the past: $timestamp")
            return false
        }
        
        // Check if timestamp is not too far in the future (more than 1 hour)
        if (timestamp > currentTime + 60 * 60 * 1000) {
            Log.w(TAG, "Timestamp too far in the future: $timestamp")
            return false
        }
        
        return true
    }
    
    /**
     * Gets current UTC time in milliseconds
     */
    fun getCurrentUTCTime(): Long {
        return System.currentTimeMillis()
    }
}
