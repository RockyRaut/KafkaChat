package com.kafkachat

import android.app.Application
import com.kafkachat.util.PreferenceManager

class Kafkachat : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferenceManager(this).clearServerConfig()
    }
}
