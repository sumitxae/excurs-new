package choruscoldchain.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
    private static final int CAMERA_PERMISSION_REQUEST = 100;
    
    private CodeScanner codeScanner;
    private CodeScannerView scannerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);
        
        scannerView = findViewById(R.id.scanner_view);
        
        
        findViewById(R.id.btn_back).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                finish();
            }
        });
        
        
        requestCameraPermission();
    }
    
    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.CAMERA}, 
                    CAMERA_PERMISSION_REQUEST);
        } else {
            
            initializeScanner();
        }
    }
    
    private void initializeScanner() {
        codeScanner = new CodeScanner(this, scannerView);
        
        
        codeScanner.setCamera(CodeScanner.CAMERA_BACK);
        codeScanner.setFormats(CodeScanner.ALL_FORMATS);
        codeScanner.setScanMode(ScanMode.SINGLE);
        codeScanner.setAutoFocusEnabled(true);
        codeScanner.setFlashEnabled(false);
        
        
        codeScanner.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(@NonNull final Result result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String scanResult = result.getText();
                        Log.d(TAG, "QR Code scanned: " + scanResult);
                        
                        
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra(EXTRA_SCAN_RESULT, scanResult);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    }
                });
            }
        });
        
        
        codeScanner.setErrorCallback(new ErrorCallback() {
            @Override
            public void onError(@NonNull Throwable error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.e(TAG, "Camera error: " + error.getMessage());
                        String errorMessage = "Camera error: " + error.getMessage();
                        
                        
                        if (error.getMessage() != null && 
                            error.getMessage().contains("failed to connect to camera service")) {
                            errorMessage = "Camera service unavailable. Please check if another app is using the camera.";
                        } else if (error.getMessage() != null && 
                                   error.getMessage().contains("permission")) {
                            errorMessage = "Camera permission denied. Please grant camera permission in settings.";
                        }
                        
                        Toast.makeText(QRScannerActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        
                        
                        new android.os.Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                finish();
                            }
                        }, 2000);
                    }
                });
            }
        });
        
        
        scannerView.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View view) {
                codeScanner.startPreview();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                
                Log.d(TAG, "Camera permission granted");
                initializeScanner();
            } else {
                
                Log.e(TAG, "Camera permission denied");
                Toast.makeText(this, "Camera permission is required to scan QR codes", 
                             Toast.LENGTH_LONG).show();
                finish();
            }
        }
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