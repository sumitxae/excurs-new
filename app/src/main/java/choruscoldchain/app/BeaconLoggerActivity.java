package choruscoldchain.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class BeaconLoggerActivity extends AppCompatActivity {
    private static final String TAG = "BeaconLogger";
    
    private TextView tvLoggerData;
    private TextView tvStatus;
    private TextView tvDeviceCount;
    private View statusIndicator;
    private HttpLogger httpLogger;
    
    private StringBuilder logData = new StringBuilder();
    private ConcurrentHashMap<String, BeaconData> deviceDataMap = new ConcurrentHashMap<>();
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    
    // Static instance for global access
    private static BeaconLoggerActivity instance;
    
    public static BeaconLoggerActivity getInstance() {
        return instance;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beacon_logger);
        
        instance = this;
        
        initViews();
        setupClickListeners();
        
        // Make text view scrollable
        tvLoggerData.setMovementMethod(new ScrollingMovementMethod());
        
        // Add initial message after layout is ready
        tvLoggerData.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
        });
        httpLogger = new HttpLogger();
    }
    
    private void initViews() {
        tvLoggerData = findViewById(R.id.tv_logger_data);
        tvStatus = findViewById(R.id.tv_status);
        tvDeviceCount = findViewById(R.id.tv_device_count);
        statusIndicator = findViewById(R.id.status_indicator);
    }
    
    private void setupClickListeners() {
        findViewById(R.id.btn_close_logger).setOnClickListener(v -> finish());
        findViewById(R.id.btn_clear_log).setOnClickListener(v -> clearLog());
        findViewById(R.id.btn_copy_log).setOnClickListener(v -> copyLogToClipboard());
        findViewById(R.id.overlay).setOnClickListener(v -> finish());
    }
    
    /**
     * Log tempEventTimestamp directly to backend
     */
    public void logTempEventTimestamp(String macAddress, long tempEventTimestamp) {
        Log.d(TAG, "logTempEventTimestamp called with macAddress: " + macAddress + ", tempEventTimestamp: " + tempEventTimestamp);
        
        // Log the raw value without any conversion
        String message = String.format("tempEventTimestamp RAW: %d (Device: %s)", tempEventTimestamp, macAddress);
        Log.d(TAG, "Adding log entry: " + message);
        addLogEntry(message);
        // Also send directly to backend for immediate logging
        Log.d(TAG, "Sending to backend: " + message);
        httpLogger.sendSimpleMessage(message);
        
        // Also log with current system time for comparison
        long currentSystemTime = System.currentTimeMillis();
        String comparisonMessage = String.format("tempEventTimestamp COMPARISON - Raw: %d, SystemTime: %d, Diff: %d (Device: %s)", 
            tempEventTimestamp, currentSystemTime, Math.abs(currentSystemTime - tempEventTimestamp), macAddress);
        Log.d(TAG, "Adding comparison log entry: " + comparisonMessage);
        addLogEntry(comparisonMessage);
        Log.d(TAG, "Sending comparison to backend: " + comparisonMessage);
        httpLogger.sendSimpleMessage(comparisonMessage);
        
        Log.d(TAG, "logTempEventTimestamp completed");
    }
    
    /**
     * Add a log entry with timestamp
     */
    public void addLogEntry(String message) {
        String timestamp = timeFormat.format(new Date());
        String logEntry = "[" + timestamp + "] " + message + "\n";
        
        // Send to backend if it's a tempEventTimestamp log or other important messages
        if (message.contains("tempEventTimestamp") || message.contains("DIRECT EXCURSION DETECTED") || 
            message.contains("LIGHT EVENT") || message.contains("EXCURSION")) {
            httpLogger.sendSimpleMessage(message);
        }
        
        runOnUiThread(() -> {
            logData.append(logEntry);
            tvLoggerData.setText(logData.toString());
            
            // Auto-scroll to bottom using post to ensure layout is complete
            tvLoggerData.post(() -> {
                try {
                    if (tvLoggerData.getLayout() != null) {
                        int scrollAmount = tvLoggerData.getLayout().getLineTop(tvLoggerData.getLineCount()) - tvLoggerData.getHeight();
                        if (scrollAmount > 0) {
                            tvLoggerData.scrollTo(0, scrollAmount);
                        }
                    }
                } catch (Exception e) {
                    // Ignore scroll errors
                    Log.w(TAG, "Error auto-scrolling: " + e.getMessage());
                }
            });
        });
    }
    
    /**
     * Log beacon data from device
     */
    public void logBeaconData(String macAddress, float temperature, float humidity, int battery, String firmwareVersion, int rssi) {
        String timestamp = timeFormat.format(new Date());
        
        // Create or update device data
        BeaconData data = new BeaconData(macAddress, temperature, humidity, battery, firmwareVersion, rssi, timestamp);
        deviceDataMap.put(macAddress, data);
        
        // Format the log entry
        String logEntry = String.format(Locale.getDefault(),
            "DEVICE: %s\n" +
            "  Temperature: %.2f°C\n" +
            "  Humidity: %.2f%%\n" +
            "  Battery: %d%%\n" +
            "  Firmware: %s\n" +
            "  RSSI: %d dBm\n" +
            "  Time: %s\n" +
            "----------------------------------------",
            macAddress, temperature, humidity, battery, firmwareVersion, rssi, timestamp
        );
        
        addLogEntry(logEntry);
        updateDeviceCount();
        updateStatus("Receiving data from " + deviceDataMap.size() + " device(s)");
    }
    
    /**
     * Log raw beacon frame data
     */
    public void logRawBeaconData(String macAddress, String frameType, String rawData) {
        String timestamp = timeFormat.format(new Date());
        String logEntry = String.format(
            "RAW DATA - %s (%s):\n  %s\n----------------------------------------",
            macAddress, frameType, rawData
        );
        httpLogger.sendSimpleMessage(logEntry);
        addLogEntry(logEntry);
    }
    
    /**
     * Log connection events
     */
    public void logConnectionEvent(String macAddress, String event) {
        String timestamp = timeFormat.format(new Date());
        String logEntry = String.format("CONNECTION EVENT - %s: %s", macAddress, event);
        addLogEntry(logEntry);
    }
    
    /**
     * Log scan events
     */
    public void logScanEvent(String event) {
        String timestamp = timeFormat.format(new Date());
        String logEntry = "SCAN EVENT: " + event;
        addLogEntry(logEntry);
    }
    
    private void updateDeviceCount() {
        runOnUiThread(() -> {
            tvDeviceCount.setText("Devices: " + deviceDataMap.size());
        });
    }
    
    private void updateStatus(String status) {
        runOnUiThread(() -> {
            tvStatus.setText(status);
        });
    }
    
    private void clearLog() {
        logData.setLength(0);
        deviceDataMap.clear();
        tvLoggerData.setText("Log cleared.\n\nWaiting for beacon data...");
        updateDeviceCount();
        updateStatus("Waiting for beacon data...");
    }
    
    private void copyLogToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Beacon Logger Data", logData.toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Log data copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
    }
    
    /**
     * Data class to store beacon information
     */
    public static class BeaconData {
        public final String macAddress;
        public final float temperature;
        public final float humidity;
        public final int battery;
        public final String firmwareVersion;
        public final int rssi;
        public final String timestamp;
        
        public BeaconData(String macAddress, float temperature, float humidity, int battery, String firmwareVersion, int rssi, String timestamp) {
            this.macAddress = macAddress;
            this.temperature = temperature;
            this.humidity = humidity;
            this.battery = battery;
            this.firmwareVersion = firmwareVersion;
            this.rssi = rssi;
            this.timestamp = timestamp;
        }
    }
}
