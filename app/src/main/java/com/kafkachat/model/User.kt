package com.kafkachat.model
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: Long,
    val username: String,
    val email: String,
    val profileImage: String?,
    val status: String?,
    val online: Boolean = false
) : Serializable
