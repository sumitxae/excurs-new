package com.choruscoldchain.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.choruscoldchain.R
import com.choruscoldchain.data.models.SensorData
import com.choruscoldchain.ui.activities.MainActivity
import com.choruscoldchain.utils.AppIdUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val CHANNEL_ID = "sensor_alerts"
        private const val NOTIFICATION_ID_TEMPERATURE = 1001
        private const val NOTIFICATION_ID_LIGHT = 1002
        private const val NOTIFICATION_ID_BATTERY = 1003
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannel()
    }
    
    fun initialize() {
        // Initialize alert service
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sensor Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for sensor threshold violations"
                enableVibration(true)
                enableLights(true)
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun checkForAlerts(sensorData: SensorData) {
        // Log alert with installation tracking
        logAlertWithInstallationId(sensorData)
        
        // Check temperature alerts
        if (sensorData.hasTemperatureAlarm) {
            showTemperatureAlert(sensorData)
        }
        
        // Check light alerts
        if (sensorData.hasLightAlarm) {
            showLightAlert(sensorData)
        }
        
        // Check battery alerts
        if (sensorData.battery < 20) {
            showBatteryAlert(sensorData)
        }
    }
    
    /**
     * Logs alert information with installation ID for tracking purposes.
     */
    private fun logAlertWithInstallationId(sensorData: SensorData) {
        try {
            val installationId = AppIdUtils.getInstallationId(context)
            val trackingId = AppIdUtils.getTrackingId(context)
            
            android.util.Log.i("AlertService", "Alert detected for installation: $installationId")
            android.util.Log.i("AlertService", "Tracking ID: $trackingId")
            android.util.Log.i("AlertService", "Device MAC: ${sensorData.macAddress}")
            android.util.Log.i("AlertService", "Temperature: ${sensorData.temperature}°C")
            android.util.Log.i("AlertService", "Has Temperature Alarm: ${sensorData.hasTemperatureAlarm}")
            android.util.Log.i("AlertService", "Has Light Alarm: ${sensorData.hasLightAlarm}")
            android.util.Log.i("AlertService", "Battery Level: ${sensorData.battery}%")
            
        } catch (e: Exception) {
            android.util.Log.e("AlertService", "Error logging alert with installation ID", e)
        }
    }
    
    private fun showTemperatureAlert(sensorData: SensorData) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Temperature Alert")
            .setContentText("Temperature threshold exceeded on device ${sensorData.macAddress}")
            .setSmallIcon(R.drawable.ic_temperature)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_TEMPERATURE, notification)
    }
    
    private fun showLightAlert(sensorData: SensorData) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Light Intensity Alert")
            .setContentText("Light intensity threshold exceeded on device ${sensorData.macAddress}")
            .setSmallIcon(R.drawable.ic_light)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_LIGHT, notification)
    }
    
    private fun showBatteryAlert(sensorData: SensorData) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Battery Low Alert")
            .setContentText("Battery level is low (${sensorData.battery}%) on device ${sensorData.macAddress}")
            .setSmallIcon(R.drawable.ic_battery)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_BATTERY, notification)
    }
    
    fun clearAlerts() {
        notificationManager.cancelAll()
    }
}
