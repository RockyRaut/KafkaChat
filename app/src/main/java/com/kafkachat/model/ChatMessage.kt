package com.kafkachat.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.time.LocalDateTime

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: Long,
    val senderId: Long,
    val senderUsername: String? = "Unknown", // Made nullable with default
    val senderImage: String?,
    val content: String? = "", // Made nullable with default empty string
    val mediaUrl: String?,
    val status: String = "SENT",
    val createdAt: String = "", // Will be set explicitly when creating messages
    val deliveredAt: String? = null,
    val readAt: String? = null
) : Serializable {
    // Helper function to create with current timestamp
    companion object {
        fun create(
            chatId: Long,
            senderId: Long,
            senderUsername: String? = "Unknown",
            senderImage: String? = null,
            content: String? = null,
            mediaUrl: String? = null,
            status: String = "SENT"
        ): ChatMessage {
            return ChatMessage(
                chatId = chatId,
                senderId = senderId,
                senderUsername = senderUsername,
                senderImage = senderImage,
                content = content,
                mediaUrl = mediaUrl,
                status = status,
                createdAt = LocalDateTime.now().toString()
            )
        }
    }
}