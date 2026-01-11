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

class UserViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getInstance(application)
    private val userDao = database.userDao()
    private val userRepository = UserRepository(application, userDao)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    fun loadUser(userId: Long) {
        viewModelScope.launch {
            _currentUser.value = userRepository.fetchAndCacheUser(userId)
        }
    }
}
