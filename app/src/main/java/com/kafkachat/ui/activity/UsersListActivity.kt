package com.kafkachat.ui.activity

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kafkachat.databinding.ActivityUsersListBinding
import com.kafkachat.model.User
import com.kafkachat.ui.activity.adapter.UsersAdapter
import com.kafkachat.util.Constants
import com.kafkachat.util.PreferenceManager
import com.kafkachat.viewmodel.AuthViewModel
import com.kafkachat.viewmodel.UsersListViewModel
import kotlinx.coroutines.launch

class UsersListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsersListBinding
    private val usersViewModel: UsersListViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var usersAdapter: UsersAdapter
    private lateinit var preferenceManager: PreferenceManager
    private var currentUserId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)
        currentUserId = preferenceManager.getLong(Constants.PREF_USER_ID, 0)

        if (currentUserId == 0L) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        loadUsers()
        observeUsers()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Users"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun setupRecyclerView() {
        usersAdapter = UsersAdapter { user ->
            // Create chat via REST API before opening chat activity
            lifecycleScope.launch {
                try {
                    binding.progressBar.visibility = View.VISIBLE
                    
                    val apiService = com.kafkachat.network.ApiClient.getClient(this@UsersListActivity)
                    
                    // Create or get existing private chat
                    val chatData = mapOf(
                        "user1Id" to currentUserId,
                        "user2Id" to user.id
                    )
                    
                    val chatResponse = apiService.createChat(chatData)
                    val chatId = chatResponse.get("id")?.asLong
                    
                    if (chatId != null && chatId != 0L) {
                        android.util.Log.d("UsersListActivity", "Chat created/retrieved: $chatId")
                        startActivity(ChatActivity.createIntent(this@UsersListActivity, chatId = chatId, recipientId = user.id))
                    } else {
                        Toast.makeText(this@UsersListActivity, "Failed to create chat", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("UsersListActivity", "Error creating chat", e)
                    val errorMsg = if (e is retrofit2.HttpException) {
                        "Failed to create chat (HTTP ${e.code()}). Please try again."
                    } else {
                        "Failed to create chat: ${e.message}"
                    }
                    Toast.makeText(this@UsersListActivity, errorMsg, Toast.LENGTH_LONG).show()
                } finally {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
        
        binding.recyclerViewUsers.apply {
            layoutManager = LinearLayoutManager(this@UsersListActivity)
            adapter = usersAdapter
        }
    }

    private fun loadUsers() {
        usersViewModel.loadUsers()
    }

    private fun observeUsers() {
        lifecycleScope.launch {
            usersViewModel.users.collect { users ->
                // Filter: exclude current user
                // TODO: Once backend implements online status tracking (sets online=true on login),
                //       add filter: it.online == true to show only online users
                val filteredUsers = users.filter { 
                    it.id != currentUserId
                }
                if (filteredUsers.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerViewUsers.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerViewUsers.visibility = View.VISIBLE
                    usersAdapter.submitList(filteredUsers)
                }
            }
        }

        lifecycleScope.launch {
            usersViewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            usersViewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@UsersListActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 998, Menu.NONE, "About")
        menu.add(0, 999, Menu.NONE, "Logout")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            998 -> {
                showAboutDialog()
                true
            }
            999 -> {
                authViewModel.logout()
                startActivity(android.content.Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAboutDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("About")
            .setMessage("KafkaChat\n\nDeveloped by: ROCKY RAUT\n\nA real-time chat application using Kafka messaging.")
            .setPositiveButton("OK", null)
            .show()
    }
}

