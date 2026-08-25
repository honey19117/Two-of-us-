package com.us.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

class RoomRepository {

    private val db = FirebaseFirestore.getInstance()
    private val roomsCollection = db.collection("rooms")

    /**
     * Creates a new unique room code (e.g., "LOVE-8F2A") and registers the creator's device token.
     */
    suspend fun createRoom(myDeviceToken: String): String {
        val shortCode = "LOVE-" + UUID.randomUUID().toString().substring(0, 4).uppercase()
        
        val roomData = hashMapOf(
            "roomCode" to shortCode,
            "partnerAToken" to myDeviceToken,
            "partnerBToken" to null, // Waiting for GF to join
            "createdAt" to System.currentTimeMillis()
        )
        
        // We use the shortCode as the document ID for easy lookup
        roomsCollection.document(shortCode).set(roomData).await()
        
        return shortCode
    }

    /**
     * Joins an existing room using the code and registers the second device token.
     */
    suspend fun joinRoom(roomCode: String, myDeviceToken: String): Boolean {
        val roomDoc = roomsCollection.document(roomCode.uppercase())
        val snapshot = roomDoc.get().await()

        if (snapshot.exists()) {
            val currentBToken = snapshot.getString("partnerBToken")
            if (currentBToken == null) {
                // Room is open, join as Partner B
                roomDoc.update("partnerBToken", myDeviceToken).await()
                return true
            } else {
                // Security: Room already has two people, reject third party
                return false
            }
        }
        return false // Room doesn't exist
    }

    /**
     * Fetches the partner's token to send the silent push notification.
     */
    suspend fun getPartnerToken(roomCode: String, myToken: String): String? {
        val snapshot = roomsCollection.document(roomCode).get().await()
        if (snapshot.exists()) {
            val tokenA = snapshot.getString("partnerAToken")
            val tokenB = snapshot.getString("partnerBToken")
            
            // Return whichever token is NOT ours
            return if (tokenA == myToken) tokenB else tokenA
        }
        return null
    }

    /**
     * Disconnects the room.
     */
    suspend fun unpair(roomCode: String) {
        roomsCollection.document(roomCode).delete().await()
    }
}
