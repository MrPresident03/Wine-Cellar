package com.example

import android.app.Application
import com.example.alerts.Notifications
import com.google.firebase.FirebaseApp

class WineCellarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Normally done automatically from google-services.json; this is a harmless safety net.
        FirebaseApp.initializeApp(this)
        Notifications.createChannel(this)
    }
}
