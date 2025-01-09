package com.example.moonecho

import android.app.Application
import com.google.firebase.FirebaseApp

class MoonEcho : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}