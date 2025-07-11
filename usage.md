# CCP-Excursion-reader Application Usage Guide

## Overview

The CCP-Excursion-reader is an Android application that demonstrates how to interact with Minew MST03 Bluetooth Low Energy (BLE) sensors. This application provides a complete example of scanning, connecting, and managing MST03 devices, including temperature and humidity monitoring capabilities.

## Features

- **BLE Device Scanning**: Scan and discover nearby MST03 devices
- **Device Connection**: Connect to MST03 sensors via Bluetooth
- **Real-time Data Monitoring**: View temperature and humidity data
- **Device Configuration**: Configure sensor parameters and settings
- **Firmware Updates**: Update device firmware
- **Data Export**: Download and export sensor data
- **LED Control**: Configure device LED indicators
- **Device Management**: Reset, power off, and manage device settings

## Prerequisites

### Hardware Requirements
- Android device with Bluetooth 4.0+ (BLE) support
- Minew MST03 sensor device(s)

### Software Requirements
- Android 7.0 (API level 24) or higher
- Android Studio (for development)
- Gradle build system

### Permissions Required
The application requires the following permissions:
- Bluetooth permissions (scan, connect, advertise)
- Location permissions (required for BLE scanning)
- Storage permissions (for data export and firmware updates)

## Installation

### Building from Source

1. **Clone or download the project**
   ```bash
   git clone <repository-url>
   cd CCP-Excursion-reader
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Open the project folder
   - Wait for Gradle sync to complete

3. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install on device**
   ```bash
   ./gradlew installDebug
   ```

5. **Run the App**
   ```bash
   adb shell am start -n com.minew.mst03demo/.ScanDevicesListActivity
   ```

### Direct APK Installation
- Download the APK file
- Enable "Install from unknown sources" in Android settings
- Install the APK on your device

## Usage Instructions

### 1. Launching the Application

The application starts with the device scanning screen (`ScanDevicesListActivity`). This is the main entry point where you can discover nearby MST03 devices.

### 2. Scanning for Devices

1. **Grant Permissions**: The app will request necessary Bluetooth and location permissions on first launch
2. **Enable Bluetooth**: Ensure Bluetooth is enabled on your device
3. **Start Scanning**: The app automatically starts scanning for MST03 devices
4. **Refresh**: Pull down to refresh the device list
5. **View Devices**: Discovered devices appear in a list with their MAC addresses

### 3. Connecting to a Device

1. **Select Device**: Tap on a device from the scan list
2. **Connection Process**: The app will attempt to connect to the selected device
3. **Connection Status**: Monitor the connection status in the UI
4. **Success**: Upon successful connection, you'll be taken to the device management screen

### 4. Device Management Screen

Once connected, you can access various device operations:

#### Device Information
- View device MAC address
- Check connection status
- Monitor real-time sensor data

#### Configuration Options
- **Set Device Name**: Configure a custom device name
- **Change Password**: Update device security password
- **LED Configuration**: Control device LED indicators
- **Sensor Parameters**: Configure temperature/humidity thresholds

#### Data Management
- **Download Data**: Export historical sensor data
- **Clear History**: Erase stored sensor data
- **Query Parameters**: Retrieve current device settings

#### Device Control
- **Reset Device**: Factory reset the sensor
- **Power Off**: Turn off the device
- **Firmware Update**: Update device firmware

### 5. Data Export

1. **Select Export**: Choose the data export option
2. **Grant Storage Permission**: Allow access to device storage
3. **Download Data**: Historical data will be downloaded and saved
4. **File Location**: Exported data is saved to device storage

### 6. Firmware Updates

1. **Select Firmware File**: Choose a .bin firmware file
2. **Upload**: The app will upload and install the firmware
3. **Progress Monitoring**: Monitor update progress
4. **Completion**: Device will restart with new firmware

## Technical Details

### Architecture
- **Main Activity**: `ScanDevicesListActivity` - Device discovery and connection
- **Device Management**: `DeviceConnectedCompleteActivity` - Device operations
- **BLE Manager**: `MST03SensorBleManager` - Core BLE functionality
- **Adapters**: `ScanDevicesListAdapter` - UI components

### Key Dependencies
- `base_ble_library.jar` - Core BLE functionality
- `minew_mst03.jar` - MST03-specific implementations
- `PermissionX` - Permission management
- `DialogX` - UI dialogs
- `BaseRecyclerViewAdapterHelper` - List management

### Supported Features
- BLE device scanning and connection
- Real-time temperature and humidity monitoring
- Device configuration management
- Firmware updates
- Data export and management
- LED control and customization

## Troubleshooting

### Common Issues

1. **No Devices Found**
   - Ensure Bluetooth is enabled
   - Check location permissions
   - Verify MST03 devices are powered on and in range
   - Try refreshing the scan

2. **Connection Failures**
   - Ensure device is not connected to another app
   - Check device battery level
   - Verify device is in pairing mode
   - Restart the application

3. **Permission Errors**
   - Grant all requested permissions
   - Check Android settings for app permissions
   - Restart the app after granting permissions

4. **Data Export Issues**
   - Ensure storage permissions are granted
   - Check available storage space
   - Verify file system access

### Debug Information
- Check Android logs for detailed error messages
- Use Android Studio's debugger for step-by-step debugging
- Monitor BLE connection states in the application logs

## Development Notes

### Building for Different Architectures
The app supports multiple CPU architectures:
- ARM64-v8a
- ARMv7-a
- x86
- x86_64

### Customization
- Modify UI layouts in `res/layout/`
- Update strings in `res/values/strings.xml`
- Customize themes in `res/values/themes.xml`

### Integration
To integrate MST03 functionality into your own app:
1. Include the required JAR files
2. Add necessary permissions to AndroidManifest.xml
3. Use `MST03SensorBleManager` for BLE operations
4. Implement appropriate listeners for data handling

## Support

For technical support or questions about the CCP-Excursion-reader application:
- Check the Minew documentation
- Review the source code comments
- Contact Minew support for hardware-specific issues

## Version Information

- **App Version**: 1.0
- **App Name**: CCP-Excursion-reader
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 33 (Android 13)
- **Compile SDK**: 33

---

*This application is provided as-is for educational and development purposes. Always refer to the official Minew documentation for the most up-to-date information about MST03 devices and SDK usage.* 