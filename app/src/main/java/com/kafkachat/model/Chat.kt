package com.kafkachat.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey
    val id: Long,
    val name: String,
    val chatImage: String?,
    val chatType: String,
    val creatorId: Long,
    val createdAt: String
) : Serializable