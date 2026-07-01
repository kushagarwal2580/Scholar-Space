package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.components.AudioRecorder
import com.example.ui.components.glassMorphic
import com.example.ui.theme.Cyan400
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun VoiceNoteRecorderScreen(
    onDismiss: () -> Unit,
    onSave: (String, File) -> Unit
) {
    val context = LocalContext.current
    val audioRecorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var hasRecorded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var titleError by remember { mutableStateOf<String?>(null) }
    val outputFile = remember { File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a") }
    var hasPermission by remember { 
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (isGranted) {
            if (audioRecorder.startRecording(outputFile)) {
                isRecording = true
            }
        } else {
            android.widget.Toast.makeText(context, "Permission required", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var recordingDuration by remember { mutableStateOf(0L) }
        LaunchedEffect(isRecording) {
            if (isRecording) {
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    recordingDuration += 1000
                }
            }
        }
        
        Box(modifier = Modifier.padding(24.dp)) {
            com.example.ui.components.GlassBackground(
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
                    Text(text = "New Voice Note", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = title,
                        onValueChange = { 
                            title = it 
                            titleError = null
                        },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan400,
                            focusedLabelColor = Cyan400,
                            cursorColor = Cyan400
                        ),
                        isError = titleError != null,
                        supportingText = {
                            if (titleError != null) {
                                Text(text = titleError!!, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!hasRecorded || isRecording) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else Cyan400.copy(alpha = 0.2f))
                                        .clickable {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            if (isRecording) {
                                                audioRecorder.stopRecording()
                                                isRecording = false
                                                if (recordingDuration < 1000L) {
                                                    hasRecorded = false
                                                    android.widget.Toast.makeText(context, "Recording too short", android.widget.Toast.LENGTH_SHORT).show()
                                                    recordingDuration = 0L
                                                } else {
                                                    hasRecorded = true
                                                }
                                            } else {
                                                if (hasPermission) {
                                                    recordingDuration = 0L
                                                    if (audioRecorder.startRecording(outputFile)) {
                                                        isRecording = true
                                                    }
                                                } else {
                                                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(if (isRecording) androidx.compose.foundation.shape.RoundedCornerShape(16.dp) else androidx.compose.foundation.shape.CircleShape)
                                            .background(if (isRecording) MaterialTheme.colorScheme.error else Cyan400),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!isRecording) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Record",
                                                tint = androidx.compose.ui.graphics.Color.Black,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = String.format("%02d:%02d", (recordingDuration / 1000) / 60, (recordingDuration / 1000) % 60),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Light,
                                    color = if (isRecording) MaterialTheme.colorScheme.error else Cyan400,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = if (isRecording) "Recording... Tap to stop" else "Tap to start recording", 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            Text(
                                text = "Recording completed.\nReady to save.", 
                                color = Cyan400, 
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!isRecording && outputFile.exists()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { 
                                    if (title.isBlank()) {
                                        titleError = "Title is mandatory"
                                    } else {
                                        onSave(title.trim(), outputFile) 
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Cyan400,
                                    contentColor = Color.Black
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                            ) {
                                Text("Save", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
