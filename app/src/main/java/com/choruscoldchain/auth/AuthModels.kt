package com.choruscoldchain.auth

import com.google.gson.annotations.SerializedName

object AuthModels {
    data class InitLoginRequest(
        @SerializedName("email") val email: String
    )

    data class LoginRequest(
        @SerializedName("email") val email: String,
        @SerializedName("password") val password: String
    )

    data class ForgotPasswordRequest(
        @SerializedName("email") val email: String
    )

    data class VerifyOtpRequest(
        @SerializedName("hashedUser") val hashedUser: String,
        @SerializedName("otp") val otp: String
    )

    data class InitPasswordRequest(
        @SerializedName("password") val password: String
    )

    data class ResetPasswordRequest(
        @SerializedName("newPassword") val newPassword: String
    )

    data class InitPasswordResponse(
        @SerializedName("user") val user: User?,
        @SerializedName("accessToken") val accessToken: String?
    ) {
        fun isSuccess(): Boolean = !accessToken.isNullOrEmpty()
        fun getToken(): String? = accessToken
        fun getMessage(): String = "Password initialized successfully"
    }

    data class ResetPasswordResponse(
        @SerializedName("message") val message: String?,
        @SerializedName("resetResponse") val resetResponse: ResetResponse?
    ) {
        fun isSuccess(): Boolean = message?.contains("successfully", ignoreCase = true) == true

        data class ResetResponse(
            @SerializedName("message") val message: String?
        )
    }

    data class InitLoginResponse(
        @SerializedName("hashedUser") val hashedUser: String?,
        @SerializedName("isUserVerified") val isUserVerified: Boolean
    ) {
        fun isSuccess(): Boolean = !hashedUser.isNullOrEmpty()
        fun getMessage(): String = "Login initialized successfully"
    }

    data class LoginResponse(
        @SerializedName("user") val user: User?,
        @SerializedName("accessToken") val accessToken: String?
    ) {
        fun isSuccess(): Boolean = !accessToken.isNullOrEmpty()
        fun getToken(): String? = accessToken
        fun getMessage(): String = "Login successful"
    }

    data class User(
        @SerializedName("id") val id: Int,
        @SerializedName("email") val email: String?,
        @SerializedName("firstName") val firstName: String?,
        @SerializedName("lastName") val lastName: String?,
        @SerializedName("middleInitial") val middleInitial: String?,
        @SerializedName("phoneNumber") val phoneNumber: String?,
        @SerializedName("isUserVerified") val isUserVerified: Boolean,
        @SerializedName("role") val role: String?
    ) {
        fun getFullName(): String = listOfNotNull(firstName, lastName).joinToString(" ")
    }

    data class ForgotPasswordResponse(
        @SerializedName("message") val message: String?,
        @SerializedName("hashedUser") val hashedUser: String?
    ) {
        fun isSuccess(): Boolean = !hashedUser.isNullOrEmpty()
    }

    data class VerifyOtpResponse(
        @SerializedName("message") val message: String?,
        @SerializedName("token") val token: TokenResponse?
    ) {
        fun isSuccess(): Boolean = token?.accessToken?.isNotEmpty() == true
        fun getAccessToken(): String? = token?.accessToken

        data class TokenResponse(
            @SerializedName("message") val message: String?,
            @SerializedName("access_token") val accessToken: String?
        )
    }
}



