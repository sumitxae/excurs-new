package com.choruscoldchain.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ErrorCallback
import com.budiyev.android.codescanner.ScanMode
import com.choruscoldchain.R

class QRScannerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "QRScannerActivity"
        const val EXTRA_SCAN_RESULT = "scan_result"
    }
    
    private lateinit var codeScanner: CodeScanner
    private lateinit var scannerView: CodeScannerView
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Camera permission granted")
            startQRScanner()
        } else {
            Log.e(TAG, "Camera permission denied")
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)
        
        findViewById<android.view.View>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        scannerView = findViewById(R.id.scanner_view)
        
        // Initialize CodeScanner
        codeScanner = CodeScanner(this, scannerView)
        
        // Request camera permission
        requestCameraPermission()
    }
    
    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                startQRScanner()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startQRScanner() {
        codeScanner.apply {
            scanMode = ScanMode.SINGLE
            decodeCallback = DecodeCallback { result ->
                runOnUiThread {
                    Log.d(TAG, "Scanned: ${result.text}")
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_SCAN_RESULT, result.text)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
            errorCallback = ErrorCallback { result ->
                runOnUiThread {
                    Log.e(TAG, "Camera initialization error: ${result.message}")
                    Toast.makeText(this@QRScannerActivity, "Camera error: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        codeScanner.startPreview()
    }
    
    override fun onResume() {
        super.onResume()
        if (::codeScanner.isInitialized) {
            codeScanner.startPreview()
        }
    }
    
    override fun onPause() {
        if (::codeScanner.isInitialized) {
            codeScanner.releaseResources()
        }
        super.onPause()
    }
}
