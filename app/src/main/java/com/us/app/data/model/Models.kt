
package com.us.app.data.model

data class Room(
    val roomId: String = ",
    val partnerAId: String = ",
    val partnerBId: String = ",
    val partnerAName: String = ",
    val partnerBName: String = ",
    val createdAt: Long = System.currentTimeMillis()
)

data class Message(
    val messageId: String = ",
    val roomId: String = ",
    val senderId: String = ",
    val paths: List<StrokePath> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val seen: Boolean = false
)

data class StrokePath(
    val points: List<Point> = emptyList(),
    val color: Int = 0xFF333333.toInt(),
    val strokeWidth: Float = 5f
)

data class Point(val x: Float = 0f, val y: Float = 0f)

