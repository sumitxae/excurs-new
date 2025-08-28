package com.choruscoldchain.auth

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/initLogin")
    fun initLogin(@Body request: AuthModels.InitLoginRequest): Call<AuthModels.InitLoginResponse>

    @POST("auth/login")
    fun login(@Body request: AuthModels.LoginRequest): Call<AuthModels.LoginResponse>

    @POST("auth/forgotPassword")
    fun forgotPassword(@Body request: AuthModels.ForgotPasswordRequest): Call<AuthModels.ForgotPasswordResponse>

    @POST("auth/verifyOtp")
    fun verifyOtp(@Body request: AuthModels.VerifyOtpRequest): Call<AuthModels.VerifyOtpResponse>

    @POST("auth/initPassword")
    fun initPassword(@Body request: AuthModels.InitPasswordRequest): Call<AuthModels.InitPasswordResponse>

    @POST("auth/resetPassword")
    fun resetPassword(
        @Header("Authorization") authorization: String,
        @Body request: AuthModels.ResetPasswordRequest
    ): Call<AuthModels.ResetPasswordResponse>
}



