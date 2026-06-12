package com.example.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.ui.screens.AppStateData
import com.example.ui.screens.StopwatchItem
import com.example.ui.screens.TimerItem
import com.example.ui.screens.LibraryViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class NotificationReceiver : BroadcastReceiver() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val stopwatchId = intent.getIntExtra("STOPWATCH_ID", -1)
        val timerId = intent.getIntExtra("TIMER_ID", -1)

        val activeVm = LibraryViewModel.activeInstance
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
                }
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Error processing background notification action", e)
            }
        }
    }
}
