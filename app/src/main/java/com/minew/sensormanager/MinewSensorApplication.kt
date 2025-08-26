package com.minew.sensormanager

import android.app.Application
import android.util.Log
import com.minew.sensormanager.utils.AppDownloadTracker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MinewSensorApplication : Application() {
    
    companion object {
        private const val TAG = "MinewSensorApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Set up global exception handler to catch SecurityExceptions
        setupGlobalExceptionHandler()
        
        // Initialize app download tracking
        initializeAppDownloadTracking()
        
        // Initialize any global components here
        // For example: Crash reporting, Analytics, etc.
    }
    
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            when (throwable) {
                is SecurityException -> {
                    Log.e(TAG, "SecurityException caught globally: ${throwable.message}", throwable)
                    // Log the error but don't crash the app
                    // You could also show a user-friendly message here
                }
                else -> {
                    // Let the default handler handle other exceptions
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            }
        }
    }
    
    /**
     * Initializes app download tracking and logs installation information.
     */
    private fun initializeAppDownloadTracking() {
        try {
            val appDownloadTracker = AppDownloadTracker.getInstance(this)
            
            // Log installation information
            appDownloadTracker.logDownloadInstance()
            
            // Log additional installation details
            Log.i(TAG, "App Installation Details:")
            Log.i(TAG, "Installation ID: ${appDownloadTracker.getInstallationId()}")
            Log.i(TAG, "Is Fresh Installation: ${appDownloadTracker.isFreshInstallation()}")
            Log.i(TAG, "App Version: ${appDownloadTracker.getAppVersion()}")
            
            if (appDownloadTracker.isFreshInstallation()) {
                Log.i(TAG, "This is a fresh installation - first time the app is running")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing app download tracking", e)
        }
    }
}
