package com.example.ui.screens
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.graphics.graphicsLayer

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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.ui.theme.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.border
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll

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

    val activity = LocalContext.current.findActivity() as? androidx.activity.ComponentActivity
    DisposableEffect(Unit) {
        onDispose {
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val currentStatusBarHeight = androidx.compose.foundation.layout.WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    var savedStatusBarHeight by remember { mutableStateOf(40.dp) }
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    LaunchedEffect(currentStatusBarHeight) {
        if (currentStatusBarHeight > 0.dp) {
            savedStatusBarHeight = currentStatusBarHeight
        }
    }
    
    val currentNavBarHeight = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var savedNavBarHeight by remember { mutableStateOf(0.dp) }
    
    var isInPipMode by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
            if (isInPipMode) {
                showControls = false
            } else {
                showControls = true
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            activity?.addOnPictureInPictureModeChangedListener(listener)
        }
        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                activity?.removeOnPictureInPictureModeChangedListener(listener)
            }
        }
    }
    
    LaunchedEffect(currentNavBarHeight) {
        if (currentNavBarHeight > 0.dp) {
            savedNavBarHeight = currentNavBarHeight
        }
    }

    val topPadding = savedStatusBarHeight + 64.dp
    val bottomPadding = savedNavBarHeight + 96.dp
    
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    
    val initialBrightness = remember { activity?.window?.attributes?.screenBrightness ?: -1f }

    DisposableEffect(Unit) {
        onDispose {
            val layoutParams = activity?.window?.attributes
            // Restore to system brightness (-1f) when leaving the player
            layoutParams?.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity?.window?.attributes = layoutParams
        }
    }

    androidx.activity.compose.BackHandler(onBack = onClose)

    val cornerRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isInPipMode) 0.dp else 32.dp,
        label = "cornerRadius"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (!isInPipMode) showControls = !showControls }
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
                val context = androidx.compose.ui.platform.LocalContext.current
                var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
                var currentPosition by remember { mutableStateOf(0) }
                var videoDuration by remember { mutableStateOf(0) }
                var isDraggingSlider by remember { mutableStateOf(false) }
                var sliderTargetPosition by remember { mutableStateOf(0f) }
                var wasPlayingBeforeDrag by remember { mutableStateOf(false) }
                var videoAspectRatio by remember { mutableStateOf<Float?>(null) }
                var playbackSpeed by remember { mutableStateOf(1f) }
                var videoViewBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
                
                MediaSessionHelper(
                    context = context,
                    title = item.title,
                    isPlaying = isPlaying,
                    duration = videoDuration.toLong(),
                    position = currentPosition.toLong(),
                    onPlay = {
                        mediaPlayerRef?.start()
                        isPlaying = true
                    },
                    onPause = {
                        mediaPlayerRef?.pause()
                        isPlaying = false
                    },
                    onSeekTo = { pos ->
                        mediaPlayerRef?.let { mp ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                mp.seekTo(pos, android.media.MediaPlayer.SEEK_CLOSEST)
                            } else {
                                mp.seekTo(pos.toInt())
                            }
                            currentPosition = pos.toInt()
                        }
                    }
                )

                androidx.compose.runtime.DisposableEffect(context, isPlaying) {
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                            if (intent?.action == "com.example.ACTION_PIP_PLAY_PAUSE") {
                                mediaPlayerRef?.let { mp ->
                                    if (isPlaying) {
                                        mp.pause()
                                        isPlaying = false
                                    } else {
                                        mp.start()
                                        isPlaying = true
                                    }
                                }
                            }
                        }
                    }
                    val filter = android.content.IntentFilter("com.example.ACTION_PIP_PLAY_PAUSE")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        context.registerReceiver(receiver, filter)
                    }
                    onDispose {
                        try {
                            context.unregisterReceiver(receiver)
                        } catch (e: Exception) {}
                    }
                }

                LaunchedEffect(isPlaying, videoViewBounds, videoAspectRatio) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val builder = android.app.PictureInPictureParams.Builder()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            builder.setAutoEnterEnabled(isPlaying)
                        }

                        val playPauseIntent = android.app.PendingIntent.getBroadcast(
                            context,
                            102,
                            android.content.Intent("com.example.ACTION_PIP_PLAY_PAUSE").setPackage(context.packageName),
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                        val playPauseTitle = if (isPlaying) "Pause" else "Play"
                        val playPauseAction = android.app.RemoteAction(
                            android.graphics.drawable.Icon.createWithResource(context, playPauseIcon),
                            playPauseTitle,
                            playPauseTitle,
                            playPauseIntent
                        )

                        builder.setActions(listOf(playPauseAction))

                        videoViewBounds?.let {
                            builder.setSourceRectHint(it)
                        }
                        if (videoAspectRatio != null && videoAspectRatio!! > 0f) {
                            try {
                                val clampedRatio = videoAspectRatio!!.coerceIn(1f/2.39f, 2.39f)
                                val rat = android.util.Rational((clampedRatio * 10000).toInt(), 10000)
                                builder.setAspectRatio(rat)
                            } catch (e: Exception) {}
                        }
                        try {
                            activity?.setPictureInPictureParams(builder.build())
                        } catch (e: Exception) {}
                    }
                }

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
                

                val activity = context as? androidx.activity.ComponentActivity
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                
                var startY by remember { mutableStateOf(0f) }
                var startBrightness by remember { mutableStateOf(0f) }
                var startVolume by remember { mutableStateOf(0) }
                
                var showBrightnessIndicator by remember { mutableStateOf(false) }
                var currentBrightness by remember { mutableStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f) }
                
                var showVolumeIndicator by remember { mutableStateOf(false) }
                var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }

                DisposableEffect(context) {
                    val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            try {
                                val systemBrightness = android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS).toFloat() / 255f
                                val windowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                                if (windowBrightness < 0) {
                                    currentBrightness = systemBrightness
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    context.contentResolver.registerContentObserver(
                        android.provider.Settings.System.getUriFor(android.provider.Settings.System.SCREEN_BRIGHTNESS),
                        false,
                        observer
                    )
                    
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                                currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                            }
                        }
                    }
                    context.registerReceiver(receiver, android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
                    
                    onDispose {
                        context.contentResolver.unregisterContentObserver(observer)
                        context.unregisterReceiver(receiver)
                    }
                }

                
                
                LaunchedEffect(showControls, isInPipMode) {
                    if (!showControls || isInPipMode) {
                        showBrightnessIndicator = false
                        showVolumeIndicator = false
                    }
                }
                


                Box(modifier = Modifier.fillMaxSize()) {
                    ZoomableContent(
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    startY = offset.y
                                    startBrightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                                    startVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                },
                                onDragEnd = {
                                    showBrightnessIndicator = false
                                    showVolumeIndicator = false
                                },
                                onDragCancel = {
                                    showBrightnessIndicator = false
                                    showVolumeIndicator = false
                                },
                                onDrag = { change, dragAmount ->
                                    val deltaY = change.position.y - startY
                                    if (change.position.x < size.width / 2) {
                                        // Brightness
                                        val deltaBrightness = (-deltaY * 4f) / size.height
                                        val newBrightness = (startBrightness + deltaBrightness).coerceIn(0f, 1f)
                                        val layoutParams = activity?.window?.attributes
                                        layoutParams?.screenBrightness = newBrightness
                                        activity?.window?.attributes = layoutParams
                                        currentBrightness = newBrightness
                                        showBrightnessIndicator = true
                                        showVolumeIndicator = false
                                    } else {
                                        // Volume
                                        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                        val deltaVolume = ((-deltaY * 4f) / size.height) * maxVolume
                                        val newVolume = (startVolume + deltaVolume).toInt().coerceIn(0, maxVolume)
                                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVolume, 0)
                                        currentVolume = newVolume
                                        showVolumeIndicator = true
                                        showBrightnessIndicator = false
                                    }
                                }
                            )
                        },
                        allowOneFingerPan = true,
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            val screenWidth = context.resources.displayMetrics.widthPixels
                            mediaPlayerRef?.let { mp ->
                                val current = mp.currentPosition
                                if (offset.x < screenWidth / 2) {
                                    mp.seekTo(maxOf(0, current - 10000))
                                } else {
                                    mp.seekTo(minOf(videoDuration, current + 10000))
                                }
                                currentPosition = mp.currentPosition
                            }
                        }
                    ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AndroidView(
                            modifier = Modifier
                                .then(
                                    if (videoAspectRatio != null) Modifier.aspectRatio(videoAspectRatio!!)
                                    else Modifier.fillMaxSize()
                                )
                                .onGloballyPositioned { coords ->
                                    val bounds = coords.boundsInWindow()
                                    videoViewBounds = android.graphics.Rect(
                                        bounds.left.toInt(),
                                        bounds.top.toInt(),
                                        bounds.right.toInt(),
                                        bounds.bottom.toInt()
                                    )
                                },
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
                                                    if (videoWidth > 0 && videoHeight > 0) {
                                                        videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
                                                    }
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
                }
                

                    // Indicator Overlays
                    if (!isInPipMode) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = if (isLandscape) 96.dp else 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.widthIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showBrightnessIndicator,
                                    enter = androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.fadeOut()
                                ) {
                                    VerticalSlider(
                                        value = currentBrightness,
                                        onValueChange = {},
                                        icon = androidx.compose.material.icons.Icons.Default.LightMode,
                                        interactive = false,
                                        textPosition = if (isLandscape) "right" else "bottom"
                                    )
                                }
                            }
                            
                            Box(modifier = Modifier.weight(1f)) // spacer
                            
                            Box(modifier = Modifier.widthIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showVolumeIndicator,
                                    enter = androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.fadeOut()
                                ) {
                                    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                    val ratio = if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f
                                    VerticalSlider(
                                        value = ratio,
                                        onValueChange = {},
                                        icon = androidx.compose.material.icons.Icons.Default.VolumeUp,
                                        interactive = false,
                                        textPosition = if (isLandscape) "left" else "bottom"
                                    )
                                }
                            }
                        }
                    }
                    }
                } // End of Box wrapping ZoomableContent
                
                // Custom Controls overlay
                if (!isInPipMode) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Empty center (buttons moved to bottom)

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                ))
                                .padding(bottom = if (isLandscape) 12.dp else savedNavBarHeight)
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Playback Controls Row (above the timer bar)
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
                            ) {
                                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                                    var showSpeedMenu by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = { showSpeedMenu = true },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Text("${playbackSpeed}x", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    androidx.compose.material3.MaterialTheme(
                                        colorScheme = androidx.compose.material3.darkColorScheme(
                                            surface = Color(0xFF222222),
                                            onSurface = Color.White
                                        ),
                                        shapes = androidx.compose.material3.MaterialTheme.shapes.copy(
                                            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                        )
                                    ) {
                                        androidx.compose.material3.DropdownMenu(
                                            expanded = showSpeedMenu,
                                            onDismissRequest = { showSpeedMenu = false },
                                            modifier = Modifier.height(192.dp).width(64.dp)
                                        ) {
                                        listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                                            androidx.compose.material3.DropdownMenuItem(
                                                modifier = Modifier.height(48.dp),
                                                contentPadding = PaddingValues(horizontal = 0.dp),
                                                text = { 
                                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            "${speed}x", 
                                                            color = if (playbackSpeed == speed) Cyan400 else Color.White,
                                                            fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 16.sp
                                                        ) 
                                                    }
                                                },
                                                onClick = {
                                                    playbackSpeed = speed
                                                    showSpeedMenu = false
                                                    try {
                                                        mediaPlayerRef?.let { mp ->
                                                            if (android.os.Build.VERSION.SDK_INT >= 23) {
                                                                mp.playbackParams = mp.playbackParams.setSpeed(speed)
                                                            }
                                                            mp.start()
                                                            isPlaying = true
                                                        }
                                                    } catch (e: Exception) {}
                                                }
                                            )
                                        }
                                    }
                                    }
                                }

                                Box(modifier = Modifier.align(Alignment.Center)) {
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
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Cyan400)
                                    ) {
                                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.Black, modifier = Modifier.size(32.dp))
                                    }
                                }

                                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                                IconButton(
                                    onClick = {
                                        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                        if (isLandscape) {
                                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                ) {
                                    Icon(androidx.compose.material.icons.Icons.Default.ScreenRotation, "Rotate", tint = Color.White, modifier = Modifier.size(24.dp))
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
                                Spacer(modifier = Modifier.weight(1f))
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
            }
            mimeType.startsWith("audio/") -> {
                val mediaPlayer = remember { android.media.MediaPlayer() }
                var currentPosition by remember { mutableStateOf(0) }
                var audioDuration by remember { mutableStateOf(0) }
                var isDraggingSlider by remember { mutableStateOf(false) }
                var sliderTargetPosition by remember { mutableStateOf(0f) }
                var wasPlayingBeforeDrag by remember { mutableStateOf(false) }
                var videoAspectRatio by remember { mutableStateOf<Float?>(null) }
                var playbackSpeed by remember { mutableStateOf(1f) }
                var videoViewBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
                
                MediaSessionHelper(
                    context = androidx.compose.ui.platform.LocalContext.current,
                    title = item.title,
                    isPlaying = isPlaying,
                    duration = audioDuration.toLong(),
                    position = currentPosition.toLong(),
                    onPlay = {
                        try { mediaPlayer.start() } catch (e: Exception) {}
                        isPlaying = true
                    },
                    onPause = {
                        try { mediaPlayer.pause() } catch (e: Exception) {}
                        isPlaying = false
                    },
                    onSeekTo = { pos ->
                        try {
                            mediaPlayer.seekTo(pos.toInt())
                            currentPosition = pos.toInt()
                        } catch (e: Exception) {}
                    }
                )

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
                val context = LocalContext.current
                val activity = context as? androidx.activity.ComponentActivity
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                
                var currentBrightness by remember { mutableStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f) }
                var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                
                var showBrightnessIndicator by remember { mutableStateOf(false) }
                var showVolumeIndicator by remember { mutableStateOf(false) }
                var startY by remember { mutableStateOf(0f) }
                var startBrightness by remember { mutableStateOf(0f) }
                var startVolume by remember { mutableStateOf(0) }

                DisposableEffect(context) {
                    val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            try {
                                val systemBrightness = android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS).toFloat() / 255f
                                val windowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                                if (windowBrightness < 0) {
                                    currentBrightness = systemBrightness
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    context.contentResolver.registerContentObserver(
                        android.provider.Settings.System.getUriFor(android.provider.Settings.System.SCREEN_BRIGHTNESS),
                        false,
                        observer
                    )
                    
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                                currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                            }
                        }
                    }
                    context.registerReceiver(receiver, android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
                    
                    onDispose {
                        context.contentResolver.unregisterContentObserver(observer)
                        context.unregisterReceiver(receiver)
                    }
                }
                
                Box(
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                startY = offset.y
                                startBrightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                                startVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                            },
                            onDragEnd = {
                                showBrightnessIndicator = false
                                showVolumeIndicator = false
                            },
                            onDragCancel = {
                                showBrightnessIndicator = false
                                showVolumeIndicator = false
                            },
                            onDrag = { change, dragAmount ->
                                val deltaY = change.position.y - startY
                                if (change.position.x < size.width / 2) {
                                    val deltaBrightness = (-deltaY * 4f) / size.height
                                    val newBrightness = (startBrightness + deltaBrightness).coerceIn(0f, 1f)
                                    val layoutParams = activity?.window?.attributes
                                    layoutParams?.screenBrightness = newBrightness
                                    activity?.window?.attributes = layoutParams
                                    currentBrightness = newBrightness
                                    showBrightnessIndicator = true
                                    showVolumeIndicator = false
                                } else {
                                    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                    val deltaVolume = ((-deltaY * 4f) / size.height) * maxVolume
                                    val newVolume = (startVolume + deltaVolume).toInt().coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVolume, 0)
                                    currentVolume = newVolume
                                    showVolumeIndicator = true
                                    showBrightnessIndicator = false
                                }
                            }
                        )
                    }.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { offset ->
                                val screenWidth = context.resources.displayMetrics.widthPixels
                                try {
                                    val current = mediaPlayer.currentPosition
                                    if (offset.x < screenWidth / 2) {
                                        mediaPlayer.seekTo(maxOf(0, current - 10000))
                                    } else {
                                        mediaPlayer.seekTo(minOf(audioDuration, current + 10000))
                                    }
                                    currentPosition = mediaPlayer.currentPosition
                                } catch (e: Exception) {}
                            }
                        )
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier
                                .padding(horizontal = 32.dp)
                                .heightIn(max = 240.dp)
                                .widthIn(max = 300.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Default.MusicNote, null, tint = Cyan400, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = item.title,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val formattedSize = item.fileSize?.let { bytes ->
                                    if (bytes < 1024) "$bytes B" else if (bytes < 1024 * 1024) "${bytes / 1024} KB" else String.format(java.util.Locale.US, "%.1f MB", bytes.toFloat() / (1024 * 1024))
                                } ?: ""
                                Text(
                                    text = "Audio File" + if (formattedSize.isNotEmpty()) " • $formattedSize" else "",
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = if (isLandscape) 96.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.widthIn(min = 48.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showBrightnessIndicator,
                                enter = androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.fadeOut()
                            ) {
                                VerticalSlider(
                                    value = currentBrightness,
                                    onValueChange = {},
                                    icon = androidx.compose.material.icons.Icons.Default.LightMode,
                                    interactive = false,
                                    textPosition = if (isLandscape) "right" else "bottom"
                                )
                            }
                        }
                        
                        Box(modifier = Modifier.weight(1f)) // spacer
                        
                        Box(modifier = Modifier.widthIn(min = 48.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showVolumeIndicator,
                                enter = androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.fadeOut()
                            ) {
                                VerticalSlider(
                                    value = if (maxVolume > 0) currentVolume.toFloat() / maxVolume else 0f,
                                    onValueChange = {},
                                    icon = androidx.compose.material.icons.Icons.Default.VolumeUp,
                                    interactive = false,
                                    textPosition = if (isLandscape) "left" else "bottom"
                                )
                            }
                        }
                    }
                }

                // Controls overlay at bottom
                if (!isInPipMode) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Empty center (buttons moved to bottom)

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                ))
                                .padding(bottom = if (isLandscape) 12.dp else savedNavBarHeight)
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Playback Controls Row (above the timer bar)
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
                            ) {
                                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                                    var showSpeedMenu by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = { showSpeedMenu = true },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Text("${playbackSpeed}x", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    androidx.compose.material3.MaterialTheme(
                                        colorScheme = androidx.compose.material3.darkColorScheme(
                                            surface = Color(0xFF222222),
                                            onSurface = Color.White
                                        ),
                                        shapes = androidx.compose.material3.MaterialTheme.shapes.copy(
                                            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                        )
                                    ) {
                                        androidx.compose.material3.DropdownMenu(
                                            expanded = showSpeedMenu,
                                            onDismissRequest = { showSpeedMenu = false },
                                            modifier = Modifier.height(192.dp).width(64.dp)
                                        ) {
                                        listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                                            androidx.compose.material3.DropdownMenuItem(
                                                modifier = Modifier.height(48.dp),
                                                contentPadding = PaddingValues(horizontal = 0.dp),
                                                text = { 
                                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            "${speed}x", 
                                                            color = if (playbackSpeed == speed) Cyan400 else Color.White,
                                                            fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 16.sp
                                                        ) 
                                                    }
                                                },
                                                onClick = {
                                                    playbackSpeed = speed
                                                    showSpeedMenu = false
                                                    try {
                                                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                                                            mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed)
                                                        }
                                                        mediaPlayer.start()
                                                        isPlaying = true
                                                    } catch (e: Exception) {}
                                                }
                                            )
                                        }
                                    }
                                    }
                                }

                                Box(modifier = Modifier.align(Alignment.Center)) {
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
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Cyan400)
                                    ) {
                                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.Black, modifier = Modifier.size(32.dp))
                                    }
                                }

                            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                            IconButton(
                                onClick = {
                                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                    if (isLandscape) {
                                        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Default.ScreenRotation, "Rotate", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        } // Close Box

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
        if (!isInPipMode) {
        androidx.compose.animation.AnimatedVisibility(
            visible = showControls,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(top = if (isLandscape) 12.dp else (savedStatusBarHeight + 12.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
        val coroutineScope = rememberCoroutineScope()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val topPaddingPx = remember(topPadding, density) { with(density) { topPadding.toPx() } }
        val bottomPaddingPx = remember(bottomPadding, density) { with(density) { bottomPadding.toPx() } }
        
        var scale by remember { mutableStateOf(1f) }
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(topPaddingPx) }
        
        var viewportWidth by remember { mutableStateOf(1f) }
        var viewportHeight by remember { mutableStateOf(1f) }
        var contentWidth by remember { mutableStateOf(1f) }
        var contentHeight by remember { mutableStateOf(1f) }
        
        LaunchedEffect(viewportWidth, viewportHeight, contentWidth, contentHeight, scale, topPaddingPx, bottomPaddingPx) {
            val bounds = calculateZoomBounds(viewportWidth, viewportHeight, contentWidth, contentHeight, scale, topPaddingPx, bottomPaddingPx)
            offsetX = offsetX.coerceIn(bounds.minX, bounds.maxX)
            offsetY = offsetY.coerceIn(bounds.minY, bounds.maxY)
        }
        
        val scrollStateY = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
            val bounds = calculateZoomBounds(viewportWidth, viewportHeight, contentWidth, contentHeight, scale, topPaddingPx, bottomPaddingPx)
            val oldOffsetY = offsetY
            offsetY = (offsetY + delta).coerceIn(bounds.minY, bounds.maxY)
            offsetY - oldOffsetY
        }
        
        val scrollStateX = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
            val bounds = calculateZoomBounds(viewportWidth, viewportHeight, contentWidth, contentHeight, scale, topPaddingPx, bottomPaddingPx)
            val oldOffsetX = offsetX
            offsetX = (offsetX + delta).coerceIn(bounds.minX, bounds.maxX)
            offsetX - oldOffsetX
        }
        
        val centerVisiblePage by remember {
            derivedStateOf {
                if (contentHeight <= 0f || pageCount <= 1 || scale <= 0f) 0
                else {
                    val scaledPageHeight = (contentHeight * scale) / pageCount
                    val centerScroll = (viewportHeight / 2) - offsetY
                    (centerScroll / scaledPageHeight).toInt().coerceIn(0, pageCount - 1)
                }
            }
        }
        
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .scrollable(scrollStateY, Orientation.Vertical)
                .scrollable(scrollStateX, Orientation.Horizontal)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (!canceled) {
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    
                                    val oldScale = scale
                                    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                    
                                    val bounds = calculateZoomBounds(viewportWidth, viewportHeight, contentWidth, contentHeight, newScale, topPaddingPx, bottomPaddingPx)
                                    
                                    val newOffsetX = centroid.x + (offsetX - centroid.x) * (newScale / oldScale) + panChange.x
                                    val newOffsetY = centroid.y + (offsetY - centroid.y) * (newScale / oldScale) + panChange.y
                                    
                                    scale = newScale
                                    offsetX = newOffsetX.coerceIn(bounds.minX, bounds.maxX)
                                    offsetY = newOffsetY.coerceIn(bounds.minY, bounds.maxY)
                                    
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onToggleControls() },
                        onDoubleTap = { tapOffset ->
                            coroutineScope.launch {
                                val targetScale = if (scale > 1f) 1f else 2.5f
                                val initialScale = scale
                                val initialOffsetX = offsetX
                                val initialOffsetY = offsetY
                                
                                val finalBounds = calculateZoomBounds(viewportWidth, viewportHeight, contentWidth, contentHeight, targetScale, topPaddingPx, bottomPaddingPx)
                                val idealFinalOffsetX = tapOffset.x + (initialOffsetX - tapOffset.x) * (targetScale / initialScale)
                                val idealFinalOffsetY = tapOffset.y + (initialOffsetY - tapOffset.y) * (targetScale / initialScale)
                                
                                val finalOffsetX = idealFinalOffsetX.coerceIn(finalBounds.minX, finalBounds.maxX)
                                val finalOffsetY = idealFinalOffsetY.coerceIn(finalBounds.minY, finalBounds.maxY)
                                
                                val fractionAnim = androidx.compose.animation.core.Animatable(0f)
                                fractionAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = androidx.compose.animation.core.tween(300)
                                ) {
                                    val fraction = this.value
                                    scale = initialScale + (targetScale - initialScale) * fraction
                                    offsetX = initialOffsetX + (finalOffsetX - initialOffsetX) * fraction
                                    offsetY = initialOffsetY + (finalOffsetY - initialOffsetY) * fraction
                                }
                            }
                        }
                    )
                }
        ) {
            viewportWidth = constraints.maxWidth.toFloat()
            viewportHeight = constraints.maxHeight.toFloat()
            val baseWidth = maxWidth
            
            Box(modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                }
            ) {
                Column(
                    modifier = Modifier
                        .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                        .width(baseWidth)
                        .onSizeChanged {
                            contentWidth = it.width.toFloat()
                            contentHeight = it.height.toFloat()
                        },
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (index in 0 until pageCount) {
                        PdfPage(
                            pdfRenderer = pdfRenderer,
                            pageIndex = index,
                            scale = 1f,
                            baseWidth = baseWidth
                        )
                        if (index < pageCount - 1) {
                            Spacer(modifier = Modifier.height(8.dp).fillMaxWidth().background(Color.Black))
                        }
                    }
                }
            }
            
            if (pageCount > 1 && showControls) {
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier
                        .padding(
                            top = topPadding, 
                            bottom = bottomPadding
                        )
                        .align(Alignment.CenterEnd)
                        .width(48.dp)
                        .fillMaxHeight()
                        .pointerInput(pageCount) {
                            val thumbHalfHeightPx = 20.dp.toPx()
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    val availableHeightPx = size.height - 2 * thumbHalfHeightPx
                                    val percent = if (availableHeightPx > 0) {
                                        ((offset.y - thumbHalfHeightPx) / availableHeightPx).coerceIn(0f, 1f)
                                    } else 0f
                                    val bounds = calculateZoomBounds(viewportWidth, viewportHeight, contentWidth, contentHeight, scale, topPaddingPx, bottomPaddingPx)
                                    val availableScrollRange = bounds.maxY - bounds.minY
                                    val newOffsetY = bounds.maxY - percent * availableScrollRange
                                    offsetY = newOffsetY.coerceIn(bounds.minY, bounds.maxY)
                                },
                                onDragEnd = {},
                                onDragCancel = {},
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    val availableHeightPx = size.height - 2 * thumbHalfHeightPx
                                    val percent = if (availableHeightPx > 0) {
                                        ((change.position.y - thumbHalfHeightPx) / availableHeightPx).coerceIn(0f, 1f)
                                    } else 0f
                                    val bounds = calculateZoomBounds(viewportWidth, viewportHeight, contentWidth, contentHeight, scale, topPaddingPx, bottomPaddingPx)
                                    val availableScrollRange = bounds.maxY - bounds.minY
                                    val newOffsetY = bounds.maxY - percent * availableScrollRange
                                    offsetY = newOffsetY.coerceIn(bounds.minY, bounds.maxY)
                                }
                            )
                        }
                ) {
                    val bounds = calculateZoomBounds(viewportWidth, viewportHeight, contentWidth, contentHeight, scale, topPaddingPx, bottomPaddingPx)
                    val availableScrollRange = bounds.maxY - bounds.minY
                    val percent = if (availableScrollRange > 0) (bounds.maxY - offsetY) / availableScrollRange else 0f
                    val availableHeight = androidx.compose.ui.unit.max(0.dp, maxHeight - 40.dp)
                    val yOffset = availableHeight * percent
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = yOffset)
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
                visible = scale > 1f && showControls,
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

private data class ZoomBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
)

private fun calculateZoomBounds(
    viewportWidth: Float,
    viewportHeight: Float,
    contentWidth: Float,
    contentHeight: Float,
    scale: Float,
    topPaddingPx: Float,
    bottomPaddingPx: Float
): ZoomBounds {
    val scaledWidth = contentWidth * scale
    val minOffsetX: Float
    val maxOffsetX: Float
    if (scaledWidth < viewportWidth) {
        val centeredX = (viewportWidth - scaledWidth) / 2f
        minOffsetX = centeredX
        maxOffsetX = centeredX
    } else {
        minOffsetX = viewportWidth - scaledWidth
        maxOffsetX = 0f
    }

    val scaledHeight = contentHeight * scale
    val availableHeight = viewportHeight - topPaddingPx - bottomPaddingPx
    
    val minY: Float
    val maxY: Float
    if (scaledHeight < availableHeight) {
        val centeredY = topPaddingPx + (availableHeight - scaledHeight) / 2f
        minY = centeredY
        maxY = centeredY
    } else {
        minY = viewportHeight - bottomPaddingPx - scaledHeight
        maxY = topPaddingPx
    }

    return ZoomBounds(minOffsetX, maxOffsetX, minY, maxY)
}

@Composable
private fun PdfPage(
    pdfRenderer: android.graphics.pdf.PdfRenderer?,
    pageIndex: Int,
    scale: Float,
    baseWidth: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    var androidBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var aspectRatio by remember { mutableStateOf(1f) }
    
    LaunchedEffect(pdfRenderer, pageIndex) {
        if (pdfRenderer != null) {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var newBitmap: android.graphics.Bitmap? = null
                var newRatio = 1f
                synchronized(pdfRenderer) {
                    try {
                        val page = pdfRenderer.openPage(pageIndex)
                        val displayMetrics = context.resources.displayMetrics
                        val width = displayMetrics.widthPixels
                        newRatio = width.toFloat() / page.width.toFloat()
                        val height = (page.height * newRatio).toInt()
                        
                        val b = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                        b.eraseColor(android.graphics.Color.WHITE)
                        
                        page.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        newBitmap = b
                    } catch (e: Exception) { e.printStackTrace() }
                }
                Pair(newBitmap, newRatio)
            }
            androidBitmap = result.first
            if (result.first != null) {
                aspectRatio = result.first!!.width.toFloat() / result.first!!.height.toFloat()
            }
        }
    }
    
    if (androidBitmap != null) {
        val imageBitmap = remember(androidBitmap) { androidBitmap!!.asImageBitmap() }
        
        Box(
            modifier = Modifier
                .width(baseWidth * scale)
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
        Box(modifier = Modifier.width(baseWidth * scale).height((baseWidth * scale) * 1.4f).background(Color.Black), contentAlignment = Alignment.Center) {
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
    onDoubleTap: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
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

    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap?.invoke() },
                    onDoubleTap = { tapOffset ->
                        if (currentOnDoubleTap != null) {
                            currentOnDoubleTap?.invoke(tapOffset)
                        } else {
                            coroutineScope.launch {
                                val targetScale = if (scale.value > 1f) 1f else 2.5f
                                
                                if (targetScale == 1f) {
                                    kotlinx.coroutines.joinAll(
                                        launch { scale.animateTo(1f) },
                                        launch { offsetX.animateTo(0f) },
                                        launch { offsetY.animateTo(0f) }
                                    )
                                } else {
                                    val center = androidx.compose.ui.geometry.Offset(containerSize.width / 2f, containerSize.height / 2f)
                                    val c = tapOffset - center
                                    
                                    val actualZoom = targetScale / scale.value
                                    val targetOffsetX = offsetX.value * actualZoom + c.x * (1 - actualZoom)
                                    val targetOffsetY = offsetY.value * actualZoom + c.y * (1 - actualZoom)
                                    
                                    val maxX = (containerSize.width * (targetScale - 1)) / 2f
                                    val maxY = (containerSize.height * (targetScale - 1)) / 2f
                                    
                                    kotlinx.coroutines.joinAll(
                                        launch { scale.animateTo(targetScale) },
                                        launch { offsetX.animateTo(targetOffsetX.coerceIn(-maxX, maxX)) },
                                        launch { offsetY.animateTo(targetOffsetY.coerceIn(-maxY, maxY)) }
                                    )
                                }
                                onScaleChange?.invoke(targetScale)
                            }
                        }
                    }
                )
            }
            .pointerInput(containerSize, allowOneFingerPan) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)
                        
                        val isMultiTouch = event.changes.count { it.pressed } > 1
                        
                        if (isMultiTouch || (scale.value > 1f && allowOneFingerPan)) {
                            if (zoomChange != 1f || panChange != androidx.compose.ui.geometry.Offset.Zero) {
                                val newScale = (scale.value * zoomChange).coerceIn(1f, 5f)
                                val actualZoom = newScale / scale.value
                                
                                coroutineScope.launch {
                                    if (scale.value != newScale) {
                                        scale.snapTo(newScale)
                                        onScaleChange?.invoke(newScale)
                                    }
                                    if (newScale > 1f) {
                                        val center = androidx.compose.ui.geometry.Offset(containerSize.width / 2f, containerSize.height / 2f)
                                        val c = centroid - center
                                        
                                        val nextX = offsetX.value * actualZoom + panChange.x + c.x * (1 - actualZoom)
                                        val nextY = offsetY.value * actualZoom + panChange.y + c.y * (1 - actualZoom)
                                        
                                        val maxX = (containerSize.width * (newScale - 1)) / 2f
                                        val maxY = (containerSize.height * (newScale - 1)) / 2f
                                        
                                        offsetX.snapTo(nextX.coerceIn(-maxX, maxX))
                                        offsetY.snapTo(nextY.coerceIn(-maxY, maxY))
                                    } else {
                                        offsetX.snapTo(0f)
                                        offsetY.snapTo(0f)
                                    }
                                }
                                
                                event.changes.forEach {
                                    if (it.positionChanged()) {
                                        it.consume()
                                    }
                                }
                            }
                        }
                    }
                }
            },
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
                contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding, start = 24.dp, end = 24.dp)
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

@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    interactive: Boolean = true,
    textPosition: String = "bottom"
) {
    var height by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    
    val textLabel = @Composable {
        Text(
            text = "${(value * 100).toInt()}%",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }

    val sliderCore = @Composable {
        Column(
            modifier = modifier.width(48.dp).height(240.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(24.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .onSizeChanged { height = it.height }
                    .then(
                        if (interactive) Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, _ ->
                                    if (height > 0) {
                                        val newValue = 1f - (change.position.y / height).coerceIn(0f, 1f)
                                        onValueChange(newValue)
                                    }
                                }
                            )
                        } else Modifier
                    )
                    .then(
                        if (interactive) Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    if (height > 0) {
                                        val newValue = 1f - (offset.y / height).coerceIn(0f, 1f)
                                        onValueChange(newValue)
                                    }
                                }
                            )
                        } else Modifier
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(value)
                        .align(Alignment.BottomCenter)
                        .background(Cyan400)
                )
            }
            if (textPosition == "bottom") {
                Box(modifier = Modifier.padding(top = 12.dp)) {
                    textLabel()
                }
            } else {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    if (textPosition == "left") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.padding(end = 16.dp)) { textLabel() }
            sliderCore()
        }
    } else if (textPosition == "right") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            sliderCore()
            Box(modifier = Modifier.padding(start = 16.dp)) { textLabel() }
        }
    } else {
        sliderCore()
    }
}

@Composable
fun GestureIndicator(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(140.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value)
                    .background(Cyan400)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(value * 100).toInt()}%",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MediaSessionHelper(
    context: android.content.Context,
    title: String,
    isPlaying: Boolean,
    duration: Long,
    position: Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val mediaSession = androidx.compose.runtime.remember(context) {
        android.media.session.MediaSession(context, "FileViewerMedia").apply {
            val sessionIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                sessionIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            setSessionActivity(pendingIntent)
        }
    }
    
    androidx.compose.runtime.DisposableEffect(mediaSession) {
        mediaSession.setCallback(object : android.media.session.MediaSession.Callback() {
            override fun onPlay() { onPlay() }
            override fun onPause() { onPause() }
            override fun onSeekTo(pos: Long) { onSeekTo(pos) }
            override fun onRewind() {
                val newPos = maxOf(0L, position - 10000L)
                onSeekTo(newPos)
            }
            override fun onFastForward() {
                val newPos = minOf(duration, position + 10000L)
                onSeekTo(newPos)
            }
        })
        mediaSession.isActive = true
        onDispose {
            mediaSession.isActive = false
            mediaSession.release()
        }
    }

    androidx.compose.runtime.LaunchedEffect(title, duration) {
        val metadataBuilder = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
            .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, duration)
        mediaSession.setMetadata(metadataBuilder.build())
    }

    androidx.compose.runtime.LaunchedEffect(isPlaying, position) {
        val stateBuilder = android.media.session.PlaybackState.Builder()
            .setActions(
                android.media.session.PlaybackState.ACTION_PLAY or
                android.media.session.PlaybackState.ACTION_PAUSE or
                android.media.session.PlaybackState.ACTION_PLAY_PAUSE or
                android.media.session.PlaybackState.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) android.media.session.PlaybackState.STATE_PLAYING else android.media.session.PlaybackState.STATE_PAUSED,
                position,
                1.0f
            )
        mediaSession.setPlaybackState(stateBuilder.build())
    }
}
