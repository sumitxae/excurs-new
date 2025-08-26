package com.minew.sensormanager.ui.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.minew.sensormanager.R
import com.minew.sensormanager.auth.AuthManager
import com.minew.sensormanager.auth.AuthModels
import com.minew.sensormanager.utils.AppIdUtils

class SignInActivity : AppCompatActivity() {
    
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var ivPasswordToggle: ImageView
    private lateinit var llPasswordContainer: View
    private lateinit var llOptionsContainer: View
    private lateinit var tvSubtitle: android.widget.TextView
    private lateinit var btnSignIn: com.google.android.material.button.MaterialButton
    
    private var isPasswordVisible = false
    private var isEmailStep = true
    private var isUserVerified = false
    private var isRememberMeSelected = false
    private var userEmail: String = ""
    private var hashedUser: String = ""

    private lateinit var authManager: AuthManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)
        
        // Force dark status bar icons on light background across APIs
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        
        initializeViews()
        setupClickListeners()
        setupTextWatchers()
        updateAppIdFooter()

        authManager = AuthManager.getInstance(this)
        // Auto-fill if remember me
        authManager.getSavedEmail()?.let { savedEmail ->
            val savedPassword = authManager.getSavedPassword()
            val remember = authManager.isRememberMeEnabled()
            if (remember && !savedEmail.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
                etEmail.setText(savedEmail)
                etPassword.setText(savedPassword)
                findViewById<android.widget.CheckBox>(R.id.cbRememberMe)?.isChecked = true
                isRememberMeSelected = true
                // Auto login
                savedPassword?.let { handleLogin(savedEmail, it) }
            }
        }
    }
    
    private fun initializeViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        ivPasswordToggle = findViewById(R.id.ivPasswordToggle)
        llPasswordContainer = findViewById(R.id.llPasswordContainer)
        llOptionsContainer = findViewById(R.id.llOptionsContainer)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        btnSignIn = findViewById(R.id.btnSignIn)
    }
    
    private fun setupClickListeners() {
        btnSignIn.setOnClickListener {
            if (!isUserVerified) {
                handleEmailSubmit()
            } else {
                val password = etPassword.text.toString().trim()
                if (password.isEmpty()) {
                    etPassword.error = "Please enter your password"
                    return@setOnClickListener
                }
                handleLogin(userEmail, password)
            }
        }
        
        ivPasswordToggle.setOnClickListener {
            togglePasswordVisibility()
        }
        
        findViewById<android.widget.TextView>(R.id.tvForgotPassword)?.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        findViewById<android.widget.CheckBox>(R.id.cbRememberMe)?.setOnCheckedChangeListener { _, isChecked ->
            isRememberMeSelected = isChecked
        }
    }
    
    private fun setupTextWatchers() {
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSignInButtonState()
            }
        })
        
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSignInButtonState()
            }
        })
    }
    
    private fun handleEmailSubmit() {
        val email = etEmail.text.toString().trim()
        if (email.isEmpty()) {
            etEmail.error = "Please enter your email"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email"
            return
        }
        setLoading(true)
        authManager.initLogin(email, object : AuthManager.AuthCallback<AuthModels.InitLoginResponse> {
            override fun onSuccess(result: AuthModels.InitLoginResponse) {
                runOnUiThread {
                    setLoading(false)
                    if (result.isSuccess()) {
                        userEmail = email
                        hashedUser = result.hashedUser ?: ""
                        if (result.isUserVerified) {
                            isUserVerified = true
                            showPasswordField()
                        } else {
                            navigateToVerifyOtp(email, hashedUser, false)
                        }
                    } else {
                        Toast.makeText(this@SignInActivity, result.getMessage(), Toast.LENGTH_LONG).show()
                    }
                }
            }
            override fun onError(error: String) {
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(this@SignInActivity, "Email is not registered please check.", Toast.LENGTH_LONG).show()
                }
            }
        })
    }
    
    private fun handleLogin(email: String, password: String) {
        setLoading(true)
        authManager.login(email, password, object : AuthManager.AuthCallback<AuthModels.LoginResponse> {
            override fun onSuccess(result: AuthModels.LoginResponse) {
                runOnUiThread {
                    setLoading(false)
                    if (result.isSuccess()) {
                        if (isRememberMeSelected) {
                            authManager.saveCredentials(email, password, true)
                        } else {
                            authManager.clearSavedCredentials()
                        }
                        navigateToMain()
                    } else {
                        Toast.makeText(this@SignInActivity, result.getMessage(), Toast.LENGTH_LONG).show()
                    }
                }
            }
            override fun onError(error: String) {
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(this@SignInActivity, "Please check email and password.", Toast.LENGTH_LONG).show()
                }
            }
        })
    }
    
    private fun setLoading(loading: Boolean) {
        btnSignIn.isEnabled = !loading
        btnSignIn.text = if (loading) "Loading..." else if (isUserVerified) "Sign In Account" else "Next"
    }

    private fun showPasswordField() {
        llPasswordContainer.visibility = View.VISIBLE
        llOptionsContainer.visibility = View.VISIBLE
        tvSubtitle.text = "Please enter a valid email and password."
        btnSignIn.text = "Sign In Account"
        etPassword.requestFocus()
    }

    private fun navigateToVerifyOtp(email: String, hashedUser: String, fromForgotPassword: Boolean) {
        val intent = Intent(this, VerifyOtpActivity::class.java)
        intent.putExtra("email", email)
        intent.putExtra("hashedUser", hashedUser)
        intent.putExtra("fromForgotPassword", fromForgotPassword)
        startActivity(intent)
    }

    private fun navigateToMain() {
        val intent = Intent(this, ScanActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        
        if (isPasswordVisible) {
            etPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility)
        } else {
            etPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            ivPasswordToggle.setImageResource(R.drawable.ic_visibility_off)
        }
        
        // Move cursor to end
        etPassword.setSelection(etPassword.text.length)
    }
    
    private fun updateSignInButtonState() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        
        if (isEmailStep) {
            btnSignIn.isEnabled = email.isNotEmpty()
        } else {
            btnSignIn.isEnabled = email.isNotEmpty() && password.isNotEmpty()
        }
    }
    
    private fun updateAppIdFooter() {
        try {
            val footerLayout = findViewById<android.view.View>(R.id.app_id_footer)
            if (footerLayout != null) {
                val appIdTextView = footerLayout.findViewById<android.widget.TextView>(R.id.tv_scan_app_id)
                if (appIdTextView != null) {
                    val installationId = AppIdUtils.getInstallationId(this)
                    appIdTextView.text = "App ID: $installationId"
                    Log.d("SignInActivity", "App ID footer updated successfully")
                } else {
                    Log.e("SignInActivity", "Could not find tv_scan_app_id TextView")
                }
            } else {
                Log.e("SignInActivity", "Could not find app_id_footer layout")
            }
        } catch (e: Exception) {
            Log.e("SignInActivity", "Error updating app ID footer", e)
        }
    }
    
    override fun onBackPressed() {
        if (isUserVerified) {
            // Go back to email step
            isUserVerified = false
            llPasswordContainer.visibility = View.GONE
            llOptionsContainer.visibility = View.GONE
            tvSubtitle.text = "Please enter a valid email."
            btnSignIn.text = "Next"
            etPassword.text.clear()
            etEmail.requestFocus()
        } else {
            super.onBackPressed()
        }
    }
}
