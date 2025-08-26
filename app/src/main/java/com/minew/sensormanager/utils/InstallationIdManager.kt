package com.minew.sensormanager.utils

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

/**
 * Manages unique installation IDs for the app.
 * Each app installation gets a unique ID that persists until the app is uninstalled.
 * Uninstalling and reinstalling will generate a new unique ID.
 */
class InstallationIdManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "InstallationIdManager"
        private const val PREF_NAME = "installation_prefs"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_FIRST_INSTALL_TIME = "first_install_time"
        
        @Volatile
        private var instance: InstallationIdManager? = null
        
        fun getInstance(context: Context): InstallationIdManager {
            return instance ?: synchronized(this) {
                instance ?: InstallationIdManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private var installationId: String? = null
    private var firstInstallTime: Long = 0
    
    init {
        loadOrGenerateInstallationId()
    }
    
    /**
     * Gets the unique installation ID for this app instance.
     * @return The unique installation ID
     */
    fun getInstallationId(): String {
        return installationId ?: ""
    }
    
    /**
     * Gets the timestamp when this installation was first created.
     * @return The first install timestamp in milliseconds
     */
    fun getFirstInstallTime(): Long {
        return firstInstallTime
    }
    
    /**
     * Checks if this is a fresh installation (first time the app is run).
     * @return true if this is a fresh installation, false otherwise
     */
    fun isFreshInstallation(): Boolean {
        return firstInstallTime == 0L
    }
    
    /**
     * Gets installation information as a formatted string.
     * @return Installation info string
     */
    fun getInstallationInfo(): String {
        return "Installation ID: $installationId\nFirst Install Time: ${
            if (firstInstallTime > 0) Date(firstInstallTime).toString() else "Unknown"
        }"
    }
    
    /**
     * Loads existing installation ID or generates a new one.
     */
    private fun loadOrGenerateInstallationId() {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Try to load existing installation ID
        installationId = prefs.getString(KEY_INSTALLATION_ID, null)
        firstInstallTime = prefs.getLong(KEY_FIRST_INSTALL_TIME, 0)
        
        // If no installation ID exists, generate a new one
        if (installationId == null) {
            installationId = generateUniqueInstallationId()
            firstInstallTime = System.currentTimeMillis()
            
            // Save the new installation ID
            prefs.edit().apply {
                putString(KEY_INSTALLATION_ID, installationId)
                putLong(KEY_FIRST_INSTALL_TIME, firstInstallTime)
                apply()
            }
            
            Log.i(TAG, "Generated new installation ID: $installationId")
        } else {
            Log.i(TAG, "Loaded existing installation ID: $installationId")
        }
    }
    
    /**
     * Generates a unique installation ID based on device-specific information.
     * @return A unique installation ID
     */
    private fun generateUniqueInstallationId(): String {
        return try {
            // Combine multiple sources for uniqueness
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val packageName = context.packageName
            val timestamp = System.currentTimeMillis().toString()
            val randomUuid = UUID.randomUUID().toString()
            
            // Create a unique string combining all sources
            val combinedString = "$deviceId$packageName$timestamp$randomUuid"
            
            // Generate SHA-256 hash for consistency and security
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combinedString.toByteArray())
            
            // Convert to hexadecimal string and take first 16 characters
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) {
                    hexString.append('0')
                }
                hexString.append(hex)
            }
            
            hexString.substring(0, 16).uppercase()
            
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "Error generating installation ID", e)
            // Fallback to UUID if SHA-256 is not available
            UUID.randomUUID().toString().replace("-", "").substring(0, 16).uppercase()
        }
    }
    
    /**
     * Resets the installation ID (for testing purposes).
     * This will generate a new installation ID as if the app was freshly installed.
     */
    fun resetInstallationId() {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        // Generate new installation ID
        loadOrGenerateInstallationId()
        Log.i(TAG, "Reset installation ID to: $installationId")
    }
}
