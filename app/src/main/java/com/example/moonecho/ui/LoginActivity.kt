package com.example.moonecho.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.moonecho.R
import com.example.moonecho.manager.AuthManager

class LoginActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button
    private lateinit var buttonRegister: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        authManager = AuthManager()
        editTextEmail = findViewById(R.id.editTextEmail)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonLogin = findViewById(R.id.buttonLogin)
        buttonRegister = findViewById(R.id.buttonRegister)

        // 检查用户是否已登录
        /*if (authManager.isUserLoggedIn()) {
            navigateToChat()
        }
        */
        buttonLogin.setOnClickListener {
            val email = editTextEmail.text.toString().trim()
            val password = editTextPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email 和密码不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performLogin(email, password)
        }

        buttonRegister.setOnClickListener {
            val email = editTextEmail.text.toString().trim()
            val password = editTextPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email 和密码不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performRegister(email, password)
        }
    }

    private fun performLogin(email: String, password: String) {
        authManager.login(email, password) { success, message ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
                    checkFriendStatus()
                } else {
                    Toast.makeText(this, "登录失败: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performRegister(email: String, password: String) {
        authManager.register(email, password) { success, message ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "注册成功", Toast.LENGTH_SHORT).show()
                    checkFriendStatus()
                } else {
                    Toast.makeText(this, "注册失败: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToChat() {
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun checkFriendStatus() {
        authManager.isFriendAdded { isAdded ->
            runOnUiThread {
                if (isAdded) {
                    navigateToChat()
                } else {
                    navigateToAddFriend()
                }
            }
        }
    }

    private fun navigateToAddFriend() {
        val intent = Intent(this, AddFriendActivity::class.java)
        startActivity(intent)
        finish()
    }
}
