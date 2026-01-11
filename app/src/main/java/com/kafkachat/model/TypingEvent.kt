package com.kafkachat.model

import java.io.Serializable

data class TypingEvent(
    val chatId: Long,
    val userId: Long,
    val username: String?,
    val isTyping: Boolean
) : Serializable

