package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Handle data payload (e.g. forced updates)
        if (remoteMessage.data.isNotEmpty()) {
            val type = remoteMessage.data["type"]
            if (type == "update_available") {
                // If you want to handle specific metadata updates
            }
        }
        
        // Handle notification payload
        remoteMessage.notification?.let {
            sendNotification(it.title, it.body)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send token to your server if needed
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val channelId = "fcm_default_channel"
        
        var largeIcon: android.graphics.Bitmap? = null
        try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_launcher)
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                largeIcon = drawable.bitmap
            } else if (drawable != null) {
                val bitmap = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth.takeIf { it > 0 } ?: 108,
                    drawable.intrinsicHeight.takeIf { it > 0 } ?: 108,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                largeIcon = bitmap
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(title ?: "New Notification")
            .setContentText(messageBody ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody ?: ""))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            
        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FCM Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}
