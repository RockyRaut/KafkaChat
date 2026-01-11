package com.kafkachat.network
import com.kafkachat.model.User
import retrofit2.http.*
import com.google.gson.JsonObject

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body credentials: JsonObject): JsonObject

    @POST("auth/register")
    suspend fun register(@Body user: JsonObject): JsonObject

    @GET("users/{userId}")
    suspend fun getUser(@Path("userId") userId: Long): User

    @GET("users")
    suspend fun getAllUsers(): List<User>


    @GET("chats/{chatId}/messages")
    suspend fun getMessages(@Path("chatId") chatId: Long): JsonObject

    @POST("chats")
    suspend fun createChat(@Body chatData: Map<String, Long>): JsonObject
    
    @GET("chats/{chatId}")
    suspend fun getChatDetails(@Path("chatId") chatId: Long): JsonObject
}
