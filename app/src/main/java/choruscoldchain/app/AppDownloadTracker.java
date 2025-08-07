package choruscoldchain.app;

import android.content.Context;
import android.util.Log;

import java.util.Date;

/**
 * Utility class for tracking app download instances and managing installation data.
 * Provides easy access to installation ID and download tracking functionality.
 */
public class AppDownloadTracker {
    private static final String TAG = "AppDownloadTracker";
    private static AppDownloadTracker instance;
    private final InstallationIdManager installationIdManager;
    private final Context context;
    
    private AppDownloadTracker(Context context) {
        this.context = context.getApplicationContext();
        this.installationIdManager = InstallationIdManager.getInstance(context);
    }
    
    public static synchronized AppDownloadTracker getInstance(Context context) {
        if (instance == null) {
            instance = new AppDownloadTracker(context);
        }
        return instance;
    }
    
    /**
     * Gets the unique installation ID for this app download instance.
     * @return The unique installation ID
     */
    public String getInstallationId() {
        return installationIdManager.getInstallationId();
    }
    
    /**
     * Gets the timestamp when this app was first installed.
     * @return The first install timestamp
     */
    public Date getFirstInstallDate() {
        long timestamp = installationIdManager.getFirstInstallTime();
        return timestamp > 0 ? new Date(timestamp) : null;
    }
    
    /**
     * Checks if this is a fresh installation (first time the app is run).
     * @return true if this is a fresh installation, false otherwise
     */
    public boolean isFreshInstallation() {
        return installationIdManager.isFreshInstallation();
    }
    
    /**
     * Gets detailed information about this app download instance.
     * @return Formatted string with installation details
     */
    public String getDownloadInstanceInfo() {
        StringBuilder info = new StringBuilder();
        info.append("App Download Instance Information:\n");
        info.append("Installation ID: ").append(getInstallationId()).append("\n");
        info.append("First Install Date: ").append(getFirstInstallDate() != null ? 
            getFirstInstallDate().toString() : "Unknown").append("\n");
        info.append("Is Fresh Installation: ").append(isFreshInstallation()).append("\n");
        info.append("App Package: ").append(context.getPackageName()).append("\n");
        info.append("App Version: ").append(getAppVersion()).append("\n");
        
        return info.toString();
    }
    
    /**
     * Gets the current app version.
     * @return App version string
     */
    public String getAppVersion() {
        try {
            return context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0)
                .versionName;
        } catch (Exception e) {
            Log.e(TAG, "Error getting app version", e);
            return "Unknown";
        }
    }
    
    /**
     * Logs the current download instance information.
     * Useful for debugging and tracking purposes.
     */
    public void logDownloadInstance() {
        Log.i(TAG, getDownloadInstanceInfo());
    }
    
    /**
     * Creates a download instance report that can be used for analytics or tracking.
     * @return Download instance report as a formatted string
     */
    public String createDownloadInstanceReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== APP DOWNLOAD INSTANCE REPORT ===\n");
        report.append("Timestamp: ").append(new Date().toString()).append("\n");
        report.append("Installation ID: ").append(getInstallationId()).append("\n");
        report.append("First Install: ").append(getFirstInstallDate() != null ? 
            getFirstInstallDate().toString() : "Unknown").append("\n");
        report.append("Fresh Installation: ").append(isFreshInstallation()).append("\n");
        report.append("App Version: ").append(getAppVersion()).append("\n");
        report.append("Package Name: ").append(context.getPackageName()).append("\n");
        report.append("=====================================\n");
        
        return report.toString();
    }
    
    /**
     * Resets the installation ID (for testing purposes).
     * This simulates a fresh app installation.
     */
    public void resetInstallation() {
        installationIdManager.resetInstallationId();
        Log.i(TAG, "Installation reset - new ID: " + getInstallationId());
    }
} 