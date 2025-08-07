package choruscoldchain.app;

import android.util.Log;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.List;
import com.minew.ble.mst03.bean.HtData;

public class HttpLogger {
    private static final String TAG = "HttpLogger";
    // Use actual IP address for real device
    private static final String LOG_URL = "http://34.61.53.179:8000/v1/logger/log";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ExecutorService executor;

    public HttpLogger() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void logScanData(String deviceMac, float temperature, int battery, String firmwareVersion, int rssi) {
        // Validate temperature before logging
        if (temperature == 0.0f || Float.isNaN(temperature)) {
            Log.w(TAG, "Skipping HTTP log for device " + deviceMac + " - invalid temperature: " + temperature);
            return;
        }
        
        try {
            JSONObject logData = new JSONObject();
            logData.put("timestamp",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            logData.put("deviceMac", deviceMac);
            logData.put("temperature", temperature);
            logData.put("battery", battery);
            logData.put("firmwareVersion", firmwareVersion);
            logData.put("rssi", rssi);
            logData.put("eventType", "scan");

            String jsonMessage = logData.toString();
            Log.d(TAG, "Sending scan data to server for device: " + deviceMac + " with temperature: " + temperature + "°C");
            sendLogAsync(jsonMessage);

        } catch (Exception e) {
            Log.e(TAG, "Error creating log data: " + e.getMessage());
        }
    }

    public void logConnectionEvent(String deviceMac, String event, String status) {
        try {
            JSONObject logData = new JSONObject();
            logData.put("timestamp",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            logData.put("deviceMac", deviceMac);
            logData.put("event", event);
            logData.put("status", status);
            logData.put("eventType", "connection");

            String jsonMessage = logData.toString();
            sendLogAsync(jsonMessage);

        } catch (Exception e) {
            Log.e(TAG, "Error creating connection log: " + e.getMessage());
        }
    }

    public void logCompleteDeviceData(String deviceMac, String deviceName, float temperature, String firmware,
            int battery, int rssi, List<HtData> historicalData, List<ExcursionData> excursions) {
        // Validate temperature before logging
        if (temperature == 0.0f || Float.isNaN(temperature)) {
            Log.w(TAG, "Skipping complete device data log for device " + deviceMac + " - invalid temperature: " + temperature);
            return;
        }
        
        Log.d(TAG, "logCompleteDeviceData called for device: " + deviceMac);
        Log.d(TAG, "Device details - Name: " + deviceName + ", Temp: " + temperature + "°C, Battery: " + battery
                + ", RSSI: " + rssi);
        Log.d(TAG, "Historical data count: " + historicalData.size() + ", Excursions count: " + excursions.size());

        // Send full data in chunks to handle large payloads
        sendFullDataInChunks(deviceMac, deviceName, temperature, firmware, battery, rssi, historicalData, excursions);
    }

    private void sendFullDataInChunks(String deviceMac, String deviceName, float temperature, String firmware,
            int battery, int rssi, List<HtData> historicalData, List<ExcursionData> excursions) {
        try {
            // First, send device info with metadata
            JSONObject deviceInfo = new JSONObject();
            deviceInfo.put("timestamp",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            deviceInfo.put("deviceMac", deviceMac);
            deviceInfo.put("deviceName", deviceName);
            deviceInfo.put("temperature", temperature);
            deviceInfo.put("firmware", firmware);
            deviceInfo.put("battery", battery);
            deviceInfo.put("rssi", rssi);
            deviceInfo.put("eventType", "deviceInfo");
            deviceInfo.put("totalHistoricalRecords", historicalData.size());
            deviceInfo.put("totalExcursions", excursions.size());
            deviceInfo.put("chunked", true);

            String deviceInfoJson = deviceInfo.toString();
            Log.d(TAG, "Sending device info, length: " + deviceInfoJson.length() + " bytes");
            sendLogAsync(deviceInfoJson);

            // Send historical data in chunks
            int chunkSize = 1000; // 1000 records per chunk
            int totalChunks = (int) Math.ceil((double) historicalData.size() / chunkSize);

            for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
                int startIndex = chunkIndex * chunkSize;
                int endIndex = Math.min(startIndex + chunkSize, historicalData.size());

                JSONObject historicalChunk = new JSONObject();
                historicalChunk.put("timestamp",
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                historicalChunk.put("deviceMac", deviceMac);
                historicalChunk.put("eventType", "historicalDataChunk");
                historicalChunk.put("chunkIndex", chunkIndex);
                historicalChunk.put("totalChunks", totalChunks);
                historicalChunk.put("startIndex", startIndex);
                historicalChunk.put("endIndex", endIndex);

                JSONArray historicalArray = new JSONArray();
                for (int i = startIndex; i < endIndex; i++) {
                    HtData htData = historicalData.get(i);
                    JSONObject histItem = new JSONObject();
                    histItem.put("temperature", htData.getTemperature());
                    histItem.put("humidity", htData.getHumidity());
                    histItem.put("timestamp", htData.getTimestamps());
                    historicalArray.put(histItem);
                }
                historicalChunk.put("historicalData", historicalArray);

                String chunkJson = historicalChunk.toString();
                Log.d(TAG, "Sending historical data chunk " + (chunkIndex + 1) + "/" + totalChunks +
                        ", records " + startIndex + "-" + (endIndex - 1) +
                        ", length: " + chunkJson.length() + " bytes");

                // Add delay between chunks to prevent overwhelming the server
                if (chunkIndex > 0) {
                    try {
                        Thread.sleep(100); // 100ms delay between chunks
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                sendLogAsync(chunkJson);
            }

            // Send excursions in chunks
            if (!excursions.isEmpty()) {
                int excursionChunkSize = 500; // 500 excursions per chunk
                int totalExcursionChunks = (int) Math.ceil((double) excursions.size() / excursionChunkSize);

                for (int chunkIndex = 0; chunkIndex < totalExcursionChunks; chunkIndex++) {
                    int startIndex = chunkIndex * excursionChunkSize;
                    int endIndex = Math.min(startIndex + excursionChunkSize, excursions.size());

                    JSONObject excursionChunk = new JSONObject();
                    excursionChunk.put("timestamp",
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                    excursionChunk.put("deviceMac", deviceMac);
                    excursionChunk.put("eventType", "excursionDataChunk");
                    excursionChunk.put("chunkIndex", chunkIndex);
                    excursionChunk.put("totalChunks", totalExcursionChunks);
                    excursionChunk.put("startIndex", startIndex);
                    excursionChunk.put("endIndex", endIndex);

                    JSONArray excursionsArray = new JSONArray();
                    for (int i = startIndex; i < endIndex; i++) {
                        ExcursionData excursion = excursions.get(i);
                        JSONObject excursionItem = new JSONObject();
                        excursionItem.put("temperature", excursion.getTemperature());
                        excursionItem.put("timestamp", excursion.getTimestamp());
                        excursionItem.put("type", excursion.getExcursionType());
                        excursionsArray.put(excursionItem);
                    }
                    excursionChunk.put("excursions", excursionsArray);

                    String chunkJson = excursionChunk.toString();
                    Log.d(TAG, "Sending excursion data chunk " + (chunkIndex + 1) + "/" + totalExcursionChunks +
                            ", excursions " + startIndex + "-" + (endIndex - 1) +
                            ", length: " + chunkJson.length() + " bytes");

                    // Add delay between chunks
                    if (chunkIndex > 0) {
                        try {
                            Thread.sleep(100); // 100ms delay between chunks
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    sendLogAsync(chunkJson);
                }
            }

            // Send completion marker
            JSONObject completionMarker = new JSONObject();
            completionMarker.put("timestamp",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            completionMarker.put("deviceMac", deviceMac);
            completionMarker.put("eventType", "dataTransferComplete");
            completionMarker.put("totalHistoricalRecords", historicalData.size());
            completionMarker.put("totalExcursions", excursions.size());
            completionMarker.put("totalHistoricalChunks", totalChunks);
            completionMarker.put("totalExcursionChunks",
                    excursions.isEmpty() ? 0 : (int) Math.ceil((double) excursions.size() / 500));

            String completionJson = completionMarker.toString();
            Log.d(TAG, "Sending data transfer completion marker, length: " + completionJson.length() + " bytes");
            sendLogAsync(completionJson);

        } catch (Exception e) {
            Log.e(TAG, "Error sending full data in chunks: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendDeviceInfoOnly(String deviceMac, String deviceName, float temperature, String firmware,
            int battery, int rssi, int totalHistoricalRecords, int totalExcursions) {
        try {
            JSONObject logData = new JSONObject();
            logData.put("timestamp",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            logData.put("deviceMac", deviceMac);
            logData.put("deviceName", deviceName);
            logData.put("temperature", temperature);
            logData.put("firmware", firmware);
            logData.put("battery", battery);
            logData.put("rssi", rssi);
            logData.put("eventType", "deviceInfoOnly");
            logData.put("totalHistoricalRecords", totalHistoricalRecords);
            logData.put("totalExcursions", totalExcursions);
            logData.put("note", "Historical data omitted due to payload size limits");

            String jsonMessage = logData.toString();
            Log.d(TAG, "Device info only JSON prepared, length: " + jsonMessage.length() + " bytes");
            sendLogAsync(jsonMessage);

        } catch (Exception e) {
            Log.e(TAG, "Error creating device info only log: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendLogAsync(String message) {
        Log.d(TAG, "sendLogAsync called, queuing HTTP request");
        executor.execute(() -> {
            try {
                Log.d(TAG, "Executing HTTP request to: " + LOG_URL);
                Log.d(TAG, "Log message length: " + message.length());

                RequestBody body = RequestBody.create(message, JSON);
                Request request = new Request.Builder()
                        .url(LOG_URL)
                        .post(body)
                        .build();

                Log.d(TAG, "HTTP request built, executing...");

                try (Response response = client.newCall(request).execute()) {
                    Log.d(TAG,
                            "HTTP response received - Code: " + response.code() + ", Message: " + response.message());
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Log sent successfully to server");
                        String responseBody = response.body() != null ? response.body().string() : "No response body";
                        Log.d(TAG, "Server response: " + responseBody);
                    } else {
                        Log.e(TAG, "Failed to send log. Response: " + response.code() + " " + response.message());
                        String responseBody = response.body() != null ? response.body().string() : "No response body";
                        Log.e(TAG, "Response body: " + responseBody);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Network error sending log to server: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in sendLogAsync: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}