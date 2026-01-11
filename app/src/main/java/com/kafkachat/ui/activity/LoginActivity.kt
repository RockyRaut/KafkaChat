package com.kafkachat.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kafkachat.databinding.ActivityLoginBinding
import com.kafkachat.util.Constants
import com.kafkachat.util.PreferenceManager
import com.kafkachat.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)

        // Check if server URL is configured
        val serverUrl = preferenceManager.getString(Constants.PREF_SERVER_URL, "")
        
        if (serverUrl.isNullOrEmpty()) {
            // Show server config first
            binding.btnLogin.text = "Configure Server"
            binding.btnLogin.setOnClickListener {
                startActivity(android.content.Intent(this, ServerConfigActivity::class.java))
                finish()
            }
            binding.btnRegister.visibility = View.GONE
            binding.etEmail.visibility = View.GONE
            binding.etPassword.visibility = View.GONE
            binding.emailInputLayout.visibility = View.GONE
            binding.passwordInputLayout.visibility = View.GONE
        } else {
            setupAuth()
        }
        
        // Add option to reconfigure server
        binding.tvServerConfig.setOnClickListener {
            startActivity(android.content.Intent(this, ServerConfigActivity::class.java))
        }
        
        // Check if already logged in
        if (viewModel.checkAuthStatus()) {
            navigateToUsersList()
        }
    }
    
    private fun setupAuth() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            
            if (email.isEmpty() || password.isEmpty()) {
                showError("Please enter email and password")
                return@setOnClickListener
            }
            
            viewModel.login(email, password)
        }
        
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            val username = email.substringBefore("@") // Use email prefix as username
            
            if (email.isEmpty() || password.isEmpty()) {
                showError("Please enter email and password")
                return@setOnClickListener
            }
            
            viewModel.register(username, email, password)
        }
        
        // Observe auth state
        lifecycleScope.launch {
            viewModel.authState.collect { result ->
                result?.let {
                    if (it.success) {
                        Toast.makeText(this@LoginActivity, it.message ?: "Success", Toast.LENGTH_SHORT).show()
                        navigateToUsersList()
                    } else {
                        showError(it.message ?: "Authentication failed")
                    }
                }
            }
        }
        
        // Observe loading state
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.btnLogin.isEnabled = !isLoading
                binding.btnRegister.isEnabled = !isLoading
                binding.btnLogin.text = if (isLoading) "Logging in..." else "Login"
            }
        }
    }
    
    private fun navigateToUsersList() {
        startActivity(android.content.Intent(this, UsersListActivity::class.java))
        finish()
    }
    
    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
