package com.kafkachat.ui.activity.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kafkachat.R
import com.kafkachat.databinding.ItemMessageBinding
import com.kafkachat.model.ChatMessage

class MessageAdapter(private val currentUserId: Long) :
    ListAdapter<ChatMessage, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding, currentUserId)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(
        private val binding: ItemMessageBinding,
        private val currentUserId: Long
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            val isCurrentUser = message.senderId == currentUserId
            val context = binding.root.context

            // Set message content (handle nullable)
            binding.messageText.text = message.content ?: ""
            // Hide text if empty and media is present
            if (message.content.isNullOrBlank() && !message.mediaUrl.isNullOrEmpty()) {
                binding.messageText.visibility = View.GONE
            } else {
                binding.messageText.visibility = View.VISIBLE
            }
            
            // Show/hide sender name for received messages
            if (isCurrentUser) {
                binding.senderName.visibility = View.GONE
            } else {
                binding.senderName.text = message.senderUsername
                binding.senderName.visibility = View.VISIBLE
            }

            // Format time
            binding.messageTime.text = formatTime(message.createdAt)

            // Handle media
            if (!message.mediaUrl.isNullOrEmpty()) {
                binding.mediaImage.visibility = View.VISIBLE
                Glide.with(context)
                    .load(message.mediaUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(binding.mediaImage)
            } else {
                binding.mediaImage.visibility = View.GONE
            }

            // Style bubble based on sender
            val sentColor = ContextCompat.getColor(context, R.color.message_bubble_sent)
            val receivedColor = ContextCompat.getColor(context, R.color.message_bubble_received)
            val sentTextColor = ContextCompat.getColor(context, R.color.message_text_sent)
            val receivedTextColor = ContextCompat.getColor(context, R.color.message_text_received)

            if (isCurrentUser) {
                // Sent message - blue bubble on right
                binding.messageBubble.setCardBackgroundColor(sentColor)
                binding.messageText.setTextColor(sentTextColor)
                binding.senderName.setTextColor(sentTextColor)
                binding.messageTime.setTextColor(Color.argb(200, 255, 255, 255))
                
                // Align to end using FrameLayout gravity
                val params = binding.messageBubble.layoutParams as android.widget.FrameLayout.LayoutParams
                params.gravity = android.view.Gravity.END
                binding.messageBubble.layoutParams = params
            } else {
                // Received message - gray bubble on left
                binding.messageBubble.setCardBackgroundColor(receivedColor)
                binding.messageText.setTextColor(receivedTextColor)
                binding.senderName.setTextColor(receivedTextColor)
                binding.messageTime.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                
                // Align to start using FrameLayout gravity
                val params = binding.messageBubble.layoutParams as android.widget.FrameLayout.LayoutParams
                params.gravity = android.view.Gravity.START
                binding.messageBubble.layoutParams = params
            }

            // Set status icon
            when (message.status) {
                "SENT" -> binding.statusIcon.setImageResource(android.R.drawable.ic_menu_send)
                "DELIVERED" -> binding.statusIcon.setImageResource(android.R.drawable.checkbox_on_background)
                "READ" -> binding.statusIcon.setImageResource(android.R.drawable.ic_menu_view)
                else -> binding.statusIcon.setImageResource(android.R.drawable.ic_menu_send)
            }
        }

        private fun formatTime(dateString: String): String {
            return try {
                val dateTime = java.time.LocalDateTime.parse(dateString)
                val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                dateTime.format(formatter)
            } catch (e: Exception) {
                try {
                    // Try ISO format
                    val instant = java.time.Instant.parse(dateString)
                    val dateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    dateTime.format(formatter)
                } catch (e2: Exception) {
                    "Now"
                }
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
