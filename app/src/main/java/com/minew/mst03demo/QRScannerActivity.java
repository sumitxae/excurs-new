package com.minew.mst03demo;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.budiyev.android.codescanner.ErrorCallback;
import com.budiyev.android.codescanner.ScanMode;
import com.google.zxing.Result;

import java.util.List;

public class QRScannerActivity extends AppCompatActivity {
    private static final String TAG = "QRScannerActivity";
    public static final String EXTRA_SCAN_RESULT = "scan_result";
    
    private CodeScanner codeScanner;
    private CodeScannerView scannerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);
        
        scannerView = findViewById(R.id.scanner_view);
        
        // Set up back button
        findViewById(R.id.btn_back).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                finish();
            }
        });
        
        // Request camera permission first
        requestCameraPermission();
    }
    
    private void requestCameraPermission() {
        // For now, just initialize the scanner directly
        // Camera permission will be requested by the scanner library
        initializeScanner();
    }
    
    private void initializeScanner() {
        codeScanner = new CodeScanner(this, scannerView);
        
        // Configure scanner
        codeScanner.setCamera(CodeScanner.CAMERA_BACK);
        codeScanner.setFormats(CodeScanner.ALL_FORMATS);
        codeScanner.setScanMode(ScanMode.SINGLE);
        codeScanner.setAutoFocusEnabled(true);
        codeScanner.setFlashEnabled(false);
        
        // Set decode callback
        codeScanner.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(@NonNull final Result result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String scanResult = result.getText();
                        Log.d(TAG, "QR Code scanned: " + scanResult);
                        
                        // Return the result to the calling activity
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra(EXTRA_SCAN_RESULT, scanResult);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    }
                });
            }
        });
        
        // Set error callback
        codeScanner.setErrorCallback(new ErrorCallback() {
            @Override
            public void onError(@NonNull Throwable error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.e(TAG, "Camera error: " + error.getMessage());
                        Toast.makeText(QRScannerActivity.this, 
                                "Camera error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        
        // Start preview when scanner view is clicked
        scannerView.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                codeScanner.startPreview();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (codeScanner != null) {
            codeScanner.startPreview();
        }
    }

    @Override
    protected void onPause() {
        if (codeScanner != null) {
            codeScanner.releaseResources();
        }
        super.onPause();
    }
} 