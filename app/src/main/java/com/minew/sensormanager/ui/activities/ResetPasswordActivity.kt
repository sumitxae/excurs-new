package com.minew.sensormanager.ui.activities

import android.os.Bundle
import android.text.TextUtils
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.minew.sensormanager.R
import com.minew.sensormanager.auth.AuthManager
import com.minew.sensormanager.auth.AuthModels
import com.minew.sensormanager.utils.InstallationIdManager

class ResetPasswordActivity : AppCompatActivity() {
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnCreatePassword: MaterialButton
    private lateinit var btnBackToLogin: MaterialButton
    private lateinit var ivNewPasswordToggle: ImageView
    private lateinit var ivConfirmPasswordToggle: ImageView

    private lateinit var authManager: AuthManager
    private lateinit var email: String
    private lateinit var accessToken: String
    private var isNewPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        email = intent.getStringExtra("email") ?: run {
            Toast.makeText(this, "Invalid access. Please try again.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        accessToken = intent.getStringExtra("accessToken") ?: run {
            Toast.makeText(this, "Invalid access. Please try again.", Toast.LENGTH_LONG).show()
            finish(); return
        }

        authManager = AuthManager.getInstance(this)

        initViews()
        setupListeners()
        setupAppId()
    }

    private fun initViews() {
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnCreatePassword = findViewById(R.id.btnCreatePassword)
        btnBackToLogin = findViewById(R.id.btnBackToLogin)
        ivNewPasswordToggle = findViewById(R.id.ivNewPasswordToggle)
        ivConfirmPasswordToggle = findViewById(R.id.ivConfirmPasswordToggle)
    }

    private fun setupListeners() {
        btnCreatePassword.setOnClickListener { handleCreatePassword() }
        btnBackToLogin.setOnClickListener { navigateToLogin() }
        ivNewPasswordToggle.setOnClickListener { toggleNewPasswordVisibility() }
        ivConfirmPasswordToggle.setOnClickListener { toggleConfirmPasswordVisibility() }
    }

    private fun setupAppId() {
        val tvAppId = findViewById<TextView>(R.id.tv_scan_app_id)
        tvAppId?.text = "App ID: ${InstallationIdManager.getInstance(this).getInstallationId()}"
    }

    private fun toggleNewPasswordVisibility() {
        if (isNewPasswordVisible) {
            etNewPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            ivNewPasswordToggle.setImageResource(R.drawable.ic_visibility_off)
            isNewPasswordVisible = false
        } else {
            etNewPassword.transformationMethod = null
            ivNewPasswordToggle.setImageResource(R.drawable.ic_visibility)
            isNewPasswordVisible = true
        }
        etNewPassword.setSelection(etNewPassword.text.length)
    }

    private fun toggleConfirmPasswordVisibility() {
        if (isConfirmPasswordVisible) {
            etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            ivConfirmPasswordToggle.setImageResource(R.drawable.ic_visibility_off)
            isConfirmPasswordVisible = false
        } else {
            etConfirmPassword.transformationMethod = null
            ivConfirmPasswordToggle.setImageResource(R.drawable.ic_visibility)
            isConfirmPasswordVisible = true
        }
        etConfirmPassword.setSelection(etConfirmPassword.text.length)
    }

    private fun handleCreatePassword() {
        val newPassword = etNewPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()
        if (TextUtils.isEmpty(newPassword)) {
            etNewPassword.error = "Please enter a new password"
            return
        }
        if (newPassword.length < 6) {
            etNewPassword.error = "Password must be at least 6 characters"
            return
        }
        if (TextUtils.isEmpty(confirmPassword)) {
            etConfirmPassword.error = "Please confirm your password"
            return
        }
        if (newPassword != confirmPassword) {
            etConfirmPassword.error = "Passwords do not match"
            return
        }
        etNewPassword.error = null
        etConfirmPassword.error = null

        btnCreatePassword.isEnabled = false
        btnCreatePassword.text = "Resetting..."

        // Persist token for reset call
        authManager.saveAuthData(accessToken, email, null)
        authManager.resetPassword(newPassword, object : AuthManager.AuthCallback<AuthModels.ResetPasswordResponse> {
            override fun onSuccess(result: AuthModels.ResetPasswordResponse) {
                runOnUiThread {
                    btnCreatePassword.isEnabled = true
                    btnCreatePassword.text = "Reset Password"
                    if (result.isSuccess()) {
                        Toast.makeText(this@ResetPasswordActivity, "Password reset successfully!", Toast.LENGTH_LONG).show()
                        navigateToLogin()
                    } else {
                        Toast.makeText(this@ResetPasswordActivity, result.message ?: "Failed to reset password", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    btnCreatePassword.isEnabled = true
                    btnCreatePassword.text = "Reset Password"
                    Toast.makeText(this@ResetPasswordActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun navigateToLogin() {
        val intent = android.content.Intent(this, SignInActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}


