package com.minew.mst03demo;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApiService {
    
    @POST("auth/initLogin")
    Call<AuthModels.InitLoginResponse> initLogin(@Body AuthModels.InitLoginRequest request);
    
    @POST("auth/login")
    Call<AuthModels.LoginResponse> login(@Body AuthModels.LoginRequest request);
    
    @POST("auth/forgotPassword")
    Call<AuthModels.ForgotPasswordResponse> forgotPassword(@Body AuthModels.ForgotPasswordRequest request);
    
    @POST("auth/verifyOtp")
    Call<AuthModels.VerifyOtpResponse> verifyOtp(@Body AuthModels.VerifyOtpRequest request);
    
    @POST("auth/initPassword")
    Call<AuthModels.InitPasswordResponse> initPassword(@Body AuthModels.InitPasswordRequest request);
} 