package com.choruscoldchain

import android.app.Application
import android.util.Log
import com.choruscoldchain.utils.AppDownloadTracker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MinewSensorApplication : Application() {
    
    companion object {
        private const val TAG = "MinewSensorApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        setupGlobalExceptionHandler()
        initializeAppDownloadTracking()
    }
    
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            when (throwable) {
                is SecurityException -> {
                    Log.e(TAG, "SecurityException caught globally: ${throwable.message}", throwable)
                }
                else -> {
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            }
        }
    }
    
    /**
     * Initializes app download tracking.
     */
    private fun initializeAppDownloadTracking() {
        try {
            val appDownloadTracker = AppDownloadTracker.getInstance(this)
            
            appDownloadTracker.logDownloadInstance()
            
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
