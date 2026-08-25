package com.us.app

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

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode
    
    private val _paired = MutableStateFlow(false)
    val paired: StateFlow<Boolean> = _paired
    
    private val _receivedPaths = MutableStateFlow<List<StrokePath>>(emptyList())
    val receivedPaths: StateFlow<List<StrokePath>> = _receivedPaths

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var roomListener: ListenerRegistration? = null
    
    // UI state for overlay
    val showCanvas = MutableStateFlow(false)

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
            _roomCode.value = code
            listenToRoom(code)
        } catch (e: Exception) {
            _errorMessage.value = "Create Room Error: "
        }
    }

    suspend fun joinRoom(code: String) {
        try {
            initAuth()
            val uid = auth.currentUser?.uid ?: throw Exception("Auth failed")
            db.collection("rooms").document(code).update("userB", uid).await()
            _roomCode.value = code
            _paired.value = true
            listenToRoom(code)
        } catch (e: Exception) {
            _errorMessage.value = "Join Room Error: "
        }
    }

    private fun listenToRoom(code: String) {
        roomListener?.remove()
        roomListener = db.collection("rooms").document(code).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val userB = snapshot.getString("userB")
                if (userB != null) _paired.value = true
                
                // Real app would parse the paths from snapshot here and set _receivedPaths
                // and if new paths arrived, set showCanvas.value = true
            }
        }
    }
    
    suspend fun sendDrawing(paths: List<StrokePath>) {
        val code = _roomCode.value ?: return
        // Real app would upload paths here
        showCanvas.value = false
    }
}
