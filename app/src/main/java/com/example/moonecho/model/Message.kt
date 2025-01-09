package com.example.moonecho.model

import com.google.firebase.Timestamp

data class Message(
    val senderId: String = "",
    val message: String = "",
    val timestamp: Timestamp? = null
)