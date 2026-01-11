package com.kafkachat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kafkachat.db.AppDatabase
import com.kafkachat.model.ChatMessage
import com.kafkachat.network.WebSocketManager
import com.kafkachat.service.NotificationService
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val messageDao = database.messageDao()
    private val webSocketManager = WebSocketManager(application, Gson())

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus

    private val _currentChatId = MutableStateFlow<Long?>(null)
    val currentChatId: StateFlow<Long?> = _currentChatId
    var currentUserId: Long = 0L

    private val _typingUsers = MutableStateFlow<Set<Long>>(emptySet())
    val typingUsers: StateFlow<Set<Long>> = _typingUsers

    private val chatRepository = com.kafkachat.repository.ChatRepository(application)
    private var chatValidated = false
    private var isAppInForeground = false

    init {
        setupWebSocket()
        NotificationService.createNotificationChannel(application)
    }

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private fun setupWebSocket() {
        webSocketManager.setConnectionListener { isConnected ->
            _connectionStatus.value = isConnected
        }

        webSocketManager.setErrorListener { error ->
            viewModelScope.launch {
                android.util.Log.e("ChatViewModel", "WebSocket error: $error")
                
                // "Chat not found" is a FATAL error - chat must exist before messaging
                if (error.contains("Chat not found", ignoreCase = true)) {
                    _errorMessage.value = "Chat does not exist on server. Please go back and select the user again to create the chat."
                    chatValidated = false // Reset validation flag
                } else {
                    _errorMessage.value = error
                }
                
                // Clear error after 8 seconds
                kotlinx.coroutines.delay(8000)
                _errorMessage.value = null
            }
        }

        webSocketManager.setTypingListener { typingEvent ->
            viewModelScope.launch {
                try {
                    // Only handle typing events for current chat
                    if (typingEvent.chatId == _currentChatId.value) {
                        val currentTyping = _typingUsers.value.toMutableSet()
                        if (typingEvent.isTyping && typingEvent.userId != currentUserId) {
                            currentTyping.add(typingEvent.userId)
                        } else {
                            currentTyping.remove(typingEvent.userId)
                        }
                        _typingUsers.value = currentTyping
                        
                        // Auto-remove typing indicator after 3 seconds
                        if (typingEvent.isTyping) {
                            kotlinx.coroutines.delay(3000)
                            val updatedTyping = _typingUsers.value.toMutableSet()
                            updatedTyping.remove(typingEvent.userId)
                            _typingUsers.value = updatedTyping
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error processing typing event", e)
                }
            }
        }

        webSocketManager.setMessageListener { message ->
            viewModelScope.launch {
                try {
                    // Validate that this is a valid chat message (not a typing indicator, handshake, etc.)
                    // Skip messages without required fields
                    if (message.chatId == 0L || message.senderId == 0L) {
                        android.util.Log.d("ChatViewModel", "Skipping invalid message: chatId=${message.chatId}, senderId=${message.senderId}")
                        return@launch
                    }
                    
                    // Safely handle nullable fields from WebSocket/JSON
                    // All network data is nullable by default - validate and sanitize
                    val content = message.content
                    if (content.isNullOrBlank()) {
                        android.util.Log.d("ChatViewModel", "Skipping message with empty/null content")
                        return@launch
                    }
                    
                    // Safely handle createdAt - it might be null from JSON
                    val createdAt = message.createdAt?.takeUnless { it.isBlank() } 
                        ?: java.time.LocalDateTime.now().toString()
                    
                    // Safely handle senderUsername
                    val senderUsername = message.senderUsername?.takeUnless { it.isBlank() } 
                        ?: "Unknown"
                    
                    // Create safe message with validated fields
                    val safeMessage = message.copy(
                        senderUsername = senderUsername,
                        content = content,
                        createdAt = createdAt
                    )
                    
                    android.util.Log.d("ChatViewModel", "Received message from WebSocket: chatId=${safeMessage.chatId}, content=${content.take(20)}")
                    messageDao.insertMessage(safeMessage)
                    loadMessages(safeMessage.chatId)
                    
                    // Show notification if app is not in foreground or not viewing this chat
                    if (!isAppInForeground || _currentChatId.value != safeMessage.chatId) {
                        NotificationService.showMessageNotification(
                            getApplication(),
                            safeMessage,
                            currentUserId
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error processing received message", e)
                }
            }
        }
    }

    fun connectWebSocket(onSuccess: () -> Unit, onError: (String) -> Unit) {
        // Ensure connection listener is set before connecting
        webSocketManager.setConnectionListener { isConnected ->
            _connectionStatus.value = isConnected
            android.util.Log.d("ChatViewModel", "Connection status updated: $isConnected")
        }
        
        // Check if already connected
        if (webSocketManager.isConnected()) {
            android.util.Log.d("ChatViewModel", "WebSocket already connected")
            _connectionStatus.value = true
            onSuccess()
            return
        }
        
        android.util.Log.d("ChatViewModel", "Attempting WebSocket connection...")
        webSocketManager.connect(
            onSuccess = {
                // Verify connection after a short delay
                viewModelScope.launch {
                    kotlinx.coroutines.delay(500)
                    val actuallyConnected = webSocketManager.isConnected()
                    _connectionStatus.value = actuallyConnected
                    android.util.Log.d("ChatViewModel", "WebSocket connected successfully: $actuallyConnected")
                    if (actuallyConnected) {
                        onSuccess()
                    } else {
                        _connectionStatus.value = false
                        onError("Connection established but WebSocket is not open")
                    }
                }
            },
            onError = { error ->
                _connectionStatus.value = false
                android.util.Log.e("ChatViewModel", "WebSocket connection failed: $error")
                onError(error)
            }
        )
    }

    fun sendMessage(message: ChatMessage) {
        viewModelScope.launch {
            try {
                // Validate required fields
                if (message.chatId == 0L) {
                    android.util.Log.e("ChatViewModel", "Invalid chatId: ${message.chatId}")
                    _errorMessage.value = "Invalid chat ID"
                    return@launch
                }
                if (message.senderId == 0L) {
                    android.util.Log.e("ChatViewModel", "Invalid senderId: ${message.senderId}")
                    _errorMessage.value = "Invalid sender ID"
                    return@launch
                }
                
                // Verify chat exists on server before sending
                if (!chatValidated) {
                    val exists = chatRepository.chatExists(message.chatId)
                    if (!exists) {
                        android.util.Log.e("ChatViewModel", "Chat ${message.chatId} does not exist on server")
                        _errorMessage.value = "Chat does not exist. Please go back and select the user again."
                        return@launch
                    }
                    chatValidated = true
                    android.util.Log.d("ChatViewModel", "Chat ${message.chatId} validated on server")
                }

                // Ensure all required fields have safe defaults before inserting
                // Safely handle nullable fields - network data is nullable by default
                val content = message.content ?: ""
                val createdAt = message.createdAt?.takeUnless { it.isBlank() } 
                    ?: java.time.LocalDateTime.now().toString()
                val senderUsername = message.senderUsername?.takeUnless { it.isBlank() } 
                    ?: "Unknown"
                
                val safeMessage = message.copy(
                    senderUsername = senderUsername,
                    content = content,
                    createdAt = createdAt
                )
                
                android.util.Log.d("ChatViewModel", "Inserting message: chatId=${safeMessage.chatId}, senderId=${safeMessage.senderId}, content=${safeMessage.content?.take(20)}, createdAt=${safeMessage.createdAt}")
                
                // Ensure we're collecting messages BEFORE inserting
                if (_currentChatId.value != safeMessage.chatId || messagesJob?.isActive != true) {
                    android.util.Log.d("ChatViewModel", "Starting message collection for chatId=${safeMessage.chatId} BEFORE insert")
                    loadMessages(safeMessage.chatId)
                    // Wait a bit for collection to start
                    kotlinx.coroutines.delay(200)
                }
                
                // Get count before insert
                val countBefore = messageDao.getMessageCount(safeMessage.chatId)
                android.util.Log.d("ChatViewModel", "Messages before insert: $countBefore")
                
                // Insert message - Room Flow will automatically emit update
                messageDao.insertMessage(safeMessage)
                android.util.Log.d("ChatViewModel", "Message inserted into database")
                
                // Verify insertion by checking message count
                kotlinx.coroutines.delay(200) // Wait for database write and Flow emission
                val countAfter = messageDao.getMessageCount(safeMessage.chatId)
                android.util.Log.d("ChatViewModel", "Messages after insert: $countAfter (was $countBefore)")
                
                // Force Flow to emit by manually checking
                if (countAfter == countBefore) {
                    android.util.Log.e("ChatViewModel", "WARNING: Message count did not increase! Insert may have failed.")
                } else {
                    android.util.Log.d("ChatViewModel", "Message successfully stored. Flow should have emitted update.")
                }
                
                // Send via WebSocket after local save
                webSocketManager.sendMessage(safeMessage)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error sending message", e)
                throw e
            }
        }
    }

    private var messagesJob: kotlinx.coroutines.Job? = null

    fun loadMessages(chatId: Long) {
        _currentChatId.value = chatId
        // Cancel previous collection if any
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            try {
                android.util.Log.d("ChatViewModel", "Starting to load messages for chatId=$chatId")
                // Use distinctUntilChanged to avoid duplicate emissions
                messageDao.getMessagesByChatId(chatId)
                    .collect { messages ->
                        android.util.Log.d("ChatViewModel", "Flow emitted: ${messages.size} messages for chatId=$chatId")
                        if (messages.isNotEmpty()) {
                            android.util.Log.d("ChatViewModel", "First message: id=${messages.first().id}, content=${messages.first().content?.take(20)}, createdAt=${messages.first().createdAt}")
                            android.util.Log.d("ChatViewModel", "Last message: id=${messages.last().id}, content=${messages.last().content?.take(20)}, createdAt=${messages.last().createdAt}")
                        }
                        _messages.value = messages // Keep ASC order for RecyclerView with stackFromEnd
                    }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error loading messages", e)
                _messages.value = emptyList() // Set empty list on error
            }
        }
    }

    fun updateMessageStatus(messageId: Long, status: String) {
        viewModelScope.launch {
            messageDao.updateMessageStatus(messageId, status)
        }
    }

    fun sendTypingIndicator(isTyping: Boolean, chatId: Long, username: String?) {
        viewModelScope.launch {
            try {
                val typingEvent = com.kafkachat.model.TypingEvent(
                    chatId = chatId,
                    userId = currentUserId,
                    username = username,
                    isTyping = isTyping
                )
                webSocketManager.sendTypingEvent(typingEvent)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error sending typing indicator", e)
            }
        }
    }

    fun disconnect() {
        webSocketManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}