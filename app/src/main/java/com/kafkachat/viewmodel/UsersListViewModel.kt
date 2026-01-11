package com.kafkachat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kafkachat.db.AppDatabase
import com.kafkachat.model.User
import com.kafkachat.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UsersListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getInstance(application)
    private val userDao = database.userDao()
    private val userRepository = UserRepository(application, userDao)

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        // Observe cached users for real-time updates
        viewModelScope.launch {
            userRepository.observeUsers().collect { cachedUsers ->
                if (cachedUsers.isNotEmpty()) {
                    _users.value = cachedUsers
                }
            }
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Fetch from API and cache
                val apiUsers = userRepository.fetchAllUsers()
                _users.value = apiUsers
            } catch (e: Exception) {
                android.util.Log.e("UsersListViewModel", "Error loading users", e)
                _error.value = "Failed to load users: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

