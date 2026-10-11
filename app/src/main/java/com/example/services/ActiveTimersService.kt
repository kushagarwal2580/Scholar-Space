package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.ui.screens.LibraryViewModel
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class ActiveTimersService : Service() {
    companion object {
        const val CHANNEL_ID = "active_timers_service_v4"
        const val NOTIFICATION_ID = 20000
        var isServiceRunning = false
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var tickJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true; isLenient = true; explicitNulls = false }
    private var lastReminderCheckTime = 0L

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Timers Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Keeps timers and stopwatches running in the background"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        
        startTicking()
    }

    private fun stopServiceCompletely() {
        isServiceRunning = false
        stopTicking()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action != null) {
            val hasRunningRemaining = handleAction(intent)
            if (!hasRunningRemaining) {
                stopServiceCompletely()
                return START_NOT_STICKY
            }
        }

        val notification = com.example.ui.notifications.NotificationHelper.getActiveTimersNotification(this)
        if (notification == null) {
            stopServiceCompletely()
            return START_NOT_STICKY
        }
        
        isServiceRunning = true
        startForeground(NOTIFICATION_ID, notification)
        startTicking()
        
        return START_STICKY
    }

    private fun handleAction(intent: Intent): Boolean {
        val action = intent.action ?: return false
        val prefs = getSharedPreferences("LibraryPrefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("app_state", null) ?: return false
        val data = try { json.decodeFromString<com.example.ui.screens.AppStateData>(jsonStr) } catch(e:Exception) { return false }
        
        val now = System.currentTimeMillis()
        val elapsedSeconds = ((now - data.timestamp) / 1000).toInt()
        
        val adjustedTimers = data.timers.map {
            if (it.isRunning) {
                val newTime = (it.timeRemaining - elapsedSeconds).coerceAtLeast(0)
                it.copy(timeRemaining = newTime, isRunning = newTime > 0)
            } else it
        }
        val adjustedStopwatches = data.stopwatches.map {
            if (it.isRunning) {
                val el = it.elapsedMillis + (now - data.timestamp)
                it.copy(elapsedMillis = el, startTime = now - el)
            } else it
        }
        
        var finalTimers = adjustedTimers
        var finalStopwatches = adjustedStopwatches
        
        when (action) {
            "com.example.ACTION_TOGGLE_TIMER" -> {
                val id = intent.getIntExtra("TIMER_ID", -1)
                finalTimers = adjustedTimers.map { if (it.id == id) it.copy(isRunning = !it.isRunning) else it }
                finalTimers.find { it.id == id }?.let { com.example.ui.notifications.NotificationHelper.updateTimerNotification(this, it) }
            }
            "com.example.ACTION_RESET_TIMER" -> {
                val id = intent.getIntExtra("TIMER_ID", -1)
                finalTimers = adjustedTimers.map { if (it.id == id) it.copy(isRunning = false, timeRemaining = it.durationMinutes * 60 + it.durationSeconds) else it }
                finalTimers.find { it.id == id }?.let { com.example.ui.notifications.NotificationHelper.updateTimerNotification(this, it) }
            }
            "com.example.ACTION_TOGGLE_STOPWATCH" -> {
                val id = intent.getIntExtra("STOPWATCH_ID", -1)
                finalStopwatches = adjustedStopwatches.map { 
                    if (it.id == id) {
                        if (it.isRunning) it.copy(isRunning = false, elapsedMillis = now - it.startTime)
                        else it.copy(isRunning = true, startTime = now - it.elapsedMillis)
                    } else it 
                }
                finalStopwatches.find { it.id == id }?.let { com.example.ui.notifications.NotificationHelper.updateStopwatchNotification(this, it) }
            }
            "com.example.ACTION_RESET_STOPWATCH" -> {
                val id = intent.getIntExtra("STOPWATCH_ID", -1)
                finalStopwatches = adjustedStopwatches.map { if (it.id == id) it.copy(isRunning = false, elapsedMillis = 0L, startTime = now) else it }
                finalStopwatches.find { it.id == id }?.let { com.example.ui.notifications.NotificationHelper.updateStopwatchNotification(this, it) }
            }
        }
        
        val updatedData = data.copy(timers = finalTimers, stopwatches = finalStopwatches, timestamp = now)
        prefs.edit().putString("app_state", json.encodeToString(updatedData)).apply()

        LibraryViewModel.activeInstance?.syncFromExternalUpdate(finalTimers, finalStopwatches)

        val hasAnyRunning = finalTimers.any { it.isRunning && it.timeRemaining > 0 } || finalStopwatches.any { it.isRunning }
        return hasAnyRunning
    }

    private fun startTicking() {
        if (tickJob != null && tickJob?.isActive == true) return
        tickJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                tickServiceTimers()
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun tickServiceTimers() {
        try {
            val prefs = getSharedPreferences("LibraryPrefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("app_state", null) ?: return
            val data = json.decodeFromString<com.example.ui.screens.AppStateData>(jsonStr)
            
            val nowMs = System.currentTimeMillis()
            var stateChanged = false
            var completedTimer = false
            
            // On each background tick, we calculate elapsed seconds relative to the state's recorded timestamp
            val elapsedSeconds = ((nowMs - data.timestamp) / 1000).toInt()
            
            // 1. Tick Timers with precision
            val updatedTimers = if (elapsedSeconds > 0) {
                data.timers.map {
                    if (it.isRunning && it.timeRemaining > 0) {
                        val newRemaining = (it.timeRemaining - elapsedSeconds).coerceAtLeast(0)
                        val updated = if (newRemaining == 0) {
                            completedTimer = true
                            val displayName = if (it.title.isNotBlank()) it.title else "${it.durationMinutes}-minute timer"
                            com.example.ui.notifications.NotificationHelper.showCustomNotification(this, "Timer Complete", "$displayName has finished.")
                            playNotificationSoundAndVibrate()
                            it.copy(timeRemaining = 0, isRunning = false)
                        } else {
                            it.copy(timeRemaining = newRemaining)
                        }
                        stateChanged = true
                        updated
                    } else if (it.isRunning && it.timeRemaining <= 0) {
                        completedTimer = true
                        it.copy(timeRemaining = 0, isRunning = false)
                    } else {
                        it
                    }
                }
            } else {
                data.timers
            }
            
            // 2. Tick Stopwatches
            val updatedStopwatches = data.stopwatches.map {
                if (it.isRunning) {
                    stateChanged = true
                    it.copy(elapsedMillis = nowMs - it.startTime)
                } else {
                    it
                }
            }

            // 3. Periodic checking for calendar reminders & daily counters
            var updatedReminders = data.reminders
            var updatedCounters = data.dayCounters
            if (nowMs - lastReminderCheckTime >= 10000L) {
                lastReminderCheckTime = nowMs
                
                val zoneId = java.time.ZoneId.systemDefault()
                val nowZoned = java.time.ZonedDateTime.now(zoneId)
                val todayMillis = nowZoned.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli().toString()
                
                // Reminders Check
                val mutableReminders = data.reminders.toMutableMap()
                var reminderChanged = false
                
                data.reminders.forEach { (dateKey, remindersList) ->
                    val dateMillis = dateKey.toLongOrNull()
                    if (dateMillis != null) {
                        val localDate = java.time.Instant.ofEpochMilli(dateMillis)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                        
                        val updatedList = remindersList.map { reminder ->
                            if (!reminder.isNotified) {
                                try {
                                    val parts = reminder.time.split(" ")
                                    if (parts.size == 2) {
                                        val timeParts = parts[0].split(":")
                                        if (timeParts.size == 2) {
                                            var hour = timeParts[0].toIntOrNull() ?: 0
                                            val minute = timeParts[1].toIntOrNull() ?: 0
                                            if (parts[1].uppercase(java.util.Locale.US) == "PM" && hour < 12) hour += 12
                                            if (parts[1].uppercase(java.util.Locale.US) == "AM" && hour == 12) hour = 0
                                            
                                            val scheduledDateTime = localDate.atTime(hour, minute).atZone(zoneId)
                                            if (!nowZoned.isBefore(scheduledDateTime)) {
                                                com.example.ui.notifications.NotificationHelper.showCustomNotification(this, "Reminder", reminder.text)
                                                reminderChanged = true
                                                return@map reminder.copy(isNotified = true)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ActiveTimersService", "Error parsing reminder time", e)
                                }
                            }
                            reminder
                        }
                        if (updatedList != remindersList) {
                            mutableReminders[dateKey] = updatedList
                            reminderChanged = true
                        }
                    }
                }
                if (reminderChanged) {
                    updatedReminders = mutableReminders
                    stateChanged = true
                }
                
                // Day Counters Check
                var counterChanged = false
                val nextCounters = data.dayCounters.map { counter ->
                    val targetDate = java.time.Instant.ofEpochMilli(if (counter.targetDateMillis > 0L) counter.targetDateMillis else nowZoned.toInstant().toEpochMilli()).atZone(zoneId).toLocalDate()
                    val computedDaysLeft = java.time.temporal.ChronoUnit.DAYS.between(nowZoned.toLocalDate(), targetDate).toInt().coerceAtLeast(0)
                    
                    var updated = if (computedDaysLeft != counter.daysLeft) {
                        counterChanged = true
                        counter.copy(daysLeft = computedDaysLeft)
                    } else {
                        counter
                    }
                    
                    if (updated.daysLeft > 0 && updated.lastNotifiedDay != nowZoned.dayOfYear && nowZoned.hour >= 9) {
                        com.example.ui.notifications.NotificationHelper.showCustomNotification(this, "Day Counter", "${updated.daysLeft} days left for ${updated.title}")
                        counterChanged = true
                        updated = updated.copy(lastNotifiedDay = nowZoned.dayOfYear)
                    }
                    updated
                }
                if (counterChanged) {
                    updatedCounters = nextCounters
                    stateChanged = true
                }
            }

            if (stateChanged) {
                val nextTimestamp = if (elapsedSeconds > 0) data.timestamp + (elapsedSeconds * 1000L) else data.timestamp
                val updatedData = data.copy(
                    timers = updatedTimers,
                    stopwatches = updatedStopwatches,
                    reminders = updatedReminders,
                    dayCounters = updatedCounters,
                    timestamp = nextTimestamp
                )
                prefs.edit().putString("app_state", json.encodeToString(updatedData)).apply()
            }
            
            // 4. Update individual notifications for running items and finished timers
            updatedTimers.forEach { newTimer ->
                if (newTimer.isRunning && newTimer.timeRemaining > 0) {
                    com.example.ui.notifications.NotificationHelper.updateTimerNotification(this, newTimer)
                } else {
                    val oldTimer = data.timers.find { it.id == newTimer.id }
                    if (oldTimer != null && oldTimer.isRunning && newTimer.timeRemaining <= 0) {
                        com.example.ui.notifications.NotificationHelper.updateTimerNotification(this, newTimer)
                    }
                }
            }
            updatedStopwatches.forEach { sw ->
                if (sw.isRunning) {
                    com.example.ui.notifications.NotificationHelper.updateStopwatchNotification(this, sw)
                }
            }
            
            // Determine if there is any active item still running
            val hasRunning = updatedTimers.any { it.isRunning && it.timeRemaining > 0 } || updatedStopwatches.any { it.isRunning }
            if (hasRunning) {
                val foregroundNotification = com.example.ui.notifications.NotificationHelper.getActiveTimersNotification(this)
                if (foregroundNotification != null) {
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, foregroundNotification)
                }
            } else {
                stopServiceCompletely()
            }
        } catch (e: Exception) {
            android.util.Log.e("ActiveTimersService", "Error ticking in background service", e)
        }
    }

    private fun playNotificationSoundAndVibrate() {
        try {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()
            
            val ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                
            val ringtone = android.media.RingtoneManager.getRingtone(this, ringtoneUri)
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.audioAttributes = audioAttributes
                }
                ringtone.play()
            }
            
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ActiveTimersService", "Failed to play notification sound/vibration", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        stopTicking()
        serviceJob.cancel()
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
