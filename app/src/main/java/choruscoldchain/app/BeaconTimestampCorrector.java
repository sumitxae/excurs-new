package choruscoldchain.app;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utility class to correct beacon timestamps from device's internal clock to current system time.
 * The beacon's internal clock is stuck around 2003, so we need to normalize timestamps.
 */
public class BeaconTimestampCorrector {
    private static final String TAG = "BeaconTimestampCorrector";
    
    /**
     * Corrects a beacon timestamp to current system time.
     * 
     * @param deviceTimestamp The raw timestamp from the beacon (Unix seconds)
     * @param currentTimestamp The beacon's currentTimestamp (Unix seconds)
     * @param systemTimeSeconds Current system time in Unix seconds
     * @return Corrected timestamp in Unix seconds
     */
    public static long correctBeaconTimestamp(long deviceTimestamp, long currentTimestamp, long systemTimeSeconds) {
        // Calculate offset between real system time and beacon's currentTimestamp
        long offset = systemTimeSeconds - currentTimestamp;
        
        // Apply offset to the device timestamp
        long correctedTimestamp = deviceTimestamp + offset;
        
        Log.d(TAG, String.format("Timestamp correction - Device: %d, Current: %d, System: %d, Offset: %d, Corrected: %d", 
            deviceTimestamp, currentTimestamp, systemTimeSeconds, offset, correctedTimestamp));
        
        return correctedTimestamp;
    }
    
    /**
     * Corrects a beacon timestamp using current system time.
     * 
     * @param deviceTimestamp The raw timestamp from the beacon (Unix seconds)
     * @param currentTimestamp The beacon's currentTimestamp (Unix seconds)
     * @return Corrected timestamp in Unix seconds
     */
    public static long correctBeaconTimestamp(long deviceTimestamp, long currentTimestamp) {
        long systemTimeSeconds = System.currentTimeMillis() / 1000;
        return correctBeaconTimestamp(deviceTimestamp, currentTimestamp, systemTimeSeconds);
    }
    
    /**
     * Converts Unix timestamp to human-readable format.
     * 
     * @param timestampSeconds Unix timestamp in seconds
     * @return Human-readable date string
     */
    public static String timestampToHumanReadable(long timestampSeconds) {
        try {
            Date date = new Date(timestampSeconds * 1000);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf.format(date);
        } catch (Exception e) {
            Log.e(TAG, "Error formatting timestamp: " + e.getMessage());
            return "Invalid timestamp: " + timestampSeconds;
        }
    }
    
    /**
     * Corrects and formats a beacon timestamp to human-readable format.
     * 
     * @param deviceTimestamp The raw timestamp from the beacon (Unix seconds)
     * @param currentTimestamp The beacon's currentTimestamp (Unix seconds)
     * @return Human-readable corrected date string
     */
    public static String correctAndFormatTimestamp(long deviceTimestamp, long currentTimestamp) {
        long correctedTimestamp = correctBeaconTimestamp(deviceTimestamp, currentTimestamp);
        return timestampToHumanReadable(correctedTimestamp);
    }
    
    /**
     * Logs timestamp correction details for debugging.
     * 
     * @param macAddress Device MAC address
     * @param deviceTimestamp The raw timestamp from the beacon
     * @param currentTimestamp The beacon's currentTimestamp
     * @param timestampType Type of timestamp (e.g., "tempEvent", "lightEvent")
     */
    public static void logTimestampCorrection(String macAddress, long deviceTimestamp, long currentTimestamp, String timestampType) {
        long systemTimeSeconds = System.currentTimeMillis() / 1000;
        long offset = systemTimeSeconds - currentTimestamp;
        long correctedTimestamp = deviceTimestamp + offset;
        
        String rawTime = timestampToHumanReadable(deviceTimestamp);
        String correctedTime = timestampToHumanReadable(correctedTimestamp);
        String systemTime = timestampToHumanReadable(systemTimeSeconds);
        
        Log.d(TAG, String.format("TIMESTAMP CORRECTION [%s] - Device: %s, Current: %s, System: %s", 
            macAddress, timestampType, rawTime, systemTime));
        Log.d(TAG, String.format("TIMESTAMP CORRECTION [%s] - Raw: %d, Corrected: %d, Offset: %d", 
            macAddress, deviceTimestamp, correctedTimestamp, offset));
        Log.d(TAG, String.format("TIMESTAMP CORRECTION [%s] - Raw: %s, Corrected: %s", 
            macAddress, rawTime, correctedTime));
    }

    /**
     * Test method to demonstrate timestamp correction functionality.
     * This can be called from anywhere to test the correction logic.
     */
    public static void testTimestampCorrection() {
        Log.d(TAG, "=== BEACON TIMESTAMP CORRECTION TEST ===");
        
        // Example data from your beacon
        long lightEventTimestamp = 1068176000;
        long tempEventTimestamp = 1068177000;
        long currentTimestamp = 1068181000;
        
        // Current system time (example: 2025-08-22 00:04 IST)
        long systemTimeSeconds = System.currentTimeMillis() / 1000;
        
        Log.d(TAG, "Raw beacon timestamps:");
        Log.d(TAG, "  lightEventTimestamp: " + lightEventTimestamp + " -> " + timestampToHumanReadable(lightEventTimestamp));
        Log.d(TAG, "  tempEventTimestamp: " + tempEventTimestamp + " -> " + timestampToHumanReadable(tempEventTimestamp));
        Log.d(TAG, "  currentTimestamp: " + currentTimestamp + " -> " + timestampToHumanReadable(currentTimestamp));
        Log.d(TAG, "  systemTime: " + systemTimeSeconds + " -> " + timestampToHumanReadable(systemTimeSeconds));
        
        // Calculate offset
        long offset = systemTimeSeconds - currentTimestamp;
        Log.d(TAG, "Offset: " + offset + " seconds");
        
        // Correct timestamps
        long correctedLightEvent = correctBeaconTimestamp(lightEventTimestamp, currentTimestamp, systemTimeSeconds);
        long correctedTempEvent = correctBeaconTimestamp(tempEventTimestamp, currentTimestamp, systemTimeSeconds);
        
        Log.d(TAG, "Corrected timestamps:");
        Log.d(TAG, "  lightEventTimestamp: " + correctedLightEvent + " -> " + timestampToHumanReadable(correctedLightEvent));
        Log.d(TAG, "  tempEventTimestamp: " + correctedTempEvent + " -> " + timestampToHumanReadable(correctedTempEvent));
        
        Log.d(TAG, "=== TEST COMPLETED ===");
    }
}
