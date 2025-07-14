package com.minew.mst03demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class BackgroundScanService extends Service {
    private static final String TAG = "BackgroundScanService";
    private static final String CHANNEL_ID = "scan_service_channel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Background scan service started");
        
        try {
            // Create notification for foreground service
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Beacon Scanner Active")
                    .setContentText("Scanning for beacons in background")
                    .setSmallIcon(R.drawable.ic_temp)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Foreground service started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service: " + e.getMessage());
            // Stop the service if we can't start foreground
            stopSelf();
            return START_NOT_STICKY;
        }
        
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Beacon Scanner",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background beacon scanning service");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Background scan service destroyed");
    }
} 