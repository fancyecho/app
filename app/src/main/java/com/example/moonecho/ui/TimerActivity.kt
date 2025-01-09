// TimerActivity.kt
package com.example.moonecho.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.ViewGroup  // 确保导入了 ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moonecho.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class TimerActivity : AppCompatActivity() {

    private lateinit var timerTextView: TextView
    private lateinit var startButton: Button
    private lateinit var numberPicker: NumberPicker
    private lateinit var hoursPicker: NumberPicker
    private lateinit var recordsRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    private lateinit var adapter: FocusSessionAdapter
    private var sessionList: MutableList<FocusSession> = mutableListOf()

    private var countDownTimer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 1500000 // 默认25分钟
    private var isTimerRunning = false

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private lateinit var auth: FirebaseAuth

    private var currentUserUID: String? = null
    private var friendUID: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timer)

        auth = FirebaseAuth.getInstance()
        timerTextView = findViewById(R.id.timerTextView)
        startButton = findViewById(R.id.startButton)
        numberPicker = findViewById(R.id.numberPicker)
        hoursPicker = findViewById(R.id.hoursPicker)
        recordsRecyclerView = findViewById(R.id.recordsRecyclerView)
        progressBar = findViewById(R.id.progressBar)

        // 设置选择器属性
        numberPicker.minValue = 0
        numberPicker.maxValue = 59
        numberPicker.value = 25
        numberPicker.wrapSelectorWheel = true

        hoursPicker.minValue = 0
        hoursPicker.maxValue = 23
        hoursPicker.value = 0
        hoursPicker.wrapSelectorWheel = true

        numberPicker.setOnValueChangedListener { _, _, _ ->
            if (!isTimerRunning) {
                updateTimeLeft()
            }
        }

        hoursPicker.setOnValueChangedListener { _, _, _ ->
            if (!isTimerRunning) {
                updateTimeLeft()
            }
        }

        startButton.setOnClickListener {
            if (!isTimerRunning) {
                startTimer()
                lockScreen()
            } else {
                pauseTimer()
            }
        }

        // 初始化 RecyclerView
        adapter = FocusSessionAdapter(sessionList)
        recordsRecyclerView.layoutManager = LinearLayoutManager(this)
        recordsRecyclerView.adapter = adapter

        // 用户登录（匿名登录示例）
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        currentUserUID = auth.currentUser?.uid
                        fetchFriendUID()
                    } else {
                        Toast.makeText(this, "登录失败: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
        } else {
            currentUserUID = auth.currentUser?.uid
            fetchFriendUID()
        }

        updateTimer()
    }

    private fun fetchFriendUID() {
        currentUserUID?.let { uid ->
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        friendUID = document.getString("friendUid")
                        friendUID?.let {
                            fetchFocusSessions()
                        } ?: run {
                            Toast.makeText(this, "未设置好友UID", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "用户文档不存在", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "获取好友UID失败: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateTimeLeft() {
        val totalMillis = (hoursPicker.value * 3600 + numberPicker.value * 60) * 1000L
        timeLeftInMillis = totalMillis
        updateTimer()
    }

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimer()
            }

            override fun onFinish() {
                isTimerRunning = false
                startButton.text = "开始"
                unlockScreen()
                saveSession()
                showTimerFinishedDialog() // 显示弹出式对话框
            }
        }.start()
        isTimerRunning = true
        startButton.text = "暂停"
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        startButton.text = "开始"
        unlockScreen()
    }

    private fun updateTimer() {
        val totalSeconds = timeLeftInMillis / 1000
        val hours = (totalSeconds / 3600).toInt()
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        val seconds = (totalSeconds % 60).toInt()
        timerTextView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun lockScreen() {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Toast.makeText(this, "专注模式已启动，尽量勿切换应用。", Toast.LENGTH_SHORT).show()
    }

    private fun unlockScreen() {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun saveSession() {
        val userId = currentUserUID
        if (userId == null) {
            Toast.makeText(this, "用户未登录，无法保存记录", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = Timestamp(Date()) // 使用 Timestamp 类型

        val session = hashMapOf(
            "userId" to userId, // 绑定用户 UID
            "hours" to (timeLeftInMillis / 3600000),
            "minutes" to ((timeLeftInMillis % 3600000) / 60000),
            "timestamp" to timestamp
        )

        db.collection("sessions")
            .add(session)
            .addOnSuccessListener {
                Toast.makeText(this, "专注记录已保存", Toast.LENGTH_SHORT).show()
                fetchFocusSessions()
            }
            .addOnFailureListener {
                Toast.makeText(this, "保存失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchFocusSessions() {
        if (currentUserUID == null || friendUID == null) return

        db.collection("sessions")
            .whereIn("userId", listOf(currentUserUID, friendUID))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                sessionList.clear()
                for (document in documents) {
                    val userId = document.getString("userId") ?: continue
                    val hours = document.getLong("hours") ?: 0
                    val minutes = document.getLong("minutes") ?: 0
                    val timestamp = document.getString("timestamp") ?: ""

                    val userType = if (userId == currentUserUID) "自己" else "好友"
                    val focusSession = FocusSession(userType, hours, minutes, timestamp)
                    sessionList.add(focusSession)
                }
                adapter.updateSessions(sessionList)
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(this, "获取记录失败: ${it.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }

    override fun onResume() {
        super.onResume()
        recordsRecyclerView.layoutManager?.scrollToPosition(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        unlockScreen()
    }

    // 内部数据类
    data class FocusSession(
        val userType: String = "", // "自己" 或 "好友"
        val hours: Long = 0,
        val minutes: Long = 0,
        val timestamp: String = ""
    )

    // 内部适配器类
    inner class FocusSessionAdapter(private var sessions: List<FocusSession>) :
        RecyclerView.Adapter<FocusSessionAdapter.SessionViewHolder>() {

        inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val userTypeTextView: TextView = itemView.findViewById(R.id.userTypeTextView)
            val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
            val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
            val view = layoutInflater.inflate(R.layout.item_focus_session, parent, false)
            return SessionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
            val session = sessions[position]
            holder.userTypeTextView.text = session.userType
            holder.durationTextView.text = "时长: ${session.hours}小时${session.minutes}分钟"
            holder.timestampTextView.text = "时间: ${session.timestamp}"
        }

        override fun getItemCount(): Int = sessions.size

        fun updateSessions(newSessions: List<FocusSession>) {
            sessions = newSessions
            notifyDataSetChanged()
        }
    }

    // 弹出式对话框
    private fun showTimerFinishedDialog() {
        AlertDialog.Builder(this)
            .setTitle("专注时间结束")
            .setMessage("恭喜您完成了一个专注时段！是否要开始新的专注？")
            .setPositiveButton("是") { dialog, _ ->
                dialog.dismiss()
                resetTimer()
                startTimer()
            }
            .setNegativeButton("否") { dialog, _ ->
                dialog.dismiss()
                // 您可以在这里执行其他操作，例如返回主界面
            }
            .setCancelable(false)
            .show()
    }

    // 重置计时器
    private fun resetTimer() {
        timeLeftInMillis = (hoursPicker.value * 3600 + numberPicker.value * 60) * 1000L
        updateTimer()
    }
}