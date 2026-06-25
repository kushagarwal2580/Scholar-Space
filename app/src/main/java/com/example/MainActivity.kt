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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
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
                                driveViewModel.syncMetadata(this@MainActivity, authViewModel, libraryViewModel, isUpload = false) {
                                    driveViewModel.syncDriveData(this@MainActivity, libraryViewModel)
                                }
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
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            window.let { win ->
                val display = win.windowManager.defaultDisplay
                val modes = display.supportedModes
                val preferredMode = modes.maxByOrNull { it.refreshRate }
                if (preferredMode != null) {
                    val layoutParams = win.attributes
                    layoutParams.preferredDisplayModeId = preferredMode.modeId
                    win.attributes = layoutParams
                }
            }
        }
        
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
                var showSyncScreen by androidx.compose.runtime.remember { mutableStateOf(false) }
                
                LaunchedEffect(isSyncing) {
                    if (isSyncing) {
                        showSyncScreen = true
                        kotlinx.coroutines.delay(5000)
                        authViewModel.setSyncing(false)
                        showSyncScreen = false
                    }
                }
                
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
                
                val recoverableAuthIntent by driveViewModel.recoverableAuthIntent.collectAsState()
                val driveAuthLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        val email = driveViewModel.activeAccount.value
                        if (email != null) {
                            driveViewModel.fetchDriveStorage(this@MainActivity, email)
                            driveViewModel.syncMetadata(this@MainActivity, authViewModel, libraryViewModel, isUpload = false) {
                                driveViewModel.syncDriveData(this@MainActivity, libraryViewModel)
                            }
                        }
                    }
                }
                
                LaunchedEffect(recoverableAuthIntent) {
                    recoverableAuthIntent?.let {
                        try {
                            driveAuthLauncher.launch(it)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(this@MainActivity, "Google Play Services not available to grant Drive access.", android.widget.Toast.LENGTH_LONG).show()
                        }
                        driveViewModel.clearRecoverableAuthIntent()
                    }
                }
                
                val scholarSpaceFolderId by driveViewModel.scholarSpaceFolderId.collectAsState()
                LaunchedEffect(activeAccount, scholarSpaceFolderId) {
                    if (previousAccount != activeAccount && previousAccount != null) {
                        libraryViewModel.clearFiles(this@MainActivity)
                    }
                    previousAccount = activeAccount
                    if (activeAccount != null && scholarSpaceFolderId != null) {
                        driveViewModel.syncMetadata(this@MainActivity, authViewModel, libraryViewModel, isUpload = false) {
                            driveViewModel.syncDriveData(this@MainActivity, libraryViewModel) {
                                libraryViewModel.onStateChangedListener = {
                                    driveViewModel.triggerMetadataUpload(this@MainActivity, authViewModel, libraryViewModel)
                                }
                            }
                        }
                    } else if (activeAccount == null) {
                        libraryViewModel.onStateChangedListener = null
                    }
                }
                
                LaunchedEffect(authState) {
                    if (authState is AuthState.Success) {
                        val success = authState as AuthState.Success
                        val email = success.email
                        driveViewModel.setAppUserEmail(this@MainActivity, email)
                    } else if (authState is AuthState.Idle || authState is AuthState.Error) {
                        driveViewModel.setAppUserEmail(this@MainActivity, null)
                        libraryViewModel.setCurrentTab("dashboard")
                    }
                }
                
                // --- Premium Startup Splash Screen Animation Logic ---
                var hasStartedDismissal by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                var splashFinished by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                var minTimeElapsed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2500)
                    minTimeElapsed = true
                }
                
                LaunchedEffect(minTimeElapsed, authState, isMetadataSyncing) {
                    if (minTimeElapsed && authState !is AuthState.Loading && !isMetadataSyncing) {
                        hasStartedDismissal = true
                    }
                }

                // Syncing Screen Animation State is declared above
                
                // GitHub Update Check State
                var showUpdateDialog by androidx.compose.runtime.remember { mutableStateOf(false) }
                var updateUrl by androidx.compose.runtime.remember { mutableStateOf("") }
                var updateVersionName by androidx.compose.runtime.remember { mutableStateOf("") }
                val context = androidx.compose.ui.platform.LocalContext.current
                val sharedPreferences = androidx.compose.runtime.remember {
                    context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                }
                
                LaunchedEffect(splashFinished, authState) {
                    if (splashFinished && authState is AuthState.Success) {
                        var launchCount = sharedPreferences.getInt("update_launch_count", 3) // Defaults to 3 so it shows first time
                        
                        if (launchCount >= 3) {
                            val updateInfo = com.example.utils.UpdateChecker.checkForUpdates(
                                githubOwner = "kushagarwal2580", // Replace with Github Username
                                githubRepo = "Scholar-Space", // Replace with Github Repository Name
                                currentVersion = com.example.BuildConfig.VERSION_NAME
                            )
                            
                            if (updateInfo != null && updateInfo.isUpdateAvailable) {
                                updateUrl = updateInfo.downloadUrl
                                updateVersionName = updateInfo.latestVersion
                                showUpdateDialog = true
                            }
                        } else {
                            // Only increment when we don't show the dialog
                            sharedPreferences.edit().putInt("update_launch_count", launchCount + 1).apply()
                        }
                    }
                }
                
                if (showUpdateDialog) {
                    com.example.ui.components.UpdateDialog(
                        showDialog = showUpdateDialog,
                        onDismiss = {
                            showUpdateDialog = false
                            sharedPreferences.edit().putInt("update_launch_count", 0).apply() // Reset timer when dismissed
                        },
                        updateUrl = updateUrl,
                        versionName = updateVersionName
                    )
                }

                com.example.ui.components.GlassBackground(
                    modifier = Modifier.fillMaxSize(),
                    drawBackgroundAndCircles = true
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (authState !is AuthState.Success) {
                            // Not logged in or loading
                            if (hasStartedDismissal || (minTimeElapsed && authState !is AuthState.Loading)) {
                                androidx.compose.animation.Crossfade(targetState = true) { _ ->
                                    com.example.ui.screens.AuthScreen(authViewModel = authViewModel)
                                }
                            }
                        } else {
                            // User is successfully authenticated
                            val authSuccess = authState as AuthState.Success
                            val isDriveConnected by driveViewModel.isConnected.collectAsState()
                            
                            val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                            ) { result ->
                                driveViewModel.handleSignInResult(this@MainActivity, result.data)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                if (!authSuccess.hasDrivePermission && !isDriveConnected) {
                                    com.example.ui.screens.ConnectDriveScreen(
                                        onConnectClick = {
                                            googleSignInLauncher.launch(driveViewModel.getSignInIntent(this@MainActivity))
                                        }
                                    )
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
                                }

                                // Syncing Screen Overlay with slide-up out animation
                                var syncFinished by androidx.compose.runtime.remember { mutableStateOf(true) }
                                val isOverlayActive = showSyncScreen || isSyncing
                                
                                androidx.compose.runtime.LaunchedEffect(isOverlayActive) {
                                    if (isOverlayActive) {
                                        syncFinished = false
                                    }
                                }
                                
                                val syncOffset by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = if (!isOverlayActive) -1f else 0f,
                                    animationSpec = if (isOverlayActive) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(
                                        durationMillis = 1000,
                                        easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
                                    ),
                                    label = "syncOffset",
                                    finishedListener = { 
                                        if (it == -1f) {
                                            syncFinished = true
                                        }
                                    }
                                )
                                val syncAlpha by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = if (!isOverlayActive) 0f else 1f,
                                    animationSpec = if (isOverlayActive) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(500),
                                    label = "syncAlpha"
                                )

                                if (!syncFinished || isOverlayActive) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                translationY = syncOffset * size.height
                                                alpha = syncAlpha
                                            }
                                    ) {
                                        com.example.ui.screens.SyncingScreen(
                                            onFinished = {
                                                showSyncScreen = false
                                                authViewModel.setSyncing(false)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Overlay the beautiful, animated Splash Screen
                        val splashOffset by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (hasStartedDismissal) -1f else 0f,
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 1500,
                                easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
                            ),
                            label = "splashOffset",
                            finishedListener = { if (it == -1f) splashFinished = true }
                        )
                        val splashAlpha by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (hasStartedDismissal) 0f else 1f,
                            animationSpec = androidx.compose.animation.core.tween(500),
                            label = "splashAlpha"
                        )

                        if (!splashFinished) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationY = splashOffset * size.height
                                        alpha = splashAlpha
                                    }
                            ) {
                                ScholarSpaceSplashScreen()
                            }

                            // Pre-compose major user-facing screens during startup animation to warm up JIT compiler,
                            // deserialize data asynchronously, and cache layouts, ensuring buttery smooth transitions.
                            Box(
                                modifier = Modifier
                                    .size(1.dp)
                                    .graphicsLayer { alpha = 0.01f }
                            ) {
                                com.example.ui.screens.LibraryScreen(
                                    libraryViewModel = libraryViewModel,
                                    driveViewModel = driveViewModel,
                                    innerPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                )
                                com.example.ui.screens.NotesScreen(
                                    libraryViewModel = libraryViewModel,
                                    driveViewModel = driveViewModel,
                                    innerPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                )
                                com.example.ui.screens.CalendarScreen(
                                    libraryViewModel = libraryViewModel,
                                    innerPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                )
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
                    androidx.compose.animation.AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val enterSpec = androidx.compose.animation.core.tween<Float>(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            val exitSpec = androidx.compose.animation.core.tween<Float>(durationMillis = 90, easing = androidx.compose.animation.core.LinearEasing)
                            (androidx.compose.animation.fadeIn(animationSpec = enterSpec) +
                             androidx.compose.animation.scaleIn(initialScale = 0.92f, animationSpec = enterSpec))
                            .togetherWith(
                                androidx.compose.animation.fadeOut(animationSpec = exitSpec) +
                                androidx.compose.animation.scaleOut(targetScale = 0.92f, animationSpec = exitSpec)
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "tab_transition"
                    ) { tab ->
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                            when(tab) {
                                "settings" -> com.example.ui.screens.SettingsScreen(
                                    authViewModel = authViewModel,
                                    libraryViewModel = libraryViewModel,
                                    driveViewModel = driveViewModel,
                                    innerPadding = customInnerPadding,
                                    onBack = { libraryViewModel.setCurrentTab("dashboard") }
                                )
                                "dashboard" -> com.example.ui.screens.DashboardScreen(
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
                                "library" -> com.example.ui.screens.LibraryScreen(
                                    libraryViewModel = libraryViewModel,
                                    driveViewModel = driveViewModel,
                                    innerPadding = customInnerPadding
                                )
                                "notes" -> com.example.ui.screens.NotesScreen(
                                    libraryViewModel = libraryViewModel,
                                    driveViewModel = driveViewModel,
                                    innerPadding = customInnerPadding
                                )
                                "calendar" -> com.example.ui.screens.CalendarScreen(
                                    libraryViewModel = libraryViewModel,
                                    innerPadding = customInnerPadding
                                )
                            }
                        }
                    }
                }

                val isSearchActive by libraryViewModel.isSearchActive.collectAsState()
                if (!isImeVisible && !isEditingNote && currentTab != "settings" && !isSearchActive) {
                    val isDark = true
                    val bottomBgColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF0F172A) else androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color.Transparent,
                                        bottomBgColor.copy(alpha = 0.8f),
                                        bottomBgColor
                                    )
                                )
                            )
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp, top = 32.dp)
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
