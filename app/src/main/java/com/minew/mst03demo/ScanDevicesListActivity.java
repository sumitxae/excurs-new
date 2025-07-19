package com.minew.mst03demo;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
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
import com.minew.mst03demo.databinding.ActivityScanDevicesBinding;
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

public class ScanDevicesListActivity extends BaseActivity {

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
    
    // Static data to pass to DeviceDetailsActivity
    private static List<HtData> processedHistoricalData = new ArrayList<>();
    private static List<ExcursionData> processedExcursionData = new ArrayList<>();
    private static boolean isDataProcessingComplete = false;
    private boolean isSearchMode = false;
    private boolean isClearingSearch = false; 
    private boolean isConnecting = false; 
    private android.os.Handler connectionTimeoutHandler = new android.os.Handler();
    private android.os.Handler searchDebounceHandler = new android.os.Handler();
    private static final int SEARCH_DEBOUNCE_DELAY = 500;
    private DeviceDiscoveryManager deviceManager;
    private DeviceDiscoveryManager.OnDevicesUpdatedListener deviceUpdateListener; 
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanDevicesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Remove toolbar setup and menu inflation
        // Set up custom kebab menu
        binding.btnKebabMenu.setOnClickListener(v -> showCustomMenu(v));

        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        initRefresh();
        initRecyclerView();
        initAnimator();
        initBleManager();
        initBlePermission();
        
        // Initialize device manager and listen for updates
        deviceManager = DeviceDiscoveryManager.getInstance();
        deviceUpdateListener = new DeviceDiscoveryManager.OnDevicesUpdatedListener() {
            @Override
            public void onDevicesUpdated(List<MST03Entity> devices) {
                runOnUiThread(() -> {
                    Log.d("ScanDebug", "Received " + devices.size() + " devices from background service");
                    updateAllDiscoveredDevices(devices);
                    
                    // Update UI based on current search mode
                    if (isSearchMode && binding.etSearch.getText().toString().trim().length() > 0) {
                        // Re-apply current search filter
                        String currentSearchText = binding.etSearch.getText().toString().trim();
                        filterDevices(currentSearchText);
                    } else {
                        mDevicesListAdapter.setList(allDiscoveredDevices);
                    }
                });
            }
        };
        deviceManager.addListener(deviceUpdateListener);
        
        // Don't start background service here - wait for permissions
        // startBackgroundScanService();
        
        httpLogger = new HttpLogger();
        
        setupSearchFunctionality();
    }

    private void showCustomMenu(View anchor) {
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        View menuView = inflater.inflate(R.layout.menu_logout_popup, null);
        android.widget.TextView logoutBtn = menuView.findViewById(R.id.menu_logout);
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
        // Show below the kebab icon, aligned right
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
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

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
            public void afterTextChanged(android.text.Editable s) {}
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
                Log.d("ScanDebug", "QR Code scanned: " + scanResult);
                
                
                processQRScanResult(scanResult);
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
            java.util.regex.Pattern macPattern = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
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
                Log.d("ScanDebug", "Search text is empty, returning early");
                return;
            }
            
            if (allDiscoveredDevices == null || allDiscoveredDevices.isEmpty()) {
                Toast.makeText(this, "No devices discovered yet. Please wait for scan results.", Toast.LENGTH_SHORT).show();
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
                if (device == null) continue;
                
                if (device.getMacAddress() != null && 
                    device.getMacAddress().toLowerCase().contains(searchLower)) {
                    filteredDevices.add(device);
                    continue;
                }
                
                String macWithoutColons = device.getMacAddress() != null ? 
                    device.getMacAddress().replace(":", "").toLowerCase() : "";
                if (macWithoutColons.contains(searchLower.replace(":", ""))) {
                    filteredDevices.add(device);
                }
            }
            
            
            if (mDevicesListAdapter != null) {
                mDevicesListAdapter.setList(filteredDevices);
                
                
                String resultText = filteredDevices.size() + " device(s) found";
                if (filteredDevices.size() == 0) {
                    resultText = "No devices found matching '" + searchText + "'";
                }
                Toast.makeText(this, resultText, Toast.LENGTH_SHORT).show();
                
                Log.d("ScanDebug", "Search for '" + searchText + "' returned " + filteredDevices.size() + " devices");
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
                Log.d("ScanDebug", "QR Search text is empty, returning early");
                return;
            }
            
            if (allDiscoveredDevices == null || allDiscoveredDevices.isEmpty()) {
                Toast.makeText(this, "No devices discovered yet. Please wait for scan results.", Toast.LENGTH_SHORT).show();
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
                if (device == null) continue;
                
                if (device.getMacAddress() != null && 
                    device.getMacAddress().toLowerCase().contains(searchLower)) {
                    filteredDevices.add(device);
                    continue;
                }
                
                String macWithoutColons = device.getMacAddress() != null ? 
                    device.getMacAddress().replace(":", "").toLowerCase() : "";
                if (macWithoutColons.contains(searchLower.replace(":", ""))) {
                    filteredDevices.add(device);
                }
            }
            
            
            if (mDevicesListAdapter != null) {
                mDevicesListAdapter.setList(filteredDevices);
                
                
                String resultText = filteredDevices.size() + " device(s) found";
                if (filteredDevices.size() == 0) {
                    resultText = "No devices found matching '" + searchText + "'";
                }
                Toast.makeText(this, resultText, Toast.LENGTH_SHORT).show();
                
                Log.d("ScanDebug", "QR Search for '" + searchText + "' returned " + filteredDevices.size() + " devices");
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
                Log.d("ScanDebug", "Search cleared, showing all " + allDiscoveredDevices.size() + " devices");
            } else {
                Log.d("ScanDebug", "Search cleared, but adapter or device list is null");
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
        setBleManagerListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeBleManagerListener();
        
        Log.d("ScanDebug", "App going to background, keeping scan active");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up connection timeout handler
        if (connectionTimeoutHandler != null) {
            connectionTimeoutHandler.removeCallbacksAndMessages(null);
        }
        
        // Clean up search debounce handler
        if (searchDebounceHandler != null) {
            searchDebounceHandler.removeCallbacksAndMessages(null);
        }
        
        // Clean up device manager listener
        if (deviceManager != null && deviceUpdateListener != null) {
            deviceManager.removeListener(deviceUpdateListener);
        }
        
        // Shutdown HTTP logger
        if (httpLogger != null) {
            httpLogger.shutdown();
        }
        
        Log.d("ScanDebug", "Activity destroyed, background service continues");
    }

    private void initRefresh(){
        binding.swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                stopScan();
                startScan();
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void initRecyclerView(){
        Log.d("ScanDebug", "=== INIT RECYCLER VIEW STARTED ===");
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(binding.recyclerView.getContext()));
        mDevicesListAdapter = new ScanDevicesListAdapter(R.layout.item_scan_device,null);

        binding.recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayout.VERTICAL));
        
        Log.d("ScanDebug", "=== SETTING ADAPTER CLICK LISTENER ===");
        mDevicesListAdapter.setOnConnectClickListener(new ScanDevicesListAdapter.OnConnectClickListener() {
            @Override
            public void onConnectClick(MST03Entity device) {
                Log.d("ScanDebug", "=== ADAPTER CLICK LISTENER TRIGGERED ===");
                Log.d("ScanDebug", "Device: " + device.getMacAddress());
                Log.d("ScanDebug", "About to call connectToDeviceAndNavigate");
                // Connect to the device and navigate to details screen
                connectToDeviceAndNavigate(device);
                Log.d("ScanDebug", "connectToDeviceAndNavigate called successfully");
                Log.d("ScanDebug", "=== ADAPTER CLICK LISTENER COMPLETED ===");
            }
        });
        Log.d("ScanDebug", "Adapter click listener set successfully");
        
        // Test the adapter listener
        Log.d("ScanDebug", "Testing adapter click listener...");
        if (mDevicesListAdapter != null) {
            Log.d("ScanDebug", "Adapter is not null");
            // We can't test the listener directly, but we can verify it's set
        } else {
            Log.e("ScanDebug", "Adapter is null!");
        }

        binding.recyclerView.setAdapter(mDevicesListAdapter);
        Log.d("ScanDebug", "=== INIT RECYCLER VIEW COMPLETED ===");
    }
    

    
    private void connectToDeviceAndNavigate(MST03Entity device) {
        Log.d("ScanDebug", "=== CONNECTION ATTEMPT STARTED ===");
        Log.d("ScanDebug", "Device: " + device.getMacAddress());
        Log.d("ScanDebug", "isConnecting: " + isConnecting);
        
        if (isConnecting) {
            Log.d("ScanDebug", "Already connecting, ignoring click");
            Toast.makeText(this, "Already connecting to a device", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d("ScanDebug", "Setting isConnecting to true");
        isConnecting = true;
        mst03Entity = device;
        
        // Disable all connect buttons during connection
        Log.d("ScanDebug", "Disabling connect buttons");
        mDevicesListAdapter.setConnectButtonsEnabled(false);
        
        // Show connection dialog
        Log.d("ScanDebug", "Showing connection dialog");
        WaitDialog.show("Connecting to device...");
        
        // Set connection timeout
        Log.d("ScanDebug", "Setting connection timeout");
        connectionTimeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnecting) {
                    Log.d("ScanDebug", "Connection timeout - navigating to device details");
                    isConnecting = false;
                    mDevicesListAdapter.setConnectButtonsEnabled(true);
                    WaitDialog.dismiss();
                    
                    // Extract device information for timeout navigation
                    int batteryLevel = -1;
                    String firmwareVersion = "Unknown";
                    float currentTemperature = Float.NaN;
                    
                    if (device != null) {
                        // Get device static info
                        DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) device.getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME);
                        if (deviceInfo != null) {
                            batteryLevel = deviceInfo.getBattery();
                            firmwareVersion = deviceInfo.getFirmwareVersion();
                            Log.d("ScanDebug", "Timeout: Extracted device info - Battery: " + batteryLevel + "%, Firmware: " + firmwareVersion);
                        }
                        
                        // Get current temperature from combination frame
                        CombinationFrame comboFrame = (CombinationFrame) device.getMinewFrame(FrameType.COMBINATION_FRAME);
                        if (comboFrame != null) {
                            currentTemperature = comboFrame.getTemperature();
                            Log.d("ScanDebug", "Timeout: Extracted current temperature: " + currentTemperature + "°C");
                        }
                    }
                    
                    // Navigate to device details even if connection fails
                    Intent intent = DeviceDetailsActivity.newIntent(ScanDevicesListActivity.this, device, batteryLevel, firmwareVersion, currentTemperature);
                    startActivity(intent);
                }
            }
        }, 8000); // 8 second timeout
        
        // Add a fallback navigation after 3 seconds to ensure user doesn't get stuck
        connectionTimeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnecting) {
                    Log.d("ScanDebug", "Fallback navigation triggered - connection taking too long");
                    // Don't dismiss dialog or change state, just log for debugging
                }
            }
        }, 3000); // 3 second fallback check
        
        // Ensure BLE manager is ready and listener is set
        Log.d("ScanDebug", "Ensuring BLE manager is ready");
        ensureBleManagerReady();
        
        // Connect to the device
        Log.d("ScanDebug", "Attempting to connect to device: " + device.getMacAddress());
        try {
            if (mBleManager != null) {
                Log.d("ScanDebug", "BLE manager is not null, calling connect");
                mBleManager.connect(this, device);
                Log.d("ScanDebug", "Connection request sent successfully");
            } else {
                Log.e("ScanDebug", "BLE manager is null, cannot connect");
                throw new Exception("BLE manager not initialized");
            }
        } catch (Exception e) {
            Log.e("ScanDebug", "Error connecting to device: " + e.getMessage());
            e.printStackTrace();
            isConnecting = false;
            mDevicesListAdapter.setConnectButtonsEnabled(true);
            WaitDialog.dismiss();
            
            // Extract device information for error navigation
            int batteryLevel = -1;
            String firmwareVersion = "Unknown";
            float currentTemperature = Float.NaN;
            
            if (device != null) {
                // Get device static info
                DeviceStaticInfoFrame deviceInfo = (DeviceStaticInfoFrame) device.getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME);
                if (deviceInfo != null) {
                    batteryLevel = deviceInfo.getBattery();
                    firmwareVersion = deviceInfo.getFirmwareVersion();
                    Log.d("ScanDebug", "Error: Extracted device info - Battery: " + batteryLevel + "%, Firmware: " + firmwareVersion);
                }
                
                // Get current temperature from combination frame
                CombinationFrame comboFrame = (CombinationFrame) device.getMinewFrame(FrameType.COMBINATION_FRAME);
                if (comboFrame != null) {
                    currentTemperature = comboFrame.getTemperature();
                    Log.d("ScanDebug", "Error: Extracted current temperature: " + currentTemperature + "°C");
                }
            }
            
            // Navigate to device details even if connection fails
            Intent intent = DeviceDetailsActivity.newIntent(ScanDevicesListActivity.this, device, batteryLevel, firmwareVersion, currentTemperature);
            startActivity(intent);
        }
        
        Log.d("ScanDebug", "=== CONNECTION ATTEMPT COMPLETED ===");
    }

    private void initAnimator(){
        mObjectAnimator = ObjectAnimator.ofFloat(binding.ibHomeScan, "rotation", 0f, 360f);
        mObjectAnimator.setDuration(1500);
        mObjectAnimator.setRepeatCount(ValueAnimator.INFINITE);
        
        binding.ibHomeScan.setOnClickListener(v -> {
            stopScan();
            startScan();
            mObjectAnimator.start();
        });
    }

    private void initBleManager(){
        try {
            mBleManager = MST03SensorBleManager.getInstance();
            if (mBleManager == null) {
                Log.e("ScanDebug", "Failed to get BLE manager instance");
                Toast.makeText(this, "Failed to initialize Bluetooth manager", Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d("ScanDebug", "BLE manager initialized: " + (mBleManager != null));
            
            // Set the connection state listener immediately
            setBleManagerListener();
            Log.d("ScanDebug", "BLE manager listener set successfully");
        } catch (Exception e) {
            Log.e("ScanDebug", "Error initializing BLE manager: " + e.getMessage());
            Toast.makeText(this, "Error initializing Bluetooth: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void ensureBleManagerReady() {
        if (mBleManager == null) {
            Log.d("ScanDebug", "Reinitializing BLE manager...");
            initBleManager();
        }
        // Always set the listener to ensure it's active
        setBleManagerListener();
        Log.d("ScanDebug", "BLE manager ready check completed");
    }
    
    private void setBleManagerListener(){
        if (mBleManager != null) {
            mBleManager.setOnConnStateListener(mConnStateListener);
            Log.d("ScanDebug", "BLE manager listener set");
        } else {
            Log.e("ScanDebug", "Cannot set BLE manager listener - manager is null");
        }
    }
    
    private void removeBleManagerListener(){
        mBleManager.setOnConnStateListener(null);
    }
    
    private void startBackgroundScanService() {
        try {
            Intent serviceIntent = new Intent(this, BackgroundScanService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d("ScanDebug", "Background scan service started successfully");
        } catch (Exception e) {
            Log.e("ScanDebug", "Failed to start background service: " + e.getMessage());
            
        }
    }

    private OnConnStateListener mConnStateListener = new OnConnStateListener() {
        @Override
        public void onUpdateConnState(String s, BleConnectionState mSensorConnectionState) {
            Log.d("ScanDebug", "Connection state changed: " + mSensorConnectionState + " for device: " + s);
            
            if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                Log.d("ScanDebug", "Connection event: " + mSensorConnectionState + " for " + mst03Entity.getMacAddress());
            }
            
            switch (mSensorConnectionState) {
                case Connecting:
                    Log.d("TAG","Connecting");
                    updateConnectionStatus("Connecting...");
                    break;
                case Connected:
                    Log.d("TAG","Connected");
                    updateConnectionStatus("Connected");
                    
                    // Set the secret key immediately when connected (required for authentication)
                    if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                        Log.d("ScanDebug", "Setting authentication key for device: " + s);
                        setKey(s);
                    }
                    break;
                case ConnectComplete:
                    Log.d("TAG","ConnectComplete");
                    
                    connectionTimeoutHandler.removeCallbacksAndMessages(null);
                    
                    // Dismiss connection dialog
                    WaitDialog.dismiss();
                    
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
                            Log.d("ScanDebug", "Extracted device info - Battery: " + batteryLevel + "%, Firmware: " + firmwareVersion);
                        }
                        
                        // Get current temperature from combination frame
                        CombinationFrame comboFrame = (CombinationFrame) mst03Entity.getMinewFrame(FrameType.COMBINATION_FRAME);
                        if (comboFrame != null) {
                            currentTemperature = comboFrame.getTemperature();
                            Log.d("ScanDebug", "Extracted current temperature: " + currentTemperature + "°C");
                        }
                    }
                    
                    // Use the existing working connection logic
                    connectedSensor();
                    
                    // Navigate to device details screen with real device data
                    Intent intent = DeviceDetailsActivity.newIntent(ScanDevicesListActivity.this, mst03Entity, batteryLevel, firmwareVersion, currentTemperature);
                    startActivity(intent);
                    break;
                case Disconnect:
                    Log.d("TAG","Disconnect");
                    
                    connectionTimeoutHandler.removeCallbacksAndMessages(null);
                    updateConnectionStatus("Disconnected");
                    
                    isConnecting = false;
                    mDevicesListAdapter.setConnectButtonsEnabled(true);
                    
                    if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                        hideDeviceDetailsCard();
                    }
                    break;

                default:
                    break;
            }
        }
    };

    private void initBlePermission(){
        String[] requestPermissionList;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionList = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION};
        } else {
            requestPermissionList = new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION};
        }
        PermissionX.init(this).permissions(requestPermissionList)
                .onExplainRequestReason(new ExplainReasonCallback() {
                    @Override
                    public void onExplainReason(@NonNull ExplainScope scope, @NonNull List<String> deniedList) {
                        scope.showRequestReasonDialog(deniedList,getString(R.string.need_permission_continue),"Ok","Cancel");
                    }
                })
                .onForwardToSettings(new ForwardToSettingsCallback() {
                    @Override
                    public void onForwardToSettings(@NonNull ForwardScope scope, @NonNull List<String> deniedList) {
                        scope.showForwardToSettingsDialog(deniedList,getString(R.string.allow_permission_in_settings),"Ok","Cancel");
                    }
                })
                .request(new RequestCallback() {
                    @Override
                    public void onResult(boolean allGranted, @NonNull List<String> grantedList, @NonNull List<String> deniedList) {
                        if(allGranted){
                            checkoutBluetooth();
                            // Start background service after permissions are granted
                            startBackgroundScanService();
                        }else{
                            Toast.makeText(ScanDevicesListActivity.this, "The following permissions are denied", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }


    private void checkoutBluetooth(){
        Log.d("ScanDebug", "Checking Bluetooth status...");
        switch (BLETool.checkBluetooth(this)){
            case BLE_NOT_SUPPORT:
                Log.d("ScanDebug", "BLE not supported on this device");
                Toast.makeText(this, "Not Support BLE", Toast.LENGTH_SHORT).show();
                break;
            case BLUETOOTH_ON:
                Log.d("ScanDebug", "Bluetooth is ON, starting scan...");
                startScan();
                break;
            case BLUETOOTH_OFF:
                Log.d("ScanDebug", "Bluetooth is OFF, requesting to enable...");
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, 4);
                break;
        }

    }



    private void startScan(){
        Log.d("ScanDebug", "Foreground scan requested - but using background service for device discovery");
        
        // The background service is handling all scanning
        // This method is kept for compatibility but doesn't start its own scan
        if (isBackgroundServiceRunning()) {
            Log.d("ScanDebug", "Background service is running and handling device discovery");
        } else {
            Log.d("ScanDebug", "Background service not running, starting it...");
            startBackgroundScanService();
        }
        
        // Start the animation to show scanning is active
        mObjectAnimator.start();
    }
    
    private void updateAllDiscoveredDevices(List<MST03Entity> newDevices) {
        
        java.util.Map<String, MST03Entity> existingDevicesMap = new java.util.HashMap<>();
        for (MST03Entity device : allDiscoveredDevices) {
            existingDevicesMap.put(device.getMacAddress(), device);
        }
        
        
        for (MST03Entity newDevice : newDevices) {
            String macAddress = newDevice.getMacAddress();
            if (existingDevicesMap.containsKey(macAddress)) {
                
                MST03Entity existingDevice = existingDevicesMap.get(macAddress);
                
                existingDevice.setRssi(newDevice.getRssi());
                
            } else {
                
                allDiscoveredDevices.add(newDevice);
            }
        }
        
        
        allDiscoveredDevices.sort(new Comparator<MST03Entity>() {
            @Override
            public int compare(MST03Entity o1, MST03Entity o2) {
                return o2.getRssi() - o1.getRssi();
            }
        });
        
        Log.d("ScanDebug", "Updated all discovered devices list. Total devices: " + allDiscoveredDevices.size());
    }

    private void stopScan(){
        Log.d("ScanDebug", "Foreground stop scan requested - but background service continues");
        // Only stop the animation, background service continues scanning
        mObjectAnimator.cancel();
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


    private void setKey(String mac){
        String key = "minewtech1234567";
        mBleManager.setSecretKey(mac,key);
    }
    private void connectedSensor(){
        
        if (mst03Entity == null) {
            Log.e("ScanDebug", "Device is null, cannot connect");
            Toast.makeText(this, "Device not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        
        if (mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME) != null) {
            currentDeviceStaticInfo = (DeviceStaticInfoFrame) mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME);
        }
        
        
        // Set the secret key for the device (required for data access)
        // setKey(mst03Entity.getMacAddress()); // Moved to Connected state
        
        // Skip historical data upload as requested
        Log.d("ScanDebug", "Historical data upload skipped as requested");
    }
    
    // Static methods to access processed data
    public static List<HtData> getProcessedHistoricalData() {
        return processedHistoricalData;
    }
    
    public static List<ExcursionData> getProcessedExcursionData() {
        return processedExcursionData;
    }
    
    public static boolean isDataProcessingComplete() {
        return isDataProcessingComplete;
    }
    
    public static void clearProcessedData() {
        processedHistoricalData.clear();
        processedExcursionData.clear();
        isDataProcessingComplete = false;
    }
    
    private void showDeviceDetailsCard() {
        binding.deviceDetailsCard.setVisibility(View.VISIBLE);
        binding.llLoading.setVisibility(View.VISIBLE);
        binding.llDeviceInfo.setVisibility(View.GONE);
        binding.llHistoricalData.setVisibility(View.GONE);
        
        
        if (currentDeviceStaticInfo != null) {
            binding.tvScanDeviceMac.setText("MAC: " + currentDeviceStaticInfo.getMacAddress());
            binding.tvScanDeviceBattery.setText("Battery: " + currentDeviceStaticInfo.getBattery() + "%");
            binding.tvScanDeviceFirmware.setText("Firmware: " + currentDeviceStaticInfo.getFirmwareVersion());
        } else {
            binding.tvScanDeviceMac.setText("MAC: " + mst03Entity.getMacAddress());
            binding.tvScanDeviceBattery.setText("Battery: Unknown");
            binding.tvScanDeviceFirmware.setText("Firmware: Unknown");
        }
        
        
        binding.btnCloseDetails.setOnClickListener(v -> hideDeviceDetailsCard());
    }
    
    private void hideDeviceDetailsCard() {
        binding.deviceDetailsCard.setVisibility(View.GONE);
        
        connectionTimeoutHandler.removeCallbacksAndMessages(null);
        
        if (mst03Entity != null && mBleManager != null) {
            mBleManager.disConnect(mst03Entity.getMacAddress());
            Log.d("ScanDebug", "Disconnected from device: " + mst03Entity.getMacAddress());
        }
        
        mst03Entity = null;
        currentDeviceStaticInfo = null;
        excursionDataList.clear();
        
        isConnecting = false;
        mDevicesListAdapter.setConnectButtonsEnabled(true);
    }
    
    private void updateConnectionStatus(String status) {
        binding.tvScanConnectionStatus.setText("Status: " + status);
    }
    
    private void showDeviceInfoImmediately() {
        
        binding.deviceDetailsCard.setVisibility(View.VISIBLE);
        binding.llLoading.setVisibility(View.GONE);
        binding.llDeviceInfo.setVisibility(View.VISIBLE);
        binding.llHistoricalData.setVisibility(View.GONE);
        
        
        if (currentDeviceStaticInfo != null) {
            binding.tvScanDeviceMac.setText("MAC: " + currentDeviceStaticInfo.getMacAddress());
            binding.tvScanDeviceBattery.setText("Battery: " + currentDeviceStaticInfo.getBattery() + "%");
            binding.tvScanDeviceFirmware.setText("Firmware: " + currentDeviceStaticInfo.getFirmwareVersion());
        } else {
            binding.tvScanDeviceMac.setText("MAC: " + mst03Entity.getMacAddress());
            binding.tvScanDeviceBattery.setText("Battery: Unknown");
            binding.tvScanDeviceFirmware.setText("Firmware: Unknown");
        }
        
        
        updateConnectionStatus("Connected - Uploading Data...");
        
        
        binding.btnCloseDetails.setOnClickListener(v -> hideDeviceDetailsCard());
        
        Log.d("ScanDebug", "Device info card shown immediately after connection");
    }
    
    private void startAsyncDataUpload() {
        
        new Thread(() -> {
            try {
                Log.d("ScanDebug", "Starting async data upload for device: " + mst03Entity.getMacAddress());
                
                
                fetchHistoricalDataAsync();
                
            } catch (Exception e) {
                Log.e("ScanDebug", "Error in async data upload: " + e.getMessage());
                e.printStackTrace();
                
                
                runOnUiThread(() -> {
                    updateConnectionStatus("Data Upload Failed");
                });
            }
        }).start();
    }
    
    private void fetchHistoricalData() {
        Log.d("ScanDebug", "Fetching historical data for device: " + mst03Entity.getMacAddress());
        
        long systemTime = System.currentTimeMillis() / 1000;
        
        long startTime = (systemTime - 3600 * 24) / 1000; 
        long endTime = systemTime;
        
        Log.d("ScanDebug", "Query parameters - startTime: " + startTime + ", endTime: " + endTime + ", systemTime: " + systemTime);
        
        mBleManager.queryHistoryData(mst03Entity.getMacAddress(), 1, startTime, endTime, systemTime, 
            new OnQueryResultListener<HistoryHtData>() {
                @Override
                public void OnQueryResult(boolean success, HistoryHtData historyHtData) {
                    Log.d("ScanDebug", "OnQueryResult called - success: " + success + ", historyHtData: " + (historyHtData != null));
                    if (success && historyHtData != null) {
                        List<HtData> allData = historyHtData.getHistoryDataList();
                        Log.d("ScanDebug", "Historical data received: " + allData.size() + " records - sending FULL data in chunks");
                        
                        
                        analyzeExcursions(allData);
                    } else {
                        Log.d("ScanDebug", "Failed to get historical data - success: " + success + ", historyHtData null: " + (historyHtData == null));
                        updateConnectionStatus("Failed to get data");
                        
                        
                        if (mst03Entity != null) {
                            Log.d("ScanDebug", "Sending device info without historical data");
                            sendCompleteDeviceData(new ArrayList<>());
                            
                            updateHistoricalDataUI(0, 0, -1);
                        }
                    }
                }
            });
    }
    
    private void fetchHistoricalDataAsync() {
        Log.d("ScanDebug", "Fetching historical data asynchronously for device: " + mst03Entity.getMacAddress());
        
        long systemTime = System.currentTimeMillis() / 1000;
        
        long startTime = (systemTime - 3600 * 24) / 1000; 
        long endTime = systemTime;
        
        Log.d("ScanDebug", "Async query parameters - startTime: " + startTime + ", endTime: " + endTime + ", systemTime: " + systemTime);
        
        mBleManager.queryHistoryData(mst03Entity.getMacAddress(), 1, startTime, endTime, systemTime, 
            new OnQueryResultListener<HistoryHtData>() {
                @Override
                public void OnQueryResult(boolean success, HistoryHtData historyHtData) {
                    Log.d("ScanDebug", "Async OnQueryResult called - success: " + success + ", historyHtData: " + (historyHtData != null));
                    if (success && historyHtData != null) {
                        List<HtData> allData = historyHtData.getHistoryDataList();
                        Log.d("ScanDebug", "Async historical data received: " + allData.size() + " records - sending FULL data in chunks");
                        
                        
                        analyzeExcursionsAsync(allData);
                    } else {
                        Log.d("ScanDebug", "Async failed to get historical data - success: " + success + ", historyHtData null: " + (historyHtData == null));
                        
                        
                        runOnUiThread(() -> {
                            updateConnectionStatus("Data Upload Failed");
                        });
                        
                        
                        if (mst03Entity != null) {
                            Log.d("ScanDebug", "Sending device info without historical data");
                            sendCompleteDeviceDataAsync(new ArrayList<>());
                        }
                    }
                }
            });
    }
    
    private void analyzeExcursions(List<HtData> htDataList) {
        excursionDataList.clear();
        
        
        long firstExcursionStartTime = -1;
        boolean wasInNormalRange = false;
        
        for (int i = 0; i < htDataList.size(); i++) {
            HtData htData = htDataList.get(i);
            float temperature = htData.getTemperature();
            long timestamp = htData.getTimestamps();
            
            
            boolean isInNormalRange = (temperature >= 2.0f && temperature <= 8.0f);
            
            
            if (wasInNormalRange && !isInNormalRange && firstExcursionStartTime == -1) {
                firstExcursionStartTime = timestamp;
                Log.d("ScanDebug", "First excursion started at: " + new java.util.Date(timestamp));
            }
            
            
            wasInNormalRange = isInNormalRange;
            
            
            if (temperature < 2.0f) {
                excursionDataList.add(new ExcursionData(temperature, timestamp, "LOW", mst03Entity.getMacAddress()));
            } else if (temperature > 8.0f) {
                excursionDataList.add(new ExcursionData(temperature, timestamp, "HIGH", mst03Entity.getMacAddress()));
            }
        }
        
        
        excursionLogger = new ExcursionLogger(this, mst03Entity.getMacAddress());
        excursionLogger.logExcursions(excursionDataList);
        
        
        sendCompleteDeviceData(htDataList);
        
        
        updateHistoricalDataUI(htDataList.size(), excursionDataList.size(), firstExcursionStartTime);
    }
    
    private void analyzeExcursionsAsync(List<HtData> htDataList) {
        excursionDataList.clear();
        
        
        long firstExcursionStartTime = -1;
        boolean wasInNormalRange = false;
        
        for (int i = 0; i < htDataList.size(); i++) {
            HtData htData = htDataList.get(i);
            float temperature = htData.getTemperature();
            long timestamp = htData.getTimestamps();
            
            
            boolean isInNormalRange = (temperature >= 2.0f && temperature <= 8.0f);
            
            
            if (wasInNormalRange && !isInNormalRange && firstExcursionStartTime == -1) {
                firstExcursionStartTime = timestamp;
                Log.d("ScanDebug", "Async - First excursion started at: " + new java.util.Date(timestamp));
            }
            
            
            wasInNormalRange = isInNormalRange;
            
            
            if (temperature < 2.0f) {
                excursionDataList.add(new ExcursionData(temperature, timestamp, "LOW", mst03Entity.getMacAddress()));
            } else if (temperature > 8.0f) {
                excursionDataList.add(new ExcursionData(temperature, timestamp, "HIGH", mst03Entity.getMacAddress()));
            }
        }
        
        
        excursionLogger = new ExcursionLogger(this, mst03Entity.getMacAddress());
        excursionLogger.logExcursions(excursionDataList);
        
        // Store processed data for DeviceDetailsActivity
        processedHistoricalData.clear();
        processedHistoricalData.addAll(htDataList);
        processedExcursionData.clear();
        processedExcursionData.addAll(excursionDataList);
        isDataProcessingComplete = true;
        
        Log.d("ScanDebug", "Data processing complete - Historical: " + htDataList.size() + ", Excursions: " + excursionDataList.size());
        
        sendCompleteDeviceDataAsync(htDataList);
    }
    
    private void sendCompleteDeviceData(List<HtData> htDataList) {
        Log.d("ScanDebug", "sendCompleteDeviceData called with " + htDataList.size() + " historical records");
        
        if (mst03Entity == null) {
            Log.e("ScanDebug", "mst03Entity is null, cannot send device data");
            return;
        }
        
        
        String deviceMac = mst03Entity.getMacAddress();
        String deviceName = mst03Entity.getName() != null ? mst03Entity.getName() : "Unknown";
        float currentTemperature = 0.0f;
        String firmwareVersion = "Unknown";
        int batteryLevel = 0;
        int rssi = mst03Entity.getRssi();
        
        Log.d("ScanDebug", "Device info - MAC: " + deviceMac + ", Name: " + deviceName + ", RSSI: " + rssi);
        
        
        if (mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME) != null) {
            com.minew.ble.mst03.frames.CombinationFrame comboFrame = 
                (com.minew.ble.mst03.frames.CombinationFrame) mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
            currentTemperature = comboFrame.getTemperature();
            Log.d("ScanDebug", "Current temperature from combo frame: " + currentTemperature);
        } else {
            Log.d("ScanDebug", "No combination frame available for temperature");
        }
        
        
        if (currentDeviceStaticInfo != null) {
            batteryLevel = currentDeviceStaticInfo.getBattery();
            firmwareVersion = currentDeviceStaticInfo.getFirmwareVersion();
            Log.d("ScanDebug", "Device static info - Battery: " + batteryLevel + "%, Firmware: " + firmwareVersion);
        } else {
            Log.d("ScanDebug", "No device static info available");
        }
        
        Log.d("ScanDebug", "About to send complete device data to HTTP server");
        
        
        httpLogger.logCompleteDeviceData(deviceMac, deviceName, currentTemperature, firmwareVersion, 
                                        batteryLevel, rssi, htDataList, excursionDataList);
        
        Log.d("ScanDebug", "Complete device data sent, scheduling disconnect and scan resume");
        
        
        if (excursionDataList.size() > 0) {
            Toast.makeText(this, "Excursion data uploaded", Toast.LENGTH_SHORT).show();
        }
        
        
        new android.os.Handler().postDelayed(() -> {
            Log.d("ScanDebug", "Disconnecting and resuming scan after data collection");
            hideDeviceDetailsCard();
            startScan();
        }, 7000); 
    }
    
    private void sendCompleteDeviceDataAsync(List<HtData> htDataList) {
        Log.d("ScanDebug", "sendCompleteDeviceDataAsync called with " + htDataList.size() + " historical records");
        
        if (mst03Entity == null) {
            Log.e("ScanDebug", "mst03Entity is null, cannot send device data");
            return;
        }
        
        
        String deviceMac = mst03Entity.getMacAddress();
        String deviceName = mst03Entity.getName() != null ? mst03Entity.getName() : "Unknown";
        float currentTemperature = 0.0f;
        String firmwareVersion = "Unknown";
        int batteryLevel = 0;
        int rssi = mst03Entity.getRssi();
        
        Log.d("ScanDebug", "Async device info - MAC: " + deviceMac + ", Name: " + deviceName + ", RSSI: " + rssi);
        
        
        if (mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME) != null) {
            com.minew.ble.mst03.frames.CombinationFrame comboFrame = 
                (com.minew.ble.mst03.frames.CombinationFrame) mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
            currentTemperature = comboFrame.getTemperature();
            Log.d("ScanDebug", "Async temperature from CombinationFrame: " + currentTemperature + "°C");
        } else {
            Log.d("ScanDebug", "Async no combination frame available for temperature");
        }
        
        // Validate temperature before HTTP logging
        boolean hasValidTemperature = (currentTemperature != 0.0f && !Float.isNaN(currentTemperature));
        if (!hasValidTemperature) {
            Log.w("ScanDebug", "Invalid temperature detected: " + currentTemperature + "°C - skipping HTTP logging");
            Log.d("ScanDebug", "Device data processing complete without HTTP logging due to invalid temperature");
            return;
        }
        
        Log.d("ScanDebug", "Valid temperature confirmed: " + currentTemperature + "°C - proceeding with HTTP logging");
        
        
        if (currentDeviceStaticInfo != null) {
            batteryLevel = currentDeviceStaticInfo.getBattery();
            firmwareVersion = currentDeviceStaticInfo.getFirmwareVersion();
            Log.d("ScanDebug", "Async device static info - Battery: " + batteryLevel + "%, Firmware: " + firmwareVersion);
        } else {
            Log.d("ScanDebug", "Async no device static info available");
        }
        
        Log.d("ScanDebug", "About to send complete device data to HTTP server asynchronously");
        
        
        httpLogger.logCompleteDeviceData(deviceMac, deviceName, currentTemperature, firmwareVersion, 
                                        batteryLevel, rssi, htDataList, excursionDataList);
        
        Log.d("ScanDebug", "Async complete device data sent");
        
        
        if (excursionDataList.size() > 0) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Excursion data uploaded", Toast.LENGTH_SHORT).show();
            });
        }
        
        
        runOnUiThread(() -> {
            updateConnectionStatus("Data Upload Complete");
        });
    }
    
    private void updateHistoricalDataUI(int totalReadings, int excursionCount, long firstExcursionStartTime) {
        binding.llLoading.setVisibility(View.GONE);
        binding.llDeviceInfo.setVisibility(View.VISIBLE);
        
        
        if (firstExcursionStartTime != -1) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            String excursionStartTimeStr = sdf.format(new java.util.Date(firstExcursionStartTime));
            binding.tvScanConnectionStatus.setText("Status: Data Analysis Complete");
            binding.tvExcursionStartTime.setVisibility(View.VISIBLE);
            binding.tvExcursionStartTime.setText("Excursion Started at: " + excursionStartTimeStr);
        } else {
            updateConnectionStatus("Data Analysis Complete - No Excursions Found");
            binding.tvExcursionStartTime.setVisibility(View.GONE);
        }
        
        
        binding.llHistoricalData.setVisibility(View.GONE);
    }
    
    private void viewLogFile() {
        String logPath = excursionLogger.getLogFilePath();
        if (logPath != null) {
            Log.d("ScanDebug", "Log file path: " + logPath);
            
            showLogFileContent(logPath);
        } else {
            Toast.makeText(this, "No log file found", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showLogFileContent(String logPath) {
        try {
            java.io.File file = new java.io.File(logPath);
            if (file.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                
                
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Excursion Log File")
                    .setMessage(content.toString())
                    .setPositiveButton("OK", null)
                    .setNegativeButton("Share", (dialog, which) -> shareLogFile(logPath))
                    .show();
            }
        } catch (Exception e) {
            Log.e("ScanDebug", "Error reading log file: " + e.getMessage());
            Toast.makeText(this, "Error reading log file", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void shareLogFile(String logPath) {
        try {
            java.io.File file = new java.io.File(logPath);
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, 
                getPackageName() + ".fileprovider", 
                file
            );
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Excursion Log Report");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "Share Log File"));
        } catch (Exception e) {
            Log.e("ScanDebug", "Error sharing log file: " + e.getMessage());
            Toast.makeText(this, "Error sharing log file", Toast.LENGTH_SHORT).show();
        }
    }
}
