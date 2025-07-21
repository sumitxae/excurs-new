package choruscoldchain.app;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;

public class VerifyOtpActivity extends AppCompatActivity {
    private static final String TAG = "VerifyOtpActivity";
    
    private EditText etOtp1, etOtp2, etOtp3, etOtp4;
    private MaterialButton btnVerify, btnBackToLogin;
    private TextView tvResendOtp;
    private ImageView ivChorusLogo;
    
    private AuthManager authManager;
    private String email, hashedUser;
    private boolean fromForgotPassword;
    private CountDownTimer timer;
    private int timeLeft = 60; // 60 seconds
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_otp);
        
        // Set status bar text color to black
        getWindow().getDecorView().setSystemUiVisibility(
            getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
        
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
        loadChorusLogo();
        startTimer();
        
        // Auto focus first input
        etOtp1.requestFocus();
    }
    
    private void initViews() {
        etOtp1 = findViewById(R.id.etOtp1);
        etOtp2 = findViewById(R.id.etOtp2);
        etOtp3 = findViewById(R.id.etOtp3);
        etOtp4 = findViewById(R.id.etOtp4);
        btnVerify = findViewById(R.id.btnVerify);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);
        tvResendOtp = findViewById(R.id.tvResendOtp);
        ivChorusLogo = findViewById(R.id.ivChorusLogo);
    }
    
    private void loadChorusLogo() {
        // Try to load chorus logo from assets in order of preference
        String[] logoPaths = {
            "images/chorus.png",           // Main chorus logo
            "chorus_logo.png",             // Alternative chorus logo
            "images/chorusWhite.jpeg"      // White version if needed
        };
        
        for (String logoPath : logoPaths) {
            try {
                InputStream inputStream = getAssets().open(logoPath);
                Drawable drawable = Drawable.createFromStream(inputStream, null);
                ivChorusLogo.setImageDrawable(drawable);
                inputStream.close();
                Log.d(TAG, "Successfully loaded chorus logo from: " + logoPath);
                return; // Successfully loaded, exit the method
            } catch (IOException e) {
                Log.d(TAG, "Could not load chorus logo from: " + logoPath);
                // Continue to next option
            }
        }
        
        // Fallback to drawable if all asset loading fails
        Log.w(TAG, "Falling back to drawable chorus logo");
        ivChorusLogo.setImageResource(R.drawable.ic_chorus_logo);
    }
    
    private void setupListeners() {
        btnVerify.setOnClickListener(v -> handleVerify());
        btnBackToLogin.setOnClickListener(v -> handleBack());
        tvResendOtp.setOnClickListener(v -> handleResendOtp());
        
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
    
    private void handleVerify() {
        String otp1 = etOtp1.getText().toString();
        String otp2 = etOtp2.getText().toString();
        String otp3 = etOtp3.getText().toString();
        String otp4 = etOtp4.getText().toString();
        
        String fullOtp = otp1 + otp2 + otp3 + otp4;
        
        if (fullOtp.length() != 4) {
            Toast.makeText(this, "Please enter the complete 4-digit OTP.", Toast.LENGTH_LONG).show();
            return;
        }
        
        btnVerify.setEnabled(false);
        btnVerify.setText("Loading...");
        
        authManager.verifyOtp(hashedUser.toLowerCase(), fullOtp, new AuthManager.AuthCallback<AuthModels.VerifyOtpResponse>() {
            @Override
            public void onSuccess(AuthModels.VerifyOtpResponse result) {
                runOnUiThread(() -> {
                    btnVerify.setEnabled(true);
                    btnVerify.setText("Verify");
                    
                    if (result.isSuccess()) {
                        // Save the access token from OTP verification
                        String accessToken = result.getAccessToken();
                        if (accessToken != null) {
                            // For OTP verification, we don't have role info yet, so save without role
                            authManager.saveAuthData(accessToken, email);
                        }
                        
                        Toast.makeText(VerifyOtpActivity.this, "OTP Verified!", Toast.LENGTH_SHORT).show();
                        navigateToResetPassword();
                    } else {
                        Toast.makeText(VerifyOtpActivity.this, 
                            "Invalid OTP. Please try again.", Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnVerify.setEnabled(true);
                    btnVerify.setText("Verify");
                    Toast.makeText(VerifyOtpActivity.this, 
                        "Something went wrong. Please try again.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void handleResendOtp() {
        if (timeLeft > 0) return;
        
        btnVerify.setEnabled(false);
        btnVerify.setText("Loading...");
        
        authManager.forgotPassword(email, new AuthManager.AuthCallback<AuthModels.ForgotPasswordResponse>() {
            @Override
            public void onSuccess(AuthModels.ForgotPasswordResponse result) {
                runOnUiThread(() -> {
                    btnVerify.setEnabled(true);
                    btnVerify.setText("Verify");
                    
                    if (result.isSuccess()) {
                        Toast.makeText(VerifyOtpActivity.this, "OTP has been sent to your email.", Toast.LENGTH_LONG).show();
                        hashedUser = result.getHashedUser();
                        clearOtpFields();
                        startTimer();
                        etOtp1.requestFocus();
                    } else {
                        Toast.makeText(VerifyOtpActivity.this, 
                            "Failed to generate OTP. Please try again.", Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnVerify.setEnabled(true);
                    btnVerify.setText("Verify");
                    Toast.makeText(VerifyOtpActivity.this, 
                        "Something went wrong. Please try again.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void handleBack() {
        finish();
    }
    
    private void startTimer() {
        timeLeft = 60;
        tvResendOtp.setText("Resend OTP in (60s)");
        tvResendOtp.setClickable(false);
        
        if (timer != null) {
            timer.cancel();
        }
        
        timer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeft = (int) (millisUntilFinished / 1000);
                tvResendOtp.setText("Resend OTP in (" + timeLeft + "s)");
            }
            
            @Override
            public void onFinish() {
                timeLeft = 0;
                tvResendOtp.setText("Resend OTP");
                tvResendOtp.setClickable(true);
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