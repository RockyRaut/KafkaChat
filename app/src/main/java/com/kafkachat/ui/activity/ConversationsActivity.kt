package com.kafkachat.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.kafkachat.databinding.ActivityConversationsBinding
import com.kafkachat.ui.activity.adapter.ConversationAdapter
import com.kafkachat.util.Constants

class ConversationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationsBinding
    private lateinit var conversationAdapter: ConversationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        conversationAdapter = ConversationAdapter { chat ->
            startActivity(ChatActivity.createIntent(this, chat.id, chat.creatorId))
        }

        binding.recyclerConversations.apply {
            layoutManager = LinearLayoutManager(this@ConversationsActivity)
            adapter = conversationAdapter
        }

        binding.fabNewChat.setOnClickListener {
            // Placeholder: open an empty chat for now
            startActivity(ChatActivity.createIntent(this, chatId = 1L, recipientId = 0L))
        }
    }
}
