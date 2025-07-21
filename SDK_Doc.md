SDK & API 
iOS 
Android 
Language 
MinewSensorKit Instruction document

Preliminary work
Import to project
Use
Scanning section
Connections
Reading and writing
History
MinewSensorKit Instruction document
This SDK only supports the Bluetooth sensor devices from Minew. The SDK helps developers to handle everything between the phone and the sensor, including: scanning the device, broadcasting data, connecting to the device, writing data to the device, receiving data from the device, etc.

Preliminary work
Overall framework: MST03SensorBleManager is the device management class, which is always a single instance when the app is running. MST03Entity is the device instance class, this suite generates an instance for each device, which is used after scanning and connecting, and contains device broadcast data inside, which will be updated during scanning as the device keeps broadcasting.

MST03SensorBleManager：Device management class that scans the surrounding mst03 devices and can connect them, verify them, etc.；

MST03Entity：Example of an mst03 sensor device acquired during scanning, inherited from BaseBleDeviceEntity

Import to project
Development Environment

The minimum sdk support is Android 5.0, corresponding to API Level 21. set minSdkVersion to 21 or above in build.gradle of the module.

android {

    defaultConfig {
        applicationId "com.xxx.xxx"
        minSdkVersion 21
    }
}
Add jar to the module's libs folder and add the following statement to the build.gradle of the module (add the dependency directly)：

implementation files('libs/base_ble_library.jar')
implementation files('libs/minew_mst03.jar')
api 'org.lucee:bcprov-jdk15on:1.52.0'
Or right-click the jar file and select Add as Library to add it to the current module.

Add the configuration to the.so library file in the App directory build.gradle

android {
    defaultConfig {
  
        ndk {
            abiFilters 'armeabi-v7a','arm64-v8a','x86','x86_64'
        }

    }
    sourceSets {
        main {
            jniLibs.srcDirs = ['libs']
        }
    }
}
The following permissions are required in AndroidManifest.xml, and if targetSdkVersion is greater than 23, you need to do permission management to get the permissions.

    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" tools:node="replace" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" tools:node="replace" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />

    <!-- Required to maintain app compatibility. -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="32"
        />

​

Use
The sdk is divided into three phases: scanning, connecting, and reading and writing.

Scanning section
Start scannin
For Android 6.0 and above systems, when performing BLE scanning, you need to apply for Bluetooth permission and turn on the positioning switch before proceeding.

To enable Bluetooth scanning, you need to turn on Bluetooth first. If you scan without turning on Bluetooth, the APP will crash. You can use BLETool.checkBluetooth(this) to determine whether Bluetooth is turned on. If it is not turned on, you can turn on Bluetooth first.

MST03SensorBleManager mBleManager = 	MST03SensorBleManager.getInstance();
switch (BLETool.checkBluetooth(this)){
    case BLE_NOT_SUPPORT:
        Toast.makeText(this, "Not Support BLE", Toast.LENGTH_SHORT).show();
        break;
    case BLUETOOTH_ON:
		//Set the scan time to 5 minutes. The default scan time of SDK is 5 minutes.
		mBleManager.startScan(this, 5 * 60 * 1000, new OnScanDevicesResultListener<MST03Entity>() {

            @Override
            public void onScanResult(List<MST03Entity> list) {
                
            }

            @Override
            public void onStopScan(List<MST03Entity> list) {

            }

        });
        
        break;
    case BLUETOOTH_OFF:
        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent, 4);
        break;
}
The SDK does not process the Bluetooth scanning duration internally, but scanning is a power-consuming operation. The SDK stops scanning after 5 minutes by default. If you still need to continue scanning, you can provide a timer or refresh operation to continue calling the scanning method to continue scanning the device.。

Retrieve data
During the scan, the APP is able to get a part of the current data of the device through the sdk. The data of the device is obtained through MST03Entity as shown below, which is stored in the broadcast frame object.

The sdk provides BaseBleDeviceEntity as the base class of MST03Entity to store the public data of the sensor device as shown in the following table.

Name	Type	Description
macAddress	String	device mac
name	String	Device name
rssi	int	Signal strength
The BaseBleDeviceEntity also keeps a Map, which is used internally to store the device broadcast data frames it acquires during a scan, and can be retrieved as follows

MST03Entity module;
DeviceStaticInfoFrame deviceInforFrame = (DeviceStaticInfoFrame)module.getMinewFrame(FrameType.DEVICE_INFORMATION_FRAME);
CombinationFrame combinationFrame = (CombinationFrame) module.getMinewFrame(FrameType.INDUSTRIAL_HT_FRAME);
if (deviceInforFrame != null) {
    //Device mac address
    String macAddress = deviceInforFrame.getMacAddress();
    //Percentage of power
    int battery = deviceInforFrame.getBattery();
    //firmware version
    String firmwareVersion = deviceInforFrame.getFirmwareVersion();
}

if (combinationFrame != null) {
    //Temperature and humidity sensor current temperature
    float temperature = combinationFrame.getTemperature();
    //Three-axis acceleration
    float xAxis = combinationFrame.getxAxi();
    float yAxis = combinationFrame.getyAxis();
    float zAxis = combinationFrame.getzAxis();
}
There are 2 types of adv frames for MST03 device。

Device static information frame

DeviceStaticInfoFrame

Name	Type	Description
frameVersion	int	device type
firmwareVersion	String	Firmware Version
batteryLevel	int	Battery Level Percentage
macAddress	String	firmware mac
peripheralSupportInfo	PeripheralSupportInfo	Description of peripheral support
Combination frame

CombinationFrame

Name	Type	Description
temperature	float	Sensor current temperature
xAxis	float	Three-axis acceleration x
yAxis	float	Three-axis acceleration y
zAxis	float	Three-axis acceleration z
String frame(V3.2.6 support)

Name	Type	Description
deviceName	String	device name
Connections
It is usually necessary to stop scanning before connecting. sdk provides methods to connect and disconnect.。

MST03SensorBleManager mBleManager = 	MST03SensorBleManager.getInstance();

//Stop scanning
mBleManager.stopScan(context);
//Setting the device secret key
String key="minewtech1234567";
mBleManager.setSecretKey(key);
//Connection, module for the device to be connected 
MST03Entity module;
mBleManager.connect(context,module);
//Disconnect. macAddress is the device mac
mBleManager.disConnect(macAddress);
Note: Before connecting the device, please confirm whether the device is scanned, if no device broadcast is scanned and the connection method is called, the connection will fail.

After calling connect(), there will be a status listener in sdk for the connection process.

//Setting the listener
mBleManager.setOnConnStateListener(new OnConnStateListener() {
    
    /*
     * Status callbacks during connection
     *
     * @param macAddress      device mac
     * @param connectionState status
     */
    @Override
    public void onUpdateConnState(String address, BleConnectionState state) {
        switch (state) {
            case Disconnect:
				//Connection failure or device disconnection will be called back, active disconnection will not call back the .
                break;

            case Connecting:
				//This state will be called back after calling connect()
                break;
            case Connected:
                //The initial connection was successful, as a transition phase, but not really successful at this point.
                break;
            case AuthenticateFail:
				//Key verification failed.
                break;
            case AuthenticateSuccess:
				//Key verification successful.
                break;
            case ConnectComplete:
				//Connection is complete, the device is now ready for read and write operations.
                break;
            default:
                break;
        }
    }
});
During the connection process, sdk will return multiple connection states to the app, which the app needs to handle properly.

BleConnectionState.Connecting，BleConnectionState.Connected: Connecting the device in, do not do time-consuming operations in this state, because at this time in the connected device discovery service, and send authentication data, etc.。
BleConnectionState.ConnectComplete: The sensor device is successfully connected at this point, and can perform read and write operations, such as configuring advparameters, sensor configuration, reading historical data, etc.。
BleConnectionState.AuthenticateFail：The secret key is verified during the authentication process. If the entered secret key is incorrect, this state will be called back and the device will actively disconnect.
BleConnectionState.AuthenticateSuccess：The secret key is verified during the authentication process. If the entered secret key is correct, this status will be called back.
BleConnectionState.Disconnect: The callback will be made if the connection fails or the device is disconnected.
Reading and writing
The read and write APIs are as follows.：

	/**
      *Set device key
      * @param macAddress device mac
      * @param secretKey secret key key
      * @param listener
      */
     void setDeviceSecretKey(String macAddress, String secretKey, OnModifyConfigurationListener listener);



    /**
     * change device key
     * @param macAddress device mac
     * @param secretKey new secretKey
     * @param listener listener
     */
     void changeSecretKey(String macAddress,String secretKey, OnModifyConfigurationListener listener);

	/**
      * Query device version information
      * @param macAddress device mac
      * @param listener listener
      */
     void queryDeviceFirmwareInfo(String macAddress, OnQueryResultListener<FirmwareVersionModel>  listener);

	/**
      * Read historical data
      *
      * @param macAddress device mac
      * @param rules Historical data acquisition rules, 0 means to obtain all data, 1 means to obtain data of a certain time           *period, when rules==0, startTime and endTime do not need to be passed, and 0 is passed by default
      * @param startTime starting timestamp unit: seconds
      * @param endTime end timestamp unit: seconds,Cannot be greater than systemTime
      * @param systemTime real-time timestamp unit: seconds
      * @param listener listener
      */
     void queryHistoryData(String macAddress,int rules,long startTime,long endTime,long systemTime, OnQueryResultListener<HistoryHtData>  listener);




	/**
      * Query adv frame broadcast parameters
      * @param macAddress device mac
      * @param slot Query the broadcast channel value, default value 0.slot = 0 DeviceStaticInfoFrame 、slot = 1 			  
      * CombinationFrame
      * @param listener listener
      */
     void queryAdvParametersConfiguration(String macAddress,int slot,  OnQueryResultListener<AdvParametersConfiguration>  listener);
	/**
      * Set broadcast frame broadcast parameters
      *
      * @param macAddress device mac
      * @param frameType FrameType enumeration gets getFrameTypeVersion()
      * @param slotNumber sets the channel value corresponding to the broadcast, 
      * @param advertisingInterval broadcast interval in milliseconds, the broadcast interval is adjustable from 1s to 60s, and 	  * the scale is 1s;
      * @param txPower -40 -20 -16 -12 -8 -4 0 4dBm
      * @param advertisingContent Device name (Supported only for String frames)
      * @param listener listener
      */
     void setAdvParametersConfiguration(String macAddress,String frameType, int slotNumber,int advertisingInterval, int txPower, String advertisingContent,OnModifyConfigurationListener listener);


	/**
      * Set temperature and humidity sensor configuration
      *
      * @param macAddress device mac
      * @param htSensorConfiguration sensor configuration
      * @param listener listener
      */
     void setHTSensorConfiguration(String macAddress, HtSensorConfiguration htSensorConfiguration, OnModifyConfigurationListener listener);

	/**
      * Query temperature and humidity sensor configuration
      *
      * @param macAddress device mac
      * @param listener listener
      */
     void queryHTSensorConfiguration(String macAddress, OnQueryResultListener<HtSensorConfiguration> listener);
		/**
      * Firmware upgrade
      *
      * @param macAddress device mac
      * @param isLinkUpgrade true url upgrade, false firmware package upgrade. The default value is false, and the url upgrade  	  * method is not supported. 
      * @param dfuTarget isLinkUpgrade =true, dfuTarget = 0
      * @param upgradeData upgrade package data
      * @param listener listener
      */
     void firmwareUpgrade(String macAddress,boolean isLinkUpgrade,int dfuTarget,byte[] upgradeData, 		 OnFirmwareUpgradeListener listener);


    /**
     * Restore factory settings
     *
     * @param macAddress device mac
     * @param listener listener
     */
     void reset(String macAddress, OnModifyConfigurationListener listener);

    /**
     * Shutdown
     * @param macAddress device mac
     * @param listener listener
     */
     void powerOff(String macAddress, OnModifyConfigurationListener listener);


/**
 * This command is used to set the LED configuration.
 * 
 * @param macAddress Device MAC address
 * @param colorTable Color table, where 1=Blue, 2=Green, 3=Red, 4=Yellow, 5=White, 6=Magenta, 7=Cyan
 * @param workTotalCount The number of operation cycles, ranging from 1 to 65535. Value = durationTime / (singleCycleLightingTime + singleCycleLightOffTime)
 * @param singleCycleLightingTime Lighting time within a single cycle, ranging from 1 to 65535 milliseconds
 * @param singleCycleLightOffTime Off time within a single cycle, ranging from 1 to 65535 milliseconds
 * @param brightness Brightness level, ranging from 0 to 100
 */
    void setLEDConfiguration(String macAddress, int colorTable, int workTotalCount, int singleCycleLightingTime, int singleCycleLightOffTime, int brightness, OnModifyConfigurationListener listener);


    /**
     * Clear historical data
     * 
     * @param macAddress Device MAC address
     * @param listener Listener s
     */
    void cleanHistoryData(String macAddress, OnModifyConfigurationListener listener);


Reading and writing：

Set device key。

MST03SensorBleManager mBleManager = 	MST03SensorBleManager.getInstance();
String secretKey = "3141592653589793"
mBleManager.setSecretKey(mAddress,secretKey);

Change device password

MST03SensorBleManager mBleManager = 	MST03SensorBleManager.getInstance();
String password = "minewtech1234567";
mBleManager.changeSecretKey(mMac, password,new OnModifyConfigurationListener() {
                    @Override
                    public void onModifyResult(boolean b) {
                        Toast.makeText(DeviceConnectedCompleteActivity.this,"Change password Result:"+b,Toast.LENGTH_LONG).show();
                    }
});
Set led Config

MST03SensorBleManager mBleManager = 	MST03SensorBleManager.getInstance();
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
Query device version information。

MST03SensorBleManager mBleManager = 	MST03SensorBleManager.getInstance();

mBleManager.queryDeviceFirmwareInfo(macAddress,new OnQueryResultListener<FirmwareVersionModel>() {
            @Override
            public void OnQueryResult(boolean b, FirmwareVersionModel firmwareVersionModel) {
				//b true query succeeds, false query fails ,firmwareVersionModel=null。
                Toast.makeText(context,"Query Result:"+b,Toast.LENGTH_LONG).show();
                if(b){
                    List<VersionInfo> versionInfoList = firmwareVersionModel.getVersionInfoList();
                    String firmwareName = versionInfoList.get(0).getFirmwareName();
                    int firmwareType = versionInfoList.get(0).getFirmwareType();
                    String firmwareVersion = versionInfoList.get(0).getFirmwareVersion();
                }
            }
        });
Query adv frame broadcast parameters and configure adv parameters.


MST03SensorBleManager mBleManager = 	MST03SensorBleManager.getInstance();
MST03Entity module
String macAddress = module.getMacAddress();
//slot = 0 DeviceStaticInfoFrame 、slot = 1 CombinationFrame
//V3.2.6 version add slot = 2 StringFrame
//query DeviceStaticInfoFrame
AdvParametersConfiguration deviceInfoAdvParametersConfiguration = null;
mBleManager.queryAdvParametersConfiguration(macAddress, 0, new OnQueryResultListener<AdvParametersConfiguration>() {
    @Override
    public void OnQueryResult(boolean b, AdvParametersConfiguration advParametersConfiguration) {
        if(b){
            deviceInfoAdvParametersConfiguration = advParametersConfiguration;
        }
        Toast.makeText(context,"Query Adv Result:"+b,Toast.LENGTH_LONG).show();
    }
});
//set DeviceStaticInfoFrame 
mBleManager.setAdvParametersConfiguration(macAddress, deviceInfoAdvParametersConfiguration.getFrameType(), 						         deviceInfoAdvParametersConfiguration.getSlotNumber(),
        1000,-4,new OnModifyConfigurationListener() {
    @Override
    public void onModifyResult(boolean b) {

        Toast.makeText(context,"Set Adv Parameters Result:"+b,Toast.LENGTH_LONG).show();
    }
});

//query CombinationFrame 
AdvParametersConfiguration combinationAdvParametersConfiguration = null
mBleManager.queryAdvParametersConfiguration(macAddress, 1, new OnQueryResultListener<AdvParametersConfiguration>() {
    @Override
    public void OnQueryResult(boolean b, AdvParametersConfiguration advParametersConfiguration) {
        if(b){
            combinationAdvParametersConfiguration = advParametersConfiguration;
        }
        Toast.makeText(context,"Query Adv Result:"+b,Toast.LENGTH_LONG).show();
    }
});
//set CombinationFrame
mBleManager.setAdvParametersConfiguration(macAddress, combinationAdvParametersConfiguration.getFrameType(), 						         combinationAdvParametersConfiguration.getSlotNumber(),
        1000,-4,new OnModifyConfigurationListener() {
    @Override
    public void onModifyResult(boolean b) {

        Toast.makeText(context,"Set Adv Parameters Result:"+b,Toast.LENGTH_LONG).show();
    }
});
// query StringFrame by V3.2.6 Version
AdvParametersConfiguration stringAdvParametersConfiguration = null
    mBleManager.queryAdvParametersConfiguration(macAddress, 2, new OnQueryResultListener<AdvParametersConfiguration>() {
    @Override
    public void OnQueryResult(boolean b, AdvParametersConfiguration advParametersConfiguration) {
        if(b){
            stringAdvParametersConfiguration = advParametersConfiguration;
        }
        Toast.makeText(context,"Query Adv Result:"+b,Toast.LENGTH_LONG).show();
    }
});
//set StringFrame 
// String frame can be used to set the device name, which can be read during the broadcasting phase.
String deviceName = "custmerName"// name length 0~16
mBleManager.setAdvParametersConfiguration(macAddress, stringAdvParametersConfiguration.getFrameType(), 						         stringAdvParametersConfiguration.getSlotNumber(),
        1000,-4,deviceName,new OnModifyConfigurationListener() {
    @Override
    public void onModifyResult(boolean b) {

        Toast.makeText(context,"Set Adv Parameters Result:"+b,Toast.LENGTH_LONG).show();
    }
});
Query temperature historical data


long systemTime = System.currentTimeMillis()/1000;
long startTime = (systemTime-60*60*1000*24)/1000;
long endTime = systemTime;

int rule = 0;

mBleManager.queryHistoryData(macAddress,rule, startTime, endTime, systemTime, new OnQueryResultListener<HistoryHtData>() {

    @Override
    public void OnQueryResult(boolean b, HistoryHtData historyHtData) {
        if(b){
               List<HtData> htDataList = historyHtData.getHistoryDataList();
               for (HtData htData : htDataList) {
                        
               }
        }else{
            Toast.makeText(context,"selectHTHistoryData Result: fail",Toast.LENGTH_LONG).show();
        }

    }


});
clear history Data

        mBleManager.cleanHistoryData(mMac, new OnModifyConfigurationListener() {
            @Override
            public void onModifyResult(boolean b) {
                Toast.makeText(DeviceConnectedCompleteActivity.this,"clear Result:"+b,Toast.LENGTH_LONG).show();
                disConnected();
            }
        });
Query sensor parameters and configure sensor parameters.

//Query sensor parameters
HtSensorConfiguration htSensorConfig = null;
mBleManager.queryHTSensorConfiguration(macAddress, new OnQueryResultListener<HtSensorConfiguration>() {
    @Override
    public void OnQueryResult(boolean b, HtSensorConfiguration htSensorConfiguration) {
        if(b){
            htSensorConfig = htSensorConfiguration;
            //Collection interval. Unit second
            int samplingInterval = htSensorConfig.getSamplingInterval();
            //Delay recording time. Unit seconds.
            int delay = htSensorConfig.getDelay();
            //2 sets of temperature threshold data
            List<HTSensorThresholdConfig> htSettingData = htSensorConfig.getHtSettingData();
        }
        Toast.makeText(context,"Query Result:"+b,Toast.LENGTH_LONG).show();
    }
});
//Set sensor parameters
        List<HTSensorThresholdConfig> htSettingData = new ArrayList<>();
		//temperature upper and lower limit configuration range: 0℃~50℃, the default value of humidity setting is 			//invalid value -128
        HTSensorThresholdConfig htSettingData = new HTSensorThresholdConfig();
        htSettingData.setHighTemperature(40f);
        htSettingData.setLowTemperature(5f);
        htSettingData.setHighHumidity(-128f);
        normalSettingData1.setLowHumidity(-128f);     
        htSettingData.add(htSettingData);
=

        HtSensorConfiguration htSensorConfiguration = new HtSensorConfiguration();
		//Delayed temperature measurement: default burning 5min, App configurable range: 5min~30min, unit seconds
        htSensorConfiguration.setDelay(5*60);
		//Sampling interval 1s ~24h, default value 10s, sampling interval is a multiple of 5 seconds. Unit second
        htSensorConfiguration.setSamplingInterval(30);
        htSensorConfiguration.setHtSettingData(htSettingData);

mBleManager.setHTSensorConfiguration(macAddress, htSensorConfiguration,new OnModifyConfigurationListener() {
    @Override
    public void onModifyResult(boolean b) {
        Toast.makeText(DeviceConnectedCompleteActivity.this,"Set Sensor Parameters Result:"+b,Toast.LENGTH_LONG).show();
    }
});
Firmware upgrade.


mBleManager.firmwareUpgrade(mac, false,0,upgradeData, new OnFirmwareUpgradeListener() {
    
    /**
     * Upgrade package data writing progress
     */
    @Override
    public void updateProgress(int progress) {
		
    }

	/**
      * Callback when the upgrade is successful. At this time, the device will actively disconnect from the mobile phone, so       * the OnConnStateListener callback will be triggered and return
      * BleConnectionState.Disconnect state
      */
    @Override
    public void upgradeSuccess() {

    }
    
	/**
      * Upgrade failed
      */
    @Override
    public void upgradeFailed() {

    }
});
Restore the factory settings.

/**
 * Restore factory settings
 *
 * @param macAddress device mac
 */
mBleManager.reset(macAddress, new OnModifyConfigurationListener() {
    @Override
    public void onModifyResult(boolean success) {

    }
});
Shutdown.

/**
 * Reset
 *
 * @param macAddress device mac
 */
mBleManager.powerOff(macAddress, new OnModifyConfigurationListener() {
    @Override
    public void onModifyResult(boolean success) {

    }
});
History
2024/02/29 edit；

2025/02/27 modify

Last Updated:: 5/15/2025, 3:31:18 PM