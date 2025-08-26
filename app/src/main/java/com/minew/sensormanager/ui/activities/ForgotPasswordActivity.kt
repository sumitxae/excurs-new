package com.minew.sensormanager.ui.activities

import android.os.Bundle
import android.text.TextUtils
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

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var etEmail: EditText
    private lateinit var btnSubmit: MaterialButton
    private lateinit var btnBackToLogin: MaterialButton
    private lateinit var ivChorusLogo: ImageView

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        authManager = AuthManager.getInstance(this)

        initViews()
        setupListeners()
        setupAppId()
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etEmail)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnBackToLogin = findViewById(R.id.btnBackToLogin)
        ivChorusLogo = findViewById(R.id.ivChorusLogo)
    }

    private fun setupListeners() {
        btnSubmit.setOnClickListener { handleSubmit() }
        btnBackToLogin.setOnClickListener { finish() }
    }

    private fun setupAppId() {
        val installationIdManager = InstallationIdManager.getInstance(this)
        val tvAppId = findViewById<TextView>(R.id.tv_scan_app_id)
        tvAppId?.text = "App ID: ${installationIdManager.getInstallationId()}"
    }

    private fun handleSubmit() {
        val email = etEmail.text.toString().trim()
        if (TextUtils.isEmpty(email)) {
            etEmail.error = "Please enter your email address."
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email address."
            return
        }

        etEmail.error = null
        btnSubmit.isEnabled = false
        btnSubmit.text = "Loading..."

        authManager.forgotPassword(email, object : AuthManager.AuthCallback<AuthModels.ForgotPasswordResponse> {
            override fun onSuccess(result: AuthModels.ForgotPasswordResponse) {
                runOnUiThread {
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Submit"
                    if (result.isSuccess()) {
                        Toast.makeText(this@ForgotPasswordActivity, "OTP sent to your email", Toast.LENGTH_LONG).show()
                        navigateToVerifyOtp(email, result.hashedUser, true)
                    } else {
                        Toast.makeText(this@ForgotPasswordActivity, "Failed to generate OTP. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Submit"
                    Toast.makeText(this@ForgotPasswordActivity, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun navigateToVerifyOtp(email: String, hashedUser: String?, fromForgotPassword: Boolean) {
        val intent = android.content.Intent(this, VerifyOtpActivity::class.java)
        intent.putExtra("email", email)
        intent.putExtra("hashedUser", hashedUser)
        intent.putExtra("fromForgotPassword", fromForgotPassword)
        startActivity(intent)
        finish()
    }
}


