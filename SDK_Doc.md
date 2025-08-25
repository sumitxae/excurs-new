---
  sidebar: auto
---
# MinewSensorKit Instruction document

This SDK only supports the Bluetooth sensor devices from Minew. The SDK helps developers to handle everything between the phone and the sensor, including: scanning the device, broadcasting data, connecting to the device, writing data to the device, receiving data from the device, etc.

## Preliminary work

Overall framework: `MST03SensorBleManager` is the device management class, which is always a single instance when the app is running. `MST03Entity` is the device instance class, this suite generates an instance for each device, which is used after scanning and connecting, and contains device broadcast data inside, which will be updated during scanning as the device keeps broadcasting.

``MST03SensorBleManager``：Device management class that scans the surrounding mst03 devices and can connect them, verify them, etc.；

`MST03Entity`：Example of an mst03 sensor device acquired during scanning, inherited from `BaseBleDeviceEntity`

## Import to project

1. Development Environment

   The minimum sdk support is Android 5.0, corresponding to API Level 21. set `minSdkVersion` to 21 or above in `build.gradle` of the module.

   ```groovy
   android {
   
       defaultConfig {
           applicationId "com.xxx.xxx"
           minSdkVersion 24
       }
   }
   ```

2. Add jar to the module's libs folder and add the following statement to the `build.gradle` of the `module` (add the dependency directly)：

   ```groovy
   implementation files('libs/base_ble_library.jar')
   implementation files('libs/minew_custom_mst03.jar')
   api 'org.lucee:bcprov-jdk15on:1.52.0'
   ```

   Or right-click the jar file and select `Add as Library` to add it to the current module.

   Add the  configuration to the.so library file in the App directory build.gradle

   ```
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
   ```

   

3. The following permissions are required in `AndroidManifest.xml`, and if `targetSdkVersion` is greater than 23, you need to do **permission management** to get the permissions.

   ```xml
       <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" tools:node="replace" />
       <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" tools:node="replace" />
       <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
       <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
       <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
       <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
       <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
       <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
   
       <uses-permission android:name="android.permission.READ_MEDIA_AUDIO"/>
       <uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
       <uses-permission android:name="android.permission.READ_MEDIA_VIDEO"/>
       <uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED"/>
       <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
           android:maxSdkVersion="32" />
       <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
           android:maxSdkVersion="32"
           />
   
   ```

​		

## Use

The sdk is divided into three phases: scanning, connecting, and reading and writing.

### Scanning section

##### 1.Ble Permissions

For Android 6.0 and above systems, when performing BLE scanning, you need to apply for Bluetooth permission and turn on the positioning switch before proceeding.

To enable Bluetooth scanning, you need to turn on Bluetooth first. If you scan without turning on Bluetooth, the APP will crash. You can use BLETool.checkBluetooth(this) to determine whether Bluetooth is turned on. If it is not turned on, you can turn on Bluetooth first.

```java
    private fun checkBlePermissions() {
        val requestPermissionList = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        PermissionConverter.putPermissionDescriptionMap(
            R.string.common_permission_nearby_devices,
            R.string.common_request_gps_nearby_devices_message_permission
        )
        PermissionConverter.putPermissionDescriptionMap(
            R.string.common_permission_location,
            R.string.common_request_gps_message_permission
        )
        if (!XXPermissions.isGrantedPermissions(this,requestPermissionList)){
            XXPermissions.with(this)
                .permission(requestPermissionList)
                .interceptor(PermissionInterceptor())
                .description(PermissionDescription())
                .request(object : OnPermissionCallback {
                    override fun onGranted(
                        permissions: MutableList<String>,
                        allGranted: Boolean
                    ) {
                        if (!allGranted) {
                            return
                        }else{
                            checkBluetooth()
                        }
                    }
                })
        }else{
            checkBluetooth()
        }
    }


    private fun checkBluetooth() {
		when (BLETool.checkBluetooth(this)){
            BluetoothState.BLE_NOT_SUPPORT -> {
                Toast.makeText(this@Mst03ScanDeviceListActivity, "Not Support BLE", Toast.LENGTH_SHORT).show()
            }
            BluetoothState.BLUETOOTH_OFF -> {
                val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                launcherActivityResultForBle.launch(enableIntent)
            }
            BluetoothState.BLUETOOTH_ON -> {

                startScan()

            }
            else -> {
            }
		}
    }


```

##### 2.Start Scanning devices

```
val mBleManager = MST03SensorBleManager.getInstance()

//ManufacturerId. 0x00E0 = Google
mBleManager.setManufacturerIdHexLe("E000")



private fun startScan(){
 	//Set the scan time to 5 minutes. The default scan time of SDK is 5 minutes.
	mBleManager.startScan(context.mApplication, 5 * 60 * 1000, object : OnScanDevicesResultListener<MST03Entity> {
            override fun onScanResult(scanList: MutableList<MST03Entity>?) {
                //During the scan, the scanned device data will be replied once per second
            }


            override fun onStopScan(scanList: MutableList<MST03Entity>?) {
                //stopScan(context.mApplication) will not actively trigger the onStopScan() method. Currently, the onStopScan() 				//method is only triggered after the set scanning time ends.
            }
        })
 }
 
 
//Custom scan duration default duration = 5min 
mBleManager.setDefaultScanTime(1*60*1000)
//Customize the scan result return interval.default interval = 1S
mBleManager.scanSensorManager.setScanResultsIntervalTime(3*1000)
```

The SDK does not process the Bluetooth scanning duration internally, but scanning is a power-consuming operation. The SDK stops scanning after 5 minutes by default. If you still need to continue scanning, you can provide a timer or refresh operation to continue calling the scanning method to continue scanning the device.。

##### 3.Stop Scanning deveces

```
mBleManager.stopScan(context)
```



##### 4.Adv data

During the scan, the APP is able to get a part of the current data of the device through the sdk. The  data of the device is obtained through `MST03Entity` as shown below, which is stored in the broadcast frame object.

The sdk provides `BaseBleDeviceEntity` as the base class of `MST03Entity` to store the public data of the sensor device as shown in the following table.

| Name       | Type   | Description     |
| ---------- | ------ | --------------- |
| macAddress | String | device mac      |
| name       | String | Device name     |
| rssi       | int    | Signal strength |

The `BaseBleDeviceEntity` also keeps a Map, which is used internally to store the device broadcast data frames it acquires during a scan, and can be retrieved as follows

```java
 val module:MST03Entity;

 val combinationFrame : CombinationFrame? = item.getMinewFrame(FrameType.CUSTOM_COMBINATION_FRAME)?.let {
            it as CombinationFrame
        }
val deviceStaticInfoFrame : DeviceStaticInfoFrame? = item.getMinewFrame(FrameType.CUSTOM_DEVICE_INFORMATION_FRAME)?.let {
            it as DeviceStaticInfoFrame
        }
deviceStaticInfoFrame?.let{
    //Device mac address
    val macAddress = it.macAddress
    //Percentage of power
    val battery = it.battery
    //firmware version
    val firmwareVersion = it.firmwareVersion
}
    


combinationFrame?.let {
    //current temperature
    val temperature = it.temperature
    val temperatureUpperLimit1AlarmMark = it.temperatureUpperLimit1AlarmMark
    val temperatureLowerLimit1AlarmMark = it.temperatureLowerLimit1AlarmMark
    val temperatureUpperLimit2AlarmMark = it.temperatureUpperLimit2AlarmMark
    val temperatureLowerLimit2AlarmMark = it.temperatureLowerLimit2AlarmMark
    val lightIntensityUpperLimitAlarmMark = it.lightIntensityUpperLimitAlarmMark
    val lightIntensityLowerLimitAlarmMark = it.lightIntensityLowerLimitAlarmMark
    val lightEventTimestamp = it.lightEventTimestamp
    val tempEventTimestamp = it.tempEventTimestamp
}
```

There are 2 types of adv frames for MST03 device。

1. Device static information frame

   - DeviceStaticInfoFrame

     | Name                  | Type                  | Description                       |
     | --------------------- | --------------------- | --------------------------------- |
     | companyId             | String                | ManufacturerId.   0x00E0 = Google |
     | googleId              | String                | googleid.   0x19                  |
     | frameVersion          | int                   | frame type                        |
     | firmwareVersion       | String                | Firmware Version                  |
     | battery               | int                   | Battery  range:0~100              |
     | macAddress            | String                | firmware mac.                     |
     | peripheralSupportInfo | PeripheralSupportInfo | Description of peripheral support |
   
2. Combination frame

   - CombinationFrame

     | Name                              | Type   | Description                                                  |
     | --------------------------------- | ------ | ------------------------------------------------------------ |
     | companyId                         | String | ManufacturerId.   0x00E0 = Google                            |
     | googleId                          | String | googleid.   0x19                                             |
     | frameVersion                      | int    | frame typee                                                  |
     | battery                           | int    | Battery  range:0~100                                         |
     | macAddress                        | String | firmware mac.                                                |
     | temperature                       | float  | temperature                                                  |
     | temperatureUpperLimit1AlarmMark   | int    | Temperature upper limit 1 alarm mark. 0 =normal , 1 = alarm  |
     | temperatureLowerLimit1AlarmMark   | int    | Temperature lower limit 1 alarm mark. 0 =normal , 1 = alarm  |
     | temperatureUpperLimit2AlarmMark   | int    | Temperature upper limit 2 alarm mark. 0 =normal , 1 = alarm  |
     | temperatureLowerLimit2AlarmMark   | int    | Temperature lower limit 2 alarm mark. 0 =normal , 1 = alarm  |
     | lightIntensityUpperLimitAlarmMark | int    | Light intensity upper limit alarm mark. 0 =normal , 1 = alarm |
     | lightIntensityLowerLimitAlarmMark | int    | Light intensity lower limit alarm mark. 0 =normal , 1 = alarm |
     | lightEventTimestamp               | long   | Light trigger timestamp.Unit: milliseconds                   |
     | tempEventTimestamp                | long   | Temperature trigger timestamp.Unit: milliseconds             |
     | currentTimestamp                  | long   | Current device timestamp.Unit: milliseconds                  |
     
     
   

### Connections

It is usually necessary to stop scanning before connecting. sdk provides methods to connect and disconnect.。

##### 1.Connect Device

```java
val  mBleManager = 	MST03SensorBleManager.getInstance()
val  module:MST03Entity
//first:Stop scanning
mBleManager.stopScan(context.mApplication)
    
//second:Setting the device secret key
val key="minewtech1234567"
mBleManager.setSecretKey(module.macAddress,key);

//setting listener
mBleManager.setOnConnStateListener(connectionListener)

//Connection, module for the device to be connected 
mBleManager.connect(context,module);
or
mBleManager.connect(context,module.macAddress);


```

Note: Before connecting the device, please confirm whether the device is scanned, if no device broadcast is scanned and the connection method is called, the connection will fail.

##### 2.DisConnect Device

```
//Disconnect. macAddress is the device mac
mBleManager.disConnect(macAddress);
```

##### 3.Device Connect Status Listener

```java
val  mBleManager = 	MST03SensorBleManager.getInstance()
//Setting the listener
mBleManager.setOnConnStateListener{ macAddress, connectionState -> 
                when (connectionState) {
                BleConnectionState.Connecting -> {
                    Log.d("connectionListener", "ConnectionState.Connecting")
                }
                BleConnectionState.Connected -> {
                    Log.d("connectionListener", "ConnectionState.Connected")
                }
                BleConnectionState.AuthenticateSuccess ->{

                }
                BleConnectionState.AuthenticateFail ->{
                    LoadingDialogUtil.dismissLoadingDialog()
                    ToastUtils.showLong(getString(R.string.input_secret_key_error))
                }
                BleConnectionState.ConnectComplete -> {
                    Log.d("connectionListener", "ConnectionState.Connect_Complete")
                    LoadingDialogUtil.dismissLoadingDialog()
                    connectSensorEntity?.let { sensor ->
                        DRouter.build(RouterPaths.MST03_DEVICE_DETAIL_ACTIVITY).apply {
                            this.putExtra(Constant.MAC_KEY,sensor.macAddress)
                        }.start(this@Mst03ScanDeviceListActivity)
                    }

                }

                BleConnectionState.Disconnect -> {
                    Log.d("connectionListener", "ConnectionState.Disconnect")
                    LoadingDialogUtil.dismissLoadingDialog()
                    ToastUtils.showLong(getString(R.string.conn_failure))
                }
                else -> {}
            }                              
                                  
}
```

During the connection process, sdk will return multiple connection states to the app, which the app needs to handle properly.

- **BleConnectionState.Connecting**，**BleConnectionState.Connected**: Connecting the device in, do not do time-consuming operations in this state, because at this time in the connected device discovery service, and send authentication data, etc.。
- **BleConnectionState.ConnectComplete**: The sensor device is successfully connected at this point, and can perform read and write operations, such as configuring advparameters, sensor configuration, reading historical data, etc.。
- **BleConnectionState.AuthenticateFail**：The secret key is verified during the authentication process. If the entered secret key is incorrect, this state will be called back and the device will actively disconnect.
- **BleConnectionState.AuthenticateSuccess**：The secret key is verified during the authentication process. If the entered secret key is correct, this status will be called back.
- **BleConnectionState.Disconnect**: The callback will be made if the connection fails or the device is disconnected. 

### Reading and writing

##### 1.Set device key

```
	/**
      *Set device key
      * @param macAddress device mac
      * @param secretKey secret key key
      * @param listener
      */
     void setDeviceSecretKey(String macAddress, String secretKey, OnModifyConfigurationListener listener);
```

use：

```java
val  mBleManager = 	MST03SensorBleManager.getInstance()
val secretKey = "minewtech1234567"
mBleManager.setSecretKey(mAddress,secretKey)

```

##### 2.Change device password 

```
    /**
     * change device key
     * @param macAddress device mac
     * @param secretKey new secretKey
     * @param listener listener
     */
     void changeSecretKey(String macAddress,String secretKey, OnModifyConfigurationListener listener);
```

use:

```
    suspend fun changePassword(password:String): Boolean = withContext(Dispatchers.Default) {
        if (connectMacAddress == null) {
            return@withContext false
        }
        return@withContext suspendCancellableCoroutine<Boolean> { continuation ->
            manager.changeSecretKey(connectMacAddress!!,password) {
                continuation.resume(it, null)
            }
        }
    }
```



##### 3.Set led Config

```
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
```

use:

```

val duration = 10*1000 //(unit: milliseconds),0-1000s,
val singleCycleLightingTime = 200 //Light on time in a single cycle (unit: milliseconds)
val singleCycleLightOffTime = 200 //The indicator off time in a single period,   (unit: milliseconds)
val workTotalCount = duration/(singleCycleLightingTime+singleCycleLightOffTime); //The number of operation cycles,  0 means no operation
val ledColor = 3 //led Color
val brightness =100 //Brightness 1, 0~100

    suspend fun setLEDConfig(
        color: Int,
        workTotalCount: Int,
        singleCycleLightingTime: Int,
        singleCycleLightOffTime: Int,
        brightness: Int
    ): Boolean =
        withContext(Dispatchers.Default) {
            if (connectMacAddress == null) {
                return@withContext false
            }
            return@withContext suspendCancellableCoroutine<Boolean> { continuation ->
                manager.setLEDConfiguration(
                    connectMacAddress!!,
                    color,
                    workTotalCount,
                    singleCycleLightingTime,
                    singleCycleLightOffTime,
                    brightness
                ) { isSuccess ->
                    continuation.resume(isSuccess, null)
                }
            }
        }
```



##### 4.Query device version information

```
	/**
      * Query device version information
      * @param macAddress device mac
      * @param listener listener
      */
     void queryDeviceFirmwareInfo(String macAddress, OnQueryResultListener<FirmwareVersionModel>  listener);
```



FirmwareVersionModel:

| Name            | Type              | Description                |
| --------------- | ----------------- | -------------------------- |
| versionInfoList | List<VersionInfo> | firmware version data list |



VersionInfo:

| Name            | Type   | Description             |
| --------------- | ------ | ----------------------- |
| firmwareName    | String | firmware name           |
| firmwareType    | int    | type                    |
| firmwareVersion | String | firmware version V3.2.6 |



use:

```java
    suspend fun queryFirmwareInfo(): FirmwareVersionModel? = withContext(Dispatchers.Default) {
        if (connectMacAddress == null) {
            return@withContext null
        }
        return@withContext suspendCancellableCoroutine<FirmwareVersionModel?> { continuation ->
            manager.queryDeviceFirmwareInfo(connectMacAddress!!) { isSuccessful, version ->
                continuation.resume(
                    when (isSuccessful) {
                        true -> version
                        else -> null
                    }, null
                )
            }
        }
    }
```

##### 5.Query adv frame broadcast parameters and configure adv parameters

```
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
```

AdvParametersConfiguration:

| Name                | Type   | Description                                                  |
| ------------------- | ------ | ------------------------------------------------------------ |
| slotNumber          | int    | 0 = DeviceStaticInfoFrame 、 1 = CombinationFrame            |
| frameType           | String | frane type                                                   |
| advertisingInterval | int    | advertising Interval   range = 1s to 60s ,the scale is 1s .   unit= millisecond |
| txPower             | int    | txPower  value = -40, -20 ,-16, -12, -8, -4, 0, 4    unit=dBm |
| alwaysAdvertising   | int    |                                                              |
| advertisingContent  | String | device  name.                                                |





use:

```
    
    //get  0 = DeviceStaticInfoFrame 、 1 = CombinationFrame
    suspend fun queryAdvertisingParametersConfiguration(slot:Int): AdvParametersConfiguration? =
        withContext(Dispatchers.Default) {
            if (connectMacAddress == null) {
                return@withContext null
            }
            return@withContext suspendCancellableCoroutine<AdvParametersConfiguration?> { continuation ->
                manager.queryAdvParametersConfiguration(connectMacAddress!!,slot) { isSuccessful, configResult ->

                    continuation.resume(
                        when (isSuccessful) {
                            true -> configResult
                            else -> null
                        }, null
                    )
                }
            }
        }
        
    //set
    suspend fun setAdvertisingParametersConfiguration(slot:Int,frameType: String,advertisingInterval:Int, txPower:Int,advertisingContent: String?): Boolean =
        withContext(Dispatchers.Default) {
            if (connectMacAddress == null) {
                return@withContext false
            }
            return@withContext suspendCancellableCoroutine<Boolean> { continuation ->
                manager.setAdvParametersConfiguration(connectMacAddress!!,frameType,slot,advertisingInterval,txPower,advertisingContent) { isSuccess ->
                    continuation.resume(isSuccess, null)
                }
            }
        } 
        //for example
        mAdvParametersConfiguration?.let {
            lifecycleScope.launch(Dispatchers.Main) {
                LoadingDialogUtil.showLoadingDialog()
                val result = mConnectViewModel.setAdvertisingParametersConfiguration(
                    it.slotNumber,
                    it.frameType,
                    it.advertisingInterval,
                    it.txPower,
                    it.advertisingContent
                )
                LoadingDialogUtil.dismissLoadingDialog()
                if (result) {
                    ToastUtils.showShort(getString(R.string.common_config_success))
                } else {
                    ToastUtils.showShort(getString(R.string.common_config_fail))
                }
            }
        }
        
```





##### 6.Query temperature historical data



```
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
     void queryTemperatureHistoryData(String macAddress,int rules,long startTime,long endTime,long systemTime, OnQueryResultListener<HistoryHtData>  listener);
```

HistoryHtData:

| Name            | Type         | Description      |
| --------------- | ------------ | ---------------- |
| historyDataList | List<HtData> | temperature data |

HtData:

| Name        | Type   | Description                     |
| ----------- | ------ | ------------------------------- |
| macAddress  | String | mac address                     |
| temperature | float  | temperature data                |
| humidity    | float  | Invalid value  -128f            |
| timestamps  | long   | record time . unit= millisecond |



use:

```

val systemTime = System.currentTimeMillis()/1000
val startTime = (systemTime-60*60*1000*24)/1000
val endTime = systemTime

val rule = 0

suspend fun readHtHistoryData(rules:Int,startTime:Long,endTime:Long,systemTime:Long): HistoryHtData?=
        withContext(Dispatchers.Default) {
        if (connectMacAddress == null) {
            return@withContext null
        }
        return@withContext suspendCancellableCoroutine<HistoryHtData?> { continuation ->
            manager.queryTemperatureHistoryData(connectMacAddress!!,rules,startTime,endTime,systemTime) { result,historyData ->
                continuation.resume(historyData, null)
            }
        }
    }
```



##### 7.Clear Temperature  Data

```
    /**
     * Clear historical  data
     * 
     * @param macAddress Device MAC address
     * @param listener Listener s
     */
    void cleanTempHistoryData(String macAddress, OnModifyConfigurationListener listener);
```



use:

```
    suspend fun cleanTempHistoryData(): Boolean = withContext(Dispatchers.Default) {
        if (connectMacAddress == null) {
            return@withContext false
        }
        return@withContext suspendCancellableCoroutine<Boolean> { continuation ->
            manager.cleanTempHistoryData(connectMacAddress!!) {
                continuation.resume(it, null)
            }
        }
    }
```



##### 8.Query Light Historical Data

```
/**
  *
  * @param macAddress device mac
  * @param rules Historical data acquisition rules, 0 means to obtain all data, 1 means to obtain data of a certain time           *period, when rules==0, startTime and endTime do not need to be passed, and 0 is passed by default
  * @param startTime starting timestamp unit: seconds
  * @param endTime end timestamp unit: seconds,Cannot be greater than systemTime
  * @param systemTime real-time timestamp unit: seconds
  * @param listener listener
  */
 void queryLightHistoryData(String macAddress,int rules,long startTime,long endTime,long systemTime, OnQueryResultListener<HistoryLightData>  listener);
```
HistoryLightData:

| Name            | Type            | Description |
| --------------- | --------------- | ----------- |
| historyDataList | List<LightData> | Light data  |

LightData:

| Name           | Type   | Description                                                  |
| -------------- | ------ | ------------------------------------------------------------ |
| macAddress     | String | mac address                                                  |
| lightIntensity | int    | light Intensity  unit=nW/cm²                                 |
| alarmType      | int    | alarm type.1=Low threshold trigger.2 = High threshold trigger,255 = Fixed interval collection |
| timestamps     | long   | record time . unit= millisecond                              |



​		use:

​		

```
val systemTime = System.currentTimeMillis()/1000
val startTime = (systemTime-60*60*1000*24)/1000
val endTime = systemTime

val rule = 0

suspend fun readLightHistoryData(rules:Int,startTime:Long,endTime:Long,systemTime:Long): HistoryLightData?=
        withContext(Dispatchers.Default) {
        if (connectMacAddress == null) {
            return@withContext null
        }
        return@withContext suspendCancellableCoroutine<HistoryLightData?> { continuation ->
            manager.queryLightHistoryData(connectMacAddress!!,rules,startTime,endTime,systemTime) { result,historyData ->
                continuation.resume(historyData, null)
            }
        }
    }
```





##### 9.Clear Light Data

​	

```
    /**
     * Clear historical  data
     * 
     * @param macAddress Device MAC address
     * @param listener Listener s
     */
    void cleanLightHistoryData(String macAddress, OnModifyConfigurationListener listener);
```



​		use:

```
    suspend fun cleanLightHistoryData(): Boolean = withContext(Dispatchers.Default) {
        if (connectMacAddress == null) {
            return@withContext false
        }
        return@withContext suspendCancellableCoroutine<Boolean> { continuation ->
            manager.cleanLightHistoryData(connectMacAddress!!) {
                continuation.resume(it, null)
            }
        }
    }
```



##### 10.Query temperature sensor parameters and configure parameters

```

	/**
      * Query temperature and humidity sensor configuration
      *
      * @param macAddress device mac
      * @param listener listener
      */
     void queryHTSensorConfiguration(String macAddress, OnQueryResultListener<HtSensorConfiguration> listener);
     
     
	/**
      * Set temperature and humidity sensor configuration
      *
      * @param macAddress device mac
      * @param htSensorConfiguration sensor configuration
      * @param listener listener
      */
     void setHTSensorConfiguration(String macAddress, HtSensorConfiguration htSensorConfiguration, 				  OnModifyConfigurationListener listener);


```

HtSensorConfiguration:

| Name             | Type                          | Description                         |
| ---------------- | ----------------------------- | ----------------------------------- |
| htSettingData    | List<HTSensorThresholdConfig> |                                     |
| samplingInterval | int                           | Collection interval. Unit: seconds  |
| delay            | int                           | Delay recording time. Unit: seconds |

HTSensorThresholdConfig:

| Name            | Type  | Description                                                  |
| --------------- | ----- | ------------------------------------------------------------ |
| lowTemperature  | float | Lower limit of low temperature，value:-128  means not enabled ,unit = ℃ |
| highTemperature | float | High temperature upper limit，value:-128 means not enabled,unit = ℃ |
| lowHumidity     | float | Invalid value -128，-128 means not enabled                   |
| highHumidity    | float | Invalid value -128，-128 means not enabled                   |

Note:The mst03 has two sets of temperature threshold configurations. The first group is the normal temperature alarm threshold, ranging from 20℃ to 50℃, and the second group is the low temperature alarm threshold, ranging from -30℃ to 15℃.

use:

```java
//Query sensor parameters
    suspend fun queryHTSensorConfiguration(): HtSensorConfiguration? =
        withContext(Dispatchers.Default) {
            if (connectMacAddress == null) {
                return@withContext null
            }
            return@withContext suspendCancellableCoroutine<HtSensorConfiguration?> { continuation ->
                manager.queryHTSensorConfiguration(connectMacAddress!!) { isSuccessful, configResult ->

                    continuation.resume(
                        when (isSuccessful) {
                            true -> configResult
                            else -> null
                        }, null
                    )
                }
            }
        }
//Set sensor parameters
        val list: MutableList<HTSensorThresholdConfig> = mutableListOf()
            //high temp config
        val tempHtConfig1 = HTSensorThresholdConfig()
        when (binding.switchTemperature1Alarm.isChecked) {
            true -> {
                tempHtConfig1.highTemperature = 25
                tempHtConfig1.lowTemperature = 23
            }
            else -> {
                tempHtConfig1.highTemperature = -128f
                tempHtConfig1.lowTemperature = -128f
            }
        }
        tempHtConfig1.highHumidity = -128f
        tempHtConfig1.lowHumidity = -128f
		//low temp config
        val tempHtConfig2 = HTSensorThresholdConfig()
        when (binding.switchTemperature2Alarm.isChecked) {
            true -> {
                tempHtConfig2.highTemperature = 8
                tempHtConfig2.lowTemperature = 2
            }
            else -> {
                tempHtConfig2.highTemperature = -128f
                tempHtConfig2.lowTemperature = -128f
            }
        }
        tempHtConfig2.highHumidity = -128f
        tempHtConfig2.lowHumidity = -128f
        list.add(tempHtConfig1)
        list.add(tempHtConfig2)

        val configuration: HtSensorConfiguration = HtSensorConfiguration()
        configuration.samplingInterval = samplingInterval
        configuration.delay = delayRecordTime
        configuration.htSettingData = list
            
    suspend fun setHTSensorConfiguration(configuration: HtSensorConfiguration): Boolean =
        withContext(Dispatchers.Default) {
            if (connectMacAddress == null) {
                return@withContext false
            }
            return@withContext suspendCancellableCoroutine<Boolean> { continuation ->
                manager.setHTSensorConfiguration(connectMacAddress!!,configuration) { isSuccess ->
                    continuation.resume(isSuccess, null)
                }
            }
        }   
```

##### 11.Query Light sensor parameters and configure parameters

​	LightIntensitySensorConfiguration:

​	

| Name                          | Type | Description                                                  |
| ----------------------------- | ---- | ------------------------------------------------------------ |
| lightIntensityLowerLimitAlarm | int  | Low Light Threshold.Range: 1.2 to 10,000,000 nW/cm². for example: 1.3 = lightIntensityLowerLimitAlarm/10f,(lightIntensityLowerLimitAlarm = 13) |
| lightIntensityUpperLimitAlarm | int  | High Light Threshold.Range: 1.2 to 10,000,000 nW/cm².for example: 1300 = lightIntensityUpperLimitAlarm/10f,(lightIntensityUpperLimitAlarm = 13000) |

​	use:

​	

```
//get
    suspend fun getLightIntensityConfiguration(): LightIntensitySensorConfiguration? =
        withContext(Dispatchers.Default) {
            if (connectMacAddress == null) {
                return@withContext null
            }
            return@withContext suspendCancellableCoroutine<LightIntensitySensorConfiguration?> { continuation ->
                manager.getLightIntensityConfiguration(connectMacAddress!!) { isSuccessful, configResult ->

                    continuation.resume(
                        when (isSuccessful) {
                            true -> configResult
                            else -> null
                        }, null
                    )
                }
            }
        }

     mLightIntensitySensorConfiguration?.let {
                binding.highLightThresholdEdit.setText("${it.lightIntensityUpperLimitAlarm/10f}")// nW/cm²
                binding.lowLightThresholdEdit.setText("${it.lightIntensityLowerLimitAlarm/10f}")// nW/cm²
     } 
        
        
        
 //set
         val lightIntensitySensorConfiguration = LightIntensitySensorConfiguration().apply {
            this.lightIntensityLowerLimitAlarm = (lowLightThresholdInputText.toFloat() * 10).toInt()
            this.lightIntensityUpperLimitAlarm = (highLightThresholdInputText.toFloat() * 10).toInt()

        }
     suspend fun setLightIntensityConfiguration(configuration: LightIntensitySensorConfiguration): Boolean =
        withContext(Dispatchers.Default) {
            if (connectMacAddress == null) {
                return@withContext false
            }
            return@withContext suspendCancellableCoroutine<Boolean> { continuation ->
                manager.setLightIntensityConfiguration(connectMacAddress!!,configuration) { isSuccess ->
                    continuation.resume(isSuccess, null)
                }
            }
        } 
```



##### 12.Query Alarm strategy and set

​	AlarmStrategyConfiguration:

​	

| Name                | Type | Description                                                  |
| ------------------- | ---- | ------------------------------------------------------------ |
| alarmLedDuration    | int  | Alarm light flashing duration, 60s (1min) to 14400s (240min) in seconds |
| alarmCountThreshold | int  | The number of consecutive threshold violations required to trigger an alarm   range:0~255 |



​			use:

​			

```
//get
    suspend fun getAlarmStrategyConfiguration(): AlarmStrategyConfiguration? =
        withContext(Dispatchers.Default) {
            if (connectMacAddress == null) {
                return@withContext null
            }
            return@withContext suspendCancellableCoroutine<AlarmStrategyConfiguration?> { continuation ->
                manager.getAlarmStrategyConfiguration(connectMacAddress!!) { isSuccessful, configResult ->

                    continuation.resume(
                        when (isSuccessful) {
                            true -> configResult
                            else -> null
                        }, null
                    )
                }
            }
        }
   //set
   val alarmStrategyConfiguration = AlarmStrategyConfiguration().apply {
            this.alarmLedDuration = 10*60  //unit:seconds
            this.alarmCountThreshold = 5
        }
        
        
suspend fun setAlarmStrategyConfiguration(configuration: AlarmStrategyConfiguration): Boolean =
        withContext(Dispatchers.Default) {
            if (connectMacAddress == null) {
                return@withContext false
            }
            return@withContext suspendCancellableCoroutine<Boolean> { continuation ->
                manager.setAlarmStrategyConfiguration(connectMacAddress!!,configuration) { isSuccess ->
                    continuation.resume(isSuccess, null)
                }
            }
        }     
   
        
```





##### 13.Firmware upgrade



```
		/**
      * Firmware upgrade
      *
      * @param macAddress device mac
      * @param isLinkUpgrade =true: url upgrade, isLinkUpgrade =false: firmware package upgrade. The default value is false, and the url upgrade  	  * method is not supported. 
      * @param dfuTarget isLinkUpgrade =true, dfuTarget = 0
      * @param upgradeData upgrade package data
      * @param listener listener
      */
     void firmwareUpgrade(String macAddress,boolean isLinkUpgrade,int dfuTarget,byte[] upgradeData, 		 OnFirmwareUpgradeListener listener);
     
      /**
		* Verify the correctness of the OTA upgrade file
		* @param zipFilePath OTA upgrade file path
		* @return true = mst03 upgrade file, false = not an mst03 ota file
	*/
    boolean verifyOtaFile(@NonNull String zipFilePath);
     
```



use:

```java

    private fun checkFile(fileUri: Uri) {

        var fileName = ""
        val cursor =this.contentResolver.query(fileUri, null, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                fileName = cursor.getString(
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                )
            } else {
            }
        } finally {
            cursor?.close()
        }
//        LogUtil.e(TAG, "fileName=$fileName")
        val zipFilePath = FileUtil.getFileAbsolutePath(this, fileUri)
        val verifyResult = mConnectViewModel.verifyOtaFile(zipFilePath)
//        LogUtil.e(TAG, "verifyResult=$verifyResult")
        if (!verifyResult) {
            chooseFileError(R.string.common_file_select_error)
            return
        }
        dealFileData(zipFilePath)
    }




    private fun dealFileData(zipFilePath: String) {
        MinewExecutors.getInstance().getDiskIO().execute {
            try {
                val bininputstream = ZipUtil.readZipFile(zipFilePath, ".bin")
                val bufferedInputStream = BufferedInputStream(bininputstream)
                val allData = ArrayList<Byte>()
                //4k
                val buffer = ByteArray(1024 * 4)
                var bytesRead = 0
                var tempLength = 0
                while (bufferedInputStream.read(buffer).also { bytesRead = it } != -1) {
                   
                    for (i in 0 until bytesRead) {
                        val by = buffer[i]
                        allData.add(by)
                    }
                    tempLength += bytesRead
                }
                val fileData = ByteArray(allData.size)
                for (i in allData.indices) {
                    fileData[i] = allData[i]
                }
                bufferedInputStream.close()
                firmwareUpgrade(fileData, 0)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                chooseFileError(R.string.common_file_select_error)
            } catch (e: IOException) {
                e.printStackTrace()
                chooseFileError(R.string.common_file_select_error)
            }
        }
    }


    private fun firmwareUpgrade(
        fileByte: ByteArray,
        dfuTarget: Int
    ) {
        mConnectViewModel.firmwareUpgrade(dfuTarget, fileByte,
            progressCallBack = { progress ->
                WaitDialog.show(getString(common.R.string.common_upgrading), (progress / 100f))
                binding.progressBarSimpleCustom.progress = progress.toFloat()
            },
            successCallBack = {
                WaitDialog.dismiss()
                ToastUtils.showShort(R.string.common_firmware_upgrade_successfully)
                showFirmwareUpgradeSuccessfulDialog()
            },
            failCallBack = {
                WaitDialog.dismiss()
                ToastUtils.showShort(R.string.common_firmware_upgrade_failure2)
            }
        )
    }


    fun firmwareUpgrade(
        dfuTarget: Int,
        fileByte: ByteArray,
        progressCallBack:(progress:Int) -> Unit,
        successCallBack:() -> Unit,
        failCallBack:() -> Unit,
    ){
        manager.firmwareUpgrade(connectMacAddress!!,false,dfuTarget,fileByte,
            object : OnFirmwareUpgradeListener {
                override fun updateProgress(progress: Int) {
                    progressCallBack(progress)
                }

                override fun upgradeSuccess() {
                    successCallBack()
                }

                override fun upgradeFailed() {
                    failCallBack()
                }
            })
    }
```

##### 14.Restore the factory settings

   ```
    /**
     * Restore factory settings
     *
     * @param macAddress device mac
     * @param listener listener
     */
     void reset(String macAddress, OnModifyConfigurationListener listener);

   ```

   use:

​	

```
    suspend fun reset(): Boolean = withContext(Dispatchers.Default) {
        if (connectMacAddress == null) {
            return@withContext false
        }
        return@withContext suspendCancellableCoroutine<Boolean> { continuation ->
            manager.reset(connectMacAddress!!) {
                continuation.resume(it, null)
            }
        }
    }
```



##### 15.Shutdown

```java
    /**
     * Shutdown
     * @param macAddress device mac
     * @param listener listener
     */
     void powerOff(String macAddress, OnModifyConfigurationListener listener);
```

use:

```
    suspend fun shutdown(): Boolean = withContext(Dispatchers.Default) {
        if (connectMacAddress == null) {
            return@withContext false
        }
        return@withContext suspendCancellableCoroutine<Boolean> { continuation ->
            manager.powerOff(connectMacAddress!!) {
                continuation.resume(it, null)
            }
        }
    }
```









## History

- 2025/08/06  edit；

  

  
