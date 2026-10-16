package com.example

import android.app.Application
import com.example.data.MarryMatchRepository
import com.example.push.PushNotifications

/**
 * Custom Application class that retains the [MarryMatchRepository]
 * instance across configuration changes and Activity recreation (such as screen rotations).
 */
class MarryMatchApplication : Application() {
    val repository: MarryMatchRepository by lazy {
        MarryMatchRepository()
    }

    override fun onCreate() {
        super.onCreate()
        PushNotifications.createChannel(this)
    }
}
