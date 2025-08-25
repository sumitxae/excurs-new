package com.minew.sensormanager

import android.app.Application
import android.util.Log
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
}
