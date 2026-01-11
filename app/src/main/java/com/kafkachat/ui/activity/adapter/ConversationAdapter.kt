package com.kafkachat.ui.activity.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kafkachat.databinding.ItemConversationBinding
import com.kafkachat.model.Chat

class ConversationAdapter(
    private val onConversationSelected: (Chat) -> Unit
) : ListAdapter<Chat, ConversationAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ConversationViewHolder(binding, onConversationSelected)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ConversationViewHolder(
        private val binding: ItemConversationBinding,
        private val onConversationSelected: (Chat) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            binding.chatName.text = chat.name
            binding.root.setOnClickListener { onConversationSelected(chat) }
        }
    }

    class ConversationDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean =
            oldItem == newItem
    }
}