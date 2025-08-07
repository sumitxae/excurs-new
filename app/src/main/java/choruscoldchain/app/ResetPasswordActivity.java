package choruscoldchain.app;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;

public class ResetPasswordActivity extends AppCompatActivity {
    private static final String TAG = "ResetPasswordActivity";
    
    private EditText etNewPassword, etConfirmPassword;
    private Button btnCreatePassword, btnBackToLogin;
    private ImageView ivChorusLogo, ivNewPasswordToggle, ivConfirmPasswordToggle;
    
    private AuthManager authManager;
    private String email;
    private String accessToken;
    private boolean isNewPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);
        
        // Get email and access token from intent
        Intent intent = getIntent();
        email = intent.getStringExtra("email");
        accessToken = intent.getStringExtra("accessToken");
        
        if (email == null || accessToken == null) {
            Toast.makeText(this, "Invalid access. Please try again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        authManager = AuthManager.getInstance(this);
        
        initViews();
        setupListeners();
        loadChorusLogo();
        setupAppId();
    }
    
    private void initViews() {
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnCreatePassword = findViewById(R.id.btnCreatePassword);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);
        ivChorusLogo = findViewById(R.id.ivChorusLogo);
        ivNewPasswordToggle = findViewById(R.id.ivNewPasswordToggle);
        ivConfirmPasswordToggle = findViewById(R.id.ivConfirmPasswordToggle);
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
                if (drawable != null) {
                    ivChorusLogo.setImageDrawable(drawable);
                    inputStream.close();
                    return; // Successfully loaded, exit
                }
                inputStream.close();
            } catch (IOException e) {
                // Continue to next logo path
            }
        }
        // If no logo loaded from assets, keep the default drawable
    }
    
    private void setupListeners() {
        btnCreatePassword.setOnClickListener(v -> handleCreatePassword());
        btnBackToLogin.setOnClickListener(v -> navigateToLogin());
        
        // Password visibility toggles
        ivNewPasswordToggle.setOnClickListener(v -> toggleNewPasswordVisibility());
        ivConfirmPasswordToggle.setOnClickListener(v -> toggleConfirmPasswordVisibility());
    }
    
    private void setupAppId() {
        InstallationIdManager installationIdManager = InstallationIdManager.getInstance(this);
        TextView tvAppId = findViewById(R.id.tv_scan_app_id);
        if (tvAppId != null) {
            tvAppId.setText("App ID: " + installationIdManager.getInstallationId());
        }
    }
    
    private void toggleNewPasswordVisibility() {
        if (isNewPasswordVisible) {
            etNewPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                            ivNewPasswordToggle.setImageResource(R.drawable.ic_visibility_off);
            isNewPasswordVisible = false;
        } else {
            etNewPassword.setTransformationMethod(null);
            ivNewPasswordToggle.setImageResource(R.drawable.ic_visibility);
            isNewPasswordVisible = true;
        }
        // Move cursor to end
        etNewPassword.setSelection(etNewPassword.getText().length());
    }
    
    private void toggleConfirmPasswordVisibility() {
        if (isConfirmPasswordVisible) {
            etConfirmPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                            ivConfirmPasswordToggle.setImageResource(R.drawable.ic_visibility_off);
            isConfirmPasswordVisible = false;
        } else {
            etConfirmPassword.setTransformationMethod(null);
            ivConfirmPasswordToggle.setImageResource(R.drawable.ic_visibility);
            isConfirmPasswordVisible = true;
        }
        // Move cursor to end
        etConfirmPassword.setSelection(etConfirmPassword.getText().length());
    }
    
    private void handleCreatePassword() {
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        
        // Validation
        if (TextUtils.isEmpty(newPassword)) {
            etNewPassword.setError("Please enter a new password");
            return;
        }
        
        if (newPassword.length() < 6) {
            etNewPassword.setError("Password must be at least 6 characters");
            return;
        }
        
        if (TextUtils.isEmpty(confirmPassword)) {
            etConfirmPassword.setError("Please confirm your password");
            return;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }
        
        // Clear previous errors
        etNewPassword.setError(null);
        etConfirmPassword.setError(null);
        
        // Disable button and show loading
        btnCreatePassword.setEnabled(false);
        btnCreatePassword.setText("Resetting...");
        
        // Save the access token temporarily for the reset password call
        authManager.saveAuthData(accessToken, email, null);
        
        authManager.resetPassword(newPassword, new AuthManager.AuthCallback<AuthModels.ResetPasswordResponse>() {
            @Override
            public void onSuccess(AuthModels.ResetPasswordResponse result) {
                runOnUiThread(() -> {
                    btnCreatePassword.setEnabled(true);
                    btnCreatePassword.setText("Reset Password");
                    
                    if (result.isSuccess()) {
                        Toast.makeText(ResetPasswordActivity.this, 
                            "Password reset successfully!", Toast.LENGTH_LONG).show();
                        navigateToLogin();
                    } else {
                        Toast.makeText(ResetPasswordActivity.this, 
                            result.getMessage() != null ? result.getMessage() : "Failed to reset password", 
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnCreatePassword.setEnabled(true);
                    btnCreatePassword.setText("Reset Password");
                    Toast.makeText(ResetPasswordActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void navigateToLogin() {
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 