package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.VideoView
import android.widget.MediaController
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.ui.theme.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.border
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch

@Composable
fun FileViewerOverlay(
    item: LibraryItem?,
    onClose: () -> Unit,
    onShare: (LibraryItem) -> Unit,
    onDownload: (LibraryItem) -> Unit
) {
    if (item == null) return

    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    val view = LocalView.current
    val window = (context as? android.app.Activity)?.window

    LaunchedEffect(showControls) {
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            if (showControls) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val statusBarHeight = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val safeTopPadding = if (statusBarHeight > 0.dp) statusBarHeight else 40.dp
    val topPadding = safeTopPadding + 64.dp
    val bottomPadding = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    androidx.activity.compose.BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls }
                )
            }
    ) {
        // Content Layer
        val ext = item.title.substringAfterLast('.', "").lowercase()
        var mimeType = when (ext) {
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
        if (mimeType == null) {
            mimeType = item.uri?.let { context.contentResolver.getType(it) } ?: "*/*"
        }
        
        when {
            mimeType.startsWith("image/") -> {
                ZoomableContent(
                    modifier = Modifier.fillMaxSize(),
                    allowOneFingerPan = true,
                    onTap = { showControls = !showControls }
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            mimeType.startsWith("video/") -> {
                var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
                var currentPosition by remember { mutableStateOf(0) }
                var videoDuration by remember { mutableStateOf(0) }
                var isDraggingSlider by remember { mutableStateOf(false) }
                var sliderTargetPosition by remember { mutableStateOf(0f) }
                var wasPlayingBeforeDrag by remember { mutableStateOf(false) }
                
                LaunchedEffect(isPlaying, showControls) {
                    while (isPlaying && !isDraggingSlider) {
                        mediaPlayerRef?.let {
                            try {
                                currentPosition = it.currentPosition
                            } catch (e: Exception) {}
                        }
                        kotlinx.coroutines.delay(100L)
                    }
                }
                
                // Cleanup on dispose
                androidx.compose.runtime.DisposableEffect(item.uri) {
                    onDispose {
                        mediaPlayerRef?.release()
                    }
                }
                
                ZoomableContent(
                    modifier = Modifier.fillMaxSize(),
                    allowOneFingerPan = true,
                    onTap = { showControls = !showControls }
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            android.view.TextureView(ctx).apply {
                                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                        val mp = android.media.MediaPlayer()
                                        mediaPlayerRef = mp
                                        try {
                                            item.uri?.let { mp.setDataSource(ctx, it) }
                                            mp.setSurface(android.view.Surface(surface))
                                            mp.setOnPreparedListener { preparedMp ->
                                                videoDuration = preparedMp.duration
                                                isPlaying = true
                                                preparedMp.start()
                                            }
                                            mp.setOnCompletionListener {
                                                isPlaying = false
                                                currentPosition = videoDuration
                                            }
                                            mp.setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
                                                // Calculate scaling to preserve aspect ratio
                                                val viewRatio = width.toFloat() / height.toFloat()
                                                val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
                                                
                                                val matrix = android.graphics.Matrix()
                                                if (videoWidth > 0 && videoHeight > 0) {
                                                    if (videoRatio > viewRatio) {
                                                        val scaleY = viewRatio / videoRatio
                                                        matrix.setScale(1f, scaleY, width / 2f, height / 2f)
                                                    } else {
                                                        val scaleX = videoRatio / viewRatio
                                                        matrix.setScale(scaleX, 1f, width / 2f, height / 2f)
                                                    }
                                                }
                                                setTransform(matrix)
                                            }
                                            mp.prepareAsync()
                                        } catch (e: Exception) {
                                            mp.release()
                                        }
                                    }
                                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                                        mediaPlayerRef?.release()
                                        mediaPlayerRef = null
                                        return true
                                    }
                                }
                            }
                        }
                    )
                }
                
                // Custom Controls overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                ))
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Playback Controls Row (above the timer bar)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                    mediaPlayerRef?.let { mp ->
                                        val current = mp.currentPosition
                                        mp.seekTo(maxOf(0, current - 10000))
                                        currentPosition = mp.currentPosition
                                    }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                ) {
                                    Icon(Icons.Default.Replay10, "Rewind 10s", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                
                                IconButton(
                                    onClick = {
                                    mediaPlayerRef?.let { mp ->
                                        if (mp.isPlaying) {
                                            mp.pause()
                                            isPlaying = false
                                        } else {
                                            if (currentPosition >= videoDuration) {
                                                mp.seekTo(0)
                                            }
                                            mp.start()
                                            isPlaying = true
                                        }
                                    }
                                    },
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(Cyan400)
                                ) {
                                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.Black, modifier = Modifier.size(36.dp))
                                }

                                IconButton(
                                    onClick = {
                                    mediaPlayerRef?.let { mp ->
                                        val current = mp.currentPosition
                                        mp.seekTo(minOf(videoDuration, current + 10000))
                                        currentPosition = mp.currentPosition
                                    }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                ) {
                                    Icon(Icons.Default.Forward10, "Forward 10s", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDuration(currentPosition),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatDuration(videoDuration),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                            Slider(
                                value = if (isDraggingSlider) sliderTargetPosition else if (videoDuration > 0) currentPosition.toFloat() / videoDuration.toFloat() else 0f,
                                onValueChange = { percent ->
                                    if (!isDraggingSlider) {
                                        isDraggingSlider = true
                                        wasPlayingBeforeDrag = isPlaying
                                        if (isPlaying) {
                                            mediaPlayerRef?.pause()
                                            isPlaying = false
                                        }
                                    }
                                    sliderTargetPosition = percent
                                    val targetPosition = (percent * videoDuration).toInt()
                                    currentPosition = targetPosition
                                },
                                onValueChangeFinished = {
                                    isDraggingSlider = false
                                    mediaPlayerRef?.let { mp ->
                                        val targetPosition = (sliderTargetPosition * videoDuration).toInt()
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            mp.seekTo(targetPosition.toLong(), android.media.MediaPlayer.SEEK_CLOSEST)
                                        } else {
                                            mp.seekTo(targetPosition)
                                        }
                                        currentPosition = targetPosition
                                        if (wasPlayingBeforeDrag) {
                                            mp.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Cyan400,
                                    activeTrackColor = Cyan400,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }
            mimeType.startsWith("audio/") -> {
                val mediaPlayer = remember { android.media.MediaPlayer() }
                var currentPosition by remember { mutableStateOf(0) }
                var audioDuration by remember { mutableStateOf(0) }
                var isDraggingSlider by remember { mutableStateOf(false) }
                var sliderTargetPosition by remember { mutableStateOf(0f) }
                var wasPlayingBeforeDrag by remember { mutableStateOf(false) }

                LaunchedEffect(isPlaying) {
                    while (isPlaying && !isDraggingSlider) {
                        try {
                            if (mediaPlayer.isPlaying) {
                                currentPosition = mediaPlayer.currentPosition
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        kotlinx.coroutines.delay(200L)
                    }
                }

                DisposableEffect(item.uri) {
                    try {
                        item.uri?.let { uri ->
                            mediaPlayer.setDataSource(context, uri)
                            mediaPlayer.setOnPreparedListener { mp ->
                                audioDuration = mp.duration
                                isPlaying = true
                                mp.start()
                            }
                            mediaPlayer.setOnCompletionListener {
                                isPlaying = false
                                currentPosition = mediaPlayer.duration
                            }
                            mediaPlayer.prepareAsync()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    onDispose {
                        try {
                            mediaPlayer.stop()
                        } catch (e: Exception) {}
                        mediaPlayer.release()
                    }
                }

                // Gray box in center for audio details
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp)
                            .height(180.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                            .background(Color.DarkGray.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { showControls = !showControls },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Cyan400,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val formattedSize = item.fileSize?.let { bytes ->
                                if (bytes < 1024) "$bytes B" else if (bytes < 1024 * 1024) "${bytes / 1024} KB" else String.format(java.util.Locale.US, "%.1f MB", bytes.toFloat() / (1024 * 1024))
                            } ?: ""
                            Text(
                                text = "Audio File" + if (formattedSize.isNotEmpty()) " • $formattedSize" else "",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Controls overlay at bottom
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                ))
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Playback Controls Row (above the timer bar)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        try {
                                            val current = mediaPlayer.currentPosition
                                            mediaPlayer.seekTo(maxOf(0, current - 10000))
                                            currentPosition = mediaPlayer.currentPosition
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                ) {
                                    Icon(Icons.Default.Replay10, "Rewind 10s", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                
                                IconButton(
                                    onClick = {
                                        try {
                                            if (mediaPlayer.isPlaying) {
                                                mediaPlayer.pause()
                                                isPlaying = false
                                            } else {
                                                if (currentPosition >= audioDuration) {
                                                    mediaPlayer.seekTo(0)
                                                }
                                                mediaPlayer.start()
                                                isPlaying = true
                                            }
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(Cyan400)
                                ) {
                                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.Black, modifier = Modifier.size(36.dp))
                                }

                                IconButton(
                                    onClick = {
                                        try {
                                            val current = mediaPlayer.currentPosition
                                            mediaPlayer.seekTo(minOf(mediaPlayer.duration, current + 10000))
                                            currentPosition = mediaPlayer.currentPosition
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                ) {
                                    Icon(Icons.Default.Forward10, "Forward 10s", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDuration(currentPosition),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatDuration(audioDuration),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                            Slider(
                                value = if (isDraggingSlider) sliderTargetPosition else if (audioDuration > 0) currentPosition.toFloat() / audioDuration.toFloat() else 0f,
                                onValueChange = { percent ->
                                    if (!isDraggingSlider) {
                                        isDraggingSlider = true
                                        wasPlayingBeforeDrag = isPlaying
                                        try {
                                            if (mediaPlayer.isPlaying) {
                                                mediaPlayer.pause()
                                                isPlaying = false
                                            }
                                        } catch (e: Exception) {}
                                    }
                                    sliderTargetPosition = percent
                                    val targetPosition = (percent * audioDuration).toInt()
                                    currentPosition = targetPosition
                                },
                                onValueChangeFinished = {
                                    isDraggingSlider = false
                                    try {
                                        val targetPosition = (sliderTargetPosition * audioDuration).toInt()
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            mediaPlayer.seekTo(targetPosition.toLong(), android.media.MediaPlayer.SEEK_CLOSEST)
                                        } else {
                                            mediaPlayer.seekTo(targetPosition)
                                        }
                                        currentPosition = targetPosition
                                        if (wasPlayingBeforeDrag) {
                                            mediaPlayer.start()
                                            isPlaying = true
                                        }
                                    } catch (e: Exception) {}
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Cyan400,
                                    activeTrackColor = Cyan400,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }
            mimeType == "application/pdf" -> {
                item.uri?.let { uri ->
                    PdfViewer(
                        uri = uri,
                        modifier = Modifier.fillMaxSize(),
                        topPadding = topPadding,
                        bottomPadding = bottomPadding,
                        showControls = showControls,
                        itemTitle = item.title,
                        itemFileSize = item.fileSize,
                        onToggleControls = { showControls = !showControls }
                    )
                } ?: run {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No file URI found", color = Color.White)
                    }
                }
            }
            mimeType.startsWith("text/") || ext in listOf("txt", "csv", "xml", "json", "md", "kt", "java", "py", "html", "css", "js", "ts", "docx") -> {
                item.uri?.let { uri ->
                    DocumentViewer(
                        uri = uri,
                        modifier = Modifier.fillMaxSize(),
                        topPadding = topPadding,
                        bottomPadding = bottomPadding,
                        showControls = showControls,
                        itemTitle = item.title,
                        onToggleControls = { showControls = !showControls }
                    )
                } ?: run {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No file URI found", color = Color.White)
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(80.dp), tint = item.iconTint)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(item.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Preview not available for this file type.", color = Slate400, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { 
                        try {
                            item.uri?.let { uri ->
                                val shareUri = if (uri.scheme == "file") {
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        java.io.File(uri.path!!)
                                    )
                                } else uri
                                
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(shareUri, mimeType)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Open file with"))
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Open File Externally")
                    }
                }
            }
        }

        // Top App Bar
        androidx.compose.animation.AnimatedVisibility(
            visible = showControls,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(top = safeTopPadding)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Row {
                    IconButton(onClick = { onDownload(item) }) {
                        Icon(Icons.Default.Download, contentDescription = "Download/Save", tint = Color.White)
                    }
                    IconButton(onClick = { onShare(item) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Int): String {
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun PdfViewer(
    uri: Uri,
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    showControls: Boolean = true,
    itemTitle: String = "",
    itemFileSize: Long? = null,
    onToggleControls: () -> Unit
) {
    val context = LocalContext.current
    var pdfRenderer by remember { mutableStateOf<android.graphics.pdf.PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<android.os.ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    
    DisposableEffect(uri) {
        try {
            fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            if (fileDescriptor != null) {
                pdfRenderer = android.graphics.pdf.PdfRenderer(fileDescriptor!!)
                pageCount = pdfRenderer?.pageCount ?: 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        onDispose {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }
    
    if (pageCount > 0) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        var isZoomed by remember { mutableStateOf(false) }
        
        val isCenterAnExtraCanvas by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val center = layoutInfo.viewportEndOffset / 2
                val itemInfo = layoutInfo.visibleItemsInfo.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - center) }
                val rawIndex = itemInfo?.index ?: 0
                pageCount > 1 && (rawIndex == 0 || rawIndex == pageCount + 1)
            }
        }

        val centerVisiblePage by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val center = layoutInfo.viewportEndOffset / 2
                val itemInfo = layoutInfo.visibleItemsInfo.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - center) }
                val rawIndex = itemInfo?.index ?: 0
                if (pageCount > 1) {
                    (rawIndex - 1).coerceIn(0, pageCount - 1)
                } else rawIndex
            }
        }
        
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            ZoomableContent(
                modifier = Modifier.fillMaxSize(),
                allowOneFingerPan = false, // Let LazyColumn handle 1-finger vertical scroll
                onScaleChange = { scale -> isZoomed = scale > 1f },
                onTap = onToggleControls
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = topPadding, bottom = bottomPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (pageCount > 1) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .height(180.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                        .background(Color.Gray)
                                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onToggleControls() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(itemTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val formattedSize = itemFileSize?.let { bytes ->
                                            if (bytes < 1024) "$bytes B" else if (bytes < 1024 * 1024) "${bytes / 1024} KB" else String.format(java.util.Locale.US, "%.1f MB", bytes.toFloat() / (1024 * 1024))
                                        } ?: ""
                                        Text("$pageCount Pages" + if (formattedSize.isNotEmpty()) " • $formattedSize" else "", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                    items(pageCount) { index ->
                        PdfPage(
                            pdfRenderer = pdfRenderer,
                            pageIndex = index
                        )
                        if (index < pageCount - 1) {
                            Spacer(modifier = Modifier.height(8.dp).fillMaxWidth().background(Color.Black))
                        }
                    }
                    if (pageCount > 1) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .height(180.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                        .background(Color.Gray)
                                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onToggleControls() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(itemTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val formattedSize = itemFileSize?.let { bytes ->
                                            if (bytes < 1024) "$bytes B" else if (bytes < 1024 * 1024) "${bytes / 1024} KB" else String.format(java.util.Locale.US, "%.1f MB", bytes.toFloat() / (1024 * 1024))
                                        } ?: ""
                                        Text("$pageCount Pages" + if (formattedSize.isNotEmpty()) " • $formattedSize" else "", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (pageCount > 1 && showControls) {
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier
                        .padding(top = topPadding, bottom = bottomPadding)
                        .align(Alignment.CenterEnd)
                        .width(48.dp)
                        .fillMaxHeight()
                        .pointerInput(pageCount) {
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    val percent = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    val targetPage = (percent * pageCount).toInt().coerceIn(0, pageCount - 1)
                                    coroutineScope.launch { listState.scrollToItem(targetPage) }
                                },
                                onDragEnd = {},
                                onDragCancel = {},
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    val percent = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    val targetPage = (percent * pageCount).toInt().coerceIn(0, pageCount - 1)
                                    coroutineScope.launch { listState.scrollToItem(targetPage) }
                                }
                            )
                        }
                ) {
                    val percent = if (pageCount > 1) centerVisiblePage.toFloat() / (pageCount - 1) else 0f
                    val yOffset = maxHeight * percent - 20.dp
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = yOffset.coerceIn(0.dp, maxHeight - 40.dp))
                            .size(32.dp, 40.dp)
                            .background(Cyan400, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${centerVisiblePage + 1}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Floating page number when zoomed
            androidx.compose.animation.AnimatedVisibility(
                visible = isZoomed && showControls && !isCenterAnExtraCanvas,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPadding + 16.dp),
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Page ${centerVisiblePage + 1} of $pageCount", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Unable to load PDF", color = Color.White)
        }
    }
}

@Composable
private fun PdfPage(
    pdfRenderer: android.graphics.pdf.PdfRenderer?,
    pageIndex: Int
) {
    val context = LocalContext.current
    var androidBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(pdfRenderer, pageIndex) {
        if (pdfRenderer != null) {
            androidBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var newBitmap: android.graphics.Bitmap? = null
                synchronized(pdfRenderer) {
                    try {
                        val page = pdfRenderer.openPage(pageIndex)
                        val displayMetrics = context.resources.displayMetrics
                        val width = displayMetrics.widthPixels
                        val ratio = width.toFloat() / page.width.toFloat()
                        val height = (page.height * ratio).toInt()
                        
                        val b = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                        b.eraseColor(android.graphics.Color.WHITE)
                        
                        page.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        newBitmap = b
                    } catch (e: Exception) { e.printStackTrace() }
                }
                newBitmap
            }
        }
    }
    
    if (androidBitmap != null) {
        val imageBitmap = remember(androidBitmap) { androidBitmap!!.asImageBitmap() }
        val aspectRatio = androidBitmap!!.width.toFloat() / androidBitmap!!.height.toFloat()
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        ) {
            androidx.compose.foundation.Image(
                bitmap = imageBitmap,
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth().height(400.dp).background(Color.Black), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = Cyan400)
        }
    }
}

@Composable
fun ZoomableContent(
    modifier: Modifier = Modifier,
    allowOneFingerPan: Boolean = true,
    onScaleChange: ((Float) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val offsetY = remember { androidx.compose.animation.core.Animatable(0f) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    
    // Prevent back gesture from system if we are zoomed in
    androidx.activity.compose.BackHandler(enabled = scale.value > 1f) {
        coroutineScope.launch {
            kotlinx.coroutines.joinAll(
                launch { scale.animateTo(1f) },
                launch { offsetX.animateTo(0f) },
                launch { offsetY.animateTo(0f) }
            )
        }
    }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale.value * zoomChange).coerceIn(1f, 5f)
        coroutineScope.launch {
            if (scale.value != newScale) {
                scale.snapTo(newScale)
                onScaleChange?.invoke(newScale)
            }
            if (newScale == 1f) {
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
            } else {
                val maxX = (containerSize.width * (newScale - 1)) / 2f
                val maxY = (containerSize.height * (newScale - 1)) / 2f
                offsetX.snapTo((offsetX.value + offsetChange.x * newScale).coerceIn(-maxX, maxX))
                offsetY.snapTo((offsetY.value + offsetChange.y * newScale).coerceIn(-maxY, maxY))
            }
        }
    }

    val currentOnTap by rememberUpdatedState(onTap)
    
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap?.invoke() },
                    onDoubleTap = {
                        coroutineScope.launch {
                            val targetScale = if (scale.value > 1f) 1f else 2.5f
                            val targetOffsetX = if (targetScale == 1f) 0f else offsetX.value
                            val targetOffsetY = if (targetScale == 1f) 0f else offsetY.value
                            
                            kotlinx.coroutines.joinAll(
                                launch { scale.animateTo(targetScale) },
                                launch { offsetX.animateTo(targetOffsetX) },
                                launch { offsetY.animateTo(targetOffsetY) }
                            )
                            onScaleChange?.invoke(targetScale)
                        }
                    }
                )
            }
            .pointerInput(allowOneFingerPan, scale.value, containerSize) {
                if (allowOneFingerPan) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            if (scale.value > 1f) {
                                val maxX = (containerSize.width * (scale.value - 1)) / 2f
                                val maxY = (containerSize.height * (scale.value - 1)) / 2f
                                
                                val newX = (offsetX.value + dragAmount.x).coerceIn(-maxX, maxX)
                                val newY = (offsetY.value + dragAmount.y).coerceIn(-maxY, maxY)
                                
                                val didConsumeVertical = newY != offsetY.value && kotlin.math.abs(dragAmount.y) > 0
                                val didConsumeHorizontal = newX != offsetX.value && kotlin.math.abs(dragAmount.x) > 0

                                if (didConsumeVertical || didConsumeHorizontal) {
                                    change.consume()
                                }
                                coroutineScope.launch {
                                    offsetX.snapTo(newX)
                                    offsetY.snapTo(newY)
                                }
                            }
                        }
                    )
                }
            }
            .transformable(state = transformState),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer(
                scaleX = scale.value,
                scaleY = scale.value,
                translationX = offsetX.value,
                translationY = offsetY.value
            )
        ) {
            content()
        }
    }
}

@Composable
private fun DocumentViewer(
    uri: Uri,
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    showControls: Boolean = true,
    itemTitle: String = "",
    onToggleControls: () -> Unit
) {
    val context = LocalContext.current
    var textContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(uri) {
        isLoading = true
        errorMessage = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val ext = itemTitle.substringAfterLast('.', "").lowercase()
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    when {
                        ext == "docx" -> {
                            val zip = java.util.zip.ZipInputStream(inputStream)
                            var text = StringBuilder()
                            var entry = zip.nextEntry
                            while (entry != null) {
                                if (entry.name == "word/document.xml") {
                                    val xml = zip.bufferedReader().readText()
                                    val regex = Regex("<w:t[^>]*>(.*?)</w:t>")
                                    val matches = regex.findAll(xml)
                                    for (match in matches) {
                                        val content = match.groupValues[1]
                                        if (content.isNotBlank()) text.append(content).append("\n")
                                    }
                                    break
                                }
                                entry = zip.nextEntry
                            }
                            zip.close()
                            textContent = if (text.isNotEmpty()) text.toString() else "Empty Document"
                        }
                        else -> {
                            // pure text reading for txt, csv, md, json etc.
                            textContent = inputStream.bufferedReader().readText()
                        }
                    }
                } else {
                    errorMessage = "Failed to open file"
                }
            } catch (e: Exception) {
                errorMessage = "Could not preview this file."
            } finally {
                isLoading = false
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onToggleControls
            )
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Cyan400)
        } else if (errorMessage != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(errorMessage!!, color = Color.White)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topPadding + 24.dp, bottom = bottomPadding + 24.dp, start = 24.dp, end = 24.dp)
            ) {
                item {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = itemTitle,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                }
                item {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = textContent ?: "Empty Document",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}