package choruscoldchain.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    
    // ENHANCED: Multiple wake locks for better reliability
    private PowerManager.WakeLock wakeLock;
    private PowerManager.WakeLock cpuWakeLock;
    
    // NEW: Aggressive health monitoring
    private Handler healthHandler = new Handler(Looper.getMainLooper());
    private Runnable healthCheckRunnable;
    private static final int HEALTH_CHECK_INTERVAL = 15000; // 15 seconds
    
    // NEW: Battery optimization detection
    private PowerManager powerManager;
    
    // OPTIMIZED: Faster scan cycle for quicker temperature data
    private static final int SCAN_DURATION = 4 * 1000; // 4 seconds (reduced from 6)
    private static final int WAIT_DURATION = 2 * 1000; // 2 seconds wait (reduced from 6)
    
    // NEW: Track devices that need temperature data
    private Set<String> devicesNeedingTemperature = ConcurrentHashMap.newKeySet();
    
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
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        
        // ENHANCED: Acquire multiple types of wake locks
        acquireWakeLocks();
        
        // NEW: Check for battery optimization
        checkBatteryOptimization();
    }
    
    /**
     * ENHANCED: Acquire multiple wake locks for better reliability
     */
    private void acquireWakeLocks() {
        try {
            if (powerManager != null) {
                // Partial wake lock to keep CPU running
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "BackgroundScanService::MainWakeLock"
                );
                wakeLock.acquire();
                Log.d(TAG, "✅ Main wake lock acquired");
                
                // Additional CPU wake lock for critical scanning periods
                cpuWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "BackgroundScanService::CPUWakeLock"
                );
                cpuWakeLock.acquire();
                Log.d(TAG, "✅ CPU wake lock acquired");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to acquire wake locks: " + e.getMessage());
        }
    }
    
    /**
     * NEW: Check if app is battery optimized and warn user
     */
    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                Log.w(TAG, "⚠️ App is battery optimized - background scanning may be affected");
                showBatteryOptimizationNotification();
            }
        }
    }
    
    /**
     * NEW: Show notification about battery optimization
     */
    private void showBatteryOptimizationNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Beacon Scanner - Battery Optimization")
                        .setContentText("For best performance, disable battery optimization for this app")
                        .setSmallIcon(R.drawable.ic_temp)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build();
                
                notificationManager.notify(NOTIFICATION_ID + 1, notification);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "🚀 Background scan service started");
        
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
            // ENHANCED: Create high-priority notification
            Notification notification = createServiceNotification();
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "✅ Foreground service started successfully");
            
            // Start the scanning cycle
            startScanningCycle();
            
            // NEW: Start aggressive health monitoring
            startHealthMonitoring();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start foreground service: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // ENHANCED: Use START_REDELIVER_INTENT for better recovery
        return START_REDELIVER_INTENT;
    }
    
    /**
     * ENHANCED: Create high-priority notification for better survival
     */
    private Notification createServiceNotification() {
        // Create pending intent for the notification
        Intent notificationIntent = new Intent(this, ScanDevicesListActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🔵 Beacon Scanner Active")
                .setContentText("Continuously scanning for temperature beacons")
                .setSmallIcon(R.drawable.ic_temp)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // HIGH priority for better survival
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }
    
    /**
     * NEW: Start aggressive health monitoring to detect and recover from failures
     */
    private void startHealthMonitoring() {
        healthCheckRunnable = new Runnable() {
            @Override
            public void run() {
                performHealthCheck();
                // Schedule next health check
                healthHandler.postDelayed(this, HEALTH_CHECK_INTERVAL);
            }
        };
        
        healthHandler.post(healthCheckRunnable);
        Log.d(TAG, "✅ Health monitoring started");
    }
    
    /**
     * NEW: Perform comprehensive health check
     */
    private void performHealthCheck() {
        Log.d(TAG, "🔍 Health check - Service alive, Scanning: " + isScanning + ", Paused: " + isPaused);
        
        // Check if BLE manager is still alive
        if (mBleManager == null) {
            Log.w(TAG, "⚠️ BLE manager is null, reinitializing");
            initBleManager();
        }
        
        // Check wake locks
        if (wakeLock != null && !wakeLock.isHeld()) {
            Log.w(TAG, "⚠️ Main wake lock released, reacquiring");
            try {
                wakeLock.acquire();
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to reacquire wake lock: " + e.getMessage());
            }
        }
        
        if (cpuWakeLock != null && !cpuWakeLock.isHeld()) {
            Log.w(TAG, "⚠️ CPU wake lock released, reacquiring");
            try {
                cpuWakeLock.acquire();
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to reacquire CPU wake lock: " + e.getMessage());
            }
        }
        
        // Check if scanning stopped unexpectedly
        if (!isScanning && !isPaused) {
            Log.w(TAG, "⚠️ Scanning stopped unexpectedly, restarting");
            startScanningCycle();
        }
        
        // Update notification to show we're alive
        updateServiceNotification();
    }
    
    /**
     * NEW: Update service notification with current status
     */
    private void updateServiceNotification() {
        try {
            String status = isPaused ? "Paused" : (isScanning ? "Scanning..." : "Waiting...");
            int devicesCount = deviceManager.getDiscoveredDevices().size();
            
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("🔵 Beacon Scanner Active")
                    .setContentText(status + " • " + devicesCount + " devices found")
                    .setSmallIcon(R.drawable.ic_temp)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .build();
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to update notification: " + e.getMessage());
        }
    }

    private void initBleManager() {
        try {
            mBleManager = MST03SensorBleManager.getInstance();
            if (mBleManager == null) {
                Log.e(TAG, "Failed to get BLE manager instance");
                return;
            }
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
                        processScannedDevices(list);
                        deviceManager.updateDevices(list);
                    }
                }

                @Override
                public void onStopScan(List<MST03Entity> list) {
                    Log.d(TAG, "Background scan stopped");
                    isScanning = false;
                    
                    // OPTIMIZED: Adaptive wait time based on temperature data availability
                    int adaptiveWaitTime = calculateAdaptiveWaitTime();
                    
                    if (!isPaused) {
                        scanHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!isScanning && !isPaused) {
                                    startScan();
                                }
                            }
                        }, adaptiveWaitTime);
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting background scan: " + e.getMessage());
            isScanning = false;
            
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
    
    /**
     * NEW: Calculate adaptive wait time based on temperature data availability
     */
    private int calculateAdaptiveWaitTime() {
        // If we have devices missing temperature data, scan more frequently
        if (!devicesNeedingTemperature.isEmpty()) {
            Log.d(TAG, "Devices missing temperature data: " + devicesNeedingTemperature.size() + " - using short wait");
            return 1000; // 1 second wait for quick temperature discovery
        } else {
            return WAIT_DURATION; // Normal 2 second wait
        }
    }
    
    /**
     * NEW: Process scanned devices and track temperature data availability
     */
    private void processScannedDevices(List<MST03Entity> devices) {
        for (MST03Entity device : devices) {
            String macAddress = device.getMacAddress();
            
            // Check if device has temperature data
            com.minew.ble.mst03.frames.CombinationFrame comboFrame = 
                (com.minew.ble.mst03.frames.CombinationFrame) device.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
            
            if (comboFrame != null) {
                float temperature = comboFrame.getTemperature();
                if (temperature != 0.0f && !Float.isNaN(temperature)) {
                    // Device has valid temperature data
                    devicesNeedingTemperature.remove(macAddress);
                    Log.d(TAG, "Temperature data found for " + macAddress + ": " + temperature + "°C");
                } else {
                    // Device discovered but no valid temperature yet
                    devicesNeedingTemperature.add(macAddress);
                    Log.d(TAG, "Device " + macAddress + " still needs temperature data");
                }
            } else {
                // No CombinationFrame yet
                devicesNeedingTemperature.add(macAddress);
                Log.d(TAG, "Device " + macAddress + " has no CombinationFrame yet");
            }
            
            // Log scan data with validation
            logDeviceScanData(device);
        }
        
        Log.d(TAG, "Devices needing temperature: " + devicesNeedingTemperature.size());
    }
    
    /**
     * NEW: Enhanced device scan data logging with better validation
     */
    private void logDeviceScanData(MST03Entity device) {
        float temperature = 0.0f;
        int battery = 0;
        String firmwareVersion = "Unknown";
        boolean hasValidTemperature = false;
        
        // Get temperature from CombinationFrame
        if (device.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME) != null) {
            com.minew.ble.mst03.frames.CombinationFrame comboFrame = 
                (com.minew.ble.mst03.frames.CombinationFrame) device.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
            temperature = comboFrame.getTemperature();
            
            // Enhanced validation: Check for reasonable temperature range
            if (temperature != 0.0f && !Float.isNaN(temperature) && temperature > -50 && temperature < 100) {
                hasValidTemperature = true;
                Log.d(TAG, "✅ Valid temperature: " + temperature + "°C for " + device.getMacAddress());
            } else {
                Log.d(TAG, "❌ Invalid temperature: " + temperature + "°C for " + device.getMacAddress());
            }
        }
        
        // Get device info
        if (device.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME) != null) {
            com.minew.ble.mst03.frames.DeviceStaticInfoFrame staticFrame = 
                (com.minew.ble.mst03.frames.DeviceStaticInfoFrame) device.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME);
            battery = staticFrame.getBattery();
            firmwareVersion = staticFrame.getFirmwareVersion();
        }
        
        // Only send HTTP log if we have valid temperature data
        if (hasValidTemperature && httpLogger != null) {
            httpLogger.logScanData(device.getMacAddress(), temperature, battery, firmwareVersion, device.getRssi());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ENHANCED: Create notification channel with high importance
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Beacon Scanner Service",
                    NotificationManager.IMPORTANCE_HIGH // HIGH importance for better survival
            );
            channel.setDescription("Background beacon scanning for temperature monitoring");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null); // No sound for continuous service
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.d(TAG, "✅ Notification channel created");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🛑 Background scan service destroyed");
        
        // Stop health monitoring
        if (healthHandler != null && healthCheckRunnable != null) {
            healthHandler.removeCallbacks(healthCheckRunnable);
        }
        
        // Release wake locks
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "✅ Main wake lock released");
            }
            if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
                cpuWakeLock.release();
                Log.d(TAG, "✅ CPU wake lock released");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error releasing wake locks: " + e.getMessage());
        }
        
        // Stop scanning
        if (mBleManager != null && isScanning) {
            try {
                mBleManager.stopScan(this);
                isScanning = false;
                Log.d(TAG, "✅ Scanning stopped");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error stopping scan: " + e.getMessage());
            }
        }
        
        // Remove handlers
        scanHandler.removeCallbacksAndMessages(null);
        
        // Shutdown HTTP logger
        if (httpLogger != null) {
            httpLogger.shutdown();
        }
    }
    
    /**
     * ENHANCED: More aggressive task removal handling
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.w(TAG, "⚠️ App removed from recents");
        
        // Don't stop the service automatically - let it continue running
        // The service will only stop when explicitly stopped or system kills it
        Log.d(TAG, "🔄 Service continues running after task removal");
    }
}