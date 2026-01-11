package com.kafkachat.network

import android.content.Context
import android.util.Log
import com.kafkachat.model.ChatMessage
import com.kafkachat.model.TypingEvent
import com.kafkachat.util.Constants
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WebSocketManager(private val context: Context, private val gson: Gson) {

    private var webSocket: WebSocket? = null
    private var messageListener: ((ChatMessage) -> Unit)? = null
    private var typingListener: ((TypingEvent) -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null
    private var connectionListener: ((Boolean) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(onSuccess: () -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                var wsUrl = Constants.getWsUrl(context)
                Log.d(Constants.TAG_WEBSOCKET, "Initial WebSocket URL from prefs: $wsUrl")
                
                // Handle wss:// for secure connections (ngrok HTTPS)
                val factory = WebSocketFactory().apply {
                    setConnectionTimeout(30000) // Increased timeout for ngrok
                    // For ngrok and development, we need to trust all certificates
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
                        setSSLContext(sslContext)
                        Log.d(Constants.TAG_WEBSOCKET, "SSL context configured for ngrok")
                    } catch (e: Exception) {
                        Log.e(Constants.TAG_WEBSOCKET, "Error setting SSL context: ${e.message}", e)
                    }
                    setVerifyHostname(false)
                }
                
                // Ensure proper protocol - handle ngrok URLs
                if (wsUrl.startsWith("https://")) {
                    wsUrl = wsUrl.replace("https://", "wss://")
                } else if (wsUrl.contains("ngrok") || wsUrl.contains("ngrok-free.app")) {
                    // ngrok always uses HTTPS/WSS
                    if (wsUrl.startsWith("http://")) {
                        wsUrl = wsUrl.replace("http://", "wss://")
                    } else if (!wsUrl.startsWith("wss://") && !wsUrl.startsWith("ws://")) {
                        wsUrl = "wss://$wsUrl"
                    } else if (wsUrl.startsWith("ws://")) {
                        wsUrl = wsUrl.replace("ws://", "wss://")
                    }
                } else if (!wsUrl.startsWith("ws://") && !wsUrl.startsWith("wss://")) {
                    wsUrl = "ws://$wsUrl"
                }
                
                Log.d(Constants.TAG_WEBSOCKET, "Final WebSocket URL: $wsUrl")
                webSocket = factory.createSocket(wsUrl)
                
                // Add custom headers for ngrok (to bypass browser warning)
                val originUrl = wsUrl.replace("wss://", "https://").replace("ws://", "http://").replace("/ws", "")
                webSocket?.addHeader("User-Agent", "KafkaChat-Android/1.0")
                webSocket?.addHeader("Origin", originUrl)
                webSocket?.addHeader("ngrok-skip-browser-warning", "true")
                
                // For ngrok, we need to handle the browser warning bypass
                if (wsUrl.contains("ngrok")) {
                    Log.d(Constants.TAG_WEBSOCKET, "Configuring for ngrok connection")
                    // Add additional headers that ngrok might need
                    webSocket?.addHeader("Accept", "*/*")
                    webSocket?.addHeader("Cache-Control", "no-cache")
                }

                webSocket?.addListener(object : WebSocketAdapter() {
                    override fun onConnected(websocket: WebSocket?, headers: MutableMap<String, MutableList<String>>?) {
                        Log.d(Constants.TAG_WEBSOCKET, "Connected to server - WebSocket is open: ${websocket?.isOpen}")
                        // Update connection status - callbacks are already on appropriate threads
                        connectionListener?.invoke(true)
                        onSuccess()
                    }

                    override fun onConnectError(websocket: WebSocket?, exception: WebSocketException?) {
                        val errorMsg = exception?.message ?: "Unknown error"
                        val errorCode = exception?.error?.name ?: "UNKNOWN"
                        val errorDetails = exception?.let { 
                            "Error Code: $errorCode, Message: ${it.message}"
                        } ?: errorMsg
                        Log.e(Constants.TAG_WEBSOCKET, "Connection error: $errorDetails")
                        Log.e(Constants.TAG_WEBSOCKET, "Attempted URL: $wsUrl")
                        
                        // Provide more specific error messages
                        val userFriendlyError = when {
                            wsUrl.contains("ngrok") -> {
                                "Ngrok connection failed. Make sure:\n" +
                                "1. ngrok is running (ngrok http 8080)\n" +
                                "2. Backend is accessible at http://localhost:8080\n" +
                                "3. Visit ngrok URL in browser first to bypass warning\n" +
                                "Error: $errorMsg"
                            }
                            else -> "Connection failed: $errorMsg"
                        }
                        
                        // Update connection status
                        connectionListener?.invoke(false)
                        onError(userFriendlyError)
                    }

                    override fun onTextMessage(websocket: WebSocket?, text: String?) {
                        // Defensive check: skip null or blank messages
                        if (text.isNullOrBlank()) {
                            Log.d(Constants.TAG_WEBSOCKET, "Received null or blank WebSocket message, skipping")
                            return
                        }
                        
                        try {
                            // First, try to parse as JSON object to check the type
                            val jsonObject = gson.fromJson(text, com.google.gson.JsonObject::class.java)
                            
                            // Check if it's an error message
                            if (jsonObject.has("error")) {
                                val errorMessage = jsonObject.get("error")?.asString ?: "Unknown error"
                                
                                // DO NOT IGNORE "Chat not found" - this is a fatal error
                                // Chat must exist before messaging
                                Log.e(Constants.TAG_WEBSOCKET, "Received error from server: $errorMessage")
                                errorListener?.invoke(errorMessage)
                                return
                            }
                            
                            // Check if it's a typing event
                            if (jsonObject.has("isTyping") || jsonObject.has("typing")) {
                                try {
                                    val typingEvent = gson.fromJson(text, TypingEvent::class.java)
                                    Log.d(Constants.TAG_WEBSOCKET, "Received typing event: chatId=${typingEvent.chatId}, userId=${typingEvent.userId}, isTyping=${typingEvent.isTyping}")
                                    typingListener?.invoke(typingEvent)
                                    return
                                } catch (e: Exception) {
                                    Log.d(Constants.TAG_WEBSOCKET, "Could not parse as TypingEvent: ${e.message}")
                                }
                            }
                            
                            // Try to parse as ChatMessage
                                val message = gson.fromJson(text, ChatMessage::class.java)
                            
                            // Validate it's actually a chat message before passing to listener
                            // Non-chat events (typing, handshake, etc.) will have invalid chatId/senderId
                            if (message.chatId == 0L && message.senderId == 0L) {
                                Log.d(Constants.TAG_WEBSOCKET, "Received non-chat event (typing/handshake/etc.), skipping: $text")
                                return
                            }
                            
                            Log.d(Constants.TAG_WEBSOCKET, "Received valid chat message: chatId=${message.chatId}, senderId=${message.senderId}")
                                messageListener?.invoke(message)
                            } catch (e: Exception) {
                            // Log but don't crash - might be a non-chat event we don't handle
                            Log.d(Constants.TAG_WEBSOCKET, "Could not parse WebSocket message: ${e.message}")
                        }
                    }

                    override fun onDisconnected(websocket: WebSocket?,
                                                serverCloseFrame: WebSocketFrame?,
                                                clientCloseFrame: WebSocketFrame?,
                                                closedByServer: Boolean) {
                        Log.d(Constants.TAG_WEBSOCKET, "Disconnected from server (closedByServer: $closedByServer)")
                        connectionListener?.invoke(false)
                    }

                    override fun onError(websocket: WebSocket?, cause: WebSocketException?) {
                        Log.e(Constants.TAG_WEBSOCKET, "WebSocket error: ${cause?.message}")
                        onError(cause?.message ?: "Unknown error")
                    }
                })

                webSocket?.connectAsynchronously()
            } catch (e: Exception) {
                Log.e(Constants.TAG_WEBSOCKET, "Failed to create WebSocket: ${e.message}")
                onError(e.message ?: "Failed to create WebSocket")
            }
        }
    }

    fun sendMessage(message: ChatMessage) {
        scope.launch {
            try {
                val json = gson.toJson(message)
                Log.d(Constants.TAG_WEBSOCKET, "Sending message: $json")
                webSocket?.sendText(json)
            } catch (e: Exception) {
                Log.e(Constants.TAG_WEBSOCKET, "Error sending message: ${e.message}")
            }
        }
    }

    fun sendTypingEvent(typingEvent: TypingEvent) {
        scope.launch {
            try {
                val json = gson.toJson(typingEvent)
                Log.d(Constants.TAG_WEBSOCKET, "Sending typing event: $json")
                webSocket?.sendText(json)
            } catch (e: Exception) {
                Log.e(Constants.TAG_WEBSOCKET, "Error sending typing event: ${e.message}")
            }
        }
    }

    fun setMessageListener(listener: (ChatMessage) -> Unit) {
        messageListener = listener
    }

    fun setTypingListener(listener: (TypingEvent) -> Unit) {
        typingListener = listener
    }

    fun setErrorListener(listener: (String) -> Unit) {
        errorListener = listener
    }

    fun setConnectionListener(listener: (Boolean) -> Unit) {
        connectionListener = listener
    }

    fun disconnect() {
        scope.launch {
            try {
                webSocket?.disconnect()
                Log.d(Constants.TAG_WEBSOCKET, "Disconnected")
            } catch (e: Exception) {
                Log.e(Constants.TAG_WEBSOCKET, "Error disconnecting: ${e.message}")
            }
        }
    }

    fun isConnected(): Boolean = webSocket?.isOpen == true
}