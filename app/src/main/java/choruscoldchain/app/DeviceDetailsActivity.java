package choruscoldchain.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
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
    private TextView tvDeviceMac;
    private TextView tvBatteryLevel;
    private TextView tvFirmwareVersion;
    private TextView tvExcursionEventAt;
    private TextView tvMaxTemperatureTrip;
    private TextView tvExcursionDuration;
    private TextView tvCurrentTemperature;
    private TextView tvCurrentStatus;
    private LineChart lineChart;
    private TextView tvGraphPlaceholder;
    private View loadingOverlay;
    private View firmwareContainer;
    private android.os.Handler statusRefreshHandler = new android.os.Handler();
    private long correctedTempEventTimestamp = -1;
    private long correctedCurrentTimestamp = -1;
    private static final int STATUS_REFRESH_INTERVAL = 1000;
    // Bluetooth state monitoring
    private BluetoothStateReceiver bluetoothStateReceiver;
    private AlertDialog bluetoothOffDialog;

    public static Intent newIntent(Context context, MST03Entity device, int batteryLevel, String firmwareVersion,
            float currentTemperature, long correctedFirstExcursionTimestamp, long correctedCurrentTimestamp) {
        Intent intent = new Intent(context, DeviceDetailsActivity.class);
        intent.putExtra(EXTRA_DEVICE, device);
        intent.putExtra("battery_level", batteryLevel);
        intent.putExtra("firmware_version", firmwareVersion);
        intent.putExtra("current_temperature", currentTemperature);
        intent.putExtra("corrected_first_excursion_timestamp", correctedFirstExcursionTimestamp);
        intent.putExtra("corrected_current_timestamp", correctedCurrentTimestamp);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_details);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        device = getIntent().getParcelableExtra(EXTRA_DEVICE);
        if (device == null) {
            Toast.makeText(this, "Device information not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (device.getMacAddress() == null || device.getMacAddress().isEmpty()) {
            Toast.makeText(this, "Invalid device information", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        int batteryLevel = getIntent().getIntExtra("battery_level", -1);
        String firmwareVersion = getIntent().getStringExtra("firmware_version");
        float currentTemperature = getIntent().getFloatExtra("current_temperature", Float.NaN);
        this.correctedTempEventTimestamp = getIntent().getLongExtra("corrected_temp_event_timestamp", -1);
        this.correctedCurrentTimestamp = getIntent().getLongExtra("corrected_current_timestamp", -1);
        initViews();
        setupBackButton();
        setupGraph();
        setupRoleBasedUI();
        setupAppId();
        startStatusRefresh();
        loadBasicDeviceInfo(batteryLevel, firmwareVersion);
        showLoaderForConnectionData();
        loadCurrentTemperatureFromBeacon(currentTemperature);
        loadConnectionDataAsync();
        
        // Force test excursion duration calculation after a delay
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d("DeviceDetails", "Forcing excursion duration calculation test after delay");
                calculateAndDisplayExcursionDuration();
            }
        }, 3000); // 3 second delay
        
        // Initialize Bluetooth state monitoring
        initBluetoothStateMonitoring();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!temperatureEntries.isEmpty()) {
            loadProcessedData();
        }
        // Register Bluetooth state receiver
        registerBluetoothStateReceiver();
    }

    private void initViews() {
        tvDeviceMac = findViewById(R.id.tv_device_mac);
        tvBatteryLevel = findViewById(R.id.tv_battery_level);
        tvFirmwareVersion = findViewById(R.id.tv_firmware_version);
        tvExcursionEventAt = findViewById(R.id.tv_excursion_event_at);
        tvMaxTemperatureTrip = findViewById(R.id.tv_max_temperature_trip);
        tvExcursionDuration = findViewById(R.id.tv_excursion_duration);
        tvCurrentTemperature = findViewById(R.id.tv_current_temperature);
        lineChart = findViewById(R.id.graph_container);
        tvGraphPlaceholder = findViewById(R.id.tv_graph_placeholder);
        loadingOverlay = findViewById(R.id.loading_overlay);
        firmwareContainer = findViewById(R.id.firmware_container);
    }

    private void setupBackButton() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void setupAppId() {
        InstallationIdManager installationIdManager = InstallationIdManager.getInstance(this);
        TextView tvAppId = findViewById(R.id.tv_scan_app_id);
        if (tvAppId != null) {
            tvAppId.setText("App ID: " + installationIdManager.getInstallationId());
        }
    }

    private void setupRoleBasedUI() {
        AuthManager authManager = AuthManager.getInstance(this);
        String userRole = authManager.getUserRole();
        boolean isAdmin = "admin".equalsIgnoreCase(userRole);
        if (isAdmin) {
            firmwareContainer.setVisibility(View.VISIBLE);
        } else {
            firmwareContainer.setVisibility(View.GONE);
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

    private void loadBasicDeviceInfo(int batteryLevel, String firmwareVersion) {
        if (device != null && device.getMacAddress() != null) {
            tvDeviceMac.setText(device.getMacAddress());
        } else {
            tvDeviceMac.setText("Unknown");
            Log.w("DeviceDetails", "No MAC address available");
        }
        if (batteryLevel >= 0) {
            tvBatteryLevel.setText(batteryLevel + "%");
            if (batteryLevel < 20) {
                tvBatteryLevel.setTextColor(getResources().getColor(R.color.error));
            } else if (batteryLevel < 50) {
                tvBatteryLevel.setTextColor(getResources().getColor(R.color.warning));
            } else {
                tvBatteryLevel.setTextColor(getResources().getColor(R.color.success));
            }
        } else {
            try {
                DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) device
                        .getMinewFrame(FrameType.CUSTOM_DEVICE_INFORMATION_FRAME);
                if (deviceInfo != null) {
                    batteryLevel = deviceInfo.getBattery();
                    tvBatteryLevel.setText(batteryLevel + "%");
                    if (batteryLevel < 20) {
                        tvBatteryLevel.setTextColor(getResources().getColor(R.color.error));
                    } else if (batteryLevel < 50) {
                        tvBatteryLevel.setTextColor(getResources().getColor(R.color.warning));
                    } else {
                        tvBatteryLevel.setTextColor(getResources().getColor(R.color.success));
                    }
                } else {
                    tvBatteryLevel.setText("--");
                    Log.w("DeviceDetails", "No battery level data available");
                }
            } catch (Exception e) {
                tvBatteryLevel.setText("--");
                Log.e("DeviceDetails", "Error loading battery level: " + e.getMessage());
            }
        }
        if (firmwareVersion != null && !firmwareVersion.isEmpty()) {
            tvFirmwareVersion.setText(firmwareVersion);
            tvFirmwareVersion.setTextColor(getResources().getColor(R.color.text_primary));
        } else {
            try {
                DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) device
                        .getMinewFrame(FrameType.CUSTOM_DEVICE_INFORMATION_FRAME);
                if (deviceInfo != null) {
                    firmwareVersion = deviceInfo.getFirmwareVersion();
                    tvFirmwareVersion.setText(firmwareVersion);
                    tvFirmwareVersion.setTextColor(getResources().getColor(R.color.text_primary));
                } else {
                    tvFirmwareVersion.setText("--");
                    Log.w("DeviceDetails", "No firmware version data available");
                }
            } catch (Exception e) {
                tvFirmwareVersion.setText("--");
                Log.e("DeviceDetails", "Error loading firmware version: " + e.getMessage());
            }
        }
    }

    private void loadCurrentTemperatureFromBeacon(float fallbackTemperature) {
        float temperature = Float.NaN;
        boolean temperatureFound = false;
        
        // Strategy 1: Try to get temperature from connected beacon's CombinationFrame
        try {
            if (device != null) {
                CombinationFrame comboFrame = (CombinationFrame) device.getMinewFrame(FrameType.CUSTOM_COMBINATION_FRAME);
                if (comboFrame != null) {
                    temperature = comboFrame.getTemperature();
                    if (!Float.isNaN(temperature) && temperature != 0.0f) {
                        temperatureFound = true;
                        Log.d("DeviceDetails", "Got temperature from CombinationFrame: " + temperature);
                    } else {
                        Log.w("DeviceDetails", "Invalid temperature from CombinationFrame: " + temperature);
                    }
                } else {
                    Log.w("DeviceDetails", "No CombinationFrame available from connected beacon");
                }
            } else {
                Log.w("DeviceDetails", "Device is null, cannot get CombinationFrame");
            }
        } catch (Exception e) {
            Log.e("DeviceDetails", "Error fetching temperature from CombinationFrame: " + e.getMessage());
        }
        
        // Strategy 2: Try fallback temperature passed from intent
        if (!temperatureFound && !Float.isNaN(fallbackTemperature) && fallbackTemperature != 0.0f) {
            temperature = fallbackTemperature;
            temperatureFound = true;
            Log.d("DeviceDetails", "Using fallback temperature from intent: " + fallbackTemperature);
        }
        
        // Strategy 3: Try cached temperature from scan activity
        if (!temperatureFound) {
            try {
                if (ScanDevicesListActivity.getInstance() != null) {
                    Float cachedTemperature = ScanDevicesListActivity.getInstance()
                            .getCachedTemperature(device.getMacAddress());
                    if (cachedTemperature != null && !Float.isNaN(cachedTemperature) && cachedTemperature != 0.0f) {
                        temperature = cachedTemperature;
                        temperatureFound = true;
                        Log.d("DeviceDetails", "Using cached temperature: " + cachedTemperature);
                    }
                }
            } catch (Exception e) {
                Log.w("DeviceDetails", "Error getting cached temperature: " + e.getMessage());
            }
        }
        
        // Strategy 4: Try to get temperature from latest historical data
        if (!temperatureFound) {
            try {
                List<HtData> historicalData = ScanDevicesListActivity.getCompleteHistoricalData();
                if (historicalData != null && !historicalData.isEmpty()) {
                    // Get the most recent temperature reading
                    HtData latestData = historicalData.get(historicalData.size() - 1);
                    float histTemp = latestData.getTemperature();
                    if (!Float.isNaN(histTemp) && histTemp != 0.0f) {
                        temperature = histTemp;
                        temperatureFound = true;
                        Log.d("DeviceDetails", "Using latest historical temperature: " + histTemp);
                    }
                }
            } catch (Exception e) {
                Log.w("DeviceDetails", "Error getting historical temperature: " + e.getMessage());
            }
        }
        
        // Display the temperature or show unavailable
        if (temperatureFound) {
            tvCurrentTemperature.setText(String.format("%.1f°C", temperature));
            if (temperature > 8.0f || temperature < 2.0f) {
                tvCurrentTemperature.setTextColor(getResources().getColor(R.color.error));
            } else {
                tvCurrentTemperature.setTextColor(getResources().getColor(R.color.primary));
            }
            Log.d("DeviceDetails", "Successfully loaded current temperature: " + temperature + "°C");
        } else {
            tvCurrentTemperature.setText("Temperature unavailable");
            tvCurrentTemperature.setTextColor(getResources().getColor(R.color.text_secondary));
            Log.w("DeviceDetails", "No temperature data available from any source");
        }
    }

    private void loadBroadcastData() {
        if (device != null && device.getMacAddress() != null) {
            tvDeviceMac.setText(device.getMacAddress());
        } else {
            tvDeviceMac.setText("Unknown");
            Log.w("DeviceDetails", "No MAC address available");
        }
        try {
            DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) device
                    .getMinewFrame(FrameType.CUSTOM_DEVICE_INFORMATION_FRAME);
            if (deviceInfo != null) {
                deviceStaticInfo = deviceInfo;
                int batteryLevel = deviceStaticInfo.getBattery();
                tvBatteryLevel.setText(batteryLevel + "%");
                if (batteryLevel < 20) {
                    tvBatteryLevel.setTextColor(getResources().getColor(R.color.error));
                } else if (batteryLevel < 50) {
                    tvBatteryLevel.setTextColor(getResources().getColor(R.color.warning));
                } else {
                    tvBatteryLevel.setTextColor(getResources().getColor(R.color.success));
                }
                String firmwareVersion = deviceStaticInfo.getFirmwareVersion();
                tvFirmwareVersion.setText(firmwareVersion);
            } else {
                tvBatteryLevel.setText("--");
                tvFirmwareVersion.setText("--");
                Log.w("DeviceDetails", "No device static info frame available");
                try {
                    FrameType[] allFrameTypes = FrameType.values();
                    for (FrameType frameType : allFrameTypes) {
                        Object frame = device.getMinewFrame(frameType);
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
        try {
            CombinationFrame comboFrame = (CombinationFrame) device.getMinewFrame(FrameType.COMBINATION_FRAME);
            if (comboFrame != null) {
                combinationFrame = comboFrame;
                float temperature = combinationFrame.getTemperature();
                if (!Float.isNaN(temperature) && temperature != 0.0f) {
                    tvCurrentTemperature.setText(String.format("%.1f°C", temperature));
                    if (temperature > 8.0f || temperature < 2.0f) {
                        tvCurrentTemperature.setTextColor(getResources().getColor(R.color.error));
                    } else {
                        tvCurrentTemperature.setTextColor(getResources().getColor(R.color.primary));
                    }
                } else {
                    tvCurrentTemperature.setText("--°C");
                    tvCurrentTemperature.setTextColor(getResources().getColor(R.color.text_secondary));
                    Log.w("DeviceDetails", "Invalid temperature data: " + temperature);
                }
            } else {
                tvCurrentTemperature.setText("--°C");
                tvCurrentTemperature.setTextColor(getResources().getColor(R.color.text_secondary));
                Log.w("DeviceDetails", "No combination frame available");
            }
        } catch (Exception e) {
            Log.e("DeviceDetails", "Error loading temperature data: " + e.getMessage());
            tvCurrentTemperature.setText("--°C");
            tvCurrentTemperature.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    private void showLoaderForConnectionData() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void loadConnectionDataAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!ScanDevicesListActivity.isDataProcessingComplete()) {
                    if (ScanDevicesListActivity.getProcessedHistoricalData().isEmpty()) {
                        if (device != null && device.getMacAddress() != null) {
                        }
                    }
                } else {
                }
                int timeoutSeconds = 30;
                int checkIntervalMs = 500;
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ScanDevicesListActivity.isDataProcessingComplete()) {
                            loadProcessedData();
                        } else {
                            showTimeoutMessage();
                            showBasicDeviceInfoOnly();
                        }
                        hideLoader();
                    }
                });
            }
        }).start();
    }

    private void showTimeoutMessage() {
        lineChart.setVisibility(View.GONE);
        tvGraphPlaceholder.setVisibility(View.VISIBLE);
        tvGraphPlaceholder.setText("Data loading timed out. Please try reconnecting to the device.");
    }

    private void showNoDataMessage() {
        lineChart.setVisibility(View.GONE);
        tvGraphPlaceholder.setVisibility(View.VISIBLE);
        tvGraphPlaceholder.setText("No historical data available from device");
    }

    private void showBasicDeviceInfoOnly() {
        tvExcursionEventAt.setText("No excursions detected");
        lineChart.setVisibility(View.GONE);
        tvGraphPlaceholder.setVisibility(View.VISIBLE);
        tvGraphPlaceholder.setText(
                "Device information loaded successfully. Historical data will be available after the device has been monitoring for some time.");
    }

    private void loadProcessedData() {
        List<HtData> historicalData = ScanDevicesListActivity.getProcessedHistoricalData();
        List<ExcursionData> excursionData = ScanDevicesListActivity.getProcessedExcursionData();
        String dataDeviceMac = ScanDevicesListActivity.getCurrentDeviceMac();
        List<HtData> completeHistoricalData = ScanDevicesListActivity.getCompleteHistoricalData();
        if (device != null && dataDeviceMac != null && !dataDeviceMac.isEmpty()) {
            String currentDeviceMac = device.getMacAddress();
            if (!dataDeviceMac.equals(currentDeviceMac)) {
                showNoDataMessage();
                return;
            }
        }
        if (historicalData != null && !historicalData.isEmpty()) {
            timeLabels.clear();
            originalTimestamps.clear();
            temperatureEntries.clear();
            alertUpperEntries.clear();
            alertLowerEntries.clear();
            for (int i = 0; i < historicalData.size(); i++) {
                HtData htData = historicalData.get(i);
                long timestamp = htData.getTimestamps();
                if (timestamp < 10000000000L) {
                    timestamp = timestamp * 1000;
                }
                java.util.Date date = new java.util.Date(timestamp);
                String timeLabel = getLocaleAwareTimeFormat().format(date);
                timeLabels.add(timeLabel);
                temperatureEntries.add(new com.github.mikephil.charting.data.Entry(i, htData.getTemperature()));
                alertUpperEntries.add(new com.github.mikephil.charting.data.Entry(i, 8.0f));
                alertLowerEntries.add(new com.github.mikephil.charting.data.Entry(i, 2.0f));
                originalTimestamps.add(timestamp);
            }
            boolean hasNormalTemperature = false;
            for (HtData htData : completeHistoricalData) {
                float temperature = htData.getTemperature();
                if (temperature >= 2.0f && temperature <= 8.0f) {
                    hasNormalTemperature = true;
                    break;
                }
            }
            // Use the corrected timestamps passed from the scanning screen
            long firstExcursionTimestamp = -1;
            
            if (this.correctedTempEventTimestamp != -1) {
                // Use the corrected tempEventTimestamp passed from scanning
                firstExcursionTimestamp = this.correctedTempEventTimestamp * 1000; // Convert to milliseconds
                Log.d("DeviceDetails", String.format("Using corrected tempEventTimestamp passed from scanning: %d -> %s", 
                    this.correctedTempEventTimestamp, BeaconTimestampCorrector.timestampToHumanReadable(this.correctedTempEventTimestamp)));
            } else {
                // Fallback to processed data if corrected timestamp not available
                if (!hasNormalTemperature) {
                    if (!completeHistoricalData.isEmpty()) {
                        firstExcursionTimestamp = completeHistoricalData.get(0).getTimestamps();
                    } else if (!historicalData.isEmpty()) {
                        firstExcursionTimestamp = historicalData.get(0).getTimestamps();
                    }
                } else if (excursionData != null && !excursionData.isEmpty()) {
                    if (!excursionData.isEmpty()) {
                        firstExcursionTimestamp = excursionData.get(0).getTimestamp();
                    }
                }
                
                if (firstExcursionTimestamp != -1 && firstExcursionTimestamp < 10000000000L) {
                    firstExcursionTimestamp = firstExcursionTimestamp * 1000;
                }
                Log.d("DeviceDetails", "Using processed excursion data timestamp for excursion event time");
            }
            
            if (firstExcursionTimestamp != -1) {
                java.util.Date firstExcursionDate = new java.util.Date(firstExcursionTimestamp);
                String formattedDate = getLocaleAwareDateTimeFormat().format(firstExcursionDate);
                tvExcursionEventAt.setText(formattedDate);
                
                // Calculate and display max temperature during the trip
                calculateAndDisplayMaxTemperature();
                
                // Calculate and display excursion duration
                Log.d("DeviceDetails", "About to call calculateAndDisplayExcursionDuration()");
                calculateAndDisplayExcursionDuration();
            } else {
                Log.d("DeviceDetails", "No excursion data found, setting duration to --");
                tvExcursionEventAt.setText("No excursion data");
                tvMaxTemperatureTrip.setText("--");
                tvExcursionDuration.setText("--");
                
                // Force test the excursion duration calculation
                Log.d("DeviceDetails", "Forcing excursion duration calculation test");
                calculateAndDisplayExcursionDuration();
            }
            updateStatisticsFromProcessedData(historicalData, excursionData);
            updateGraph();
        } else {
            showNoDataMessage();
        }
    }

    private void updateStatisticsFromProcessedData(List<HtData> historicalData, List<ExcursionData> excursionData) {
        if (historicalData.isEmpty())
            return;
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
    }

    private void setupGraph() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDrawGridBackground(false);
        lineChart.setExtraOffsets(16, 8, 16, 24);
        lineChart.setMarker(new com.github.mikephil.charting.components.MarkerView(this, R.layout.marker_view) {
            @Override
            public void refreshContent(com.github.mikephil.charting.data.Entry e, Highlight highlight) {
                TextView tvDate = findViewById(R.id.tv_marker_date);
                TextView tvTime = findViewById(R.id.tv_marker_time);
                TextView tvTemperature = findViewById(R.id.tv_marker_temperature);
                TextView tvStatus = findViewById(R.id.tv_marker_status);
                int index = (int) e.getX();
                float temperature = e.getY();
                if (index >= 0 && index < timeLabels.size() && index < originalTimestamps.size()) {
                    long timestamp = originalTimestamps.get(index);
                    java.util.Date date = new java.util.Date(timestamp);
                    String dateStr = getLocaleAwareDateOnlyFormat().format(date);
                    String timeStr = getLocaleAwareTimeFormat().format(date);
                    tvDate.setText("Date: " + dateStr);
                    tvTime.setText("Time: " + timeStr);
                } else {
                    tvDate.setText("Date: --");
                    tvTime.setText("Time: --");
                }
                tvTemperature.setText(String.format("Temperature: %.1f°C", temperature));
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
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(12f);
        leftAxis.setGranularity(2f);
        leftAxis.setDrawAxisLine(true);
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        leftAxis.setDrawLabels(true);
        leftAxis.setTextColor(getResources().getColor(R.color.text_primary));
        leftAxis.setTextSize(12f);
        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setHighlightPerDragEnabled(true);
        lineChart.setHighlightPerTapEnabled(true);
        lineChart.animateX(1000);
        lineChart.setAutoScaleMinMaxEnabled(true);
        lineChart.setVisibility(View.GONE);
        tvGraphPlaceholder.setVisibility(View.VISIBLE);
    }

    private List<String> timeLabels = new ArrayList<>();
    private List<Long> originalTimestamps = new ArrayList<>();
    private List<com.github.mikephil.charting.data.Entry> temperatureEntries = new ArrayList<>();
    private List<com.github.mikephil.charting.data.Entry> alertUpperEntries = new ArrayList<>();
    private List<com.github.mikephil.charting.data.Entry> alertLowerEntries = new ArrayList<>();

    private void updateGraph() {
        try {
            if (temperatureEntries == null || temperatureEntries.isEmpty()) {
                showNoDataMessage();
                return;
            }
            findViewById(R.id.tv_y_axis_title).setVisibility(View.VISIBLE);
            findViewById(R.id.tv_x_axis_title).setVisibility(View.VISIBLE);
            float minTemp = Float.MAX_VALUE;
            float maxTemp = Float.MIN_VALUE;
            for (com.github.mikephil.charting.data.Entry entry : temperatureEntries) {
                minTemp = Math.min(minTemp, entry.getY());
                maxTemp = Math.max(maxTemp, entry.getY());
            }
            float yMin = Math.min(minTemp, 2.0f);
            float yMax = Math.max(maxTemp, 8.0f);
            float padding = Math.max(0.5f, (yMax - yMin) * 0.1f);
            yMin = yMin - padding;
            yMax = yMax + padding;
            YAxis leftAxis = lineChart.getAxisLeft();
            leftAxis.setAxisMinimum(yMin);
            leftAxis.setAxisMaximum(yMax);
            LineDataSet temperatureDataSet = new LineDataSet(temperatureEntries, "Temperature (°C)");
            temperatureDataSet.setColor(getResources().getColor(R.color.primary));
            temperatureDataSet.setLineWidth(2f);
            temperatureDataSet.setMode(LineDataSet.Mode.LINEAR);
            temperatureDataSet.setDrawValues(false);
            List<LineDataSet> dataSets = new ArrayList<>();
            if (temperatureEntries.size() >= 2) {
                int firstExcursionIndex = -1;
                for (int i = 0; i < temperatureEntries.size(); i++) {
                    float temperature = temperatureEntries.get(i).getY();
                    if (temperature < 2.0f || temperature > 8.0f) {
                        firstExcursionIndex = i;
                        break;
                    }
                }
                if (firstExcursionIndex != -1) {
                    List<com.github.mikephil.charting.data.Entry> normalSegment = new ArrayList<>();
                    List<com.github.mikephil.charting.data.Entry> excursionSegment = new ArrayList<>();
                    for (int i = 0; i <= firstExcursionIndex; i++) {
                        normalSegment.add(temperatureEntries.get(i));
                    }
                    for (int i = firstExcursionIndex; i < temperatureEntries.size(); i++) {
                        excursionSegment.add(temperatureEntries.get(i));
                    }
                    if (!normalSegment.isEmpty()) {
                        LineDataSet normalDataSet = new LineDataSet(normalSegment, "Normal Temperature (°C)");
                        normalDataSet.setColor(getResources().getColor(R.color.blue));
                        normalDataSet.setLineWidth(2f);
                        normalDataSet.setMode(LineDataSet.Mode.LINEAR);
                        normalDataSet.setDrawValues(false);
                        normalDataSet.setDrawCircles(false);
                        dataSets.add(normalDataSet);
                    }
                    if (!excursionSegment.isEmpty()) {
                        LineDataSet excursionDataSet = new LineDataSet(excursionSegment, "Excursion Temperature (°C)");
                        excursionDataSet.setColor(getResources().getColor(R.color.error));
                        excursionDataSet.setLineWidth(2f);
                        excursionDataSet.setMode(LineDataSet.Mode.LINEAR);
                        excursionDataSet.setDrawValues(false);
                        excursionDataSet.setDrawCircles(false);
                        dataSets.add(excursionDataSet);
                    }
                } else {
                    LineDataSet normalDataSet = new LineDataSet(temperatureEntries, "Normal Temperature (°C)");
                    normalDataSet.setColor(getResources().getColor(R.color.blue));
                    normalDataSet.setLineWidth(2f);
                    normalDataSet.setMode(LineDataSet.Mode.LINEAR);
                    normalDataSet.setDrawValues(false);
                    normalDataSet.setDrawCircles(false);
                    dataSets.add(normalDataSet);
                }
            } else {
                dataSets.add(temperatureDataSet);
            }
            LineDataSet circleDataSet = new LineDataSet(temperatureEntries, "Circles");
            circleDataSet.setColor(Color.TRANSPARENT);
            circleDataSet.setLineWidth(0f);
            circleDataSet.setMode(LineDataSet.Mode.LINEAR);
            circleDataSet.setDrawValues(false);
            circleDataSet.setDrawCircles(true);
            circleDataSet.setCircleRadius(6f);
            circleDataSet.setDrawCircleHole(false);
            List<Integer> circleColors = new ArrayList<>();
            boolean allPointsAreExcursions = true;
            for (int i = 0; i < temperatureEntries.size(); i++) {
                float temperature = temperatureEntries.get(i).getY();
                if (temperature >= 2.0f && temperature <= 8.0f) {
                    allPointsAreExcursions = false;
                    break;
                }
            }
            for (int i = 0; i < temperatureEntries.size(); i++) {
                float temperature = temperatureEntries.get(i).getY();
                boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);
                if (isNormal) {
                    circleColors.add(getResources().getColor(R.color.blue));
                } else if (allPointsAreExcursions) {
                    if (i == 0 || i == temperatureEntries.size() - 1) {
                        circleColors.add(getResources().getColor(R.color.error));
                    } else {
                        circleColors.add(Color.TRANSPARENT);
                    }
                } else if (i == 0) {
                    circleColors.add(getResources().getColor(R.color.error));
                } else if (i == 1) {
                    boolean isFirstExcursion = temperatureEntries.get(0).getY() <= 8.0f
                            && temperatureEntries.get(0).getY() >= 2.0f;
                    if (isFirstExcursion) {
                        circleColors.add(getResources().getColor(R.color.error));
                    } else {
                        circleColors.add(Color.TRANSPARENT);
                    }
                } else if (i == temperatureEntries.size() - 1) {
                    circleColors.add(getResources().getColor(R.color.error));
                } else {
                    circleColors.add(Color.TRANSPARENT);
                }
            }
            circleDataSet.setCircleColors(circleColors);
            dataSets.add(circleDataSet);
            LineDataSet upperAlertDataSet = new LineDataSet(alertUpperEntries, "High Threshold (8°C)");
            upperAlertDataSet.setColor(getResources().getColor(R.color.error));
            upperAlertDataSet.setLineWidth(1f);
            upperAlertDataSet.setDrawCircles(false);
            upperAlertDataSet.setDrawValues(false);
            upperAlertDataSet.setMode(LineDataSet.Mode.LINEAR);
            upperAlertDataSet.enableDashedLine(10f, 5f, 0f);
            LineDataSet lowerAlertDataSet = new LineDataSet(alertLowerEntries, "Low Threshold (2°C)");
            lowerAlertDataSet.setColor(getResources().getColor(R.color.blue));
            lowerAlertDataSet.setLineWidth(1f);
            lowerAlertDataSet.setDrawCircles(false);
            lowerAlertDataSet.setDrawValues(false);
            lowerAlertDataSet.setMode(LineDataSet.Mode.LINEAR);
            lowerAlertDataSet.enableDashedLine(10f, 5f, 0f);
            LineData lineData;
            if (dataSets.size() == 1) {
                lineData = new LineData(dataSets.get(0), upperAlertDataSet, lowerAlertDataSet);
            } else if (dataSets.size() == 2) {
                lineData = new LineData(dataSets.get(0), dataSets.get(1), upperAlertDataSet, lowerAlertDataSet);
            } else if (dataSets.size() == 3) {
                lineData = new LineData(dataSets.get(0), dataSets.get(1), dataSets.get(2), upperAlertDataSet,
                        lowerAlertDataSet);
            } else {
                lineData = new LineData(dataSets.get(0), upperAlertDataSet, lowerAlertDataSet);
            }
            lineChart.setData(lineData);
            lineChart.invalidate();
            lineChart.setVisibility(View.VISIBLE);
            tvGraphPlaceholder.setVisibility(View.GONE);
            int visiblePoints = 0;
            for (int i = 0; i < temperatureEntries.size(); i++) {
                float temperature = temperatureEntries.get(i).getY();
                boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);
                if (isNormal) {
                    visiblePoints++;
                } else if (allPointsAreExcursions) {
                    if (i == 0 || i == temperatureEntries.size() - 1) {
                        visiblePoints++;
                    }
                } else if (i == 0) {
                    visiblePoints++;
                } else if (i == 1) {
                    boolean isFirstExcursion = temperatureEntries.get(0).getY() <= 8.0f
                            && temperatureEntries.get(0).getY() >= 2.0f;
                    if (isFirstExcursion) {
                        visiblePoints++;
                    }
                } else if (i == temperatureEntries.size() - 1) {
                    visiblePoints++;
                } else {
                }
            }
            lineChart.getXAxis().setLabelCount(visiblePoints, true);
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
        // Unregister Bluetooth state receiver
        unregisterBluetoothStateReceiver();
        // Dismiss dialog if showing
        if (bluetoothOffDialog != null && bluetoothOffDialog.isShowing()) {
            bluetoothOffDialog.dismiss();
        }
    }

    private java.text.SimpleDateFormat getLocaleAwareDateFormat(String pattern) {
        java.util.Locale deviceLocale = java.util.Locale.getDefault();
        return new java.text.SimpleDateFormat(pattern, deviceLocale);
    }

    private java.text.SimpleDateFormat getLocaleAwareTimeFormat() {
        return getLocaleAwareDateFormat("HH:mm");
    }

    private java.text.SimpleDateFormat getLocaleAwareDateTimeFormat() {
        String datePattern = getDeviceDateFormatPattern();
        return new java.text.SimpleDateFormat(datePattern, java.util.Locale.getDefault());
    }

    private java.text.SimpleDateFormat getLocaleAwareDateOnlyFormat() {
        String datePattern = getDeviceDateFormatPattern();
        if (datePattern.contains("HH:mm")) {
            datePattern = datePattern.replace(" HH:mm", "");
        } else if (datePattern.contains("HH:mm:ss")) {
            datePattern = datePattern.replace(" HH:mm:ss", "");
        }
        return new java.text.SimpleDateFormat(datePattern, java.util.Locale.getDefault());
    }

    private String getDeviceDateFormatPattern() {
        try {
            java.util.TimeZone timeZone = java.util.TimeZone.getDefault();
            String timeZoneId = timeZone.getID();
            java.util.Locale deviceLocale = java.util.Locale.getDefault();
            String pattern = getDateFormatByTimezoneAndLocale(timeZoneId, deviceLocale);
            return pattern + " HH:mm";
        } catch (Exception e) {
            Log.e("DeviceDetails", "Error detecting device date format: " + e.getMessage());
            return "MM/dd/yyyy HH:mm";
        }
    }

    private String getDateFormatByTimezoneAndLocale(String timeZoneId, java.util.Locale locale) {
        if (timeZoneId.startsWith("America/") || timeZoneId.startsWith("US/") || timeZoneId.equals("CST")
                || timeZoneId.equals("EST") || timeZoneId.equals("PST") || timeZoneId.equals("MST")
                || timeZoneId.equals("CST6CDT") || timeZoneId.equals("EST5EDT")) {
            return "MM/dd/yyyy";
        }
        if (timeZoneId.startsWith("Europe/") || timeZoneId.equals("GMT") || timeZoneId.equals("UTC")) {
            return "dd/MM/yyyy";
        }
        if (timeZoneId.startsWith("Asia/") || timeZoneId.equals("IST") || timeZoneId.equals("JST")
                || timeZoneId.equals("KST") || timeZoneId.equals("CST")) {
            return "dd/MM/yyyy";
        }
        if (timeZoneId.startsWith("Australia/") || timeZoneId.equals("AEST") || timeZoneId.equals("AEDT")) {
            return "dd/MM/yyyy";
        }
        if (timeZoneId.startsWith("Canada/")) {
            return "dd/MM/yyyy";
        }
        String country = locale.getCountry();
        if ("US".equals(country)) {
            return "MM/dd/yyyy";
        } else if ("IN".equals(country)) {
            return "dd/MM/yyyy";
        } else if ("GB".equals(country) || "AU".equals(country) || "CA".equals(country)) {
            return "dd/MM/yyyy";
        } else if ("DE".equals(country) || "AT".equals(country) || "CH".equals(country)) {
            return "dd.MM.yyyy";
        } else {
            return "MM/dd/yyyy";
        }
    }

    // Bluetooth state monitoring methods
    private void initBluetoothStateMonitoring() {
        bluetoothStateReceiver = new BluetoothStateReceiver(new BluetoothStateReceiver.BluetoothStateListener() {
            @Override
            public void onBluetoothTurnedOff() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing() && !isDestroyed()) {
                            showBluetoothOffDialog();
                        }
                    }
                });
            }

            @Override
            public void onBluetoothTurnedOn() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing() && !isDestroyed()) {
                            // Dismiss dialog if showing
                            if (bluetoothOffDialog != null && bluetoothOffDialog.isShowing()) {
                                bluetoothOffDialog.dismiss();
                            }
                            // Show a toast to inform user
                            Toast.makeText(DeviceDetailsActivity.this, "Bluetooth is now available", Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });
            }
        });
    }

    private void registerBluetoothStateReceiver() {
        if (bluetoothStateReceiver != null && !isFinishing() && !isDestroyed()) {
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED);
                registerReceiver(bluetoothStateReceiver, filter);
            } catch (Exception e) {
                Log.e("DeviceDetails", "Error registering Bluetooth receiver: " + e.getMessage(), e);
            }
        }
    }

    private void unregisterBluetoothStateReceiver() {
        if (bluetoothStateReceiver != null) {
            try {
                unregisterReceiver(bluetoothStateReceiver);
            } catch (IllegalArgumentException e) {
                Log.w("DeviceDetails", "Bluetooth receiver not registered");
            } catch (Exception e) {
                Log.e("DeviceDetails", "Error unregistering Bluetooth receiver: " + e.getMessage(), e);
            }
        }
    }

    private void showBluetoothOffDialog() {
        // Check if activity is finishing or destroyed
        if (isFinishing() || isDestroyed()) {
            Log.w("DeviceDetails", "Activity is finishing or destroyed, cannot show dialog");
            return;
        }
        // Don't show multiple dialogs
        if (bluetoothOffDialog != null && bluetoothOffDialog.isShowing()) {
            return;
        }
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_bluetooth_off, null);
            builder.setView(dialogView);
            builder.setCancelable(false);
            bluetoothOffDialog = builder.create();
            bluetoothOffDialog.show();
            // Set up button click listeners
            View cancelButton = dialogView.findViewById(R.id.btn_cancel);
            if (cancelButton != null) {
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (bluetoothOffDialog != null && bluetoothOffDialog.isShowing()) {
                            bluetoothOffDialog.dismiss();
                        }
                    }
                });
            }
            View settingsButton = dialogView.findViewById(R.id.btn_settings);
            if (settingsButton != null) {
                settingsButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Open Bluetooth settings
                        Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        if (bluetoothOffDialog != null && bluetoothOffDialog.isShowing()) {
                            bluetoothOffDialog.dismiss();
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.e("DeviceDetails", "Error showing Bluetooth dialog: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calculate and display the maximum temperature during the trip period
     * (between tempEventTimestamp and currentTime from beacon data)
     */
    private void calculateAndDisplayMaxTemperature() {
        try {
            // Check if device is available
            if (device == null) {
                Log.w("DeviceDetails", "Device is null for max temperature calculation");
                calculateMaxTemperatureFallback();
                return;
            }
            
            // Get beacon data to access tempEventTimestamp and currentTime
            CombinationFrame comboFrame = null;
            try {
                comboFrame = (CombinationFrame) device.getMinewFrame(FrameType.CUSTOM_COMBINATION_FRAME);
            } catch (Exception e) {
                Log.e("DeviceDetails", "Error getting CombinationFrame for max temperature: " + e.getMessage(), e);
                calculateMaxTemperatureFallback();
                return;
            }
            
            if (comboFrame == null) {
                Log.w("DeviceDetails", "No CombinationFrame available, trying fallback calculation");
                calculateMaxTemperatureFallback();
                return;
            }
            
            long tempEventTimestamp = comboFrame.getTempEventTimestamp();
            long currentTime = comboFrame.getCurrentTimestamp();
            float currentTemperature = comboFrame.getTemperature();
            
            // Correct the timestamp to current system time
            long correctedTempEventTimestamp = BeaconTimestampCorrector.correctBeaconTimestamp(tempEventTimestamp, currentTime);
            
            // Log raw tempEventTimestamp as simple message
            Log.d("DeviceDetails", String.format("tempEventTimestamp: %d", tempEventTimestamp));
            Log.d("DeviceDetails", String.format("CORRECTED tempEventTimestamp: %d -> %s", 
                tempEventTimestamp, BeaconTimestampCorrector.timestampToHumanReadable(correctedTempEventTimestamp)));
            
            Log.d("DeviceDetails", String.format("Beacon data - tempEvent: %d, currentTime: %d, currentTemp: %.2f°C", 
                tempEventTimestamp, currentTime, currentTemperature));
            
            // Get historical data to find max temperature
            List<HtData> historicalData = ScanDevicesListActivity.getCompleteHistoricalData();
            if (historicalData == null || historicalData.isEmpty()) {
                Log.w("DeviceDetails", "No historical data available, using current temperature from beacon");
                if (!Float.isNaN(currentTemperature) && currentTemperature != 0.0f) {
                    tvMaxTemperatureTrip.setText(String.format("%.1f°C", currentTemperature));
                } else {
                    calculateMaxTemperatureFallback();
                }
                return;
            }
            
            // Strategy 1: Try to find max temperature within specific time range (if timestamps are meaningful)
            boolean foundDataInRange = false;
            float maxTemperature = Float.MIN_VALUE;
            
            if (tempEventTimestamp > 0 && currentTime > tempEventTimestamp) {
                // Convert beacon timestamps to wall-clock time
                long tempEventWallClock = alignBeaconTimestampToWallClock(tempEventTimestamp, currentTime);
                long currentTimeWallClock = System.currentTimeMillis();
                
                Log.d("DeviceDetails", String.format("Looking for data between %d (%s) and %d (%s)", 
                    tempEventWallClock, new java.util.Date(tempEventWallClock), 
                    currentTimeWallClock, new java.util.Date(currentTimeWallClock)));
                
                for (HtData htData : historicalData) {
                    long dataTimestamp = htData.getTimestamps();
                    // Normalize timestamp to milliseconds if needed
                    if (dataTimestamp < 10000000000L) {
                        dataTimestamp = dataTimestamp * 1000;
                    }
                    
                    // Check if this data point is within our time range
                    if (dataTimestamp >= tempEventWallClock && dataTimestamp <= currentTimeWallClock) {
                        float temperature = htData.getTemperature();
                        maxTemperature = Math.max(maxTemperature, temperature);
                        foundDataInRange = true;
                    }
                }
            }
            
            // Strategy 2: If no data found in specific range, use all available historical data
            if (!foundDataInRange) {
                Log.d("DeviceDetails", "No data in specific time range, using all historical data");
                for (HtData htData : historicalData) {
                    float temperature = htData.getTemperature();
                    maxTemperature = Math.max(maxTemperature, temperature);
                    foundDataInRange = true;
                }
            }
            
            // Strategy 3: Include current beacon temperature as well
            if (!Float.isNaN(currentTemperature) && currentTemperature != 0.0f) {
                maxTemperature = Math.max(maxTemperature, currentTemperature);
                foundDataInRange = true;
                Log.d("DeviceDetails", "Including current beacon temperature: " + currentTemperature);
            }
            
            if (foundDataInRange) {
                String maxTempText = String.format("%.1f°C", maxTemperature);
                tvMaxTemperatureTrip.setText(maxTempText);
                Log.d("DeviceDetails", "Max temperature calculated: " + maxTempText);
            } else {
                calculateMaxTemperatureFallback();
            }
            
        } catch (Exception e) {
            Log.e("DeviceDetails", "Error calculating max temperature: " + e.getMessage(), e);
            calculateMaxTemperatureFallback();
        }
    }
    
    /**
     * Fallback method to calculate max temperature when primary method fails
     */
    private void calculateMaxTemperatureFallback() {
        try {
            Log.d("DeviceDetails", "Using fallback max temperature calculation");
            
            // Try to get current temperature from any available source
            float fallbackTemp = Float.MIN_VALUE;
            boolean foundTemp = false;
            
            // Source 1: Current beacon temperature
            try {
                if (device != null) {
                    CombinationFrame comboFrame = (CombinationFrame) device.getMinewFrame(FrameType.CUSTOM_COMBINATION_FRAME);
                    if (comboFrame != null) {
                        float currentTemp = comboFrame.getTemperature();
                        if (!Float.isNaN(currentTemp) && currentTemp != 0.0f) {
                            fallbackTemp = Math.max(fallbackTemp, currentTemp);
                            foundTemp = true;
                            Log.d("DeviceDetails", "Found current temp from beacon: " + currentTemp);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w("DeviceDetails", "Could not get current temp from beacon: " + e.getMessage());
            }
            
            // Source 2: Cached temperature
            try {
                if (ScanDevicesListActivity.getInstance() != null) {
                    Float cachedTemp = ScanDevicesListActivity.getInstance().getCachedTemperature(device.getMacAddress());
                    if (cachedTemp != null && !Float.isNaN(cachedTemp) && cachedTemp != 0.0f) {
                        fallbackTemp = Math.max(fallbackTemp, cachedTemp);
                        foundTemp = true;
                        Log.d("DeviceDetails", "Found cached temp: " + cachedTemp);
                    }
                }
            } catch (Exception e) {
                Log.w("DeviceDetails", "Could not get cached temp: " + e.getMessage());
            }
            
            // Source 3: Any available historical data
            try {
                List<HtData> historicalData = ScanDevicesListActivity.getCompleteHistoricalData();
                if (historicalData != null && !historicalData.isEmpty()) {
                    for (HtData htData : historicalData) {
                        float temp = htData.getTemperature();
                        if (!Float.isNaN(temp) && temp != 0.0f) {
                            fallbackTemp = Math.max(fallbackTemp, temp);
                            foundTemp = true;
                        }
                    }
                    Log.d("DeviceDetails", "Found max temp from historical data: " + fallbackTemp);
                }
            } catch (Exception e) {
                Log.w("DeviceDetails", "Could not get historical data: " + e.getMessage());
            }
            
            if (foundTemp) {
                String maxTempText = String.format("%.1f°C", fallbackTemp);
                tvMaxTemperatureTrip.setText(maxTempText);
                Log.d("DeviceDetails", "Fallback max temperature: " + maxTempText);
            } else {
                tvMaxTemperatureTrip.setText("--");
                Log.w("DeviceDetails", "No temperature data available from any source");
            }
            
        } catch (Exception e) {
            Log.e("DeviceDetails", "Error in fallback calculation: " + e.getMessage(), e);
            tvMaxTemperatureTrip.setText("--");
        }
    }
    
    /**
     * Calculate and display the excursion duration from excursion event timestamp to current time
     */
    private void calculateAndDisplayExcursionDuration() {
        Log.d("DeviceDetails", "=== Starting excursion duration calculation ===");
        
        // Use the corrected tempEventTimestamp passed from the scanning screen
        long excursionEventTimestamp = -1;
        
        if (this.correctedTempEventTimestamp != -1) {
            // Use the corrected tempEventTimestamp passed from scanning
            excursionEventTimestamp = this.correctedTempEventTimestamp * 1000; // Convert to milliseconds
            Log.d("DeviceDetails", String.format("Using corrected tempEventTimestamp passed from scanning for duration: %d -> %s", 
                this.correctedTempEventTimestamp, BeaconTimestampCorrector.timestampToHumanReadable(this.correctedTempEventTimestamp)));
        } else {
            // Fallback to processed data if corrected timestamp not available
            List<ExcursionData> excursionData = ScanDevicesListActivity.getProcessedExcursionData();
            if (excursionData != null && !excursionData.isEmpty()) {
                excursionEventTimestamp = excursionData.get(0).getTimestamp();
                Log.d("DeviceDetails", "Got excursion timestamp from processed data: " + excursionEventTimestamp);
            }
        }
        
        if (excursionEventTimestamp == -1) {
            Log.w("DeviceDetails", "No excursion event timestamp found");
            tvExcursionDuration.setText("--");
            return;
        }
        
        // Calculate duration from excursion event to current time
        long currentTime = System.currentTimeMillis();
        long durationMs = currentTime - excursionEventTimestamp;
        
        Log.d("DeviceDetails", String.format("Duration calculation - excursionEvent: %d (%s), currentTime: %d (%s), durationMs: %d", 
            excursionEventTimestamp, new java.util.Date(excursionEventTimestamp), 
            currentTime, new java.util.Date(currentTime), durationMs));
        
        if (durationMs < 0) {
            Log.w("DeviceDetails", "Negative duration calculated, using absolute value");
            durationMs = Math.abs(durationMs);
        }
        
        // Convert to human-readable format
        String durationText = formatDuration(durationMs);
        
        tvExcursionDuration.setText(durationText);
        Log.d("DeviceDetails", "Excursion duration calculated successfully: " + durationText + " (" + durationMs + " ms)");
        Log.d("DeviceDetails", "=== Excursion duration calculation completed ===");
    }
    

    
    /**
     * Format duration in milliseconds to human-readable string
     */
    private String formatDuration(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + " ms";
        }
        
        long seconds = durationMs / 1000;
        if (seconds < 60) {
            return seconds + " sec";
        }
        
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours < 24) {
            return hours + "h " + minutes + "m";
        }
        
        long days = hours / 24;
        hours = hours % 24;
        return days + "d " + hours + "h";
    }
    
    /**
     * Converts a beacon event timestamp to wall-clock using the beacon's currentTimestamp as reference.
     * This method is copied from ScanDevicesListActivity for consistency.
     */
    private long alignBeaconTimestampToWallClock(long beaconEventTimestamp, long beaconCurrentTimestamp) {
        long diffRaw = Math.abs(beaconCurrentTimestamp - beaconEventTimestamp);
        long nowMs = System.currentTimeMillis();
        long candidateMs = nowMs - diffRaw;            // assume diff is already in ms
        long candidateSec = nowMs - diffRaw * 1000L;   // assume diff is in seconds
        long twoYearsMs = 730L * 24 * 60 * 60 * 1000;  // 2 years
        long lowerBound = nowMs - twoYearsMs;
        boolean aOk = candidateMs <= nowMs && candidateMs >= lowerBound;
        boolean bOk = candidateSec <= nowMs && candidateSec >= lowerBound;
        if (aOk && !bOk) return candidateMs;
        if (bOk && !aOk) return candidateSec;
        // Heuristic: small diffs (< 1,000,000) are likely seconds; larger diffs likely milliseconds
        if (diffRaw < 1_000_000L) return candidateSec;
        return candidateMs;
    }
}