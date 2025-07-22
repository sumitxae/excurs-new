package choruscoldchain.app;

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
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;

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
    private boolean isSearchMode = false;
    private boolean isClearingSearch = false; 
    private boolean isConnecting = false; 
    private android.os.Handler connectionTimeoutHandler = new android.os.Handler();
    private android.os.Handler searchDebounceHandler = new android.os.Handler();
    private static final int SEARCH_DEBOUNCE_DELAY = 500;
    private DeviceDiscoveryManager deviceManager;
    private DeviceDiscoveryManager.OnDevicesUpdatedListener deviceUpdateListener; 
    private boolean permissionsGranted = false;
    private ImageView ivChorusLogo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
                runOnUiThread(() -> {
                    updateAllDiscoveredDevices(devices);

                    if (isSearchMode && binding.etSearch.getText().toString().trim().length() > 0) {

                        String currentSearchText = binding.etSearch.getText().toString().trim();
                        filterDevices(currentSearchText);
                    } else {
                        mDevicesListAdapter.setList(allDiscoveredDevices);
                        mDevicesListAdapter.notifyDataSetChanged();
                    }
                });
            }
        };
        deviceManager.addListener(deviceUpdateListener);

        httpLogger = new HttpLogger();
        setupSearchFunctionality();
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
            // Check if storage permission was granted
            if (hasStoragePermissions()) {
                Log.d("BeaconRawData", "Storage permission granted via settings");
                // Retry CSV generation
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
        setBleManagerListener();
        startScan();
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeBleManagerListener();
        
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (connectionTimeoutHandler != null) {
            connectionTimeoutHandler.removeCallbacksAndMessages(null);
        }
        
        if (searchDebounceHandler != null) {
            searchDebounceHandler.removeCallbacksAndMessages(null);
        }
        
        if (deviceManager != null && deviceUpdateListener != null) {
            deviceManager.removeListener(deviceUpdateListener);
        }
        
        if (httpLogger != null) {
            httpLogger.shutdown();
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
        
        mDevicesListAdapter.setConnectButtonsEnabled(false);
        
        WaitDialog.show("Connecting to device...");
        
        connectionTimeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnecting) {
                    
                    isConnecting = false;
                    mDevicesListAdapter.setConnectButtonsEnabled(true);
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
        
        connectionTimeoutHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnecting) {
                    
                }
            }
        }, 3000);
        
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
            mDevicesListAdapter.setConnectButtonsEnabled(true);
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
                return;
            }
            
            setBleManagerListener();
            
        } catch (Exception e) {
            Log.e("ScanDebug", "Error initializing BLE manager: " + e.getMessage());
            Toast.makeText(this, "Error initializing Bluetooth: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void ensureBleManagerReady() {
        if (mBleManager == null) {
            
            initBleManager();
        }

        setBleManagerListener();
        
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

    private OnConnStateListener mConnStateListener = new OnConnStateListener() {
        @Override
        public void onUpdateConnState(String s, BleConnectionState mSensorConnectionState) {
            
            if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                
            }
            
            switch (mSensorConnectionState) {
                case Connecting:
                    Log.d("TAG", "Connecting");
                    updateConnectionStatus("Connecting...");
                    break;
                case Connected:
                    Log.d("TAG", "Connected");
                    updateConnectionStatus("Connected - Authenticating...");
                    
                    if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                        
                        setKey(s);
                    }
                    break;
                case AuthenticateSuccess:
                    Log.d("TAG", "AuthenticateSuccess");
                    updateConnectionStatus("Authenticated Successfully");
                    
                    break;
                case AuthenticateFail:
                    Log.d("TAG", "AuthenticateFail");
                    updateConnectionStatus("Authentication Failed");
                    Log.e("ScanDebug", "Authentication failed for device: " + s);
                    
                    if (mst03Entity != null && s.equals(mst03Entity.getMacAddress())) {
                        connectionTimeoutHandler.removeCallbacksAndMessages(null);
                        WaitDialog.dismiss();
                        
                        runOnUiThread(() -> {
                            Toast.makeText(ScanDevicesListActivity.this, "Authentication failed. Please try again.",
                                    Toast.LENGTH_LONG).show();
                            isConnecting = false;
                            mDevicesListAdapter.setConnectButtonsEnabled(true);
                            
                            if (mBleManager != null) {
                                mBleManager.disConnect(s);
                            }
                        });
                    }
                    break;
                case ConnectComplete:
                    Log.d("TAG", "ConnectComplete");
                    
                    connectionTimeoutHandler.removeCallbacksAndMessages(null);
                    
                    WaitDialog.dismiss();
                    
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
                    
                    connectedSensor();
                    
                    Intent intent = DeviceDetailsActivity.newIntent(ScanDevicesListActivity.this, mst03Entity,
                            batteryLevel, firmwareVersion, currentTemperature);
                    startActivity(intent);
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

                    startBackgroundScanService();
                    Log.d("ScanDebug", "Called startBackgroundScanService() after disconnect");
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
        if (!permissionsGranted || mObjectAnimator == null)
            return;

        if (isBackgroundServiceRunning()) {
            
        } else {
            
            startBackgroundScanService();
        }
        
        mObjectAnimator.start();
    }
    
    private void updateAllDiscoveredDevices(List<MST03Entity> newDevices) {
        java.util.Map<String, Integer> existingDevicesIndexMap = new java.util.HashMap<>();
        for (int i = 0; i < allDiscoveredDevices.size(); i++) {
            existingDevicesIndexMap.put(allDiscoveredDevices.get(i).getMacAddress(), i);
        }
        for (MST03Entity newDevice : newDevices) {
            String macAddress = newDevice.getMacAddress();
            if (existingDevicesIndexMap.containsKey(macAddress)) {

                int index = existingDevicesIndexMap.get(macAddress);
                allDiscoveredDevices.set(index, newDevice);
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
        Log.d("BeaconRawData", "fetchHistoricalDataForLocalDisplay called");
        
        isDataProcessingComplete = false;
        updateProcessingStatus("Fetching");
        
        long systemTime = System.currentTimeMillis() / 1000;

        long startTime = systemTime - (24 * 60 * 60);
        long endTime = systemTime;

        try {
            
            mBleManager.queryHistoryData(mst03Entity.getMacAddress(), 1, startTime, endTime, systemTime, 
                new OnQueryResultListener<HistoryHtData>() {
                    @Override
                    public void OnQueryResult(boolean success, HistoryHtData historyHtData) {
                            Log.d("BeaconRawData",
                                    "OnQueryResult called, success=" + success + ", historyHtData=" + historyHtData);
                        
                        if (success && historyHtData != null) {
                            List<HtData> allData = historyHtData.getHistoryDataList();
                            
                            if (allData.isEmpty()) {
                                
                                tryFallbackQuery(systemTime);
                            } else {
                                
                                    processHistoricalDataForLocalDisplayUltraOptimized(allData);
                            }
                        } else {
                            
                            tryFallbackQuery(systemTime);
                        }
                    }
                });
        } catch (Exception e) {
            Log.e("ScanDebug", "Exception during historical data query: " + e.getMessage());
            e.printStackTrace();
            
            tryFallbackQuery(systemTime);
        }
    }
    
    private void tryFallbackQuery(long systemTime) {
        Log.d("BeaconRawData", "tryFallbackQuery called");
        try {
            mBleManager.queryHistoryData(mst03Entity.getMacAddress(), 0, 0, 0, systemTime, 
                new OnQueryResultListener<HistoryHtData>() {
                    @Override
                    public void OnQueryResult(boolean fallbackSuccess, HistoryHtData fallbackHistoryHtData) {
                            Log.d("BeaconRawData", "Fallback OnQueryResult called, success=" + fallbackSuccess
                                    + ", historyHtData=" + fallbackHistoryHtData);
                        
                        if (fallbackSuccess && fallbackHistoryHtData != null) {
                            List<HtData> fallbackData = fallbackHistoryHtData.getHistoryDataList();
                            
                            if (fallbackData.isEmpty()) {
                                
                                handleNoDataAvailable();
                            } else {
                                
                                    processHistoricalDataForLocalDisplayUltraOptimized(fallbackData);
                            }
                        } else {
                            
                            handleNoDataAvailable();
                        }
                    }
                });
        } catch (Exception e) {
            Log.e("ScanDebug", "Exception during fallback query: " + e.getMessage());
            e.printStackTrace();
            handleNoDataAvailable();
        }
    }
    
    private void handleNoDataAvailable() {
        updateProcessingStatus("Completed");
        isDataProcessingComplete = true;
        
        if (mst03Entity != null && mBleManager != null) {
            mBleManager.disConnect(mst03Entity.getMacAddress());
            
            isConnecting = false;
            mDevicesListAdapter.setConnectButtonsEnabled(true);
        }
    }

    private void processHistoricalDataForLocalDisplayUltraOptimized(List<HtData> htDataList) {
        if (htDataList == null || htDataList.isEmpty()) {
            Log.d("BeaconRawData", "No data to process");
        isDataProcessingComplete = true;
            updateProcessingStatus("Completed");
            cleanupDeviceConnection();
            return;
        }

        updateProcessingStatus("Fetch Completed");
        
        int dataSize = htDataList.size();
        int sampleSize = Math.min(dataSize, 1000);

        processHistoricalDataForTripDetectionAdaptive(htDataList);
            return;

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
            mDevicesListAdapter.setConnectButtonsEnabled(true);
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
    
    public static void clearProcessedData() {
        processedHistoricalData.clear();
        processedExcursionData.clear();
        isDataProcessingComplete = false;
        currentProcessingStatus = "Idle";
    }
    
    private void updateConnectionStatus(String status) {
        binding.tvScanConnectionStatus.setText("Status: " + status);
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
        mDevicesListAdapter.setConnectButtonsEnabled(true);
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

        // Use the correct trip detection logic for optimized approach
        List<HtData> tripData = new ArrayList<>();
        List<ExcursionData> tripExcursions = new ArrayList<>();

        // Find the latest excursion with no normal after it (going backwards)
        int latestExcursionIndex = -1;

        // Process from latest (current time) to oldest
        for (int i = htDataList.size() - 1; i >= 0; i--) {
            HtData htData = htDataList.get(i);
            float temperature = htData.getTemperature();
            boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);
            
            if (!isNormal) {
                // Found an excursion - mark it as potential trip start
                latestExcursionIndex = i;
            } else {
                // Found a normal reading - this invalidates any excursions before it
                // The excursion we found before this normal is our trip start
                break; // Stop here - no need to continue
            }
        }

        // Extract trip data: from latest excursion to current time
        if (latestExcursionIndex != -1) {
            // Trip data: from latest excursion to current time
            tripData = htDataList.subList(latestExcursionIndex, htDataList.size());

            // Find all excursions within the trip period
            for (HtData htData : tripData) {
                float temperature = htData.getTemperature();
                if (temperature < 2.0f || temperature > 8.0f) {
                    tripExcursions.add(new ExcursionData(temperature, htData.getTimestamps(),
                        temperature < 2.0f ? "LOW" : "HIGH", mst03Entity.getMacAddress()));
                }
            }

            long tripDurationMinutes = tripData.isEmpty() ? 0 : 
                (tripData.get(tripData.size() - 1).getTimestamps() - tripData.get(0).getTimestamps()) / (1000 * 60);

            Log.d("BeaconRawData", "Optimized trip detected: " + tripData.size() + " records from index " +
                latestExcursionIndex + " to " + (htDataList.size() - 1) +
                " with " + tripExcursions.size() + " excursions. Duration: " + tripDurationMinutes + " minutes");
        } else {
            // No excursion found - use recent data (last 1000 records or all if less)
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

            tripData = htDataList.subList(firstExcursionStartIndex, htDataList.size());

            for (HtData htData : tripData) {
                float temperature = htData.getTemperature();
                if (temperature < 2.0f || temperature > 8.0f) {
                    tripExcursions.add(new ExcursionData(temperature, htData.getTimestamps(),
                            temperature < 2.0f ? "LOW" : "HIGH", mst03Entity.getMacAddress()));
                }
            }

            Log.d("BeaconRawData", "Optimized trip detected: " + tripData.size() + " records from index " +
                    firstExcursionStartIndex + " with " + tripExcursions.size() + " excursions");
        } else {

            int recentSize = Math.min(1000, htDataList.size());
            int startIndex = htDataList.size() - recentSize;
            tripData = htDataList.subList(startIndex, htDataList.size());
            Log.d("BeaconRawData", "No excursion trip found, using recent " + tripData.size() + " records");
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

        // Generate all CSV files for testing purposes
        generateAllCSVFiles(tripData, tripExcursions);

        isDataProcessingComplete = true;
        cleanupDeviceConnection();
    }

    // Generate CSV file with trip data for testing
    private void generateTripDataCSV(List<HtData> tripData, List<ExcursionData> tripExcursions) {
        try {
            // Create CSV file with timestamp
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            String deviceMac = mst03Entity != null ? mst03Entity.getMacAddress().replace(":", "_") : "unknown_device";
            String fileName = "trip_data_" + deviceMac + "_" + timestamp + ".csv";

            // Try to save in Downloads directory (accessible in Android 15)
            java.io.File csvFile = saveToDownloadsDirectory(fileName);
            if (csvFile == null) {
                // Fallback to app's external files directory
                java.io.File externalDir = getExternalFilesDir(null);
                if (externalDir == null) {
                    Log.e("BeaconRawData", "No accessible directory available");
                    return;
                }
                csvFile = new java.io.File(externalDir, fileName);
            }

            // Write CSV data
            java.io.FileWriter writer = new java.io.FileWriter(csvFile);
            java.io.BufferedWriter bufferedWriter = new java.io.BufferedWriter(writer);

            // Write comprehensive CSV header with all relevant information
            bufferedWriter.write(
                    "Record Number,Unix Timestamp,Date & Time,Temperature (°C),Humidity (%),Temperature Status,Excursion Type,Device MAC Address,Trip Duration (minutes),Notes\n");

            // Calculate trip statistics for reference
            long tripStartTime = tripData.isEmpty() ? 0 : tripData.get(0).getTimestamps();
            long tripEndTime = tripData.isEmpty() ? 0 : tripData.get(tripData.size() - 1).getTimestamps();
            long tripDurationMinutes = tripData.isEmpty() ? 0 : (tripEndTime - tripStartTime) / (1000 * 60);

            // Write all trip data records
            for (int i = 0; i < tripData.size(); i++) {
                HtData htData = tripData.get(i);
                long timestamp_ms = htData.getTimestamps();

                // Format date and time in readable format
                String dateTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date(timestamp_ms));

            float temperature = htData.getTemperature();
                float humidity = htData.getHumidity();

                // Determine temperature status and excursion type
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

                // Calculate time from trip start
                long timeFromStart = tripData.isEmpty() ? 0 : (timestamp_ms - tripStartTime) / (1000 * 60);

                // Write comprehensive CSV line with all data
                bufferedWriter.write(String.format("%d,%d,%s,%.2f,%.2f,%s,%s,%s,%d,%s\n",
                        i + 1, // Record Number
                        timestamp_ms, // Unix Timestamp
                        dateTime, // Date & Time
                        temperature, // Temperature (°C)
                        humidity, // Humidity (%)
                        temperatureStatus, // Temperature Status
                        excursionType, // Excursion Type
                        deviceMacAddress, // Device MAC Address
                        timeFromStart, // Trip Duration (minutes from start)
                        notes // Notes
                ));
            }

            bufferedWriter.close();
            writer.close();

            Log.d("BeaconRawData", "Comprehensive CSV file generated: " + csvFile.getAbsolutePath());
            Log.d("BeaconRawData",
                    "Trip data: " + tripData.size() + " records, " + tripExcursions.size() + " excursions");
            Log.d("BeaconRawData", "Trip duration: " + tripDurationMinutes + " minutes");

            // Show success message with file location and summary
            final String finalFileName = fileName;
            final String fileParent = csvFile.getParent();
                        runOnUiThread(() -> {
                String message = "CSV saved: " + finalFileName + " (" + tripData.size() + " records)";
                if (fileParent != null && fileParent.contains("Download")) {
                    message += " (in Downloads folder)";
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            Log.e("BeaconRawData", "Error generating CSV: " + e.getMessage());
            e.printStackTrace();

            runOnUiThread(() -> {
                Toast.makeText(this, "Error generating CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    // Generate detailed excursion report CSV
    private void generateExcursionReportCSV(List<ExcursionData> excursions) {
        if (excursions.isEmpty()) {
            Log.d("BeaconRawData", "No excursions to report");
            return;
        }

        try {
            // Create CSV file with timestamp
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            String deviceMac = mst03Entity != null ? mst03Entity.getMacAddress().replace(":", "_") : "unknown_device";
            String fileName = "excursion_report_" + deviceMac + "_" + timestamp + ".csv";

            // Try to save in Downloads directory
            java.io.File csvFile = saveToDownloadsDirectory(fileName);
            if (csvFile == null) {
                // Fallback to app's external files directory
                java.io.File externalDir = getExternalFilesDir(null);
                if (externalDir == null) {
                    Log.e("BeaconRawData", "No accessible directory available");
                    return;
                }
                csvFile = new java.io.File(externalDir, fileName);
            }

            // Write CSV data
            java.io.FileWriter writer = new java.io.FileWriter(csvFile);
            java.io.BufferedWriter bufferedWriter = new java.io.BufferedWriter(writer);

            // Write comprehensive CSV header
            bufferedWriter.write(
                    "Excursion Number,Unix Timestamp,Date & Time,Temperature (°C),Excursion Type,Device MAC Address,Severity Level,Description,Duration (seconds),Duration (minutes),Risk Assessment\n");

            // Group excursions by type and calculate duration
            java.util.Map<String, java.util.List<ExcursionData>> excursionsByType = new java.util.HashMap<>();
            for (ExcursionData excursion : excursions) {
                String type = excursion.getExcursionType();
                excursionsByType.computeIfAbsent(type, k -> new java.util.ArrayList<>()).add(excursion);
            }

            int excursionNumber = 1;
            // Write excursion data with detailed analysis
            for (java.util.Map.Entry<String, java.util.List<ExcursionData>> entry : excursionsByType.entrySet()) {
                String type = entry.getKey();
                java.util.List<ExcursionData> typeExcursions = entry.getValue();

                // Sort by timestamp
                typeExcursions.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

                long startTime = typeExcursions.get(0).getTimestamp();
                long endTime = typeExcursions.get(typeExcursions.size() - 1).getTimestamp();
                long durationSeconds = endTime - startTime;
                long durationMinutes = durationSeconds / 60;

                for (ExcursionData excursion : typeExcursions) {
                    long timestamp_ms = excursion.getTimestamp();
                    String dateTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                            java.util.Locale.getDefault())
                            .format(new java.util.Date(timestamp_ms));

                    String deviceMacAddress = mst03Entity != null ? mst03Entity.getMacAddress() : "unknown";
                    float temperature = excursion.getTemperature();

                    // Determine severity level and description
                    String severityLevel;
                    String description;
                    String riskAssessment;

                    if ("LOW".equals(type)) {
                        if (temperature < 0.0f) {
                            severityLevel = "CRITICAL";
                            description = "Extreme low temperature - Risk of freezing";
                            riskAssessment = "HIGH RISK - Immediate action required";
                        } else if (temperature < 1.0f) {
                            severityLevel = "HIGH";
                            description = "Very low temperature - Significant risk";
                            riskAssessment = "HIGH RISK - Action required";
                        } else {
                            severityLevel = "MEDIUM";
                            description = "Low temperature - Moderate risk";
                            riskAssessment = "MEDIUM RISK - Monitor closely";
                        }
                    } else if ("HIGH".equals(type)) {
                        if (temperature > 15.0f) {
                            severityLevel = "CRITICAL";
                            description = "Extreme high temperature - Risk of spoilage";
                            riskAssessment = "HIGH RISK - Immediate action required";
                        } else if (temperature > 12.0f) {
                            severityLevel = "HIGH";
                            description = "Very high temperature - Significant risk";
                            riskAssessment = "HIGH RISK - Action required";
                        } else {
                            severityLevel = "MEDIUM";
                            description = "High temperature - Moderate risk";
                            riskAssessment = "MEDIUM RISK - Monitor closely";
                        }
                    } else {
                        severityLevel = "NORMAL";
                        description = "Temperature within normal range";
                        riskAssessment = "LOW RISK - Normal operation";
                    }

                    // Write comprehensive CSV line
                    bufferedWriter.write(String.format("%d,%d,%s,%.2f,%s,%s,%s,%s,%d,%d,%s\n",
                            excursionNumber++, // Excursion Number
                            timestamp_ms, // Unix Timestamp
                            dateTime, // Date & Time
                            temperature, // Temperature (°C)
                            type, // Excursion Type
                            deviceMacAddress, // Device MAC Address
                            severityLevel, // Severity Level
                            description, // Description
                            durationSeconds, // Duration (seconds)
                            durationMinutes, // Duration (minutes)
                            riskAssessment // Risk Assessment
                    ));
                }
            }

            bufferedWriter.close();
            writer.close();

            Log.d("BeaconRawData", "Comprehensive excursion report CSV generated: " + csvFile.getAbsolutePath());
            Log.d("BeaconRawData", "Total excursions: " + excursions.size());

        } catch (Exception e) {
            Log.e("BeaconRawData", "Error generating excursion report CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Generate trip summary CSV
    private void generateTripSummaryCSV(List<HtData> tripData, List<ExcursionData> tripExcursions) {
        try {
            // Create CSV file with timestamp
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            String deviceMac = mst03Entity != null ? mst03Entity.getMacAddress().replace(":", "_") : "unknown_device";
            String fileName = "trip_summary_" + deviceMac + "_" + timestamp + ".csv";

            // Try to save in Downloads directory
            java.io.File csvFile = saveToDownloadsDirectory(fileName);
            if (csvFile == null) {
                // Fallback to app's external files directory
                java.io.File externalDir = getExternalFilesDir(null);
                if (externalDir == null) {
                    Log.e("BeaconRawData", "No accessible directory available");
                    return;
                }
                csvFile = new java.io.File(externalDir, fileName);
            }

            // Write CSV data
            java.io.FileWriter writer = new java.io.FileWriter(csvFile);
            java.io.BufferedWriter bufferedWriter = new java.io.BufferedWriter(writer);

            // Write comprehensive CSV header
            bufferedWriter.write("Category,Parameter,Value,Unit,Description\n");

            // Calculate comprehensive trip statistics
            if (!tripData.isEmpty()) {
                long tripStartTime = tripData.get(0).getTimestamps();
                long tripEndTime = tripData.get(tripData.size() - 1).getTimestamps();
                long tripDurationSeconds = tripEndTime - tripStartTime;
                long tripDurationMinutes = tripDurationSeconds / 60;
                long tripDurationHours = tripDurationMinutes / 60;

                String tripStartDateTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.getDefault())
                        .format(new java.util.Date(tripStartTime));
                String tripEndDateTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.getDefault())
                        .format(new java.util.Date(tripEndTime));

                // Calculate temperature statistics
                float minTemp = Float.MAX_VALUE, maxTemp = Float.MIN_VALUE, avgTemp = 0;
                float minHumidity = Float.MAX_VALUE, maxHumidity = Float.MIN_VALUE, avgHumidity = 0;

                // Track temperature ranges
                int normalTempCount = 0, lowTempCount = 0, highTempCount = 0;
                int criticalLowCount = 0, criticalHighCount = 0;

                for (HtData htData : tripData) {
                    float temp = htData.getTemperature();
                    float humidity = htData.getHumidity();

                    minTemp = Math.min(minTemp, temp);
                    maxTemp = Math.max(maxTemp, temp);
                    avgTemp += temp;

                    minHumidity = Math.min(minHumidity, humidity);
                    maxHumidity = Math.max(maxHumidity, humidity);
                    avgHumidity += humidity;

                    // Count temperature ranges
                    if (temp >= 2.0f && temp <= 8.0f) {
                        normalTempCount++;
                    } else if (temp < 2.0f) {
                        lowTempCount++;
                        if (temp < 0.0f)
                            criticalLowCount++;
        } else {
                        highTempCount++;
                        if (temp > 15.0f)
                            criticalHighCount++;
                    }
                }

                avgTemp /= tripData.size();
                avgHumidity /= tripData.size();

                // Count excursions by type and severity
                int lowExcursions = 0, highExcursions = 0;
                int criticalExcursions = 0, highSeverityExcursions = 0, mediumSeverityExcursions = 0;

                for (ExcursionData excursion : tripExcursions) {
                    float temp = excursion.getTemperature();
                    if ("LOW".equals(excursion.getExcursionType())) {
                        lowExcursions++;
                        if (temp < 0.0f)
                            criticalExcursions++;
                        else if (temp < 1.0f)
                            highSeverityExcursions++;
                        else
                            mediumSeverityExcursions++;
                    } else if ("HIGH".equals(excursion.getExcursionType())) {
                        highExcursions++;
                        if (temp > 15.0f)
                            criticalExcursions++;
                        else if (temp > 12.0f)
                            highSeverityExcursions++;
                        else
                            mediumSeverityExcursions++;
                    }
                }

                // Calculate percentages
                float normalTempPercentage = (normalTempCount * 100.0f) / tripData.size();
                float excursionPercentage = (tripExcursions.size() * 100.0f) / tripData.size();
                float criticalExcursionPercentage = (criticalExcursions * 100.0f) / tripExcursions.size();

                // Write comprehensive summary data
                bufferedWriter.write("Device Information,Device MAC,"
                        + (mst03Entity != null ? mst03Entity.getMacAddress() : "unknown") + ",,\n");
                bufferedWriter.write("Device Information,Device Name,"
                        + (mst03Entity != null ? mst03Entity.getName() : "unknown") + ",,\n");

                bufferedWriter.write("Trip Timeline,Trip Start," + tripStartDateTime + ",,\n");
                bufferedWriter.write("Trip Timeline,Trip End," + tripEndDateTime + ",,\n");
                bufferedWriter.write("Trip Timeline,Trip Duration (seconds)," + tripDurationSeconds + ",seconds,\n");
                bufferedWriter.write("Trip Timeline,Trip Duration (minutes)," + tripDurationMinutes + ",minutes,\n");
                bufferedWriter.write("Trip Timeline,Trip Duration (hours)," + tripDurationHours + ",hours,\n");

                bufferedWriter.write("Data Statistics,Total Records," + tripData.size() + ",count,\n");
                bufferedWriter.write("Data Statistics,Data Collection Rate,"
                        + String.format("%.2f", (tripData.size() * 60.0 / tripDurationMinutes)) + ",records/minute,\n");

                bufferedWriter
                        .write("Temperature Analysis,Minimum Temperature," + String.format("%.2f", minTemp) + ",°C,\n");
                bufferedWriter
                        .write("Temperature Analysis,Maximum Temperature," + String.format("%.2f", maxTemp) + ",°C,\n");
                bufferedWriter
                        .write("Temperature Analysis,Average Temperature," + String.format("%.2f", avgTemp) + ",°C,\n");
                bufferedWriter.write("Temperature Analysis,Temperature Range,"
                        + String.format("%.2f", maxTemp - minTemp) + ",°C,\n");

                bufferedWriter
                        .write("Humidity Analysis,Minimum Humidity," + String.format("%.2f", minHumidity) + ",%,\n");
                bufferedWriter
                        .write("Humidity Analysis,Maximum Humidity," + String.format("%.2f", maxHumidity) + ",%,\n");
                bufferedWriter
                        .write("Humidity Analysis,Average Humidity," + String.format("%.2f", avgHumidity) + ",%,\n");
                bufferedWriter.write("Humidity Analysis,Humidity Range,"
                        + String.format("%.2f", maxHumidity - minHumidity) + ",%,\n");

                bufferedWriter
                        .write("Temperature Distribution,Normal Temperature Records," + normalTempCount + ",count,\n");
                bufferedWriter.write("Temperature Distribution,Normal Temperature Percentage,"
                        + String.format("%.2f", normalTempPercentage) + ",%,\n");
                bufferedWriter.write("Temperature Distribution,Low Temperature Records," + lowTempCount + ",count,\n");
                bufferedWriter
                        .write("Temperature Distribution,High Temperature Records," + highTempCount + ",count,\n");
                bufferedWriter.write(
                        "Temperature Distribution,Critical Low Records (<0°C)," + criticalLowCount + ",count,\n");
                bufferedWriter.write(
                        "Temperature Distribution,Critical High Records (>15°C)," + criticalHighCount + ",count,\n");

                bufferedWriter.write("Excursion Analysis,Total Excursions," + tripExcursions.size() + ",count,\n");
                bufferedWriter.write("Excursion Analysis,Excursion Percentage,"
                        + String.format("%.2f", excursionPercentage) + ",%,\n");
                bufferedWriter.write("Excursion Analysis,Low Temperature Excursions," + lowExcursions + ",count,\n");
                bufferedWriter.write("Excursion Analysis,High Temperature Excursions," + highExcursions + ",count,\n");
                bufferedWriter.write("Excursion Analysis,Critical Excursions," + criticalExcursions + ",count,\n");
                bufferedWriter.write("Excursion Analysis,Critical Excursion Percentage,"
                        + String.format("%.2f", criticalExcursionPercentage) + ",%,\n");
                bufferedWriter
                        .write("Excursion Analysis,High Severity Excursions," + highSeverityExcursions + ",count,\n");
                bufferedWriter.write(
                        "Excursion Analysis,Medium Severity Excursions," + mediumSeverityExcursions + ",count,\n");

                // Risk assessment
                String overallRiskLevel;
                String riskDescription;
                if (criticalExcursions > 0) {
                    overallRiskLevel = "CRITICAL";
                    riskDescription = "Critical temperature excursions detected - Immediate action required";
                } else if (highSeverityExcursions > 0) {
                    overallRiskLevel = "HIGH";
                    riskDescription = "High severity excursions detected - Action required";
                } else if (mediumSeverityExcursions > 0) {
                    overallRiskLevel = "MEDIUM";
                    riskDescription = "Medium severity excursions detected - Monitor closely";
                } else if (tripExcursions.size() > 0) {
                    overallRiskLevel = "LOW";
                    riskDescription = "Minor excursions detected - Normal monitoring";
                } else {
                    overallRiskLevel = "NONE";
                    riskDescription = "No excursions detected - Perfect temperature control";
                }

                bufferedWriter.write("Risk Assessment,Overall Risk Level," + overallRiskLevel + ",,\n");
                bufferedWriter.write("Risk Assessment,Risk Description," + riskDescription + ",,\n");
                bufferedWriter.write("Risk Assessment,Compliance Status,"
                        + (tripExcursions.size() == 0 ? "COMPLIANT" : "NON-COMPLIANT") + ",,\n");

                // Recommendations
                String recommendations = "";
                if (criticalExcursions > 0) {
                    recommendations = "Immediate action required: Check refrigeration system, verify temperature controls";
                } else if (highSeverityExcursions > 0) {
                    recommendations = "Action required: Review temperature monitoring, check equipment";
                } else if (mediumSeverityExcursions > 0) {
                    recommendations = "Monitor closely: Consider preventive maintenance";
                } else if (tripExcursions.size() > 0) {
                    recommendations = "Minor issues: Continue monitoring, consider optimization";
        } else {
                    recommendations = "Excellent performance: Maintain current practices";
                }

                bufferedWriter.write("Recommendations,Action Required," + recommendations + ",,\n");
                bufferedWriter.write("Recommendations,Next Review,Within 24 hours,,\n");
            }

            bufferedWriter.close();
            writer.close();

            Log.d("BeaconRawData", "Comprehensive trip summary CSV generated: " + csvFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e("BeaconRawData", "Error generating trip summary CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processHistoricalDataForTripDetectionParallel(List<HtData> htDataList) {
        if (htDataList == null || htDataList.isEmpty()) {
            Log.d("BeaconRawData", "No data to process");
            isDataProcessingComplete = true;
            cleanupDeviceConnection();
            return;
        }
        
        // Use the correct trip detection logic for parallel approach
        List<HtData> tripData = new ArrayList<>();
        List<ExcursionData> tripExcursions = new ArrayList<>();

        // Find the latest excursion with no normal after it (going backwards)
        int latestExcursionIndex = -1;

        // Process from latest (current time) to oldest
        for (int i = htDataList.size() - 1; i >= 0; i--) {
            HtData htData = htDataList.get(i);
            float temperature = htData.getTemperature();
            boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);
            
            if (!isNormal) {
                // Found an excursion - mark it as potential trip start
                latestExcursionIndex = i;
            } else {
                // Found a normal reading - this invalidates any excursions before it
                // The excursion we found before this normal is our trip start
                break; // Stop here - no need to continue
            }
        }

        // Extract trip data: from latest excursion to current time
        if (latestExcursionIndex != -1) {
            // Trip data: from latest excursion to current time
            tripData = htDataList.subList(latestExcursionIndex, htDataList.size());

            // Find all excursions within the trip period
            for (HtData htData : tripData) {
                float temperature = htData.getTemperature();
                if (temperature < 2.0f || temperature > 8.0f) {
                    tripExcursions.add(new ExcursionData(temperature, htData.getTimestamps(),
                        temperature < 2.0f ? "LOW" : "HIGH", mst03Entity.getMacAddress()));
                }
            }

            long tripDurationMinutes = tripData.isEmpty() ? 0 : 
                (tripData.get(tripData.size() - 1).getTimestamps() - tripData.get(0).getTimestamps()) / (1000 * 60);

            Log.d("BeaconRawData", "Parallel trip detected: " + tripData.size() + " records from index " +
                latestExcursionIndex + " to " + (htDataList.size() - 1) +
                " with " + tripExcursions.size() + " excursions. Duration: " + tripDurationMinutes + " minutes");
        } else {
            // No excursion found - use recent data (last 1000 records or all if less)
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

        // Find the latest excursion with no normal after it (going backwards)
        int latestExcursionIndex = -1;

        // Process from latest (current time) to oldest
        for (int i = htDataList.size() - 1; i >= 0; i--) {
            HtData htData = htDataList.get(i);
            float temperature = htData.getTemperature();
            boolean isNormal = (temperature >= 2.0f && temperature <= 8.0f);
            
            if (!isNormal) {
                // Found an excursion - mark it as potential trip start
                latestExcursionIndex = i;
        } else {
                // Found a normal reading - this invalidates any excursions before it
                // The excursion we found before this normal is our trip start
                break; // Stop here - no need to continue
            }
        }

        // Extract trip data: from latest excursion to current time
        if (latestExcursionIndex != -1) {
            // Trip data: from latest excursion to current time
            tripData = htDataList.subList(latestExcursionIndex, htDataList.size());

            // Find all excursions within the trip period
            for (HtData htData : tripData) {
                float temperature = htData.getTemperature();
                if (temperature < 2.0f || temperature > 8.0f) {
                    tripExcursions.add(new ExcursionData(temperature, htData.getTimestamps(),
                        temperature < 2.0f ? "LOW" : "HIGH", mst03Entity.getMacAddress()));
                }
            }

            long tripDurationMinutes = tripData.isEmpty() ? 0 : 
                (tripData.get(tripData.size() - 1).getTimestamps() - tripData.get(0).getTimestamps()) / (1000 * 60);

            Log.d("BeaconRawData", "Trip detected: " + tripData.size() + " records from index " +
                latestExcursionIndex + " to " + (htDataList.size() - 1) +
                " with " + tripExcursions.size() + " excursions. Duration: " + tripDurationMinutes + " minutes");
        } else {
            // No excursion found - use recent data (last 1000 records or all if less)
            int startIndex = Math.max(0, htDataList.size() - 1000);
            tripData = htDataList.subList(startIndex, htDataList.size());
            Log.d("BeaconRawData", "No excursion trip found, using recent " + tripData.size() + " records");
        }

        storeProcessedDataOptimized(tripData, tripExcursions);
    }

    // Generate all CSV files for testing
    private void generateAllCSVFiles(List<HtData> tripData, List<ExcursionData> tripExcursions) {
        updateProcessingStatus("Uploading");
        
        // Generate main trip data CSV only
        generateTripDataCSV(tripData, tripExcursions);
        
        updateProcessingStatus("Completed");
        
        Log.d("BeaconRawData", "Trip Data CSV file generated successfully");
        
            runOnUiThread(() -> {
            Toast.makeText(this, "Trip Data CSV generated successfully", Toast.LENGTH_LONG).show();
        });
    }

    // Manual CSV generation for testing (can be called from UI)
    public void generateCSVForCurrentData() {
        if (processedHistoricalData.isEmpty()) {
            Toast.makeText(this, "No trip data available. Please connect to a device first.", Toast.LENGTH_LONG).show();
            return;
        }

        List<HtData> tripData = new ArrayList<>(processedHistoricalData);
        List<ExcursionData> tripExcursions = new ArrayList<>(processedExcursionData);

        // Generate only the Trip Data CSV
        generateTripDataCSV(tripData, tripExcursions);
    }

    // Get list of generated CSV files
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

    // Share CSV file
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

            // Create share intent
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

    // Save file to Downloads directory (accessible in Android 15)
    private java.io.File saveToDownloadsDirectory(String fileName) {
        try {
            // Check if we have storage permissions
            if (!hasStoragePermissions()) {
                Log.w("BeaconRawData", "Storage permissions not granted, requesting...");
                requestStoragePermissions();
                return null;
            }

            // Try to get Downloads directory
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

    // Check if storage permissions are granted
    private boolean hasStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11+ (API 30+), check MANAGE_EXTERNAL_STORAGE permission
            try {
                return android.os.Environment.isExternalStorageManager();
        } catch (Exception e) {
                Log.e("BeaconRawData", "Error checking storage permissions: " + e.getMessage());
                return false;
            }
        } else {
            // For older versions, check traditional permissions
            return checkSelfPermission(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }

    // Request storage permissions
    private void requestStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11+ (API 30+), request MANAGE_EXTERNAL_STORAGE
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(
                        android.net.Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                startActivityForResult(intent, 1002);
            } catch (Exception e) {
                // Fallback to general storage settings
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, 1002);
            }
        } else {
            // For older versions, request traditional permissions
            requestPermissions(new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1003);
        }
    }

    // Handle permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1003) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d("BeaconRawData", "Storage permission granted");
                // Retry CSV generation
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

    // Status management methods
    private static void updateProcessingStatus(String status) {
        currentProcessingStatus = status;
        Log.d("BeaconRawData", "Processing status updated: " + status);
    }

    public static String getCurrentProcessingStatus() {
        return currentProcessingStatus;
    }

}
