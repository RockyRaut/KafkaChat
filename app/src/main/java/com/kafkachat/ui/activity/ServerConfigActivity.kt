package com.kafkachat.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kafkachat.databinding.ActivityServerConfigBinding
import com.kafkachat.util.Constants
import com.kafkachat.util.PreferenceManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ServerConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerConfigBinding
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)

        // Load saved server URL
        val savedUrl = preferenceManager.getString(Constants.PREF_SERVER_URL, "")
        if (!savedUrl.isNullOrEmpty()) {
            binding.etServerUrl.setText(savedUrl)
        } else {
            // Default to local network IP
            binding.etServerUrl.setText("http://192.168.1.100:8080")
        }

        binding.btnSave.setOnClickListener {
            var serverUrl = binding.etServerUrl.text.toString().trim()
            
            if (serverUrl.isEmpty()) {
                Toast.makeText(this, "Please enter server URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Auto-add protocol if missing, and force HTTPS for ngrok
            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                // If it's an ngrok domain, use https://, otherwise use http://
                if (serverUrl.contains("ngrok")) {
                    serverUrl = "https://$serverUrl"
                } else {
                    serverUrl = "http://$serverUrl"
                }
            } else if (serverUrl.contains("ngrok") && serverUrl.startsWith("http://")) {
                // Force HTTPS for ngrok even if user entered http://
                serverUrl = serverUrl.replace("http://", "https://")
            }

            // Remove trailing slash if present
            val cleanUrl = if (serverUrl.endsWith("/")) serverUrl.dropLast(1) else serverUrl
            val cleanUrlWithSlash = "$cleanUrl/"

            // Save to preferences
            preferenceManager.putString(Constants.PREF_SERVER_URL, cleanUrlWithSlash)
            
            // Convert to WebSocket URL - handle ngrok properly
            val wsUrl = when {
                cleanUrl.startsWith("https://") -> cleanUrl.replace("https://", "wss://") + "/ws"
                cleanUrl.startsWith("http://") -> cleanUrl.replace("http://", "ws://") + "/ws"
                cleanUrl.contains("ngrok") -> "wss://$cleanUrl/ws" // ngrok always uses HTTPS
                else -> "ws://$cleanUrl/ws"
            }
            preferenceManager.putString(Constants.PREF_WS_URL, wsUrl)
            
            android.util.Log.d("ServerConfig", "Saved BASE_URL: $cleanUrlWithSlash")
            android.util.Log.d("ServerConfig", "Saved WS_URL: $wsUrl")

            // Show saved URLs for debugging
            android.util.Log.d("ServerConfig", "Saved URLs:")
            android.util.Log.d("ServerConfig", "  BASE_URL: $cleanUrlWithSlash")
            android.util.Log.d("ServerConfig", "  WS_URL: $wsUrl")
            
            Toast.makeText(this, "Server URL saved!\nBASE: $cleanUrlWithSlash\nWS: $wsUrl", Toast.LENGTH_LONG).show()
            
            // Go to login
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnTestConnection.setOnClickListener {
            var serverUrl = binding.etServerUrl.text.toString().trim()
            if (serverUrl.isEmpty()) {
                Toast.makeText(this, "Please enter server URL first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Auto-add protocol if missing, and force HTTPS for ngrok
            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                if (serverUrl.contains("ngrok")) {
                    serverUrl = "https://$serverUrl"
                } else {
                    serverUrl = "http://$serverUrl"
                }
            } else if (serverUrl.contains("ngrok") && serverUrl.startsWith("http://")) {
                // Force HTTPS for ngrok even if user entered http://
                serverUrl = serverUrl.replace("http://", "https://")
            }

            // Test connection in background
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                binding.btnTestConnection.isEnabled = false
                binding.btnTestConnection.text = "Testing..."
                
                Thread {
                    try {
                        val cleanUrl = if (serverUrl.endsWith("/")) serverUrl.dropLast(1) else serverUrl
                        val testUrl = "$cleanUrl/actuator/health"
                        android.util.Log.d("ServerConfig", "Testing URL: $testUrl")
                        
                        // Use OkHttp for better ngrok support
                        val client = OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .apply {
                                // Handle SSL for ngrok (trust all certificates in dev)
                                if (testUrl.contains("ngrok")) {
                                    try {
                                        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                                            object : javax.net.ssl.X509TrustManager {
                                                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                                                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                                                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                                            }
                                        )
                                        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                                        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                                        sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                                        hostnameVerifier { _, _ -> true }
                                    } catch (e: Exception) {
                                        android.util.Log.e("ServerConfig", "Error setting SSL context: ${e.message}")
                                    }
                                }
                            }
                            .build()
                        
                        // Build request with ngrok bypass headers
                        val requestBuilder = Request.Builder()
                            .url(testUrl)
                            .header("User-Agent", "KafkaChat-Android/1.0")
                            .header("Accept", "*/*")
                            .header("Cache-Control", "no-cache")
                        
                        // Critical: Add ngrok browser warning bypass header
                        if (testUrl.contains("ngrok")) {
                            requestBuilder.header("ngrok-skip-browser-warning", "true")
                            android.util.Log.d("ServerConfig", "Added ngrok-skip-browser-warning header")
                        }
                        
                        val request = requestBuilder.build()
                        val response = client.newCall(request).execute()
                        
                        val responseCode = response.code
                        val responseMessage = response.message
                        val responseBody = response.body?.string() ?: ""
                        
                        android.util.Log.d("ServerConfig", "Response: $responseCode $responseMessage")
                        android.util.Log.d("ServerConfig", "Response body: ${responseBody.take(200)}")
                        
                        runOnUiThread {
                            binding.btnTestConnection.isEnabled = true
                            binding.btnTestConnection.text = "Test Connection"
                            if (responseCode == 200) {
                                Toast.makeText(this, "✓ Connection successful!", Toast.LENGTH_LONG).show()
                            } else {
                                val errorMsg = if (responseBody.contains("ngrok") || responseBody.contains("browser")) {
                                    "Ngrok browser warning detected.\nVisit the URL in a browser first to bypass it."
                                } else {
                                    "Connection failed: $responseCode $responseMessage"
                                }
                                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                        response.close()
                    } catch (e: Exception) {
                        android.util.Log.e("ServerConfig", "Connection test error", e)
                        runOnUiThread {
                            binding.btnTestConnection.isEnabled = true
                            binding.btnTestConnection.text = "Test Connection"
                            val errorMsg = when {
                                e.message?.contains("ngrok") == true -> {
                                    "Ngrok connection failed. Make sure:\n" +
                                    "1. Ngrok is running (ngrok http 8080)\n" +
                                    "2. URL is correct\n" +
                                    "3. Backend is accessible\n" +
                                    "4. Visit URL in browser first to bypass warning"
                                }
                                e.message?.contains("unexpected end of stream") == true -> {
                                    "Connection closed unexpectedly.\n" +
                                    "If using ngrok, visit the URL in a browser first to bypass the warning page."
                                }
                                else -> "Connection failed: ${e.message ?: "Unknown error"}"
                            }
                            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        }
    }
}

