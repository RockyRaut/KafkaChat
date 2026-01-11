package com.kafkachat.ui.activity.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.kafkachat.databinding.FragmentConversationBinding
import com.kafkachat.ui.activity.adapter.ConversationAdapter

class ConversationFragment : Fragment() {

    private var _binding: FragmentConversationBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ConversationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ConversationAdapter { conversation ->
            // TODO: open ChatActivity with this conversation
        }

        binding.recyclerConversations.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerConversations.adapter = adapter

        // TODO: load conversations list and submit to adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
