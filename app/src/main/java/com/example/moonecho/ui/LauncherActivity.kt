// LauncherActivity.kt
package com.example.moonecho.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查用户是否已登录
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // 用户已登录，跳转到 ChatActivity
            startActivity(Intent(this, ChatActivity::class.java))
        } else {
            // 用户未登录，跳转到 LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
        }
        // 结束 LauncherActivity，不保留在返回栈中
        finish()
    }
}