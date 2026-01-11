package com.kafkachat.repository

import android.content.Context
import com.google.gson.JsonObject
import com.kafkachat.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(private val context: Context) {
    
    private val apiService = ApiClient.getClient(context)
    
    /**
     * Create or get existing private chat between two users.
     * Returns the chatId if successful, null otherwise.
     */
    suspend fun createOrGetPrivateChat(user1Id: Long, user2Id: Long): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val chatData = mapOf(
                    "user1Id" to user1Id,
                    "user2Id" to user2Id
                )
                
                val response = apiService.createChat(chatData)
                response.get("id")?.asLong
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Error creating chat", e)
                null
            }
        }
    }
    
    /**
     * Check if chat exists on the server
     */
    suspend fun chatExists(chatId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                apiService.getChatDetails(chatId)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}

