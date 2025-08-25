package com.minew.sensormanager.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SensorMonitoringService : Service() {
    
    override fun onBind(intent: Intent?): IBinder? = null
}
