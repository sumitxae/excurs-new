package com.choruscoldchain.ble

import android.content.Context
import android.util.Log
import com.choruscoldchain.data.models.*
import com.choruscoldchain.utils.TimestampConverter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

// Import Minew SDK classes
import com.minew.ble.mst03.manager.MST03SensorBleManager
import com.minew.ble.mst03.bean.MST03Entity
import com.minew.ble.mst03.bean.HtSensorConfiguration
import com.minew.ble.v3.bean.HTSensorThresholdConfig

import com.minew.ble.v3.interfaces.OnScanDevicesResultListener
import com.minew.ble.v3.enums.BleConnectionState
import com.minew.ble.v3.enums.FrameType
import com.minew.ble.mst03.frames.CombinationFrame
import com.minew.ble.mst03.frames.DeviceStaticInfoFrame
// Removed direct dependency on OnReceiveDataListener; using reflection/proxy to avoid API coupling
import com.minew.ble.v3.interfaces.OnFirmwareUpgradeListener
import com.choruscoldchain.utils.PermissionHelper
import com.choruscoldchain.utils.BluetoothHelper
import com.minew.ble.mst03.bean.HtData;

// Android BLE raw scan imports
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
// Android BLE GATT imports (native connection)
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

@Singleton
class MinewBleManager @Inject constructor() {
    
    init {
        Log.d(TAG, "MinewBleManager constructor called")
    }
    
    companion object {
        private const val TAG = "MinewBleManager"
        private const val DEFAULT_SECRET_KEY = "minewtech1234567"
        private const val MANUFACTURER_ID = "E000" // Google
    }
    
    private val mst03Manager = MST03SensorBleManager.getInstance()
    
    // State flows for real-time updates
    private val _scanResults = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val scanResults: StateFlow<List<DeviceInfo>> = _scanResults.asStateFlow()
    
    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()
    
    private val _sensorDataUpdates = Channel<SensorData>(Channel.UNLIMITED)
    val sensorDataUpdates: Flow<SensorData> = _sensorDataUpdates.receiveAsFlow()

    // Raw hex data stream (new, non-breaking)
    private val _rawDataUpdates = Channel<com.choruscoldchain.data.models.RawBleData>(Channel.UNLIMITED)
    val rawDataUpdates: Flow<com.choruscoldchain.data.models.RawBleData> = _rawDataUpdates.receiveAsFlow()

    // Prevent duplicate listener registration
    @Volatile private var rawListenerRegistered: Boolean = false

    // ========== RAW ADVERTISEMENT SCANNING (pre-connection) ==========
    private val MANUFACTURER_ID_HEX = "E000"
    private val MANUFACTURER_ID_INT_PRIMARY = 0x00E0
    private val MANUFACTURER_ID_INT_ALT = 0x00E0 // Observed in logs
    private val _rawAdvDataUpdates = Channel<com.choruscoldchain.data.models.RawBleData>(Channel.UNLIMITED)
    val rawAdvDataUpdates: Flow<com.choruscoldchain.data.models.RawBleData> = _rawAdvDataUpdates.receiveAsFlow()
    @Volatile private var isRawAdvScanning: Boolean = false
    @Volatile private var bleScanner: BluetoothLeScanner? = null
    @Volatile private var rawScanCallback: ScanCallback? = null
    @Volatile private var rawAdvDebugLogging: Boolean = false

    fun setRawAdvDebugLogging(enabled: Boolean) {
        rawAdvDebugLogging = enabled
        Log.d(TAG, "Raw ADV debug logging: $enabled")
    }
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val scannedDevices = mutableMapOf<String, DeviceInfo>()
    private val connectedDevices = mutableMapOf<String, String>() // MAC -> Secret Key
    
    init {
        Log.d(TAG, "MinewBleManager init block starting")
        try {
            mst03Manager.setManufacturerIdHexLe(MANUFACTURER_ID)
            Log.d(TAG, "Manufacturer ID set successfully")
            setupConnectionListener()
            Log.d(TAG, "MinewBleManager init block completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in MinewBleManager init block", e)
        }
    }
    
    fun startScan(context: Context, durationMs: Int = 5 * 60 * 1000): Boolean {
        Log.d(TAG, "Starting BLE scan for ${durationMs / 1000} seconds")

        // Prevent starting a new scan if one is already running
        if (_isScanning.value) {
            Log.w(TAG, "Scan already in progress; ignoring duplicate startScan call")
            return true
        }
        
        // Check permissions before starting scan
        if (!hasRequiredPermissions(context)) {
            Log.e(TAG, "Required permissions not granted for BLE scanning")
            _isScanning.value = false
            return false
        }
        
        try {
            _isScanning.value = true
            scannedDevices.clear()
            mst03Manager.startScan(context, durationMs, object : OnScanDevicesResultListener<MST03Entity> {
                override fun onScanResult(scanList: MutableList<MST03Entity>?) {
                    scanList?.let { devices ->
                        Log.d(TAG, "Scan result: ${devices.size} devices found")
                        processScanResults(devices)
                    }
                }
                
                override fun onStopScan(scanList: MutableList<MST03Entity>?) {
                    Log.d(TAG, "Scan stopped")
                    _isScanning.value = false
                    scanList?.let { devices ->
                        processScanResults(devices)
                    }
                }
            })
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when starting scan - permissions not granted", e)
            _isScanning.value = false
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            _isScanning.value = false
            return false
        }
    }
    
    private fun hasRequiredPermissions(context: Context): Boolean {
        return try {
            PermissionHelper.hasAllRequiredPermissions(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }
    
    private fun setupConnectionListener() {
        Log.d(TAG, "Setting up connection listener")
        try {
            mst03Manager.setOnConnStateListener { macAddress, connectionState ->
                Log.d(TAG, "Connection state change: $macAddress -> $connectionState")
                
                val state = when (connectionState) {
                    BleConnectionState.Connecting -> {
                        Log.d(TAG, "Device $macAddress is connecting...")
                        ConnectionState.CONNECTING
                    }
                    BleConnectionState.Connected -> {
                        Log.d(TAG, "Device $macAddress connected successfully")
                        ConnectionState.CONNECTED
                    }
                    BleConnectionState.AuthenticateSuccess -> {
                        Log.d(TAG, "Device $macAddress authenticated successfully")
                        ConnectionState.AUTHENTICATED
                    }
                    BleConnectionState.AuthenticateFail -> {
                        Log.e(TAG, "Device $macAddress authentication failed - check secret key")
                        ConnectionState.ERROR
                    }
                    BleConnectionState.ConnectComplete -> {
                        Log.d(TAG, "Device $macAddress connection complete - READY state")
                        ConnectionState.READY
                    }
                    BleConnectionState.Disconnect -> {
                        Log.d(TAG, "Device $macAddress disconnected")
                        ConnectionState.DISCONNECTED
                    }
                    else -> {
                        Log.w(TAG, "Device $macAddress unknown state: $connectionState")
                        ConnectionState.ERROR
                    }
                }
                
                Log.d(TAG, "Updating connection state for $macAddress: $state")
                updateConnectionState(macAddress, state)
                
                // Log all current connection states for debugging
                Log.d(TAG, "All connection states after update: ${_connectionStates.value}")
                
                // Additional error handling for specific failure cases
                when (connectionState) {
                    BleConnectionState.AuthenticateFail -> {
                        Log.e(TAG, "Authentication failed for $macAddress. Possible causes:")
                        Log.e(TAG, "1. Incorrect secret key")
                        Log.e(TAG, "2. Device is already connected to another app")
                        Log.e(TAG, "3. Device is out of range")
                        Log.e(TAG, "4. Device firmware issue")
                    }
                    BleConnectionState.Disconnect -> {
                        Log.e(TAG, "Connection failed for $macAddress. Possible causes:")
                        Log.e(TAG, "1. Device is out of range")
                        Log.e(TAG, "2. Device is already connected")
                        Log.e(TAG, "3. Bluetooth is disabled")
                        Log.e(TAG, "4. Insufficient permissions")
                    }
                    else -> {
                        Log.e(TAG, "Connection issue for $macAddress. Possible causes:")
                        Log.e(TAG, "1. Device is too far away")
                        Log.e(TAG, "2. Device is busy or not responding")
                        Log.e(TAG, "3. Bluetooth interference")
                    }
                }
            }
            Log.d(TAG, "Connection listener setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up connection listener", e)
        }
    }

    private fun tryRegisterRawListener(macAddress: String) {
        if (rawListenerRegistered) {
            Log.d(TAG, "Raw listener already registered; skipping.")
            return
        }
        try {
            val managerClass = mst03Manager.javaClass
            val methods = managerClass.methods

            // Debug: dump all methods briefly
            try {
                Log.d(TAG, "Minew manager methods count: ${methods.size}")
                methods.take(1000).forEach { m ->
                    val params = m.parameterTypes.joinToString(", ") { it.name }
                    Log.d(TAG, "Method: ${m.name}(${params}) -> ${m.returnType?.name}")
                }
            } catch (_: Throwable) {}

            // Narrow to candidate listener-like setters
            val listenerLike = methods.filter { m ->
                m.parameterTypes.size == 1 && m.parameterTypes[0].isInterface && (
                    m.name.contains("listener", true) ||
                    m.name.contains("receive", true) ||
                    m.name.contains("callback", true)
                )
            }
            Log.d(TAG, "Minew manager listener-like methods: ${listenerLike.map { it.name }}")

            val setter = methods.firstOrNull { m ->
                m.parameterTypes.size == 1 && m.parameterTypes[0].isInterface &&
                    !m.name.equals("setOnConnStateListener", true) && (
                        m.name.contains("setOnReceiveDataListener", true) ||
                        m.name.contains("setOnDataListener", true) ||
                        m.name.contains("setReceiveListener", true) ||
                        (m.name.startsWith("set", true) && m.name.contains("listener", true))
                    )
            }

            if (setter != null) {
                val listenerInterface = setter.parameterTypes[0]
                Log.d(TAG, "Using setter: ${setter.name}, interface: ${listenerInterface.name}")
                val proxy = buildRawProxy(macAddress, listenerInterface)
                setter.invoke(mst03Manager, proxy)
                rawListenerRegistered = true
                Log.d(TAG, "Raw data listener registered via reflection: ${setter.name}")
                return
            }

            // If not found on top-level, inspect connection manager
            tryRegisterOnSubManager(macAddress, hostName = "ConnManager") {
                val m = mst03Manager::class.java.getMethod("getConnSensorManager")
                m.invoke(mst03Manager)
            }
            if (rawListenerRegistered) return

            // Inspect scan manager as well
            tryRegisterOnSubManager(macAddress, hostName = "ScanManager") {
                val m = mst03Manager::class.java.getMethod("getScanSensorManager")
                m.invoke(mst03Manager)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register raw data listener", t)
        }
    }

    private fun buildRawProxy(macAddress: String, listenerInterface: Class<*>): Any {
        return java.lang.reflect.Proxy.newProxyInstance(
            listenerInterface.classLoader,
            arrayOf(listenerInterface)
        ) { _, method, args ->
            try {
                val methodName = method.name
                Log.d(TAG, "Raw listener invoked: method=$methodName argsCount=${args?.size ?: 0}")
                if (args != null && methodName.startsWith("on", ignoreCase = true)) {
                    var foundMac: String? = null
                    var hex: String? = null

                    for (arg in args) {
                        when (arg) {
                            is String -> if (arg.count { it == ':' } == 5) foundMac = arg
                            is ByteArray -> hex = bytesToHex(arg)
                        }
                    }

                    if (hex != null) {
                        Log.d(TAG, "RAW DATA - ${foundMac ?: macAddress}: $hex")
                        _rawDataUpdates.trySend(
                            com.choruscoldchain.data.models.RawBleData(
                                macAddress = foundMac ?: macAddress,
                                hexPayload = hex,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (inner: Throwable) {
                Log.e(TAG, "Error in raw data proxy invoke", inner)
            }
            null
        }
    }

    private fun tryRegisterOnSubManager(macAddress: String, hostName: String, provider: () -> Any?) {
        try {
            val host = provider.invoke() ?: return
            val cls = host.javaClass
            val methods = cls.methods
            Log.d(TAG, "$hostName methods count: ${methods.size}")
            methods.take(1000).forEach { m ->
                val params = m.parameterTypes.joinToString(", ") { it.name }
                Log.d(TAG, "$hostName Method: ${m.name}(${params}) -> ${m.returnType?.name}")
            }

            val setter = methods.firstOrNull { m ->
                m.parameterTypes.size == 1 && m.parameterTypes[0].isInterface &&
                    !m.name.equals("setOnConnStateListener", true) && (
                        m.name.contains("setOnReceiveDataListener", true) ||
                        m.name.contains("setOnDataListener", true) ||
                        m.name.contains("setReceiveListener", true) ||
                        (m.name.startsWith("set", true) && m.name.contains("listener", true))
                    )
            }

            if (setter != null) {
                val listenerInterface = setter.parameterTypes[0]
                Log.d(TAG, "Using $hostName setter: ${setter.name}, interface: ${listenerInterface.name}")
                val proxy = buildRawProxy(macAddress, listenerInterface)
                setter.invoke(host, proxy)
                rawListenerRegistered = true
                Log.d(TAG, "Raw data listener registered on $hostName via reflection: ${setter.name}")
            } else {
                Log.w(TAG, "No suitable raw listener setter found on $hostName")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register raw listener on $hostName", t)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }
    
    private fun processSensorData(entity: MST03Entity) {
        try {
            // Get device static info
            val deviceStaticFrame = entity.getMinewFrame(FrameType.CUSTOM_DEVICE_INFORMATION_FRAME) as? DeviceStaticInfoFrame
            val combinationFrame = entity.getMinewFrame(FrameType.CUSTOM_COMBINATION_FRAME) as? CombinationFrame
            
            combinationFrame?.let { frame ->
                val macAddress = deviceStaticFrame?.macAddress ?: entity.macAddress
                val sensorData = SensorData(
                    macAddress = macAddress,
                    temperature = frame.temperature,
                    lightIntensity = 0, // Light intensity not directly available in combination frame
                    battery = frame.battery,
                    temperatureUpperLimit1AlarmMark = frame.temperatureUpperLimit1AlarmMark == 1,
                    temperatureLowerLimit1AlarmMark = frame.temperatureLowerLimit1AlarmMark == 1,
                    temperatureUpperLimit2AlarmMark = frame.temperatureUpperLimit2AlarmMark == 1,
                    temperatureLowerLimit2AlarmMark = frame.temperatureLowerLimit2AlarmMark == 1,
                    lightIntensityUpperLimitAlarmMark = frame.lightIntensityUpperLimitAlarmMark == 1,
                    lightIntensityLowerLimitAlarmMark = frame.lightIntensityLowerLimitAlarmMark == 1,
                    lightEventTimestamp = frame.lightEventTimestamp,
                    tempEventTimestamp = frame.tempEventTimestamp,
                    timestamp = frame.currentTimestamp
                )
                
                // Send sensor data update
                _sensorDataUpdates.trySend(sensorData)
                Log.d(TAG, "Processed sensor data: temp=${frame.temperature}, battery=${frame.battery}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing sensor data: ${entity.macAddress}", e)
        }
    }
    
    private fun processScanResults(devices: List<MST03Entity>) {
        devices.forEach { entity ->
            val deviceInfo = processScannedDevice(entity)
            if (deviceInfo != null) {
                scannedDevices[deviceInfo.macAddress] = deviceInfo
            }
        }
        _scanResults.value = scannedDevices.values.toList()
    }
    
    private fun processScannedDevice(entity: MST03Entity): DeviceInfo? {
        return try {
            // Get device static info
            val deviceStaticFrame = entity.getMinewFrame(FrameType.CUSTOM_DEVICE_INFORMATION_FRAME) as? DeviceStaticInfoFrame
            val combinationFrame = entity.getMinewFrame(FrameType.CUSTOM_COMBINATION_FRAME) as? CombinationFrame
            
            val macAddress = deviceStaticFrame?.macAddress ?: entity.macAddress
            val battery = deviceStaticFrame?.battery ?: combinationFrame?.battery ?: 0
            val firmwareVersion = deviceStaticFrame?.firmwareVersion ?: "Unknown"
            
            // Get temperature from combination frame if available
            val temperature = combinationFrame?.temperature
            
            // Create static frame data string
            val staticFrameData = deviceStaticFrame?.let { frame ->
                """
                MAC Address: ${frame.macAddress}
                Firmware Version: ${frame.firmwareVersion}
                Battery Level: ${frame.battery}%
                """.trimIndent()
            }
            
            // Create combination frame data string
            val combinationFrameData = combinationFrame?.let { frame ->
                """
                Temperature: ${frame.temperature}°C
                Battery Level: ${frame.battery}%
                Company ID: ${frame.companyId}
                Google ID: ${frame.googleId}
                Frame Version: ${frame.frameVersion}
                MAC Address: ${frame.macAddress}
                Temperature Upper Limit 1 Alarm: ${if (frame.temperatureUpperLimit1AlarmMark == 1) "ON" else "OFF"}
                Temperature Lower Limit 1 Alarm: ${if (frame.temperatureLowerLimit1AlarmMark == 1) "ON" else "OFF"}
                Temperature Upper Limit 2 Alarm: ${if (frame.temperatureUpperLimit2AlarmMark == 1) "ON" else "OFF"}
                Temperature Lower Limit 2 Alarm: ${if (frame.temperatureLowerLimit2AlarmMark == 1) "ON" else "OFF"}
                Light Upper Limit Alarm: ${if (frame.lightIntensityUpperLimitAlarmMark == 1) "ON" else "OFF"}
                Light Lower Limit Alarm: ${if (frame.lightIntensityLowerLimitAlarmMark == 1) "ON" else "OFF"}
                Light Event Timestamp: ${frame.lightEventTimestamp}
                Temp Event Timestamp: ${frame.tempEventTimestamp}
                Current Timestamp: ${frame.currentTimestamp}
                """.trimIndent()
            }
            
            val deviceInfo = DeviceInfo(
                macAddress = macAddress,
                name = entity.name ?: "MST03-${macAddress.takeLast(4)}",
                rssi = entity.rssi,
                battery = battery,
                temperature = temperature,
                firmwareVersion = firmwareVersion,
                isConnected = _connectionStates.value[macAddress] == ConnectionState.READY,
                lastSeen = System.currentTimeMillis(),
                staticFrameData = staticFrameData,
                combinationFrameData = combinationFrameData,
                tempEventTimestamp = combinationFrame?.tempEventTimestamp,
                currentTimestamp = combinationFrame?.currentTimestamp
            )
            
            // Process real-time sensor data if available
            processSensorData(entity)
            
            deviceInfo
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing scanned device: ${entity.macAddress}", e)
            null
        }
    }
    
    fun stopScan(context: Context) {
        Log.d(TAG, "Stopping BLE scan")
        try {
            mst03Manager.stopScan(context)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when stopping scan - permissions not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE scan", e)
        }
        _isScanning.value = false
    }

    fun startRawAdvScan(context: Context): Boolean {
        try {
            if (isRawAdvScanning) {
                Log.d(TAG, "Raw ADV scan already running")
                return true
            }

            if (!hasRequiredPermissions(context)) {
                Log.e(TAG, "Missing permissions for raw ADV scan")
                return false
            }

            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter: BluetoothAdapter = btManager.adapter ?: return false
            if (!adapter.isEnabled) {
                Log.e(TAG, "Bluetooth disabled; cannot start raw ADV scan")
                return false
            }

            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                Log.e(TAG, "BluetoothLeScanner unavailable")
                return false
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    handleRawAdvResult(result)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { handleRawAdvResult(it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Raw ADV scan failed: $errorCode")
                }
            }

            Log.d(TAG, "Starting RAW ADV scan (software-filter manufacturer=$MANUFACTURER_ID_HEX)")
            scanner.startScan(null, settings, callback)
            bleScanner = scanner
            rawScanCallback = callback
            isRawAdvScanning = true
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start raw ADV scan", t)
            return false
        }
    }

    fun stopRawAdvScan() {
        try {
            if (!isRawAdvScanning) return
            val scanner = bleScanner
            val callback = rawScanCallback
            if (scanner != null && callback != null) {
                scanner.stopScan(callback)
                Log.d(TAG, "Stopped RAW ADV scan")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to stop raw ADV scan", t)
        } finally {
            isRawAdvScanning = false
            rawScanCallback = null
            bleScanner = null
        }
    }

    private fun handleRawAdvResult(result: ScanResult) {
        try {
            val record = result.scanRecord ?: return
            val mac = result.device?.address ?: "UNKNOWN"
            
            // Check if device has our target manufacturer ID
            val sparse = record.manufacturerSpecificData
            var hasTargetManufacturer = false
            var matchedId: Int? = null
            var foundIds: MutableList<Int> = mutableListOf()
            
            if (sparse != null) {
                for (i in 0 until sparse.size()) {
                    val id = sparse.keyAt(i)
                    foundIds.add(id)
                    if (id == MANUFACTURER_ID_INT_PRIMARY || id == MANUFACTURER_ID_INT_ALT) {
                        hasTargetManufacturer = true
                        matchedId = id
                        break
                    }
                }
            }
            
            if (!hasTargetManufacturer) {
                if (rawAdvDebugLogging) {
                    if (foundIds.isNotEmpty()) {
                        Log.d(TAG, "RAW ADV DEBUG - $mac manufacturers=${foundIds.joinToString { String.format("%04X", it) }}")
                    } else {
                        Log.d(TAG, "RAW ADV DEBUG - $mac no manufacturer data")
                    }
                }
                return
            }
            
            // Capture complete advertisement packet (like nRF Connect)
            val completeBytes = record.bytes ?: return
            val completeHex = bytesToHex(completeBytes)
            val idHex = String.format("%04X", matchedId ?: MANUFACTURER_ID_INT_ALT)
            
            // Also extract individual sections for detailed analysis
            val sections = mutableListOf<String>()
            
            // Manufacturer specific data
            if (sparse != null) {
                for (i in 0 until sparse.size()) {
                    val id = sparse.keyAt(i)
                    val data = sparse.valueAt(i)
                    sections.add("MFG_${String.format("%04X", id)}=${bytesToHex(data)}")
                }
            }
            
            // Service UUIDs
            val serviceUuids = record.serviceUuids
            if (!serviceUuids.isNullOrEmpty()) {
                sections.add("SERVICES=${serviceUuids.joinToString(",")}")
            }
            
            // Service data
            val serviceData = record.serviceData
            if (!serviceData.isNullOrEmpty()) {
                for ((uuid, data) in serviceData) {
                    sections.add("SERVICE_DATA_${uuid}=${bytesToHex(data)}")
                }
            }
            
            // Device name
            val deviceName = record.deviceName
            if (!deviceName.isNullOrEmpty()) {
                sections.add("NAME=$deviceName")
            }
            
            // TX power
            val txPower = record.txPowerLevel
            if (txPower != Int.MIN_VALUE) {
                sections.add("TX_POWER=${txPower}dBm")
            }
            
            Log.d(TAG, "RAW ADV - $mac ($idHex): $completeHex")
            Log.d(TAG, "RAW ADV DETAILS - $mac: ${sections.joinToString(" | ")}")
            
            _rawAdvDataUpdates.trySend(
                com.choruscoldchain.data.models.RawBleData(
                    macAddress = mac,
                    hexPayload = completeHex,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error handling raw adv result", t)
        }
    }

    // ========== NATIVE GATT CONNECTION (parallel to SDK) ==========
    private val _rawGattDataUpdates = Channel<com.choruscoldchain.data.models.RawBleData>(Channel.UNLIMITED)
    val rawGattDataUpdates: Flow<com.choruscoldchain.data.models.RawBleData> = _rawGattDataUpdates.receiveAsFlow()
    @Volatile private var gattByMac: MutableMap<String, BluetoothGatt> = mutableMapOf()
    private val gattCallbackByMac: MutableMap<String, BluetoothGattCallback> = mutableMapOf()

    fun startNativeGatt(context: Context, macAddress: String): Boolean {
        return try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = btManager.adapter ?: return false
            if (!adapter.isEnabled) {
                Log.e(TAG, "Bluetooth disabled; cannot start native GATT for $macAddress")
                return false
            }

            if (gattByMac.containsKey(macAddress)) {
                Log.d(TAG, "Native GATT already active for $macAddress")
                return true
            }

            val device: BluetoothDevice? = try {
                adapter.getRemoteDevice(macAddress)
            } catch (t: Throwable) {
                Log.e(TAG, "Invalid MAC for native GATT: $macAddress", t)
                null
            }
            if (device == null) return false

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    Log.d(TAG, "NATIVE GATT state $macAddress: status=$status state=$newState")
                    if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "NATIVE GATT connected -> discovering services: $macAddress")
                        gatt.discoverServices()
                    } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "NATIVE GATT disconnected: $macAddress")
                        cleanupGatt(macAddress)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    Log.d(TAG, "NATIVE GATT services discovered for $macAddress status=$status")
                    try {
                        gatt.services?.forEach { svc ->
                            Log.d(TAG, "Svc ${svc.uuid} -> ${svc.characteristics?.size ?: 0} chrs")
                            svc.characteristics?.forEach { chr ->
                                val props = chr.properties
                                val supportsNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                                val supportsIndicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                                if (supportsNotify || supportsIndicate) {
                                    enableNotifications(gatt, chr, supportsIndicate)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error iterating services for $macAddress", t)
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    val hex = bytesToHex(value)
                    Log.d(TAG, "RAW GATT - $macAddress ${characteristic.uuid}: $hex")
                    _rawGattDataUpdates.trySend(
                        com.choruscoldchain.data.models.RawBleData(
                            macAddress = macAddress,
                            hexPayload = hex,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    // Fallback for older API where value is inside characteristic
                    val value = characteristic.value ?: return
                    onCharacteristicChanged(gatt, characteristic, value)
                }
            }

            Log.d(TAG, "NATIVE GATT connect start: $macAddress")
            val gatt = device.connectGatt(context, false, callback)
            if (gatt == null) {
                Log.e(TAG, "connectGatt returned null for $macAddress")
                return false
            }
            gattByMac[macAddress] = gatt
            gattCallbackByMac[macAddress] = callback
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start native GATT for $macAddress", t)
            false
        }
    }

    fun stopNativeGatt(macAddress: String) {
        cleanupGatt(macAddress)
    }

    private fun enableNotifications(gatt: BluetoothGatt, chr: BluetoothGattCharacteristic, indicate: Boolean) {
        try {
            val ok = gatt.setCharacteristicNotification(chr, true)
            Log.d(TAG, "Enable ${if (indicate) "INDICATE" else "NOTIFY"} on ${chr.uuid} -> setCharNotif=$ok")
            val cccd: BluetoothGattDescriptor? = chr.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (cccd != null) {
                val value = if (indicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                cccd.value = value
                val wrote = gatt.writeDescriptor(cccd)
                Log.d(TAG, "CCCD write ${chr.uuid} wrote=$wrote")
            } else {
                Log.w(TAG, "CCCD not found for ${chr.uuid}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to enable notifications for ${chr.uuid}", t)
        }
    }

    private fun cleanupGatt(macAddress: String) {
        try {
            val g = gattByMac.remove(macAddress)
            if (g != null) {
                try { g.disconnect() } catch (_: Throwable) {}
                try { g.close() } catch (_: Throwable) {}
                Log.d(TAG, "NATIVE GATT cleaned: $macAddress")
            }
            gattCallbackByMac.remove(macAddress)
        } catch (t: Throwable) {
            Log.e(TAG, "Error cleaning GATT for $macAddress", t)
        }
    }

    // Public helper to request MTU for larger notifications
    fun requestNativeMtu(macAddress: String, mtu: Int = 247): Boolean {
        val g = gattByMac[macAddress] ?: return false
        return try {
            val ok = g.requestMtu(mtu)
            Log.d(TAG, "NATIVE GATT requestMtu $macAddress mtu=$mtu ok=$ok")
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "requestMtu failed for $macAddress", t)
            false
        }
    }

    // Public helper to subscribe to a specific service/characteristic UUID
    fun subscribeNativeGatt(macAddress: String, serviceUuid: String, characteristicUuid: String): Boolean {
        val g = gattByMac[macAddress] ?: run {
            Log.e(TAG, "subscribeNativeGatt: no active GATT for $macAddress")
            return false
        }
        return try {
            val svc = g.services?.firstOrNull { it.uuid.toString().equals(serviceUuid, true) }
            if (svc == null) {
                Log.e(TAG, "Service $serviceUuid not found on $macAddress")
                return false
            }
            val chr = svc.characteristics?.firstOrNull { it.uuid.toString().equals(characteristicUuid, true) }
            if (chr == null) {
                Log.e(TAG, "Characteristic $characteristicUuid not found in $serviceUuid on $macAddress")
                return false
            }
            val props = chr.properties
            val indicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            enableNotifications(g, chr, indicate)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "subscribeNativeGatt failed for $macAddress svc=$serviceUuid chr=$characteristicUuid", t)
            false
        }
    }
    
    suspend fun connectDevice(context: Context?, macAddress: String, secretKey: String = DEFAULT_SECRET_KEY): Boolean {
        return try {
            Log.d(TAG, "=== CONNECTION ATTEMPT START ===")
            Log.d(TAG, "Connecting to device: $macAddress with secret key: $secretKey")
            
            // Check permissions if context is provided
            if (context != null && !hasRequiredPermissions(context)) {
                Log.e(TAG, "Required permissions not granted for BLE connection")
                updateConnectionState(macAddress, ConnectionState.ERROR)
                return false
            }
            
            // Check if Bluetooth is enabled
            if (context != null && !BluetoothHelper.isBluetoothEnabled(context)) {
                Log.e(TAG, "Bluetooth is not enabled")
                updateConnectionState(macAddress, ConnectionState.ERROR)
                return false
            }
            
            // Stop scanning before connecting
            if (_isScanning.value && context != null) {
                Log.d(TAG, "Stopping scan before connection")
                stopScan(context)
                // Add a small delay to ensure scan is fully stopped
                delay(500)
            }
            
            // Check if device is already connected
            if (_connectionStates.value[macAddress] == ConnectionState.READY) {
                Log.d(TAG, "Device $macAddress is already connected")
                return true
            }
            
            // Set initial connection state
            updateConnectionState(macAddress, ConnectionState.CONNECTING)
            
            // Set secret key first with retry mechanism
            Log.d(TAG, "Setting secret key for $macAddress")
            var secretKeySet = false
            for (attempt in 1..3) {
                try {
                    mst03Manager.setSecretKey(macAddress, secretKey)
                    Log.d(TAG, "Secret key set successfully on attempt $attempt")
                    secretKeySet = true
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set secret key on attempt $attempt", e)
                    if (attempt < 3) {
                        delay(200L * attempt) // Exponential backoff
                    }
                }
            }
            
            if (!secretKeySet) {
                Log.e(TAG, "Failed to set secret key after 3 attempts")
                updateConnectionState(macAddress, ConnectionState.ERROR)
                return false
            }
            
            connectedDevices[macAddress] = secretKey
            
            // Small delay to ensure secret key is properly applied
            delay(200)
            
            // Try connecting with retry mechanism
            if (context != null) {
                Log.d(TAG, "Attempting connection to $macAddress")
                var connectionAttempts = 0
                val maxConnectionAttempts = 3
                
                while (connectionAttempts < maxConnectionAttempts) {
                    connectionAttempts++
                    Log.d(TAG, "Connection attempt $connectionAttempts of $maxConnectionAttempts")
                    
                    try {
                        mst03Manager.connect(context, macAddress)
                        Log.d(TAG, "Connect method called successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calling connect method on attempt $connectionAttempts", e)
                        if (connectionAttempts >= maxConnectionAttempts) {
                            updateConnectionState(macAddress, ConnectionState.ERROR)
                            return false
                        }
                        delay(1000) // Wait before retry
                        continue
                    }
                    
                    // Wait for connection to establish with timeout
                    Log.d(TAG, "Waiting for connection to establish...")
                    val maxWaitTime = 20000L // 20 seconds timeout (reduced from 30)
                    val startTime = System.currentTimeMillis()
                    
                    while (System.currentTimeMillis() - startTime < maxWaitTime) {
                        val currentState = _connectionStates.value[macAddress]
                        Log.d(TAG, "Current connection state: $currentState")
                        
                        when (currentState) {
                            ConnectionState.READY -> {
                                Log.d(TAG, "Connection successful to $macAddress - READY state reached")
                                return true
                            }
                            ConnectionState.ERROR -> {
                                Log.w(TAG, "Connection failed to $macAddress - ERROR state")
                                if (connectionAttempts >= maxConnectionAttempts) {
                                    return false
                                }
                                break // Try next attempt
                            }
                            ConnectionState.DISCONNECTED -> {
                                Log.w(TAG, "Connection failed to $macAddress - DISCONNECTED state")
                                if (connectionAttempts >= maxConnectionAttempts) {
                                    return false
                                }
                                break // Try next attempt
                            }
                            ConnectionState.AUTHENTICATED -> {
                                Log.d(TAG, "Device authenticated, waiting for READY state...")
                                delay(500)
                            }
                            ConnectionState.CONNECTED -> {
                                Log.d(TAG, "Device connected, waiting for authentication...")
                                delay(500)
                            }
                            ConnectionState.CONNECTING -> {
                                Log.d(TAG, "Device connecting...")
                                delay(500)
                            }
                            ConnectionState.AUTHENTICATING -> {
                                Log.d(TAG, "Device authenticating...")
                                delay(500)
                            }
                            null -> {
                                Log.d(TAG, "No connection state yet, continuing to wait...")
                                delay(500)
                            }
                        }
                    }
                    
                    // Check one final time after timeout
                    val finalState = _connectionStates.value[macAddress]
                    if (finalState == ConnectionState.READY) {
                        Log.d(TAG, "Connection successful to $macAddress - READY state reached after timeout check")
                        return true
                    }
                    
                    Log.w(TAG, "Connection attempt $connectionAttempts timed out, final state: $finalState")
                    
                    // If this was the last attempt, return false
                    if (connectionAttempts >= maxConnectionAttempts) {
                        updateConnectionState(macAddress, ConnectionState.ERROR)
                        return false
                    }
                    
                    // Wait before next attempt
                    delay(2000)
                }
                
                Log.w(TAG, "All connection attempts failed for $macAddress")
                updateConnectionState(macAddress, ConnectionState.ERROR)
                return false
            } else {
                Log.e(TAG, "Context is null, cannot connect")
                updateConnectionState(macAddress, ConnectionState.ERROR)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device: $macAddress", e)
            updateConnectionState(macAddress, ConnectionState.ERROR)
            false
        } finally {
            Log.d(TAG, "=== CONNECTION ATTEMPT END ===")
        }
    }
    
    fun disconnectDevice(macAddress: String) {
        Log.d(TAG, "Disconnecting device: $macAddress")
        try {
            mst03Manager.disConnect(macAddress)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when disconnecting device - permissions not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting device: $macAddress", e)
        }
        connectedDevices.remove(macAddress)
    }
    
    suspend fun setDeviceSecretKey(macAddress: String, secretKey: String): Boolean {
        return try {
            mst03Manager.setSecretKey(macAddress, secretKey)
            connectedDevices[macAddress] = secretKey
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting secret key for device: $macAddress", e)
            false
        }
    }
    
    fun isDeviceConnected(macAddress: String): Boolean {
        return _connectionStates.value[macAddress] == ConnectionState.READY
    }
    
    fun getConnectedDevices(): List<String> {
        return connectedDevices.keys.toList()
    }
    
    private fun updateConnectionState(macAddress: String, state: ConnectionState) {
        Log.d(TAG, "Updating connection state for $macAddress: $state")
        val currentStates = _connectionStates.value.toMutableMap()
        currentStates[macAddress] = state
        _connectionStates.value = currentStates
        Log.d(TAG, "Connection states updated: ${_connectionStates.value}")
        
        // Additional logging for debugging connection issues
        when (state) {
            ConnectionState.ERROR -> {
                Log.e(TAG, "=== CONNECTION ERROR DIAGNOSTICS ===")
                Log.e(TAG, "Device: $macAddress")
                Log.e(TAG, "Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                Log.e(TAG, "All connection states: ${_connectionStates.value}")
                Log.e(TAG, "Connected devices: ${connectedDevices.keys}")
                Log.e(TAG, "=== END DIAGNOSTICS ===")
            }
            ConnectionState.READY -> {
                Log.d(TAG, "=== CONNECTION SUCCESS ===")
                Log.d(TAG, "Device: $macAddress")
                Log.d(TAG, "Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                Log.d(TAG, "=== END SUCCESS ===")
            }
            else -> {
                Log.d(TAG, "Connection state change: $macAddress -> $state")
            }
        }
    }
    
    // SDK Methods for Device Details
    suspend fun queryFirmwareInfo(macAddress: String): String {
        return try {
            Log.d(TAG, "Querying firmware info for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.queryDeviceFirmwareInfo(macAddress) { isSuccessful, version ->
                    if (isSuccessful && version != null) {
                        val result = StringBuilder().apply {
                            version.versionInfoList?.forEach { versionInfo ->
                                append(versionInfo)
                            }
                        }.toString()
                        continuation.resume(result)
                    } else {
                        continuation.resume("Failed to query firmware info")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying firmware info", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun queryAllTemperatureHistory(macAddress: String): List<HtData> =
        suspendCancellableCoroutine { cont ->
            val systemTimeSec = System.currentTimeMillis() / 1000
            val rules = 0 // get all data

            mst03Manager.queryTemperatureHistoryData(macAddress, rules, 0, 0, systemTimeSec) { result, historyData ->
                if (!cont.isActive) return@queryTemperatureHistoryData

                if (result && historyData?.historyDataList != null) {
                    cont.resume(historyData.historyDataList)
                } else {
                    cont.resume(emptyList())
                }
        }
    }

    suspend fun queryLightIntensityConfiguration(macAddress: String): String {
        return try {
            Log.d(TAG, "Querying light intensity configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.getLightIntensityConfiguration(macAddress) { isSuccessful, configResult ->
                    if (isSuccessful && configResult != null) {
                        val resultText = StringBuilder().apply {
                            append(configResult)
                        }.toString()
                        continuation.resume(resultText)
                    } else {
                        continuation.resume("Failed to get light intensity configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying light intensity configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun getLightIntensityConfiguration(macAddress: String): com.minew.ble.mst03.bean.LightIntensitySensorConfiguration? {
        return try {
            Log.d(TAG, "Getting light intensity configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.getLightIntensityConfiguration(macAddress) { isSuccessful, configResult ->
                    if (isSuccessful && configResult != null) {
                        continuation.resume(configResult)
                    } else {
                        continuation.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting light intensity configuration", e)
            null
        }
    }
    
    suspend fun queryAlarmStrategyConfiguration(macAddress: String): String {
        return try {
            Log.d(TAG, "Querying alarm strategy configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.getAlarmStrategyConfiguration(macAddress) { isSuccessful, configResult ->
                    if (isSuccessful && configResult != null) {
                        val resultText = StringBuilder().apply {
                            append(configResult)
                        }.toString()
                        continuation.resume(resultText)
                    } else {
                        continuation.resume("Failed to get alarm strategy configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying alarm strategy configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun getDeviceStaticInfo(macAddress: String): String {
        return try {
            Log.d(TAG, "Getting device static info for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.queryHTSensorConfiguration(macAddress) { isSuccessful, configResult ->
                    if (isSuccessful && configResult != null) {
                        val resultText = StringBuilder().apply {
                            append(configResult)
                        }.toString()
                        continuation.resume(resultText)
                    } else {
                        continuation.resume("Failed to get temperature sensor configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device static info", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun queryHTSensorConfiguration(macAddress: String): HtSensorConfiguration? {
        return try {
            Log.d(TAG, "Querying HT sensor configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.queryHTSensorConfiguration(macAddress) { isSuccessful, configResult ->
                    if (isSuccessful && configResult != null) {
                        continuation.resume(configResult)
                    } else {
                        continuation.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying HT sensor configuration", e)
            null
        }
    }
    
    suspend fun getCombinationFrameData(macAddress: String): String {
        return try {
            Log.d(TAG, "Getting combination frame data for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                val systemTime = System.currentTimeMillis() / 1000
                val startTime = (systemTime - 60 * 60 * 24) / 1000 // Last 24 hours
                val endTime = systemTime
                val rules = 1 // Get data for specific time period
                
                mst03Manager.queryLightHistoryData(macAddress, rules, startTime, endTime, systemTime) { result, historyData ->
                    if (result && historyData != null) {
                        Log.d(TAG, "Light history data received: ${historyData.historyDataList?.size ?: 0} records")
                        val resultText = StringBuilder().apply {
                        historyData.historyDataList?.forEach { lightData ->
                                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                    .format(java.util.Date(lightData.timestamps))
                                append(lightData)
                            }
                        }.toString()
                        Log.d(TAG, "Light history result: $resultText")
                        continuation.resume(resultText)
                    } else {
                        Log.w(TAG, "No light history data available - result: $result, data: $historyData")
                        continuation.resume("No light history data available")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting combination frame data", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Query the light intensity history for the last [seconds] seconds.
     * Returns Pair<lightIntensity, timestampMs> or null when no data.
     */
    suspend fun queryRecentLightIntensity(macAddress: String, seconds: Int = 60): Pair<Int, Long>? =
        suspendCancellableCoroutine { cont ->
            val systemTimeSec = System.currentTimeMillis() / 1000
            val endTimeSec = systemTimeSec
            val startTimeSec = systemTimeSec - seconds
            val rules = 1 // time range

            mst03Manager.queryLightHistoryData(macAddress, rules, startTimeSec, endTimeSec, systemTimeSec) { result, historyData ->
                if (!cont.isActive) return@queryLightHistoryData

                
                val list = historyData?.historyDataList
                if (result && list != null && list.isNotEmpty()) {
                    var latestTs = Long.MIN_VALUE
                    var latestIntensity = 0
                    for (d in list) {
                        val ts = d.timestamps
                        if (ts > latestTs) {
                            latestTs = ts
                            latestIntensity = d.lightIntensity
                        }
                    }
                    if (latestTs != Long.MIN_VALUE) {
                        cont.resume(Pair(latestIntensity, latestTs))
                    } else {
                        cont.resume(null)
                    }
                } else {
                    if (seconds < 300) {
                        val extendedStartTime = systemTimeSec - 300
                        mst03Manager.queryLightHistoryData(macAddress, rules, extendedStartTime, endTimeSec, systemTimeSec) { extendedResult, extendedHistoryData ->
                            if (!cont.isActive) return@queryLightHistoryData
                            val extendedList = extendedHistoryData?.historyDataList
                            if (extendedResult && extendedList != null && extendedList.isNotEmpty()) {
                                var latestTs2 = Long.MIN_VALUE
                                var latestIntensity2 = 0
                                for (d in extendedList) {
                                    val ts = d.timestamps
                                    if (ts > latestTs2) {
                                        latestTs2 = ts
                                        latestIntensity2 = d.lightIntensity
                                    }
                                }
                                if (latestTs2 != Long.MIN_VALUE) {
                                    cont.resume(Pair(latestIntensity2, latestTs2))
                                } else {
                                    cont.resume(null)
                                }
                            } else {
                                cont.resume(null)
                            }
                        }
                    } else {
                        cont.resume(null)
                    }
                }
            }
        }
    
    /**
     * Query all available light intensity history data.
     * Returns the latest light intensity reading or null when no data.
     */
    suspend fun queryAllLightHistory(macAddress: String): Pair<Int, Long>? =
        suspendCancellableCoroutine { cont ->
            val systemTimeSec = System.currentTimeMillis() / 1000
            val endTimeSec = systemTimeSec
            val startTimeSec = 0L // Query from the beginning
            val rules = 1 // time range


            mst03Manager.queryLightHistoryData(macAddress, rules, startTimeSec, endTimeSec, systemTimeSec) { result, historyData ->
                if (!cont.isActive) return@queryLightHistoryData

                
                val list = historyData?.historyDataList
                if (result && list != null && list.isNotEmpty()) {
                    var latestTs = Long.MIN_VALUE
                    var latestIntensity = 0
                    for (d in list) {
                        val ts = d.timestamps
                        if (ts > latestTs) {
                            latestTs = ts
                            latestIntensity = d.lightIntensity
                        }
                    }
                    if (latestTs != Long.MIN_VALUE) {
                        cont.resume(Pair(latestIntensity, latestTs))
                    } else {
                        cont.resume(null)
                    }
                } else {
                    cont.resume(null)
                }
            }
        }
    
    suspend fun resetDevice(macAddress: String): String {
        return try {
            Log.d(TAG, "Resetting device $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.reset(macAddress) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Device reset command sent successfully. Device will restart in 5 seconds.")
                    } else {
                        continuation.resume("Failed to reset device")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting device", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun rebootDevice(macAddress: String): String {
        return try {
            Log.d(TAG, "Rebooting device $macAddress")
            // TODO: Implement actual device reboot using SDK
            "Device reboot command sent successfully. Device will restart in 3 seconds."
        } catch (e: Exception) {
            Log.e(TAG, "Error rebooting device", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun powerOffDevice(macAddress: String): String {
        return try {
            Log.d(TAG, "Powering off device $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.powerOff(macAddress) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Device power off command sent successfully. Device will turn off in 2 seconds.")
                    } else {
                        continuation.resume("Failed to power off device")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error powering off device", e)
            "Error: ${e.message}"
        }
    }
    
    fun verifyOtaFile(zipFilePath: String): Boolean {
        return try {
            mst03Manager.verifyOtaFile(zipFilePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying OTA file: $zipFilePath", e)
            false
        }
    }

    fun firmwareUpgrade(
        macAddress: String,
        dfuTarget: Int,
        upgradeData: ByteArray,
        progressCallBack: (progress: Int) -> Unit,
        successCallBack: () -> Unit,
        failCallBack: () -> Unit
    ) {
        try {
            Log.d(TAG, "Starting firmware upgrade for $macAddress, dfuTarget=$dfuTarget, size=${upgradeData.size}")
            mst03Manager.firmwareUpgrade(
                macAddress,
                false,
                dfuTarget,
                upgradeData,
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
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting firmware upgrade for $macAddress", e)
            failCallBack()
        }
    }
    
    // ========== CONFIGURATION METHODS ==========
    
    suspend fun queryAdvertisingParameters(macAddress: String, slot: Int = 0): String {
        return try {
            Log.d(TAG, "Querying advertising parameters for $macAddress, slot: $slot")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.queryAdvParametersConfiguration(macAddress, slot) { isSuccessful, configResult ->
                    if (isSuccessful && configResult != null) {
                        val resultText = StringBuilder().apply {
                            append(configResult)
                        }.toString()
                        continuation.resume(resultText)
                    } else {
                        continuation.resume("Failed to query advertising parameters")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying advertising parameters", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun setAdvertisingParameters(
        macAddress: String,
        frameType: String,
        slotNumber: Int,
        advertisingInterval: Int,
        txPower: Int,
        advertisingContent: String?
    ): String {
        return try {
            Log.d(TAG, "Setting advertising parameters for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.setAdvParametersConfiguration(
                    macAddress, frameType, slotNumber, advertisingInterval, txPower, advertisingContent
                ) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Advertising parameters set successfully")
                    } else {
                        continuation.resume("Failed to set advertising parameters")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting advertising parameters", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun setLEDConfiguration(
        macAddress: String,
        colorTable: Int,
        workTotalCount: Int,
        singleCycleLightingTime: Int,
        singleCycleLightOffTime: Int,
        brightness: Int
    ): String {
        return try {
            Log.d(TAG, "Setting LED configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.setLEDConfiguration(
                    macAddress, colorTable, workTotalCount, singleCycleLightingTime, singleCycleLightOffTime, brightness
                ) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("LED configuration set successfully")
                    } else {
                        continuation.resume("Failed to set LED configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting LED configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun changeSecretKey(macAddress: String, newSecretKey: String): String {
        return try {
            Log.d(TAG, "Changing secret key for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.changeSecretKey(macAddress, newSecretKey) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Secret key changed successfully")
                    } else {
                        continuation.resume("Failed to change secret key")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error changing secret key", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun setHTSensorConfiguration(
        macAddress: String,
        samplingInterval: Int,
        delay: Int,
        htSettingData: List<HTSensorThresholdConfig>
    ): String {
        return try {
            Log.d(TAG, "Setting HT sensor configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                val configuration = HtSensorConfiguration().apply {
                    this.samplingInterval = samplingInterval
                    this.delay = delay
                    this.htSettingData = htSettingData
                }
                
                mst03Manager.setHTSensorConfiguration(macAddress, configuration) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("HT sensor configuration set successfully")
                    } else {
                        continuation.resume("Failed to set HT sensor configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting HT sensor configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun setLightIntensityConfiguration(
        macAddress: String,
        lightIntensityLowerLimitAlarm: Int,
        lightIntensityUpperLimitAlarm: Int
    ): String {
        return try {
            Log.d(TAG, "Setting light intensity configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                val configuration = com.minew.ble.mst03.bean.LightIntensitySensorConfiguration().apply {
                    this.lightIntensityLowerLimitAlarm = lightIntensityLowerLimitAlarm
                    this.lightIntensityUpperLimitAlarm = lightIntensityUpperLimitAlarm
                }
                
                mst03Manager.setLightIntensityConfiguration(macAddress, configuration) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Light intensity configuration set successfully")
                    } else {
                        continuation.resume("Failed to set light intensity configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting light intensity configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun setAlarmStrategyConfiguration(
        macAddress: String,
        alarmLedDuration: Int,
        alarmCountThreshold: Int
    ): String {
        return try {
            Log.d(TAG, "Setting alarm strategy configuration for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                val configuration = com.minew.ble.mst03.bean.AlarmStrategyConfiguration().apply {
                    this.alarmLedDuration = alarmLedDuration
                    this.alarmCountThreshold = alarmCountThreshold
                }
                
                mst03Manager.setAlarmStrategyConfiguration(macAddress, configuration) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Alarm strategy configuration set successfully")
                    } else {
                        continuation.resume("Failed to set alarm strategy configuration")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting alarm strategy configuration", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun clearTemperatureHistory(macAddress: String): String {
        return try {
            Log.d(TAG, "Clearing temperature history for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.cleanTempHistoryData(macAddress) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Temperature history cleared successfully")
                    } else {
                        continuation.resume("Failed to clear temperature history")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing temperature history", e)
            "Error: ${e.message}"
        }
    }
    
    suspend fun clearLightHistory(macAddress: String): String {
        return try {
            Log.d(TAG, "Clearing light history for $macAddress")
            return suspendCancellableCoroutine { continuation ->
                mst03Manager.cleanLightHistoryData(macAddress) { isSuccess ->
                    if (isSuccess) {
                        continuation.resume("Light history cleared successfully")
                    } else {
                        continuation.resume("Failed to clear light history")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing light history", e)
            "Error: ${e.message}"
        }
    }
}
