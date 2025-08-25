package com.minew.sensormanager.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.minew.sensormanager.R
import com.minew.sensormanager.data.models.SensorData
import com.minew.sensormanager.ui.activities.MainActivity
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
