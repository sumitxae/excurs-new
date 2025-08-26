package com.minew.sensormanager.utils

import android.content.Context
import android.util.Log

/**
 * Utility class providing easy access to app ID and installation tracking functionality.
 * This class serves as a convenient wrapper around AppDownloadTracker for common use cases.
 */
object AppIdUtils {
    
    private const val TAG = "AppIdUtils"
    
    /**
     * Gets the unique installation ID for the current app instance.
     * @param context Application context
     * @return The unique installation ID
     */
    fun getInstallationId(context: Context): String {
        return try {
            AppDownloadTracker.getInstance(context).getInstallationId()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installation ID", e)
            "UNKNOWN"
        }
    }
    
    /**
     * Checks if this is a fresh installation.
     * @param context Application context
     * @return true if this is a fresh installation, false otherwise
     */
    fun isFreshInstallation(context: Context): Boolean {
        return try {
            AppDownloadTracker.getInstance(context).isFreshInstallation()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking fresh installation status", e)
            false
        }
    }
    
    /**
     * Gets the app version.
     * @param context Application context
     * @return App version string
     */
    fun getAppVersion(context: Context): String {
        return try {
            AppDownloadTracker.getInstance(context).getAppVersion()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app version", e)
            "Unknown"
        }
    }
    
    /**
     * Gets a formatted string with basic installation information.
     * @param context Application context
     * @return Formatted installation info string
     */
    fun getInstallationSummary(context: Context): String {
        return try {
            val tracker = AppDownloadTracker.getInstance(context)
            "ID: ${tracker.getInstallationId()} | Version: ${tracker.getAppVersion()} | Fresh: ${tracker.isFreshInstallation()}"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installation summary", e)
            "Installation info unavailable"
        }
    }
    
    /**
     * Creates a simple identifier that can be used for analytics or tracking.
     * @param context Application context
     * @return A simple tracking identifier
     */
    fun getTrackingId(context: Context): String {
        return try {
            val tracker = AppDownloadTracker.getInstance(context)
            "${tracker.getInstallationId()}_${tracker.getAppVersion()}"
        } catch (e: Exception) {
            Log.e(TAG, "Error creating tracking ID", e)
            "unknown_tracking_id"
        }
    }
    
    /**
     * Logs the current installation information for debugging purposes.
     * @param context Application context
     */
    fun logInstallationInfo(context: Context) {
        try {
            AppDownloadTracker.getInstance(context).logDownloadInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Error logging installation info", e)
        }
    }
    
    /**
     * Gets a complete installation report for analytics or debugging.
     * @param context Application context
     * @return Complete installation report
     */
    fun getInstallationReport(context: Context): String {
        return try {
            AppDownloadTracker.getInstance(context).createDownloadInstanceReport()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installation report", e)
            "Installation report unavailable"
        }
    }
}
