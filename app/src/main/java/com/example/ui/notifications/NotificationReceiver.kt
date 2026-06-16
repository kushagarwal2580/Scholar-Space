package com.example.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import com.example.ui.screens.AppStateData
import com.example.ui.screens.StopwatchItem
import com.example.ui.screens.TimerItem
import com.example.ui.screens.LibraryViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class NotificationReceiver : BroadcastReceiver() {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true; isLenient = true; explicitNulls = false }

        fun scheduleNextAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = "com.example.ACTION_ALARM_TICK"
                }
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    999, // Specific request code for the periodic tick
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                
                val zoneId = java.time.ZoneId.systemDefault()
                val nowZoned = java.time.ZonedDateTime.now(zoneId)
                val nowMs = System.currentTimeMillis()
                
                // Default to tomorrow 9:00 AM local time (Day Counter update check)
                var nextAlarmTime = nowZoned.toLocalDate().atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli()
                if (nextAlarmTime <= nowMs) {
                    nextAlarmTime = nowZoned.toLocalDate().plusDays(1).atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli()
                }
                
                val prefs = context.getSharedPreferences("LibraryPrefs", Context.MODE_PRIVATE)
                val jsonStr = prefs.getString("app_state", null)
                if (jsonStr != null) {
                    val data = json.decodeFromString<AppStateData>(jsonStr)
                    
                    data.reminders.forEach { (dateKey, remindersList) ->
                        val dateMillis = dateKey.toLongOrNull()
                        if (dateMillis != null) {
                            val localDate = java.time.Instant.ofEpochMilli(dateMillis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                                
                            remindersList.forEach { reminder ->
                                if (!reminder.isNotified) {
                                    try {
                                        val parts = reminder.time.split(" ")
                                        if (parts.size == 2) {
                                            val timeParts = parts[0].split(":")
                                            if (timeParts.size == 2) {
                                                var hour = timeParts[0].toIntOrNull() ?: 0
                                                val minute = timeParts[1].toIntOrNull() ?: 0
                                                if (parts[1].uppercase(java.util.Locale.US) == "PM" && hour < 12) hour += 12
                                                if (parts[1].uppercase(java.util.Locale.US) == "AM" && hour == 12) hour =  0
                                                
                                                val scheduledDateTime = localDate.atTime(hour, minute).atZone(zoneId)
                                                val reminderEpoch = scheduledDateTime.toInstant().toEpochMilli()
                                                
                                                // Schedule strictly for the earliest future unnotified reminder
                                                if (reminderEpoch > nowMs && reminderEpoch < nextAlarmTime) {
                                                    nextAlarmTime = reminderEpoch
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("NotificationReceiver", "Error parsing reminder time during scheduling", e)
                                    }
                                }
                            }
                        }
                    }
                }
                
                val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }

                if (canScheduleExact) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
                }
                Log.d("NotificationReceiver", "Scheduled next alarm check for: " + java.time.Instant.ofEpochMilli(nextAlarmTime).atZone(zoneId).toString())
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Error scheduling next alarm", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val stopwatchId = intent.getIntExtra("STOPWATCH_ID", -1)
        val timerId = intent.getIntExtra("TIMER_ID", -1)

        val activeVm = LibraryViewModel.activeInstance
        Log.d("NotificationReceiver", "Received action: $action")

        // Handle alarm ticks or boot/power events
        if (action == "com.example.ACTION_ALARM_TICK" || 
            action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON") {
            
            // Only perform background checks if the app is NOT active in foreground
            if (activeVm == null || !activeVm.isAppInForeground) {
                checkRemindersAndCountersAndTimers(context)
            }
            
            // Schedule next alarm AFTER performing checks to ensure fresh state in SharedPreferences!
            scheduleNextAlarm(context)
            return
        }

        if (activeVm != null) {
            // App is open, delegate to the active ViewModel
            when (action) {
                "com.example.ACTION_TOGGLE_STOPWATCH" -> {
                    if (stopwatchId != -1) activeVm.toggleStopwatch(stopwatchId)
                }
                "com.example.ACTION_TOGGLE_TIMER" -> {
                    if (timerId != -1) activeVm.toggleTimer(timerId)
                }
            }
        } else {
            // App is closed, handle in the background and update SharedPreferences
            try {
                val prefs = context.getSharedPreferences("LibraryPrefs", Context.MODE_PRIVATE)
                val jsonStr = prefs.getString("app_state", null)
                if (jsonStr != null) {
                    val data = json.decodeFromString<AppStateData>(jsonStr)
                    val now = System.currentTimeMillis()
                    val elapsedSeconds = ((now - data.timestamp) / 1000).toInt()
                    
                    // 1. First, adjust all currently running timers and stopwatches for the elapsed time since last save!
                    val adjustedTimers = data.timers.map {
                        if (it.isRunning) {
                            val newTime = (it.timeRemaining - elapsedSeconds).coerceAtLeast(0)
                            val isStillRunning = newTime > 0
                            it.copy(timeRemaining = newTime, isRunning = isStillRunning)
                        } else {
                            it
                        }
                    }
                    
                    val adjustedStopwatches = data.stopwatches.map {
                        if (it.isRunning) {
                            val elapsedSinceLastSave = now - data.timestamp
                            val totalElapsed = it.elapsedMillis + elapsedSinceLastSave
                            it.copy(
                                elapsedMillis = totalElapsed,
                                startTime = now - totalElapsed
                            )
                        } else {
                            it
                        }
                    }
                    
                    // 2. Apply the toggle action on the ADJUSTED lists!
                    var finalTimers = adjustedTimers
                    var finalStopwatches = adjustedStopwatches
                    
                    if (action == "com.example.ACTION_TOGGLE_STOPWATCH" && stopwatchId != -1) {
                        finalStopwatches = adjustedStopwatches.map {
                            if (it.id == stopwatchId) {
                                if (it.isRunning) {
                                    // Pause the stopwatch
                                    it.copy(isRunning = false, elapsedMillis = now - it.startTime)
                                } else {
                                    // Resume the stopwatch
                                    it.copy(isRunning = true, startTime = now - it.elapsedMillis)
                                }
                            } else {
                                it
                            }
                        }
                        
                        // Update the notification for this stopwatch
                        val targetStopwatch = finalStopwatches.find { it.id == stopwatchId }
                        if (targetStopwatch != null) {
                            NotificationHelper.updateStopwatchNotification(context, targetStopwatch)
                        }
                    } else if (action == "com.example.ACTION_TOGGLE_TIMER" && timerId != -1) {
                        finalTimers = adjustedTimers.map {
                            if (it.id == timerId) {
                                it.copy(isRunning = !it.isRunning)
                            } else {
                                it
                            }
                        }
                        
                        // Update the notification for this timer
                        val targetTimer = finalTimers.find { it.id == timerId }
                        if (targetTimer != null) {
                            NotificationHelper.updateTimerNotification(context, targetTimer)
                        }
                    }
                    
                    val updatedData = data.copy(
                        timers = finalTimers,
                        stopwatches = finalStopwatches,
                        timestamp = now
                    )
                    
                    prefs.edit().putString("app_state", json.encodeToString(updatedData)).apply()

                    val hasRunning = finalTimers.any { it.isRunning && it.timeRemaining > 0 } || finalStopwatches.any { it.isRunning }
                    if (!hasRunning && com.example.services.ActiveTimersService.isServiceRunning) {
                        val serviceIntent = Intent(context, com.example.services.ActiveTimersService::class.java)
                        context.stopService(serviceIntent)
                    } else if (hasRunning && !com.example.services.ActiveTimersService.isServiceRunning) {
                        val serviceIntent = Intent(context, com.example.services.ActiveTimersService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Error processing background notification action", e)
            }
        }
    }

    private fun checkRemindersAndCountersAndTimers(context: Context) {
        if (com.example.services.ActiveTimersService.isServiceRunning) {
            return
        }
        try {
            val prefs = context.getSharedPreferences("LibraryPrefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("app_state", null) ?: return
            val data = json.decodeFromString<AppStateData>(jsonStr)
            val now = System.currentTimeMillis()
            val rawElapsed = ((now - data.timestamp) / 1000).toInt()
            val elapsedSeconds = if (rawElapsed > 0) rawElapsed else 0

            var stateChanged = false

            // 1. Check if any timers finished while app was closed
            var completedTimer = false
            val adjustedTimers = data.timers.map {
                if (it.isRunning) {
                    val newTime = (it.timeRemaining - elapsedSeconds).coerceAtLeast(0)
                    val isStillRunning = newTime > 0
                    if (!isStillRunning && it.timeRemaining > 0) {
                        completedTimer = true
                        val displayName = if (it.title.isNotBlank()) it.title else "${it.durationMinutes}-minute timer"
                        NotificationHelper.showCustomNotification(context, "Timer Complete", "$displayName has finished.")
                    }
                    it.copy(timeRemaining = newTime, isRunning = isStillRunning)
                } else {
                    it
                }
            }
            if (completedTimer || adjustedTimers != data.timers) {
                stateChanged = true
            }

            // 2. Adjust stopwatches
            val adjustedStopwatches = data.stopwatches.map {
                if (it.isRunning) {
                    val elapsedSinceLastSave = now - data.timestamp
                    val totalElapsed = it.elapsedMillis + elapsedSinceLastSave
                    stateChanged = true
                    it.copy(
                        elapsedMillis = totalElapsed,
                        startTime = now - totalElapsed
                    )
                } else {
                    it
                }
            }

            // 3. Reminders Check (Evaluate across all dates in map)
            val zoneId = java.time.ZoneId.systemDefault()
            val nowZoned = java.time.ZonedDateTime.now(zoneId)
            
            val newReminders = data.reminders.toMutableMap()
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
                                        if (parts[1].uppercase(java.util.Locale.US) == "AM" && hour == 12) hour =  0
                                        
                                        val scheduledDateTime = localDate.atTime(hour, minute).atZone(zoneId)
                                        if (!nowZoned.isBefore(scheduledDateTime)) {
                                            NotificationHelper.showCustomNotification(context, "Reminder", reminder.text)
                                            reminderChanged = true
                                            return@map reminder.copy(isNotified = true)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("NotificationReceiver", "Error parsing reminder time in background", e)
                            }
                        }
                        reminder
                    }
                    if (updatedList != remindersList) {
                        newReminders[dateKey] = updatedList
                        reminderChanged = true
                    }
                }
            }
            
            if (reminderChanged) {
                stateChanged = true
            }

            // 4. Day Counters
            var counterChanged = false
            val adjustedDayCounters = data.dayCounters.map { counter ->
                val targetDate = java.time.Instant.ofEpochMilli(if (counter.targetDateMillis > 0L) counter.targetDateMillis else nowZoned.toInstant().toEpochMilli()).atZone(zoneId).toLocalDate()
                val computedDaysLeft = java.time.temporal.ChronoUnit.DAYS.between(nowZoned.toLocalDate(), targetDate).toInt().coerceAtLeast(0)
                
                var updated = if (computedDaysLeft != counter.daysLeft) {
                    counterChanged = true
                    counter.copy(daysLeft = computedDaysLeft)
                } else {
                    counter
                }
                
                if (updated.daysLeft > 0 && updated.lastNotifiedDay != nowZoned.dayOfYear && nowZoned.hour >= 9) {
                    NotificationHelper.showCustomNotification(context, "Day Counter", "${updated.daysLeft} days left for ${updated.title}")
                    counterChanged = true
                    updated = updated.copy(lastNotifiedDay = nowZoned.dayOfYear)
                }
                updated
            }
            
            if (counterChanged) {
                stateChanged = true
            }

            // Save state if changed or after elapsed seconds to keep timestamps relatively fresh
            if (stateChanged || elapsedSeconds >= 10) {
                val updatedData = data.copy(
                    timers = adjustedTimers,
                    stopwatches = adjustedStopwatches,
                    reminders = newReminders,
                    dayCounters = adjustedDayCounters,
                    timestamp = now
                )
                prefs.edit().putString("app_state", json.encodeToString(updatedData)).apply()
            }

            val hasRunning = adjustedTimers.any { it.isRunning && it.timeRemaining > 0 } || adjustedStopwatches.any { it.isRunning }
            if (!hasRunning && com.example.services.ActiveTimersService.isServiceRunning) {
                val serviceIntent = Intent(context, com.example.services.ActiveTimersService::class.java)
                context.stopService(serviceIntent)
            } else if (hasRunning && !com.example.services.ActiveTimersService.isServiceRunning) {
                val serviceIntent = Intent(context, com.example.services.ActiveTimersService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationReceiver", "Error checking reminders/timers in background", e)
        }
    }
}
