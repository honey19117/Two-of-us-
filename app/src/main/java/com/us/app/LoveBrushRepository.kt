package com.us.app

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
    
    private val _receivedPaths = MutableStateFlow<List<StrokePath>>(emptyList())
    val receivedPaths: StateFlow<List<StrokePath>> = _receivedPaths

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var roomListener: ListenerRegistration? = null
    
    val showCanvas = MutableStateFlow(false)
    val hasNewMessage = MutableStateFlow(false)

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("LoveBrushPrefs", Context.MODE_PRIVATE)
            val savedCode = prefs?.getString("ROOM_CODE", null)
            if (savedCode != null) {
                _roomCode.value = savedCode
                listenToRoom(savedCode)
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
                
                val lastMessage = snapshot.get("lastMessage") as? Map<String, Any>
                if (lastMessage != null) {
                    val senderId = lastMessage["senderId"] as? String
                    if (senderId != auth.currentUser?.uid) {
                        try {
                            val pathsList = lastMessage["paths"] as? List<Map<String, Any>> ?: emptyList()
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
                            _receivedPaths.value = parsedPaths
                            hasNewMessage.value = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
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
            
            val messageMap = mapOf(
                "senderId" to uid,
                "timestamp" to System.currentTimeMillis(),
                "paths" to pathsMapList
            )
            
            db.collection("rooms").document(code).update("lastMessage", messageMap).await()
            showCanvas.value = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
