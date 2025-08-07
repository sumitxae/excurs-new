package choruscoldchain.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Manages unique installation IDs for the app.
 * Each app installation gets a unique ID that persists until the app is uninstalled.
 * Uninstalling and reinstalling will generate a new unique ID.
 */
public class InstallationIdManager {
    private static final String TAG = "InstallationIdManager";
    private static final String PREF_NAME = "installation_prefs";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEY_FIRST_INSTALL_TIME = "first_install_time";
    
    private static InstallationIdManager instance;
    private final Context context;
    private String installationId;
    private long firstInstallTime;
    
    private InstallationIdManager(Context context) {
        this.context = context.getApplicationContext();
        loadOrGenerateInstallationId();
    }
    
    public static synchronized InstallationIdManager getInstance(Context context) {
        if (instance == null) {
            instance = new InstallationIdManager(context);
        }
        return instance;
    }
    
    /**
     * Gets the unique installation ID for this app instance.
     * @return The unique installation ID
     */
    public String getInstallationId() {
        return installationId;
    }
    
    /**
     * Gets the timestamp when this installation was first created.
     * @return The first install timestamp in milliseconds
     */
    public long getFirstInstallTime() {
        return firstInstallTime;
    }
    
    /**
     * Checks if this is a fresh installation (first time the app is run).
     * @return true if this is a fresh installation, false otherwise
     */
    public boolean isFreshInstallation() {
        return firstInstallTime == 0;
    }
    
    /**
     * Gets installation information as a formatted string.
     * @return Installation info string
     */
    public String getInstallationInfo() {
        return String.format("Installation ID: %s\nFirst Install Time: %s", 
            installationId, 
            firstInstallTime > 0 ? new java.util.Date(firstInstallTime).toString() : "Unknown");
    }
    
    /**
     * Loads existing installation ID or generates a new one.
     */
    private void loadOrGenerateInstallationId() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // Try to load existing installation ID
        installationId = prefs.getString(KEY_INSTALLATION_ID, null);
        firstInstallTime = prefs.getLong(KEY_FIRST_INSTALL_TIME, 0);
        
        // If no installation ID exists, generate a new one
        if (installationId == null) {
            installationId = generateUniqueInstallationId();
            firstInstallTime = System.currentTimeMillis();
            
            // Save the new installation ID
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_INSTALLATION_ID, installationId);
            editor.putLong(KEY_FIRST_INSTALL_TIME, firstInstallTime);
            editor.apply();
            
            Log.i(TAG, "Generated new installation ID: " + installationId);
        } else {
            Log.i(TAG, "Loaded existing installation ID: " + installationId);
        }
    }
    
    /**
     * Generates a unique installation ID based on device-specific information.
     * @return A unique installation ID
     */
    private String generateUniqueInstallationId() {
        try {
            // Combine multiple sources for uniqueness
            String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String packageName = context.getPackageName();
            String timestamp = String.valueOf(System.currentTimeMillis());
            String randomUuid = UUID.randomUUID().toString();
            
            // Create a unique string combining all sources
            String combinedString = deviceId + packageName + timestamp + randomUuid;
            
            // Generate SHA-256 hash for consistency and security
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combinedString.getBytes());
            
            // Convert to hexadecimal string and take first 16 characters
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.substring(0, 16).toUpperCase();
            
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error generating installation ID", e);
            // Fallback to UUID if SHA-256 is not available
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        }
    }
    
    /**
     * Resets the installation ID (for testing purposes).
     * This will generate a new installation ID as if the app was freshly installed.
     */
    public void resetInstallationId() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        
        // Generate new installation ID
        loadOrGenerateInstallationId();
        Log.i(TAG, "Reset installation ID to: " + installationId);
    }
} 