package com.us.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.us.app.MainActivity
import com.us.app.R
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FCMService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val imageUrl = remoteMessage.data["imageUrl"]
        
        if (imageUrl != null) {
            // Download the actual handwritten drawing
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = downloadImage(imageUrl)
                if (bitmap != null) {
                    showDrawingNotification(bitmap)
                    showFloatingOverlayIfUnlocked(imageUrl)
                }
            }
        }
    }

    private suspend fun downloadImage(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection()
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            null
        }
    }

    private fun showDrawingNotification(bitmap: Bitmap) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "love_brush_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Love Brush Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to open the reply canvas
        val replyIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_canvas", true)
        }
        val replyPendingIntent = PendingIntent.getActivity(
            this, 0, replyIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Use BigPictureStyle to show the ACTUAL handwriting in the lock screen/notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("Your person sent you something ❤️")
            .setContentText("Expand to see their handwriting")
            .setLargeIcon(bitmap)
            .setStyle(NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .bigLargeIcon(null as Bitmap?)) // Hide large icon when expanded
            .addAction(android.R.drawable.sym_def_app_icon, "Reply ❤️", replyPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showFloatingOverlayIfUnlocked(imageUrl: String) {
        // If the device is currently unlocked and in use, we launch the floating overlay service
        // to directly display the drawing on the screen without them opening the app.
        val intent = Intent(this, LoveBrushOverlayService::class.java).apply {
            putExtra("show_drawing_url", imageUrl)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send this token to Firebase so your GF's phone knows how to reach you
    }
}
