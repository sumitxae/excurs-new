package com.choruscoldchain.utils

import android.util.Log
import com.minew.ble.mst03.bean.HtData
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object CsvGenerator {
    private const val TAG = "CsvGenerator"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private const val BASE_URL = "http://34.61.53.179:8000/v1"
    private const val uploadUrl = "$BASE_URL/csv/upload"

    /** Generate Excel in memory and upload it */
    fun generateAndUploadCsv(
    deviceMac: String,
    dataPoints: List<HtData>,
    callback: (Boolean) -> Unit
) {
    try {
        // Validate inputs
        if (deviceMac.isBlank()) {
            Log.e(TAG, "Device MAC is blank")
            callback(false)
            return
        }
        
        if (dataPoints.isEmpty()) {
            Log.e(TAG, "No data points provided")
            callback(false)
            return
        }
        
        Log.d(TAG, "Generating CSV for device: $deviceMac with ${dataPoints.size} data points")

        // Build CSV content
        val csvBuilder = StringBuilder()
        csvBuilder.append("Date&Time,Temperature,Temperature Status,Device MAC,Alarm\n")

        dataPoints.forEach { dataPoint ->
            val dateTime = formatTimestamp(dataPoint.timestamps)
            val temp = dataPoint.temperature
            val status = getTemperatureStatus(temp)
            val mac = dataPoint.macAddress ?: deviceMac
            val alarm = getAlarmStatus(temp)

            csvBuilder.append("$dateTime,$temp,$status,$mac,$alarm\n")
        }

        val csvBytes = csvBuilder.toString().toByteArray(Charsets.UTF_8)
        Log.d(TAG, "CSV content generated, size: ${csvBytes.size} bytes")

        // Prepare request body
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "trips_data_${deviceMac.replace(":", "_")}.csv",
                RequestBody.create("text/csv".toMediaType(), csvBytes)
            )
            .build()

        // Build request
        val request = Request.Builder().url(uploadUrl).post(requestBody).build()
        Log.d(TAG, "Starting upload to: $uploadUrl")

        // Execute upload
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Upload failed", e)
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        Log.d(TAG, "CSV uploaded successfully")
                        callback(true)
                    } else {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "Upload failed: ${response.code} $errorBody")
                        callback(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing response", e)
                    callback(false)
                } finally {
                    // Ensure response body is closed
                    response.body?.close()
                }
            }
        })

    } catch (e: Exception) {
        Log.e(TAG, "Error generating/uploading CSV", e)
        callback(false)
    }
}


    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val dateFormat = SimpleDateFormat("MM-dd-yyyy HH:mm:ss", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        } catch (e: Exception) {
            "Invalid Date"
        }
    }

    private fun getTemperatureStatus(temperature: Float): String {
        return when {
            temperature > 8.0f -> "HIGH TEMPERATURE"
            temperature < 2.0f -> "LOW TEMPERATURE"
            else -> "NORMAL"
        }
    }

    private fun getAlarmStatus(temperature: Float): String {
        return when {
            temperature > 8.0f -> "High"
            temperature < 2.0f -> "Low"
            else -> ""
        }
    }
}
