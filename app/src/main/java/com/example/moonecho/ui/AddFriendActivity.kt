package com.example.moonecho.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.moonecho.R
import com.example.moonecho.manager.AuthManager

class AddFriendActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var editTextFriendEmail: EditText
    private lateinit var buttonAddFriend: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_friend)

        authManager = AuthManager()
        editTextFriendEmail = findViewById(R.id.editTextFriendEmail)
        buttonAddFriend = findViewById(R.id.buttonAddFriend)

        buttonAddFriend.setOnClickListener {
            val friendEmail = editTextFriendEmail.text.toString().trim()
            if (friendEmail.isEmpty()) {
                Toast.makeText(this, "好友邮箱不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addFriend(friendEmail)
        }
    }

    private fun addFriend(friendEmail: String) {
        authManager.addFriend(friendEmail) { success, message ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "好友添加成功", Toast.LENGTH_SHORT).show()
                    navigateToChat()
                } else {
                    Toast.makeText(this, "添加好友失败: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToChat() {
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
        finish()
    }
}