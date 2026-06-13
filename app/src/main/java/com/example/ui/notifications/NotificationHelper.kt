package com.example.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R

object NotificationHelper {
    const val CHANNEL_ID_TIMER = "timers_v4"
    const val CHANNEL_ID_STOPWATCH = "stopwatches_v4"

    private fun getBitmapFromVector(context: Context, drawableId: Int): android.graphics.Bitmap? {
        try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId) ?: return null
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                return drawable.bitmap
            }
            val bitmap = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth.takeIf { it > 0 } ?: 108,
                drawable.intrinsicHeight.takeIf { it > 0 } ?: 108,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) {
            return null
        }
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val timerChannel = NotificationChannel(CHANNEL_ID_TIMER, "Timers", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(timerChannel)
            
            val stopwatchChannel = NotificationChannel(CHANNEL_ID_STOPWATCH, "Stopwatches", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(stopwatchChannel)
        }
    }

    fun buildStopwatchNotification(context: Context, item: com.example.ui.screens.StopwatchItem): android.app.Notification {
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
        val timeStr = if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
        
        val nameText = if (item.title.isNotBlank()) item.title else "Stopwatch"

        val remoteViews = android.widget.RemoteViews(context.packageName, R.layout.notification_timer)
        remoteViews.setTextViewText(R.id.notification_title, nameText)
        
        if (item.isRunning) {
            remoteViews.setViewVisibility(R.id.notification_chronometer, android.view.View.VISIBLE)
            remoteViews.setChronometer(R.id.notification_chronometer, android.os.SystemClock.elapsedRealtime() - elapsed, null, true)
            remoteViews.setViewVisibility(R.id.notification_time, android.view.View.GONE)
        } else {
            remoteViews.setViewVisibility(R.id.notification_chronometer, android.view.View.GONE)
            remoteViews.setViewVisibility(R.id.notification_time, android.view.View.VISIBLE)
            remoteViews.setTextViewText(R.id.notification_time, timeStr)
        }

        val largeIcon = getBitmapFromVector(context, com.example.R.mipmap.ic_launcher)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_STOPWATCH)
            .setSmallIcon(com.example.R.drawable.ic_stat_logo)
            .setContentTitle(nameText)
            .setContentText(if (item.isRunning) "Running: $timeStr" else "Paused: $timeStr")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setOngoing(item.isRunning)
            .setContentIntent(contentPendingIntent)
            .setStyle(androidx.core.app.NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .addAction(
                if (item.isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (item.isRunning) "Pause" else "Play",
                pendingIntent
            )

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        return builder.build()
    }

    fun updateStopwatchNotification(context: Context, item: com.example.ui.screens.StopwatchItem) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildStopwatchNotification(context, item)
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(item.id, notification)
            if (!item.isRunning && item.elapsedMillis <= 0) {
                manager.cancel(item.id)
            }
        }
    }

    fun buildTimerNotification(context: Context, item: com.example.ui.screens.TimerItem): android.app.Notification {
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
        val timeStr = if (hrs > 0) {
            String.format("%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
        
        val nameText = if (item.title.isNotBlank()) item.title else "Timer"

        val remoteViews = android.widget.RemoteViews(context.packageName, R.layout.notification_timer)
        remoteViews.setTextViewText(R.id.notification_title, nameText)

        if (remaining <= 0) {
            remoteViews.setTextViewText(R.id.notification_title, "Time's up!")
            remoteViews.setViewVisibility(R.id.notification_chronometer, android.view.View.GONE)
            remoteViews.setViewVisibility(R.id.notification_time, android.view.View.VISIBLE)
            remoteViews.setTextViewText(R.id.notification_time, nameText)
        } else if (item.isRunning) {
            remoteViews.setViewVisibility(R.id.notification_chronometer, android.view.View.VISIBLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                remoteViews.setBoolean(R.id.notification_chronometer, "setCountDown", true)
            }
            remoteViews.setChronometer(R.id.notification_chronometer, android.os.SystemClock.elapsedRealtime() + (remaining * 1000L), null, true)
            remoteViews.setViewVisibility(R.id.notification_time, android.view.View.GONE)
        } else {
            remoteViews.setViewVisibility(R.id.notification_chronometer, android.view.View.GONE)
            remoteViews.setViewVisibility(R.id.notification_time, android.view.View.VISIBLE)
            remoteViews.setTextViewText(R.id.notification_time, timeStr)
        }

        val largeIcon = getBitmapFromVector(context, com.example.R.mipmap.ic_launcher)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_TIMER)
            .setSmallIcon(com.example.R.drawable.ic_stat_logo)
            .setContentTitle(nameText)
            .setContentText(if (remaining <= 0) "Time's up!" else "$timeStr remaining")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true)
            .setOngoing(item.isRunning && remaining > 0)
            .setContentIntent(contentPendingIntent)
            .setStyle(androidx.core.app.NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .addAction(
                if (item.isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (item.isRunning) "Pause" else "Play",
                pendingIntent
            )

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        return builder.build()
    }

    fun updateTimerNotification(context: Context, item: com.example.ui.screens.TimerItem) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val remaining = item.timeRemaining
        val notification = buildTimerNotification(context, item)
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(10000 + item.id, notification)
            if (remaining <= 0 && !item.isRunning) {
                manager.cancel(10000 + item.id)
            }
        }
    }

    fun getActiveTimersNotification(context: Context): android.app.Notification? {
        val prefs = context.getSharedPreferences("LibraryPrefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("app_state", null) ?: return null
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
            val data = json.decodeFromString<com.example.ui.screens.AppStateData>(jsonStr)
            
            val activeTimersCount = data.timers.count { it.timeRemaining > 0 }
            val activeStopwatchesCount = data.stopwatches.count { it.isRunning || it.elapsedMillis > 0L }
            
            if (activeTimersCount == 0 && activeStopwatchesCount == 0) return null
            
            val runningTimersCount = data.timers.count { it.isRunning && it.timeRemaining > 0 }
            val runningStopwatchesCount = data.stopwatches.count { it.isRunning }
            
            val title = "Scholar Space"
            val message = when {
                runningTimersCount > 0 && runningStopwatchesCount > 0 -> 
                    "$runningTimersCount timers & $runningStopwatchesCount stopwatches active"
                runningTimersCount > 0 -> 
                    "$runningTimersCount active timer" + (if (runningTimersCount > 1) "s" else "") + " running"
                runningStopwatchesCount > 0 -> 
                    "$runningStopwatchesCount active stopwatch" + (if (runningStopwatchesCount > 1) "es" else "") + " running"
                else -> "Active timers/stopwatches are paused"
            }
            
            val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                action = "com.example.ACTION_OPEN_CALENDAR"
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                20000,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            val largeIcon = getBitmapFromVector(context, com.example.R.mipmap.ic_launcher)
            
            val builder = NotificationCompat.Builder(context, com.example.services.ActiveTimersService.CHANNEL_ID)
                .setSmallIcon(com.example.R.drawable.ic_stat_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(runningTimersCount > 0 || runningStopwatchesCount > 0)
                .setContentIntent(pendingIntent)
                
            if (largeIcon != null) {
                builder.setLargeIcon(largeIcon)
            }
            
            builder.build()
        } catch (e: Exception) {
            null
        }
    }

    fun cancelNotification(context: Context, id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }

    fun showCustomNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Ensure channels exist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("reminders_v4", "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val app = context.applicationContext
        val intent = android.content.Intent(app, com.example.MainActivity::class.java).apply {
            action = "com.example.ACTION_OPEN_CALENDAR"
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            app, 
            987, 
            intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = getBitmapFromVector(app, com.example.R.mipmap.ic_launcher)

        val builder = NotificationCompat.Builder(app, "reminders_v4")
            .setSmallIcon(com.example.R.drawable.ic_stat_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        val notificationId = when (title) {
            "Day Counter" -> 30001
            "Reminder" -> 30002
            "Timer Complete" -> 30003
            else -> (System.currentTimeMillis() % 10000).toInt()
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(notificationId, builder.build())
        }
    }
}
