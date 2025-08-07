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
import android.os.Handler;

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
    
    // NEW: Add faster direct scanning for foreground
    private boolean isForegroundScanning = false;
    private static final int FOREGROUND_SCAN_DURATION = 3000; // 3 seconds for quick temperature discovery

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
    protected void onResume() {
        super.onResume();
        setBleManagerListener();
        
        // NEW: Start aggressive foreground scanning for faster temperature data
        startForegroundScan();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        removeBleManagerListener();
        
        // Stop foreground scanning when leaving the screen
        stopForegroundScan();
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
        
        isConnecting = true;
        mst03Entity = device;
        
        // Stop foreground scanning during connection
        stopForegroundScan();
        
        // Disable all connect buttons during connection
        mDevicesListAdapter.setConnectButtonsEnabled(false);
        
        // Show connection dialog
        WaitDialog.show("");
        
        // Start connection with improved timeout handling
        startConnectionWithTimeout(device);
        
        // Ensure BLE manager is ready and listener is set
        ensureBleManagerReady();
        
        // Stop background scan service before connecting
        if (isBackgroundServiceRunning()) {
            stopService(new Intent(this, BackgroundScanService.class));
        }
        
        // Connect to the device
        try {
            if (mBleManager != null) {
                mBleManager.connect(this, device);
                Log.d("ScanDebug", "Connection initiated for device: " + device.getMacAddress());
            } else {
                Log.e("ScanDebug", "BLE manager is null, cannot connect");
                throw new Exception("BLE manager not initialized");
            }
        } catch (Exception e) {
            Log.e("ScanDebug", "Error connecting to device: " + e.getMessage());
            e.printStackTrace();
            handleConnectionError(device, e.getMessage());
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
            
            if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                // Only process events for our target device
            }
            
            switch (mSensorConnectionState) {
                case Connecting:
                    Log.d("TAG","Connecting");
                    updateConnectionStatus("Connecting...");
                    break;
                case Connected:
                    Log.d("TAG","Connected");
                    updateConnectionStatus("Connected - Authenticating...");
                    
                    // Set the secret key immediately when connected (required for authentication)
                    if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                        setKey(s);
                    }
                    break;
                case AuthenticateSuccess:
                    Log.d("TAG","AuthenticateSuccess");
                    updateConnectionStatus("Authenticated Successfully");
                    break;
                case AuthenticateFail:
                    Log.d("TAG","AuthenticateFail");
                    updateConnectionStatus("Authentication Failed");
                    Log.e("ScanDebug", "Authentication failed for device: " + s);
                    
                    // Handle authentication failure
                    if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                        handleConnectionFailure("Authentication failed. Please try again.");
                    }
                    break;
                case ConnectComplete:
                    Log.d("TAG","ConnectComplete");
                    
                    connectionTimeoutHandler.removeCallbacksAndMessages(null);
                    
                    // Extract device information for navigation
                    int batteryLevel = -1;
                    String firmwareVersion = "Unknown";
                    float currentTemperature = Float.NaN;
                    
                    if (mst03Entity != null) {
                        // Get device static info
                        DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) mst03Entity.getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME);
                        if (deviceInfo != null) {
                            batteryLevel = deviceInfo.getBattery();
                            firmwareVersion = deviceInfo.getFirmwareVersion();
                            currentDeviceStaticInfo = deviceInfo;
                        }
                        // Get current temperature from combination frame
                        CombinationFrame comboFrame = (CombinationFrame) mst03Entity.getMinewFrame(FrameType.COMBINATION_FRAME);
                        if (comboFrame != null) {
                            currentTemperature = comboFrame.getTemperature();
                        }
                    }
                    
                    // Store device info for navigation after data fetch
                    final int finalBatteryLevel = batteryLevel;
                    final String finalFirmwareVersion = firmwareVersion;
                    final float finalCurrentTemperature = currentTemperature;
                    
                    // Start data fetching and wait for completion before navigation
                    updateConnectionStatus("Connected - Fetching Data...");
                    
                    // Fetch data with callback for navigation
                    fetchHistoricalDataWithNavigation(finalBatteryLevel, finalFirmwareVersion, finalCurrentTemperature);
                    break;
                case Disconnect:
                    Log.d("ScanDebug", "Device disconnected, restarting scan service");
                    connectionTimeoutHandler.removeCallbacksAndMessages(null);
                    updateConnectionStatus("Disconnected");
                    isConnecting = false;
                    mDevicesListAdapter.setConnectButtonsEnabled(true);

                    if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                        hideDeviceDetailsCard();
                    }

                    // Restart background scan service after disconnect
                    startBackgroundScanService();
                    Log.d("ScanDebug", "Called startBackgroundScanService() after disconnect");
                    break;

                default:
                    break;
            }
        }
    };

    // Helper method to handle connection failures
    private void handleConnectionFailure(String errorMessage) {
        connectionTimeoutHandler.removeCallbacksAndMessages(null);
        WaitDialog.dismiss();
        
        runOnUiThread(() -> {
            Toast.makeText(ScanDevicesListActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            isConnecting = false;
            mDevicesListAdapter.setConnectButtonsEnabled(true);
            
            // Disconnect from device
            if (mBleManager != null && mst03Entity != null) {
                mBleManager.disConnect(mst03Entity.getMacAddress());
            }
        });
    }

    /**
     * Fetch historical data and navigate to DeviceDetailsActivity only after completion
     */
    private void fetchHistoricalDataWithNavigation(int batteryLevel, String firmwareVersion, float currentTemperature) {
        Log.d("BeaconRawData", "fetchHistoricalDataWithNavigation called");
        
        // Set processing as not complete initially
        isDataProcessingComplete = false;
        
        // Add a timeout for data fetching (15 seconds)
        Handler dataTimeoutHandler = new Handler();
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.w("ScanDebug", "Data fetch timeout - proceeding with navigation");
                // Navigate even if data fetch timed out
                navigateToDeviceDetails(batteryLevel, firmwareVersion, currentTemperature);
            }
        };
        dataTimeoutHandler.postDelayed(timeoutRunnable, 15000); // 15 second timeout
        
        long systemTime = System.currentTimeMillis() / 1000;
        // Get data from last 24 hours 
        long startTime = systemTime - (24 * 60 * 60); // Last 24 hours in seconds
        long endTime = systemTime;
        
        try {
            // Try with rules=1 for time-based query as per SDK documentation
            mBleManager.queryHistoryData(mst03Entity.getMacAddress(), 1, startTime, endTime, systemTime, 
                new OnQueryResultListener<HistoryHtData>() {
                    @Override
                    public void OnQueryResult(boolean success, HistoryHtData historyHtData) {
                        Log.d("BeaconRawData", "OnQueryResult called, success=" + success + ", historyHtData=" + historyHtData);
                        
                        // Cancel timeout since we got a response
                        dataTimeoutHandler.removeCallbacks(timeoutRunnable);
                        
                        if (success && historyHtData != null) {
                            List<HtData> allData = historyHtData.getHistoryDataList();
                            
                            if (allData.isEmpty()) {
                                // Try fallback with rules=0 (all data)
                                tryFallbackQueryWithNavigation(systemTime, batteryLevel, firmwareVersion, currentTemperature, dataTimeoutHandler);
                            } else {
                                // Process data and navigate
                                processHistoricalDataAndNavigate(allData, batteryLevel, firmwareVersion, currentTemperature);
                            }
                        } else {
                            // Try fallback with rules=0 (all data)
                            tryFallbackQueryWithNavigation(systemTime, batteryLevel, firmwareVersion, currentTemperature, dataTimeoutHandler);
                        }
                    }
                });
        } catch (Exception e) {
            Log.e("ScanDebug", "Exception during historical data query: " + e.getMessage());
            e.printStackTrace();
            
            // Cancel timeout
            dataTimeoutHandler.removeCallbacks(timeoutRunnable);
            
            // Try fallback with rules=0 (all data)
            tryFallbackQueryWithNavigation(systemTime, batteryLevel, firmwareVersion, currentTemperature, dataTimeoutHandler);
        }
    }

    /**
     * Try fallback query with rules=0 and navigate after completion
     */
    private void tryFallbackQueryWithNavigation(long systemTime, int batteryLevel, String firmwareVersion, 
                                              float currentTemperature, Handler dataTimeoutHandler) {
        Log.d("BeaconRawData", "tryFallbackQueryWithNavigation called");
        
        // Reset timeout for fallback query
        Runnable fallbackTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.w("ScanDebug", "Fallback query timeout - proceeding with navigation");
                handleNoDataAndNavigate(batteryLevel, firmwareVersion, currentTemperature);
            }
        };
        dataTimeoutHandler.postDelayed(fallbackTimeoutRunnable, 10000); // 10 second timeout for fallback
        
        try {
            mBleManager.queryHistoryData(mst03Entity.getMacAddress(), 0, 0, 0, systemTime, 
                new OnQueryResultListener<HistoryHtData>() {
                    @Override
                    public void OnQueryResult(boolean fallbackSuccess, HistoryHtData fallbackHistoryHtData) {
                        Log.d("BeaconRawData", "Fallback OnQueryResult called, success=" + fallbackSuccess + ", historyHtData=" + fallbackHistoryHtData);
                        
                        // Cancel fallback timeout
                        dataTimeoutHandler.removeCallbacks(fallbackTimeoutRunnable);
                        
                        if (fallbackSuccess && fallbackHistoryHtData != null) {
                            List<HtData> fallbackData = fallbackHistoryHtData.getHistoryDataList();
                            
                            if (fallbackData.isEmpty()) {
                                handleNoDataAndNavigate(batteryLevel, firmwareVersion, currentTemperature);
                            } else {
                                processHistoricalDataAndNavigate(fallbackData, batteryLevel, firmwareVersion, currentTemperature);
                            }
                        } else {
                            handleNoDataAndNavigate(batteryLevel, firmwareVersion, currentTemperature);
                        }
                    }
                });
        } catch (Exception e) {
            Log.e("ScanDebug", "Exception during fallback query: " + e.getMessage());
            e.printStackTrace();
            dataTimeoutHandler.removeCallbacks(fallbackTimeoutRunnable);
            handleNoDataAndNavigate(batteryLevel, firmwareVersion, currentTemperature);
        }
    }

    /**
     * Process historical data and navigate to DeviceDetailsActivity
     */
    private void processHistoricalDataAndNavigate(List<HtData> htDataList, int batteryLevel, 
                                                String firmwareVersion, float currentTemperature) {
        Log.d("BeaconRawData", "processHistoricalDataAndNavigate called with " + htDataList.size() + " records");
        
        // Log raw data for debugging
        for (int i = 0; i < Math.min(htDataList.size(), 5); i++) {
            HtData htData = htDataList.get(i);
            Log.d("BeaconRawData", "Sample data " + i + ": " + htData.toString());
        }

        // Find the last excursion start and determine graph data range
        int lastExcursionStartIndex = -1;
        int lastNormalBeforeExcursionIndex = -1;
        boolean wasInNormalRange = false;
        
        // First pass: find the last excursion start
        for (int i = 0; i < htDataList.size(); i++) {
            float temp = htDataList.get(i).getTemperature();
            boolean isNormal = (temp >= 2.0f && temp <= 8.0f);
            if (wasInNormalRange && !isNormal) {
                lastExcursionStartIndex = i;
            }
            wasInNormalRange = isNormal;
        }
        
        // Second pass: find the last normal point before excursion (if excursion exists)
        if (lastExcursionStartIndex != -1) {
            for (int i = lastExcursionStartIndex - 1; i >= 0; i--) {
                float temp = htDataList.get(i).getTemperature();
                boolean isNormal = (temp >= 2.0f && temp <= 8.0f);
                if (isNormal) {
                    lastNormalBeforeExcursionIndex = i;
                    break;
                }
            }
        }
        
        // Determine the data range for graph display
        List<HtData> filteredData;
        if (lastExcursionStartIndex != -1 && lastNormalBeforeExcursionIndex != -1) {
            // Start from just before the excursion (last normal point)
            int startIndex = Math.max(0, lastNormalBeforeExcursionIndex);
            filteredData = htDataList.subList(startIndex, htDataList.size());
            Log.d("BeaconRawData", "Graph data: Starting from just before excursion (index " + startIndex + "): " + filteredData.size() + " records");
        } else if (lastExcursionStartIndex != -1) {
            // Excursion found but no normal point before it, start from excursion
            filteredData = htDataList.subList(lastExcursionStartIndex, htDataList.size());
            Log.d("BeaconRawData", "Graph data: Starting from excursion (no normal point before): " + filteredData.size() + " records");
        } else {
            // No excursion found, use latest 1000 records
            int startIndex = Math.max(0, htDataList.size() - 1000);
            filteredData = htDataList.subList(startIndex, htDataList.size());
            Log.d("BeaconRawData", "Graph data: No excursion found, using latest " + filteredData.size() + " records (from index " + startIndex + ")");
        }

        // Store the complete dataset for CSV generation
        synchronized (completeHistoricalData) {
            completeHistoricalData.clear();
            completeHistoricalData.addAll(htDataList);
            Log.d("BeaconRawData", "Stored complete historical data: " + completeHistoricalData.size() + " records");
        }
        
        // Clear previous data and add new data
        processedHistoricalData.clear();
        processedExcursionData.clear();
        processedHistoricalData.addAll(filteredData);
        
        // Analyze excursions for local display
        analyzeExcursionsForProcessedData(filteredData);
        
        // Generate CSV automatically with complete historical data
        synchronized (completeHistoricalData) {
            Log.d("BeaconRawData", "Checking completeHistoricalData size: " + completeHistoricalData.size());
            if (!completeHistoricalData.isEmpty()) {
                Log.d("BeaconRawData", "Generating CSV with complete historical data: " + completeHistoricalData.size() + " records");
                generateTripDataCSV(completeHistoricalData, processedExcursionData);
            } else {
                Log.d("BeaconRawData", "No complete historical data available for CSV generation");
                // Try to generate CSV with the original data if complete data is empty
                if (!htDataList.isEmpty()) {
                    Log.d("BeaconRawData", "Falling back to original data for CSV generation: " + htDataList.size() + " records");
                    generateTripDataCSV(htDataList, processedExcursionData);
                }
            }
        }
        
        // Mark processing as complete
        isDataProcessingComplete = true;
        
        Log.d("BeaconRawData", "Data processing complete: " + processedHistoricalData.size() + 
              " historical records, " + processedExcursionData.size() + " excursions");
        
        // Navigate to device details
        navigateToDeviceDetails(batteryLevel, firmwareVersion, currentTemperature);
        
        // Force CSV generation for testing (remove this after testing)
        if (!htDataList.isEmpty()) {
            Log.d("BeaconRawData", "FORCE TEST: Generating CSV with original data: " + htDataList.size() + " records");
            generateTripDataCSV(htDataList, processedExcursionData);
        }
    }

    /**
     * Handle case when no data is available and navigate
     */
    private void handleNoDataAndNavigate(int batteryLevel, String firmwareVersion, float currentTemperature) {
        Log.d("BeaconRawData", "handleNoDataAndNavigate called");
        
        // Clear data and mark as complete even with no data
        processedHistoricalData.clear();
        processedExcursionData.clear();
        isDataProcessingComplete = true;
        
        // Try to generate CSV with complete historical data
        Log.d("BeaconRawData", "Attempting CSV generation with available data");
        synchronized (completeHistoricalData) {
            if (!completeHistoricalData.isEmpty()) {
                Log.d("BeaconRawData", "Generating CSV with complete historical data: " + completeHistoricalData.size() + " records");
                generateTripDataCSV(completeHistoricalData, processedExcursionData);
            } else {
                Log.d("BeaconRawData", "No complete historical data available for CSV generation");
            }
        }
        
        // Navigate to device details anyway
        navigateToDeviceDetails(batteryLevel, firmwareVersion, currentTemperature);
    }

    /**
     * Analyze excursions from processed data
     */
    private void analyzeExcursionsForProcessedData(List<HtData> htDataList) {
        if (htDataList.isEmpty()) {
            return;
        }
        
        List<ExcursionData> excursions = new ArrayList<>();
        
        for (HtData htData : htDataList) {
            float temperature = htData.getTemperature();
            long timestamp = htData.getTimestamps();
            
            // Check if temperature is outside the 2-8°C range
            if (temperature < 2.0f) {
                // Low temperature excursion
                ExcursionData excursion = new ExcursionData(temperature, timestamp, "LOW", mst03Entity.getMacAddress());
                excursions.add(excursion);
            } else if (temperature > 8.0f) {
                // High temperature excursion
                ExcursionData excursion = new ExcursionData(temperature, timestamp, "HIGH", mst03Entity.getMacAddress());
                excursions.add(excursion);
            }
        }
        
        processedExcursionData.addAll(excursions);
        Log.d("BeaconRawData", "Found " + excursions.size() + " excursions");
    }

    /**
     * Navigate to DeviceDetailsActivity with device data
     */
    private void navigateToDeviceDetails(int batteryLevel, String firmwareVersion, float currentTemperature) {
        Log.d("ScanDebug", "navigateToDeviceDetails called");
        
        runOnUiThread(() -> {
            // Dismiss connection dialog
            WaitDialog.dismiss();
            
            // Reset connection state
            isConnecting = false;
            mDevicesListAdapter.setConnectButtonsEnabled(true);
            
            // Navigate to device details screen with real device data
            Intent intent = DeviceDetailsActivity.newIntent(ScanDevicesListActivity.this, mst03Entity, 
                                                           batteryLevel, firmwareVersion, currentTemperature);
            startActivity(intent);
            
            // Disconnect from device after navigation (with a small delay to ensure navigation completes)
            Handler disconnectHandler = new Handler();
            disconnectHandler.postDelayed(() -> {
                if (mst03Entity != null && mBleManager != null) {
                    mBleManager.disConnect(mst03Entity.getMacAddress());
                    Log.d("ScanDebug", "Device disconnected after navigation");
                }
            }, 1000); // 1 second delay
        });
    }

    // Add this method to improve connection timeout handling
    private void startConnectionWithTimeout(MST03Entity device) {
        Log.d("ScanDebug", "Starting connection with timeout for device: " + device.getMacAddress());
        
        // Clear any previous processed data
        clearProcessedData();
        
        // Set connection timeout (increase to 12 seconds for data fetching)
        connectionTimeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnecting) {
                    Log.w("ScanDebug", "Connection timeout - proceeding with available data");
                    isConnecting = false;
                    mDevicesListAdapter.setConnectButtonsEnabled(true);
                    WaitDialog.dismiss();
                    
                    // Extract basic device information for timeout navigation
                    int batteryLevel = -1;
                    String firmwareVersion = "Unknown";
                    float currentTemperature = Float.NaN;
                    
                    if (device != null) {
                        // Get device static info
                        DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) device.getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME);
                        if (deviceInfo != null) {
                            batteryLevel = deviceInfo.getBattery();
                            firmwareVersion = deviceInfo.getFirmwareVersion();
                        }
                        
                        // Get current temperature from combination frame
                        CombinationFrame comboFrame = (CombinationFrame) device.getMinewFrame(FrameType.COMBINATION_FRAME);
                        if (comboFrame != null) {
                            currentTemperature = comboFrame.getTemperature();
                        }
                    }
                    
                    // Set empty data as complete and navigate
                    isDataProcessingComplete = true;
                    
                    // Try to generate CSV with complete historical data
                    Log.d("BeaconRawData", "Attempting CSV generation on connection timeout");
                    synchronized (completeHistoricalData) {
                        if (!completeHistoricalData.isEmpty()) {
                            Log.d("BeaconRawData", "Generating CSV with complete historical data on timeout: " + completeHistoricalData.size() + " records");
                            generateTripDataCSV(completeHistoricalData, processedExcursionData);
                        } else {
                            Log.d("BeaconRawData", "No complete historical data available for CSV generation on timeout");
                        }
                    }
                    
                    navigateToDeviceDetails(batteryLevel, firmwareVersion, currentTemperature);
                }
            }
        }, 12000); // 12 second timeout (increased from 8 seconds)
    }

    // Helper method to handle connection errors
    private void handleConnectionError(MST03Entity device, String errorMessage) {
        isConnecting = false;
        mDevicesListAdapter.setConnectButtonsEnabled(true);
        WaitDialog.dismiss();
        connectionTimeoutHandler.removeCallbacksAndMessages(null);
        
        Toast.makeText(this, "Connection failed: " + errorMessage, Toast.LENGTH_LONG).show();
        
        // Still navigate with basic device info even on connection error
        int batteryLevel = -1;
        String firmwareVersion = "Unknown";
        float currentTemperature = Float.NaN;
        
        if (device != null) {
            DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) device.getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME);
            if (deviceInfo != null) {
                batteryLevel = deviceInfo.getBattery();
                firmwareVersion = deviceInfo.getFirmwareVersion();
            }
            
            CombinationFrame comboFrame = (CombinationFrame) device.getMinewFrame(FrameType.COMBINATION_FRAME);
            if (comboFrame != null) {
                currentTemperature = comboFrame.getTemperature();
            }
        }
        
        // Set empty data as complete and navigate
        clearProcessedData();
        isDataProcessingComplete = true;
        
        // Try to generate CSV with complete historical data
        Log.d("BeaconRawData", "Attempting CSV generation on connection error");
        synchronized (completeHistoricalData) {
            if (!completeHistoricalData.isEmpty()) {
                Log.d("BeaconRawData", "Generating CSV with complete historical data on error: " + completeHistoricalData.size() + " records");
                generateTripDataCSV(completeHistoricalData, processedExcursionData);
            } else {
                Log.d("BeaconRawData", "No complete historical data available for CSV generation on error");
            }
        }
        
        navigateToDeviceDetails(batteryLevel, firmwareVersion, currentTemperature);
    }

    private void updateConnectionStatus(String status) {
        Log.d("ScanDebug", "Connection status: " + status);
        // You can add UI updates here if needed
    }
    
    /**
     * NEW: Start aggressive foreground scanning for faster temperature data discovery
     */
    private void startForegroundScan() {
        if (!permissionsGranted || mBleManager == null) return;
        
        Log.d("ScanDebug", "Starting foreground scan for faster temperature discovery");
        
        try {
            isForegroundScanning = true;
            
            // Stop background service temporarily to avoid conflicts
            pauseBackgroundService();
            
            // Start foreground scan with shorter duration for quicker updates
            mBleManager.startScan(this, FOREGROUND_SCAN_DURATION, new OnScanDevicesResultListener<MST03Entity>() {
                @Override
                public void onScanResult(List<MST03Entity> list) {
                    Log.d("ScanDebug", "Foreground scan result: " + list.size() + " devices");
                    
                    if (list.size() > 0) {
                        // Update devices immediately
                        updateAllDiscoveredDevices(list);
                        
                        // Update UI based on current search mode
                        if (isSearchMode && binding.etSearch.getText().toString().trim().length() > 0) {
                            String currentSearchText = binding.etSearch.getText().toString().trim();
                            filterDevices(currentSearchText);
                        } else {
                            mDevicesListAdapter.setList(allDiscoveredDevices);
                            mDevicesListAdapter.notifyDataSetChanged();
                        }
                        
                        // Also update the device manager for background service
                        deviceManager.updateDevices(list);
                    }
                }

                @Override
                public void onStopScan(List<MST03Entity> list) {
                    Log.d("ScanDebug", "Foreground scan stopped");
                    
                    if (isForegroundScanning) {
                        // Restart foreground scan immediately if we're still active
                        new Handler().postDelayed(() -> {
                            if (isForegroundScanning && !isConnecting) {
                                startForegroundScan();
                            }
                        }, 500); // Very short delay for continuous scanning
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e("ScanDebug", "Error starting foreground scan: " + e.getMessage());
            isForegroundScanning = false;
            // Resume background service if foreground scan fails
            resumeBackgroundService();
        }
    }
    
    /**
     * NEW: Stop foreground scanning and resume background service
     */
    private void stopForegroundScan() {
        Log.d("ScanDebug", "Stopping foreground scan");
        isForegroundScanning = false;
        
        if (mBleManager != null) {
            try {
                mBleManager.stopScan(this);
            } catch (Exception e) {
                Log.e("ScanDebug", "Error stopping foreground scan: " + e.getMessage());
            }
        }
        
        // Resume background service
        resumeBackgroundService();
    }
    
    /**
     * NEW: Pause background scanning service
     */
    private void pauseBackgroundService() {
        Intent pauseIntent = new Intent(this, BackgroundScanService.class);
        pauseIntent.setAction("PAUSE_SCAN");
        startService(pauseIntent);
    }
    
    /**
     * NEW: Resume background scanning service
     */
    private void resumeBackgroundService() {
        Intent resumeIntent = new Intent(this, BackgroundScanService.class);
        resumeIntent.setAction("RESUME_SCAN");
        startService(resumeIntent);
    }

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
        if (!permissionsGranted || mObjectAnimator == null) return;
        
        // Check if we're in foreground or background
        if (isResumed()) {
            // Foreground - use fast direct scanning
            startForegroundScan();
        } else {
            // Background - use background service
            if (isBackgroundServiceRunning()) {
                resumeBackgroundService();
            } else {
                startBackgroundScanService();
            }
        }
        
        // Start the animation to show scanning is active
        mObjectAnimator.start();
    }
    
    // Helper method to check if activity is resumed
    private boolean isResumed() {
        return !isFinishing() && !isDestroyed();
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
            
            // Generate CSV automatically with complete historical data
            synchronized (completeHistoricalData) {
                if (!completeHistoricalData.isEmpty()) {
                    Log.d("BeaconRawData", "Generating CSV with complete historical data: " + completeHistoricalData.size() + " records");
                    generateTripDataCSV(completeHistoricalData, processedExcursionData);
                } else {
                    Log.d("BeaconRawData", "No complete historical data available for CSV generation");
                }
            }
            
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

        // Find the last excursion start and determine graph data range
        int lastExcursionStartIndex = -1;
        int lastNormalBeforeExcursionIndex = -1;
        boolean wasInNormalRange = false;
        
        // First pass: find the last excursion start
        for (int i = 0; i < htDataList.size(); i++) {
            float temp = htDataList.get(i).getTemperature();
            boolean isNormal = (temp >= 2.0f && temp <= 8.0f);
            if (wasInNormalRange && !isNormal) {
                lastExcursionStartIndex = i;
            }
            wasInNormalRange = isNormal;
        }
        
        // Second pass: find the last normal point before excursion (if excursion exists)
        if (lastExcursionStartIndex != -1) {
            for (int i = lastExcursionStartIndex - 1; i >= 0; i--) {
                float temp = htDataList.get(i).getTemperature();
                boolean isNormal = (temp >= 2.0f && temp <= 8.0f);
                if (isNormal) {
                    lastNormalBeforeExcursionIndex = i;
                    break;
                }
            }
        }
        
        // Determine the data range for graph display
        if (lastExcursionStartIndex != -1 && lastNormalBeforeExcursionIndex != -1) {
            // Start from just before the excursion (last normal point)
            int startIndex = Math.max(0, lastNormalBeforeExcursionIndex);
            tripData = htDataList.subList(startIndex, htDataList.size());
            Log.d("BeaconRawData", "Optimized: Starting from just before excursion (index " + startIndex + "): " + tripData.size() + " records");
        } else if (lastExcursionStartIndex != -1) {
            // Excursion found but no normal point before it, start from excursion
            tripData = htDataList.subList(lastExcursionStartIndex, htDataList.size());
            Log.d("BeaconRawData", "Optimized: Starting from excursion (no normal point before): " + tripData.size() + " records");
        } else {
            // No excursion found, use latest 1000 records
            int startIndex = Math.max(0, htDataList.size() - 1000);
            tripData = htDataList.subList(startIndex, htDataList.size());
            Log.d("BeaconRawData", "Optimized: No excursion found, using latest " + tripData.size() + " records (from index " + startIndex + ")");
        }

        // Find excursions in the selected data range
        for (int i = 0; i < tripData.size(); i++) {
            HtData htData = tripData.get(i);
            float temperature = htData.getTemperature();
            if (temperature < 2.0f || temperature > 8.0f) {
                tripExcursions.add(new ExcursionData(temperature, htData.getTimestamps(),
                        temperature < 2.0f ? "LOW" : "HIGH", mst03Entity.getMacAddress()));
            }
        }

            long tripDurationMinutes = tripData.isEmpty() ? 0
                    : (tripData.get(tripData.size() - 1).getTimestamps() - tripData.get(0).getTimestamps())
                            / (1000 * 60);

            Log.d("BeaconRawData",
                    "Optimized trip detected: " + tripData.size() + " records with " + tripExcursions.size()
                            + " excursions. Duration: " + tripDurationMinutes + " minutes");

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
        Log.d("BeaconRawData", "generateTripDataCSV called with " + tripData.size() + " records and " + tripExcursions.size() + " excursions");
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

            // Show success message to user
            runOnUiThread(() -> {
                String message = tripData.isEmpty() ? 
                    "CSV file generated (no data available)" : 
                    "CSV file generated with " + tripData.size() + " records";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            });

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
            Toast.makeText(this, "No complete historical data available. Please connect to a device first.", Toast.LENGTH_LONG)
                    .show();
            return;
        }

        List<ExcursionData> tripExcursions = new ArrayList<>(processedExcursionData);

        Log.d("BeaconRawData", "Generating CSV with complete historical dataset: " + csvData.size() + " records");
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
                    }
                }
            }, 2000); // Wait 2 seconds for Bluetooth to stabilize
            
        } catch (Exception e) {
            Log.e("ScanDevicesList", "Error restarting scanning: " + e.getMessage(), e);
        }
    }
    
}
