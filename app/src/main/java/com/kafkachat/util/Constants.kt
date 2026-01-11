package com.kafkachat.util

import android.content.Context
import android.util.Log

object Constants {
    const val PREFS_NAME = "kafka_chat_prefs"
    const val PREF_SERVER_URL = "pref_server_url"
    const val PREF_WS_URL = "pref_ws_url"
    const val PREF_USER_ID = "pref_user_id"
    const val PREF_USERNAME = "pref_username"

    const val EXTRA_CHAT_ID = "chatId"
    const val EXTRA_RECIPIENT_ID = "recipientId"

    const val MESSAGE_STATUS_SENT = "SENT"

    const val TAG_WEBSOCKET = "KafkaChatWebSocket"

    // Defaults for local dev (emulator -> host)
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"
    private const val DEFAULT_WS_URL = "ws://10.0.2.2:8080/ws"

    /**
        Returns the REST base URL (always with trailing slash).
    */
    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_SERVER_URL, null)?.trim().orEmpty()
        val base = normalizeBaseUrl(saved)
        if (saved.isNotEmpty()) {
            Log.d(TAG_WEBSOCKET, "Using saved BASE_URL: $base")
        } else {
            Log.d(TAG_WEBSOCKET, "Using default BASE_URL: $base")
        }
        return base
    }

    /**
        Returns the WebSocket URL, normalized for ngrok (forces wss).
    */
    fun getWsUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_WS_URL, null)?.trim().orEmpty()
        val ws = normalizeWsUrl(saved)
        if (saved.isNotEmpty()) {
            Log.d(TAG_WEBSOCKET, "Using saved WS_URL: $ws")
        } else {
            Log.d(TAG_WEBSOCKET, "Using default WS_URL: $ws")
        }
        return ws
    }

    private fun normalizeBaseUrl(raw: String): String {
        if (raw.isBlank()) return DEFAULT_BASE_URL
        var url = raw
        // Auto-add protocol
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = if (url.contains("ngrok")) "https://$url" else "http://$url"
        }
        // Force https for ngrok
        if (url.contains("ngrok") && url.startsWith("http://")) {
            url = url.replaceFirst("http://", "https://")
        }
        // Ensure trailing slash
        if (!url.endsWith("/")) url += "/"
        return url
    }

    private fun normalizeWsUrl(raw: String): String {
        if (raw.isBlank()) return DEFAULT_WS_URL
        var url = raw

        // If user entered HTTP/S, convert to WS/S
        if (url.startsWith("https://")) url = url.replaceFirst("https://", "wss://")
        if (url.startsWith("http://")) url = url.replaceFirst("http://", "ws://")

        // ngrok should always be secure
        if (url.contains("ngrok")) {
            url = url.replace("http://", "wss://")
            url = url.replace("ws://", "wss://")
            if (!url.startsWith("wss://")) url = "wss://$url"
        } else if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            // If missing protocol, default to ws://
            url = "ws://$url"
        }

        return url
    }
}

