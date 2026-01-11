package com.kafkachat.repository

import android.content.Context
import com.kafkachat.db.UserDao
import com.kafkachat.model.User
import com.kafkachat.network.ApiClient
import kotlinx.coroutines.flow.Flow

class UserRepository(
    private val context: Context,
    private val userDao: UserDao
) {
    private val apiService = ApiClient.getClient(context)

    suspend fun fetchAndCacheUser(userId: Long): User? {
        return try {
            val user = apiService.getUser(userId)
            userDao.insertUser(user)
            user
        } catch (e: Exception) {
            userDao.getUserById(userId)
        }
    }

    suspend fun fetchAllUsers(): List<User> {
        return try {
            val users = apiService.getAllUsers()
            // Cache all users
            users.forEach { user ->
                userDao.insertUser(user)
            }
            users
        } catch (e: Exception) {
            // Return empty list if API fails - cached users will be observed via Flow
            emptyList()
        }
    }

    fun observeUsers(): Flow<List<User>> = userDao.getAllUsers()
}