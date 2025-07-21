package choruscoldchain.app;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.io.IOException;
import java.io.InputStream;

public class ForgotPasswordActivity extends AppCompatActivity {
    private static final String TAG = "ForgotPasswordActivity";
    
    private EditText etEmail;
    private MaterialButton btnSubmit;
    private MaterialButton btnBackToLogin;
    private ImageView ivChorusLogo;
    
    private AuthManager authManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        
        authManager = AuthManager.getInstance(this);
        
        initViews();
        setupListeners();
        loadChorusLogo();
    }
    
    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);
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
        btnSubmit.setOnClickListener(v -> handleSubmit());
        btnBackToLogin.setOnClickListener(v -> finish());
    }
    
    private void handleSubmit() {
        String email = etEmail.getText().toString().trim();
        
        // Validation
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Please enter your email address.");
            return;
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email address.");
            return;
        }
        
        // Clear previous errors
        etEmail.setError(null);
        
        // Disable button and show loading
        btnSubmit.setEnabled(false);
        btnSubmit.setText("Loading...");
        
        authManager.forgotPassword(email, new AuthManager.AuthCallback<AuthModels.ForgotPasswordResponse>() {
            @Override
            public void onSuccess(AuthModels.ForgotPasswordResponse result) {
                runOnUiThread(() -> {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Submit");
                    
                    if (result.isSuccess()) {
                        Toast.makeText(ForgotPasswordActivity.this, 
                            "OTP sent to your email", Toast.LENGTH_LONG).show();
                        
                        // Navigate to OTP verification
                        navigateToVerifyOtp(email, result.getHashedUser(), true);
                    } else {
                        Toast.makeText(ForgotPasswordActivity.this, 
                            "Failed to generate OTP. Please try again.", Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Submit");
                    Toast.makeText(ForgotPasswordActivity.this, 
                        "Something went wrong. Please try again.", Toast.LENGTH_LONG).show();
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