package com.us.app

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.us.app.data.model.Message
import com.us.app.data.model.StrokePath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

object LoveBrushRepository {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    
    private var prefs: SharedPreferences? = null

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode
    
    private val _paired = MutableStateFlow(false)
    val paired: StateFlow<Boolean> = _paired
    
    private val _inbox = MutableStateFlow<List<Message>>(emptyList())
    val inbox: StateFlow<List<Message>> = _inbox

    private val _receivedPaths = MutableStateFlow<List<StrokePath>>(emptyList())
    val receivedPaths: StateFlow<List<StrokePath>> = _receivedPaths

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var roomListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    
    val showCanvas = MutableStateFlow(false)
    val hasNewMessage = MutableStateFlow(false)
    val showInbox = MutableStateFlow(false)

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("LoveBrushPrefs", Context.MODE_PRIVATE)
            val savedCode = prefs?.getString("ROOM_CODE", null)
            if (savedCode != null) {
                _roomCode.value = savedCode
                listenToRoom(savedCode)
                listenToMessages(savedCode)
            }
        }
    }

    suspend fun initAuth() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    suspend fun createRoom() {
        try {
            initAuth()
            val uid = auth.currentUser?.uid ?: throw Exception("Auth failed")
            val code = "LOVE-" + UUID.randomUUID().toString().substring(0, 4).uppercase()
            db.collection("rooms").document(code).set(mapOf("userA" to uid, "userB" to null)).await()
            saveCode(code)
            listenToRoom(code)
            listenToMessages(code)
        } catch (e: Exception) {
            _errorMessage.value = "Create Room Error:\n${e.stackTraceToString()}"
        }
    }

    suspend fun joinRoom(code: String) {
        try {
            initAuth()
            val uid = auth.currentUser?.uid ?: throw Exception("Auth failed")
            db.collection("rooms").document(code).update("userB", uid).await()
            saveCode(code)
            _paired.value = true
            listenToRoom(code)
            listenToMessages(code)
        } catch (e: Exception) {
            _errorMessage.value = "Join Room Error:\n${e.stackTraceToString()}"
        }
    }
    
    private fun saveCode(code: String) {
        _roomCode.value = code
        prefs?.edit()?.putString("ROOM_CODE", code)?.apply()
    }

    private fun listenToRoom(code: String) {
        roomListener?.remove()
        roomListener = db.collection("rooms").document(code).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val userB = snapshot.getString("userB")
                if (userB != null) _paired.value = true
            }
        }
    }

    private fun listenToMessages(code: String) {
        messagesListener?.remove()
        messagesListener = db.collection("rooms").document(code)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val messagesList = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val senderId = data["senderId"] as? String ?: ""
                        if (senderId == auth.currentUser?.uid) return@mapNotNull null // Don't show our own sent messages in inbox
                        
                        val messageId = doc.id
                        val timestamp = data["timestamp"] as? Long ?: 0L
                        val seen = data["seen"] as? Boolean ?: false
                        
                        val pathsList = data["paths"] as? List<Map<String, Any>> ?: emptyList()
                        val parsedPaths = pathsList.map { pathMap ->
                            val color = (pathMap["color"] as? Number)?.toInt() ?: 0xFF333333.toInt()
                            val strokeWidth = (pathMap["strokeWidth"] as? Number)?.toFloat() ?: 5f
                            val pointsList = pathMap["points"] as? List<Map<String, Number>> ?: emptyList()
                            val points = pointsList.map { ptMap ->
                                com.us.app.data.model.Point(
                                    ptMap["x"]?.toFloat() ?: 0f, 
                                    ptMap["y"]?.toFloat() ?: 0f
                                )
                            }
                            StrokePath(points, color, strokeWidth)
                        }
                        
                        Message(
                            messageId = messageId,
                            roomId = code,
                            senderId = senderId,
                            paths = parsedPaths,
                            timestamp = timestamp,
                            seen = seen
                        )
                    }
                    
                    _inbox.value = messagesList
                    
                    // Show notification if there are unseen messages
                    hasNewMessage.value = messagesList.any { !it.seen }
                }
            }
    }
    
    suspend fun markAsRead(messageId: String) {
        val code = _roomCode.value ?: return
        try {
            db.collection("rooms").document(code).collection("messages").document(messageId).update("seen", true).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun sendDrawing(paths: List<StrokePath>) {
        val code = _roomCode.value ?: return
        val uid = auth.currentUser?.uid ?: return
        
        try {
            val pathsMapList = paths.map { path ->
                mapOf(
                    "color" to path.color,
                    "strokeWidth" to path.strokeWidth,
                    "points" to path.points.map { pt -> mapOf("x" to pt.x, "y" to pt.y) }
                )
            }
            
            val messageRef = db.collection("rooms").document(code).collection("messages").document()
            
            val messageMap = mapOf(
                "senderId" to uid,
                "timestamp" to System.currentTimeMillis(),
                "seen" to false,
                "paths" to pathsMapList
            )
            
            messageRef.set(messageMap).await()
            showCanvas.value = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun viewMessage(message: Message) {
        _receivedPaths.value = message.paths
        showInbox.value = false
        showCanvas.value = true
    }
    
    fun clearCanvasLocally() {
        _receivedPaths.value = emptyList()
    }
}
