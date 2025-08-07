# App Installation Tracking System

This document describes the implementation of a unique installation ID system for the CCP Excursion Reader app. The system generates a unique ID for each app download instance, ensuring that uninstall and reinstall operations generate new unique IDs.

## Overview

The installation tracking system consists of three main components:

1. **InstallationIdManager** - Core class that generates and manages unique installation IDs
2. **AppDownloadTracker** - Utility class providing easy access to installation tracking functionality
3. **App** - Updated application class that initializes the tracking system

## Key Features

- **Unique Installation IDs**: Each app installation gets a unique 16-character hexadecimal ID
- **Persistent Storage**: Installation ID persists until app uninstallation
- **Fresh Installation Detection**: Ability to detect if this is a fresh app installation
- **Installation Timestamp**: Tracks when the app was first installed
- **Secure Generation**: Uses SHA-256 hashing for ID generation

## How It Works

### Installation ID Generation

The system generates unique installation IDs by combining:
- Device Android ID
- App package name
- Current timestamp
- Random UUID
- SHA-256 hashing for consistency

### Storage Mechanism

Installation IDs are stored in SharedPreferences, which are automatically cleared when the app is uninstalled. This ensures that reinstalling the app will generate a new unique ID.

## Usage Examples

### Basic Usage

```java
// Get the installation ID anywhere in your app
AppDownloadTracker tracker = AppDownloadTracker.getInstance(context);
String installationId = tracker.getInstallationId();

// Check if this is a fresh installation
boolean isFresh = tracker.isFreshInstallation();

// Get the first install date
Date firstInstallDate = tracker.getFirstInstallDate();
```

### Advanced Usage

```java
// Get detailed installation information
String info = tracker.getDownloadInstanceInfo();

// Create a detailed report for analytics
String report = tracker.createDownloadInstanceReport();

// Log installation details
tracker.logDownloadInstance();
```

### Direct Access via App Class

```java
// Access through the App class
App app = (App) getApplication();
InstallationIdManager manager = app.getInstallationIdManager();
String installationId = manager.getInstallationId();
```

## Implementation Details

### InstallationIdManager Class

- **Singleton Pattern**: Ensures single instance across the app
- **Automatic Initialization**: Generates ID on first access
- **Persistent Storage**: Uses SharedPreferences for storage
- **Secure Generation**: SHA-256 hashing with fallback to UUID

### AppDownloadTracker Class

- **High-level Interface**: Provides easy-to-use methods
- **Comprehensive Information**: Includes app version, package name, etc.
- **Reporting Capabilities**: Creates detailed installation reports
- **Logging Support**: Built-in logging for debugging

### Integration Points

1. **App.java**: Initializes the tracking system on app startup
2. **MainActivity.kt**: Demonstrates usage and logs installation info
3. **InstallationInfoActivity.java**: Example activity showing installation details

## Testing

### Fresh Installation Testing

To test fresh installation behavior:

```java
// Reset installation ID (for testing only)
AppDownloadTracker tracker = AppDownloadTracker.getInstance(context);
tracker.resetInstallation();
```

### Verification

Check the logs for installation information:
```
I/App: App started with Installation ID: A1B2C3D4E5F6G7H8
I/MainActivity: App started with installation ID: A1B2C3D4E5F6G7H8
I/MainActivity: This is a fresh app installation
```

## Security Considerations

- Installation IDs are generated using SHA-256 hashing
- No personally identifiable information is used in ID generation
- IDs are stored locally and not transmitted without explicit consent
- Fallback mechanisms ensure ID generation even if primary method fails

## File Structure

```
app/src/main/java/choruscoldchain/app/
├── InstallationIdManager.java      # Core installation ID management
├── AppDownloadTracker.java         # High-level tracking interface
├── App.java                        # Application class with tracking init
├── MainActivity.kt                 # Updated with tracking demo
└── InstallationInfoActivity.java   # Example activity
```

## Dependencies

The implementation uses only standard Android APIs:
- `android.content.SharedPreferences`
- `android.provider.Settings`
- `java.security.MessageDigest`
- `java.util.UUID`

No additional dependencies are required.

## Future Enhancements

Potential improvements could include:
- Cloud synchronization of installation IDs
- Analytics integration
- Installation event tracking
- Cross-device installation correlation
- Installation metadata storage

## Troubleshooting

### Common Issues

1. **ID Not Generated**: Check if the app has proper permissions
2. **ID Changes Unexpectedly**: Verify app wasn't uninstalled/reinstalled
3. **Logs Not Appearing**: Ensure logging is enabled in your build configuration

### Debug Information

Enable verbose logging to see detailed installation information:
```java
Log.d("InstallationTracker", "Detailed info: " + tracker.getDownloadInstanceInfo());
```

## License

This implementation is part of the CCP Excursion Reader app and follows the same licensing terms as the main project. 