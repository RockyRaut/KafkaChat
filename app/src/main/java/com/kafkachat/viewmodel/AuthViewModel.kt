package com.kafkachat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.kafkachat.network.ApiClient
import com.kafkachat.util.Constants
import com.kafkachat.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AuthResult(
    val success: Boolean,
    val message: String? = null,
    val token: String? = null,
    val userId: Long? = null,
    val username: String? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    
    private val apiService = ApiClient.getClient(application)
    private val preferenceManager = PreferenceManager(application)
    
    private val _authState = MutableStateFlow<AuthResult?>(null)
    val authState: StateFlow<AuthResult?> = _authState
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthResult(
                success = false,
                message = "Email and password are required"
            )
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val credentials = JsonObject().apply {
                    addProperty("email", email.trim())
                    addProperty("password", password)
                }
                
                val response = apiService.login(credentials)
                
                val token = response.get("token")?.asString
                val userId = response.get("userId")?.asLong
                val username = response.get("username")?.asString
                
                if (token != null && userId != null) {
                    // Save auth data
                    preferenceManager.putLong(Constants.PREF_USER_ID, userId)
                    preferenceManager.putString(Constants.PREF_USERNAME, username ?: "User")
                    preferenceManager.putString("auth_token", token)
                    
                    // Update API client with token
                    ApiClient.reset()
                    ApiClient.getClient(getApplication(), token)
                    
                    _authState.value = AuthResult(
                        success = true,
                        message = "Login successful",
                        token = token,
                        userId = userId,
                        username = username
                    )
                } else {
                    _authState.value = AuthResult(
                        success = false,
                        message = "Invalid response from server"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Login error", e)
                
                // Try to extract error message from HTTP response
                val errorMessage = when {
                    e is HttpException -> {
                        val errorBody = e.response()?.errorBody()?.string()
                        android.util.Log.e("AuthViewModel", "HTTP ${e.code()}: $errorBody")
                        when (e.code()) {
                            401 -> "Invalid email or password"
                            404 -> "User not found"
                            500 -> "Server error. Please try again later or contact support."
                            else -> errorBody ?: "Login failed (HTTP ${e.code()})"
                        }
                    }
                    e.message?.contains("Unable to resolve host") == true -> 
                        "Network error. Please check your internet connection."
                    e.message?.contains("timeout") == true -> 
                        "Connection timeout. Please try again."
                    else -> e.message ?: "Login failed. Please check your credentials."
                }
                
                _authState.value = AuthResult(
                    success = false,
                    message = errorMessage
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            _authState.value = AuthResult(
                success = false,
                message = "All fields are required"
            )
            return
        }
        
        // Basic email validation
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            _authState.value = AuthResult(
                success = false,
                message = "Please enter a valid email address"
            )
            return
        }
        
        // Password length validation
        if (password.length < 3) {
            _authState.value = AuthResult(
                success = false,
                message = "Password must be at least 3 characters"
            )
            return
        }
        
        // Username validation
        if (username.trim().length < 2) {
            _authState.value = AuthResult(
                success = false,
                message = "Username must be at least 2 characters"
            )
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userData = JsonObject().apply {
                    addProperty("username", username.trim())
                    addProperty("email", email.trim())
                    addProperty("password", password)
                }
                
                val response = apiService.register(userData)
                
                val token = response.get("token")?.asString
                val userId = response.get("userId")?.asLong
                val usernameResponse = response.get("username")?.asString
                
                if (token != null && userId != null) {
                    // Save auth data
                    preferenceManager.putLong(Constants.PREF_USER_ID, userId)
                    preferenceManager.putString(Constants.PREF_USERNAME, usernameResponse ?: username)
                    preferenceManager.putString("auth_token", token)
                    
                    // Update API client with token
                    ApiClient.reset()
                    ApiClient.getClient(getApplication(), token)
                    
                    _authState.value = AuthResult(
                        success = true,
                        message = "Registration successful",
                        token = token,
                        userId = userId,
                        username = usernameResponse ?: username
                    )
                } else {
                    _authState.value = AuthResult(
                        success = false,
                        message = "Invalid response from server"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Register error", e)
                
                // Try to extract error message from HTTP response
                val errorMessage = when {
                    e is HttpException -> {
                        val errorBody = try {
                            e.response()?.errorBody()?.string()
                        } catch (ex: Exception) {
                            null
                        }
                        android.util.Log.e("AuthViewModel", "HTTP ${e.code()}: $errorBody")
                        
                        // Try to parse error details from response
                        val detailedError = errorBody?.let { body ->
                            try {
                                val errorJson = com.google.gson.JsonParser.parseString(body).asJsonObject
                                errorJson.get("message")?.asString 
                                    ?: errorJson.get("error")?.asString
                                    ?: null
                            } catch (ex: Exception) {
                                null
                            }
                        }
                        
                        when (e.code()) {
                            400 -> detailedError ?: "Invalid registration data. Please check your input."
                            409 -> "Email already in use. Please use a different email."
                            500 -> detailedError ?: "Server error. Please check:\n1. Backend is running\n2. Database is accessible\n3. Try again later"
                            else -> detailedError ?: errorBody ?: "Registration failed (HTTP ${e.code()})"
                        }
                    }
                    e.message?.contains("Unable to resolve host") == true -> 
                        "Network error. Please check your internet connection and server URL."
                    e.message?.contains("timeout") == true -> 
                        "Connection timeout. Please try again."
                    else -> e.message ?: "Registration failed. Please check your input and try again."
                }
                
                _authState.value = AuthResult(
                    success = false,
                    message = errorMessage
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkAuthStatus(): Boolean {
        val token = preferenceManager.getString("auth_token", null)
        val userId = preferenceManager.getLong(Constants.PREF_USER_ID, 0)
        return !token.isNullOrBlank() && userId != 0L
    }

    fun logout() {
        preferenceManager.putString("auth_token", null)
        preferenceManager.putLong(Constants.PREF_USER_ID, 0)
        preferenceManager.putString(Constants.PREF_USERNAME, null)
        ApiClient.reset()
        _authState.value = null
    }
}

