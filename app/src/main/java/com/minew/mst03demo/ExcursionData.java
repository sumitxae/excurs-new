package com.minew.mst03demo;

import java.util.Date;

public class ExcursionData {
    private float temperature;
    private long timestamp;
    private String excursionType; // "HIGH" or "LOW"
    private String deviceMac;
    
    public ExcursionData(float temperature, long timestamp, String excursionType, String deviceMac) {
        this.temperature = temperature;
        this.timestamp = timestamp;
        this.excursionType = excursionType;
        this.deviceMac = deviceMac;
    }
    
    public float getTemperature() {
        return temperature;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getExcursionType() {
        return excursionType;
    }
    
    public String getDeviceMac() {
        return deviceMac;
    }
    
    public Date getDate() {
        return new Date(timestamp);
    }
    
    @Override
    public String toString() {
        return String.format("Excursion: %s - Temperature: %.2f°C - Time: %s - Device: %s", 
            excursionType, temperature, new Date(timestamp), deviceMac);
    }
} 