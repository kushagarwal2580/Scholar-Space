package com.example.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R

object NotificationHelper {
    const val CHANNEL_ID_TIMER = "timers"
    const val CHANNEL_ID_STOPWATCH = "stopwatches"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val timerChannel = NotificationChannel(CHANNEL_ID_TIMER, "Timers", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(timerChannel)
            
            val stopwatchChannel = NotificationChannel(CHANNEL_ID_STOPWATCH, "Stopwatches", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(stopwatchChannel)
        }
    }

    fun updateStopwatchNotification(context: Context, item: com.example.ui.screens.StopwatchItem) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannels(context)
        
        val intent = android.content.Intent(context, com.example.ui.notifications.NotificationReceiver::class.java).apply {
            action = "com.example.ACTION_TOGGLE_STOPWATCH"
            putExtra("STOPWATCH_ID", item.id)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            item.id,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val contentIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            action = "com.example.ACTION_OPEN_CALENDAR"
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = android.app.PendingIntent.getActivity(
            context,
            Math.abs(item.id.hashCode()), // Unique request code
            contentIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val elapsed = item.elapsedMillis
        val hrs = (elapsed / 3600000)
        val minutes = ((elapsed % 3600000) / 60000)
        val seconds = ((elapsed % 60000) / 1000)
        val timeStr = String.format("%02d:%02d:%02d", hrs, minutes, seconds)
        
        val nameText = if (item.title.isNotBlank()) item.title else "Stopwatch"

        val remoteViews = android.widget.RemoteViews(context.packageName, R.layout.notification_timer)
        remoteViews.setTextViewText(R.id.notification_title, nameText)
        
        if (item.isRunning) {
            val formatStr = if (hrs > 0) null else "00:%s"
            remoteViews.setViewVisibility(R.id.notification_chronometer, android.view.View.VISIBLE)
            remoteViews.setChronometer(R.id.notification_chronometer, android.os.SystemClock.elapsedRealtime() - elapsed, formatStr, true)
            remoteViews.setViewVisibility(R.id.notification_time, android.view.View.GONE)
        } else {
            remoteViews.setViewVisibility(R.id.notification_chronometer, android.view.View.GONE)
            remoteViews.setViewVisibility(R.id.notification_time, android.view.View.VISIBLE)
            remoteViews.setTextViewText(R.id.notification_time, timeStr)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_STOPWATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(item.isRunning)
            .setContentIntent(contentPendingIntent)
            .setStyle(androidx.core.app.NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .addAction(
                if (item.isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (item.isRunning) "Pause" else "Play",
                pendingIntent
            )

        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(item.id, builder.build())
        }
    }

    fun updateTimerNotification(context: Context, item: com.example.ui.screens.TimerItem) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannels(context)
        
        val intent = android.content.Intent(context, com.example.ui.notifications.NotificationReceiver::class.java).apply {
            action = "com.example.ACTION_TOGGLE_TIMER"
            putExtra("TIMER_ID", item.id)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            10000 + item.id,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val contentIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            action = "com.example.ACTION_OPEN_CALENDAR"
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = android.app.PendingIntent.getActivity(
            context,
            Math.abs(item.id.hashCode()) + 10000, // Unique request code
            contentIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val remaining = item.timeRemaining
        val hrs = remaining / 3600
        val mins = (remaining % 3600) / 60
        val secs = remaining % 60
        val timeStr = String.format("%02d:%02d:%02d", hrs, mins, secs)
        
        val nameText = if (item.title.isNotBlank()) item.title else "Timer"

        val remoteViews = android.widget.RemoteViews(context.packageName, R.layout.notification_timer)
        remoteViews.setTextViewText(R.id.notification_title, nameText)

        if (remaining <= 0) {
            remoteViews.setTextViewText(R.id.notification_title, "Time's up!")
            remoteViews.setViewVisibility(R.id.notification_chronometer, android.view.View.GONE)
            remoteViews.setViewVisibility(R.id.notification_time, android.view.View.VISIBLE)
            remoteViews.setTextViewText(R.id.notification_time, nameText)
        } else if (item.isRunning) {
            val formatStr = if (hrs > 0) null else "00:%s"
            remoteViews.setViewVisibility(R.id.notification_chronometer, android.view.View.VISIBLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                remoteViews.setBoolean(R.id.notification_chronometer, "setCountDown", true)
            }
            remoteViews.setChronometer(R.id.notification_chronometer, android.os.SystemClock.elapsedRealtime() + (remaining * 1000L), formatStr, true)
            remoteViews.setViewVisibility(R.id.notification_time, android.view.View.GONE)
        } else {
            remoteViews.setViewVisibility(R.id.notification_chronometer, android.view.View.GONE)
            remoteViews.setViewVisibility(R.id.notification_time, android.view.View.VISIBLE)
            remoteViews.setTextViewText(R.id.notification_time, timeStr)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(item.isRunning && remaining > 0)
            .setContentIntent(contentPendingIntent)
            .setStyle(androidx.core.app.NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .addAction(
                if (item.isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (item.isRunning) "Pause" else "Play",
                pendingIntent
            )

        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(10000 + item.id, builder.build())
            if (remaining <= 0 && !item.isRunning) manager.cancel(10000 + item.id)
        }
    }

    fun cancelNotification(context: Context, id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }
}
