package com.minew.mst03demo;

import com.google.gson.annotations.SerializedName;

public class AuthModels {
    
    // Request Models
    public static class InitLoginRequest {
        @SerializedName("email")
        private String email;
        
        public InitLoginRequest(String email) {
            this.email = email;
        }
        
        public String getEmail() {
            return email;
        }
    }
    
    public static class LoginRequest {
        @SerializedName("email")
        private String email;
        
        @SerializedName("password")
        private String password;
        
        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
        
        public String getEmail() {
            return email;
        }
        
        public String getPassword() {
            return password;
        }
    }
    
    public static class ForgotPasswordRequest {
        @SerializedName("email")
        private String email;
        
        public ForgotPasswordRequest(String email) {
            this.email = email;
        }
        
        public String getEmail() {
            return email;
        }
    }
    
    public static class VerifyOtpRequest {
        @SerializedName("hashedUser")
        private String hashedUser;
        
        @SerializedName("otp")
        private String otp;
        
        public VerifyOtpRequest(String hashedUser, String otp) {
            this.hashedUser = hashedUser;
            this.otp = otp;
        }
        
        public String getHashedUser() {
            return hashedUser;
        }
        
        public String getOtp() {
            return otp;
        }
    }
    
    public static class InitPasswordRequest {
        @SerializedName("password")
        private String password;
        
        public InitPasswordRequest(String password) {
            this.password = password;
        }
        
        public String getPassword() {
            return password;
        }
    }
    
    public static class InitPasswordResponse {
        @SerializedName("user")
        private User user;
        
        @SerializedName("accessToken")
        private String accessToken;
        
        public boolean isSuccess() {
            return accessToken != null && !accessToken.isEmpty();
        }
        
        public String getToken() {
            return accessToken;
        }
        
        public User getUser() {
            return user;
        }
        
        public String getMessage() {
            return "Password initialized successfully";
        }
    }
    
    // Response Models
    public static class InitLoginResponse {
        @SerializedName("hashedUser")
        private String hashedUser;
        
        @SerializedName("isUserVerified")
        private boolean isUserVerified;
        
        public boolean isSuccess() {
            return hashedUser != null && !hashedUser.isEmpty();
        }
        
        public boolean isUserVerified() {
            return isUserVerified;
        }
        
        public String getHashedUser() {
            return hashedUser;
        }
        
        public String getMessage() {
            return "Login initialized successfully";
        }
    }
    
    public static class LoginResponse {
        @SerializedName("user")
        private User user;
        
        @SerializedName("accessToken")
        private String accessToken;
        
        public boolean isSuccess() {
            return accessToken != null && !accessToken.isEmpty();
        }
        
        public String getToken() {
            return accessToken;
        }
        
        public User getUser() {
            return user;
        }
        
        public String getMessage() {
            return "Login successful";
        }
    }
    
    public static class User {
        @SerializedName("id")
        private int id;
        
        @SerializedName("email")
        private String email;
        
        @SerializedName("firstName")
        private String firstName;
        
        @SerializedName("lastName")
        private String lastName;
        
        @SerializedName("middleInitial")
        private String middleInitial;
        
        @SerializedName("phoneNumber")
        private String phoneNumber;
        
        @SerializedName("isUserVerified")
        private boolean isUserVerified;
        
        @SerializedName("role")
        private String role;
        
        public int getId() {
            return id;
        }
        
        public String getEmail() {
            return email;
        }
        
        public String getFirstName() {
            return firstName;
        }
        
        public String getLastName() {
            return lastName;
        }
        
        public String getMiddleInitial() {
            return middleInitial;
        }
        
        public String getPhoneNumber() {
            return phoneNumber;
        }
        
        public boolean isUserVerified() {
            return isUserVerified;
        }
        
        public String getRole() {
            return role;
        }
        
        public String getFullName() {
            return firstName + " " + lastName;
        }
    }
    
    public static class ForgotPasswordResponse {
        @SerializedName("message")
        private String message;
        
        @SerializedName("hashedUser")
        private String hashedUser;
        
        public boolean isSuccess() {
            return hashedUser != null && !hashedUser.isEmpty();
        }
        
        public String getHashedUser() {
            return hashedUser;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    public static class VerifyOtpResponse {
        @SerializedName("message")
        private String message;
        
        @SerializedName("token")
        private TokenResponse token;
        
        public boolean isSuccess() {
            return token != null && token.getAccessToken() != null;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getAccessToken() {
            return token != null ? token.getAccessToken() : null;
        }
        
        public static class TokenResponse {
            @SerializedName("message")
            private String message;
            
            @SerializedName("access_token")
            private String accessToken;
            
            public String getMessage() {
                return message;
            }
            
            public String getAccessToken() {
                return accessToken;
            }
        }
    }
    

} 