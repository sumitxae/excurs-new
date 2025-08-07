package choruscoldchain.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;
import android.app.AlertDialog;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.kongzue.dialogx.dialogs.WaitDialog;
import com.minew.ble.mst03.bean.MST03Entity;
import com.minew.ble.mst03.bean.HistoryHtData;
import com.minew.ble.mst03.bean.HtData;
import com.minew.ble.mst03.frames.DeviceStaticInfoFrame;
import com.minew.ble.mst03.manager.MST03SensorBleManager;
import com.minew.ble.v3.enums.BleConnectionState;
import com.minew.ble.v3.interfaces.OnConnStateListener;
import com.minew.ble.v3.interfaces.OnScanDevicesResultListener;
import com.minew.ble.v3.interfaces.OnQueryResultListener;
import com.minew.ble.v3.utils.BLETool;
import choruscoldchain.app.databinding.ActivityScanDevicesBinding;
import com.permissionx.guolindev.PermissionX;
import com.permissionx.guolindev.callback.ExplainReasonCallback;
import com.permissionx.guolindev.callback.ForwardToSettingsCallback;
import com.permissionx.guolindev.callback.RequestCallback;
import com.permissionx.guolindev.request.ExplainScope;
import com.permissionx.guolindev.request.ForwardScope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.minew.ble.mst03.frames.CombinationFrame;
import com.minew.ble.v3.enums.FrameType;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.HashSet;

public class ScanDevicesListActivity extends BaseActivity {

    private static ScanDevicesListActivity instance;

    public static ScanDevicesListActivity getInstance() {
        return instance;
    }

    private ActivityScanDevicesBinding binding;

    private ScanDevicesListAdapter mDevicesListAdapter;
    private ObjectAnimator mObjectAnimator;

    private MST03SensorBleManager mBleManager;

    private MST03Entity mst03Entity;
    private DeviceStaticInfoFrame currentDeviceStaticInfo;
    private ExcursionLogger excursionLogger;
    private HttpLogger httpLogger;
    private List<ExcursionData> excursionDataList = new ArrayList<>();
    private List<MST03Entity> allDiscoveredDevices = new ArrayList<>();
    private List<MST03Entity> filteredDevices = new ArrayList<>();

    private static List<HtData> processedHistoricalData = new ArrayList<>();
    private static List<ExcursionData> processedExcursionData = new ArrayList<>();
    private static boolean isDataProcessingComplete = false;
    private static String currentProcessingStatus = "Idle";
    private static String currentDeviceMac = "";
    private static List<HtData> completeHistoricalData = new ArrayList<>();
    private boolean isSearchMode = false;
    private boolean isClearingSearch = false;
    private boolean isConnecting = false;
    private boolean isBleManagerReady = false;
    private boolean isActivityLaunched = false; // Flag to prevent double activity launch
    private android.os.Handler connectionTimeoutHandler = new android.os.Handler();
    private android.os.Handler searchDebounceHandler = new android.os.Handler();
    private android.os.Handler bleReadyHandler = new android.os.Handler();
    private static final int SEARCH_DEBOUNCE_DELAY = 500;
    private static final int BLE_READY_DELAY = 2000;
    
    // Bluetooth state monitoring
    private BluetoothStateReceiver bluetoothStateReceiver;
    private AlertDialog bluetoothOffDialog;

    private java.util.Map<String, Float> temperatureCache = new java.util.HashMap<>();
    private java.util.Map<String, Long> temperatureTimestampCache = new java.util.HashMap<>();
    private static final long TEMPERATURE_CACHE_TIMEOUT = 30000;
    private DeviceDiscoveryManager deviceManager;
    private DeviceDiscoveryManager.OnDevicesUpdatedListener deviceUpdateListener;
    private boolean permissionsGranted = false;
    private ImageView ivChorusLogo;
    private boolean isInitialScan = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        binding = ActivityScanDevicesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnKebabMenu.setOnClickListener(v -> showCustomMenu(v));

        ivChorusLogo = findViewById(R.id.iv_chorus_logo);
        loadChorusLogo();

        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        initRefresh();
        initRecyclerView();

        initBlePermission();

        deviceManager = DeviceDiscoveryManager.getInstance();
        deviceUpdateListener = new DeviceDiscoveryManager.OnDevicesUpdatedListener() {
            @Override
            public void onDevicesUpdated(List<MST03Entity> devices) {
                Log.d("ScanOptimization", "onDevicesUpdated called with " + devices.size() + " devices");

                runOnUiThread(() -> {
                    try {
                        updateAllDiscoveredDevices(devices);

                        Log.d("ScanOptimization", "Final device list (" + allDiscoveredDevices.size() + " devices):");
                        for (int i = 0; i < allDiscoveredDevices.size(); i++) {
                            MST03Entity device = allDiscoveredDevices.get(i);
                            boolean hasExcursion = hasExcursion(device);
                            Log.d("ScanOptimization", "  " + i + ": " + device.getMacAddress() +
                                    " - Excursion: " + hasExcursion);
                        }

                        if (isSearchMode && binding.etSearch.getText().toString().trim().length() > 0) {
                            String currentSearchText = binding.etSearch.getText().toString().trim();
                            filterDevices(currentSearchText);
                        } else {
                            if (mDevicesListAdapter != null) {
                                mDevicesListAdapter.setList(new ArrayList<>(allDiscoveredDevices));
                                mDevicesListAdapter.notifyDataSetChanged();
                                Log.d("ScanOptimization",
                                        "Adapter updated with " + allDiscoveredDevices.size() + " devices");
                            } else {
                                Log.e("ScanOptimization", "Adapter is null - cannot update UI");
                            }
                        }
                        Log.d("ScanOptimization", "Real-time update: " + devices.size() + " devices, total: "
                                + allDiscoveredDevices.size());
                    } catch (Exception e) {
                        Log.e("ScanOptimization", "Error in onDevicesUpdated: " + e.getMessage(), e);
                    }
                });
            }
        };
        deviceManager.addListener(deviceUpdateListener);

        httpLogger = new HttpLogger();
        setupSearchFunctionality();
        setupAppId();
        
        // Initialize Bluetooth state monitoring
        initBluetoothStateMonitoring();
    }

    private void showCustomMenu(View anchor) {
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        View menuView = inflater.inflate(R.layout.menu_logout_popup, null);
        android.widget.LinearLayout logoutBtn = menuView.findViewById(R.id.menu_logout);
        android.widget.PopupWindow popup = new android.widget.PopupWindow(menuView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setElevation(16f);
        popup.setBackgroundDrawable(getDrawable(R.drawable.bg_popup_menu));
        popup.setOutsideTouchable(true);
        logoutBtn.setOnClickListener(v -> {
            popup.dismiss();
            handleLogout();
        });

        menuView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int xOff = anchor.getWidth() - menuView.getMeasuredWidth();
        popup.showAsDropDown(anchor, xOff, 0);
    }

    private void handleLogout() {
        AuthManager authManager = AuthManager.getInstance(this);
        authManager.logout();

        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupSearchFunctionality() {

        binding.btnScanQr.setOnClickListener(v -> {
            startQRScanner();
        });

        binding.btnClearSearch.setOnClickListener(v -> {
            clearSearch();
        });

        binding.etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {

                    if (isClearingSearch) {
                        return;
                    }

                    String searchText = s.toString().trim();

                    searchDebounceHandler.removeCallbacksAndMessages(null);

                    if (searchText.isEmpty()) {
                        clearSearch();
                        return;
                    }

                    binding.btnClearSearch.setVisibility(View.VISIBLE);

                    searchDebounceHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {

                            String currentSearchText = binding.etSearch.getText().toString().trim();
                            if (!currentSearchText.isEmpty()) {
                                filterDevices(currentSearchText);
                            }
                        }
                    }, SEARCH_DEBOUNCE_DELAY);

                } catch (Exception e) {
                    Log.e("ScanDebug", "Error in search text change: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
    }

    private void startQRScanner() {
        Intent intent = new Intent(this, QRScannerActivity.class);
        startActivityForResult(intent, QR_SCAN_REQUEST_CODE);
    }

    private static final int QR_SCAN_REQUEST_CODE = 1001;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == QR_SCAN_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String scanResult = data.getStringExtra(QRScannerActivity.EXTRA_SCAN_RESULT);
            if (scanResult != null && !scanResult.trim().isEmpty()) {

                processQRScanResult(scanResult);
            }
        } else if (requestCode == 1002) {

            if (hasStoragePermissions()) {
                Log.d("BeaconRawData", "Storage permission granted via settings");

                if (!processedHistoricalData.isEmpty()) {
                    generateCSVForCurrentData();
                }
            } else {
                Log.w("BeaconRawData", "Storage permission not granted, will use app directory");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Storage permission not granted. CSV will be saved in app directory.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }
    }

    private void processQRScanResult(String scanResult) {

        String macAddress = extractMacAddressFromQR(scanResult);

        if (macAddress != null) {

            isClearingSearch = true;
            binding.etSearch.setText(macAddress);
            isClearingSearch = false;

            binding.btnClearSearch.setVisibility(View.VISIBLE);

            filterDevicesWithoutToast(macAddress);
        } else {

            isClearingSearch = true;
            binding.etSearch.setText(scanResult);
            isClearingSearch = false;

            binding.btnClearSearch.setVisibility(View.VISIBLE);

            filterDevicesWithoutToast(scanResult);
        }
    }

    private String extractMacAddressFromQR(String qrText) {

        String[] patterns = {
                "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})",
                "([0-9A-Fa-f]{2}){6}",
                "MAC[\\s:]*([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})",
                "Device[\\s:]*([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})"
        };

        for (String pattern : patterns) {
            java.util.regex.Pattern macPattern = java.util.regex.Pattern.compile(pattern,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = macPattern.matcher(qrText);
            if (matcher.find()) {
                return matcher.group().toUpperCase();
            }
        }

        if (qrText.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")) {
            return qrText.toUpperCase();
        }

        return null;
    }

    private void filterDevices(String searchText) {
        try {

            if (searchText == null || searchText.trim().isEmpty()) {

                return;
            }

            if (allDiscoveredDevices == null || allDiscoveredDevices.isEmpty()) {
                Toast.makeText(this, "No devices discovered yet. Please wait for scan results.", Toast.LENGTH_SHORT)
                        .show();
                return;
            }

            isSearchMode = true;
            if (filteredDevices == null) {
                filteredDevices = new ArrayList<>();
            } else {
                filteredDevices.clear();
            }

            String searchLower = searchText.toLowerCase();

            for (MST03Entity device : allDiscoveredDevices) {
                if (device == null)
                    continue;

                if (device.getMacAddress() != null &&
                        device.getMacAddress().toLowerCase().contains(searchLower)) {
                    filteredDevices.add(device);
                    continue;
                }

                String macWithoutColons = device.getMacAddress() != null
                        ? device.getMacAddress().replace(":", "").toLowerCase()
                        : "";
                if (macWithoutColons.contains(searchLower.replace(":", ""))) {
                    filteredDevices.add(device);
                }
            }

            if (mDevicesListAdapter != null) {
                mDevicesListAdapter.setList(filteredDevices);
                mDevicesListAdapter.notifyDataSetChanged();

            } else {
                Log.e("ScanDebug", "Adapter is null, cannot update search results");
            }

        } catch (Exception e) {
            Log.e("ScanDebug", "Error in filterDevices: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Error during search: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void filterDevicesWithoutToast(String searchText) {
        try {

            if (searchText == null || searchText.trim().isEmpty()) {

                return;
            }

            if (allDiscoveredDevices == null || allDiscoveredDevices.isEmpty()) {
                Toast.makeText(this, "No devices discovered yet. Please wait for scan results.", Toast.LENGTH_SHORT)
                        .show();
                return;
            }

            isSearchMode = true;
            if (filteredDevices == null) {
                filteredDevices = new ArrayList<>();
            } else {
                filteredDevices.clear();
            }

            String searchLower = searchText.toLowerCase();

            for (MST03Entity device : allDiscoveredDevices) {
                if (device == null)
                    continue;

                if (device.getMacAddress() != null &&
                        device.getMacAddress().toLowerCase().contains(searchLower)) {
                    filteredDevices.add(device);
                    continue;
                }

                String macWithoutColons = device.getMacAddress() != null
                        ? device.getMacAddress().replace(":", "").toLowerCase()
                        : "";
                if (macWithoutColons.contains(searchLower.replace(":", ""))) {
                    filteredDevices.add(device);
                }
            }

            if (mDevicesListAdapter != null) {
                mDevicesListAdapter.setList(filteredDevices);
                mDevicesListAdapter.notifyDataSetChanged();

                String resultText = filteredDevices.size() + " device(s) found";
                if (filteredDevices.size() == 0) {
                    resultText = "No devices found matching '" + searchText + "'";
                }
                Toast.makeText(this, resultText, Toast.LENGTH_SHORT).show();

            } else {
                Log.e("ScanDebug", "Adapter is null, cannot update search results");
            }

        } catch (Exception e) {
            Log.e("ScanDebug", "Error in filterDevicesWithoutToast: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Error during search: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void clearSearch() {
        try {
            isClearingSearch = true;
            isSearchMode = false;

            if (binding.etSearch != null) {

                binding.etSearch.setText("");
            }

            if (binding.btnClearSearch != null) {
                binding.btnClearSearch.setVisibility(View.GONE);
            }

            if (filteredDevices != null) {
                filteredDevices.clear();
            }

            if (mDevicesListAdapter != null && allDiscoveredDevices != null) {
                mDevicesListAdapter.setList(allDiscoveredDevices);
                mDevicesListAdapter.notifyDataSetChanged();

            } else {

            }

        } catch (Exception e) {
            Log.e("ScanDebug", "Error in clearSearch: " + e.getMessage());
            e.printStackTrace();
        } finally {
            isClearingSearch = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        isBleManagerReady = false;
        isConnecting = false;

        Log.d("ScanDebug",
                "Returning to scan screen, preserving temperature cache with " + temperatureCache.size() + " entries");

        ensureBleManagerReady();

        bleReadyHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startScan();
            }
        }, 1000);
        
        // Register Bluetooth state receiver
        registerBluetoothStateReceiver();
    }

    @Override
    protected void onStop() {
        super.onStop();

        bleReadyHandler.removeCallbacksAndMessages(null);
        connectionTimeoutHandler.removeCallbacksAndMessages(null);

        removeBleManagerListener();

        isConnecting = false;
        isBleManagerReady = false;

        Log.d("ScanDebug",
                "Leaving scan screen, preserving temperature cache with " + temperatureCache.size() + " entries");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        instance = null;

        if (searchDebounceHandler != null) {
            searchDebounceHandler.removeCallbacksAndMessages(null);
        }
        if (connectionTimeoutHandler != null) {
            connectionTimeoutHandler.removeCallbacksAndMessages(null);
        }
        if (bleReadyHandler != null) {
            bleReadyHandler.removeCallbacksAndMessages(null);
        }

        if (deviceManager != null && deviceUpdateListener != null) {
            deviceManager.removeListener(deviceUpdateListener);
        }
        if (isBackgroundServiceRunning()) {
            stopService(new Intent(this, BackgroundScanService.class));
        }
        if (mBleManager != null) {
            mBleManager.setOnConnStateListener(null);
        }
        if (httpLogger != null) {
            httpLogger.shutdown();
        }
        
        // Unregister Bluetooth state receiver
        unregisterBluetoothStateReceiver();
        
        // Dismiss dialog if showing
        if (bluetoothOffDialog != null && bluetoothOffDialog.isShowing()) {
            bluetoothOffDialog.dismiss();
        }
    }

    private void initRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                stopScan();
                startScan();
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void initRecyclerView() {

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(binding.recyclerView.getContext()));
        mDevicesListAdapter = new ScanDevicesListAdapter(R.layout.item_scan_device, null);

        binding.recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayout.VERTICAL));

        mDevicesListAdapter.setOnConnectClickListener(new ScanDevicesListAdapter.OnConnectClickListener() {
            @Override
            public void onConnectClick(MST03Entity device) {

                connectToDeviceAndNavigate(device);

            }
        });

        if (mDevicesListAdapter != null) {

        } else {
            Log.e("ScanDebug", "Adapter is null!");
        }

        binding.recyclerView.setAdapter(mDevicesListAdapter);

    }

    private void connectToDeviceAndNavigate(MST03Entity device) {

        if (isConnecting) {

            Toast.makeText(this, "Already connecting to a device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isBleManagerReady) {
            Toast.makeText(this, "Bluetooth is initializing, please wait a moment...", Toast.LENGTH_LONG).show();
            Log.d("ScanDebug", "Connection attempt while BLE manager not ready, user should wait");
            return;
        }

        clearProcessedData();
        resetConnectionState();

        isConnecting = true;
        mst03Entity = device;

        currentDeviceMac = device.getMacAddress();

        Log.d("ScanDebug", "Starting connection to device: " + device.getMacAddress());
        WaitDialog.show("");

        connectionTimeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnecting && !isActivityLaunched) {
                    Log.d("ScanDebug", "Connection timeout - proceeding to device details with available data");
                    isConnecting = false;
                    isActivityLaunched = true;
                    WaitDialog.dismiss();

                    isDataProcessingComplete = true;
                    updateProcessingStatus("Timeout");

                    int batteryLevel = -1;
                    String firmwareVersion = "Unknown";
                    float currentTemperature = Float.NaN;

                    if (device != null) {

                        DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) device
                                .getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME);
                        if (deviceInfo != null) {
                            batteryLevel = deviceInfo.getBattery();
                            firmwareVersion = deviceInfo.getFirmwareVersion();

                        }

                        CombinationFrame comboFrame = (CombinationFrame) device
                                .getMinewFrame(FrameType.COMBINATION_FRAME);
                        if (comboFrame != null) {
                            currentTemperature = comboFrame.getTemperature();

                        }
                    }

                    Intent intent = DeviceDetailsActivity.newIntent(ScanDevicesListActivity.this, device, batteryLevel,
                            firmwareVersion, currentTemperature);
                    startActivity(intent);
                }
            }
        }, 8000);

        ensureBleManagerReady();

        if (isBackgroundServiceRunning()) {
            stopService(new Intent(this, BackgroundScanService.class));
        }

        mBleManager.stopScan(this);

        try {
            if (mBleManager != null) {

                mBleManager.connect(this, device);

            } else {
                Log.e("ScanDebug", "BLE manager is null, cannot connect");
                throw new Exception("BLE manager not initialized");
            }
        } catch (Exception e) {
            Log.e("ScanDebug", "Error connecting to device: " + e.getMessage());
            e.printStackTrace();
            isConnecting = false;
            WaitDialog.dismiss();

            int batteryLevel = -1;
            String firmwareVersion = "Unknown";
            float currentTemperature = Float.NaN;

            if (device != null) {

                DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) device
                        .getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME);
                if (deviceInfo != null) {
                    batteryLevel = deviceInfo.getBattery();
                    firmwareVersion = deviceInfo.getFirmwareVersion();

                }

                CombinationFrame comboFrame = (CombinationFrame) device.getMinewFrame(FrameType.COMBINATION_FRAME);
                if (comboFrame != null) {
                    currentTemperature = comboFrame.getTemperature();

                }
            }

            Intent intent = DeviceDetailsActivity.newIntent(ScanDevicesListActivity.this, device, batteryLevel,
                    firmwareVersion, currentTemperature);
            startActivity(intent);
        }

    }

    private void initAnimator() {
        mObjectAnimator = ObjectAnimator.ofFloat(binding.ibHomeScan, "rotation", 0f, 360f);
        mObjectAnimator.setDuration(1500);
        mObjectAnimator.setRepeatCount(ValueAnimator.INFINITE);

        binding.ibHomeScan.setOnClickListener(v -> {
            if (!permissionsGranted || mObjectAnimator == null)
                return;
            stopScan();
            startScan();
            mObjectAnimator.start();
        });
    }

    private void initBleManager() {
        if (!permissionsGranted)
            return;
        try {
            mBleManager = MST03SensorBleManager.getInstance();
            if (mBleManager == null) {
                Log.e("ScanDebug", "Failed to get BLE manager instance");
                Toast.makeText(this, "Failed to initialize Bluetooth manager", Toast.LENGTH_SHORT).show();
                isBleManagerReady = false;
                return;
            }

            setBleManagerListener();

            isBleManagerReady = false;
            bleReadyHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isBleManagerReady = true;
                    Log.d("ScanDebug", "BLE Manager is now ready for connections");
                }
            }, BLE_READY_DELAY);

        } catch (Exception e) {
            Log.e("ScanDebug", "Error initializing BLE manager: " + e.getMessage());
            Toast.makeText(this, "Error initializing Bluetooth: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isBleManagerReady = false;
        }
    }

    private void ensureBleManagerReady() {
        if (mBleManager == null) {
            Log.d("ScanDebug", "BLE Manager is null, initializing...");
            initBleManager();
        }

        setBleManagerListener();

        if (!isBleManagerReady) {
            Log.d("ScanDebug", "BLE Manager not ready, waiting for stabilization...");
            isBleManagerReady = false;
            bleReadyHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isBleManagerReady = true;
                    Log.d("ScanDebug", "BLE Manager is now ready for connections");
                }
            }, BLE_READY_DELAY);
        }
    }

    private void setBleManagerListener() {
        if (mBleManager != null) {
            mBleManager.setOnConnStateListener(mConnStateListener);

        } else {
            Log.e("ScanDebug", "Cannot set BLE manager listener - manager is null");
        }
    }

    private void removeBleManagerListener() {
        mBleManager.setOnConnStateListener(null);
    }

    private void startBackgroundScanService() {
        if (!permissionsGranted)
            return;
        try {
            Intent serviceIntent = new Intent(this, BackgroundScanService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

        } catch (Exception e) {
            Log.e("ScanDebug", "Failed to start background service: " + e.getMessage());

        }
    }
    
    private void restartBackgroundScanService() {
        if (!permissionsGranted)
            return;
        try {
            // Stop the existing service
            Intent stopIntent = new Intent(this, BackgroundScanService.class);
            stopService(stopIntent);
            
            // Wait a bit then start fresh
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent serviceIntent = new Intent(ScanDevicesListActivity.this, BackgroundScanService.class);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent);
                        } else {
                            startService(serviceIntent);
                        }
                        Log.d("ScanDevicesList", "Background scan service restarted");
                    } catch (Exception e) {
                        Log.e("ScanDevicesList", "Failed to restart background service: " + e.getMessage());
                    }
                }
            }, 1000); // Wait 1 second before restarting
            
        } catch (Exception e) {
            Log.e("ScanDevicesList", "Failed to restart background service: " + e.getMessage());
        }
    }

    private OnConnStateListener mConnStateListener = new OnConnStateListener() {
        @Override
        public void onUpdateConnState(String s, BleConnectionState mSensorConnectionState) {
            Log.d("ScanDebug", "Connection state update: " + s + " -> " + mSensorConnectionState);

            if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                Log.d("ScanDebug", "State update for current device: " + mst03Entity.getMacAddress());
            }

            switch (mSensorConnectionState) {
                case Connecting:
                    Log.d("ScanDebug", "Connecting to device: " + s);
                    break;
                case Connected:
                    Log.d("ScanDebug", "Connected to device: " + s);
                    if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                        Log.d("ScanDebug", "Setting authentication key for device: " + s);
                        setKey(s);
                    }
                    break;
                case AuthenticateSuccess:
                    Log.d("ScanDebug", "Authentication successful for device: " + s);
                    break;
                case AuthenticateFail:
                    Log.e("ScanDebug", "Authentication failed for device: " + s);

                    if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                        connectionTimeoutHandler.removeCallbacksAndMessages(null);
                        WaitDialog.dismiss();

                        isDataProcessingComplete = true;
                        updateProcessingStatus("Auth Failed");

                        runOnUiThread(() -> {
                            Toast.makeText(ScanDevicesListActivity.this, "Authentication failed. Please try again.",
                                    Toast.LENGTH_LONG).show();
                            isConnecting = false;

                            if (mBleManager != null) {
                                Log.d("ScanDebug", "Disconnecting device after auth failure: " + s);
                                mBleManager.disConnect(s);
                            }
                        });
                    }
                    break;

                case ConnectComplete:
                    Log.d("ScanDebug", "Connection complete for device: " + s);

                    connectionTimeoutHandler.removeCallbacksAndMessages(null);

                    WaitDialog.dismiss();

                    if (mst03Entity == null || !s.equals(mst03Entity.getMacAddress())) {
                        Log.w("ScanDebug", "Connection complete for different device or device is null");
                        return;
                    }

                    // Check if activity is already launched to prevent double launch
                    if (isActivityLaunched) {
                        Log.d("ScanDebug", "Activity already launched, skipping duplicate launch");
                        return;
                    }

                    int batteryLevel = -1;
                    String firmwareVersion = "Unknown";
                    float currentTemperature = Float.NaN;

                    if (mst03Entity != null) {
                        DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) mst03Entity
                                .getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME);
                        if (deviceInfo != null) {
                            batteryLevel = deviceInfo.getBattery();
                            firmwareVersion = deviceInfo.getFirmwareVersion();
                            currentDeviceStaticInfo = deviceInfo;
                            Log.d("BeaconData", "[ConnectComplete] MAC: " + mst03Entity.getMacAddress()
                                    + ", DeviceInfo: " + deviceInfo.toString());
                        } else {
                            Log.d("BeaconData",
                                    "[ConnectComplete] MAC: " + mst03Entity.getMacAddress() + ", DeviceInfo: null");
                        }

                        CombinationFrame comboFrame = (CombinationFrame) mst03Entity
                                .getMinewFrame(FrameType.COMBINATION_FRAME);
                        if (comboFrame != null) {
                            currentTemperature = comboFrame.getTemperature();
                            Log.d("BeaconData", "[ConnectComplete] MAC: " + mst03Entity.getMacAddress()
                                    + ", CombinationFrame: " + comboFrame.toString());
                        } else {
                            Log.d("BeaconData", "[ConnectComplete] MAC: " + mst03Entity.getMacAddress()
                                    + ", CombinationFrame: null");
                        }
                    }

                    startDataFetchingAndLaunchActivity(batteryLevel, firmwareVersion, currentTemperature);
                    break;
                case Disconnect:
                    Log.d("ScanDebug", "Device disconnected: " + s);
                    connectionTimeoutHandler.removeCallbacksAndMessages(null);

                    if (mst03Entity != null && s.equals(mst03Entity.getMacAddress()) && isConnecting) {
                        WaitDialog.dismiss();

                        isDataProcessingComplete = true;
                        updateProcessingStatus("Disconnected");

                        runOnUiThread(() -> {
                            Toast.makeText(ScanDevicesListActivity.this, "Device disconnected unexpectedly.",
                                    Toast.LENGTH_LONG).show();
                            isConnecting = false;
                        });
                    } else {

                        Log.d("ScanDebug", "Device disconnected, restarting scan service");
                        isConnecting = false;

                        if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                            hideDeviceDetailsCard();
                        }

                        startBackgroundScanService();
                        Log.d("ScanDebug", "Called startBackgroundScanService() after disconnect");
                    }
                    break;

                default:

                    break;
            }
        }
    };

    private void initBlePermission() {
        String[] requestPermissionList;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionList = new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION };
        } else {
            requestPermissionList = new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION };
        }
        PermissionX.init(this).permissions(requestPermissionList)
                .onExplainRequestReason(new ExplainReasonCallback() {
                    @Override
                    public void onExplainReason(@NonNull ExplainScope scope, @NonNull List<String> deniedList) {
                        scope.showRequestReasonDialog(deniedList, getString(R.string.need_permission_continue), "Ok",
                                "Cancel");
                    }
                })
                .onForwardToSettings(new ForwardToSettingsCallback() {
                    @Override
                    public void onForwardToSettings(@NonNull ForwardScope scope, @NonNull List<String> deniedList) {
                        scope.showForwardToSettingsDialog(deniedList, getString(R.string.allow_permission_in_settings),
                                "Ok", "Cancel");
                    }
                })
                .request(new RequestCallback() {
                    @Override
                    public void onResult(boolean allGranted, @NonNull List<String> grantedList,
                            @NonNull List<String> deniedList) {
                        permissionsGranted = allGranted;
                        if (allGranted) {

                            initAnimator();
                            initBleManager();
                            checkoutBluetooth();

                            startBackgroundScanService();
                        } else {
                            Toast.makeText(ScanDevicesListActivity.this,
                                    "The following permissions are denied. BLE scanning will not work.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void checkoutBluetooth() {

        switch (BLETool.checkBluetooth(this)) {
            case BLE_NOT_SUPPORT:

                Toast.makeText(this, "Not Support BLE", Toast.LENGTH_SHORT).show();
                break;
            case BLUETOOTH_ON:

                startScan();
                break;
            case BLUETOOTH_OFF:

                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, 4);
                break;
        }

    }

    private void startScan() {
        Log.d("ScanOptimization", "startScan called - permissionsGranted: " + permissionsGranted +
                ", mObjectAnimator: " + (mObjectAnimator != null) + ", isInitialScan: " + isInitialScan);

        if (mObjectAnimator != null) {
            mObjectAnimator.start();
            Log.d("ScanOptimization", "Animation started");
        } else {
            Log.w("ScanOptimization", "mObjectAnimator is null, cannot start animation");
        }

        if (isBackgroundServiceRunning()) {
            Log.d("ScanOptimization", "Background service already running");
        } else {
            Log.d("ScanOptimization", "Starting background scan service");
            startBackgroundScanService();
        }

        if (permissionsGranted && deviceManager != null) {
            Log.d("ScanOptimization", "Device discovery ready - waiting for broadcast data");
        } else {
            Log.w("ScanOptimization", "Cannot start device discovery - permissions: " + permissionsGranted +
                    ", deviceManager: " + (deviceManager != null));
        }
    }

    private void updateAllDiscoveredDevices(List<MST03Entity> newDevices) {
        boolean needsSorting = false;
        boolean hasAnyExcursions = false;

        java.util.Map<String, Integer> existingDevicesIndexMap = new java.util.HashMap<>();
        for (int i = 0; i < allDiscoveredDevices.size(); i++) {
            existingDevicesIndexMap.put(allDiscoveredDevices.get(i).getMacAddress(), i);
        }

        for (MST03Entity newDevice : newDevices) {
            String macAddress = newDevice.getMacAddress();

            boolean hasValidTemperature = false;
            float newTemperature = Float.NaN;

            if (newDevice.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME) != null) {
                com.minew.ble.mst03.frames.CombinationFrame comboFrame = (com.minew.ble.mst03.frames.CombinationFrame) newDevice
                        .getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
                newTemperature = comboFrame.getTemperature();
                hasValidTemperature = !Float.isNaN(newTemperature) && newTemperature != 0.0f;

                if (hasValidTemperature) {

                    cacheTemperature(macAddress, newTemperature);
                }
            }

            if (existingDevicesIndexMap.containsKey(macAddress)) {
                int index = existingDevicesIndexMap.get(macAddress);
                MST03Entity oldDevice = allDiscoveredDevices.get(index);

                boolean oldHasExcursion = hasExcursion(oldDevice);
                boolean newHasExcursion = hasExcursion(newDevice);

                if (oldHasExcursion != newHasExcursion) {
                    needsSorting = true;
                    Log.d("ScanOptimization", "Excursion status changed for " + macAddress +
                            ": " + oldHasExcursion + " -> " + newHasExcursion);
                }
                if (newHasExcursion) {
                    hasAnyExcursions = true;
                }

                if (!hasValidTemperature) {
                    Float cachedTemperature = getCachedTemperature(macAddress);
                    if (cachedTemperature != null) {
                        Log.d("ScanDebug",
                                "Preserving cached temperature for " + macAddress + ": " + cachedTemperature + "°C");

                    }
                }

                allDiscoveredDevices.set(index, newDevice);
            } else {
                boolean newHasExcursion = hasExcursion(newDevice);
                if (newHasExcursion) {
                    needsSorting = true;
                    hasAnyExcursions = true;
                    Log.d("ScanOptimization", "New device with excursion: " + macAddress);
                }
                allDiscoveredDevices.add(newDevice);
            }
        }

        if (hasAnyExcursions) {
            needsSorting = true;
            Log.d("ScanOptimization", "Forcing sort because excursions detected");
        }

        if (needsSorting) {
            allDiscoveredDevices.sort(new Comparator<MST03Entity>() {
                @Override
                public int compare(MST03Entity o1, MST03Entity o2) {
                    boolean o1HasExcursion = hasExcursion(o1);
                    boolean o2HasExcursion = hasExcursion(o2);

                    if (o1HasExcursion && !o2HasExcursion) {
                        return -1;
                    } else if (!o1HasExcursion && o2HasExcursion) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            });
            Log.d("ScanOptimization", "Sorted devices due to excursion status change or excursions detected");
        }
    }

    private boolean hasExcursion(MST03Entity device) {
        if (device.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME) != null) {
            com.minew.ble.mst03.frames.CombinationFrame combinationFrame = (com.minew.ble.mst03.frames.CombinationFrame) device
                    .getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
            float temperature = combinationFrame.getTemperature();
            boolean hasExcursion = !Float.isNaN(temperature) && temperature != 0.0f &&
                    (temperature > 8.0f || temperature < 2.0f);
            Log.d("ScanSort", "Device " + device.getMacAddress() +
                    " - Temp: " + temperature + "°C, Has Excursion: " + hasExcursion);
            return hasExcursion;
        }
        Log.d("ScanSort", "Device " + device.getMacAddress() + " - No CombinationFrame, Has Excursion: false");
        return false;
    }

    private void stopScan() {
        if (mObjectAnimator != null) {
            mObjectAnimator.cancel();
        }
    }

    private boolean isBackgroundServiceRunning() {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (BackgroundScanService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void setKey(String mac) {
        String key = "minewtech1234567";
        mBleManager.setSecretKey(mac, key);
    }

    private void connectedSensor() {

        if (mst03Entity == null) {
            Log.e("ScanDebug", "Device is null, cannot connect");
            Toast.makeText(this, "Device not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME) != null) {
            currentDeviceStaticInfo = (DeviceStaticInfoFrame) mst03Entity
                    .getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME);
        }

        fetchHistoricalDataForLocalDisplay();
    }

    private void fetchHistoricalDataForLocalDisplay() {
        Log.d("ScanDebug", "Starting historical data fetch");
        
        // Add a small delay to ensure device is fully ready
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                performHistoricalDataQuery();
            }
        }, 500); // Reduced from 1000ms to 500ms
    }

    private void performHistoricalDataQuery() {
        try {
            Log.d("ScanDebug", "Performing historical data query");
            
            isDataProcessingComplete = false;
            updateProcessingStatus("Fetching");
            
            if (mst03Entity == null || mBleManager == null) {
                Log.e("ScanDebug", "Device or BLE manager is null, cannot query data");
                handleNoDataAvailable();
                return;
            }

            long systemTime = System.currentTimeMillis() / 1000;
            long startTime = systemTime - (24 * 60 * 60);
            long endTime = systemTime;

            Log.d("ScanDebug", "Querying data for device: " + mst03Entity.getMacAddress() +
                    ", time range: " + startTime + " to " + endTime);

            mBleManager.queryHistoryData(mst03Entity.getMacAddress(), 1, startTime, endTime, systemTime,
                    new OnQueryResultListener<HistoryHtData>() {
                        @Override
                        public void OnQueryResult(boolean success, HistoryHtData historyHtData) {
                            Log.d("ScanDebug", "Query result - success: " + success + ", data: " + 
                                    (historyHtData != null ? historyHtData.getHistoryDataList().size() : "null"));
                            
                            if (success && historyHtData != null && !historyHtData.getHistoryDataList().isEmpty()) {
                                Log.d("ScanDebug", "Historical data received: " + historyHtData.getHistoryDataList().size() + " records");
                                processHistoricalDataForLocalDisplayUltraOptimized(historyHtData.getHistoryDataList());
                            } else {
                                Log.w("ScanDebug", "Primary query failed or returned no data, trying fallback");
                                tryFallbackQuery(systemTime);
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e("ScanDebug", "Error in performHistoricalDataQuery: " + e.getMessage());
            e.printStackTrace();
            handleNoDataAvailable();
        }
    }

    private void tryFallbackQuery(long systemTime) {
        Log.d("BeaconRawData", "tryFallbackQuery called");

        if (mst03Entity == null || mBleManager == null) {
            Log.e("BeaconRawData", "Device or BLE manager is null in fallback query");
            handleNoDataAvailable();
            return;
        }

        try {

            Log.d("BeaconRawData", "Attempting fallback query with different parameters");

            mBleManager.queryHistoryData(mst03Entity.getMacAddress(), 0, 0, 0, systemTime,
                    new OnQueryResultListener<HistoryHtData>() {
                        @Override
                        public void OnQueryResult(boolean fallbackSuccess, HistoryHtData fallbackHistoryHtData) {
                            Log.d("BeaconRawData", "Fallback OnQueryResult called, success=" + fallbackSuccess
                                    + ", historyHtData=" + fallbackHistoryHtData);

                            if (fallbackSuccess && fallbackHistoryHtData != null) {
                                List<HtData> fallbackData = fallbackHistoryHtData.getHistoryDataList();
                                Log.d("BeaconRawData",
                                        "Fallback query returned " + fallbackData.size() + " data points");

                                if (fallbackData.isEmpty()) {
                                    Log.w("BeaconRawData", "Fallback query also returned no data");

                                    tryAlternativeQuery(systemTime);
                                } else {
                                    Log.d("BeaconRawData", "Fallback query successful, processing data");
                                    processHistoricalDataForLocalDisplayUltraOptimized(fallbackData);
                                }
                            } else {
                                Log.w("BeaconRawData", "Fallback query failed, trying alternative");
                                tryAlternativeQuery(systemTime);
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e("BeaconRawData", "Exception during fallback query: " + e.getMessage());
            e.printStackTrace();
            tryAlternativeQuery(systemTime);
        }
    }

    private void tryAlternativeQuery(long systemTime) {
        Log.d("BeaconRawData", "tryAlternativeQuery called");

        if (mst03Entity == null || mBleManager == null) {
            Log.e("BeaconRawData", "Device or BLE manager is null in alternative query");
            handleNoDataAvailable();
            return;
        }

        try {

            long startTime = systemTime - (7 * 24 * 60 * 60);
            long endTime = systemTime;

            Log.d("BeaconRawData", "Trying alternative query with 7-day range: " + startTime + " to " + endTime);

            mBleManager.queryHistoryData(mst03Entity.getMacAddress(), 1, startTime, endTime, systemTime,
                    new OnQueryResultListener<HistoryHtData>() {
                        @Override
                        public void OnQueryResult(boolean alternativeSuccess, HistoryHtData alternativeHistoryHtData) {
                            Log.d("BeaconRawData", "Alternative OnQueryResult called, success=" + alternativeSuccess
                                    + ", historyHtData=" + alternativeHistoryHtData);

                            if (alternativeSuccess && alternativeHistoryHtData != null) {
                                List<HtData> alternativeData = alternativeHistoryHtData.getHistoryDataList();
                                Log.d("BeaconRawData",
                                        "Alternative query returned " + alternativeData.size() + " data points");

                                if (alternativeData.isEmpty()) {
                                    Log.w("BeaconRawData", "All query attempts failed, no data available");
                                    handleNoDataAvailable();
                                } else {
                                    Log.d("BeaconRawData", "Alternative query successful, processing data");
                                    processHistoricalDataForLocalDisplayUltraOptimized(alternativeData);
                                }
                            } else {
                                Log.w("BeaconRawData", "Alternative query also failed");
                                handleNoDataAvailable();
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e("BeaconRawData", "Exception during alternative query: " + e.getMessage());
            e.printStackTrace();
            handleNoDataAvailable();
        }
    }

    private void handleNoDataAvailable() {
        Log.w("ScanDebug", "No historical data available for device: " + 
                (mst03Entity != null ? mst03Entity.getMacAddress() : "null"));
        
        // Ensure we have some basic data even if no historical data
        synchronized (processedHistoricalData) {
            if (processedHistoricalData.isEmpty()) {
                Log.d("ScanDebug", "Setting empty processed data to prevent null issues");
                // Don't add empty data, just ensure the list exists
            }
        }
        
        updateProcessingStatus("Completed - No Data");
        isDataProcessingComplete = true;
        Log.d("ScanDebug", "Data processing marked as complete (no data available)");
        
        // Provide user feedback
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ScanDevicesListActivity.this, 
                        "No historical data found. The device may be new or have no recorded data.", 
                        Toast.LENGTH_LONG).show();
            }
        });
        
        // Don't disconnect immediately, let the activity launch with no data
        Log.d("ScanDebug", "Allowing activity to launch with no data");
    }

    private void processHistoricalDataForLocalDisplayUltraOptimized(List<HtData> htDataList) {
        try {
            if (htDataList == null || htDataList.isEmpty()) {
                Log.d("BeaconRawData", "No data to process");
                isDataProcessingComplete = true;
                updateProcessingStatus("Completed - No Data");
                cleanupDeviceConnection();
                return;
            }

            updateProcessingStatus("Fetch Completed");

            // Store the complete dataset for CSV generation
            synchronized (completeHistoricalData) {
                completeHistoricalData.clear();
                completeHistoricalData.addAll(htDataList);
            }
            
            Log.d("BeaconRawData", "Stored complete dataset with " + htDataList.size() + " records for CSV");

            // Process the data and ensure completion flag is set
            processHistoricalDataForTripDetectionAdaptive(htDataList);
            
            // Ensure completion flag is set even if processing doesn't set it
            if (!isDataProcessingComplete) {
                Log.d("BeaconRawData", "Data processing completed, setting completion flag");
                isDataProcessingComplete = true;
                updateProcessingStatus("Completed");
                cleanupDeviceConnection();
            }
        } catch (Exception e) {
            Log.e("BeaconRawData", "Error processing historical data: " + e.getMessage());
            e.printStackTrace();
            // Ensure completion flag is set even on error
            isDataProcessingComplete = true;
            updateProcessingStatus("Error");
            cleanupDeviceConnection();
        }
    }

    private void storeProcessedData(List<HtData> filteredData, List<ExcursionData> excursions) {
        synchronized (processedHistoricalData) {
            processedHistoricalData.clear();
            processedHistoricalData.addAll(filteredData);
        }

        synchronized (processedExcursionData) {
            processedExcursionData.clear();
            processedExcursionData.addAll(excursions);
        }

        isDataProcessingComplete = true;
        cleanupDeviceConnection();
    }

    private void cleanupDeviceConnection() {
        if (mst03Entity != null && mBleManager != null) {
            mBleManager.disConnect(mst03Entity.getMacAddress());
            isConnecting = false;
        }
    }

    public static List<HtData> getProcessedHistoricalData() {
        return processedHistoricalData;
    }

    public static List<ExcursionData> getProcessedExcursionData() {
        return processedExcursionData;
    }

    public static boolean isDataProcessingComplete() {
        return isDataProcessingComplete;
    }

    public static String getCurrentDeviceMac() {
        return currentDeviceMac;
    }
    
    public static List<HtData> getCompleteHistoricalData() {
        synchronized (completeHistoricalData) {
            return new ArrayList<>(completeHistoricalData);
        }
    }

    public boolean isBleManagerReadyForConnection() {
        return isBleManagerReady && mBleManager != null;
    }

    private void cacheTemperature(String macAddress, float temperature) {
        temperatureCache.put(macAddress, temperature);
        temperatureTimestampCache.put(macAddress, System.currentTimeMillis());
        Log.d("ScanDebug", "Cached temperature for " + macAddress + ": " + temperature + "°C");
    }

    public Float getCachedTemperature(String macAddress) {
        Long timestamp = temperatureTimestampCache.get(macAddress);
        if (timestamp != null) {
            long age = System.currentTimeMillis() - timestamp;
            if (age < TEMPERATURE_CACHE_TIMEOUT) {
                Float temperature = temperatureCache.get(macAddress);
                Log.d("ScanDebug",
                        "Using cached temperature for " + macAddress + ": " + temperature + "°C (age: " + age + "ms)");
                return temperature;
            } else {

                temperatureCache.remove(macAddress);
                temperatureTimestampCache.remove(macAddress);
                Log.d("ScanDebug", "Cached temperature expired for " + macAddress + " (age: " + age + "ms)");
            }
        }
        return null;
    }

    private void clearTemperatureCache() {
        temperatureCache.clear();
        temperatureTimestampCache.clear();
        Log.d("ScanDebug", "Temperature cache cleared");
    }

    public static void clearProcessedData() {
        processedHistoricalData.clear();
        processedExcursionData.clear();
        completeHistoricalData.clear();
        isDataProcessingComplete = false;
        currentProcessingStatus = "Idle";
        currentDeviceMac = "";
        Log.d("BeaconRawData", "Processed data cleared");
    }
    
    private void resetConnectionState() {
        isConnecting = false;
        isActivityLaunched = false;
        Log.d("ScanDebug", "Connection state reset");
    }

    private void hideDeviceDetailsCard() {
        binding.deviceDetailsCard.setVisibility(View.GONE);

        connectionTimeoutHandler.removeCallbacksAndMessages(null);

        if (mst03Entity != null && mBleManager != null) {
            mBleManager.disConnect(mst03Entity.getMacAddress());
        }

        mst03Entity = null;
        currentDeviceStaticInfo = null;
        excursionDataList.clear();

        isConnecting = false;
    }

    private void setupAppId() {
        InstallationIdManager installationIdManager = InstallationIdManager.getInstance(this);
        TextView tvScanAppId = findViewById(R.id.tv_scan_app_id);
        if (tvScanAppId != null) {
            tvScanAppId.setText("App ID: " + installationIdManager.getInstallationId());
        }
    }
    
    private void loadChorusLogo() {
        try {

            InputStream inputStream = getAssets().open("images/chorus.png");
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            ivChorusLogo.setImageBitmap(bitmap);
            inputStream.close();
        } catch (IOException e) {
            try {

                ivChorusLogo.setImageResource(R.drawable.ic_chorus_logo);
            } catch (Exception ex) {
                Log.e("ScanDevicesListActivity", "Error loading chorus logo", ex);
            }
        }
    }

    private void processHistoricalDataForTripDetectionOptimized(List<HtData> htDataList) {
        if (htDataList == null || htDataList.isEmpty()) {
            Log.d("BeaconRawData", "No data to process");
            isDataProcessingComplete = true;
            cleanupDeviceConnection();
            return;
        }

        List<HtData> tripData = new ArrayList<>();
        List<ExcursionData> tripExcursions = new ArrayList<>();

        // First check if there's any normal temperature in the complete dataset
        boolean hasNormalTemperature = false;
        for (HtData htData : htDataList) {
            float temperature = htData.getTemperature();
            if (temperature >= 2.0f && temperature <= 8.0f) {
                hasNormalTemperature = true;
                break;
            }
        }

        Log.d("BeaconRawData", "Complete dataset has normal temperature: " + hasNormalTemperature);

        if (!hasNormalTemperature) {
            // No normal temperature found - use the last 1000 records
            int recentSize = Math.min(1000, htDataList.size());
            int startIndex = htDataList.size() - recentSize;
            tripData = htDataList.subList(startIndex, htDataList.size());
            Log.d("BeaconRawData", "No normal temperature found in complete dataset, using recent " + tripData.size() + " records");
            storeProcessedDataOptimized(tripData, tripExcursions);
            return;
        }

        // Normal temperature found - proceed with excursion detection
        int latestExcursionIndex = -1;
        int lastNormalIndex = -1;

        for (int i = htDataList.size() - 1; i >= 0; i--) {
            HtData htData = htDataList.get(i);
            float temperature = htData.getTemperature();
            boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);

            if (!isNormal) {
                latestExcursionIndex = i;
                break;
            }
        }

        if (latestExcursionIndex != -1) {

            for (int i = latestExcursionIndex - 1; i >= 0; i--) {
                HtData htData = htDataList.get(i);
                float temperature = htData.getTemperature();
                boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);

                if (isNormal) {
                    lastNormalIndex = i;
                    break;
                }
            }

            int startIndex = (lastNormalIndex != -1) ? lastNormalIndex : latestExcursionIndex;
            tripData = htDataList.subList(startIndex, htDataList.size());

            for (int i = latestExcursionIndex; i < htDataList.size(); i++) {
                HtData htData = htDataList.get(i);
                float temperature = htData.getTemperature();
                if (temperature < 2.0f || temperature > 8.0f) {
                    tripExcursions.add(new ExcursionData(temperature, htData.getTimestamps(),
                            temperature < 2.0f ? "LOW" : "HIGH", mst03Entity.getMacAddress()));
                }
            }

            long tripDurationMinutes = tripData.isEmpty() ? 0
                    : (tripData.get(tripData.size() - 1).getTimestamps() - tripData.get(0).getTimestamps())
                            / (1000 * 60);

            String startPoint = (lastNormalIndex != -1) ? "last normal point (index " + lastNormalIndex + ")"
                    : "excursion start (index " + latestExcursionIndex + ")";
            Log.d("BeaconRawData",
                    "Optimized trip detected: " + tripData.size() + " records starting from " + startPoint +
                            " to " + (htDataList.size() - 1) + " with " + tripExcursions.size()
                            + " excursions. Duration: " + tripDurationMinutes + " minutes");
        } else {
            // No excursion found but normal temperature exists - use recent data
            int startIndex = Math.max(0, htDataList.size() - 1000);
            tripData = htDataList.subList(startIndex, htDataList.size());
            Log.d("BeaconRawData", "No excursion trip found, using recent " + tripData.size() + " records");
        }

        storeProcessedDataOptimized(tripData, tripExcursions);
    }

    private boolean hasRecentExcursions(List<HtData> htDataList) {

        int checkSize = Math.min(100, htDataList.size());
        for (int i = htDataList.size() - 1; i >= htDataList.size() - checkSize; i--) {
            float temperature = htDataList.get(i).getTemperature();
            if (temperature < 2.0f || temperature > 8.0f) {
                return true;
            }
        }
        return false;
    }

    private int findExcursionStartBinarySearch(List<HtData> htDataList) {
        int left = 0;
        int right = htDataList.size() - 1;
        int firstExcursionStart = -1;

        while (left <= right) {
            int mid = left + (right - left) / 2;
            float temperature = htDataList.get(mid).getTemperature();

            if (temperature < 2.0f || temperature > 8.0f) {

                firstExcursionStart = findFirstExcursionBackwards(htDataList, mid);
                break;
            } else {

                left = mid + 1;
            }
        }

        return firstExcursionStart;
    }

    private int findFirstExcursionBackwards(List<HtData> htDataList, int excursionPoint) {
        boolean wasInNormalRange = false;

        for (int i = excursionPoint; i >= 0; i--) {
            float temperature = htDataList.get(i).getTemperature();
            boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);

            if (wasInNormalRange && !isNormal) {
                return i;
            }

            wasInNormalRange = isNormal;
        }

        return 0;
    }

    private int findExcursionStartWithEarlyTermination(List<HtData> htDataList) {
        int firstExcursionStartIndex = -1;
        boolean wasInNormalRange = false;
        int consecutiveNormalReadings = 0;
        final int EARLY_TERMINATION_THRESHOLD = 200;

        for (int i = htDataList.size() - 1; i >= 0; i--) {
            HtData htData = htDataList.get(i);
            float temperature = htData.getTemperature();

            boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);

            if (isNormal) {
                consecutiveNormalReadings++;
                if (consecutiveNormalReadings >= EARLY_TERMINATION_THRESHOLD) {

                    break;
                }
            } else {
                consecutiveNormalReadings = 0;
            }

            if (wasInNormalRange && !isNormal) {
                firstExcursionStartIndex = i;
                break;
            }

            wasInNormalRange = isNormal;
        }

        return firstExcursionStartIndex;
    }

    private void extractTripDataOptimized(List<HtData> htDataList, int firstExcursionStartIndex) {
        List<HtData> tripData;
        List<ExcursionData> tripExcursions = new ArrayList<>();

        if (firstExcursionStartIndex != -1) {

            int lastNormalIndex = -1;
            for (int i = firstExcursionStartIndex - 1; i >= 0; i--) {
                HtData htData = htDataList.get(i);
                float temperature = htData.getTemperature();
                boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);

                if (isNormal) {
                    lastNormalIndex = i;
                    break;
                }
            }

            int startIndex = (lastNormalIndex != -1) ? lastNormalIndex : firstExcursionStartIndex;
            tripData = htDataList.subList(startIndex, htDataList.size());

            for (int i = firstExcursionStartIndex; i < htDataList.size(); i++) {
                HtData htData = htDataList.get(i);
                float temperature = htData.getTemperature();
                if (temperature < 2.0f || temperature > 8.0f) {
                    tripExcursions.add(new ExcursionData(temperature, htData.getTimestamps(),
                            temperature < 2.0f ? "LOW" : "HIGH", mst03Entity.getMacAddress()));
                }
            }

            String startPoint = (lastNormalIndex != -1) ? "last normal point (index " + lastNormalIndex + ")"
                    : "excursion start (index " + firstExcursionStartIndex + ")";
            Log.d("BeaconRawData",
                    "Optimized trip detected: " + tripData.size() + " records starting from " + startPoint +
                            " with " + tripExcursions.size() + " excursions");
        } else {

            int recentSize = Math.min(1000, htDataList.size());
            int startIndex = htDataList.size() - recentSize;
            tripData = htDataList.subList(startIndex, htDataList.size());
            Log.d("BeaconRawData", "No excursion trip found, using recent " + tripData.size() + " records");
            Log.d("BeaconRawData", "Original data size: " + htDataList.size() + ", Recent size: " + recentSize + ", Start index: " + startIndex);
            if (!tripData.isEmpty()) {
                Log.d("BeaconRawData", "First trip record timestamp: " + tripData.get(0).getTimestamps() + ", temp: " + tripData.get(0).getTemperature());
                Log.d("BeaconRawData", "Last trip record timestamp: " + tripData.get(tripData.size() - 1).getTimestamps() + ", temp: " + tripData.get(tripData.size() - 1).getTemperature());
            }
        }

        storeProcessedDataOptimized(tripData, tripExcursions);
    }

    private void storeProcessedDataOptimized(List<HtData> tripData, List<ExcursionData> tripExcursions) {
        synchronized (processedHistoricalData) {
            processedHistoricalData.clear();
            processedHistoricalData.addAll(tripData);
        }

        synchronized (processedExcursionData) {
            processedExcursionData.clear();
            processedExcursionData.addAll(tripExcursions);
        }

        currentDeviceMac = mst03Entity != null ? mst03Entity.getMacAddress() : "";

        Log.d("BeaconRawData", "Stored processed data: " + tripData.size() + " historical records, " +
                tripExcursions.size() + " excursions for device: " + currentDeviceMac);

        generateAllCSVFiles(tripData, tripExcursions);

        isDataProcessingComplete = true;
        updateProcessingStatus("Completed");
        Log.d("BeaconRawData", "Data processing marked as complete");
        cleanupDeviceConnection();
    }

    private void generateTripDataCSV(List<HtData> tripData, List<ExcursionData> tripExcursions) {
        try {

            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            String deviceMac = mst03Entity != null ? mst03Entity.getMacAddress().replace(":", "_") : "unknown_device";
            String fileName = "trip_data_" + deviceMac + "_" + timestamp + ".csv";

            java.io.File csvFile = saveToDownloadsDirectory(fileName);
            if (csvFile == null) {

                java.io.File externalDir = getExternalFilesDir(null);
                if (externalDir == null) {
                    Log.e("BeaconRawData", "No accessible directory available");
                    return;
                }
                csvFile = new java.io.File(externalDir, fileName);
            }

            java.io.FileWriter writer = new java.io.FileWriter(csvFile);
            java.io.BufferedWriter bufferedWriter = new java.io.BufferedWriter(writer);

            bufferedWriter.write(
                    "Record Number,Unix Timestamp,Date & Time,Temperature (°C),Humidity (%),Temperature Status,Excursion Type,Device MAC Address,Trip Duration (minutes),Notes\n");

            long tripStartTime = tripData.isEmpty() ? 0 : tripData.get(0).getTimestamps();
            long tripEndTime = tripData.isEmpty() ? 0 : tripData.get(tripData.size() - 1).getTimestamps();
            long tripDurationMinutes = tripData.isEmpty() ? 0 : (tripEndTime - tripStartTime) / (1000 * 60);

            for (int i = 0; i < tripData.size(); i++) {
                HtData htData = tripData.get(i);
                long timestamp_ms = htData.getTimestamps();

                String dateTime = getLocaleAwareCSVDateFormat().format(new java.util.Date(timestamp_ms));

                float temperature = htData.getTemperature();
                float humidity = htData.getHumidity();

                String temperatureStatus;
                String excursionType;
                String notes = "";

                if (temperature < 2.0f) {
                    temperatureStatus = "LOW TEMPERATURE";
                    excursionType = "LOW";
                    notes = "Temperature below safe range (2°C)";
                } else if (temperature > 8.0f) {
                    temperatureStatus = "HIGH TEMPERATURE";
                    excursionType = "HIGH";
                    notes = "Temperature above safe range (8°C)";
                } else {
                    temperatureStatus = "NORMAL";
                    excursionType = "NORMAL";
                    notes = "Temperature within safe range (2-8°C)";
                }

                String deviceMacAddress = mst03Entity != null ? mst03Entity.getMacAddress() : "unknown";

                long timeFromStart = tripData.isEmpty() ? 0 : (timestamp_ms - tripStartTime) / (1000 * 60);

                bufferedWriter.write(String.format("%d,%d,%s,%.2f,%.2f,%s,%s,%s,%d,%s\n",
                        i + 1,
                        timestamp_ms,
                        dateTime,
                        temperature,
                        humidity,
                        temperatureStatus,
                        excursionType,
                        deviceMacAddress,
                        timeFromStart,
                        notes));
            }

            bufferedWriter.close();
            writer.close();

            Log.d("BeaconRawData", "Comprehensive CSV file generated: " + csvFile.getAbsolutePath());
            Log.d("BeaconRawData",
                    "Trip data: " + tripData.size() + " records, " + tripExcursions.size() + " excursions");
            Log.d("BeaconRawData", "Trip duration: " + tripDurationMinutes + " minutes");

            final String finalFileName = fileName;
            final String fileParent = csvFile.getParent();
        } catch (Exception e) {
            Log.e("BeaconRawData", "Error generating CSV: " + e.getMessage());
            e.printStackTrace();

            runOnUiThread(() -> {
                Toast.makeText(this, "Error generating CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void processHistoricalDataForTripDetectionParallel(List<HtData> htDataList) {
        if (htDataList == null || htDataList.isEmpty()) {
            Log.d("BeaconRawData", "No data to process");
            isDataProcessingComplete = true;
            cleanupDeviceConnection();
            return;
        }

        List<HtData> tripData = new ArrayList<>();
        List<ExcursionData> tripExcursions = new ArrayList<>();

        int latestExcursionIndex = -1;
        int lastNormalIndex = -1;

        for (int i = htDataList.size() - 1; i >= 0; i--) {
            HtData htData = htDataList.get(i);
            float temperature = htData.getTemperature();
            boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);

            if (!isNormal) {
                latestExcursionIndex = i;
                break;
            }
        }

        if (latestExcursionIndex != -1) {

            for (int i = latestExcursionIndex - 1; i >= 0; i--) {
                HtData htData = htDataList.get(i);
                float temperature = htData.getTemperature();
                boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);

                if (isNormal) {
                    lastNormalIndex = i;
                    break;
                }
            }

            int startIndex = (lastNormalIndex != -1) ? lastNormalIndex : latestExcursionIndex;
            tripData = htDataList.subList(startIndex, htDataList.size());

            for (int i = latestExcursionIndex; i < htDataList.size(); i++) {
                HtData htData = htDataList.get(i);
                float temperature = htData.getTemperature();
                if (temperature < 2.0f || temperature > 8.0f) {
                    tripExcursions.add(new ExcursionData(temperature, htData.getTimestamps(),
                            temperature < 2.0f ? "LOW" : "HIGH", mst03Entity.getMacAddress()));
                }
            }

            long tripDurationMinutes = tripData.isEmpty() ? 0
                    : (tripData.get(tripData.size() - 1).getTimestamps() - tripData.get(0).getTimestamps())
                            / (1000 * 60);

            String startPoint = (lastNormalIndex != -1) ? "last normal point (index " + lastNormalIndex + ")"
                    : "excursion start (index " + latestExcursionIndex + ")";
            Log.d("BeaconRawData",
                    "Parallel trip detected: " + tripData.size() + " records starting from " + startPoint +
                            " to " + (htDataList.size() - 1) + " with " + tripExcursions.size()
                            + " excursions. Duration: " + tripDurationMinutes + " minutes");
        } else {

            int startIndex = Math.max(0, htDataList.size() - 1000);
            tripData = htDataList.subList(startIndex, htDataList.size());
            Log.d("BeaconRawData", "No excursion trip found, using recent " + tripData.size() + " records");
        }

        storeProcessedDataOptimized(tripData, tripExcursions);
    }

    private void processHistoricalDataForTripDetectionAdaptive(List<HtData> htDataList) {
        if (htDataList == null || htDataList.isEmpty()) {
            Log.d("BeaconRawData", "No data to process");
            isDataProcessingComplete = true;
            cleanupDeviceConnection();
            return;
        }

        int dataSize = htDataList.size();

        if (dataSize > 50000) {
            Log.d("BeaconRawData", "Using parallel processing for " + dataSize + " records");
            processHistoricalDataForTripDetectionParallel(htDataList);
        } else if (dataSize > 10000) {
            Log.d("BeaconRawData", "Using optimized approach for " + dataSize + " records");
            processHistoricalDataForTripDetectionOptimized(htDataList);
        } else if (dataSize > 1000) {
            Log.d("BeaconRawData", "Using early termination for " + dataSize + " records");
            processHistoricalDataForTripDetectionOptimized(htDataList);
        } else {
            Log.d("BeaconRawData", "Using simple approach for " + dataSize + " records");
            processHistoricalDataForTripDetectionSimple(htDataList);
        }
    }

    private void processHistoricalDataForTripDetectionSimple(List<HtData> htDataList) {
        if (htDataList == null || htDataList.isEmpty()) {
            Log.d("BeaconRawData", "No data to process");
            isDataProcessingComplete = true;
            cleanupDeviceConnection();
            return;
        }

        List<HtData> tripData = new ArrayList<>();
        List<ExcursionData> tripExcursions = new ArrayList<>();

        int latestExcursionIndex = -1;
        int lastNormalIndex = -1;

        for (int i = htDataList.size() - 1; i >= 0; i--) {
            HtData htData = htDataList.get(i);
            float temperature = htData.getTemperature();
            boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);

            if (!isNormal) {
                latestExcursionIndex = i;
                break;
            }
        }

        if (latestExcursionIndex != -1) {

            for (int i = latestExcursionIndex - 1; i >= 0; i--) {
                HtData htData = htDataList.get(i);
                float temperature = htData.getTemperature();
                boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);

                if (isNormal) {
                    lastNormalIndex = i;
                    break;
                }
            }

            int startIndex = (lastNormalIndex != -1) ? lastNormalIndex : latestExcursionIndex;
            tripData = htDataList.subList(startIndex, htDataList.size());

            for (int i = latestExcursionIndex; i < htDataList.size(); i++) {
                HtData htData = htDataList.get(i);
                float temperature = htData.getTemperature();
                if (temperature < 2.0f || temperature > 8.0f) {
                    tripExcursions.add(new ExcursionData(temperature, htData.getTimestamps(),
                            temperature < 2.0f ? "LOW" : "HIGH", mst03Entity.getMacAddress()));
                }
            }

            long tripDurationMinutes = tripData.isEmpty() ? 0
                    : (tripData.get(tripData.size() - 1).getTimestamps() - tripData.get(0).getTimestamps())
                            / (1000 * 60);

            String startPoint = (lastNormalIndex != -1) ? "last normal point (index " + lastNormalIndex + ")"
                    : "excursion start (index " + latestExcursionIndex + ")";
            Log.d("BeaconRawData", "Trip detected: " + tripData.size() + " records starting from " + startPoint +
                    " to " + (htDataList.size() - 1) + " with " + tripExcursions.size() + " excursions. Duration: "
                    + tripDurationMinutes + " minutes");
        } else {

            int startIndex = Math.max(0, htDataList.size() - 1000);
            tripData = htDataList.subList(startIndex, htDataList.size());
            Log.d("BeaconRawData", "No excursion trip found, using recent " + tripData.size() + " records");
        }

        storeProcessedDataOptimized(tripData, tripExcursions);
    }

    private void generateAllCSVFiles(List<HtData> tripData, List<ExcursionData> tripExcursions) {
        updateProcessingStatus("Uploading");

        List<HtData> csvData;
        synchronized (completeHistoricalData) {
            csvData = new ArrayList<>(completeHistoricalData);
        }

        Log.d("BeaconRawData", "Generating CSV with complete dataset: " + csvData.size() + " records");

        generateTripDataCSV(csvData, tripExcursions);

        updateProcessingStatus("Completed");

        Log.d("BeaconRawData", "Trip Data CSV file generated successfully");

        runOnUiThread(() -> {
            Toast.makeText(this, "Trip Data CSV generated successfully in Downloads folder", Toast.LENGTH_LONG).show();
        });
    }

    public void generateCSVForCurrentData() {
        List<HtData> csvData;
        synchronized (completeHistoricalData) {
            csvData = new ArrayList<>(completeHistoricalData);
        }

        if (csvData.isEmpty()) {
            Toast.makeText(this, "No complete data available. Please connect to a device first.", Toast.LENGTH_LONG)
                    .show();
            return;
        }

        List<ExcursionData> tripExcursions = new ArrayList<>(processedExcursionData);

        Log.d("BeaconRawData", "Generating CSV with complete dataset: " + csvData.size() + " records");
        generateTripDataCSV(csvData, tripExcursions);
    }

    public List<String> getGeneratedCSVFiles() {
        List<String> csvFiles = new ArrayList<>();

        try {
            java.io.File externalDir = getExternalFilesDir(null);
            if (externalDir != null && externalDir.exists()) {
                java.io.File[] files = externalDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
                if (files != null) {
                    for (java.io.File file : files) {
                        csvFiles.add(file.getName());
                    }
                }
            }
        } catch (Exception e) {
            Log.e("BeaconRawData", "Error getting CSV files: " + e.getMessage());
        }

        return csvFiles;
    }

    public void shareCSVFile(String fileName) {
        try {
            java.io.File externalDir = getExternalFilesDir(null);
            if (externalDir == null) {
                Toast.makeText(this, "External directory not available", Toast.LENGTH_SHORT).show();
                return;
            }

            java.io.File csvFile = new java.io.File(externalDir, fileName);
            if (!csvFile.exists()) {
                Toast.makeText(this, "CSV file not found: " + fileName, Toast.LENGTH_SHORT).show();
                return;
            }

            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    csvFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Trip Data CSV: " + fileName);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share CSV File"));

        } catch (Exception e) {
            Log.e("BeaconRawData", "Error sharing CSV file: " + e.getMessage());
            Toast.makeText(this, "Error sharing CSV file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private java.io.File saveToDownloadsDirectory(String fileName) {
        try {

            if (!hasStoragePermissions()) {
                Log.w("BeaconRawData", "Storage permissions not granted, requesting...");
                requestStoragePermissions();
                return null;
            }

            java.io.File downloadsDir = android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);

            if (downloadsDir != null && downloadsDir.exists() && downloadsDir.canWrite()) {
                java.io.File csvFile = new java.io.File(downloadsDir, fileName);
                Log.d("BeaconRawData", "Saving CSV to Downloads: " + csvFile.getAbsolutePath());
                return csvFile;
            } else {
                Log.w("BeaconRawData", "Downloads directory not accessible, will use fallback");
                return null;
            }
        } catch (Exception e) {
            Log.e("BeaconRawData", "Error accessing Downloads directory: " + e.getMessage());
            return null;
        }
    }

    private boolean hasStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {

            try {
                return android.os.Environment.isExternalStorageManager();
            } catch (Exception e) {
                Log.e("BeaconRawData", "Error checking storage permissions: " + e.getMessage());
                return false;
            }
        } else {

            return checkSelfPermission(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {

            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(
                        android.net.Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                startActivityForResult(intent, 1002);
            } catch (Exception e) {

                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, 1002);
            }
        } else {

            requestPermissions(new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1003);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1003) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d("BeaconRawData", "Storage permission granted");

                if (!processedHistoricalData.isEmpty()) {
                    generateCSVForCurrentData();
                }
            } else {
                Log.w("BeaconRawData", "Storage permission denied, will use app directory");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Storage permission denied. CSV will be saved in app directory.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }
    }

    private static void updateProcessingStatus(String status) {
        currentProcessingStatus = status;
        Log.d("BeaconRawData", "Processing status updated: " + status);
    }

    public static String getCurrentProcessingStatus() {
        return currentProcessingStatus;
    }

    private void startDataFetchingAndLaunchActivity(int batteryLevel, String firmwareVersion,
            float currentTemperature) {
        Log.d("ScanDebug", "Starting data fetching with activity launch");

        // Set flag to prevent double launch
        isActivityLaunched = true;

        WaitDialog.show("");

        // Reduce delay to ensure faster data fetching
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mst03Entity != null) {
                    Log.d("ScanDebug", "Device is ready, proceeding with data fetch");
                    connectedSensor();

                    waitForDataProcessingAndLaunchActivity(batteryLevel, firmwareVersion, currentTemperature);
                } else {
                    Log.w("ScanDebug", "Device is null, launching activity without data");
                    WaitDialog.dismiss();
                    Intent intent = DeviceDetailsActivity.newIntent(ScanDevicesListActivity.this, mst03Entity,
                            batteryLevel, firmwareVersion, currentTemperature);
                    startActivity(intent);
                }
            }
        }, 200); // Reduced from 500ms to 200ms
    }

    private void waitForDataProcessingAndLaunchActivity(int batteryLevel, String firmwareVersion,
            float currentTemperature) {

        final android.os.Handler checkHandler = new android.os.Handler();
        final Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (isDataProcessingComplete) {
                    Log.d("ScanDebug", "Data processing complete, launching DeviceDetailsActivity");
                    WaitDialog.dismiss();

                    Intent intent = DeviceDetailsActivity.newIntent(ScanDevicesListActivity.this, mst03Entity,
                            batteryLevel, firmwareVersion, currentTemperature);
                    startActivity(intent);
                } else {
                    Log.d("ScanDebug", "Data processing not complete yet, waiting...");

                    checkHandler.postDelayed(this, 300); // Reduced from 500ms to 300ms
                }
            }
        };

        // Start checking sooner
        checkHandler.postDelayed(checkRunnable, 1000); // Reduced from 2000ms to 1000ms

        // Reduce timeout to launch activity faster
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkHandler.removeCallbacks(checkRunnable);
                if (!isDataProcessingComplete) {
                    Log.w("ScanDebug", "Data processing timeout, launching activity anyway");
                    WaitDialog.dismiss();

                    Intent intent = DeviceDetailsActivity.newIntent(ScanDevicesListActivity.this, mst03Entity,
                            batteryLevel, firmwareVersion, currentTemperature);
                    startActivity(intent);
                }
            }
        }, 8000); // Reduced from 15000ms to 8000ms
    }

    private java.text.SimpleDateFormat getLocaleAwareCSVDateFormat() {
        // Get the actual date format pattern from device settings
        String datePattern = getDeviceDateFormatPattern();
        Log.d("BeaconRawData", "Using device date pattern for CSV: " + datePattern);
        
        // For CSV, we'll use a standard format that's widely compatible
        // but we can still log the device locale for debugging
        java.util.Locale deviceLocale = java.util.Locale.getDefault();
        Log.d("BeaconRawData", "Using CSV date format for locale: " + deviceLocale.toString());
        
        // Use ISO format for CSV to ensure compatibility across different systems
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", deviceLocale);
    }
    
    private String getDeviceDateFormatPattern() {
        try {
            // Get the current timezone
            java.util.TimeZone timeZone = java.util.TimeZone.getDefault();
            String timeZoneId = timeZone.getID();
            
            // Get the device locale
            java.util.Locale deviceLocale = java.util.Locale.getDefault();
            
            Log.d("BeaconRawData", "Device locale: " + deviceLocale.toString());
            Log.d("BeaconRawData", "Device timezone: " + timeZoneId);
            
            // Determine date format based on timezone and locale
            String pattern = getDateFormatByTimezoneAndLocale(timeZoneId, deviceLocale);
            Log.d("BeaconRawData", "Selected pattern: " + pattern);
            
            return pattern + " HH:mm";
            
        } catch (Exception e) {
            Log.e("BeaconRawData", "Error detecting device date format: " + e.getMessage());
            return "MM/dd/yyyy HH:mm"; // Fallback
        }
    }
    
    private String getDateFormatByTimezoneAndLocale(String timeZoneId, java.util.Locale locale) {
        // US timezones
        if (timeZoneId.startsWith("America/") || timeZoneId.startsWith("US/") || 
            timeZoneId.equals("CST") || timeZoneId.equals("EST") || timeZoneId.equals("PST") ||
            timeZoneId.equals("MST") || timeZoneId.equals("CST6CDT") || timeZoneId.equals("EST5EDT")) {
            Log.d("BeaconRawData", "US timezone detected: " + timeZoneId);
            return "MM/dd/yyyy";
        }
        
        // European timezones
        if (timeZoneId.startsWith("Europe/") || timeZoneId.equals("GMT") || timeZoneId.equals("UTC")) {
            Log.d("BeaconRawData", "European timezone detected: " + timeZoneId);
            return "dd/MM/yyyy";
        }
        
        // Asian timezones
        if (timeZoneId.startsWith("Asia/") || timeZoneId.equals("IST") || timeZoneId.equals("JST") ||
            timeZoneId.equals("KST") || timeZoneId.equals("CST")) {
            Log.d("BeaconRawData", "Asian timezone detected: " + timeZoneId);
            return "dd/MM/yyyy";
        }
        
        // Australian timezones
        if (timeZoneId.startsWith("Australia/") || timeZoneId.equals("AEST") || timeZoneId.equals("AEDT")) {
            Log.d("BeaconRawData", "Australian timezone detected: " + timeZoneId);
            return "dd/MM/yyyy";
        }
        
        // Canadian timezones
        if (timeZoneId.startsWith("Canada/")) {
            Log.d("BeaconRawData", "Canadian timezone detected: " + timeZoneId);
            return "dd/MM/yyyy";
        }
        
        // Fallback to locale-based detection
        String country = locale.getCountry();
        Log.d("BeaconRawData", "Using locale-based detection for country: " + country);
        
        if ("US".equals(country)) {
            return "MM/dd/yyyy";
        } else if ("IN".equals(country)) {
            return "dd/MM/yyyy";
        } else if ("GB".equals(country) || "AU".equals(country) || "CA".equals(country)) {
            return "dd/MM/yyyy";
        } else if ("DE".equals(country) || "AT".equals(country) || "CH".equals(country)) {
            return "dd.MM.yyyy";
        } else {
            return "MM/dd/yyyy"; // Default
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
                            
                            // Restart scanning
                            Log.d("ScanDevicesList", "Bluetooth turned ON - restarting scanning");
                            restartScanningAfterBluetoothOn();
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
                Log.e("ScanDevicesList", "Error registering Bluetooth receiver: " + e.getMessage(), e);
            }
        }
    }

    private void unregisterBluetoothStateReceiver() {
        if (bluetoothStateReceiver != null) {
            try {
                unregisterReceiver(bluetoothStateReceiver);
            } catch (IllegalArgumentException e) {
                Log.w("ScanDevicesList", "Bluetooth receiver not registered");
            } catch (Exception e) {
                Log.e("ScanDevicesList", "Error unregistering Bluetooth receiver: " + e.getMessage(), e);
            }
        }
    }

    private void showBluetoothOffDialog() {
        // Check if activity is finishing or destroyed
        if (isFinishing() || isDestroyed()) {
            Log.w("ScanDevicesList", "Activity is finishing or destroyed, cannot show dialog");
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
            Log.e("ScanDevicesList", "Error showing Bluetooth dialog: " + e.getMessage(), e);
        }
    }
    
    private void restartScanningAfterBluetoothOn() {
        try {
            Log.d("ScanDevicesList", "Restarting scanning after Bluetooth turned ON");
            
            // Clear existing device lists to start fresh
            allDiscoveredDevices.clear();
            filteredDevices.clear();
            
            // Update UI to show empty state
            if (mDevicesListAdapter != null) {
                mDevicesListAdapter.setList(new ArrayList<>());
                mDevicesListAdapter.notifyDataSetChanged();
            }
            
            // Clear temperature cache
            clearTemperatureCache();
            
            // Reinitialize BLE manager
            ensureBleManagerReady();
            
            // Wait a bit for Bluetooth to stabilize
            bleReadyHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing() && !isDestroyed()) {
                        // Clear device discovery manager's internal cache
                        if (deviceManager != null) {
                            deviceManager.removeListener(deviceUpdateListener);
                            deviceManager.clearDevices(); // Clear the internal device cache
                        }
                        
                        // Get device discovery manager instance
                        deviceManager = DeviceDiscoveryManager.getInstance();
                        deviceManager.addListener(deviceUpdateListener);
                        
                        // Restart background scan service to ensure fresh scanning
                        restartBackgroundScanService();
                        
                        // Use the existing checkoutBluetooth method which handles all states correctly
                        checkoutBluetooth();
                        
                        // Show a toast to inform user
                        Toast.makeText(ScanDevicesListActivity.this, 
                            "Bluetooth scanning resumed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, 2000); // Wait 2 seconds for Bluetooth to stabilize
            
        } catch (Exception e) {
            Log.e("ScanDevicesList", "Error restarting scanning: " + e.getMessage(), e);
        }
    }
    
}
