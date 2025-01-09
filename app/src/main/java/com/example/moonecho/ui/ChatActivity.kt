// ChatActivity.kt
package com.example.moonecho.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moonecho.R
import com.example.moonecho.adapter.MessageAdapter
import com.example.moonecho.manager.AuthManager
import com.example.moonecho.model.Message
import com.google.android.material.navigation.NavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ChatActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var authManager: AuthManager
    private lateinit var recyclerViewMessages: RecyclerView
    private lateinit var editTextMessageInput: EditText
    private lateinit var buttonSend: Button
    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private var chatId: String? = null

    // 侧边栏相关
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        authManager = AuthManager()
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages)
        editTextMessageInput = findViewById(R.id.editTextMessageInput)
        buttonSend = findViewById(R.id.buttonSend)

        // 初始化侧边栏
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)

        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        navigationView.setNavigationItemSelectedListener(this)

        // 设置用户邮箱
        val headerView = navigationView.getHeaderView(0)
        val textViewEmail = headerView.findViewById<TextView>(R.id.textViewEmail)
        val currentUser = FirebaseAuth.getInstance().currentUser
        textViewEmail.text = currentUser?.email ?: "未登录"

        messageAdapter = MessageAdapter(messages)
        recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = messageAdapter
        }

        determineChatId()

        buttonSend.setOnClickListener {
            val messageText = editTextMessageInput.text.toString().trim()
            if (messageText.isNotEmpty() && chatId != null) {
                sendMessage(chatId!!, messageText)
                editTextMessageInput.text.clear()
            } else {
                Toast.makeText(this, "消息不能为空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 重写 onOptionsItemSelected 方法，处理侧边栏按钮点击事件
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // 实现 NavigationView.OnNavigationItemSelectedListener 接口的方法
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_chat -> {
                // 当前已在ChatActivity，无需操作
            }
            R.id.nav_location -> {
                // 启动MapActivity，传递对方用户的邮箱
                val intent = Intent(this, MapActivity::class.java).apply {
                    // TODO: 获取对方用户的邮箱，可以从聊天对象中获取
                    putExtra("otherUserEmail", "对方用户的邮箱@example.com")
                }
                startActivity(intent)
            }
            R.id.nav_timer -> {
                // 启动 TimerActivity
                val intent = Intent(this, TimerActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_logout -> {
                performLogout()
            }
            // 可以添加更多的菜单项处理
        }
        drawerLayout.closeDrawers()
        return true
    }

    private fun performLogout() {
        authManager.logout()
        Toast.makeText(this, "已登出", Toast.LENGTH_SHORT).show()
        // 跳转回登录页面，并清除活动栈
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun determineChatId() {
        val currentUser = authManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "用户未登录", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        // 获取当前用户的好友UID
        authManager.isFriendAdded { isAdded ->
            if (isAdded) {
                db.collection("users").document(currentUser.uid)
                    .get()
                    .addOnSuccessListener { document ->
                        val friendUid = document.getString("friendUid")
                        if (friendUid != null && friendUid.isNotEmpty()) {
                            chatId = generateChatId(currentUser.uid, friendUid)
                            setupMessagesListener()
                        } else {
                            Toast.makeText(this, "好友UID无效", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "获取好友UID失败", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            } else {
                Toast.makeText(this, "尚未添加好友", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun generateChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) {
            "${uid1}_${uid2}"
        } else {
            "${uid2}_${uid1}"
        }
    }

    private fun setupMessagesListener() {
        if (chatId == null) return

        db.collection("chats").document(chatId!!)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "监听消息失败: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    messages.clear()
                    for (doc in snapshots.documents) {
                        val message = doc.toObject(Message::class.java)
                        if (message != null) {
                            messages.add(message)
                        }
                    }
                    messageAdapter.notifyDataSetChanged()
                    recyclerViewMessages.scrollToPosition(messages.size - 1)
                }
            }
    }

    private fun sendMessage(chatId: String, messageText: String) {
        val message = Message(
            senderId = currentUserId ?: "",
            message = messageText,
            timestamp = Timestamp.now()
        )
        db.collection("chats").document(chatId)
            .collection("messages")
            .add(message)
            .addOnSuccessListener {
                // 消息发送成功
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "发送消息失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}