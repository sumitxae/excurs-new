package com.minew.mst03demo;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.TextView;

public class VerifyOtpActivity extends AppCompatActivity {
    private static final String TAG = "VerifyOtpActivity";
    
    private TextInputEditText etOtp1, etOtp2, etOtp3, etOtp4;
    private MaterialButton btnVerifyOtp;
    private TextView tvEmail, tvResendTimer, tvResendOtp, tvBack;
    
    private AuthManager authManager;
    private String email, hashedUser;
    private boolean fromForgotPassword;
    private CountDownTimer timer;
    private int timeLeft = 60; // 60 seconds
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_otp);
        
        // Get data from intent
        Intent intent = getIntent();
        email = intent.getStringExtra("email");
        hashedUser = intent.getStringExtra("hashedUser");
        fromForgotPassword = intent.getBooleanExtra("fromForgotPassword", false);
        
        if (email == null || hashedUser == null) {
            Toast.makeText(this, "Invalid access. Please try again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        authManager = AuthManager.getInstance(this);
        
        initViews();
        setupListeners();
        startTimer();
        
        // Auto focus first input
        etOtp1.requestFocus();
    }
    
    private void initViews() {
        etOtp1 = findViewById(R.id.etOtp1);
        etOtp2 = findViewById(R.id.etOtp2);
        etOtp3 = findViewById(R.id.etOtp3);
        etOtp4 = findViewById(R.id.etOtp4);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        tvEmail = findViewById(R.id.tvEmail);
        tvResendTimer = findViewById(R.id.tvResendTimer);
        tvResendOtp = findViewById(R.id.tvResendOtp);
        tvBack = findViewById(R.id.tvBack);
        
        // Set email
        tvEmail.setText(email);
        
        // Update back button text based on source
        if (fromForgotPassword) {
            tvBack.setText("← Back to Forgot Password");
        } else {
            tvBack.setText("← Back to Sign In");
        }
    }
    
    private void setupListeners() {
        btnVerifyOtp.setOnClickListener(v -> handleVerifyOtp());
        tvResendOtp.setOnClickListener(v -> handleResendOtp());
        tvBack.setOnClickListener(v -> handleBack());
        
        // Setup OTP input listeners for auto-focus
        setupOtpInputListeners();
    }
    
    private void setupOtpInputListeners() {
        TextWatcher otpWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 1) {
                    // Move to next input
                    if (etOtp1.hasFocus()) {
                        etOtp2.requestFocus();
                    } else if (etOtp2.hasFocus()) {
                        etOtp3.requestFocus();
                    } else if (etOtp3.hasFocus()) {
                        etOtp4.requestFocus();
                    }
                } else if (s.length() == 0) {
                    // Move to previous input on backspace
                    if (etOtp2.hasFocus()) {
                        etOtp1.requestFocus();
                    } else if (etOtp3.hasFocus()) {
                        etOtp2.requestFocus();
                    } else if (etOtp4.hasFocus()) {
                        etOtp3.requestFocus();
                    }
                }
            }
        };
        
        etOtp1.addTextChangedListener(otpWatcher);
        etOtp2.addTextChangedListener(otpWatcher);
        etOtp3.addTextChangedListener(otpWatcher);
        etOtp4.addTextChangedListener(otpWatcher);
    }
    
    private void handleVerifyOtp() {
        String otp1 = etOtp1.getText().toString();
        String otp2 = etOtp2.getText().toString();
        String otp3 = etOtp3.getText().toString();
        String otp4 = etOtp4.getText().toString();
        
        String fullOtp = otp1 + otp2 + otp3 + otp4;
        
        if (fullOtp.length() != 4) {
            Toast.makeText(this, "Please enter complete 4-digit OTP", Toast.LENGTH_LONG).show();
            return;
        }
        
        btnVerifyOtp.setEnabled(false);
        btnVerifyOtp.setText("Verifying...");
        
        authManager.verifyOtp(hashedUser.toLowerCase(), fullOtp, new AuthManager.AuthCallback<AuthModels.VerifyOtpResponse>() {
            @Override
            public void onSuccess(AuthModels.VerifyOtpResponse result) {
                runOnUiThread(() -> {
                    btnVerifyOtp.setEnabled(true);
                    btnVerifyOtp.setText("Verify OTP");
                    
                    if (result.isSuccess()) {
                        // Save the access token from OTP verification
                        String accessToken = result.getAccessToken();
                        if (accessToken != null) {
                            authManager.saveAuthData(accessToken, email);
                        }
                        
                        Toast.makeText(VerifyOtpActivity.this, "OTP Verified!", Toast.LENGTH_SHORT).show();
                        navigateToResetPassword();
                    } else {
                        Toast.makeText(VerifyOtpActivity.this, 
                            result.getMessage() != null ? result.getMessage() : "Invalid OTP", 
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnVerifyOtp.setEnabled(true);
                    btnVerifyOtp.setText("Verify OTP");
                    Toast.makeText(VerifyOtpActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void handleResendOtp() {
        if (timeLeft > 0) return;
        
        authManager.forgotPassword(email, new AuthManager.AuthCallback<AuthModels.ForgotPasswordResponse>() {
            @Override
            public void onSuccess(AuthModels.ForgotPasswordResponse result) {
                runOnUiThread(() -> {
                    if (result.isSuccess()) {
                        Toast.makeText(VerifyOtpActivity.this, "OTP resent to your email", Toast.LENGTH_LONG).show();
                        hashedUser = result.getHashedUser();
                        clearOtpFields();
                        startTimer();
                        etOtp1.requestFocus();
                    } else {
                        Toast.makeText(VerifyOtpActivity.this, 
                            result.getMessage() != null ? result.getMessage() : "Failed to resend OTP", 
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(VerifyOtpActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void handleBack() {
        if (fromForgotPassword) {
            Intent intent = new Intent(this, ForgotPasswordActivity.class);
            startActivity(intent);
        }
        finish();
    }
    
    private void startTimer() {
        timeLeft = 60;
        tvResendTimer.setVisibility(View.VISIBLE);
        tvResendOtp.setVisibility(View.GONE);
        
        if (timer != null) {
            timer.cancel();
        }
        
        timer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeft = (int) (millisUntilFinished / 1000);
                int minutes = timeLeft / 60;
                int seconds = timeLeft % 60;
                tvResendTimer.setText(String.format("Resend OTP in %d:%02d", minutes, seconds));
            }
            
            @Override
            public void onFinish() {
                timeLeft = 0;
                tvResendTimer.setVisibility(View.GONE);
                tvResendOtp.setVisibility(View.VISIBLE);
            }
        }.start();
    }
    
    private void clearOtpFields() {
        etOtp1.setText("");
        etOtp2.setText("");
        etOtp3.setText("");
        etOtp4.setText("");
    }
    
    private void navigateToResetPassword() {
        Intent intent = new Intent(this, ResetPasswordActivity.class);
        intent.putExtra("email", email);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
} 