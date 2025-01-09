package com.example.moonecho.manager

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.moonecho.model.User

class AuthManager {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * 获取当前用户
     */
    fun getCurrentUser() = auth.currentUser

    /**
     * 用户登录方法
     */
    fun login(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        // 确保用户文档存在
                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { document ->
                                if (!document.exists()) {
                                    // 如果文档不存在，创建新文档
                                    val user = User(email = email, friendUid = "")
                                    db.collection("users").document(uid).set(user)
                                        .addOnSuccessListener {
                                            callback(true, null)
                                        }
                                        .addOnFailureListener { e ->
                                            callback(false, e.message)
                                        }
                                } else {
                                    callback(true, null)
                                }
                            }
                            .addOnFailureListener { e ->
                                callback(false, e.message)
                            }
                    } else {
                        callback(false, "无法获取用户ID")
                    }
                } else {
                    callback(false, task.exception?.message)
                }
            }
    }

    /**
     * 用户注册方法
     */
    fun register(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        val user = User(email = email, friendUid = "")
                        db.collection("users").document(uid).set(user)
                            .addOnCompleteListener { firestoreTask ->
                                if (firestoreTask.isSuccessful) {
                                    callback(true, null)
                                } else {
                                    callback(false, firestoreTask.exception?.message)
                                }
                            }
                    } else {
                        callback(false, "无法获取用户ID")
                    }
                } else {
                    callback(false, task.exception?.message)
                }
            }
    }

    /**
     * 检查用户是否已登录
     */
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * 用户登出方法
     */
    fun logout() {
        auth.signOut()
    }

    /**
     * 添加好友方法
     */
    fun addFriend(friendEmail: String, callback: (Boolean, String?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            callback(false, "用户未登录")
            return
        }

        // 查找好友的用户文档
        db.collection("users")
            .whereEqualTo("email", friendEmail)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    callback(false, "未找到该好友")
                } else {
                    val friendDoc = querySnapshot.documents[0]
                    val friendUid = friendDoc.id

                    if (friendUid == currentUser.uid) {
                        callback(false, "无法添加自己为好友")
                        return@addOnSuccessListener
                    }

                    // 获取当前用户的好友UID
                    val userRef = db.collection("users").document(currentUser.uid)
                    userRef.get()
                        .addOnSuccessListener { userDoc ->
                            val currentFriendUid = userDoc.getString("friendUid") ?: ""
                            if (currentFriendUid.isNotEmpty()) {
                                callback(false, "已添加一个好友，无需重复添加")
                                return@addOnSuccessListener
                            }

                            // 添加好友 - 设置 friendUid
                            userRef.update("friendUid", friendUid)
                                .addOnSuccessListener {
                                    // 选填：在好友的文档中记录当前用户UID，实现双向好友关系
                                    val friendRef = db.collection("users").document(friendUid)
                                    friendRef.update("friendUid", currentUser.uid)
                                        .addOnSuccessListener {
                                            callback(true, "好友添加成功")
                                        }
                                        .addOnFailureListener { e ->
                                            callback(false, "好友添加失败: ${e.message}")
                                        }
                                }
                                .addOnFailureListener { e ->
                                    callback(false, "添加好友失败: ${e.message}")
                                }
                        }
                        .addOnFailureListener { e ->
                            callback(false, "获取用户信息失败: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                callback(false, "查询好友失败: ${e.message}")
            }
    }

    /**
     * 检查是否已添加好友
     */
    fun isFriendAdded(callback: (Boolean) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            callback(false)
            return
        }

        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val friendUid = document.getString("friendUid") ?: ""
                    callback(friendUid.isNotEmpty())
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener {
                callback(false)
            }
    }
}