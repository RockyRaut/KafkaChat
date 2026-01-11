package com.kafkachat.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.kafkachat.databinding.ActivityChatBinding
import com.kafkachat.model.ChatMessage
import com.kafkachat.ui.activity.adapter.MessageAdapter
import com.kafkachat.util.Constants
import com.kafkachat.util.PreferenceManager
import com.kafkachat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var preferenceManager: PreferenceManager

    private var chatId: Long = 0
    private var currentUserId: Long = 0
    private var recipientId: Long = 0
    private var selectedMediaUri: Uri? = null

    private val imagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openImagePicker()
        } else {
            Snackbar.make(binding.root, "Permission needed to select images", Snackbar.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            android.util.Log.d("ChatActivity", "Notification permission denied")
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedMediaUri = it
            // For now, we'll just send the URI as a string
            // In production, you'd upload the image to a server and get a URL
            sendMessage(mediaUrl = it.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)
        currentUserId = preferenceManager.getLong(Constants.PREF_USER_ID, 0)
        viewModel.currentUserId = currentUserId
        chatId = intent.getLongExtra(Constants.EXTRA_CHAT_ID, 0)
        recipientId = intent.getLongExtra(Constants.EXTRA_RECIPIENT_ID, 0)
        
        // Request notification permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Mark app as in foreground when chat is visible
        viewModel.setAppInForeground(true)

        // If chatId is 0, create a default chatId (for testing)
        if (chatId == 0L) {
            chatId = 1L // Default chat ID for testing
            android.util.Log.w("ChatActivity", "chatId was 0, using default: $chatId")
        }

        // If currentUserId is 0, set a default (for testing)
        if (currentUserId == 0L) {
            currentUserId = 1L // Default user ID for testing
            viewModel.currentUserId = currentUserId
            preferenceManager.putLong(Constants.PREF_USER_ID, currentUserId)
            android.util.Log.w("ChatActivity", "currentUserId was 0, using default: $currentUserId")
        }

        android.util.Log.d("ChatActivity", "Initialized: chatId=$chatId, currentUserId=$currentUserId, recipientId=$recipientId")

        setupToolbar()
        setupUI()
        setupWebSocket()
        validateAndLoadChat()
    }

    companion object {
        fun createIntent(context: android.content.Context, chatId: Long, recipientId: Long) =
            android.content.Intent(context, ChatActivity::class.java).apply {
                putExtra(Constants.EXTRA_CHAT_ID, chatId)
                putExtra(Constants.EXTRA_RECIPIENT_ID, recipientId)
            }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Chat"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupUI() {
        messageAdapter = MessageAdapter(currentUserId)
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
            reverseLayout = false
        }
        binding.messagesRecyclerView.apply {
            this.layoutManager = layoutManager
            adapter = messageAdapter
            // Ensure RecyclerView is visible
            visibility = View.VISIBLE
        }
        
        // Post to ensure layout is complete
        binding.messagesRecyclerView.post {
            android.util.Log.d("ChatActivity", "RecyclerView after layout - Visibility: ${binding.messagesRecyclerView.visibility}, " +
                    "Height: ${binding.messagesRecyclerView.height}, " +
                    "MeasuredHeight: ${binding.messagesRecyclerView.measuredHeight}, " +
                    "Adapter itemCount: ${messageAdapter.itemCount}")
            
            // Force a layout pass
            binding.messagesRecyclerView.requestLayout()
        }

        // Send button
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        // Media button
        binding.mediaButton.setOnClickListener {
            checkImagePermissionAndPick()
        }

        // Send on Enter key
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // Typing indicator - send typing events when user types
        var typingJob: kotlinx.coroutines.Job? = null
        binding.messageInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Cancel previous typing job
                typingJob?.cancel()
                
                // Send typing indicator
                val senderName = preferenceManager.getString(Constants.PREF_USERNAME, "User") ?: "User"
                viewModel.sendTypingIndicator(true, chatId, senderName)
                
                // Auto-stop typing after 2 seconds of no typing
                typingJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(2000)
                    viewModel.sendTypingIndicator(false, chatId, senderName)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Auto-scroll when new messages arrive
        messageAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == messageAdapter.itemCount - 1) {
                    binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
                }
            }
        })
    }

    private fun setupWebSocket() {
        viewModel.connectWebSocket(
            onSuccess = {
                updateConnectionStatus(true)
            },
            onError = { error ->
                updateConnectionStatus(false)
                showError("Connection failed: $error")
            }
        )

        lifecycleScope.launch {
            viewModel.connectionStatus.collect { isConnected ->
                updateConnectionStatus(isConnected)
            }
        }

        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                android.util.Log.d("ChatActivity", "Received ${messages.size} messages in UI")
                val previousSize = messageAdapter.itemCount
                messageAdapter.submitList(messages) {
                    android.util.Log.d("ChatActivity", "Adapter updated with ${messages.size} messages, previous size: $previousSize")
                    android.util.Log.d("ChatActivity", "RecyclerView visibility: ${binding.messagesRecyclerView.visibility}, height: ${binding.messagesRecyclerView.height}")
                    android.util.Log.d("ChatActivity", "Adapter itemCount: ${messageAdapter.itemCount}")
                    
                    // Force layout and scroll to bottom if new messages were added
                    if (messages.isNotEmpty()) {
                        binding.messagesRecyclerView.post {
                            // Ensure RecyclerView is visible and has proper dimensions
                            binding.messagesRecyclerView.visibility = View.VISIBLE
                            android.util.Log.d("ChatActivity", "Scrolling to position ${messages.size - 1}, RecyclerView height: ${binding.messagesRecyclerView.height}")
                            
                            // Scroll to bottom
                            binding.messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                            
                            // Also try scrollToPosition as fallback
                            binding.messagesRecyclerView.postDelayed({
                                binding.messagesRecyclerView.scrollToPosition(messages.size - 1)
                            }, 100)
                        }
                    } else {
                        android.util.Log.w("ChatActivity", "No messages to display!")
                    }
                }
            }
        }

        // Observe typing indicators
        lifecycleScope.launch {
            viewModel.typingUsers.collect { typingUserIds ->
                if (typingUserIds.isNotEmpty() && typingUserIds.contains(recipientId)) {
                    binding.tvTypingIndicator.text = "User is typing..."
                    binding.tvTypingIndicator.visibility = View.VISIBLE
                } else {
                    binding.tvTypingIndicator.visibility = View.GONE
                }
            }
        }

        // Observe error messages
        lifecycleScope.launch {
            viewModel.errorMessage.collect { error ->
                error?.let {
                    showError(it)
                }
            }
        }
    }

    private fun validateAndLoadChat() {
        lifecycleScope.launch {
            try {
                // Validate chat exists on server
                val chatRepo = com.kafkachat.repository.ChatRepository(this@ChatActivity)
                val exists = chatRepo.chatExists(chatId)
                
                if (!exists) {
                    android.util.Log.e("ChatActivity", "Chat $chatId does not exist on server")
                    showError("Chat does not exist. Please go back and select the user again.")
                    // Disable message input
                    binding.messageInput.isEnabled = false
                    binding.sendButton.isEnabled = false
                    binding.mediaButton.isEnabled = false
                    return@launch
                }
                
                android.util.Log.d("ChatActivity", "Chat $chatId validated on server")
                loadMessages()
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "Error validating chat", e)
                showError("Failed to validate chat. Please check your connection.")
            }
        }
    }

    private fun loadMessages() {
        viewModel.loadMessages(chatId)
    }

    private fun sendMessage(mediaUrl: String? = null) {
        val content = binding.messageInput.text?.toString()?.trim() ?: ""
        if (content.isEmpty() && mediaUrl == null) return

        // Validate required fields
        if (chatId == 0L) {
            showError("Invalid chat ID")
            return
        }
        if (currentUserId == 0L) {
            showError("User not logged in")
            return
        }

        val senderName = preferenceManager.getString(Constants.PREF_USERNAME, "Unknown") ?: "Unknown"

        val message = ChatMessage.create(
            chatId = chatId,
            senderId = currentUserId,
            senderUsername = senderName,
            senderImage = null,
            content = content.ifEmpty { null }, // Allow null content for media-only messages
            mediaUrl = mediaUrl,
            status = Constants.MESSAGE_STATUS_SENT
        )

        try {
            viewModel.sendMessage(message)
            binding.messageInput.text?.clear()
            selectedMediaUri = null
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "Error sending message", e)
            showError("Failed to send message: ${e.message}")
        }
    }

    private fun checkImagePermissionAndPick() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED -> {
                openImagePicker()
            }
            else -> {
                imagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
    }

    private fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        android.util.Log.d("ChatActivity", "Updating connection status: $isConnected")
        if (isConnected) {
            binding.connectionStatusCard.visibility = View.GONE
            android.util.Log.d("ChatActivity", "Connection status: Connected - hiding status card")
        } else {
            binding.connectionStatusCard.visibility = View.VISIBLE
            binding.connectionStatus.text = "Disconnected"
            binding.connectionStatusCard.setCardBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_red_light)
            )
            android.util.Log.d("ChatActivity", "Connection status: Disconnected - showing status card")
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        viewModel.setAppInForeground(false)
    }

    override fun onResume() {
        super.onResume()
        viewModel.setAppInForeground(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.setAppInForeground(false)
        viewModel.disconnect()
    }
}