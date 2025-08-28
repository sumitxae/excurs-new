package com.choruscoldchain.ui.activities

import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.choruscoldchain.R
import com.choruscoldchain.auth.AuthManager
import com.choruscoldchain.auth.AuthModels
import com.choruscoldchain.utils.InstallationIdManager

class VerifyOtpActivity : AppCompatActivity() {
    private lateinit var etOtp1: EditText
    private lateinit var etOtp2: EditText
    private lateinit var etOtp3: EditText
    private lateinit var etOtp4: EditText
    private lateinit var btnVerify: MaterialButton
    private lateinit var btnBackToLogin: MaterialButton
    private lateinit var tvResendOtp: TextView
    private lateinit var ivChorusLogo: ImageView

    private lateinit var authManager: AuthManager
    private lateinit var email: String
    private lateinit var hashedUser: String
    private var fromForgotPassword: Boolean = false
    private var timer: CountDownTimer? = null
    private var timeLeft = 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_otp)

        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        email = intent.getStringExtra("email") ?: run {
            Toast.makeText(this, "Invalid access. Please try again.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        hashedUser = intent.getStringExtra("hashedUser") ?: run {
            Toast.makeText(this, "Invalid access. Please try again.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        fromForgotPassword = intent.getBooleanExtra("fromForgotPassword", false)

        authManager = AuthManager.getInstance(this)

        initViews()
        setupListeners()
        setupAppId()
        startTimer()
        etOtp1.requestFocus()
    }

    private fun initViews() {
        etOtp1 = findViewById(R.id.etOtp1)
        etOtp2 = findViewById(R.id.etOtp2)
        etOtp3 = findViewById(R.id.etOtp3)
        etOtp4 = findViewById(R.id.etOtp4)
        btnVerify = findViewById(R.id.btnVerify)
        btnBackToLogin = findViewById(R.id.btnBackToLogin)
        tvResendOtp = findViewById(R.id.tvResendOtp)
        ivChorusLogo = findViewById(R.id.ivChorusLogo)
    }

    private fun setupAppId() {
        val tvAppId = findViewById<TextView>(R.id.tv_scan_app_id)
        tvAppId?.text = "App ID: ${InstallationIdManager.getInstance(this).getInstallationId()}"
    }

    private fun setupListeners() {
        btnVerify.setOnClickListener { handleVerify() }
        btnBackToLogin.setOnClickListener { finish() }
        tvResendOtp.setOnClickListener { handleResendOtp() }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 1) {
                    when {
                        etOtp1.hasFocus() -> etOtp2.requestFocus()
                        etOtp2.hasFocus() -> etOtp3.requestFocus()
                        etOtp3.hasFocus() -> etOtp4.requestFocus()
                    }
                } else if (s?.isEmpty() == true) {
                    when {
                        etOtp2.hasFocus() -> etOtp1.requestFocus()
                        etOtp3.hasFocus() -> etOtp2.requestFocus()
                        etOtp4.hasFocus() -> etOtp3.requestFocus()
                    }
                }
            }
        }

        etOtp1.addTextChangedListener(watcher)
        etOtp2.addTextChangedListener(watcher)
        etOtp3.addTextChangedListener(watcher)
        etOtp4.addTextChangedListener(watcher)
    }

    private fun handleVerify() {
        val fullOtp = listOf(etOtp1, etOtp2, etOtp3, etOtp4).joinToString("") { it.text.toString() }
        if (fullOtp.length != 4) {
            Toast.makeText(this, "Please enter the complete 4-digit OTP.", Toast.LENGTH_LONG).show()
            return
        }
        btnVerify.isEnabled = false
        btnVerify.text = "Loading..."

        authManager.verifyOtp(hashedUser.lowercase(), fullOtp, object : AuthManager.AuthCallback<AuthModels.VerifyOtpResponse> {
            override fun onSuccess(result: AuthModels.VerifyOtpResponse) {
                runOnUiThread {
                    btnVerify.isEnabled = true
                    btnVerify.text = "Verify"
                    if (result.isSuccess()) {
                        val accessToken = result.getAccessToken()
                        if (!accessToken.isNullOrEmpty()) {
                            authManager.saveAuthData(accessToken, email, null)
                        }
                        Toast.makeText(this@VerifyOtpActivity, "OTP Verified!", Toast.LENGTH_SHORT).show()
                        navigateToResetPassword()
                    } else {
                        Toast.makeText(this@VerifyOtpActivity, "Invalid OTP. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    btnVerify.isEnabled = true
                    btnVerify.text = "Verify"
                    Toast.makeText(this@VerifyOtpActivity, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun handleResendOtp() {
        if (timeLeft > 0) return
        btnVerify.isEnabled = false
        btnVerify.text = "Loading..."
        authManager.forgotPassword(email, object : AuthManager.AuthCallback<AuthModels.ForgotPasswordResponse> {
            override fun onSuccess(result: AuthModels.ForgotPasswordResponse) {
                runOnUiThread {
                    btnVerify.isEnabled = true
                    btnVerify.text = "Verify"
                    if (result.isSuccess()) {
                        Toast.makeText(this@VerifyOtpActivity, "OTP has been sent to your email.", Toast.LENGTH_LONG).show()
                        hashedUser = result.hashedUser ?: hashedUser
                        clearOtpFields()
                        startTimer()
                        etOtp1.requestFocus()
                    } else {
                        Toast.makeText(this@VerifyOtpActivity, "Failed to generate OTP. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    btnVerify.isEnabled = true
                    btnVerify.text = "Verify"
                    Toast.makeText(this@VerifyOtpActivity, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun startTimer() {
        timeLeft = 60
        tvResendOtp.text = "Resend OTP in (60s)"
        tvResendOtp.isClickable = false
        timer?.cancel()
        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = (millisUntilFinished / 1000).toInt()
                tvResendOtp.text = "Resend OTP in (${timeLeft}s)"
            }
            override fun onFinish() {
                timeLeft = 0
                tvResendOtp.text = "Resend OTP"
                tvResendOtp.isClickable = true
            }
        }.start()
    }

    private fun clearOtpFields() {
        etOtp1.setText("")
        etOtp2.setText("")
        etOtp3.setText("")
        etOtp4.setText("")
    }

    private fun navigateToResetPassword() {
        val intent = android.content.Intent(this, ResetPasswordActivity::class.java)
        intent.putExtra("email", email)
        intent.putExtra("accessToken", authManager.getToken())
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}


