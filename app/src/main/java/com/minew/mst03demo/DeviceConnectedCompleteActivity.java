package com.minew.mst03demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.kongzue.dialogx.dialogs.WaitDialog;
import com.minew.ble.mst03.bean.AdvParametersConfiguration;
import com.minew.ble.mst03.bean.HistoryHtData;
import com.minew.ble.mst03.bean.HtData;
import com.minew.ble.mst03.bean.HtSensorConfiguration;
import com.minew.ble.mst03.bean.SensorSettingData;
import com.minew.ble.mst03.frames.DeviceStaticInfoFrame;
import com.minew.ble.mst03.interfaces.OnReceiveDataListener;
import com.minew.ble.mst03.manager.MST03SensorBleManager;
import com.minew.ble.v3.bean.FirmwareVersionModel;
import com.minew.ble.v3.bean.HTSensorThresholdConfig;
import com.minew.ble.v3.enums.BleConnectionState;
import com.minew.ble.v3.enums.FrameType;
import com.minew.ble.v3.interfaces.OnConnStateListener;
import com.minew.ble.v3.interfaces.OnFirmwareUpgradeListener;
import com.minew.ble.v3.interfaces.OnModifyConfigurationListener;
import com.minew.ble.v3.interfaces.OnQueryResultListener;
import com.minew.ble.v3.utils.LogUtil;
import com.minew.ble.v3.utils.MinewExecutors;
import com.minew.ble.v3.utils.ZipUtil;
import com.minew.mst03demo.databinding.ActivityDeviceConnectedCompleteBinding;
import com.permissionx.guolindev.PermissionX;
import com.permissionx.guolindev.callback.ExplainReasonCallback;
import com.permissionx.guolindev.callback.ForwardToSettingsCallback;
import com.permissionx.guolindev.callback.RequestCallback;
import com.permissionx.guolindev.request.ExplainScope;
import com.permissionx.guolindev.request.ForwardScope;


import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DeviceConnectedCompleteActivity extends BaseActivity{
    public static final String TAG="Connected";
    private ActivityDeviceConnectedCompleteBinding binding;
    private MST03SensorBleManager mBleManager;
    private String mMac="";

    // Add variables to store historical data
    private List<HtData> historicalDataList = new ArrayList<>();
    private boolean hasHistoricalData = false;

    private AdvParametersConfiguration deviceInfoAdvParametersConfiguration = null;
    private AdvParametersConfiguration combinationAdvParametersConfiguration = null;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceConnectedCompleteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initToolBar();
        initBleManager();
        initData();
        // Remove all old button listeners
        // TODO: Add Download Data button logic here if needed
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        disConnected();
    }

    private void initToolBar(){
        setToolbar(binding.toolBar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        binding.toolBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }
    private void initBleManager(){
        mBleManager = MST03SensorBleManager.getInstance();
        mBleManager.setOnConnStateListener(new OnConnStateListener() {
            @Override
            public void onUpdateConnState(String s, BleConnectionState sensorConnectionState) {
                switch (sensorConnectionState){
                    case Connecting:
                        Log.d("TAG","Connecting");
                        break;
                    case Connected:
                        Log.d("TAG","Connected");
                        break;
                    case ConnectComplete:
                        Log.d("TAG","ConnectComplete");
                        break;
                    case Disconnect:
                        Log.d("TAG","Disconnect");
                        finish();
                        break;
                    default:
                        break;
                }
            }
        });
        
        // TODO: Add beacon payload logging when the correct method is available
        // For now, we'll log connection events and add payload logging later
        Log.d(TAG, "BLE Manager initialized for MAC: " + mMac);
    }

    private void initData(){
        mMac = getIntent().getStringExtra("mac");
        Log.d(TAG, "Device MAC: " + mMac);
        
        // Add button click listener for downloading data
        binding.btnDownloadData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Download Data button clicked");
                
                // First query device static information
                queryDeviceStaticInfo();
                
                // Try to get device static info from connection
                getDeviceStaticInfoFromConnection();
                
                // Then get historical data
                selectHTHistoryData();
            }
        });
    }

    private void initListener(){
        // Remove all old button listeners
    }

    /**
     * Set Device Name
     * V3.2.6 support
     */
    private void setDeviceName(){
        String name = "mst03_test";
        mBleManager.setAdvParametersConfiguration(mMac, FrameType.STRING_FRAME.getFrameTypeVersion(), 2,
                5 * 1000, 0, name, new OnModifyConfigurationListener() {
                    @Override
                    public void onModifyResult(boolean b) {
                        Toast.makeText(DeviceConnectedCompleteActivity.this,"Set Name Result:"+b,Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * change Device password
     * V3.2.6 support
     */
    private void changeDevicePassword(){
        String password = "minewtech1234567";
        mBleManager.changeSecretKey(mMac, password,new OnModifyConfigurationListener() {
                    @Override
                    public void onModifyResult(boolean b) {
                        Toast.makeText(DeviceConnectedCompleteActivity.this,"Change password Result:"+b,Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * set led
     */
    private void setLedConfig(){
        int duration = 10*1000;//(unit: milliseconds),0-1000s,
        int singleCycleLightingTime = 200;//Light on time in a single cycle (unit: milliseconds)
        int singleCycleLightOffTime = 200;//The indicator off time in a single period,   (unit: milliseconds)
        int workTotalCount = duration/(singleCycleLightingTime+singleCycleLightOffTime); //The number of operation cycles,  0 means no operation
        int ledColor = 3;//led Color
        int brightness =100;//Brightness 1, 0~100
        mBleManager.setLEDConfiguration(mMac, ledColor, workTotalCount, singleCycleLightingTime, singleCycleLightOffTime, brightness, new OnModifyConfigurationListener() {
            @Override
            public void onModifyResult(boolean b) {
                Toast.makeText(DeviceConnectedCompleteActivity.this,"Set LED Result:"+b,Toast.LENGTH_LONG).show();
            }
        });
    }

    private void querySensorParameters(){
        mBleManager.queryHTSensorConfiguration(mMac, new OnQueryResultListener<HtSensorConfiguration>() {
            @Override
            public void OnQueryResult(boolean b, HtSensorConfiguration htSensorConfiguration) {

            }
        });
    }


    /**
     * Sensor Parameters setting
     * If you want to get the default configuration of the device,you can use queryHTSensorConfiguration()
     *
     * V3.2.6  There's only one set of temperature data
     */
    private void setSensorParameters(){
        //
        List<HTSensorThresholdConfig> htSettingData = new ArrayList<>();
        HTSensorThresholdConfig settingData1 = new HTSensorThresholdConfig();
        settingData1.setHighTemperature(40f);
        settingData1.setLowTemperature(5f);
        settingData1.setHighHumidity(-128f);
        settingData1.setLowHumidity(-128f);
        //humidity range: 0%-100% Invalid value:-128f

        HtSensorConfiguration htSensorConfiguration = new HtSensorConfiguration();
        htSensorConfiguration.setDelay(5*60);
        htSensorConfiguration.setSamplingInterval(30);
        htSensorConfiguration.setHtSettingData(htSettingData);

        mBleManager.setHTSensorConfiguration(mMac, htSensorConfiguration,new OnModifyConfigurationListener() {
            @Override
            public void onModifyResult(boolean b) {
                Toast.makeText(DeviceConnectedCompleteActivity.this,"Set Sensor Parameters Result:"+b,Toast.LENGTH_LONG).show();
            }
        });
    }
    /**
     * query Adv Parameters
     */
    private void queryDeviceInfoAdvParameters(){
        mBleManager.queryAdvParametersConfiguration(mMac, 0, new OnQueryResultListener<AdvParametersConfiguration>() {
            @Override
            public void OnQueryResult(boolean b, AdvParametersConfiguration advParametersConfiguration) {
                if(b){
                    deviceInfoAdvParametersConfiguration = advParametersConfiguration;
                }
                Toast.makeText(DeviceConnectedCompleteActivity.this,"Query Adv Result:"+b,Toast.LENGTH_LONG).show();
            }
        });

    }
    /**
     * Adv Parameters setting
     */
    private void setDeviceInfoAdvParameters(){
        if(deviceInfoAdvParametersConfiguration == null){
            return;
        }
        mBleManager.setAdvParametersConfiguration(mMac, deviceInfoAdvParametersConfiguration.getFrameType(), deviceInfoAdvParametersConfiguration.getSlotNumber(),
                1000,-4,deviceInfoAdvParametersConfiguration.getAdvertisingContent(),new OnModifyConfigurationListener() {
            @Override
            public void onModifyResult(boolean b) {

                Toast.makeText(DeviceConnectedCompleteActivity.this,"Set Adv Parameters Result:"+b,Toast.LENGTH_LONG).show();
            }
        });

    }
    /**
     * query Adv Parameters
     *
     */
    private void queryCombinationAdvParameters(){

        mBleManager.queryAdvParametersConfiguration(mMac, 1, new OnQueryResultListener<AdvParametersConfiguration>() {
            @Override
            public void OnQueryResult(boolean b, AdvParametersConfiguration advParametersConfiguration) {
                if(b){
                    combinationAdvParametersConfiguration = advParametersConfiguration;
                }
                Toast.makeText(DeviceConnectedCompleteActivity.this,"Query Adv Result:"+b,Toast.LENGTH_LONG).show();
            }
        });

    }
    /**
     * BroadCast Parameters setting
     */
    private void setCombinationAdvParameters(){
        if(deviceInfoAdvParametersConfiguration == null){
            return;
        }
        mBleManager.setAdvParametersConfiguration(mMac, combinationAdvParametersConfiguration.getFrameType(), combinationAdvParametersConfiguration.getSlotNumber(),
                1000,-4,combinationAdvParametersConfiguration.getAdvertisingContent(),new OnModifyConfigurationListener() {
                    @Override
                    public void onModifyResult(boolean b) {

                        Toast.makeText(DeviceConnectedCompleteActivity.this,"Set Adv Parameters Result:"+b,Toast.LENGTH_LONG).show();
                    }
                });

    }
    private void selectHTHistoryData(){
        long systemTime = System.currentTimeMillis()/1000;
        long startTime = (systemTime-3600*1000*24)/1000;
        long endTime = systemTime;

        int rule = 1;

        new Thread(new Runnable() {
            @Override
            public void run() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        WaitDialog.show(R.string.loading);
                    }
                });
                mBleManager.queryHistoryData(mMac,rule, startTime, endTime, systemTime, new OnQueryResultListener<HistoryHtData>() {

                    @Override
                    public void OnQueryResult(boolean b, HistoryHtData historyHtData) {
                        Log.d("BeaconRawData", "OnQueryResult called, success=" + b + ", historyHtData=" + historyHtData);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                WaitDialog.dismiss();
                                Log.d(TAG, "selectHTHistoryData Result: " + b);
                                if(b){
                                    List<HtData> htDataList = historyHtData.getHistoryDataList();
                                    
                                    // Store the data for later use
                                    historicalDataList.clear();
                                    historicalDataList.addAll(htDataList);
                                    hasHistoricalData = true;
                                    
                                    Log.d(TAG, "Total beacon data records: " + historyHtData.getHistoryDataList().size());
                                    
                                    // Log each piece of beacon data
                                    for (int i = 0; i < htDataList.size(); i++) {
                                        HtData htData = htDataList.get(i);
                                        Log.d(TAG, "=== Beacon Data Record " + (i + 1) + " ===");
                                        Log.d(TAG, "Temperature: " + htData.getTemperature() + "°C");
                                        Log.d(TAG, "Humidity: " + htData.getHumidity() + "%");
                                        Log.d(TAG, "Raw data: " + htData.toString());
                                        Log.d(TAG, "================================");
                                    }
                                    
                                    Toast.makeText(DeviceConnectedCompleteActivity.this, 
                                        "Downloaded " + htDataList.size() + " records", Toast.LENGTH_SHORT).show();
                                }else{
                                    Log.d(TAG, "Failed to get beacon data");
                                    Toast.makeText(DeviceConnectedCompleteActivity.this,"selectHTHistoryData Result: fail",Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }


                });
            }
        }).start();


    }
    private void clearHistoryData(){
        mBleManager.cleanHistoryData(mMac, new OnModifyConfigurationListener() {
            @Override
            public void onModifyResult(boolean b) {
                Toast.makeText(DeviceConnectedCompleteActivity.this,"clear Result:"+b,Toast.LENGTH_LONG).show();
                disConnected();
            }
        });
    }

    private void reset(){
        mBleManager.reset(mMac, new OnModifyConfigurationListener() {
            @Override
            public void onModifyResult(boolean b) {
                Toast.makeText(DeviceConnectedCompleteActivity.this,"reset Result:"+b,Toast.LENGTH_LONG).show();
                disConnected();
            }
        });
    }

    private void powerOff(){
        mBleManager.powerOff(mMac, new OnModifyConfigurationListener() {
            @Override
            public void onModifyResult(boolean b) {
                Toast.makeText(DeviceConnectedCompleteActivity.this,"powerOff Result:"+b,Toast.LENGTH_LONG).show();
                disConnected();
            }
        });
    }


    private void disConnected(){
        mBleManager.disConnect(mMac);
    }
    
    // Method to get all historical data
    public List<HtData> getHistoricalData() {
        return historicalDataList;
    }
    
    // Method to get the latest temperature reading
    public float getLatestTemperature() {
        if (hasHistoricalData && !historicalDataList.isEmpty()) {
            return historicalDataList.get(historicalDataList.size() - 1).getTemperature();
        }
        return -999; // Indicates no data
    }
    
    // Method to get the latest humidity reading
    public float getLatestHumidity() {
        if (hasHistoricalData && !historicalDataList.isEmpty()) {
            return historicalDataList.get(historicalDataList.size() - 1).getHumidity();
        }
        return -999; // Indicates no data
    }
    
    // Method to get data count
    public int getDataCount() {
        return historicalDataList.size();
    }
    
    // Method to check if data is available
    public boolean hasData() {
        return hasHistoricalData;
    }
    
    /**
     * Try to get device static information from the connected device
     * This attempts to retrieve the DeviceStaticInfoFrame that was available during scanning
     */
    private void getDeviceStaticInfoFromConnection() {
        Log.d(TAG, "Attempting to get device static info from connection...");
        
        // Note: According to the SDK documentation, DeviceStaticInfoFrame is available during scanning
        // and contains: frameVersion, firmwareVersion, batteryLevel, macAddress
        // However, this information might not be directly accessible after connection
        // The firmware information is available through queryDeviceFirmwareInfo()
        
        Log.d(TAG, "Device static info (battery level, etc.) is typically available during scanning");
        Log.d(TAG, "For connected devices, use queryDeviceFirmwareInfo() for firmware details");
        Log.d(TAG, "Battery level and other static info may need to be captured during scan phase");
    }
    
    /**
     * Query and log device static information
     * This retrieves device type, firmware version, battery level, and MAC address
     */
    private void queryDeviceStaticInfo() {
        Log.d(TAG, "Querying device static information...");
        
        try {
            // Method 1: Query device firmware information using the SDK method
            mBleManager.queryDeviceFirmwareInfo(mMac, new OnQueryResultListener<FirmwareVersionModel>() {
                @Override
                public void OnQueryResult(boolean success, FirmwareVersionModel firmwareVersionModel) {
                    if (success && firmwareVersionModel != null) {
                        Log.d(TAG, "=== Device Firmware Information ===");
                        Log.d(TAG, "Firmware Model: " + firmwareVersionModel.toString());
                        
                        // Try to get version info if available
                        try {
                            Object versionInfoList = firmwareVersionModel.getVersionInfoList();
                            if (versionInfoList != null) {
                                Log.d(TAG, "Version Info List: " + versionInfoList.toString());
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "Version info not available in this SDK version");
                        }
                        
                        Log.d(TAG, "================================");
                    } else {
                        Log.d(TAG, "Failed to get device firmware information");
                    }
                }
            });
            
            // Method 2: Try to get device info through advertising parameters
            queryDeviceInfoAdvParameters();
            
            // Method 3: Log what we know about the device
            Log.d(TAG, "=== Known Device Information ===");
            Log.d(TAG, "Connected MAC Address: " + mMac);
            Log.d(TAG, "Device Type: MST03 Sensor");
            Log.d(TAG, "================================");
            
        } catch (Exception e) {
            Log.e(TAG, "Error querying device static info: " + e.getMessage());
        }
    }


    private void initStoragePermission(){
        String[] requestPermissionList ;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            requestPermissionList = new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
//                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }else{
            requestPermissionList = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE};

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
                            gotoSystemFilePage();
                        }
                    }
                });
    }

    private ActivityResultLauncher launcherActivityResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult result) {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Uri fileUri =result.getData().getData();
                handleFileByQ(fileUri);
            }
        }
    });

    private void  gotoSystemFilePage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        launcherActivityResult.launch(intent);
    }


    @SuppressLint("Range")
    private void handleFileByQ(Uri fileUri) {
        String zipFilePath = FileUtil.getFileAbsolutePath(this,fileUri);
        boolean verifyResult = ZipUtil.verifyZip(zipFilePath);
        if(!verifyResult){
            chooseFileError(R.string.file_select_error);
            return;
        }
        InputStream bininputstream = ZipUtil.readZipFile(zipFilePath, ".bin");
        dealFileData(bininputstream);
    }
    private Handler mHandler =  new Handler(Looper.getMainLooper());
    private void chooseFileError(@StringRes int resId) {

        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show();
        mHandler.postDelayed(() -> finish(), 500);
    }

    private void dealFileData(InputStream inputStream) {
        MinewExecutors.getInstance().getDiskIO().execute(() -> {

            try {
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                ArrayList<Byte> allData = new ArrayList<>();
                //每次读取4k
                byte[] buffer = new byte[1024 * 4];
                int bytesRead = 0;
                int tempLength = 0;
                while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                    for (int i = 0; i < bytesRead; i++) {
                        byte by = buffer[i];
                        allData.add(by);
                    }
                    tempLength += bytesRead;
                }
                byte[] fileData = new byte[allData.size()];
                for (int i = 0; i < allData.size(); i++) {
                    fileData[i] = allData.get(i);
                }
                bufferedInputStream.close();
                firmwareUpgrade(mMac,fileData);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(this, getString(R.string.file_select_error), Toast.LENGTH_SHORT).show();
                finish();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, getString(R.string.file_select_error), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }


    private void firmwareUpgrade(String macAddress,byte[] fileByte){
        mBleManager.firmwareUpgrade(macAddress,false,0, fileByte, new OnFirmwareUpgradeListener() {
            @Override
            public void updateProgress(int progress) {
                WaitDialog.show(progress);
            }

            @Override
            public void upgradeSuccess() {
                WaitDialog.dismiss();
                Toast.makeText(
                        DeviceConnectedCompleteActivity.this,
                        "upgrade success",
                        Toast.LENGTH_SHORT
                ).show();
            }

            @Override
            public void upgradeFailed() {
                WaitDialog.dismiss();
                Toast.makeText(
                        DeviceConnectedCompleteActivity.this,
                        "upgrade fail",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}
