package choruscoldchain.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class SignInActivity extends AppCompatActivity {
    private static final String TAG = "SignInActivity";
    
    private EditText etEmail, etPassword;
    private MaterialButton btnSignIn;
    private TextView tvForgotPassword;
    private LinearLayout llPasswordContainer;
    private ImageView ivPasswordToggle;
    
    private AuthManager authManager;
    private boolean isUserVerified = false;
    private boolean isPasswordVisible = false;
    private String userEmail = "";
    private String hashedUser = "";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        
        // Check if user is already logged in
        authManager = AuthManager.getInstance(this);
        if (authManager.isLoggedIn()) {
            navigateToMain();
            return;
        }
        
        initViews();
        setupListeners();
    }
    
    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        llPasswordContainer = findViewById(R.id.llPasswordContainer);
        ivPasswordToggle = findViewById(R.id.ivPasswordToggle);
    }
    
    private void setupListeners() {
        btnSignIn.setOnClickListener(v -> handleSignIn());
        tvForgotPassword.setOnClickListener(v -> navigateToForgotPassword());
        
        // Password visibility toggle
        ivPasswordToggle.setOnClickListener(v -> togglePasswordVisibility());
    }
    
    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility_off);
            isPasswordVisible = false;
        } else {
            etPassword.setTransformationMethod(null);
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility);
            isPasswordVisible = true;
        }
        // Move cursor to end
        etPassword.setSelection(etPassword.getText().length());
    }
    
    private void handleSignIn() {
        String email = etEmail.getText().toString().trim();
        
        if (!isUserVerified) {
            // First step: Check if user exists and is verified
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Please enter your email");
                return;
            }
            
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Please enter a valid email");
                return;
            }
            
            btnSignIn.setEnabled(false);
            btnSignIn.setText("Loading...");
            
            authManager.initLogin(email, new AuthManager.AuthCallback<AuthModels.InitLoginResponse>() {
                @Override
                public void onSuccess(AuthModels.InitLoginResponse result) {
                    runOnUiThread(() -> {
                        btnSignIn.setEnabled(true);
                        if (result.isSuccess()) {
                            userEmail = email;
                            hashedUser = result.getHashedUser();
                            
                            if (result.isUserVerified()) {
                                // User is verified, show password field
                                isUserVerified = true;
                                showPasswordField();
                            } else {
                                // User needs OTP verification
                                navigateToVerifyOtp(email, hashedUser, false);
                            }
                        } else {
                            Toast.makeText(SignInActivity.this, 
                                result.getMessage() != null ? result.getMessage() : "Sign in failed", 
                                Toast.LENGTH_LONG).show();
                            btnSignIn.setText("Next");
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        btnSignIn.setEnabled(true);
                        btnSignIn.setText("Next");
                        Toast.makeText(SignInActivity.this, error, Toast.LENGTH_LONG).show();
                    });
                }
            });
            
        } else {
            // Second step: Login with email and password
            String password = etPassword.getText().toString().trim();
            
            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Please enter your password");
                return;
            }
            
            if (password.length() < 6) {
                etPassword.setError("Password must be at least 6 characters");
                return;
            }
            
            btnSignIn.setEnabled(false);
            btnSignIn.setText("Loading...");
            
            authManager.login(userEmail, password, new AuthManager.AuthCallback<AuthModels.LoginResponse>() {
                @Override
                public void onSuccess(AuthModels.LoginResponse result) {
                    runOnUiThread(() -> {
                        btnSignIn.setEnabled(true);
                        if (result.isSuccess()) {
                            Toast.makeText(SignInActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                            navigateToMain();
                        } else {
                            Toast.makeText(SignInActivity.this, 
                                result.getMessage() != null ? result.getMessage() : "Login failed", 
                                Toast.LENGTH_LONG).show();
                            btnSignIn.setText("Login");
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        btnSignIn.setEnabled(true);
                        btnSignIn.setText("Login");
                        Toast.makeText(SignInActivity.this, error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }
    
    private void showPasswordField() {
        llPasswordContainer.setVisibility(View.VISIBLE);
        btnSignIn.setText("Login");
        etPassword.requestFocus();
    }
    
    private void navigateToForgotPassword() {
        Intent intent = new Intent(this, ForgotPasswordActivity.class);
        startActivity(intent);
    }
    
    private void navigateToVerifyOtp(String email, String hashedUser, boolean fromForgotPassword) {
        Intent intent = new Intent(this, VerifyOtpActivity.class);
        intent.putExtra("email", email);
        intent.putExtra("hashedUser", hashedUser);
        intent.putExtra("fromForgotPassword", fromForgotPassword);
        startActivity(intent);
    }
    
    private void navigateToMain() {
        Intent intent = new Intent(this, ScanDevicesListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    @Override
    public void onBackPressed() {
        if (isUserVerified) {
            // If password field is shown, go back to email step
            isUserVerified = false;
            userEmail = "";
            hashedUser = "";
            llPasswordContainer.setVisibility(View.GONE);
            btnSignIn.setText("Next");
            etEmail.requestFocus();
        } else {
            super.onBackPressed();
        }
    }
} 