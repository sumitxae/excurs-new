package choruscoldchain.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.io.IOException;
import java.io.InputStream;

public class SignInActivity extends AppCompatActivity {
    private static final String TAG = "SignInActivity";
    
    private EditText etEmail, etPassword;
    private MaterialButton btnSignIn;
    private TextView tvForgotPassword, tvContactUs, tvSubtitle;
    private LinearLayout llPasswordContainer, llOptionsContainer;
    private ImageView ivPasswordToggle, ivChorusLogo;
    private CheckBox cbRememberMe;
    
    private AuthManager authManager;
    private boolean isUserVerified = false;
    private boolean isPasswordVisible = false;
    private boolean isRememberMeSelected = false;
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
        loadChorusLogo();
        checkSavedCredentials();
    }
    
    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvContactUs = findViewById(R.id.tvContactUs);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        llPasswordContainer = findViewById(R.id.llPasswordContainer);
        llOptionsContainer = findViewById(R.id.llOptionsContainer);
        ivPasswordToggle = findViewById(R.id.ivPasswordToggle);
        ivChorusLogo = findViewById(R.id.ivChorusLogo);
        cbRememberMe = findViewById(R.id.cbRememberMe);
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
        btnSignIn.setOnClickListener(v -> handleSignIn());
        tvForgotPassword.setOnClickListener(v -> navigateToForgotPassword());
        tvContactUs.setOnClickListener(v -> showContactUsDialog());
        
        // Password visibility toggle
        ivPasswordToggle.setOnClickListener(v -> togglePasswordVisibility());
        
        // Remember me checkbox
        cbRememberMe.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isRememberMeSelected = isChecked;
        });
    }
    
    private void checkSavedCredentials() {
        // Check if remember me was enabled and credentials exist
        String savedEmail = authManager.getSavedEmail();
        String savedPassword = authManager.getSavedPassword();
        boolean rememberMe = authManager.isRememberMeEnabled();
        
        if (rememberMe && !TextUtils.isEmpty(savedEmail) && !TextUtils.isEmpty(savedPassword)) {
            etEmail.setText(savedEmail);
            etPassword.setText(savedPassword);
            cbRememberMe.setChecked(true);
            isRememberMeSelected = true;
            // Auto-login with saved credentials
            handleLogin(savedEmail, savedPassword);
        }
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
        if (!isUserVerified) {
            handleEmailSubmit();
        } else {
            String password = etPassword.getText().toString().trim();
            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Please enter your password");
                return;
            }
            handleLogin(userEmail, password);
        }
    }
    
    private void handleEmailSubmit() {
        String email = etEmail.getText().toString().trim();
        
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Please enter your email");
            return;
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email");
            return;
        }
        
        setLoading(true);
        
        authManager.initLogin(email, new AuthManager.AuthCallback<AuthModels.InitLoginResponse>() {
            @Override
            public void onSuccess(AuthModels.InitLoginResponse result) {
                runOnUiThread(() -> {
                    setLoading(false);
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
                            result.getMessage() != null ? result.getMessage() : "Email is not registered please check.", 
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(SignInActivity.this, "Email is not registered please check.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void handleLogin(String email, String password) {
        setLoading(true);
        
        authManager.login(email, password, new AuthManager.AuthCallback<AuthModels.LoginResponse>() {
            @Override
            public void onSuccess(AuthModels.LoginResponse result) {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (result.isSuccess()) {
                        // Save remember me preferences
                        if (isRememberMeSelected) {
                            authManager.saveCredentials(email, password, true);
                        } else {
                            authManager.clearSavedCredentials();
                        }
                        
                        Toast.makeText(SignInActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                        navigateToMain();
                    } else {
                        Toast.makeText(SignInActivity.this, 
                            result.getMessage() != null ? result.getMessage() : "Please check email and password.", 
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(SignInActivity.this, "Please check email and password.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void setLoading(boolean loading) {
        btnSignIn.setEnabled(!loading);
        if (loading) {
            btnSignIn.setText("Loading...");
        } else {
            btnSignIn.setText(isUserVerified ? "Sign In Account" : "Next");
        }
    }
    
    private void showPasswordField() {
        llPasswordContainer.setVisibility(View.VISIBLE);
        llOptionsContainer.setVisibility(View.VISIBLE);
        tvSubtitle.setText("Please enter a valid email and password.");
        btnSignIn.setText("Sign In Account");
        etPassword.requestFocus();
    }
    
    private void showContactUsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_contact_us, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // Set dialog background and styling
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
        
        // Setup dialog views
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvContent = dialogView.findViewById(R.id.tvDialogContent);
        MaterialButton btnContact = dialogView.findViewById(R.id.btnContact);
        ImageView ivClose = dialogView.findViewById(R.id.ivClose);
        
        tvTitle.setText("Reach out to us");
        tvContent.setText("Need help? Contact us anytime at help@chorusview.com");
        
        btnContact.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:help@chorusview.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Help Request");
            startActivity(Intent.createChooser(intent, "Send email"));
            dialog.dismiss();
        });
        
        ivClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
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
            llOptionsContainer.setVisibility(View.GONE);
            tvSubtitle.setText("Please enter a valid email.");
            btnSignIn.setText("Next");
            etEmail.requestFocus();
        } else {
            super.onBackPressed();
        }
    }
} 