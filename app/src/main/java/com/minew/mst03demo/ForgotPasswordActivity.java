package com.minew.mst03demo;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.widget.TextView;

public class ForgotPasswordActivity extends AppCompatActivity {
    private static final String TAG = "ForgotPasswordActivity";
    
    private TextInputLayout tilEmail;
    private TextInputEditText etEmail;
    private MaterialButton btnSendOtp;
    private TextView tvBackToSignIn;
    
    private AuthManager authManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        
        authManager = AuthManager.getInstance(this);
        
        initViews();
        setupListeners();
    }
    
    private void initViews() {
        tilEmail = findViewById(R.id.tilEmail);
        etEmail = findViewById(R.id.etEmail);
        btnSendOtp = findViewById(R.id.btnSendOtp);
        tvBackToSignIn = findViewById(R.id.tvBackToSignIn);
    }
    
    private void setupListeners() {
        btnSendOtp.setOnClickListener(v -> handleSendOtp());
        tvBackToSignIn.setOnClickListener(v -> finish());
    }
    
    private void handleSendOtp() {
        String email = etEmail.getText().toString().trim();
        
        // Validation
        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Please enter your email");
            return;
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter a valid email");
            return;
        }
        
        // Clear previous errors
        tilEmail.setError(null);
        
        // Disable button and show loading
        btnSendOtp.setEnabled(false);
        btnSendOtp.setText("Sending OTP...");
        
        authManager.forgotPassword(email, new AuthManager.AuthCallback<AuthModels.ForgotPasswordResponse>() {
            @Override
            public void onSuccess(AuthModels.ForgotPasswordResponse result) {
                runOnUiThread(() -> {
                    btnSendOtp.setEnabled(true);
                    btnSendOtp.setText("Send OTP");
                    
                    if (result.isSuccess()) {
                        Toast.makeText(ForgotPasswordActivity.this, 
                            "OTP sent to your email", Toast.LENGTH_LONG).show();
                        
                        // Navigate to OTP verification
                        navigateToVerifyOtp(email, result.getHashedUser(), true);
                    } else {
                        Toast.makeText(ForgotPasswordActivity.this, 
                            result.getMessage() != null ? result.getMessage() : "Failed to send OTP", 
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnSendOtp.setEnabled(true);
                    btnSendOtp.setText("Send OTP");
                    Toast.makeText(ForgotPasswordActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void navigateToVerifyOtp(String email, String hashedUser, boolean fromForgotPassword) {
        Intent intent = new Intent(this, VerifyOtpActivity.class);
        intent.putExtra("email", email);
        intent.putExtra("hashedUser", hashedUser);
        intent.putExtra("fromForgotPassword", fromForgotPassword);
        startActivity(intent);
        finish();
    }
} 