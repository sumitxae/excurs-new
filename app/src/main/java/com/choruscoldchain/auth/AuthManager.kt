package com.choruscoldchain.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthManager private constructor(private val appContext: Context) {
    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_NAME = "AuthPrefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_SAVED_EMAIL = "saved_email"
        private const val KEY_SAVED_PASSWORD = "saved_password"
        private const val KEY_REMEMBER_ME = "remember_me"

        // TODO: replace with BuildConfig base URL if available
        private const val BASE_URL = "http://34.61.53.179:8000/v1/"

        @Volatile private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager =
            instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
    }

    interface AuthCallback<T> {
        fun onSuccess(result: T)
        fun onError(error: String)
    }

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val apiService: AuthApiService

    init {
        Log.d(TAG, "Initializing AuthManager with BASE_URL: $BASE_URL")
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        apiService = retrofit.create(AuthApiService::class.java)
    }

    fun initLogin(email: String, callback: AuthCallback<AuthModels.InitLoginResponse>) {
        val request = AuthModels.InitLoginRequest(email)
        apiService.initLogin(request).enqueue(object : Callback<AuthModels.InitLoginResponse> {
            override fun onResponse(
                call: Call<AuthModels.InitLoginResponse>,
                response: Response<AuthModels.InitLoginResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    callback.onSuccess(response.body()!!)
                } else {
                    callback.onError(parseError(response))
                }
            }

            override fun onFailure(call: Call<AuthModels.InitLoginResponse>, t: Throwable) {
                callback.onError(networkError(t))
            }
        })
    }

    fun login(email: String, password: String, callback: AuthCallback<AuthModels.LoginResponse>) {
        val request = AuthModels.LoginRequest(email, password)
        apiService.login(request).enqueue(object : Callback<AuthModels.LoginResponse> {
            override fun onResponse(
                call: Call<AuthModels.LoginResponse>,
                response: Response<AuthModels.LoginResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.isSuccess()) {
                        saveAuthData(body.getToken(), email, body.user?.role)
                    }
                    callback.onSuccess(body)
                } else {
                    callback.onError(parseError(response))
                }
            }

            override fun onFailure(call: Call<AuthModels.LoginResponse>, t: Throwable) {
                callback.onError(networkError(t))
            }
        })
    }

    fun forgotPassword(email: String, callback: AuthCallback<AuthModels.ForgotPasswordResponse>) {
        val request = AuthModels.ForgotPasswordRequest(email)
        apiService.forgotPassword(request).enqueue(object : Callback<AuthModels.ForgotPasswordResponse> {
            override fun onResponse(
                call: Call<AuthModels.ForgotPasswordResponse>,
                response: Response<AuthModels.ForgotPasswordResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    callback.onSuccess(response.body()!!)
                } else {
                    callback.onError(parseError(response))
                }
            }

            override fun onFailure(call: Call<AuthModels.ForgotPasswordResponse>, t: Throwable) {
                callback.onError(networkError(t))
            }
        })
    }

    fun verifyOtp(hashedUser: String, otp: String, callback: AuthCallback<AuthModels.VerifyOtpResponse>) {
        val request = AuthModels.VerifyOtpRequest(hashedUser, otp)
        apiService.verifyOtp(request).enqueue(object : Callback<AuthModels.VerifyOtpResponse> {
            override fun onResponse(
                call: Call<AuthModels.VerifyOtpResponse>,
                response: Response<AuthModels.VerifyOtpResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    callback.onSuccess(response.body()!!)
                } else {
                    callback.onError(parseError(response))
                }
            }

            override fun onFailure(call: Call<AuthModels.VerifyOtpResponse>, t: Throwable) {
                callback.onError(networkError(t))
            }
        })
    }

    fun initPassword(password: String, callback: AuthCallback<AuthModels.InitPasswordResponse>) {
        val request = AuthModels.InitPasswordRequest(password)
        apiService.initPassword(request).enqueue(object : Callback<AuthModels.InitPasswordResponse> {
            override fun onResponse(
                call: Call<AuthModels.InitPasswordResponse>,
                response: Response<AuthModels.InitPasswordResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.isSuccess()) {
                        val role = body.user?.role
                        saveAuthData(body.getToken(), body.user?.email, role)
                    }
                    callback.onSuccess(body)
                } else {
                    callback.onError(parseError(response))
                }
            }

            override fun onFailure(call: Call<AuthModels.InitPasswordResponse>, t: Throwable) {
                callback.onError(networkError(t))
            }
        })
    }

    fun resetPassword(newPassword: String, callback: AuthCallback<AuthModels.ResetPasswordResponse>) {
        val token = getToken()
        if (token.isNullOrEmpty()) {
            callback.onError("No authentication token available")
            return
        }
        val request = AuthModels.ResetPasswordRequest(newPassword)
        val authorization = "Bearer $token"
        apiService.resetPassword(authorization, request).enqueue(object : Callback<AuthModels.ResetPasswordResponse> {
            override fun onResponse(
                call: Call<AuthModels.ResetPasswordResponse>,
                response: Response<AuthModels.ResetPasswordResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    callback.onSuccess(response.body()!!)
                } else {
                    callback.onError(parseError(response))
                }
            }

            override fun onFailure(call: Call<AuthModels.ResetPasswordResponse>, t: Throwable) {
                callback.onError(networkError(t))
            }
        })
    }

    private fun parseError(response: Response<*>): String {
        return try {
            val raw = response.errorBody()?.string()
            raw ?: "Request failed with status ${response.code()}"
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing error response", e)
            "Request failed with status ${response.code()}"
        }
    }

    private fun networkError(t: Throwable): String {
        Log.e(TAG, "Network error", t)
        return when (t) {
            is java.net.UnknownHostException -> "Cannot connect to server. Please check your internet connection."
            is java.net.SocketTimeoutException -> "Connection timeout. Please try again."
            is javax.net.ssl.SSLHandshakeException -> "SSL/TLS error. Please check server configuration."
            else -> "Network error: ${t.message}"
        }
    }

    fun saveAuthData(token: String?, email: String?, role: String?) {
        val editor = prefs.edit()
        editor.putString(KEY_TOKEN, token)
        editor.putString(KEY_USER_EMAIL, email)
        editor.putString(KEY_USER_ROLE, role)
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()
    }

    fun saveCredentials(email: String, password: String, rememberMe: Boolean) {
        val editor = prefs.edit()
        if (rememberMe) {
            editor.putString(KEY_SAVED_EMAIL, email)
            editor.putString(KEY_SAVED_PASSWORD, password)
            editor.putBoolean(KEY_REMEMBER_ME, true)
        } else {
            editor.remove(KEY_SAVED_EMAIL)
            editor.remove(KEY_SAVED_PASSWORD)
            editor.putBoolean(KEY_REMEMBER_ME, false)
        }
        editor.apply()
    }

    fun clearSavedCredentials() {
        val editor = prefs.edit()
        editor.remove(KEY_SAVED_EMAIL)
        editor.remove(KEY_SAVED_PASSWORD)
        editor.putBoolean(KEY_REMEMBER_ME, false)
        editor.apply()
    }

    fun getSavedEmail(): String? = prefs.getString(KEY_SAVED_EMAIL, null)
    fun getSavedPassword(): String? = prefs.getString(KEY_SAVED_PASSWORD, null)
    fun isRememberMeEnabled(): Boolean = prefs.getBoolean(KEY_REMEMBER_ME, false)
    fun logout() {
        val editor = prefs.edit()
        editor.remove(KEY_TOKEN)
        editor.remove(KEY_USER_EMAIL)
        editor.remove(KEY_USER_ROLE)
        editor.putBoolean(KEY_IS_LOGGED_IN, false)
        editor.remove(KEY_SAVED_EMAIL)
        editor.remove(KEY_SAVED_PASSWORD)
        editor.putBoolean(KEY_REMEMBER_ME, false)
        editor.apply()
    }
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)
    fun getUserRole(): String? = prefs.getString(KEY_USER_ROLE, null)
}



