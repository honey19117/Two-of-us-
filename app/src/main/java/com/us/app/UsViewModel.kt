package com.us.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.us.app.data.model.StrokePath
import com.us.app.data.model.Point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class UsViewModel : ViewModel() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode
    
    private val _paired = MutableStateFlow(false)
    val paired: StateFlow<Boolean> = _paired
    
    private val _receivedPaths = MutableStateFlow<List<StrokePath>>(emptyList())
    val receivedPaths: StateFlow<List<StrokePath>> = _receivedPaths

    private var roomListener: ListenerRegistration? = null

    init {
        viewModelScope.launch {
            try {
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun createRoom() {
        viewModelScope.launch {
            try {
                if (auth.currentUser == null) auth.signInAnonymously().await()
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    _errorMessage.value = "Failed to log in anonymously. Enable Anonymous Auth in Firebase."
                    return@launch
                }
                val code = "LOVE-" + UUID.randomUUID().toString().substring(0, 4).uppercase()
                db.collection("rooms").document(code).set(
                    mapOf("userA" to uid, "userB" to null)
                ).await()
                _roomCode.value = code
                listenToRoom(code)
            } catch (e: Exception) {
                _errorMessage.value = "Create Room Error: ${e.message}"
            }
        }
    }

    fun joinRoom(code: String) {
        viewModelScope.launch {
            try {
                if (auth.currentUser == null) auth.signInAnonymously().await()
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    _errorMessage.value = "Failed to log in anonymously."
                    return@launch
                }
                db.collection("rooms").document(code).update("userB", uid).await()
                _roomCode.value = code
                _paired.value = true
                listenToRoom(code)
            } catch (e: Exception) {
                _errorMessage.value = "Join Room Error: ${e.message}"
            }
        }
    }

    private fun listenToRoom(code: String) {
        roomListener?.remove()
        roomListener = db.collection("rooms").document(code)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val userB = snapshot.getString("userB")
                    if (userB != null) {
                        _paired.value = true
                    }
                }
            }
    }
    
    fun sendDrawing(paths: List<StrokePath>) {
        // Simplified for prototype
    }
}
