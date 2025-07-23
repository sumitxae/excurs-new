package choruscoldchain.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.minew.ble.mst03.bean.MST03Entity;
import com.minew.ble.mst03.bean.HistoryHtData;
import com.minew.ble.mst03.bean.HtData;
import choruscoldchain.app.ExcursionData;
import com.minew.ble.mst03.frames.DeviceStaticInfoFrame;
import com.minew.ble.mst03.frames.CombinationFrame;
import com.minew.ble.v3.enums.FrameType;
import com.minew.ble.v3.interfaces.OnQueryResultListener;

import java.util.ArrayList;
import java.util.List;
import android.widget.ImageView;

public class DeviceDetailsActivity extends AppCompatActivity {

    private static final String EXTRA_DEVICE = "extra_device";
    
    private MST03Entity device;
    private DeviceStaticInfoFrame deviceStaticInfo;
    private CombinationFrame combinationFrame;
    
    // UI Components
    private TextView tvDeviceMac;
    private TextView tvBatteryLevel;
    private TextView tvFirmwareVersion;
    private TextView tvExcursionEventAt;
    private TextView tvCurrentTemperature;
    private TextView tvCurrentStatus;
    // private TextView tvAvgTemperature;
    // private TextView tvMaxTemperature;
    // private TextView tvMinTemperature;
    // private TextView tvExcursionCount;
    private LineChart lineChart;
    private TextView tvGraphPlaceholder;
    private View loadingOverlay;
    // private TextView tvLoadingText;
    private View firmwareContainer;
    private android.os.Handler statusRefreshHandler = new android.os.Handler();
    private static final int STATUS_REFRESH_INTERVAL = 1000; // 1 second

    public static Intent newIntent(Context context, MST03Entity device, int batteryLevel, String firmwareVersion, float currentTemperature) {
        Intent intent = new Intent(context, DeviceDetailsActivity.class);
        intent.putExtra(EXTRA_DEVICE, device);
        intent.putExtra("battery_level", batteryLevel);
        intent.putExtra("firmware_version", firmwareVersion);
        intent.putExtra("current_temperature", currentTemperature);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_details);
        // Ensure status bar icons are dark for visibility on light background
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        
        // Get device from intent
        device = getIntent().getParcelableExtra(EXTRA_DEVICE);
        if (device == null) {
            Toast.makeText(this, "Device information not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Validate device has basic information
        if (device.getMacAddress() == null || device.getMacAddress().isEmpty()) {
            Toast.makeText(this, "Invalid device information", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Get device data from intent extras
        int batteryLevel = getIntent().getIntExtra("battery_level", -1);
        String firmwareVersion = getIntent().getStringExtra("firmware_version");
        float currentTemperature = getIntent().getFloatExtra("current_temperature", Float.NaN);
        
        initViews();
        setupBackButton();
        setupGraph();
        setupRoleBasedUI();
        
        // Start status refresh
        startStatusRefresh();
        
        // Load data based on what's available
        if (currentTemperature != Float.NaN) {
            loadBroadcastDataWithExtras(batteryLevel, firmwareVersion, currentTemperature);
        } else {
            loadBroadcastData();
        }
        
        // Show loader only for components that need connection data
        showLoaderForConnectionData();
        
        // Load connection data asynchronously
        loadConnectionDataAsync();
    }

    private void initViews() {
        tvDeviceMac = findViewById(R.id.tv_device_mac);
        tvBatteryLevel = findViewById(R.id.tv_battery_level);
        tvFirmwareVersion = findViewById(R.id.tv_firmware_version);
        tvExcursionEventAt = findViewById(R.id.tv_excursion_event_at);
        tvCurrentTemperature = findViewById(R.id.tv_current_temperature);
        // tvCurrentStatus = findViewById(R.id.tv_current_status);
        // tvAvgTemperature = findViewById(R.id.tv_avg_temperature);
        // tvMaxTemperature = findViewById(R.id.tv_max_temperature);
        // tvMinTemperature = findViewById(R.id.tv_min_temperature);
        // tvExcursionCount = findViewById(R.id.tv_excursion_count);
        lineChart = findViewById(R.id.graph_container);
        tvGraphPlaceholder = findViewById(R.id.tv_graph_placeholder);
        loadingOverlay = findViewById(R.id.loading_overlay);
        // tvLoadingText = findViewById(R.id.tv_loading_text);
        firmwareContainer = findViewById(R.id.firmware_container);
    }

    private void setupBackButton() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
    
    private void setupRoleBasedUI() {
        // Get current user's role
        AuthManager authManager = AuthManager.getInstance(this);
        String userRole = authManager.getUserRole();
        
        Log.d("DeviceDetails", "Current user role: " + (userRole != null ? userRole : "null"));
        
        // Check if user is admin
        boolean isAdmin = "admin".equalsIgnoreCase(userRole);
        
        if (isAdmin) {
            // Show firmware container and upgrade button for admin
            firmwareContainer.setVisibility(View.VISIBLE);    
            Log.d("DeviceDetails", "User is admin - showing firmware controls");
        } else {
            // Hide firmware container for regular users
            firmwareContainer.setVisibility(View.GONE);
            Log.d("DeviceDetails", "User is not admin - hiding firmware controls. Role: " + userRole);
        }
    }
    
    private void showLoader() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
    }
    
    private void hideLoader() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }


    private void loadBroadcastDataWithExtras(int batteryLevel, String firmwareVersion, float currentTemperature) {
        Log.d("DeviceDetails", "=== LOADING BROADCAST DATA WITH EXTRAS ===");
        Log.d("DeviceDetails", "Device: " + (device != null ? device.getMacAddress() : "NULL"));
        Log.d("DeviceDetails", "Battery Level: " + batteryLevel + "%");
        Log.d("DeviceDetails", "Firmware Version: " + firmwareVersion);
        Log.d("DeviceDetails", "Current Temperature: " + currentTemperature + "°C");
        
        // Set MAC address (available from broadcast)
        if (device != null && device.getMacAddress() != null) {
            tvDeviceMac.setText(device.getMacAddress());
            Log.d("DeviceDetails", "MAC address set: " + device.getMacAddress());
        } else {
            tvDeviceMac.setText("Unknown");
            Log.w("DeviceDetails", "No MAC address available");
        }
        
        // Set battery level from extras
        if (batteryLevel >= 0) {
            tvBatteryLevel.setText(batteryLevel + "%");
            Log.d("DeviceDetails", "✅ Battery level set from extras: " + batteryLevel + "%");
            
            if (batteryLevel < 20) {
                tvBatteryLevel.setTextColor(getResources().getColor(R.color.error));
            } else if (batteryLevel < 50) {
                tvBatteryLevel.setTextColor(getResources().getColor(R.color.warning));
            } else {
                tvBatteryLevel.setTextColor(getResources().getColor(R.color.success));
            }
        } else {
            tvBatteryLevel.setText("--");
            Log.w("DeviceDetails", "❌ No battery level data available");
        }
        
        // Set firmware version from extras (only visible to admin users)
        if (firmwareVersion != null && !firmwareVersion.isEmpty()) {
            tvFirmwareVersion.setText(firmwareVersion);
            tvFirmwareVersion.setTextColor(getResources().getColor(R.color.text_primary));
            Log.d("DeviceDetails", "✅ Firmware version set from extras: " + firmwareVersion);
        } else {
            tvFirmwareVersion.setText("--");
            Log.w("DeviceDetails", "❌ No firmware version data available");
        }
        
        // Set current temperature from extras
        if (!Float.isNaN(currentTemperature) && currentTemperature != 0.0f) {
            tvCurrentTemperature.setText(String.format("%.1f°C", currentTemperature));
            Log.d("DeviceDetails", "✅ Current temperature set from extras: " + currentTemperature + "°C");
            
            // Color code temperature using updated thresholds (2-8°C)
            if (currentTemperature > 8.0f || currentTemperature < 2.0f) {
                tvCurrentTemperature.setTextColor(getResources().getColor(R.color.error));
                Log.d("DeviceDetails", "Temperature alert: " + currentTemperature + "°C (outside 2-8°C range)");
            } else {
                tvCurrentTemperature.setTextColor(getResources().getColor(R.color.primary));
                Log.d("DeviceDetails", "Temperature normal: " + currentTemperature + "°C");
            }
        } else {
            tvCurrentTemperature.setText("--°C");
            tvCurrentTemperature.setTextColor(getResources().getColor(R.color.text_secondary));
            Log.w("DeviceDetails", "❌ No temperature data available");
        }
        
        Log.d("DeviceDetails", "=== BROADCAST DATA LOADING COMPLETE ===");
    }

    private void loadBroadcastData() {
        Log.d("DeviceDetails", "=== LOADING BROADCAST DATA ===");
        Log.d("DeviceDetails", "Device: " + (device != null ? device.getMacAddress() : "NULL"));
        
        // Set MAC address (available from broadcast)
        if (device != null && device.getMacAddress() != null) {
            tvDeviceMac.setText(device.getMacAddress());
            Log.d("DeviceDetails", "MAC address set: " + device.getMacAddress());
        } else {
            tvDeviceMac.setText("Unknown");
            Log.w("DeviceDetails", "No MAC address available");
        }
        
        // Get device static info from broadcast
        try {
            Log.d("DeviceDetails", "Checking for DeviceStaticInfoFrame...");
            
            // Try the same frame type detection that works in ScanDevicesListAdapter
            DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) device.getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME);
            
            if (deviceInfo != null) {
                deviceStaticInfo = deviceInfo;
                
                // Set battery level
                int batteryLevel = deviceStaticInfo.getBattery();
                tvBatteryLevel.setText(batteryLevel + "%");
                Log.d("DeviceDetails", "✅ Battery level set: " + batteryLevel + "%");
                
                if (batteryLevel < 20) {
                    tvBatteryLevel.setTextColor(getResources().getColor(R.color.error));
                } else if (batteryLevel < 50) {
                    tvBatteryLevel.setTextColor(getResources().getColor(R.color.warning));
                } else {
                    tvBatteryLevel.setTextColor(getResources().getColor(R.color.success));
                }
                
                // Set firmware version (only visible to admin users)
                String firmwareVersion = deviceStaticInfo.getFirmwareVersion();
                tvFirmwareVersion.setText(firmwareVersion);
                Log.d("DeviceDetails", "✅ Firmware version set: " + firmwareVersion);
                
                Log.d("DeviceDetails", "✅ Device static info loaded successfully - Battery: " + batteryLevel + "%, Firmware: " + firmwareVersion);
            } else {
                tvBatteryLevel.setText("--");
                tvFirmwareVersion.setText("--");
                Log.w("DeviceDetails", "❌ No device static info frame available");
                
                // Try to log what frames are available
                Log.d("DeviceDetails", "Checking all available frames...");
                try {
                    FrameType[] allFrameTypes = FrameType.values();
                    for (FrameType frameType : allFrameTypes) {
                        Object frame = device.getMinewFrame(frameType);
                        if (frame != null) {
                            Log.d("DeviceDetails", "Available frame: " + frameType + " - " + frame.getClass().getSimpleName());
                        }
                    }
                } catch (Exception e) {
                    Log.e("DeviceDetails", "Error checking frames: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e("DeviceDetails", "Error loading device static info: " + e.getMessage());
            tvBatteryLevel.setText("--");
            tvFirmwareVersion.setText("--");
        }
        
        // Get combination frame for current temperature from broadcast
        try {
            Log.d("DeviceDetails", "Checking for CombinationFrame...");
            
            CombinationFrame comboFrame = (CombinationFrame) device.getMinewFrame(FrameType.COMBINATION_FRAME);
            
            if (comboFrame != null) {
                combinationFrame = comboFrame;
                float temperature = combinationFrame.getTemperature();
                
                Log.d("DeviceDetails", "Raw temperature from frame: " + temperature);
                
                if (!Float.isNaN(temperature) && temperature != 0.0f) {
                    tvCurrentTemperature.setText(String.format("%.1f°C", temperature));
                    Log.d("DeviceDetails", "✅ Current temperature set: " + temperature + "°C");
                    
                    // Color code temperature using updated thresholds (2-8°C)
                    if (temperature > 8.0f || temperature < 2.0f) {
                        tvCurrentTemperature.setTextColor(getResources().getColor(R.color.error));
                        Log.d("DeviceDetails", "Temperature alert: " + temperature + "°C (outside 2-8°C range)");
                    } else {
                        tvCurrentTemperature.setTextColor(getResources().getColor(R.color.primary));
                        Log.d("DeviceDetails", "Temperature normal: " + temperature + "°C");
                    }
                } else {
                    tvCurrentTemperature.setText("--°C");
                    tvCurrentTemperature.setTextColor(getResources().getColor(R.color.text_secondary));
                    Log.w("DeviceDetails", "❌ Invalid temperature data: " + temperature);
                }
            } else {
                tvCurrentTemperature.setText("--°C");
                tvCurrentTemperature.setTextColor(getResources().getColor(R.color.text_secondary));
                Log.w("DeviceDetails", "❌ No combination frame available");
            }
        } catch (Exception e) {
            Log.e("DeviceDetails", "Error loading temperature data: " + e.getMessage());
            tvCurrentTemperature.setText("--°C");
            tvCurrentTemperature.setTextColor(getResources().getColor(R.color.text_secondary));
        }
        
        Log.d("DeviceDetails", "=== BROADCAST DATA LOADING COMPLETE ===");
    }
    
    private void showLoaderForConnectionData() {
        // Show loader only for components that need connection data
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        
        // Update loading text
        // if (tvLoadingText != null) {
        //     tvLoadingText.setText("Fetching historical data...");
        // }
        
        // Show placeholder text for statistics
        // tvAvgTemperature.setText("Loading...");
        // tvMaxTemperature.setText("Loading...");
        // tvMinTemperature.setText("Loading...");
        // tvExcursionCount.setText("Loading...");
    }
    
    private void loadConnectionDataAsync() {
        // Check for processed data from ScanDevicesListActivity
        new Thread(new Runnable() {
            @Override
            public void run() {
                // First, check if data processing has already started
                if (!ScanDevicesListActivity.isDataProcessingComplete()) {
                    Log.d("DeviceDetails", "Data processing not complete, checking if it was started...");
                    
                    // If no data processing is happening, we might need to trigger it
                    // This can happen if the connection was established but data fetching failed
                    if (ScanDevicesListActivity.getProcessedHistoricalData().isEmpty()) {
                        Log.d("DeviceDetails", "No processed data available, data fetching may have failed");
                    }
                }
                
                // Wait for data processing to complete with a timeout
                int timeoutSeconds = 180; // 60 second timeout
                int checkIntervalMs = 500; // Check every 500ms
                int maxChecks = (timeoutSeconds * 1000) / checkIntervalMs;
                int checks = 0;
                
                while (!ScanDevicesListActivity.isDataProcessingComplete() && checks < maxChecks) {
                    try {
                        Thread.sleep(checkIntervalMs);
                        checks++;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
                
                // Run UI updates on main thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ScanDevicesListActivity.isDataProcessingComplete()) {
                            loadProcessedData();
                        } else {
                            // Show timeout message
                            Log.d("DeviceDetails", "Data loading timed out after " + timeoutSeconds + " seconds");
                            showTimeoutMessage();
                        }
                        hideLoader();
                    }
                });
            }
        }).start();
    }
    
    private void showTimeoutMessage() {
        // Show timeout message when data loading takes too long
        // tvAvgTemperature.setText("--");
        // tvMinTemperature.setText("--");
        // tvMaxTemperature.setText("--");
        // tvExcursionCount.setText("--");
        
        // Show timeout message in graph area
        lineChart.setVisibility(View.GONE);
        tvGraphPlaceholder.setVisibility(View.VISIBLE);
        tvGraphPlaceholder.setText("Data loading timed out. Please try reconnecting to the device.");
        
        Log.d("DeviceDetails", "Data loading timed out - showing timeout message");
    }
    
    private void showNoDataMessage() {
        // Show error message when no data is available
        // tvAvgTemperature.setText("--");
        // tvMinTemperature.setText("--");
        // tvMaxTemperature.setText("--");
        // tvExcursionCount.setText("--");
        
        // Show error message in graph area
        lineChart.setVisibility(View.GONE);
        tvGraphPlaceholder.setVisibility(View.VISIBLE);
        tvGraphPlaceholder.setText("No historical data available from device");
        
        Log.d("DeviceDetails", "No historical data available from device");
    }
    
    private void loadProcessedData() {
        List<HtData> historicalData = ScanDevicesListActivity.getProcessedHistoricalData();
        List<ExcursionData> excursionData = ScanDevicesListActivity.getProcessedExcursionData();
        
        if (historicalData != null && !historicalData.isEmpty()) {
            // Clear previous data
            timeLabels.clear();
            originalTimestamps.clear();
            temperatureEntries.clear();
            alertUpperEntries.clear();
            alertLowerEntries.clear();
            
            // Find the 3 key points: start, excursion start, and end
            int startIndex = 0;
            int excursionStartIndex = -1;
            int endIndex = historicalData.size() - 1;
            
            // Find the first excursion point
            for (int i = 0; i < historicalData.size(); i++) {
                HtData htData = historicalData.get(i);
                float temperature = htData.getTemperature();
                if (temperature < 2.0f || temperature > 8.0f) {
                    excursionStartIndex = i;
                    break;
                }
            }
            
            // Create only 3 key points
            List<Integer> keyIndices = new ArrayList<>();
            keyIndices.add(startIndex); // Starting point
            
            if (excursionStartIndex != -1) {
                keyIndices.add(excursionStartIndex); // Excursion start point
            }
            
            keyIndices.add(endIndex); // End point
            
            // Create data for only the key points
            for (int i = 0; i < keyIndices.size(); i++) {
                int dataIndex = keyIndices.get(i);
                HtData htData = historicalData.get(dataIndex);
                
                // Handle timestamp conversion properly
                long timestamp = htData.getTimestamps();
                if (timestamp < 10000000000L) {
                    timestamp = timestamp * 1000;
                }
                
                java.util.Date date = new java.util.Date(timestamp);
                java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                String timeLabel = timeFormat.format(date);
                
                timeLabels.add(timeLabel);
                temperatureEntries.add(new Entry(i, htData.getTemperature()));
                alertUpperEntries.add(new Entry(i, 8.0f));
                alertLowerEntries.add(new Entry(i, 2.0f));
                originalTimestamps.add(timestamp);
            }
            
            // Update excursion event timestamp (first excursion event timestamp)
            if (excursionData != null && !excursionData.isEmpty()) {
                // Find the first excursion timestamp from historical data
                long firstExcursionTimestamp = -1;
                
                // Search through historical data to find the first excursion
                for (HtData htData : historicalData) {
                    float temperature = htData.getTemperature();
                    if (temperature < 2.0f || temperature > 8.0f) {
                        firstExcursionTimestamp = htData.getTimestamps();
                        break;
                    }
                }
                
                // If we found an excursion in historical data, use it; otherwise use the first excursion from excursionData
                if (firstExcursionTimestamp == -1 && !excursionData.isEmpty()) {
                    firstExcursionTimestamp = excursionData.get(0).getTimestamp();
                }
                
                if (firstExcursionTimestamp != -1) {
                    // Handle timestamp conversion properly
                    if (firstExcursionTimestamp < 10000000000L) {
                        // Timestamp is in seconds, convert to milliseconds
                        firstExcursionTimestamp = firstExcursionTimestamp * 1000;
                    }
                    
                    java.util.Date firstExcursionDate = new java.util.Date(firstExcursionTimestamp);
                    java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm", java.util.Locale.getDefault());
                    String formattedDate = dateFormat.format(firstExcursionDate);
                    
                    tvExcursionEventAt.setText(formattedDate);
                    Log.d("DeviceDetails", "Excursion event at: " + formattedDate + " (timestamp: " + firstExcursionTimestamp + ")");
                } else {
                    tvExcursionEventAt.setText("No excursion data");
                    Log.d("DeviceDetails", "No excursion timestamp found");
                }
            } else {
                tvExcursionEventAt.setText("No data");
                Log.d("DeviceDetails", "No excursion or historical data available");
            }
            
            updateStatisticsFromProcessedData(historicalData, excursionData);
            updateGraph();
            Log.d("DeviceDetails", "Loaded " + keyIndices.size() + " key points for graph from " + historicalData.size() + " total records");
        } else {
            // Show no data message instead of fallback
            showNoDataMessage();
            Log.d("DeviceDetails", "No real historical data available from device");
        }
    }
    
    private void updateStatisticsFromProcessedData(List<HtData> historicalData, List<ExcursionData> excursionData) {
        if (historicalData.isEmpty()) return;
        
        float sum = 0;
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        
        for (HtData htData : historicalData) {
            float temp = htData.getTemperature();
            sum += temp;
            min = Math.min(min, temp);
            max = Math.max(max, temp);
        }
        
        float avg = sum / historicalData.size();
        int excursionCount = excursionData != null ? excursionData.size() : 0;
        
        // tvAvgTemperature.setText(String.format("%.1f°C", avg));
        // tvMinTemperature.setText(String.format("%.1f°C", min));
        // tvMaxTemperature.setText(String.format("%.1f°C", max));
        // tvExcursionCount.setText(String.valueOf(excursionCount));
        
        // Color code max temperature using updated thresholds (2-8°C)
        // if (max > 8.0f) {
        //     tvMaxTemperature.setTextColor(getResources().getColor(R.color.error));
        // } else if (max > 6.0f) {
        //     tvMaxTemperature.setTextColor(getResources().getColor(R.color.warning));
        // } else {
        //     tvMaxTemperature.setTextColor(getResources().getColor(R.color.success));
        // }
        
        // // Color code min temperature using updated thresholds (2-8°C)
        // if (min < 2.0f) {
        //     tvMinTemperature.setTextColor(getResources().getColor(R.color.error));
        // } else if (min < 3.0f) {
        //     tvMinTemperature.setTextColor(getResources().getColor(R.color.warning));
        // } else {
        //     tvMinTemperature.setTextColor(getResources().getColor(R.color.success));
        // }
        
        Log.d("DeviceDetails", "Statistics updated - Avg: " + avg + "°C, Min: " + min + "°C, Max: " + max + "°C, Excursions: " + excursionCount);
    }

    private void setupGraph() {
        // Initialize the chart
        lineChart.getDescription().setEnabled(false); // Remove chart title
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDrawGridBackground(false);
        // Add extra offsets for axis labels
        lineChart.setExtraOffsets(16, 8, 16, 24); // left, top, right, bottom
        
        // Enable marker view for showing details on touch
        lineChart.setMarker(new com.github.mikephil.charting.components.MarkerView(this, R.layout.marker_view) {
            @Override
            public void refreshContent(Entry e, Highlight highlight) {
                TextView tvDate = findViewById(R.id.tv_marker_date);
                TextView tvTime = findViewById(R.id.tv_marker_time);
                TextView tvTemperature = findViewById(R.id.tv_marker_temperature);
                TextView tvStatus = findViewById(R.id.tv_marker_status);
                
                int index = (int) e.getX();
                float temperature = e.getY();
                
                if (index >= 0 && index < timeLabels.size() && index < originalTimestamps.size()) {
                    // Get original timestamp
                    long timestamp = originalTimestamps.get(index);
                    
                    // Convert timestamp to date and time
                    java.util.Date date = new java.util.Date(timestamp);
                    java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
                    java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                    
                    String dateStr = dateFormat.format(date);
                    String timeStr = timeFormat.format(date);
                    
                    tvDate.setText("Date: " + dateStr);
                    tvTime.setText("Time: " + timeStr);
                } else {
                    tvDate.setText("Date: --");
                    tvTime.setText("Time: --");
                }
                
                tvTemperature.setText(String.format("Temperature: %.1f°C", temperature));
                
                // Set status based on temperature using updated thresholds (2-8°C)
                if (temperature > 8.0f || temperature < 2.0f) {
                    tvStatus.setText("Status: ALERT");
                    tvStatus.setTextColor(getResources().getColor(R.color.error));
                } else {
                    tvStatus.setText("Status: NORMAL");
                    tvStatus.setTextColor(getResources().getColor(R.color.success));
                }
                
                super.refreshContent(e, highlight);
            }
        });
        
        // X Axis
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setDrawAxisLine(true);
        xAxis.setTextColor(getResources().getColor(R.color.text_primary));
        xAxis.setTextSize(10f);
        
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < timeLabels.size()) {
                    return timeLabels.get(index);
                }
                return "";
            }
        });
        
        // Y Axis
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(0f);   // Start from 0°C to show excursions below 2°C
        leftAxis.setAxisMaximum(12f);  // Go up to 12°C to accommodate excursions above 8°C
        leftAxis.setGranularity(2f);   // Show every 2°C
        leftAxis.setDrawAxisLine(true);
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        
        // Add Y-axis title
        leftAxis.setDrawLabels(true);
        leftAxis.setTextColor(getResources().getColor(R.color.text_primary));
        leftAxis.setTextSize(12f);
        
        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false);
        
        // Legend - Disable since we have custom legends below the graph
        lineChart.getLegend().setEnabled(false);
        
        // Enable highlighting for better touch interaction
        lineChart.setHighlightPerDragEnabled(true);
        lineChart.setHighlightPerTapEnabled(true);
        
        // Set animation duration
        lineChart.animateX(1000);
        
        // Enable auto scaling
        lineChart.setAutoScaleMinMaxEnabled(true);
        
        // Initially show placeholder
        lineChart.setVisibility(View.GONE);
        tvGraphPlaceholder.setVisibility(View.VISIBLE);
    }

    private List<String> timeLabels = new ArrayList<>();
    private List<Long> originalTimestamps = new ArrayList<>(); // Store original timestamps for date conversion
    private List<Entry> temperatureEntries = new ArrayList<>();
    private List<Entry> alertUpperEntries = new ArrayList<>();
    private List<Entry> alertLowerEntries = new ArrayList<>();

    // Removed loadHistoricalData() and createSampleData() methods - no fallback data wanted
    // Removed updateStatistics() method - only used for sample data

    private void updateGraph() {
        try {
            if (temperatureEntries == null || temperatureEntries.isEmpty()) {
                Log.w("DeviceDetails", "No temperature data available for chart");
                showNoDataMessage();
                return;
            }
            findViewById(R.id.tv_y_axis_title).setVisibility(View.VISIBLE);
            findViewById(R.id.tv_x_axis_title).setVisibility(View.VISIBLE);
            
            // Calculate min and max for dynamic scaling
            float minTemp = Float.MAX_VALUE;
            float maxTemp = Float.MIN_VALUE;
            for (Entry entry : temperatureEntries) {
                minTemp = Math.min(minTemp, entry.getY());
                maxTemp = Math.max(maxTemp, entry.getY());
            }
            
            // Ensure threshold lines are always visible
            float yMin = Math.min(minTemp, 2.0f); // Start from lowest temp or 2°C, whichever is lower
            float yMax = Math.max(maxTemp, 8.0f); // End at highest temp or 8°C, whichever is higher
            
            // Add padding to the range
            float padding = Math.max(0.5f, (yMax - yMin) * 0.1f);
            yMin = yMin - padding;
            yMax = yMax + padding;
            
            // Update Y-axis range for dynamic scaling
            YAxis leftAxis = lineChart.getAxisLeft();
            leftAxis.setAxisMinimum(yMin);
            leftAxis.setAxisMaximum(yMax);
            
            // Create temperature data set with custom colors for key points
            LineDataSet temperatureDataSet = new LineDataSet(temperatureEntries, "Temperature (°C)");
            temperatureDataSet.setColor(getResources().getColor(R.color.primary));
            temperatureDataSet.setLineWidth(2f);
            temperatureDataSet.setMode(LineDataSet.Mode.LINEAR);
            temperatureDataSet.setDrawValues(false);
            
            // Set custom colors for the 3 key points
            List<Integer> circleColors = new ArrayList<>();
            List<Float> circleRadius = new ArrayList<>();
            
            for (int i = 0; i < temperatureEntries.size(); i++) {
                if (i == 0) {
                    // Starting point - Green
                    circleColors.add(getResources().getColor(R.color.success));
                    circleRadius.add(6f);
                } else if (i == 1 && temperatureEntries.size() == 3) {
                    // Excursion start point - Dark Red
                    circleColors.add(getResources().getColor(R.color.error));
                    circleRadius.add(6f);
                } else {
                    // End point - Normal color
                    circleColors.add(getResources().getColor(R.color.primary));
                    circleRadius.add(6f);
                }
            }
            
            temperatureDataSet.setCircleColors(circleColors);
            temperatureDataSet.setCircleRadius(6f);
            temperatureDataSet.setDrawCircleHole(false);
            temperatureDataSet.setDrawCircles(true);
            
            // Create upper alert line
            LineDataSet upperAlertDataSet = new LineDataSet(alertUpperEntries, "High Threshold (8°C)");
            upperAlertDataSet.setColor(getResources().getColor(R.color.error));
            upperAlertDataSet.setLineWidth(1f);
            upperAlertDataSet.setDrawCircles(false);
            upperAlertDataSet.setDrawValues(false);
            upperAlertDataSet.setMode(LineDataSet.Mode.LINEAR);
            upperAlertDataSet.enableDashedLine(10f, 5f, 0f);
            
            // Create lower alert line
            LineDataSet lowerAlertDataSet = new LineDataSet(alertLowerEntries, "Low Threshold (2°C)");
            lowerAlertDataSet.setColor(getResources().getColor(R.color.blue));
            lowerAlertDataSet.setLineWidth(1f);
            lowerAlertDataSet.setDrawCircles(false);
            lowerAlertDataSet.setDrawValues(false);
            lowerAlertDataSet.setMode(LineDataSet.Mode.LINEAR);
            lowerAlertDataSet.enableDashedLine(10f, 5f, 0f);
            
            // Combine data sets
            LineData lineData = new LineData(temperatureDataSet, upperAlertDataSet, lowerAlertDataSet);
            lineChart.setData(lineData);
            
            // Update chart
            lineChart.invalidate();
            
            // Show chart and hide placeholder
            lineChart.setVisibility(View.VISIBLE);
            tvGraphPlaceholder.setVisibility(View.GONE);

            // Set appropriate number of labels based on data size (now always 3 points)
            lineChart.getXAxis().setLabelCount(temperatureEntries.size(), true);
            
        } catch (Exception e) {
            Log.e("DeviceDetails", "Error updating chart: " + e.getMessage(), e);
            showNoDataMessage();
        }
    }

    private void startStatusRefresh() {
        statusRefreshHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                statusRefreshHandler.postDelayed(this, STATUS_REFRESH_INTERVAL);
            }
        }, STATUS_REFRESH_INTERVAL);
    }

    private void stopStatusRefresh() {
        statusRefreshHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStatusRefresh();
    }
} 