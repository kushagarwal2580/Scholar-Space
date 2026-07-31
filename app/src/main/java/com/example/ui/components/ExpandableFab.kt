package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun ExpandableFab(
    onScanClick: (() -> Unit)? = null,
    onUploadClick: (() -> Unit)? = null,
    onFolderClick: (() -> Unit)? = null,
    onNoteClick: (() -> Unit)? = null,
    onVoiceNoteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    var localExpanded by remember { mutableStateOf(false) }
    val isExpandedValue = expanded ?: localExpanded
    val setExpandedValue: (Boolean) -> Unit = { newVal ->
        if (onExpandedChange != null) {
            onExpandedChange(newVal)
        } else {
            localExpanded = newVal
        }
    }

    // Rotating '+' to 'x' inside Google Drive uses a fluid motion
    val rotation by animateFloatAsState(targetValue = if (isExpandedValue) 135f else 0f, label = "fab_rotation")

    // Staggered trigger states for sub-items
    var showScan by remember { mutableStateOf(false) }
    var showUpload by remember { mutableStateOf(false) }
    var showFolder by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var showVoiceNote by remember { mutableStateOf(false) }

    LaunchedEffect(isExpandedValue) {
        if (isExpandedValue) {
            // Opening stagger: bottom first
            if (onFolderClick != null) {
                showFolder = true
                kotlinx.coroutines.delay(45)
            }
            if (onUploadClick != null) {
                showUpload = true
                kotlinx.coroutines.delay(45)
            }
            if (onScanClick != null) {
                showScan = true
                kotlinx.coroutines.delay(45)
            }
            if (onVoiceNoteClick != null) {
                showVoiceNote = true
                kotlinx.coroutines.delay(45)
            }
            if (onNoteClick != null) {
                showNote = true
                kotlinx.coroutines.delay(45)
            }
        } else {
            // Closing stagger: top first
            showNote = false
            kotlinx.coroutines.delay(30)
            showVoiceNote = false
            kotlinx.coroutines.delay(30)
            showScan = false
            kotlinx.coroutines.delay(30)
            showUpload = false
            kotlinx.coroutines.delay(30)
            showFolder = false
        }
    }

    // Spring animations with an overshoot bounce feel, like Google Drive has!
    val scanAlpha by animateFloatAsState(
        targetValue = if (showScan) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scan_alpha"
    )
    val scanScale by animateFloatAsState(
        targetValue = if (showScan) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scan_scale"
    )
    val scanY by animateFloatAsState(
        targetValue = if (showScan) 0f else 40f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scan_y"
    )

    val uploadAlpha by animateFloatAsState(
        targetValue = if (showUpload) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "upload_alpha"
    )
    val uploadScale by animateFloatAsState(
        targetValue = if (showUpload) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "upload_scale"
    )
    val uploadY by animateFloatAsState(
        targetValue = if (showUpload) 0f else 40f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "upload_y"
    )

    val folderAlpha by animateFloatAsState(
        targetValue = if (showFolder) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "folder_alpha"
    )
    val folderScale by animateFloatAsState(
        targetValue = if (showFolder) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "folder_scale"
    )
    val folderY by animateFloatAsState(
        targetValue = if (showFolder) 0f else 40f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "folder_y"
    )

    val noteAlpha by animateFloatAsState(
        targetValue = if (showNote) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "note_alpha"
    )
    val noteScale by animateFloatAsState(
        targetValue = if (showNote) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "note_scale"
    )
    val noteY by animateFloatAsState(
        targetValue = if (showNote) 0f else 40f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "note_y"
    )

    val voiceNoteAlpha by animateFloatAsState(
        targetValue = if (showVoiceNote) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "voice_note_alpha"
    )
    val voiceNoteScale by animateFloatAsState(
        targetValue = if (showVoiceNote) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "voice_note_scale"
    )
    val voiceNoteY by animateFloatAsState(
        targetValue = if (showVoiceNote) 0f else 40f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "voice_note_y"
    )

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 76.dp)
        ) {
            if (onNoteClick != null) {
                FabMenuItem(
                    icon = Icons.Default.Description,
                    label = "Text",
                    alphaVal = noteAlpha,
                    scaleVal = noteScale,
                    yValOffsetDp = noteY,
                    onClick = { setExpandedValue(false); onNoteClick() }
                )
            }
            if (onVoiceNoteClick != null) {
                FabMenuItem(
                    icon = Icons.Default.Mic,
                    label = "Voice",
                    alphaVal = voiceNoteAlpha,
                    scaleVal = voiceNoteScale,
                    yValOffsetDp = voiceNoteY,
                    onClick = { setExpandedValue(false); onVoiceNoteClick() }
                )
            }
            if (onScanClick != null) {
                FabMenuItem(
                    icon = Icons.Default.CameraAlt,
                    label = "Scan",
                    alphaVal = scanAlpha,
                    scaleVal = scanScale,
                    yValOffsetDp = scanY,
                    onClick = { setExpandedValue(false); onScanClick() }
                )
            }
            if (onUploadClick != null) {
                FabMenuItem(
                    icon = Icons.Default.Upload,
                    label = "Upload",
                    alphaVal = uploadAlpha,
                    scaleVal = uploadScale,
                    yValOffsetDp = uploadY,
                    onClick = { setExpandedValue(false); onUploadClick() }
                )
            }
            if (onFolderClick != null) {
                FabMenuItem(
                    icon = Icons.Default.CreateNewFolder,
                    label = "Folder",
                    alphaVal = folderAlpha,
                    scaleVal = folderScale,
                    yValOffsetDp = folderY,
                    onClick = { setExpandedValue(false); onFolderClick() }
                )
            }
        }


        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        FloatingActionButton(
            onClick = { 
                setExpandedValue(!isExpandedValue) 
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = androidx.compose.foundation.shape.CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = if (isExpandedValue) 0.dp else 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (isExpandedValue) "Close" else "Add",
                modifier = Modifier.rotate(rotation).size(32.dp)
            )
        }
    }
}

@Composable
private fun FabMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    alphaVal: Float,
    scaleVal: Float,
    yValOffsetDp: Float,
    onClick: () -> Unit
) {
    if (alphaVal <= 0.01f) return

    val density = LocalDensity.current.density
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .graphicsLayer {
                alpha = alphaVal
                scaleX = scaleVal
                scaleY = scaleVal
                translationY = yValOffsetDp * density
            }
            .height(56.dp)
            .defaultMinSize(minWidth = 140.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Slate800.copy(alpha = 0.95f))
            .border(
                width = 1.dp,
                color = Cyan400.copy(alpha = 0.45f),
                shape = RoundedCornerShape(percent = 50)
            )
            .clickable(onClick = {
                onClick()
            })
            .padding(horizontal = 20.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Cyan400,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
