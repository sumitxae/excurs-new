package com.minew.sensormanager.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.minew.sensormanager.R

class CustomScannerActivity : CaptureActivity() {
    
    companion object {
        private const val TAG = "CustomScannerActivity"
        private const val CAMERA_PERMISSION_REQUEST = 100
    }
    
    private lateinit var barcodeScannerView: DecoratedBarcodeView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set custom layout
        setContentView(R.layout.activity_custom_scanner)
        
        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner)
        
        // Setup back button
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        // Request camera permission
        requestCameraPermission()
    }
    
    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        } else {
            startScanning()
        }
    }
    
    private fun startScanning() {
        try {
            // Configure the scanner view
            barcodeScannerView.apply {
                // Set QR code format
                barcodeView.decoderFactory = com.journeyapps.barcodescanner.DefaultDecoderFactory()
                
                // Start scanning
                resume()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scanner", e)
            Toast.makeText(this, "Error starting camera", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        barcodeScannerView.resume()
    }
    
    override fun onPause() {
        super.onPause()
        barcodeScannerView.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        barcodeScannerView.pause()
    }
}
