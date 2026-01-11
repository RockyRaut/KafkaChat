package com.kafkachat.util

import android.content.Context
import android.content.SharedPreferences
import com.kafkachat.util.Constants

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, default: String? = null): String? =
        prefs.getString(key, default)

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, default: Long = 0L): Long =
        prefs.getLong(key, default)

    fun clearServerConfig() {
        prefs.edit()
            .remove(Constants.PREF_SERVER_URL)
            .remove(Constants.PREF_WS_URL)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}