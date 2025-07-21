package choruscoldchain.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AuthManager {
    private static final String TAG = "AuthManager";
    private static final String PREFS_NAME = "AuthPrefs";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_ROLE = "user_role";
    
    private static AuthManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final AuthApiService apiService;
    private final Gson gson;
    
    // Base URL for your API - update this to match your backend
    private static final String BASE_URL = "http://51.21.86.14:8000/v1/";
    
    private AuthManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        
        Log.d(TAG, "Initializing AuthManager with BASE_URL: " + BASE_URL);
        
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        
        this.apiService = retrofit.create(AuthApiService.class);
        Log.d(TAG, "AuthManager initialized successfully");
    }
    
    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }
    
    public interface AuthCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
    
    public void initLogin(String email, AuthCallback<AuthModels.InitLoginResponse> callback) {
        AuthModels.InitLoginRequest request = new AuthModels.InitLoginRequest(email);
        
        apiService.initLogin(request).enqueue(new Callback<AuthModels.InitLoginResponse>() {
            @Override
            public void onResponse(Call<AuthModels.InitLoginResponse> call, Response<AuthModels.InitLoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    String errorMessage = "Failed to initialize login";
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            // Try to parse error message from response
                            try {
                                AuthModels.InitLoginResponse errorResponse = gson.fromJson(errorBody, AuthModels.InitLoginResponse.class);
                                if (errorResponse != null && errorResponse.getMessage() != null) {
                                    errorMessage = errorResponse.getMessage();
                                }
                            } catch (Exception e) {
                                // If parsing fails, use raw error body
                                errorMessage = errorBody;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error response", e);
                    }
                    callback.onError(errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<AuthModels.InitLoginResponse> call, Throwable t) {
                Log.e(TAG, "Network error during init login", t);
                Log.e(TAG, "Error details: " + t.getClass().getSimpleName() + " - " + t.getMessage());
                if (t instanceof java.net.UnknownHostException) {
                    callback.onError("Cannot connect to server. Please check your internet connection.");
                } else if (t instanceof java.net.SocketTimeoutException) {
                    callback.onError("Connection timeout. Please try again.");
                } else if (t instanceof javax.net.ssl.SSLHandshakeException) {
                    callback.onError("SSL/TLS error. Please check server configuration.");
                } else {
                    callback.onError("Network error: " + t.getMessage());
                }
            }
        });
    }
    
    public void login(String email, String password, AuthCallback<AuthModels.LoginResponse> callback) {
        AuthModels.LoginRequest request = new AuthModels.LoginRequest(email, password);
        
        apiService.login(request).enqueue(new Callback<AuthModels.LoginResponse>() {
            @Override
            public void onResponse(Call<AuthModels.LoginResponse> call, Response<AuthModels.LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthModels.LoginResponse loginResponse = response.body();
                    if (loginResponse.isSuccess()) {
                        // Save authentication data with role
                        String role = null;
                        if (loginResponse.getUser() != null) {
                            role = loginResponse.getUser().getRole();
                        }
                        saveAuthData(loginResponse.getToken(), email, role);
                    }
                    callback.onSuccess(loginResponse);
                } else {
                    String errorMessage = "Login failed";
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            try {
                                AuthModels.LoginResponse errorResponse = gson.fromJson(errorBody, AuthModels.LoginResponse.class);
                                if (errorResponse != null && errorResponse.getMessage() != null) {
                                    errorMessage = errorResponse.getMessage();
                                }
                            } catch (Exception e) {
                                // If parsing fails, use raw error body
                                errorMessage = errorBody;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error response", e);
                    }
                    callback.onError(errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<AuthModels.LoginResponse> call, Throwable t) {
                Log.e(TAG, "Network error during login", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    public void forgotPassword(String email, AuthCallback<AuthModels.ForgotPasswordResponse> callback) {
        AuthModels.ForgotPasswordRequest request = new AuthModels.ForgotPasswordRequest(email);
        
        apiService.forgotPassword(request).enqueue(new Callback<AuthModels.ForgotPasswordResponse>() {
            @Override
            public void onResponse(Call<AuthModels.ForgotPasswordResponse> call, Response<AuthModels.ForgotPasswordResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    String errorMessage = "Failed to send OTP";
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            try {
                                AuthModels.ForgotPasswordResponse errorResponse = gson.fromJson(errorBody, AuthModels.ForgotPasswordResponse.class);
                                if (errorResponse != null && errorResponse.getMessage() != null) {
                                    errorMessage = errorResponse.getMessage();
                                }
                            } catch (Exception e) {
                                // If parsing fails, use raw error body
                                errorMessage = errorBody;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error response", e);
                    }
                    callback.onError(errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<AuthModels.ForgotPasswordResponse> call, Throwable t) {
                Log.e(TAG, "Network error during forgot password", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    public void verifyOtp(String hashedUser, String otp, AuthCallback<AuthModels.VerifyOtpResponse> callback) {
        AuthModels.VerifyOtpRequest request = new AuthModels.VerifyOtpRequest(hashedUser, otp);
        
        apiService.verifyOtp(request).enqueue(new Callback<AuthModels.VerifyOtpResponse>() {
            @Override
            public void onResponse(Call<AuthModels.VerifyOtpResponse> call, Response<AuthModels.VerifyOtpResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    String errorMessage = "Invalid OTP";
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            try {
                                AuthModels.VerifyOtpResponse errorResponse = gson.fromJson(errorBody, AuthModels.VerifyOtpResponse.class);
                                if (errorResponse != null && errorResponse.getMessage() != null) {
                                    errorMessage = errorResponse.getMessage();
                                }
                            } catch (Exception e) {
                                // If parsing fails, use raw error body
                                errorMessage = errorBody;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error response", e);
                    }
                    callback.onError(errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<AuthModels.VerifyOtpResponse> call, Throwable t) {
                Log.e(TAG, "Network error during OTP verification", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    public void initPassword(String password, AuthCallback<AuthModels.InitPasswordResponse> callback) {
        AuthModels.InitPasswordRequest request = new AuthModels.InitPasswordRequest(password);
        
        apiService.initPassword(request).enqueue(new Callback<AuthModels.InitPasswordResponse>() {
            @Override
            public void onResponse(Call<AuthModels.InitPasswordResponse> call, Response<AuthModels.InitPasswordResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthModels.InitPasswordResponse initPasswordResponse = response.body();
                    if (initPasswordResponse.isSuccess()) {
                        // Save authentication data with role
                        String role = null;
                        if (initPasswordResponse.getUser() != null) {
                            role = initPasswordResponse.getUser().getRole();
                        }
                        saveAuthData(initPasswordResponse.getToken(), initPasswordResponse.getUser().getEmail(), role);
                    }
                    callback.onSuccess(initPasswordResponse);
                } else {
                    String errorMessage = "Failed to initialize password";
                    try {
                        if (response.errorBody() != null) {
                            String errorBody = response.errorBody().string();
                            // Try to parse error message from response
                            try {
                                AuthModels.InitPasswordResponse errorResponse = gson.fromJson(errorBody, AuthModels.InitPasswordResponse.class);
                                if (errorResponse != null && errorResponse.getMessage() != null) {
                                    errorMessage = errorResponse.getMessage();
                                }
                            } catch (Exception e) {
                                // If parsing fails, use raw error body
                                errorMessage = errorBody;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error response", e);
                    }
                    callback.onError(errorMessage);
                }
            }
            
            @Override
            public void onFailure(Call<AuthModels.InitPasswordResponse> call, Throwable t) {
                Log.e(TAG, "Network error during password initialization", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }
    
    public void saveAuthData(String token, String email) {
        saveAuthData(token, email, null);
    }
    
    public void saveAuthData(String token, String email, String role) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_TOKEN, token);
        editor.putString(KEY_USER_EMAIL, email);
        editor.putString(KEY_USER_ROLE, role);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();
    }
    
    public void logout() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_TOKEN);
        editor.remove(KEY_USER_EMAIL);
        editor.remove(KEY_USER_ROLE);
        editor.putBoolean(KEY_IS_LOGGED_IN, false);
        editor.apply();
    }
    
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }
    
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }
    
    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, null);
    }
    
    public String getUserRole() {
        return prefs.getString(KEY_USER_ROLE, null);
    }
} 