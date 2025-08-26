package com.minew.sensormanager.utils

import android.content.Context
import android.util.Log
import java.util.*

/**
 * Utility class for tracking app download instances and managing installation data.
 * Provides easy access to installation ID and download tracking functionality.
 */
class AppDownloadTracker private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "AppDownloadTracker"
        
        @Volatile
        private var instance: AppDownloadTracker? = null
        
        fun getInstance(context: Context): AppDownloadTracker {
            return instance ?: synchronized(this) {
                instance ?: AppDownloadTracker(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val installationIdManager: InstallationIdManager = InstallationIdManager.getInstance(context)
    
    /**
     * Gets the unique installation ID for this app download instance.
     * @return The unique installation ID
     */
    fun getInstallationId(): String {
        return installationIdManager.getInstallationId()
    }
    
    /**
     * Gets the timestamp when this app was first installed.
     * @return The first install timestamp
     */
    fun getFirstInstallDate(): Date? {
        val timestamp = installationIdManager.getFirstInstallTime()
        return if (timestamp > 0) Date(timestamp) else null
    }
    
    /**
     * Checks if this is a fresh installation (first time the app is run).
     * @return true if this is a fresh installation, false otherwise
     */
    fun isFreshInstallation(): Boolean {
        return installationIdManager.isFreshInstallation()
    }
    
    /**
     * Gets detailed information about this app download instance.
     * @return Formatted string with installation details
     */
    fun getDownloadInstanceInfo(): String {
        val info = StringBuilder()
        info.append("App Download Instance Information:\n")
        info.append("Installation ID: ").append(getInstallationId()).append("\n")
        info.append("First Install Date: ").append(
            getFirstInstallDate()?.toString() ?: "Unknown"
        ).append("\n")
        info.append("Is Fresh Installation: ").append(isFreshInstallation()).append("\n")
        info.append("App Package: ").append(context.packageName).append("\n")
        info.append("App Version: ").append(getAppVersion()).append("\n")
        
        return info.toString()
    }
    
    /**
     * Gets the current app version.
     * @return App version string
     */
    fun getAppVersion(): String {
        return try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app version", e)
            "Unknown"
        }
    }
    
    /**
     * Logs the current download instance information.
     * Useful for debugging and tracking purposes.
     */
    fun logDownloadInstance() {
        Log.i(TAG, getDownloadInstanceInfo())
    }
    
    /**
     * Creates a download instance report that can be used for analytics or tracking.
     * @return Download instance report as a formatted string
     */
    fun createDownloadInstanceReport(): String {
        val report = StringBuilder()
        report.append("=== APP DOWNLOAD INSTANCE REPORT ===\n")
        report.append("Timestamp: ").append(Date().toString()).append("\n")
        report.append("Installation ID: ").append(getInstallationId()).append("\n")
        report.append("First Install: ").append(
            getFirstInstallDate()?.toString() ?: "Unknown"
        ).append("\n")
        report.append("Fresh Installation: ").append(isFreshInstallation()).append("\n")
        report.append("App Version: ").append(getAppVersion()).append("\n")
        report.append("Package Name: ").append(context.packageName).append("\n")
        report.append("=====================================\n")
        
        return report.toString()
    }
    
    /**
     * Resets the installation ID (for testing purposes).
     * This simulates a fresh app installation.
     */
    fun resetInstallation() {
        installationIdManager.resetInstallationId()
        Log.i(TAG, "Installation reset - new ID: ${getInstallationId()}")
    }
    
    /**
     * Gets installation information from the underlying InstallationIdManager.
     * @return Installation info string
     */
    fun getInstallationInfo(): String {
        return installationIdManager.getInstallationInfo()
    }
}
