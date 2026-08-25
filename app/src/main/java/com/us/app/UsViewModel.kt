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
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode
    
    private val _paired = MutableStateFlow(false)
    val paired: StateFlow<Boolean> = _paired
    
    private val _receivedPaths = MutableStateFlow<List<StrokePath>>(emptyList())
    val receivedPaths: StateFlow<List<StrokePath>> = _receivedPaths

    private var roomListener: ListenerRegistration? = null

    init {
        viewModelScope.launch {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
        }
    }

    fun createRoom() {
        viewModelScope.launch {
            val code = "LOVE-" + UUID.randomUUID().toString().substring(0, 4).uppercase()
            val uid = auth.currentUser?.uid ?: return@launch
            db.collection("rooms").document(code).set(
                mapOf("userA" to uid, "userB" to null)
            ).await()
            _roomCode.value = code
            listenToRoom(code)
        }
    }

    fun joinRoom(code: String) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            db.collection("rooms").document(code).update("userB", uid).await()
            _roomCode.value = code
            _paired.value = true
            listenToRoom(code)
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
