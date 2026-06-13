package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.DragEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.example.data.local.AppDatabase
import com.example.data.repository.UserRepository
import com.example.ui.screens.AuthViewModel
import com.example.ui.screens.AuthViewModelFactory
import com.example.ui.screens.AuthState
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LibraryViewModel
import com.example.ui.screens.DriveViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Cyan400
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding

import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.togetherWith

class MainActivity : ComponentActivity() {
    private val appDatabase by lazy { AppDatabase.getDatabase(this) }
    private val userRepository by lazy { UserRepository(appDatabase.userDao()) }
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(userRepository, applicationContext)
    }
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val driveViewModel: DriveViewModel by viewModels()
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    private fun isCurrentlyOnline(): Boolean {
        try {
            val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            return false
        }
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
                private var wasOffline = !isCurrentlyOnline()

                override fun onAvailable(network: android.net.Network) {
                    runOnUiThread {
                        if (wasOffline) {
                            android.util.Log.d("MainActivity", "Internet connected, auto-syncing / uploading pending files...")
                            if (driveViewModel.isConnected.value) {
                                driveViewModel.syncDriveData(this@MainActivity, libraryViewModel)
                                driveViewModel.syncMetadata(this@MainActivity, authViewModel, libraryViewModel, isUpload = false)
                            }
                        }
                        wasOffline = false
                    }
                }

                override fun onLost(network: android.net.Network) {
                    wasOffline = true
                }
            }
            val request = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error registering network callback", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let { callback ->
            try {
                val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error unregistering network callback", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build()
                com.google.firebase.FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            android.util.Log.e("Firebase", "Failed to initialize Firebase", e)
        }
        
        registerNetworkCallback()
        
        enableEdgeToEdge()
        
        handleIntent(intent)
        
        setContent {
            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {}

            LaunchedEffect(Unit) {
                val permissions = mutableListOf<String>()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                    permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
                    permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
                    permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                val notGranted = permissions.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (notGranted.isNotEmpty()) {
                    permissionLauncher.launch(notGranted.toTypedArray())
                }
            }

            val isDarkMode by libraryViewModel.isDarkMode.collectAsState()
            
            MyApplicationTheme {
                val isImeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
                val authState by authViewModel.uiState.collectAsState()
                val isSyncing by authViewModel.isSyncing.collectAsState()
                val currentTab by libraryViewModel.currentTab.collectAsState()
                
                LaunchedEffect(currentTab) {
                    if (currentTab == "calendar") {
                        libraryViewModel.clearAllNotifications()
                    }
                }
                
                androidx.activity.compose.BackHandler(enabled = currentTab != "dashboard" && authState is AuthState.Success) {
                    libraryViewModel.setCurrentTab("dashboard")
                }
                
                val viewingItem by libraryViewModel.viewingItem.collectAsState()
                val activeAccount by driveViewModel.activeAccount.collectAsState()
                val isMetadataSyncing by driveViewModel.isMetadataSyncing.collectAsState()
                var previousAccount by remember { mutableStateOf(activeAccount) }
                
                LaunchedEffect(activeAccount) {
                    if (previousAccount != activeAccount && previousAccount != null) {
                        libraryViewModel.clearFiles(this@MainActivity)
                    }
                    previousAccount = activeAccount
                    if (activeAccount != null) {
                        driveViewModel.syncDriveData(this@MainActivity, libraryViewModel)
                        driveViewModel.syncMetadata(this@MainActivity, authViewModel, libraryViewModel, isUpload = false)
                        
                        libraryViewModel.onStateChangedListener = {
                            driveViewModel.syncMetadata(this@MainActivity, authViewModel, libraryViewModel, isUpload = true)
                        }
                    } else {
                        libraryViewModel.onStateChangedListener = null
                    }
                }
                
                var lastSyncedBio by remember { mutableStateOf<String?>(null) }
                var lastSyncedStatusMsg by remember { mutableStateOf<String?>(null) }
                var lastSyncedNickname by remember { mutableStateOf<String?>(null) }
                var lastSyncedProfilePic by remember { mutableStateOf<String?>(null) }
                var lastSyncedEmail by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(authState) {
                    if (authState is AuthState.Success) {
                        val success = authState as AuthState.Success
                        val email = success.email
                        driveViewModel.setAppUserEmail(this@MainActivity, email)
                        
                        // Detect when profile actually changed locally (user edited nickname, bio, or status)
                        val profileChanged = lastSyncedEmail == email && (
                            lastSyncedBio != success.bio ||
                            lastSyncedStatusMsg != success.statusMsg ||
                            lastSyncedNickname != success.displayName ||
                            lastSyncedProfilePic != success.profilePic
                        )
                        
                        lastSyncedEmail = email
                        lastSyncedBio = success.bio
                        lastSyncedStatusMsg = success.statusMsg
                        lastSyncedNickname = success.displayName
                        lastSyncedProfilePic = success.profilePic
                        
                        if (profileChanged) {
                            driveViewModel.syncMetadata(this@MainActivity, authViewModel, libraryViewModel, isUpload = true)
                        }
                    } else if (authState is AuthState.Idle || authState is AuthState.Error) {
                        driveViewModel.setAppUserEmail(this@MainActivity, null)
                        libraryViewModel.setCurrentTab("dashboard")
                        lastSyncedEmail = null
                        lastSyncedBio = null
                        lastSyncedStatusMsg = null
                        lastSyncedNickname = null
                        lastSyncedProfilePic = null
                    }
                }
                
                // --- Premium Startup Splash Screen Animation Logic ---
                var hasStartedDismissal by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                var splashFinished by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                var minTimeElapsed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    minTimeElapsed = true
                }
                
                LaunchedEffect(minTimeElapsed, authState, isMetadataSyncing) {
                    if (minTimeElapsed && authState !is AuthState.Loading && !isMetadataSyncing) {
                        hasStartedDismissal = true
                    }
                }

                val isSlidingUp = hasStartedDismissal

                // Splash screen translation animation upwards (slides out of view)
                val splashSlideOffsetFraction by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isSlidingUp) -1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 1000,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    finishedListener = {
                        if (it == -1f) {
                            splashFinished = true
                        }
                    }
                )

                // Dashboard enters from behind with a smooth scale-up & fade-in animation
                val dashboardScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = 1f, // Removed scale animation completely to prevent edge blinking
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 1000,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )

                val dashboardAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isSlidingUp) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 700,
                        easing = androidx.compose.animation.core.LinearOutSlowInEasing
                    )
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    if (splashFinished) {
                        // Standard view layout once splash has fully completed & slid away
                        if (authState !is AuthState.Success) {
                            com.example.ui.screens.AuthScreen(authViewModel = authViewModel)
                        } else {
                            MainAppContent(
                                currentTab = currentTab,
                                isImeVisible = isImeVisible,
                                viewingItem = viewingItem,
                                authViewModel = authViewModel,
                                libraryViewModel = libraryViewModel,
                                driveViewModel = driveViewModel,
                                activity = this@MainActivity
                            )

                            // Syncing Screen Overlay with slide-up out animation
                            var hideSyncingByAnimation by remember { mutableStateOf(false) }
                            val syncSlideOffset by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (hideSyncingByAnimation) -1f else 0f,
                                animationSpec = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                finishedListener = { if (it == -1f) authViewModel.setSyncing(false) }
                            )

                            if (isSyncing) {
                                Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationY = syncSlideOffset * size.height }) {
                                    com.example.ui.screens.SyncingScreen(
                                        onFinished = {
                                            hideSyncingByAnimation = true
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // Draw the dashboard behind the splash screen only when successfully authenticated
                        if (authState is AuthState.Success) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = dashboardAlpha
                                        scaleX = dashboardScale
                                        scaleY = dashboardScale
                                    }
                            ) {
                                MainAppContent(
                                    currentTab = currentTab,
                                    isImeVisible = isImeVisible,
                                    viewingItem = viewingItem,
                                    authViewModel = authViewModel,
                                    libraryViewModel = libraryViewModel,
                                    driveViewModel = driveViewModel,
                                    activity = this@MainActivity
                                )

                                if (isSyncing) {
                                    com.example.ui.screens.SyncingScreen(
                                        onFinished = {
                                            authViewModel.setSyncing(false)
                                        }
                                    )
                                }
                            }
                        } else {
                            // If user is not logged in and min time has passed, transition to the sign-in screen
                            if (minTimeElapsed && authState !is AuthState.Loading) {
                                androidx.compose.animation.Crossfade(targetState = true) { _ ->
                                    com.example.ui.screens.AuthScreen(authViewModel = authViewModel)
                                }
                            }
                        }

                        // Overlay the beautiful, animated Splash Screen
                        if (splashSlideOffsetFraction > -1f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationY = splashSlideOffsetFraction * size.height
                                    }
                            ) {
                                ScholarSpaceSplashScreen()
                            }
                        }
                    }
                }
    } // Theme end
} // setContent end
        
        try {
            findViewById<View>(android.R.id.content)?.setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DROP -> {
                        val clipData = event.clipData
                        if (clipData != null) {
                            val dropPermissions = requestDragAndDropPermissions(event)
                            for (i in 0 until clipData.itemCount) {
                                val uri = clipData.getItemAt(i).uri
                                if (uri != null) {
                                    Log.d("MainActivity", "Dropped URI: $uri")
                                    libraryViewModel.addFileFromUri(this, uri) { fileUri, mime, name, fileId ->
                                        if (driveViewModel.isConnected.value) {
                                            driveViewModel.uploadFileToDrive(this, fileUri, mime, name, fileId, libraryViewModel)
                                        }
                                    }
                                }
                            }
                            dropPermissions?.release()
                        }
                        true
                    }
                    else -> true
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Drag listener error", e)
        }
    }

    override fun onResume() {
        super.onResume()
        libraryViewModel.onAppResume()
    }

    override fun onPause() {
        super.onPause()
        libraryViewModel.onAppPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        val openTab = intent.getStringExtra("OPEN_TAB")
        if (openTab != null) {
            libraryViewModel.setCurrentTab(openTab)
        }
        
        when (intent.action) {
            "com.example.ACTION_OPEN_SEARCH" -> {
                libraryViewModel.setCurrentTab("dashboard")
                libraryViewModel.isSearchActive.value = true
            }
            "com.example.ACTION_OPEN_NOTES" -> {
                libraryViewModel.setCurrentTab("notes")
            }
            "com.example.ACTION_OPEN_CALENDAR" -> {
                libraryViewModel.setCurrentTab("calendar")
                libraryViewModel.clearAllNotifications()
            }
            "com.example.ACTION_TOGGLE_STOPWATCH" -> {
                val id = intent.getIntExtra("STOPWATCH_ID", -1)
                if (id != -1) {
                    libraryViewModel.toggleStopwatch(id)
                }
            }
            "com.example.ACTION_TOGGLE_TIMER" -> {
                val id = intent.getIntExtra("TIMER_ID", -1)
                if (id != -1) {
                    libraryViewModel.toggleTimer(id)
                }
            }
            "com.example.ACTION_NEW_NOTE" -> {
                libraryViewModel.setCurrentTab("notes")
                libraryViewModel.triggerOpenNewNoteDirectly()
            }
            "com.example.ACTION_UPLOAD" -> {
                libraryViewModel.setCurrentTab("library")
                // Cannot reliably open launcher from here easily, but we can set fab expanded
                libraryViewModel.isFabExpanded.value = true
            }
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (sharedText != null) {
                        libraryViewModel.addTextFile(sharedText)
                    }
                    Log.d("MainActivity", "Shared Text: $sharedText")
                } else {
                    val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
                    if (uri != null) {
                        libraryViewModel.addFileFromUri(this, uri) { fileUri, mime, name, fileId ->
                            if (driveViewModel.isConnected.value) {
                                driveViewModel.uploadFileToDrive(this, fileUri, mime, name, fileId, libraryViewModel)
                            }
                        }
                    }
                    Log.d("MainActivity", "Shared URI: $uri")
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.filterIsInstance<Uri>()
                uris?.forEach { uri ->
                    Log.d("MainActivity", "Shared Multi URI: $uri")
                    libraryViewModel.addFileFromUri(this, uri) { fileUri, mime, name, fileId ->
                        if (driveViewModel.isConnected.value) {
                            driveViewModel.uploadFileToDrive(this, fileUri, mime, name, fileId, libraryViewModel)
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MainAppContent(
    currentTab: String,
    isImeVisible: Boolean,
    viewingItem: com.example.ui.screens.LibraryItem?,
    authViewModel: AuthViewModel,
    libraryViewModel: LibraryViewModel,
    driveViewModel: DriveViewModel,
    activity: MainActivity
) {
    com.example.ui.components.GlassBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        val isFabExpanded by libraryViewModel.isFabExpanded.collectAsState()
        val isEditingNote by libraryViewModel.isEditingNote.collectAsState()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) { innerPadding ->
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val navBarsHeight = androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density)
                val navBarsHeightDp = with(density) { navBarsHeight.toDp() }
                val bottomGap = 64.dp + 16.dp + navBarsHeightDp

                val customInnerPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = bottomGap
                )

                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Pre-render LibraryScreen in background to avoid jank on first switch
                    // Remains transparent and unclickable, structurally underneath AnimatedContent
                    if (currentTab != "library") {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(-1f)
                                .graphicsLayer { alpha = 0.001f }
                        ) {
                            com.example.ui.screens.LibraryScreen(
                                libraryViewModel = libraryViewModel,
                                driveViewModel = driveViewModel,
                                innerPadding = customInnerPadding
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val tweenSpec = androidx.compose.animation.core.tween<Float>(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            (androidx.compose.animation.fadeIn(animationSpec = tweenSpec) + 
                             androidx.compose.animation.scaleIn(initialScale = 0.95f, animationSpec = tweenSpec))
                             .togetherWith(
                                androidx.compose.animation.fadeOut(animationSpec = tweenSpec)
                             )
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "tab_transition"
                    ) { tab ->
                        when (tab) {
                            "settings" -> {
                                com.example.ui.screens.SettingsScreen(
                                    authViewModel = authViewModel,
                                    libraryViewModel = libraryViewModel,
                                    driveViewModel = driveViewModel,
                                    innerPadding = customInnerPadding
                                )
                            }
                            "dashboard" -> {
                                com.example.ui.screens.DashboardScreen(
                                    authViewModel = authViewModel,
                                    libraryViewModel = libraryViewModel,
                                    driveViewModel = driveViewModel,
                                    innerPadding = customInnerPadding,
                                    onTabSelected = { selected -> libraryViewModel.setCurrentTab(selected) },
                                    onAddClick = {
                                        libraryViewModel.setCurrentTab("library")
                                    },
                                    onOpenItem = { file ->
                                        libraryViewModel.openFile(activity, file, driveViewModel) { item ->
                                            libraryViewModel.setCurrentTab("library")
                                            android.widget.Toast.makeText(activity, "Summarizing ${item.title} on Library Screen", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            "library" -> {
                                com.example.ui.screens.LibraryScreen(
                                    libraryViewModel = libraryViewModel,
                                    driveViewModel = driveViewModel,
                                    innerPadding = customInnerPadding
                                )
                            }
                            "notes" -> {
                                com.example.ui.screens.NotesScreen(
                                    libraryViewModel = libraryViewModel,
                                    driveViewModel = driveViewModel,
                                    innerPadding = customInnerPadding
                                )
                            }
                            "calendar" -> {
                                com.example.ui.screens.CalendarScreen(
                                    libraryViewModel = libraryViewModel,
                                    innerPadding = customInnerPadding
                                )
                            }
                        }
                    }
                }

                val isSearchActive by libraryViewModel.isSearchActive.collectAsState()
                if (!isImeVisible && !isEditingNote && currentTab != "settings" && !isSearchActive) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        com.example.ui.screens.BottomNavBar(
                            currentTab = currentTab,
                            onTabSelected = { tab ->
                                libraryViewModel.isFabExpanded.value = false
                                if (tab == "library") {
                                    libraryViewModel.triggerResetLibrary()
                                } else if (tab == "dashboard") {
                                    libraryViewModel.triggerResetDashboard()
                                }
                                libraryViewModel.setCurrentTab(tab)
                            },
                            onAddClick = { libraryViewModel.setCurrentTab("library") }
                        )
                    }
                }
            }
        }

        var rememberedViewingItem by remember { mutableStateOf<com.example.ui.screens.LibraryItem?>(null) }
        LaunchedEffect(viewingItem) {
            if (viewingItem != null) {
                rememberedViewingItem = viewingItem
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = viewingItem != null,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) + 
                    androidx.compose.animation.scaleIn(initialScale = 0.9f, animationSpec = androidx.compose.animation.core.tween(300)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) + 
                   androidx.compose.animation.scaleOut(targetScale = 0.9f, animationSpec = androidx.compose.animation.core.tween(300))
        ) {
            rememberedViewingItem?.let { item ->
                com.example.ui.screens.FileViewerOverlay(
                    item = item,
                    onClose = { libraryViewModel.setViewingItem(null) },
                    onShare = { libraryViewModel.shareFile(activity, it, driveViewModel) },
                    onDownload = { libraryViewModel.saveToDevice(activity, it, driveViewModel) }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ScholarSpaceSplashScreen() {
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startAnim = true
    }

    val logoScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.4f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        )
    )

    val logoAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(1000)
    )

    val textAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(1200)
    )

    val textOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (startAnim) 0.dp else 40.dp,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        )
    )

    com.example.ui.components.GlassBackground(
        modifier = Modifier.fillMaxSize(),
        drawBackgroundAndCircles = true
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // App Logo with pulse & pop enter
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                            alpha = logoAlpha
                        }
                        .size(160.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFF0F172A).copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                        contentDescription = "App Logo",
                        modifier = Modifier.requiredSize(240.dp),
                        tint = Color.Unspecified
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // App Name with slide-fade-in
                androidx.compose.material3.Text(
                    text = "Scholar Space",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier
                        .offset(y = textOffset)
                        .graphicsLayer {
                            alpha = textAlpha
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Premium Tagline with slide-fade-in
                androidx.compose.material3.Text(
                    text = "Your Intelligent Academic Organizer",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier
                        .offset(y = textOffset)
                        .graphicsLayer {
                            alpha = textAlpha
                        }
                )
            }

            // Account status and loading check indicator
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 48.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = com.example.ui.theme.Cyan400,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Text(
                        text = "Securing account connection...",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

