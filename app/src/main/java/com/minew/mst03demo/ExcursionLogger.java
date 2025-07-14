package com.minew.mst03demo;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExcursionLogger {
    private static final String TAG = "ExcursionLogger";
    private static final String LOG_DIR = "excursion_logs";
    private static final String LOG_FILE_PREFIX = "excursion_log_";
    
    private Context context;
    private String deviceMac;
    
    public ExcursionLogger(Context context, String deviceMac) {
        this.context = context;
        this.deviceMac = deviceMac.replace(":", "_");
    }
    
    public void logExcursions(List<ExcursionData> excursions) {
        if (excursions == null || excursions.isEmpty()) {
            Log.d(TAG, "No excursions to log");
            return;
        }
        
        try {
            // Create log directory if it doesn't exist
            File logDir = new File(context.getFilesDir(), LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // Create log file with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = LOG_FILE_PREFIX + deviceMac + "_" + timestamp + ".txt";
            File logFile = new File(logDir, fileName);
            
            FileWriter writer = new FileWriter(logFile);
            
            // Write header
            writer.write("=== EXCURSION LOG REPORT ===\n");
            writer.write("Device MAC: " + deviceMac.replace("_", ":") + "\n");
            writer.write("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n");
            writer.write("Total Excursions: " + excursions.size() + "\n");
            writer.write("Excursion Threshold: 2°C - 8°C\n");
            writer.write("================================\n\n");
            
            // Write excursion details
            for (ExcursionData excursion : excursions) {
                writer.write(excursion.toString() + "\n");
            }
            
            // Write summary
            writer.write("\n=== SUMMARY ===\n");
            long highExcursions = excursions.stream().filter(e -> "HIGH".equals(e.getExcursionType())).count();
            long lowExcursions = excursions.stream().filter(e -> "LOW".equals(e.getExcursionType())).count();
            writer.write("High Temperature Excursions (>8°C): " + highExcursions + "\n");
            writer.write("Low Temperature Excursions (<2°C): " + lowExcursions + "\n");
            
            writer.close();
            
            Log.d(TAG, "Excursion log saved to: " + logFile.getAbsolutePath());
            
        } catch (IOException e) {
            Log.e(TAG, "Error writing excursion log: " + e.getMessage());
        }
    }
    
    public String getLogFilePath() {
        File logDir = new File(context.getFilesDir(), LOG_DIR);
        if (logDir.exists()) {
            File[] files = logDir.listFiles((dir, name) -> name.startsWith(LOG_FILE_PREFIX + deviceMac));
            if (files != null && files.length > 0) {
                // Return the most recent log file
                File latestFile = files[0];
                for (File file : files) {
                    if (file.lastModified() > latestFile.lastModified()) {
                        latestFile = file;
                    }
                }
                return latestFile.getAbsolutePath();
            }
        }
        return null;
    }
} 