package com.minew.mst03demo;

import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ResetPasswordActivity extends AppCompatActivity {
    private static final String TAG = "ResetPasswordActivity";
    
    private TextInputLayout tilNewPassword, tilConfirmPassword;
    private TextInputEditText etNewPassword, etConfirmPassword;
    private MaterialButton btnResetPassword;
    
    private AuthManager authManager;
    private String email;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);
        
        // Get email from intent
        Intent intent = getIntent();
        email = intent.getStringExtra("email");
        
        if (email == null) {
            Toast.makeText(this, "Invalid access. Please try again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        authManager = AuthManager.getInstance(this);
        
        initViews();
        setupListeners();
    }
    
    private void initViews() {
        tilNewPassword = findViewById(R.id.tilNewPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnResetPassword = findViewById(R.id.btnResetPassword);
    }
    
    private void setupListeners() {
        btnResetPassword.setOnClickListener(v -> handleResetPassword());
    }
    
    private void handleResetPassword() {
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        
        // Validation
        if (TextUtils.isEmpty(newPassword)) {
            tilNewPassword.setError("Please enter a new password");
            return;
        }
        
        if (newPassword.length() < 6) {
            tilNewPassword.setError("Password must be at least 6 characters");
            return;
        }
        
        if (TextUtils.isEmpty(confirmPassword)) {
            tilConfirmPassword.setError("Please confirm your password");
            return;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            tilConfirmPassword.setError("Passwords do not match");
            return;
        }
        
        // Clear previous errors
        tilNewPassword.setError(null);
        tilConfirmPassword.setError(null);
        
        // Disable button and show loading
        btnResetPassword.setEnabled(false);
        btnResetPassword.setText("Resetting...");
        
        authManager.initPassword(newPassword, new AuthManager.AuthCallback<AuthModels.InitPasswordResponse>() {
            @Override
            public void onSuccess(AuthModels.InitPasswordResponse result) {
                runOnUiThread(() -> {
                    btnResetPassword.setEnabled(true);
                    btnResetPassword.setText("Reset Password");
                    
                    if (result.isSuccess()) {
                        Toast.makeText(ResetPasswordActivity.this, 
                            "Password set successfully!", Toast.LENGTH_LONG).show();
                        navigateToMain();
                    } else {
                        Toast.makeText(ResetPasswordActivity.this, 
                            result.getMessage() != null ? result.getMessage() : "Failed to set password", 
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnResetPassword.setEnabled(true);
                    btnResetPassword.setText("Reset Password");
                    Toast.makeText(ResetPasswordActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void navigateToMain() {
        Intent intent = new Intent(this, ScanDevicesListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 