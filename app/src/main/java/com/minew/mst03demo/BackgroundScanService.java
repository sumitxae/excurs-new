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
    private List<MST03Entity> discoveredDevices = new ArrayList<>();
    private HttpLogger httpLogger;
    private boolean isScanning = false;
    
    // Scan cycle: 6 seconds scan + 6 seconds wait
    private static final int SCAN_DURATION = 6 * 1000; // 6 seconds
    private static final int WAIT_DURATION = 6 * 1000; // 6 seconds wait

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initBleManager();
        httpLogger = new HttpLogger();
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
                    .setOngoing(true)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Foreground service started successfully");
            
            // Start the scanning cycle
            startScanningCycle();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }
        
        return START_STICKY;
    }
    
    private void initBleManager() {
        try {
            mBleManager = MST03SensorBleManager.getInstance();
            if (mBleManager == null) {
                Log.e(TAG, "Failed to get BLE manager instance");
                return;
            }
            Log.d(TAG, "BLE manager initialized successfully");
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
    
    private void startScan() {
        if (isScanning) {
            Log.d(TAG, "Scan already in progress, skipping");
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
                        // Update discovered devices list
                        updateDiscoveredDevices(list);
                        
                        // Log scan data for each device
                        for (MST03Entity device : list) {
                            Log.d(TAG, "Background device: " + device.getMacAddress() + " - " + device.getName());
                            
                            // Log to HTTP server
                            float temperature = 0.0f;
                            int battery = 0;
                            String firmwareVersion = "Unknown";
                            
                            if (device.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME) != null) {
                                com.minew.ble.mst03.frames.CombinationFrame comboFrame = 
                                    (com.minew.ble.mst03.frames.CombinationFrame) device.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
                                temperature = comboFrame.getTemperature();
                            }
                            
                            if (device.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME) != null) {
                                com.minew.ble.mst03.frames.DeviceStaticInfoFrame staticFrame = 
                                    (com.minew.ble.mst03.frames.DeviceStaticInfoFrame) device.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME);
                                battery = staticFrame.getBattery();
                                firmwareVersion = staticFrame.getFirmwareVersion();
                            }
                            
                            httpLogger.logScanData(device.getMacAddress(), temperature, battery, firmwareVersion, device.getRssi());
                        }
                    }
                }

                @Override
                public void onStopScan(List<MST03Entity> list) {
                    Log.d(TAG, "Background scan stopped");
                    isScanning = false;
                    
                    // Schedule next scan after wait period
                    scanHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startScan();
                        }
                    }, WAIT_DURATION);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting background scan: " + e.getMessage());
            isScanning = false;
            
            // Retry after a delay
            scanHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startScan();
                }
            }, WAIT_DURATION);
        }
    }
    
    private void updateDiscoveredDevices(List<MST03Entity> newDevices) {
        for (MST03Entity newDevice : newDevices) {
            boolean found = false;
            for (int i = 0; i < discoveredDevices.size(); i++) {
                MST03Entity existingDevice = discoveredDevices.get(i);
                if (existingDevice.getMacAddress().equals(newDevice.getMacAddress())) {
                    // Update existing device with new data
                    discoveredDevices.set(i, newDevice);
                    found = true;
                    break;
                }
            }
            if (!found) {
                discoveredDevices.add(newDevice);
            }
        }
        
        // Sort by RSSI (strongest first)
        discoveredDevices.sort(new Comparator<MST03Entity>() {
            @Override
            public int compare(MST03Entity o1, MST03Entity o2) {
                return o2.getRssi() - o1.getRssi();
            }
        });
        
        Log.d(TAG, "Updated discovered devices list: " + discoveredDevices.size() + " total devices");
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
            channel.setShowBadge(false);
            
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
    }
} 