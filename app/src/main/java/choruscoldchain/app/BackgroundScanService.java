package choruscoldchain.app;

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

    private static final int SCAN_DURATION = 6 * 1000;
    private static final int INITIAL_SCAN_DURATION = 20 * 1000;
    private static final int WAIT_DURATION = 6 * 1000;
    private boolean isInitialScan = true;

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

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Beacon Scanner Active")
                    .setContentText("Scanning for beacons in background")
                    .setSmallIcon(R.drawable.ic_temp)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Foreground service started successfully");

            startScanningCycle();

            startHealthCheck();

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

        if (!isScanning && !isPaused) {
            Log.d(TAG, "Service appears to have been killed, restarting scan cycle");
            startScanningCycle();
        }
    }

    private void startHealthCheck() {

        scanHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Health check - Service running: " + (!isPaused) + ", Scanning: " + isScanning);

                if (!isScanning && !isPaused) {
                    Log.d(TAG, "Health check detected service not scanning, restarting");
                    startScanningCycle();
                }

                startHealthCheck();
            }
        }, 30000);
    }

    private void startScan() {
        if (isScanning || isPaused) {
            Log.d(TAG, "Scan already in progress or paused, skipping");
            return;
        }

        int scanDuration = isInitialScan ? INITIAL_SCAN_DURATION : SCAN_DURATION;
        Log.d(TAG, "Starting background BLE scan with duration: " + (scanDuration / 1000) + "s (initial: "
                + isInitialScan + ")");

        try {
            isScanning = true;
            Log.d(TAG, "Starting background BLE scan...");

            mBleManager.startScan(this, scanDuration, new OnScanDevicesResultListener<MST03Entity>() {
                @Override
                public void onScanResult(List<MST03Entity> list) {
                    Log.d(TAG, "Background scan result: " + list.size() + " devices found");

                    if (list.size() > 0) {

                        for (MST03Entity mst03Entity : list) {

                            com.minew.ble.mst03.frames.DeviceStaticInfoFrame deviceInfo = (com.minew.ble.mst03.frames.DeviceStaticInfoFrame) mst03Entity
                                    .getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME);

                            com.minew.ble.mst03.frames.CombinationFrame comboFrame = (com.minew.ble.mst03.frames.CombinationFrame) mst03Entity
                                    .getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
                        }

                        deviceManager.updateDevices(list);

                        for (MST03Entity device : list) {
                            float temperature = 0.0f;
                            int battery = 0;
                            String firmwareVersion = "Unknown";
                            boolean hasValidTemperature = false;

                            if (device.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME) != null) {
                                com.minew.ble.mst03.frames.CombinationFrame comboFrame = (com.minew.ble.mst03.frames.CombinationFrame) device
                                        .getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
                                temperature = comboFrame.getTemperature();

                                if (!Float.isNaN(temperature)) hasValidTemperature = true;
                            }

                            if (device
                                    .getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME) != null) {
                                com.minew.ble.mst03.frames.DeviceStaticInfoFrame staticFrame = (com.minew.ble.mst03.frames.DeviceStaticInfoFrame) device
                                        .getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME);
                                battery = staticFrame.getBattery();
                                firmwareVersion = staticFrame.getFirmwareVersion();
                            }

                            if (hasValidTemperature)
                                httpLogger.logScanData(device.getMacAddress(), temperature, battery, firmwareVersion,
                                        device.getRssi());
                        }
                    }
                }

                @Override
                public void onStopScan(List<MST03Entity> list) {
                    Log.d(TAG, "Background scan stopped");
                    isScanning = false;

                    if (isInitialScan) {
                        isInitialScan = false;
                        Log.d(TAG, "Initial scan completed, switching to normal 6s scan cycle");
                    }

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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Beacon Scanner",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Background beacon scanning service");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
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

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "Wake lock released");
        }

        if (mBleManager != null && isScanning) {
            try {
                mBleManager.stopScan(this);
                isScanning = false;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping scan: " + e.getMessage());
            }
        }

        scanHandler.removeCallbacksAndMessages(null);

        if (httpLogger != null) {
            httpLogger.shutdown();
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "App removed from recents - stopping background service");

        stopSelf();
    }
}