package com.minew.mst03demo;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View;
import android.widget.LinearLayout;
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
    private boolean isSearchMode = false;
    private boolean isClearingSearch = false; // Flag to prevent recursive calls
    private boolean isConnecting = false; // Flag to track connection state
    private android.os.Handler connectionTimeoutHandler = new android.os.Handler();
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanDevicesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Set status bar icons/text to dark
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        initRefresh();
        initRecyclerView();
        initAnimator();
        initBleManager();
        initBlePermission();
        
        // Start background scanning service
        startBackgroundScanService();
        
        // Initialize HTTP logger
        httpLogger = new HttpLogger();
        
        // Set up search functionality
        setupSearchFunctionality();
    }
    
    private void setupSearchFunctionality() {
        // QR Scan button
        binding.btnScanQr.setOnClickListener(v -> {
            startQRScanner();
        });
        
        // Clear search button
        binding.btnClearSearch.setOnClickListener(v -> {
            clearSearch();
        });
        
        // Add text change listener for real-time search
        binding.etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    // Skip if we're in the middle of clearing search
                    if (isClearingSearch) {
                        return;
                    }
                    
                    String searchText = s.toString().trim();
                    if (!searchText.isEmpty()) {
                        filterDevices(searchText);
                        // Show clear button when searching
                        binding.btnClearSearch.setVisibility(View.VISIBLE);
                    } else {
                        clearSearch();
                    }
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
                
                // Process the QR code result
                processQRScanResult(scanResult);
            }
        }
    }
    
    private void processQRScanResult(String scanResult) {
        // Try to extract MAC address from QR code
        String macAddress = extractMacAddressFromQR(scanResult);
        
        if (macAddress != null) {
            // Search for the device with this MAC address
            binding.etSearch.setText(macAddress);
            filterDevices(macAddress);
            Toast.makeText(this, "Searching for device: " + macAddress, Toast.LENGTH_SHORT).show();
        } else {
            // If no MAC address found, just search for the raw text
            binding.etSearch.setText(scanResult);
            filterDevices(scanResult);
            Toast.makeText(this, "Searching for: " + scanResult, Toast.LENGTH_SHORT).show();
        }
    }
    
    private String extractMacAddressFromQR(String qrText) {
        // Common MAC address patterns
        String[] patterns = {
            "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})", // Standard MAC format
            "([0-9A-Fa-f]{2}){6}", // MAC without separators
            "MAC[\\s:]*([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})", // MAC with prefix
            "Device[\\s:]*([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})" // Device with MAC
        };
        
        for (String pattern : patterns) {
            java.util.regex.Pattern macPattern = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = macPattern.matcher(qrText);
            if (matcher.find()) {
                return matcher.group().toUpperCase();
            }
        }
        
        // If no pattern matches, check if the entire text looks like a MAC
        if (qrText.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")) {
            return qrText.toUpperCase();
        }
        
        return null;
    }
    
    private void filterDevices(String searchText) {
        try {
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
                
                // Search by MAC address (case insensitive)
                if (device.getMacAddress() != null && 
                    device.getMacAddress().toLowerCase().contains(searchLower)) {
                    filteredDevices.add(device);
                    continue;
                }
                
                // Search by device name (case insensitive)
                if (device.getName() != null && 
                    device.getName().toLowerCase().contains(searchLower)) {
                    filteredDevices.add(device);
                    continue;
                }
                
                // Search by partial MAC address (without colons)
                String macWithoutColons = device.getMacAddress() != null ? 
                    device.getMacAddress().replace(":", "").toLowerCase() : "";
                if (macWithoutColons.contains(searchLower.replace(":", ""))) {
                    filteredDevices.add(device);
                }
            }
            
            // Update the adapter with filtered results safely
            if (mDevicesListAdapter != null) {
                mDevicesListAdapter.setList(filteredDevices);
                
                // Show search results count
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
    
    private void clearSearch() {
        try {
            isClearingSearch = true; // Set flag to prevent recursive calls
            isSearchMode = false;
            
            if (binding.etSearch != null) {
                // Clear the search text safely
                binding.etSearch.setText("");
            }
            
            // Hide clear button safely
            if (binding.btnClearSearch != null) {
                binding.btnClearSearch.setVisibility(View.GONE);
            }
            
            // Clear filtered devices
            if (filteredDevices != null) {
                filteredDevices.clear();
            }
            
            // Show all discovered devices safely
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
            isClearingSearch = false; // Reset flag
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
        // Keep scanning active in background
        Log.d("ScanDebug", "App going to background, keeping scan active");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup HTTP logger
        if (httpLogger != null) {
            httpLogger.shutdown();
        }
        // Don't stop the background service - let it continue scanning
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
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(binding.recyclerView.getContext()));
        mDevicesListAdapter = new ScanDevicesListAdapter(R.layout.item_scan_device,null);

        binding.recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayout.VERTICAL));
        
        // Set up connect button click listener
        mDevicesListAdapter.setOnConnectClickListener(new ScanDevicesListAdapter.OnConnectClickListener() {
            @Override
            public void onConnectClick(MST03Entity device) {
                // Check if already connecting
                if (isConnecting) {
                    Toast.makeText(ScanDevicesListActivity.this, "Already connecting to a device", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Set connecting state
                isConnecting = true;
                
                // Disable all connect buttons
                mDevicesListAdapter.setConnectButtonsEnabled(false);
                
                // Disconnect any existing connection first
                if (mBleManager != null && mst03Entity != null) {
                    mBleManager.disConnect(mst03Entity.getMacAddress());
                }
                
                // Hide any existing device details card
                hideDeviceDetailsCard();
                
                // Set new device and connect
                mst03Entity = device;
                setKey(mst03Entity.getMacAddress());
                
                try {
                    connectedSensor();
                } catch (Exception e) {
                    Log.e("ScanDebug", "Error during connection: " + e.getMessage());
                    Toast.makeText(ScanDevicesListActivity.this, "Connection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Reset state on error
                    isConnecting = false;
                    mDevicesListAdapter.setConnectButtonsEnabled(true);
                    hideDeviceDetailsCard();
                }
                
                // Set connection timeout (30 seconds)
                connectionTimeoutHandler.postDelayed(() -> {
                    if (isConnecting) {
                        Log.d("ScanDebug", "Connection timeout - resetting state");
                        isConnecting = false;
                        mDevicesListAdapter.setConnectButtonsEnabled(true);
                        Toast.makeText(ScanDevicesListActivity.this, "Connection timeout", Toast.LENGTH_SHORT).show();
                        hideDeviceDetailsCard();
                    }
                }, 30000); // 30 seconds timeout
            }
        });

        binding.recyclerView.setAdapter(mDevicesListAdapter);
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
        mBleManager = MST03SensorBleManager.getInstance();
        Log.d("ScanDebug", "BLE manager initialized: " + (mBleManager != null));
    }
    
    private void ensureBleManagerReady() {
        if (mBleManager == null) {
            Log.d("ScanDebug", "Reinitializing BLE manager...");
            mBleManager = MST03SensorBleManager.getInstance();
            setBleManagerListener();
        }
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
            // Don't crash the app if service fails to start
        }
    }
    private void setBleManagerListener(){
        mBleManager.setOnConnStateListener(mConnStateListener);
    }
    private void removeBleManagerListener(){
        mBleManager.setOnConnStateListener(null);
    }

    private OnConnStateListener mConnStateListener = new OnConnStateListener() {
        @Override
        public void onUpdateConnState(String s, BleConnectionState mSensorConnectionState) {
            Log.d("ScanDebug", "Connection state changed: " + mSensorConnectionState + " for device: " + s);
            
            // Connection events are logged only for debugging
            if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                Log.d("ScanDebug", "Connection event: " + mSensorConnectionState + " for " + mst03Entity.getMacAddress());
            }
            
            switch (mSensorConnectionState){
                case Connecting:
                    Log.d("TAG","Connecting");
                    updateConnectionStatus("Connecting...");
                    break;
                case Connected:
                    Log.d("TAG","Connected");
                    updateConnectionStatus("Connected");
                    break;
                case ConnectComplete:
                    Log.d("TAG","ConnectComplete");
                    // Cancel connection timeout
                    connectionTimeoutHandler.removeCallbacksAndMessages(null);
                    updateConnectionStatus("Connection Complete - Fetching Data...");
                    // Fetch historical data immediately after connection
                    fetchHistoricalData();
                    break;
                case Disconnect:
                    Log.d("TAG","Disconnect");
                    // Cancel connection timeout
                    connectionTimeoutHandler.removeCallbacksAndMessages(null);
                    updateConnectionStatus("Disconnected");
                    // Reset connecting state and re-enable buttons
                    isConnecting = false;
                    mDevicesListAdapter.setConnectButtonsEnabled(true);
                    // Only hide card if it's the current device that disconnected
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
        Log.d("ScanDebug", "Starting BLE scan...");
        ensureBleManagerReady();
        mDevicesListAdapter.setList(null);
        // Use a longer scan duration and restart automatically
        mBleManager.startScan(this, 10 * 60 * 1000, new OnScanDevicesResultListener<MST03Entity>() {

            @Override
            public void onScanResult(List<MST03Entity> list) {
                Log.d("ScanDebug", "Scan result received. Found " + list.size() + " devices");
                
                if (list.size() > 0) {
                    // Update the master list of all discovered devices
                    updateAllDiscoveredDevices(list);
                    
                    for (MST03Entity device : list) {
                        Log.d("ScanDebug", "Device found: " + device.getMacAddress() + " - " + device.getName() + " (RSSI: " + device.getRssi() + ")");
                        
                        // Log scan data to HTTP server
                        float temperature = 0.0f;
                        int battery = 0;
                        String firmwareVersion = "Unknown";
                        
                        // Get temperature from combination frame
                        if (device.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME) != null) {
                            com.minew.ble.mst03.frames.CombinationFrame comboFrame = 
                                (com.minew.ble.mst03.frames.CombinationFrame) device.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
                            temperature = comboFrame.getTemperature();
                        }
                        
                        // Get device info from static frame
                        if (device.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME) != null) {
                            DeviceStaticInfoFrame staticFrame = 
                                (DeviceStaticInfoFrame) device.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME);
                            battery = staticFrame.getBattery();
                            firmwareVersion = staticFrame.getFirmwareVersion();
                        }
                        
                        httpLogger.logScanData(device.getMacAddress(), temperature, battery, firmwareVersion, device.getRssi());
                    }
                } else {
                    Log.d("ScanDebug", "No devices found in this scan result");
                }
                
                // Sort the list by RSSI (strongest signal first)
                list.sort(new Comparator<MST03Entity>() {
                    @Override
                    public int compare(MST03Entity o1, MST03Entity o2) {
                        return o2.getRssi() - o1.getRssi();
                    }
                });
                
                // Update the adapter based on search mode
                if (isSearchMode && !filteredDevices.isEmpty()) {
                    // In search mode, show filtered results
                    mDevicesListAdapter.setList(filteredDevices);
                } else {
                    // Normal mode, show all discovered devices
                    mDevicesListAdapter.setList(allDiscoveredDevices);
                }
            }

            @Override
            public void onStopScan(List<MST03Entity> list) {
                Log.d("ScanDebug", "Scan stopped. Total devices found: " + (list != null ? list.size() : 0));
                // Restart scan automatically after a short delay
                new android.os.Handler().postDelayed(() -> {
                    Log.d("ScanDebug", "Auto-restarting scan...");
                    startScan();
                }, 2000); // 2 second delay before restart
            }

        });
        mObjectAnimator.start();
    }
    
    private void updateAllDiscoveredDevices(List<MST03Entity> newDevices) {
        // Create a map of existing devices by MAC address for quick lookup
        java.util.Map<String, MST03Entity> existingDevicesMap = new java.util.HashMap<>();
        for (MST03Entity device : allDiscoveredDevices) {
            existingDevicesMap.put(device.getMacAddress(), device);
        }
        
        // Add or update devices from the new scan result
        for (MST03Entity newDevice : newDevices) {
            String macAddress = newDevice.getMacAddress();
            if (existingDevicesMap.containsKey(macAddress)) {
                // Update existing device with new data (RSSI, etc.)
                MST03Entity existingDevice = existingDevicesMap.get(macAddress);
                // Update RSSI and other dynamic properties
                existingDevice.setRssi(newDevice.getRssi());
                // You might want to update other properties as well
            } else {
                // Add new device
                allDiscoveredDevices.add(newDevice);
            }
        }
        
        // Sort all discovered devices by RSSI
        allDiscoveredDevices.sort(new Comparator<MST03Entity>() {
            @Override
            public int compare(MST03Entity o1, MST03Entity o2) {
                return o2.getRssi() - o1.getRssi();
            }
        });
        
        Log.d("ScanDebug", "Updated all discovered devices list. Total devices: " + allDiscoveredDevices.size());
    }

    private void stopScan(){
        mBleManager.stopScan(this);
        mObjectAnimator.cancel();
    }


    private void setKey(String mac){
        String key = "minewtech1234567";
        mBleManager.setSecretKey(mac,key);
    }
    private void connectedSensor(){
        // Validate device
        if (mst03Entity == null) {
            Log.e("ScanDebug", "Device is null, cannot connect");
            Toast.makeText(this, "Device not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Store device static info before connecting
        if (mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME) != null) {
            currentDeviceStaticInfo = (DeviceStaticInfoFrame) mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.DEVICE_INFORMATION_FRAME);
        }
        
        // Show device details card
        showDeviceDetailsCard();
        
        // Ensure BLE manager is properly initialized
        if (mBleManager == null) {
            Log.e("ScanDebug", "BLE manager is null, reinitializing...");
            mBleManager = MST03SensorBleManager.getInstance();
            setBleManagerListener();
        }
        
        // Connect to device
        Log.d("ScanDebug", "Attempting to connect to device: " + mst03Entity.getMacAddress());
        mBleManager.connect(this, mst03Entity);
    }
    
    private void showDeviceDetailsCard() {
        binding.deviceDetailsCard.setVisibility(View.VISIBLE);
        binding.llLoading.setVisibility(View.VISIBLE);
        binding.llDeviceInfo.setVisibility(View.GONE);
        binding.llHistoricalData.setVisibility(View.GONE);
        
        // Set device info
        if (currentDeviceStaticInfo != null) {
            binding.tvScanDeviceMac.setText("MAC: " + currentDeviceStaticInfo.getMacAddress());
            binding.tvScanDeviceBattery.setText("Battery: " + currentDeviceStaticInfo.getBattery() + "%");
            binding.tvScanDeviceFirmware.setText("Firmware: " + currentDeviceStaticInfo.getFirmwareVersion());
        } else {
            binding.tvScanDeviceMac.setText("MAC: " + mst03Entity.getMacAddress());
            binding.tvScanDeviceBattery.setText("Battery: Unknown");
            binding.tvScanDeviceFirmware.setText("Firmware: Unknown");
        }
        
        // Set up close button
        binding.btnCloseDetails.setOnClickListener(v -> hideDeviceDetailsCard());
    }
    
    private void hideDeviceDetailsCard() {
        binding.deviceDetailsCard.setVisibility(View.GONE);
        // Cancel connection timeout
        connectionTimeoutHandler.removeCallbacksAndMessages(null);
        // Disconnect if connected
        if (mst03Entity != null && mBleManager != null) {
            mBleManager.disConnect(mst03Entity.getMacAddress());
            Log.d("ScanDebug", "Disconnected from device: " + mst03Entity.getMacAddress());
        }
        // Reset state
        mst03Entity = null;
        currentDeviceStaticInfo = null;
        excursionDataList.clear();
        // Reset connecting state and re-enable buttons
        isConnecting = false;
        mDevicesListAdapter.setConnectButtonsEnabled(true);
    }
    
    private void updateConnectionStatus(String status) {
        binding.tvScanConnectionStatus.setText("Status: " + status);
    }
    
    private void fetchHistoricalData() {
        Log.d("ScanDebug", "Fetching historical data for device: " + mst03Entity.getMacAddress());
        
        long systemTime = System.currentTimeMillis() / 1000;
        // Query full 24 hours of data as requested by client
        long startTime = (systemTime - 3600 * 24) / 1000; // Last 24 hours
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
                        
                        // Send ALL data - no limitations
                        analyzeExcursions(allData);
                    } else {
                        Log.d("ScanDebug", "Failed to get historical data - success: " + success + ", historyHtData null: " + (historyHtData == null));
                        updateConnectionStatus("Failed to get data");
                        
                        // Even if historical data fails, try to send device info
                        if (mst03Entity != null) {
                            Log.d("ScanDebug", "Sending device info without historical data");
                            sendCompleteDeviceData(new ArrayList<>());
                        }
                    }
                }
            });
    }
    
    private void analyzeExcursions(List<HtData> htDataList) {
        excursionDataList.clear();
        
        for (HtData htData : htDataList) {
            float temperature = htData.getTemperature();
            long timestamp = htData.getTimestamps();
            
            // Check for excursions (outside 2-8°C range)
            if (temperature < 2.0f) {
                excursionDataList.add(new ExcursionData(temperature, timestamp, "LOW", mst03Entity.getMacAddress()));
            } else if (temperature > 8.0f) {
                excursionDataList.add(new ExcursionData(temperature, timestamp, "HIGH", mst03Entity.getMacAddress()));
            }
        }
        
        // Log excursions to file
        excursionLogger = new ExcursionLogger(this, mst03Entity.getMacAddress());
        excursionLogger.logExcursions(excursionDataList);
        
        // Send complete device data to HTTP server
        sendCompleteDeviceData(htDataList);
        
        // Update UI
        updateHistoricalDataUI(htDataList.size(), excursionDataList.size());
    }
    
    private void sendCompleteDeviceData(List<HtData> htDataList) {
        Log.d("ScanDebug", "sendCompleteDeviceData called with " + htDataList.size() + " historical records");
        
        if (mst03Entity == null) {
            Log.e("ScanDebug", "mst03Entity is null, cannot send device data");
            return;
        }
        
        // Get device info
        String deviceMac = mst03Entity.getMacAddress();
        String deviceName = mst03Entity.getName() != null ? mst03Entity.getName() : "Unknown";
        float currentTemperature = 0.0f;
        String firmwareVersion = "Unknown";
        int batteryLevel = 0;
        int rssi = mst03Entity.getRssi();
        
        Log.d("ScanDebug", "Device info - MAC: " + deviceMac + ", Name: " + deviceName + ", RSSI: " + rssi);
        
        // Get current temperature from combination frame
        if (mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME) != null) {
            com.minew.ble.mst03.frames.CombinationFrame comboFrame = 
                (com.minew.ble.mst03.frames.CombinationFrame) mst03Entity.getMinewFrame(com.minew.ble.v3.enums.FrameType.COMBINATION_FRAME);
            currentTemperature = comboFrame.getTemperature();
            Log.d("ScanDebug", "Current temperature from combo frame: " + currentTemperature);
        } else {
            Log.d("ScanDebug", "No combination frame available for temperature");
        }
        
        // Get device info from static frame
        if (currentDeviceStaticInfo != null) {
            batteryLevel = currentDeviceStaticInfo.getBattery();
            firmwareVersion = currentDeviceStaticInfo.getFirmwareVersion();
            Log.d("ScanDebug", "Device static info - Battery: " + batteryLevel + "%, Firmware: " + firmwareVersion);
        } else {
            Log.d("ScanDebug", "No device static info available");
        }
        
        Log.d("ScanDebug", "About to send complete device data to HTTP server");
        
        // Send complete device data
        httpLogger.logCompleteDeviceData(deviceMac, deviceName, currentTemperature, firmwareVersion, 
                                        batteryLevel, rssi, htDataList, excursionDataList);
        
        Log.d("ScanDebug", "Complete device data sent, scheduling disconnect and scan resume");
        
        // Show toast for excursion data upload
        if (excursionDataList.size() > 0) {
            Toast.makeText(this, "Excursion data uploaded", Toast.LENGTH_SHORT).show();
        }
        
        // Disconnect and resume scanning after a short delay
        new android.os.Handler().postDelayed(() -> {
            Log.d("ScanDebug", "Disconnecting and resuming scan after data collection");
            hideDeviceDetailsCard();
            startScan();
        }, 2000); // 2 second delay to ensure data is sent
    }
    
    private void updateHistoricalDataUI(int totalReadings, int excursionCount) {
        binding.llLoading.setVisibility(View.GONE);
        binding.llDeviceInfo.setVisibility(View.VISIBLE);
        // Hide historical data section - don't show excursion data to user
        binding.llHistoricalData.setVisibility(View.GONE);
        
        // Don't update excursion UI elements since we're hiding them
        updateConnectionStatus("Data Analysis Complete");
    }
    
    private void viewLogFile() {
        String logPath = excursionLogger.getLogFilePath();
        if (logPath != null) {
            Log.d("ScanDebug", "Log file path: " + logPath);
            // Show log file content in a dialog or new activity
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
                
                // Show content in a dialog
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
