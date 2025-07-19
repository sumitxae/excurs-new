package com.minew.mst03demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.minew.ble.mst03.bean.MST03Entity;
import com.minew.ble.mst03.manager.MST03SensorBleManager;
import com.minew.ble.v3.interfaces.OnScanDevicesResultListener;
import com.minew.ble.v3.utils.BLETool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BackgroundScanService extends Service {
    private static final String TAG = "BackgroundScanService";
    private static final String CHANNEL_ID = "scan_service_channel";
    private static final int NOTIFICATION_ID = 1;
    
    private MST03SensorBleManager mBleManager;
    private Handler scanHandler = new Handler(Looper.getMainLooper());
    private HttpLogger httpLogger;
    private boolean isScanning = false;
    private boolean isPaused = false;
    private DeviceDiscoveryManager deviceManager;
    private PowerManager.WakeLock wakeLock;
    
    // Scan cycle: 6 seconds scan + 6 seconds wait
    private static final int SCAN_DURATION = 6 * 1000; // 6 seconds
    private static final int WAIT_DURATION = 6 * 1000; // 6 seconds wait
    
    public void pauseScanning() {
        Log.d(TAG, "Pausing background scanning");
        isPaused = true;
        if (isScanning && mBleManager != null) {
            try {
                mBleManager.stopScan(this);
                isScanning = false;
                Log.d(TAG, "Background scanning paused");
            } catch (Exception e) {
                Log.e(TAG, "Error pausing scan: " + e.getMessage());
            }
        }
    }
    
    public void resumeScanning() {
        Log.d(TAG, "Resuming background scanning");
        isPaused = false;
        if (!isScanning) {
            startScan();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initBleManager();
        httpLogger = new HttpLogger();
        deviceManager = DeviceDiscoveryManager.getInstance();
        
        // Acquire wake lock to keep service running when device is locked
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BackgroundScanService::WakeLock");
            wakeLock.acquire();
            Log.d(TAG, "Wake lock acquired to keep service running");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Background scan service started");
        
        // Handle pause/resume actions
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "Received action: " + action);
            
            switch (action) {
                case "PAUSE_SCAN":
                    pauseScanning();
                    return START_STICKY;
                case "RESUME_SCAN":
                    resumeScanning();
                    return START_STICKY;
            }
        }
        
        try {
            // Create notification for foreground service with higher priority
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Beacon Scanner Active")
                    .setContentText("Scanning for beacons in background")
                    .setSmallIcon(R.drawable.ic_temp)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Changed from LOW to DEFAULT
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Foreground service started successfully");
            
            // Start the scanning cycle
            startScanningCycle();
            
            // Start periodic health check
            startHealthCheck();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }
        
        return START_STICKY; // This ensures the service restarts if killed
    }
    
    private void initBleManager() {
        try {
            // Get the singleton instance
            mBleManager = MST03SensorBleManager.getInstance();
            if (mBleManager == null) {
                Log.e(TAG, "Failed to get BLE manager instance");
                return;
            }
            
            // Don't set connection listeners in background service to avoid conflicts
            // The main activity will handle connection state management
            Log.d(TAG, "BLE manager initialized successfully for background scanning");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing BLE manager: " + e.getMessage());
        }
    }
    
    private void startScanningCycle() {
        if (mBleManager == null) {
            Log.e(TAG, "BLE manager not initialized, cannot start scanning");
            return;
        }
        
        Log.d(TAG, "Starting background scanning cycle");
        startScan();
    }
    
    private void restartServiceIfNeeded() {
        // If service was killed, restart it
        if (!isScanning && !isPaused) {
            Log.d(TAG, "Service appears to have been killed, restarting scan cycle");
            startScanningCycle();
        }
    }
    
    private void startHealthCheck() {
        // Check every 30 seconds if the service is still running properly
        scanHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Health check - Service running: " + (!isPaused) + ", Scanning: " + isScanning);
                
                // If not scanning and not paused, restart
                if (!isScanning && !isPaused) {
                    Log.d(TAG, "Health check detected service not scanning, restarting");
                    startScanningCycle();
                }
                
                // Schedule next health check
                startHealthCheck();
            }
        }, 30000); // 30 seconds
    }
    
    private void startScan() {
        if (isScanning || isPaused) {
            Log.d(TAG, "Scan already in progress or paused, skipping");
            return;
        }
        
        try {
            isScanning = true;
            Log.d(TAG, "Starting background BLE scan...");
            
            mBleManager.startScan(this, SCAN_DURATION, new OnScanDevicesResultListener<MST03Entity>() {
                @Override
                public void onScanResult(List<MST03Entity> list) {
                    Log.d(TAG, "Background scan result: " + list.size() + " devices found");
                    
                    if (list.size() > 0) {
                        // Log detailed data for each device
                        for (MST03Entity mst03Entity : list) {
                            Log.d("ScanDebug", "=== BACKGROUND SCAN DEVICE DISCOVERED ===");
                            Log.d("ScanDebug", "Device MAC: " + mst03Entity.getMacAddress());
                            Log.d("ScanDebug", "Device Name: " + mst03Entity.getName());
                            Log.d("ScanDebug", "Device RSSI: " + mst03Entity.getRssi());
                            
                            // Specifically check for battery, temperature, and firmware
                            Log.d("ScanDebug", "--- CRITICAL SCAN DATA ---");
                            
                            // Check DeviceStaticInfoFrame for battery and firmware
                            com.minew.ble.mst03.frames.DeviceStaticInfoFrame deviceInfo = 
                                (com.minew.ble.mst03.frames.DeviceStaticInfoFrame) mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME);
                            if (deviceInfo != null) {
                                Log.d("ScanDebug", "✅ SCAN DEVICE STATIC INFO FOUND:");
                                Log.d("ScanDebug", "   Battery Level: " + deviceInfo.getBattery() + "%");
                                Log.d("ScanDebug", "   Firmware Version: " + deviceInfo.getFirmwareVersion());
                                Log.d("ScanDebug", "   MAC Address: " + deviceInfo.getMacAddress());
                                Log.d("ScanDebug", "   Frame Version: " + deviceInfo.getFrameVersion());
                            } else {
                                Log.d("ScanDebug", "❌ SCAN DEVICE STATIC INFO FRAME NOT AVAILABLE");
                            }
                            
                            // Check CombinationFrame for temperature
                            com.minew.ble.mst03.frames.CombinationFrame comboFrame = 
                                (com.minew.ble.mst03.frames.CombinationFrame) mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
                            if (comboFrame != null) {
                                Log.d("ScanDebug", "✅ SCAN COMBINATION FRAME FOUND:");
                                Log.d("ScanDebug", "   Temperature: " + comboFrame.getTemperature() + "°C");
                                Log.d("ScanDebug", "   xAxis: " + comboFrame.getxAxis());
                                Log.d("ScanDebug", "   yAxis: " + comboFrame.getyAxis());
                                Log.d("ScanDebug", "   zAxis: " + comboFrame.getzAxis());
                            } else {
                                Log.d("ScanDebug", "❌ SCAN COMBINATION FRAME NOT AVAILABLE");
                            }
                            
                            Log.d("ScanDebug", "--- END CRITICAL SCAN DATA ---");
                            
                            // Log all available frames in scan result
                            Log.d("ScanDebug", "--- BACKGROUND SCAN FRAMES ---");
                            try {
                                com.minew.ble.v3.enums.FrameType[] allFrameTypes = com.minew.ble.v3.enums.FrameType.values();
                                for (com.minew.ble.v3.enums.FrameType frameType : allFrameTypes) {
                                    try {
                                        Object frame = mst03Entity.getMinewFrame(frameType);
                                        if (frame != null) {
                                            Log.d("ScanDebug", "Background Scan Frame: " + frameType + " - AVAILABLE");
                                            Log.d("ScanDebug", "  Frame Object: " + frame.getClass().getSimpleName());
                                            Log.d("ScanDebug", "  Frame toString: " + frame.toString());
                                            
                                            // Try to get specific data based on frame type
                                            if (frame instanceof com.minew.ble.mst03.frames.DeviceStaticInfoFrame) {
                                                Log.d("ScanDebug", "  Background Scan Device Info:");
                                                Log.d("ScanDebug", "    - MAC: " + ((com.minew.ble.mst03.frames.DeviceStaticInfoFrame) frame).getMacAddress());
                                                Log.d("ScanDebug", "    - Battery: " + ((com.minew.ble.mst03.frames.DeviceStaticInfoFrame) frame).getBattery() + "%");
                                                Log.d("ScanDebug", "    - Firmware: " + ((com.minew.ble.mst03.frames.DeviceStaticInfoFrame) frame).getFirmwareVersion());
                                            } else if (frame instanceof com.minew.ble.mst03.frames.CombinationFrame) {
                                                Log.d("ScanDebug", "  Background Scan Combination Frame:");
                                                Log.d("ScanDebug", "    - Temperature: " + ((com.minew.ble.mst03.frames.CombinationFrame) frame).getTemperature() + "°C");
                                            }
                                        } else {
                                            Log.d("ScanDebug", "Background Scan Frame: " + frameType + " - NULL");
                                        }
                                    } catch (Exception e) {
                                        Log.d("ScanDebug", "Background Scan Frame: " + frameType + " - ERROR: " + e.getMessage());
                                    }
                                }
                            } catch (Exception e) {
                                Log.d("ScanDebug", "Background Scan Frames Error: " + e.getMessage());
                            }
                            Log.d("ScanDebug", "=== END BACKGROUND SCAN DEVICE DISCOVERED ===");
                        }
                        
                        // Update the shared device manager
                        deviceManager.updateDevices(list);
                        
                        // Log scan data for each device
                        for (MST03Entity device : list) {
                            Log.d(TAG, "Background device: " + device.getMacAddress() + " - " + device.getName());
                            
                            // Log to HTTP server only if we have valid temperature data
                            float temperature = 0.0f;
                            int battery = 0;
                            String firmwareVersion = "Unknown";
                            boolean hasValidTemperature = false;
                            
                            if (device.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME) != null) {
                                com.minew.ble.mst03.frames.CombinationFrame comboFrame = 
                                    (com.minew.ble.mst03.frames.CombinationFrame) device.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
                                temperature = comboFrame.getTemperature();
                                
                                // Validate temperature - only log if temperature is valid (not 0, not NaN)
                                if (temperature != 0.0f && !Float.isNaN(temperature)) {
                                    hasValidTemperature = true;
                                    Log.d(TAG, "Valid temperature found: " + temperature + "°C for device: " + device.getMacAddress());
                                } else {
                                    Log.d(TAG, "Invalid temperature (0 or NaN): " + temperature + "°C for device: " + device.getMacAddress() + " - skipping HTTP log");
                                }
                            }
                            
                            if (device.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME) != null) {
                                com.minew.ble.mst03.frames.DeviceStaticInfoFrame staticFrame = 
                                    (com.minew.ble.mst03.frames.DeviceStaticInfoFrame) device.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME);
                                battery = staticFrame.getBattery();
                                firmwareVersion = staticFrame.getFirmwareVersion();
                            }
                            
                            // Only send HTTP log if we have valid temperature data
                            if (hasValidTemperature) {
                                Log.d(TAG, "Sending HTTP log for device: " + device.getMacAddress() + " with valid temperature: " + temperature + "°C");
                                httpLogger.logScanData(device.getMacAddress(), temperature, battery, firmwareVersion, device.getRssi());
                            } else {
                                Log.d(TAG, "Skipping HTTP log for device: " + device.getMacAddress() + " - no valid temperature data");
                            }
                        }
                    }
                }

                @Override
                public void onStopScan(List<MST03Entity> list) {
                    Log.d(TAG, "Background scan stopped");
                    isScanning = false;
                    
                    // Schedule next scan after wait period only if not paused
                    if (!isPaused) {
                        scanHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Check if we should continue scanning
                                if (!isScanning && !isPaused) {
                                    startScan();
                                }
                            }
                        }, WAIT_DURATION);
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting background scan: " + e.getMessage());
            isScanning = false;
            
            // Retry after a delay only if not paused
            if (!isPaused) {
                scanHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isScanning && !isPaused) {
                            startScan();
                        }
                    }
                }, WAIT_DURATION);
            }
        }
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
                    NotificationManager.IMPORTANCE_DEFAULT // Changed from LOW to DEFAULT
            );
            channel.setDescription("Background beacon scanning service");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); // Show on lock screen
            channel.enableLights(false);
            channel.enableVibration(false);
            
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
        
        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "Wake lock released");
        }
        
        // Stop scanning
        if (mBleManager != null && isScanning) {
            try {
                mBleManager.stopScan(this);
                isScanning = false;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping scan: " + e.getMessage());
            }
        }
        
        // Remove any pending scan operations
        scanHandler.removeCallbacksAndMessages(null);
        
        // Shutdown HTTP logger
        if (httpLogger != null) {
            httpLogger.shutdown();
        }
    }
    
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "App removed from recents - stopping background service");
        
        // Stop the service when app is removed from recents
        stopSelf();
    }
} 