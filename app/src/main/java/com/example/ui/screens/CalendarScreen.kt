package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassBackground
import com.example.ui.components.glassMorphic
import com.example.ui.theme.Cyan400
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    libraryViewModel: LibraryViewModel,
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val timers by libraryViewModel.timers.collectAsState()
    var showAddTimerDialog by remember { mutableStateOf(false) }
    var newTimerTitle by remember { mutableStateOf("") }
    var newTimerHours by remember { mutableStateOf(0) }
    var newTimerMinutes by remember { mutableStateOf(25) }
    var newTimerSeconds by remember { mutableStateOf(0) }

    // Multi-Counter state
    val dayCounters by libraryViewModel.dayCounters.collectAsState()
    val stopwatches by libraryViewModel.stopwatches.collectAsState()
    var showAddCounterDialog by remember { mutableStateOf(false) }
    var newCounterTitle by remember { mutableStateOf("") }
    var newCounterDays by remember { mutableStateOf(10) }
    var showAddStopwatchDialog by remember { mutableStateOf(false) }
    var newStopwatchTitle by remember { mutableStateOf("") }

    // Deletion states
    var reminderToDelete by remember { mutableStateOf<Pair<Long, com.example.ui.screens.Reminder>?>(null) }
    var counterToDelete by remember { mutableStateOf<Int?>(null) }
    var timerToDelete by remember { mutableStateOf<Int?>(null) }
    var stopwatchToDelete by remember { mutableStateOf<Int?>(null) }
    var requiredAckForAction: (() -> Unit)? by remember { mutableStateOf(null) }
    
    val hasAcknowledgedBackgroundRun by libraryViewModel.hasAcknowledgedBackgroundRun.collectAsState()

    if (requiredAckForAction != null) {
        com.example.ui.components.BackgroundPermissionDialog(
            onConfirm = {
                libraryViewModel.acknowledgeBackgroundRun()
                requiredAckForAction?.invoke()
                requiredAckForAction = null
            },
            onDismiss = { requiredAckForAction = null }
        )
    }

    if (reminderToDelete != null) {
        com.example.ui.components.ConfirmationDialog(
            title = "Delete reminder",
            message = "Are you sure you want to delete this reminder?",
            onConfirm = {
                val (dateMillis, reminder) = reminderToDelete!!
                libraryViewModel.removeReminder(dateMillis, reminder)
            },
            onDismiss = { reminderToDelete = null }
        )
    }

    if (counterToDelete != null) {
        com.example.ui.components.ConfirmationDialog(
            title = "Delete counter",
            message = "Are you sure you want to delete this day counter?",
            onConfirm = { libraryViewModel.removeDayCounter(counterToDelete!!) },
            onDismiss = { counterToDelete = null }
        )
    }

    if (timerToDelete != null) {
        com.example.ui.components.ConfirmationDialog(
            title = "Delete timer",
            message = "Are you sure you want to delete this timer?",
            onConfirm = { libraryViewModel.removeTimer(timerToDelete!!) },
            onDismiss = { timerToDelete = null }
        )
    }

    if (stopwatchToDelete != null) {
        com.example.ui.components.ConfirmationDialog(
            title = "Delete stopwatch",
            message = "Are you sure you want to delete this stopwatch?",
            onConfirm = { libraryViewModel.removeStopwatch(stopwatchToDelete!!) },
            onDismiss = { stopwatchToDelete = null }
        )
    }

    // Calendar & Reminders state
    val zoneId = remember {
        java.time.ZoneId.systemDefault()
    }
    val todayMillis = remember(zoneId) {
        java.time.LocalDate.now(zoneId).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val datePickerState = key(zoneId) {
        rememberDatePickerState(initialSelectedDateMillis = todayMillis)
    }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var newReminderText by remember { mutableStateOf("") }
    var newReminderTime by remember { mutableStateOf("09:00 AM") }
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(initialHour = 9, initialMinute = 0)
    val allReminders by libraryViewModel.allReminders.collectAsState()

    var isCalendarExpanded by remember { mutableStateOf(false) }

    GlassBackground(
        modifier = Modifier
            .fillMaxSize(),
        drawBackgroundAndCircles = false
    ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.clip = true
                    }
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = 0.dp
                    )
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
            val scrollState = androidx.compose.foundation.rememberScrollState()
            val minHt = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
                    .heightIn(min = minHt),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Calendar & Schedule",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

            // Reminders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "REMINDERS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        showAddReminderDialog = true 
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Reminder", tint = Cyan400)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val sortedDates = allReminders.keys.mapNotNull { it.toLongOrNull() }.sorted()
                
                if (sortedDates.isEmpty()) {
                    Text("No reminders", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                } else {
                    sortedDates.forEach { dateMillis ->
                        val reminders = allReminders[dateMillis.toString()] ?: emptyList()
                        if (reminders.isNotEmpty()) {
                            val dateObj = Instant.ofEpochMilli(dateMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
                            Text(
                                text = dateObj.format(formatter),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassMorphic(RoundedCornerShape(24.dp))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    reminders.forEachIndexed { index, reminder ->
                                        if (reminder.isNotified) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(24.dp))
                                                    .clickable { libraryViewModel.removeReminder(dateMillis, reminder) }
                                                    .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${reminder.text} (tap to dismiss)",
                                                    color = Cyan400,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        } else {
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(Icons.Default.Circle, contentDescription = null, tint = Cyan400, modifier = Modifier.size(8.dp))
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        Text(reminder.text, color = MaterialTheme.colorScheme.onSurface)
                                                        Text(reminder.time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                                    }
                                                }
                                                IconButton(onClick = { 
                                                    reminderToDelete = Pair(dateMillis, reminder)
                                                }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete Reminder", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                        if (index < reminders.size - 1) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))

            // Day Counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "DAY COUNTERS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        showAddCounterDialog = true 
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Counter", tint = Cyan400)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (dayCounters.isEmpty()) {
                    Text("No day counters", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }

                dayCounters.forEach { counter ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .glassMorphic(RoundedCornerShape(24.dp))
                    ) {
                        if (counter.daysLeft <= 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable { libraryViewModel.removeDayCounter(counter.id) }
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${counter.title} (tap to dismiss)",
                                    color = Cyan400,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("DAYS UNTIL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cyan400, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = counter.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Cyan400.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = counter.daysLeft.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Cyan400)
                                }
                                Column {
    
                                    IconButton(onClick = { counterToDelete = counter.id }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Counter", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Stopwatch section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "STOPWATCHES", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        showAddStopwatchDialog = true 
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Stopwatch", tint = Cyan400)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (stopwatches.isEmpty()) {
                    Text("No stopwatches", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }

                stopwatches.forEach { stopwatch ->
                    var currentElapsed by remember(stopwatch.isRunning, stopwatch.elapsedMillis) { mutableStateOf(stopwatch.elapsedMillis) }
                    
                    LaunchedEffect(stopwatch.isRunning, stopwatch.elapsedMillis) {
                        if (stopwatch.isRunning) {
                            while(true) {
                                currentElapsed = System.currentTimeMillis() - stopwatch.startTime
                                kotlinx.coroutines.delay(30)
                            }
                        } else {
                            currentElapsed = stopwatch.elapsedMillis
                        }
                    }

                    val hrs = (currentElapsed / 3600000)
                    val minutes = ((currentElapsed % 3600000) / 60000)
                    val seconds = ((currentElapsed % 60000) / 1000)
                    val timeStr = String.format("%02d:%02d:%02d", hrs, minutes, seconds)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .glassMorphic(RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = stopwatch.title.ifBlank { "Stopwatch" }.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cyan400, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = timeStr, fontSize = 32.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { 
                                    if (!hasAcknowledgedBackgroundRun && !stopwatch.isRunning) {
                                        requiredAckForAction = { libraryViewModel.toggleStopwatch(stopwatch.id) }
                                    } else {
                                        libraryViewModel.toggleStopwatch(stopwatch.id)
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (stopwatch.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (stopwatch.isRunning) "Pause" else "Play",
                                        tint = Cyan400
                                    )
                                }
                                IconButton(onClick = { stopwatchToDelete = stopwatch.id }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Stopwatch", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))

            // CountDown Timers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "COUNTDOWN TIMERS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        showAddTimerDialog = true 
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Timer", tint = Cyan400)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (timers.isEmpty()) {
                    Text("No countdown timers", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }

            timers.forEach { timer ->
                val hrs = timer.timeRemaining / 3600
                val mins = (timer.timeRemaining % 3600) / 60
                val secs = timer.timeRemaining % 60
                val timeStr = String.format("%02d:%02d:%02d", hrs, mins, secs)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .glassMorphic(RoundedCornerShape(24.dp))
                ) {
                    if (timer.timeRemaining <= 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { libraryViewModel.resetTimer(timer.id) }
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("TIMER ENDED! (TAP TO RESET)", color = Cyan400, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 2.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val durationHrs = timer.durationMinutes / 60
                                val durationMins = timer.durationMinutes % 60
                                val titleText = if (timer.title.isNotBlank()) timer.title.uppercase() else if (durationHrs > 0 && durationMins > 0) "${durationHrs}H ${durationMins}M TIMER" else if (durationHrs > 0) "${durationHrs}H TIMER" else "${durationMins} MIN TIMER"
                                Text(text = titleText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cyan400, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = timeStr, fontSize = 32.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { 
                                    if (!hasAcknowledgedBackgroundRun && !timer.isRunning) {
                                        requiredAckForAction = { libraryViewModel.toggleTimer(timer.id) }
                                    } else {
                                        libraryViewModel.toggleTimer(timer.id)
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (timer.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (timer.isRunning) "Pause" else "Play",
                                        tint = Cyan400
                                    )
                                }
                                IconButton(onClick = { libraryViewModel.resetTimer(timer.id) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                IconButton(onClick = { timerToDelete = timer.id }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Timer", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

                Spacer(modifier = Modifier.height(120.dp))
        }
            }
    }

    if (showAddCounterDialog) {
        var counterTitleError by remember { mutableStateOf<String?>(null) }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAddCounterDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                GlassBackground(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Add Day Counter",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        OutlinedTextField(
                            value = newCounterTitle,
                            onValueChange = { 
                                newCounterTitle = it
                                counterTitleError = null
                            },
                            label = { Text("Title") },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                            isError = counterTitleError != null,
                            supportingText = {
                                if (counterTitleError != null) {
                                    Text(text = counterTitleError!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("Days:", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(16.dp))
                            WheelNumberPicker(
                                value = newCounterDays,
                                onValueChange = { newCounterDays = it },
                                range = 1..365,
                                modifier = Modifier.width(80.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddCounterDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                if (newCounterTitle.trim().isEmpty()) {
                                    counterTitleError = "Title name is mandatory"
                                } else {
                                    val days = newCounterDays
                                    libraryViewModel.addDayCounter(newCounterTitle, days)
                                    showAddCounterDialog = false
                                    newCounterTitle = ""
                                    newCounterDays = 10
                                }
                            }) {
                                Text("Add", color = Cyan400)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTimerDialog) {
        var timerError by remember { mutableStateOf<String?>(null) }
        var timerTitleError by remember { mutableStateOf<String?>(null) }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAddTimerDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                GlassBackground(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Add Timer",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedTextField(
                            value = newTimerTitle,
                            onValueChange = { 
                                newTimerTitle = it
                                timerTitleError = null
                            },
                            label = { Text("Timer Title") },
                            isError = timerTitleError != null,
                            supportingText = { if (timerTitleError != null) Text(timerTitleError!!) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("Time:", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(16.dp))
                            WheelNumberPicker(
                                value = newTimerHours,
                                onValueChange = { 
                                    newTimerHours = it
                                    timerError = null
                                },
                                range = 0..23,
                                format = { String.format("%02d", it) },
                                modifier = Modifier.width(60.dp)
                            )
                            Text(":", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp))
                            WheelNumberPicker(
                                value = newTimerMinutes,
                                onValueChange = { 
                                    newTimerMinutes = it
                                    timerError = null
                                },
                                range = 0..59,
                                format = { String.format("%02d", it) },
                                modifier = Modifier.width(60.dp)
                            )
                            Text(":", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp))
                            WheelNumberPicker(
                                value = newTimerSeconds,
                                onValueChange = { 
                                    newTimerSeconds = it
                                    timerError = null
                                },
                                range = 0..59,
                                format = { String.format("%02d", it) },
                                modifier = Modifier.width(60.dp)
                            )
                        }

                        if (timerError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = timerError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddTimerDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                val totalSeconds = newTimerHours * 3600 + newTimerMinutes * 60 + newTimerSeconds
                                if (newTimerTitle.trim().isEmpty()) {
                                    timerTitleError = "Title is mandatory"
                                } else if (totalSeconds <= 0) {
                                    timerError = "Timer duration must be greater than 0"
                                } else {
                                    libraryViewModel.addTimer(newTimerTitle, newTimerHours, newTimerMinutes, newTimerSeconds)
                                    showAddTimerDialog = false
                                    newTimerTitle = ""
                                    newTimerHours = 0
                                    newTimerMinutes = 25
                                    newTimerSeconds = 0
                                    timerError = null
                                    timerTitleError = null
                                }
                            }) {
                                Text("Add", color = Cyan400)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddStopwatchDialog) {
        var titleError by remember { mutableStateOf<String?>(null) }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAddStopwatchDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                GlassBackground(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Add Stopwatch",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedTextField(
                            value = newStopwatchTitle,
                            onValueChange = { 
                                newStopwatchTitle = it
                                titleError = null
                            },
                            label = { Text("Stopwatch Title") },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                            isError = titleError != null,
                            supportingText = {
                                if (titleError != null) {
                                    Text(text = titleError!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showAddStopwatchDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                if (newStopwatchTitle.trim().isEmpty()) {
                                    titleError = "Title is mandatory"
                                } else {
                                    libraryViewModel.addStopwatch(newStopwatchTitle)
                                    showAddStopwatchDialog = false
                                    newStopwatchTitle = ""
                                }
                            }) {
                                Text("Add", color = Cyan400)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddReminderDialog) {
        var reminderTextError by remember { mutableStateOf<String?>(null) }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAddReminderDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                GlassBackground(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Add Reminder",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        var showDatePickerDialog by remember { mutableStateOf(false) }
                        
                        OutlinedTextField(
                            value = newReminderText,
                            onValueChange = { 
                                newReminderText = it
                                reminderTextError = null
                            },
                            label = { Text("Reminder description") },
                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                            isError = reminderTextError != null,
                            supportingText = {
                                if (reminderTextError != null) {
                                    Text(text = reminderTextError!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentMillis = datePickerState.selectedDateMillis ?: todayMillis
                            val displayDate = java.time.Instant.ofEpochMilli(currentMillis).atZone(zoneId).toLocalDate()
                            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
                            Text(
                                text = "Date: ${displayDate.format(formatter)}",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { showDatePickerDialog = true }) {
                                Text("Set Date", color = Cyan400)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Time: $newReminderTime",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { showTimePicker = true }) {
                                Text("Set Time", color = Cyan400)
                            }
                        }

                        if (showDatePickerDialog) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { showDatePickerDialog = false },
                                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .requiredWidth(360.dp)
                                            .scale(0.85f)
                                    ) {
                                        GlassBackground(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                                        ) {
                                            CustomDatePickerDialogUI(
                                                initialDate = java.time.Instant.ofEpochMilli(datePickerState.selectedDateMillis ?: todayMillis)
                                                    .atZone(zoneId)
                                                    .toLocalDate(),
                                                onDateSelected = { localDate ->
                                                    // Convert localDate to millis in UTC for DatePickerState
                                                    val millis = localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                                                    datePickerState.selectedDateMillis = millis
                                                    showDatePickerDialog = false
                                                },
                                                onDismiss = { showDatePickerDialog = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddReminderDialog = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                val dateMillis = datePickerState.selectedDateMillis ?: todayMillis
                                if (newReminderText.trim().isEmpty()) {
                                    reminderTextError = "Reminder description is mandatory"
                                } else {
                                    libraryViewModel.addReminder(dateMillis, Reminder(text = newReminderText, time = newReminderTime))
                                    showAddReminderDialog = false
                                    newReminderText = ""
                                    newReminderTime = "09:00 AM"
                                }
                            }) {
                                Text("Add", color = Cyan400)
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showTimePicker) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showTimePicker = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                GlassBackground(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Select Time",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        TimePicker(
                            state = timePickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                selectorColor = Cyan400,
                                containerColor = Color.Transparent,
                                periodSelectorSelectedContainerColor = Cyan400.copy(alpha = 0.2f),
                                periodSelectorUnselectedContainerColor = Color.Transparent,
                                timeSelectorSelectedContainerColor = Cyan400.copy(alpha = 0.2f),
                                timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showTimePicker = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                val amPm = if (timePickerState.hour >= 12) "PM" else "AM"
                                val hr = if (timePickerState.hour % 12 == 0) 12 else timePickerState.hour % 12
                                newReminderTime = String.format("%02d:%02d %s", hr, timePickerState.minute, amPm)
                                showTimePicker = false
                            }) {
                                Text("OK", color = Cyan400)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WheelNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
    format: (Int) -> String = { it.toString() }
) {
    val items = range.toList()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = items.indexOf(value).coerceAtLeast(0))
    val snapBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState)
    
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (index in items.indices) {
                onValueChange(items[index])
            }
        }
    }

    Box(
        modifier = modifier.height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = snapBehavior,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Spacer(modifier = Modifier.height(40.dp)) }
            items(items) { itemValue ->
                Box(
                    modifier = Modifier.height(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = format(itemValue),
                        style = textStyle,
                        color = if (itemValue == value) Cyan400 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = if (itemValue == value) 24.sp else 18.sp
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
        HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp), color = Cyan400.copy(alpha = 0.3f))
        HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), color = Cyan400.copy(alpha = 0.3f))
    }
}
