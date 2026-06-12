package com.example.ui.screens

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import androidx.compose.foundation.clickable
import com.example.ui.components.GlassBackground
import com.example.ui.components.glassMorphic
import java.io.File

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.DriveViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn

import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler

@Composable
fun DashboardScreen(
    authViewModel: AuthViewModel? = null,
    libraryViewModel: LibraryViewModel,
    driveViewModel: DriveViewModel = viewModel(),
    innerPadding: PaddingValues = PaddingValues(0.dp),
    onTabSelected: (String) -> Unit,
    onAddClick: () -> Unit,
    onOpenItem: (LibraryItem) -> Unit
) {
    val context = LocalContext.current
    val fallbackFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<AuthState>(AuthState.Idle) }
    val authState by (authViewModel?.uiState ?: fallbackFlow).collectAsState()
    
    LaunchedEffect(Unit) {
        authViewModel?.checkExistingGoogleAccount(context)
    }
    
    val accounts by driveViewModel.accounts.collectAsState()
    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            val appEmail = (authState as AuthState.Success).email
            driveViewModel.setAppUserEmail(context, appEmail)
        } else {
            driveViewModel.setAppUserEmail(context, null)
        }
    }
    
    val currentTab by libraryViewModel.currentTab.collectAsState()
    var showEventSelectionDialog by remember { mutableStateOf(false) }
    var showNoteSelectionDialogForSlot by remember { mutableStateOf<Int?>(null) }
    var showAudioPlayDialogForNote by remember { mutableStateOf<VoiceNote?>(null) }
    val isFabExpanded by libraryViewModel.isFabExpanded.collectAsState()
    val allFiles by libraryViewModel.allFiles.collectAsState()
    val dayCounters by libraryViewModel.dayCounters.collectAsState()
    val timers by libraryViewModel.timers.collectAsState()
    val stopwatches by libraryViewModel.stopwatches.collectAsState()
    val allReminders by libraryViewModel.allReminders.collectAsState()
    val recentFiles = allFiles.filter { !it.isFolder && it.lastAccessedAt > 0L }.sortedByDescending { it.lastAccessedAt }

    val driveUsage by driveViewModel.storageUsage.collectAsState()
    val driveLimit by driveViewModel.storageLimit.collectAsState()
    val isDriveConnected by driveViewModel.isConnected.collectAsState()
    val activeAccount by driveViewModel.activeAccount.collectAsState()
    val scholarSpaceFolderId by driveViewModel.scholarSpaceFolderId.collectAsState()
    val uploadingFiles by driveViewModel.uploadingFiles.collectAsState()
    val downloadingFiles by driveViewModel.downloadingFiles.collectAsState()
    
    val isOnline = remember { 
        mutableStateOf(
            (context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).let { cm ->
                val nw = cm.activeNetwork
                if (nw == null) false
                else {
                    val actNw = cm.getNetworkCapabilities(nw)
                    actNw?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                }
            }
        ) 
    }
    
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { isOnline.value = true }
            override fun onLost(network: android.net.Network) { isOnline.value = false }
            override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
                isOnline.value = networkCapabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    LaunchedEffect(isDriveConnected, scholarSpaceFolderId) {
        if (isDriveConnected && scholarSpaceFolderId != null) {
            driveViewModel.syncDriveData(context, libraryViewModel)
        }
    }

    val statFs = remember { android.os.StatFs(android.os.Environment.getDataDirectory().path) }
    val totalBytes = statFs.totalBytes
    val freeBytes = statFs.availableBytes
    val usedBytes = totalBytes - freeBytes

    val usedGb = if (isDriveConnected) driveUsage / (1024f * 1024f * 1024f) else usedBytes / (1024f * 1024f * 1024f)
    val totalGb = if (isDriveConnected) driveLimit / (1024f * 1024f * 1024f) else totalBytes / (1024f * 1024f * 1024f)
    val usedRatio = if (isDriveConnected) {
        if (driveLimit > 0) (driveUsage.toFloat() / driveLimit).coerceIn(0f, 1f) else 0f
    } else {
        if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
    fun formatStorageValue(gb: Float): String {
        return if (gb >= 1024f) {
            String.format(java.util.Locale.US, "%.1f TB", gb / 1024f)
        } else {
            String.format(java.util.Locale.US, "%.1f GB", gb)
        }
    }
    val storageText = "${formatStorageValue(usedGb)} / ${formatStorageValue(totalGb)}"
    val storageTitle = if (isDriveConnected) "DRIVE OR LOCAL STORAGE" else "STORAGE USAGE"

    val prefs = remember { context.getSharedPreferences("SettingsPrefs", android.content.Context.MODE_PRIVATE) }
    
    val recoverableAuthIntent by driveViewModel.recoverableAuthIntent.collectAsState()
    val driveAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val email = driveViewModel.activeAccount.value
            if (email != null) {
                driveViewModel.fetchDriveStorage(context, email)
            }
        }
    }
    LaunchedEffect(recoverableAuthIntent) {
        recoverableAuthIntent?.let {
            try {
                driveAuthLauncher.launch(it)
            } catch (e: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(context, "Google Play Services not available to grant Drive access.", android.widget.Toast.LENGTH_LONG).show()
            }
            driveViewModel.clearRecoverableAuthIntent()
        }
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            libraryViewModel.addFileFromUri(context, uri) { fileUri, mimeType, name, fileId ->
                val syncEnabled = true
                if (syncEnabled && driveViewModel.isConnected.value) {
                    driveViewModel.uploadFileToDrive(context, fileUri, mimeType, name, fileId, libraryViewModel)
                }
            }
        }
    }

    val scannerOptions = remember {
        com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(
                com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember { com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(scannerOptions) }
    
    val scannerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanResult = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pdf?.uri?.let { uri ->
                libraryViewModel.addFileFromUri(context, uri) { fileUri, mimeType, name, fileId ->
                    val syncEnabled = true
                    if (syncEnabled && driveViewModel.isConnected.value) {
                        driveViewModel.uploadFileToDrive(context, fileUri, mimeType, name, fileId, libraryViewModel)
                    }
                }
            } ?: scanResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                libraryViewModel.addFileFromUri(context, uri) { fileUri, mimeType, name, fileId ->
                    val syncEnabled = true
                    if (syncEnabled && driveViewModel.isConnected.value) {
                        driveViewModel.uploadFileToDrive(context, fileUri, mimeType, name, fileId, libraryViewModel)
                    }
                }
            }
        }
    }

    val searchActive by libraryViewModel.isSearchActive.collectAsState()
    var searchQuery by remember { androidx.compose.runtime.mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var activeViewingNote by remember { mutableStateOf<NoteItem?>(null) }
    val notesState by libraryViewModel.notes.collectAsState()
    
    val resetTrigger by libraryViewModel.resetDashboardTrigger.collectAsState()
    androidx.compose.runtime.LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0L) {
            libraryViewModel.isSearchActive.value = false
            searchQuery = ""
            showEventSelectionDialog = false
        }
    }
    
    BackHandler(enabled = searchActive) {
        libraryViewModel.isSearchActive.value = false
        searchQuery = ""
    }

    val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    GlassBackground(
        modifier = Modifier.fillMaxSize(),
        drawBackgroundAndCircles = false
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                    }
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = 0.dp
                    )
            ) {
        androidx.compose.animation.AnimatedContent(
            targetState = searchActive,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) + 
                    androidx.compose.animation.scaleIn(
                        initialScale = 0.95f, 
                        animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                    )).togetherWith(
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(250)) + 
                    androidx.compose.animation.scaleOut(
                        targetScale = 0.95f, 
                        animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                    )
                )
            },
            label = "searchAnimation"
        ) { active ->
        if (active) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).glassMorphic(RoundedCornerShape(28.dp)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { 
                        libraryViewModel.isSearchActive.value = false
                        searchQuery = ""
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Cyan400),
                        decorationBox = { innerTextField ->
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search here...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                
                // Local helper to match file category
                fun getFileCategory(item: LibraryItem): String {
                    val titleLower = item.title.lowercase()
                    return when {
                        titleLower.endsWith(".png") || titleLower.endsWith(".jpg") || titleLower.endsWith(".jpeg") || titleLower.endsWith(".gif") || titleLower.endsWith(".bmp") || titleLower.endsWith(".webp") || item.icon == Icons.Default.Image -> "photo"
                        titleLower.endsWith(".mp4") || titleLower.endsWith(".mkv") || titleLower.endsWith(".avi") || titleLower.endsWith(".mov") || titleLower.endsWith(".flv") || titleLower.endsWith(".webm") || titleLower.endsWith(".3gp") -> "video"
                        titleLower.endsWith(".mp3") || titleLower.endsWith(".wav") || titleLower.endsWith(".m4a") || titleLower.endsWith(".ogg") || titleLower.endsWith(".flac") || titleLower.endsWith(".aac") || item.icon == Icons.Default.Mic -> "audio"
                        titleLower.endsWith(".pdf") || titleLower.endsWith(".doc") || titleLower.endsWith(".docx") || titleLower.endsWith(".txt") || titleLower.endsWith(".xls") || titleLower.endsWith(".xlsx") || titleLower.endsWith(".ppt") || titleLower.endsWith(".pptx") || item.icon == Icons.Default.Description -> "document"
                        else -> "other"
                    }
                }

                val queryText = searchQuery.trim()
                
                // Determine filter mode
                val filterMode = when {
                    queryText.startsWith("/photo ") || queryText == "/photo" -> "photo"
                    queryText.startsWith("/image ") || queryText == "/image" -> "photo"
                    queryText.startsWith("/video ") || queryText == "/video" -> "video"
                    queryText.startsWith("/audio ") || queryText == "/audio" -> "audio"
                    queryText.startsWith("/document ") || queryText == "/document" -> "document"
                    queryText.startsWith("/notes ") || queryText == "/notes" || queryText.startsWith("/note ") || queryText == "/note" -> "notes"
                    else -> null
                }
                
                // The actual search query after stripping the slash prefix
                val actualQuery = if (filterMode != null) {
                    val prefixUsed = when {
                        queryText.startsWith("/photo") -> "/photo"
                        queryText.startsWith("/image") -> "/image"
                        queryText.startsWith("/video") -> "/video"
                        queryText.startsWith("/audio") -> "/audio"
                        queryText.startsWith("/document") -> "/document"
                        queryText.startsWith("/notes") -> "/notes"
                        queryText.startsWith("/note") -> "/note"
                        else -> "/$filterMode"
                    }
                    if (queryText.length > prefixUsed.length) {
                        queryText.substring(prefixUsed.length).trim()
                    } else {
                        ""
                    }
                } else {
                    queryText
                }
                
                val lowerActualQuery = actualQuery.lowercase()
                
                // Filter files
                val filteredFiles = if (lowerActualQuery.isEmpty() && filterMode == null) {
                    emptyList()
                } else if (filterMode == "notes") {
                    emptyList()
                } else {
                    allFiles.filter { file ->
                        !file.title.equals("App Data", ignoreCase = true) && !file.isFolder &&
                        (filterMode == null || getFileCategory(file) == filterMode) &&
                        (lowerActualQuery.isEmpty() || file.title.lowercase().contains(lowerActualQuery) || file.subtitle.lowercase().contains(lowerActualQuery))
                    }
                }
                
                // Filter notes (only show if filterMode is "notes" or if filterMode is null and query matches)
                val filteredNotes = if (lowerActualQuery.isEmpty() && filterMode == null) {
                    emptyList()
                } else if (filterMode != null && filterMode != "notes") {
                    emptyList()
                } else {
                    notesState.filter { note ->
                        lowerActualQuery.isEmpty() || note.title.lowercase().contains(lowerActualQuery) || note.content.lowercase().contains(lowerActualQuery)
                    }
                }

                val isSlashCommandActive = searchQuery.startsWith("/")
                val showTagSuggestions = isSlashCommandActive && !searchQuery.contains(" ")

                if (showTagSuggestions) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassMorphic(RoundedCornerShape(16.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "FILTER SEARCH",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Cyan400,
                                modifier = Modifier.padding(bottom = 6.dp, start = 8.dp, top = 4.dp)
                            )
                            val tagSuggestions = listOf(
                                Triple("/photo", "Images / Photos", Icons.Default.Image),
                                Triple("/video", "Videos & Lecture Media", Icons.Default.PlayArrow),
                                Triple("/audio", "Voice Memos & Audio recordings", Icons.Default.Mic),
                                Triple("/document", "PDFs & Documents", Icons.Default.Description),
                                Triple("/notes", "Personal Notepad & Notes", Icons.Default.Description)
                            ).filter { it.first.startsWith(searchQuery.lowercase()) }
                            
                            if (tagSuggestions.isEmpty()) {
                                Text(
                                    text = "No matching tags",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            } else {
                                tagSuggestions.forEach { (tag, desc, icon) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                searchQuery = "$tag "
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Cyan400.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = Cyan400,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = tag,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = desc,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if ((filteredFiles.isNotEmpty() || filteredNotes.isNotEmpty())) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (filteredFiles.isNotEmpty()) {
                            item {
                                Text(
                                    text = "MATCHING FILES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Cyan400,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(filteredFiles, key = { "file_${it.id}" }) { file ->
                                com.example.ui.screens.LibraryListItem(
                                    item = file,
                                    uploadProgress = uploadingFiles[file.id],
                                    downloadProgress = downloadingFiles[file.id],
                                    isSelected = false,
                                    onDelete = { 
                                        libraryViewModel.deleteFile(file.id) 
                                        if (driveViewModel.isConnected.value) {
                                            driveViewModel.deleteFileFromDrive(context, file.title, file.driveFileId)
                                        }
                                    },
                                    onRename = {  },
                                    onMove = {  },
                                    onShare = { libraryViewModel.shareFile(context, file, driveViewModel) },
                                    onSaveToDevice = { libraryViewModel.saveToDevice(context, file, driveViewModel) },
                                    onCancelUpload = { driveViewModel.cancelUpload(file.id) },
                                    onCancelDownload = { driveViewModel.cancelDownload(file.id) },
                                    onClick = {
                                        if (downloadingFiles.containsKey(file.id)) {
                                            /* do nothing while downloading */
                                        } else if (file.uri == null && file.driveFileId != null) {
                                            if (!isOnline.value) {
                                                android.widget.Toast.makeText(context, "Please connect to internet to open this file", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                driveViewModel.downloadFileFromDrive(context, libraryViewModel, file.driveFileId, file.title, file.id) { }
                                            }
                                        } else {
                                            // libraryViewModel.isSearchActive.value = false
                                            onOpenItem(file)
                                        }
                                    },
                                    showOptions = false
                                )
                            }
                        }
                        
                        if (filteredNotes.isNotEmpty()) {
                            item {
                                Text(
                                    text = "MATCHING NOTES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Cyan400,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(filteredNotes, key = { "note_${it.id}" }) { note ->
                                NoteItemRow(
                                    item = note,
                                    onOpen = {
                                        libraryViewModel.triggerOpenNoteDirectly(note.id)
                                    },
                                    onDelete = {
                                        libraryViewModel.deleteNote(note.id)
                                    }
                                )
                            }
                        }
                    }
                } else if (lowerActualQuery.isNotEmpty() || filterMode != null) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No items found for \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Type to search...\nPress / to use tags", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
            Spacer(modifier = Modifier.height(32.dp))

            // App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.4f)), // Dull effect behind logo
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                            contentDescription = "App Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.requiredSize(84.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Scholar Space",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (authState is AuthState.Success) {
                            Text(
                                text = (authState as AuthState.Success).displayName ?: "Signed in",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if ((authState as AuthState.Success).statusMsg.isNullOrBlank()) "No status set" else (authState as AuthState.Success).statusMsg!!,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        } else if (activeAccount != null) {
                            Text(
                                text = "Signed in",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = activeAccount!!,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        } else {
                            Text(
                                text = "Not signed in",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (authState is AuthState.Success || activeAccount != null) Cyan400 else Slate800)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        .clickable { 
                            onTabSelected("settings")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Slate400, strokeWidth = 2.dp)
                    } else if (authState is AuthState.Success && !(authState as AuthState.Success).profilePic.isNullOrEmpty()) {
                        val picUrl = (authState as AuthState.Success).profilePic
                        val imgReq = remember(picUrl) {
                            coil.request.ImageRequest.Builder(context)
                                .data(picUrl)
                                .memoryCacheKey(picUrl)
                                .diskCacheKey(picUrl)
                                .crossfade(false)
                                .build()
                        }
                        coil.compose.AsyncImage(
                            model = imgReq,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else if (authState is AuthState.Success) {
                        Text(
                            text = (authState as AuthState.Success).displayName?.firstOrNull()?.uppercase() ?: "U",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (activeAccount != null) {
                        Text(
                            text = activeAccount!!.firstOrNull()?.uppercase() ?: "U",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Slate400
                        )
                    }
                }
            }

            // Local Search Bar (Frosted)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .glassMorphic(CircleShape)
                    .clickable { libraryViewModel.isSearchActive.value = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search Files", tint = Cyan400)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Search here...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Stats
            // Drive Storage Widget (Extended to full width like research/search box)
            val accounts by driveViewModel.accounts.collectAsState()
            val activeAccount by driveViewModel.activeAccount.collectAsState()

            Box(modifier = Modifier.fillMaxWidth()) {
                FrostedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { 
                        onTabSelected("library")
                    }
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (isDriveConnected) "DRIVE STORAGE" else "LOCAL STORAGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Cyan300, letterSpacing = 1.sp)
                            
                            if (!isDriveConnected && authState is AuthState.Success) {
                                IconButton(
                                    onClick = {
                                        driveViewModel.signInWithGoogle(context) { driveEmail ->
                                            if (driveEmail != null) {
                                                driveViewModel.handleSignInEmail(context, driveEmail)
                                            } else {
                                                android.widget.Toast.makeText(context, "Sign in cancelled or failed. Missing Client ID?", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = "Connect Drive", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = storageText, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Slate700)) {
                            Box(modifier = Modifier.fillMaxWidth(usedRatio).fillMaxHeight().background(Cyan400))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Spotlight/Selected Event Widget (Extended and below, same UI style as CalendarScreen)
            val pinnedCounter = dayCounters.firstOrNull { it.isPinned }
            val pinnedTimer = timers.firstOrNull { it.isPinned }
            val pinnedStopwatch = stopwatches.firstOrNull { it.isPinned }
            
            var pinnedReminderDateMillis = 0L
            var pinnedReminder: Reminder? = null
            
            allReminders.forEach { (dateKey, list) ->
                val found = list.find { it.isPinned }
                if (found != null) {
                    pinnedReminder = found
                    pinnedReminderDateMillis = dateKey.toLongOrNull() ?: 0L
                }
            }

            if (pinnedCounter != null) {
                val isCompleted = pinnedCounter.daysLeft <= 0
                FrostedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isCompleted) {
                            libraryViewModel.togglePinDayCounter(pinnedCounter.id)
                        } else {
                            showEventSelectionDialog = true
                        }
                    }
                ) {
                    if (isCompleted) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "EVENT COMPLETED! TAP TO DISMISS",
                                color = Cyan400,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("DAYS UNTIL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Cyan400, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = pinnedCounter.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Cyan400.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = pinnedCounter.daysLeft.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Cyan400)
                            }
                        }
                    }
                }
            } else if (pinnedTimer != null) {
                val isCompleted = pinnedTimer.timeRemaining <= 0
                FrostedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isCompleted) {
                            libraryViewModel.togglePinTimer(pinnedTimer.id)
                        } else {
                            showEventSelectionDialog = true
                        }
                    }
                ) {
                    if (isCompleted) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "EVENT COMPLETED! TAP TO DISMISS",
                                color = Cyan400,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    } else {
                        val hrs = pinnedTimer.timeRemaining / 3600
                        val mins = (pinnedTimer.timeRemaining % 3600) / 60
                        val secs = pinnedTimer.timeRemaining % 60
                        val timeStr = String.format("%02d:%02d:%02d", hrs, mins, secs)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val durationHrs = pinnedTimer.durationMinutes / 60
                                val durationMins = pinnedTimer.durationMinutes % 60
                                val titleText = if (pinnedTimer.title.isNotBlank()) pinnedTimer.title.uppercase() else if (durationHrs > 0 && durationMins > 0) "${durationHrs}H ${durationMins}M TIMER" else if (durationHrs > 0) "${durationHrs}H TIMER" else "${durationMins} MIN TIMER"
                                Text(text = titleText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Cyan400, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = timeStr, fontSize = 28.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { libraryViewModel.toggleTimer(pinnedTimer.id) }) {
                                    Icon(
                                        imageVector = if (pinnedTimer.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (pinnedTimer.isRunning) "Pause" else "Play",
                                        tint = Cyan400
                                    )
                                }
                                IconButton(onClick = { libraryViewModel.resetTimer(pinnedTimer.id) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else if (pinnedStopwatch != null) {
                var currentElapsed by remember { mutableStateOf(pinnedStopwatch.elapsedMillis) }
                
                LaunchedEffect(pinnedStopwatch.isRunning, pinnedStopwatch.elapsedMillis) {
                    if (pinnedStopwatch.isRunning) {
                        while(true) {
                            currentElapsed = System.currentTimeMillis() - pinnedStopwatch.startTime
                            kotlinx.coroutines.delay(30)
                        }
                    } else {
                        currentElapsed = pinnedStopwatch.elapsedMillis
                    }
                }
                
                val hrs = (currentElapsed / 3600000)
                val minutes = ((currentElapsed % 3600000) / 60000)
                val seconds = ((currentElapsed % 60000) / 1000)
                val timeStr = String.format("%02d:%02d:%02d", hrs, minutes, seconds)

                FrostedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showEventSelectionDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("STOPWATCH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Cyan400, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = pinnedStopwatch.title.uppercase(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = timeStr, fontSize = 24.sp, fontWeight = FontWeight.Light, color = Cyan400)
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(onClick = { libraryViewModel.toggleStopwatch(pinnedStopwatch.id) }) {
                                Icon(
                                    imageVector = if (pinnedStopwatch.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (pinnedStopwatch.isRunning) "Pause" else "Play",
                                    tint = Cyan400
                                )
                            }
                        }
                    }
                }
            } else if (pinnedReminder != null) {
                val currentPinnedReminder = pinnedReminder!!
                val isCompleted = currentPinnedReminder.isNotified || getReminderTimeRemaining(pinnedReminderDateMillis, currentPinnedReminder.time) == "Reminder elapsed"
                FrostedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isCompleted) {
                            libraryViewModel.togglePinReminder(pinnedReminderDateMillis, currentPinnedReminder.id)
                        } else {
                            showEventSelectionDialog = true
                        }
                    }
                ) {
                    if (isCompleted) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "EVENT COMPLETED! TAP TO DISMISS",
                                color = Cyan400,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = Cyan400, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = "REMINDER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Cyan400, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(currentPinnedReminder.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val remainingStr = getReminderTimeRemaining(pinnedReminderDateMillis, currentPinnedReminder.time)
                                    Text(remainingStr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                FrostedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showEventSelectionDialog = true }
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = Cyan400.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "TAP TO SELECT SPOTLIGHT EVENT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Cyan300,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            if (showEventSelectionDialog) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showEventSelectionDialog = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(modifier = Modifier.padding(24.dp)) {
                        GlassBackground(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "Select Spotlight Event",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                val scrollState = androidx.compose.foundation.rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .verticalScroll(scrollState)
                                ) {
                                    // 1. Reminders
                                    val flatReminders = allReminders.flatMap { (dateKey, list) ->
                                        val dateMillis = dateKey.toLongOrNull() ?: 0L
                                        list.map { it to dateMillis }
                                    }
                                    
                                    if (flatReminders.isNotEmpty() || dayCounters.isNotEmpty() || timers.isNotEmpty() || stopwatches.isNotEmpty()) {
                                        if (flatReminders.isNotEmpty()) {
                                            Text(
                                                text = "REMINDERS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Cyan400,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                            flatReminders.forEach { (reminder, dateMillis) ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            libraryViewModel.togglePinReminder(dateMillis, reminder.id)
                                                            showEventSelectionDialog = false
                                                        }
                                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Notifications,
                                                        contentDescription = null,
                                                        tint = Cyan400,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = reminder.text,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = getReminderTimeRemaining(dateMillis, reminder.time),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    if (reminder.isPinned) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Pinned",
                                                            tint = Cyan400,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }

                                        // 2. Day Counters
                                        if (dayCounters.isNotEmpty()) {
                                            Text(
                                                text = "COUNTDOWNS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Cyan400,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                            dayCounters.forEach { counter ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            libraryViewModel.togglePinDayCounter(counter.id)
                                                            showEventSelectionDialog = false
                                                        }
                                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CalendarToday,
                                                        contentDescription = null,
                                                        tint = Cyan400,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = counter.title,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = "In ${counter.daysLeft} days",
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    if (counter.isPinned) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Pinned",
                                                            tint = Cyan400,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }

                                        // 3. Timers
                                        if (timers.isNotEmpty()) {
                                            Text(
                                                text = "TIMERS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Cyan400,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                            timers.forEach { timer ->
                                                val hrs = timer.timeRemaining / 3600
                                                val mins = (timer.timeRemaining % 3600) / 60
                                                val secs = timer.timeRemaining % 60
                                                val timeStr = String.format("%02d:%02d:%02d", hrs, mins, secs)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            libraryViewModel.togglePinTimer(timer.id)
                                                            showEventSelectionDialog = false
                                                        }
                                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.HourglassEmpty,
                                                        contentDescription = null,
                                                        tint = Cyan400,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = timer.title.ifBlank { "Timer" },
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = timeStr,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    if (timer.isPinned) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Pinned",
                                                            tint = Cyan400,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 4. Stopwatches
                                        if (stopwatches.isNotEmpty()) {
                                            if (timers.isNotEmpty()) Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "STOPWATCHES",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Cyan400,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                            stopwatches.forEach { stopwatch ->
                                                val elapsed = stopwatch.elapsedMillis
                                                val hrs = (elapsed / 3600000)
                                                val minutes = ((elapsed % 3600000) / 60000)
                                                val seconds = ((elapsed % 60000) / 1000)
                                                val timeStr = String.format("%02d:%02d:%02d", hrs, minutes, seconds)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            libraryViewModel.togglePinStopwatch(stopwatch.id)
                                                            showEventSelectionDialog = false
                                                        }
                                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Timer,
                                                        contentDescription = null,
                                                        tint = Cyan400,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = stopwatch.title.ifBlank { "Stopwatch" },
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = timeStr,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    if (stopwatch.isPinned) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Pinned",
                                                            tint = Cyan400,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "No events currently configured.\nGo to the Calendar tab to configure reminders, timers, or stopwatches.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(vertical = 16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            showEventSelectionDialog = false
                                            onTabSelected("calendar")
                                        }
                                    ) {
                                        Text("GO TO CALENDAR", color = Cyan400, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(
                                        onClick = { showEventSelectionDialog = false }
                                    ) {
                                        Text("CLOSE", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pinned Notes Section
            val notesState by libraryViewModel.notes.collectAsState()
            val voiceNotesState by libraryViewModel.voiceNotes.collectAsState()
            val pinnedNote1Id by libraryViewModel.pinnedNote1Id.collectAsState()
            val pinnedNote2Id by libraryViewModel.pinnedNote2Id.collectAsState()

            val textNote1 = notesState.find { it.id == pinnedNote1Id }
            val voiceNote1 = voiceNotesState.find { it.id == pinnedNote1Id }
            val hasNote1 = textNote1 != null || voiceNote1 != null
            
            val textNote2 = notesState.find { it.id == pinnedNote2Id }
            val voiceNote2 = voiceNotesState.find { it.id == pinnedNote2Id }
            val hasNote2 = textNote2 != null || voiceNote2 != null

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SLOT 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(155.dp)
                ) {
                    if (hasNote1) {
                        FrostedCard(
                            modifier = Modifier.fillMaxSize(),
                            onClick = { showNoteSelectionDialogForSlot = 1 }
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (voiceNote1 != null) Icons.Default.Mic else Icons.Default.EditNote,
                                        contentDescription = "Type",
                                        tint = Cyan400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = textNote1?.title?.ifBlank { "Untitled" } ?: voiceNote1?.title ?: "Untitled",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.ui.graphics.Color.White,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                if (voiceNote1 != null) {
                                    PinnedVoiceNotePlayer(note = voiceNote1, driveViewModel = driveViewModel, libraryViewModel = libraryViewModel, onClick = { showAudioPlayDialogForNote = voiceNote1 })
                                } else {
                                    Text(
                                        text = textNote1?.content?.ifBlank { "No content" } ?: "",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                                        maxLines = 4,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // Slot 1 is Empty
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .glassMorphic(RoundedCornerShape(24.dp))
                                .clickable { showNoteSelectionDialogForSlot = 1 },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Pin Note",
                                    tint = Cyan400.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap to Pin Notes",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // SLOT 2
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(155.dp)
                ) {
                    if (hasNote2) {
                        FrostedCard(
                            modifier = Modifier.fillMaxSize(),
                            onClick = { showNoteSelectionDialogForSlot = 2 }
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (voiceNote2 != null) Icons.Default.Mic else Icons.Default.EditNote,
                                        contentDescription = "Type",
                                        tint = Cyan400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = textNote2?.title?.ifBlank { "Untitled" } ?: voiceNote2?.title ?: "Untitled",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.ui.graphics.Color.White,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                if (voiceNote2 != null) {
                                    PinnedVoiceNotePlayer(note = voiceNote2, driveViewModel = driveViewModel, libraryViewModel = libraryViewModel, onClick = { showAudioPlayDialogForNote = voiceNote2 })
                                } else {
                                    Text(
                                        text = textNote2?.content?.ifBlank { "No content" } ?: "",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                                        maxLines = 4,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // Slot 2 is Empty
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .glassMorphic(RoundedCornerShape(24.dp))
                                .clickable { showNoteSelectionDialogForSlot = 2 },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Pin Note",
                                    tint = Cyan400.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap to Pin Notes",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Audio Playback overlay
            showAudioPlayDialogForNote?.let { note ->
                AudioPlayDialog(
                    note = note,
                    driveViewModel = driveViewModel,
                    libraryViewModel = libraryViewModel,
                    onDismiss = { showAudioPlayDialogForNote = null }
                )
            }

            // Note Selection Dialog overlay
            showNoteSelectionDialogForSlot?.let { slot ->
                val context = androidx.compose.ui.platform.LocalContext.current
                val otherSlotPinnedId = if (slot == 1) pinnedNote2Id else pinnedNote1Id
                
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showNoteSelectionDialogForSlot = null },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(modifier = Modifier.padding(24.dp)) {
                        GlassBackground(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(32.dp))
                                .border(
                                    1.dp,
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.4f),
                                            Color.White.copy(alpha = 0.1f)
                                        )
                                    ),
                                    RoundedCornerShape(32.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "Pin Note to Slot $slot",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                val scrollState = androidx.compose.foundation.rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .verticalScroll(scrollState)
                                ) {
                                    val allPinItems = notesState.map { Pair(it.id, it) } + voiceNotesState.map { Pair(it.id, it) }
                                    if (allPinItems.isEmpty()) {
                                        Text(
                                            text = "No notes found.\nGo to the Notes tab to create some notes.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(vertical = 16.dp)
                                        )
                                    } else {
                                        allPinItems.forEach { (id, item) ->
                                            val isPinnedInOther = id == otherSlotPinnedId
                                            val isPinnedInThis = id == (if (slot == 1) pinnedNote1Id else pinnedNote2Id)
                                            val title = if (item is com.example.ui.screens.NoteItem) item.title else (item as com.example.ui.screens.VoiceNote).title
                                            val contentPreview = if (item is com.example.ui.screens.NoteItem) item.content else "Voice Note"
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .graphicsLayer {
                                                        alpha = if (isPinnedInOther) 0.4f else 1.0f
                                                    }
                                                    .clickable {
                                                        if (isPinnedInOther) {
                                                            android.widget.Toast.makeText(context, "Already pinned!", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            if (isPinnedInThis) {
                                                                libraryViewModel.pinNoteToSlot(slot, null)
                                                            } else {
                                                                libraryViewModel.pinNoteToSlot(slot, id)
                                                            }
                                                            showNoteSelectionDialogForSlot = null
                                                        }
                                                    }
                                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (item is com.example.ui.screens.VoiceNote) Icons.Default.Mic else Icons.Default.EditNote,
                                                    contentDescription = null,
                                                    tint = Cyan400,
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = title.ifBlank { "Untitled Note" },
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    if (contentPreview.isNotBlank()) {
                                                        Text(
                                                            text = contentPreview,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            fontSize = 12.sp,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                
                                                if (isPinnedInThis) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Pinned",
                                                        tint = Cyan400,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                } else if (isPinnedInOther) {
                                                    Icon(
                                                        imageVector = Icons.Default.PushPin,
                                                        contentDescription = "Pinned in other container",
                                                        tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            showNoteSelectionDialogForSlot = null
                                            onTabSelected("notes")
                                        }
                                    ) {
                                        Text("GO TO NOTES", color = Cyan400, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(
                                        onClick = { showNoteSelectionDialogForSlot = null }
                                    ) {
                                        Text("CLOSE", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recent File List
            Text(text = "RECENT FILES", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (recentFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Gray.copy(alpha = 0.05f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No files yet. Tap the '+' icon to add some!", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    recentFiles.forEach { file ->
                        com.example.ui.screens.LibraryListItem(
                            item = file,
                            uploadProgress = uploadingFiles[file.id],
                            downloadProgress = downloadingFiles[file.id],
                            isSelected = false,
                            onDelete = { 
                                libraryViewModel.deleteFile(file.id) 
                                if (driveViewModel.isConnected.value) {
                                    driveViewModel.deleteFileFromDrive(context, file.title, file.driveFileId)
                                }
                            },
                            onRename = {  }, // omitted for recent files without options
                            onMove = {  }, // omitted
                            onShare = { libraryViewModel.shareFile(context, file, driveViewModel) },
                            onSaveToDevice = { libraryViewModel.saveToDevice(context, file, driveViewModel) },
                            onCancelUpload = { driveViewModel.cancelUpload(file.id) },
                            onCancelDownload = { driveViewModel.cancelDownload(file.id) },
                            onClick = { 
                                if (downloadingFiles.containsKey(file.id)) {
                                    /* do nothing while downloading */
                                } else if (file.uri == null && file.driveFileId != null) {
                                    if (!isOnline.value) {
                                        android.widget.Toast.makeText(context, "Please connect to internet to open this file", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        driveViewModel.downloadFileFromDrive(context, libraryViewModel, file.driveFileId, file.title, file.id) { }
                                    }
                                } else {
                                    onOpenItem(file)
                                }
                            },
                            showOptions = false
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 96.dp))
            }
        }

        }
        }
        }

        val triggerScan by libraryViewModel.triggerScan.collectAsState()
        val triggerUpload by libraryViewModel.triggerUpload.collectAsState()
        val showCreateFolderDialogGlobal by libraryViewModel.showCreateFolderDialog.collectAsState()
        
        LaunchedEffect(showCreateFolderDialogGlobal) {
            if (showCreateFolderDialogGlobal) {
                showCreateFolderDialog = true
                libraryViewModel.showCreateFolderDialog.value = false
            }
        }
        
        LaunchedEffect(triggerScan) {
            if (triggerScan > 0L) {
                val activity = context.findActivity() ?: return@LaunchedEffect
                scanner.getStartScanIntent(activity).addOnSuccessListener { intentSender ->
                    scannerLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                    )
                }
            }
        }
        
        LaunchedEffect(triggerUpload) {
            if (triggerUpload > 0L) {
                launcher.launch(arrayOf("*/*")) 
            }
        }

        if (!searchActive) {
            if (isFabExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            libraryViewModel.isFabExpanded.value = false
                        }
                )
            }
            
            com.example.ui.components.ExpandableFab(
                onScanClick = {
                    libraryViewModel.isFabExpanded.value = false
                    val activity = context.findActivity() ?: return@ExpandableFab
                    scanner.getStartScanIntent(activity).addOnSuccessListener { intentSender ->
                        scannerLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                        )
                    }
                },
                onUploadClick = { 
                    libraryViewModel.isFabExpanded.value = false
                    launcher.launch(arrayOf("*/*")) 
                },
                onFolderClick = { 
                    libraryViewModel.isFabExpanded.value = false
                    showCreateFolderDialog = true 
                },
                onNoteClick = {
                    libraryViewModel.isFabExpanded.value = false
                    libraryViewModel.triggerOpenNewNoteDirectly()
                },
                onVoiceNoteClick = {
                    libraryViewModel.isFabExpanded.value = false
                    libraryViewModel.triggerVoiceRecorder.value = System.currentTimeMillis()
                },
                expanded = isFabExpanded,
                onExpandedChange = { libraryViewModel.isFabExpanded.value = it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp + innerPadding.calculateBottomPadding(), end = 24.dp)
            )
        }
        
        val triggerVoiceRecorder by libraryViewModel.triggerVoiceRecorder.collectAsState()
        var showVoiceRecorder by remember { mutableStateOf(false) }

        LaunchedEffect(triggerVoiceRecorder) {
            if (triggerVoiceRecorder > 0L) {
                showVoiceRecorder = true
                libraryViewModel.triggerVoiceRecorder.value = 0L
            }
        }
        
        if (showVoiceRecorder) {
            com.example.ui.screens.VoiceNoteRecorderScreen(
                onDismiss = { showVoiceRecorder = false },
                onSave = { title, file -> 
                    showVoiceRecorder = false
                    val voiceNotesDir = File(context.filesDir, "voice_notes")
                    if (!voiceNotesDir.exists()) voiceNotesDir.mkdirs()
                    val persistentFile = File(voiceNotesDir, file.name)
                    file.copyTo(persistentFile, overwrite = true)
                    
                    val uri = android.net.Uri.fromFile(persistentFile)
                    libraryViewModel.addVoiceNote(title, uri, driveViewModel, context)
                }
            )
        }

        if (showCreateFolderDialog) {
            var folderNameError by remember { mutableStateOf<String?>(null) }
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { 
                    showCreateFolderDialog = false
                    newFolderName = ""
                },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.padding(24.dp)) {
                    GlassBackground(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(32.dp))
                            .border(
                                1.dp,
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.4f),
                                        Color.White.copy(alpha = 0.1f)
                                    )
                                ),
                                RoundedCornerShape(32.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Create Folder",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = newFolderName,
                                onValueChange = { 
                                    newFolderName = it
                                    folderNameError = null
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                                placeholder = { Text("Folder Name") },
                                label = { Text("Folder Name") },
                                modifier = Modifier.fillMaxWidth(),
                                isError = folderNameError != null,
                                supportingText = {
                                    if (folderNameError != null) {
                                        Text(text = folderNameError!!, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { 
                                    showCreateFolderDialog = false
                                    newFolderName = ""
                                }) {
                                    Text("Cancel", color = Slate400)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = { 
                                    if (newFolderName.trim().isEmpty()) {
                                        folderNameError = "Folder name is mandatory"
                                    } else {
                                        libraryViewModel.createFolder(newFolderName, null) // Roots level folder
                                        showCreateFolderDialog = false 
                                        newFolderName = ""
                                    }
                                }) {
                                    Text("Create", color = Cyan400)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // note editing dialog is removed because it is now handled as a full screen editor page via the libraryViewModel's direct open trigger
        }
    }
}

@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .glassMorphic(RoundedCornerShape(24.dp))
            .clickable { onClick() }
    } else {
        modifier
            .glassMorphic(RoundedCornerShape(24.dp))
    }
    Box(
        modifier = cardModifier
            .padding(12.dp)
    ) {
        content()
    }
}

@Composable
fun FileItemRow(
    item: LibraryItem,
    isUploading: Boolean = false,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onSaveToDevice: () -> Unit
) {
    var expanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showDeleteDialog by remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showDeleteDialog) {
        com.example.ui.components.ConfirmationDialog(
            title = if (item.isFolder) "Delete folder" else "Delete file",
            message = if (item.isFolder) "Are you sure you want to delete this folder?" else "Are you sure you want to delete this file?",
            onConfirm = onDelete,
            onDismiss = { showDeleteDialog = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassMorphic(RoundedCornerShape(16.dp))
            .clickable { onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(item.iconBg),
            contentAlignment = Alignment.Center
        ) {
            if (isUploading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Cyan400,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(imageVector = item.icon, contentDescription = "File Type", tint = item.iconTint)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = item.subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (isUploading || item.tags.isNotEmpty()) {
                    Text(text = "•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val tagText = buildString {
                        if (isUploading) append("Uploading...")
                        else append(item.tags.joinToString(", "))
                    }
                    Text(text = tagText, fontSize = 10.sp, color = Cyan400, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                DropdownMenuItem(
                    text = { Text("Open", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { expanded = false; onOpen() },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, tint = Cyan400) }
                )
                DropdownMenuItem(
                    text = { Text("Save to device", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { expanded = false; onSaveToDevice() },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { expanded = false; showDeleteDialog = true },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

@Composable
fun BottomNavBar(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (isDark) {
        Color(0xFF0F172A).copy(alpha = 0.72f) // Slate 900 translucent
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.82f) // Pure white translucent
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .graphicsLayer {
                shadowElevation = 8f
                shape = RoundedCornerShape(32.dp)
                clip = true
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    color = bgColor,
                    shape = RoundedCornerShape(32.dp)
                )
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isDashboard = currentTab == "dashboard"
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isDashboard) Cyan500.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onTabSelected("dashboard") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Dashboard",
                    tint = if (isDashboard) Cyan400 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            val isLibrary = currentTab == "library"
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isLibrary) Cyan500.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onTabSelected("library") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Library",
                    tint = if (isLibrary) Cyan400 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            val isNotes = currentTab == "notes"
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isNotes) Cyan500.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onTabSelected("notes") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Notes",
                    tint = if (isNotes) Cyan400 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            val isCalendar = currentTab == "calendar"
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isCalendar) Cyan500.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onTabSelected("calendar") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Calendar",
                    tint = if (isCalendar) Cyan400 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

fun getReminderTimeRemaining(dateMillis: Long, timeStr: String): String {
    val zoneId = try {
        java.time.ZoneId.systemDefault()
    } catch (e: Exception) {
        java.time.ZoneId.systemDefault()
    }

    val currentDateTime = java.time.ZonedDateTime.now(zoneId)
    val targetDateTime = try {
        val localDate = java.time.Instant.ofEpochMilli(dateMillis)
            .atZone(java.time.ZoneOffset.UTC)
            .toLocalDate()
        
        val parts = timeStr.trim().split(" ")
        if (parts.size == 2) {
            val timeParts = parts[0].split(":")
            if (timeParts.size == 2) {
                var hour = timeParts[0].toIntOrNull() ?: 0
                val minute = timeParts[1].toIntOrNull() ?: 0
                if (parts[1].uppercase(java.util.Locale.US) == "PM" && hour < 12) hour += 12
                if (parts[1].uppercase(java.util.Locale.US) == "AM" && hour == 12) hour = 0
                val localTime = java.time.LocalTime.of(hour, minute)
                localDate.atTime(localTime).atZone(zoneId)
            } else null
        } else null
    } catch (e: Exception) {
        null
    } ?: return "At $timeStr"

    if (currentDateTime.isAfter(targetDateTime)) {
        return "Reminder elapsed"
    }

    val duration = java.time.Duration.between(currentDateTime, targetDateTime)
    val secsTotal = duration.seconds

    if (secsTotal <= 0) {
        return "Reminder reached"
    }

    val period = java.time.Period.between(currentDateTime.toLocalDate(), targetDateTime.toLocalDate())
    val months = period.months + period.years * 12
    val daysTotal = java.time.temporal.ChronoUnit.DAYS.between(currentDateTime.toLocalDate(), targetDateTime.toLocalDate())

    return when {
        months > 0 -> {
            val days = period.days
            if (days > 0) {
                "In $months ${if (months == 1) "month" else "months"}, $days ${if (days == 1) "day" else "days"} ($timeStr)"
            } else {
                "In $months ${if (months == 1) "month" else "months"} ($timeStr)"
            }
        }
        daysTotal > 1 -> {
            "In $daysTotal days ($timeStr)"
        }
        secsTotal >= 3600 -> {
            val hours = secsTotal / 3600
            val mins = (secsTotal % 3600) / 60
            if (mins > 0L) {
                "In $hours ${if (hours == 1L) "hour" else "hours"}, $mins ${if (mins == 1L) "min" else "mins"} ($timeStr)"
            } else {
                "In $hours ${if (hours == 1L) "hour" else "hours"} ($timeStr)"
            }
        }
        secsTotal >= 60 -> {
            val mins = secsTotal / 60
            val secs = secsTotal % 60
            if (secs > 0L) {
                "In $mins ${if (mins == 1L) "min" else "mins"}, $secs sec"
            } else {
                "In $mins ${if (mins == 1L) "minute" else "minutes"}"
            }
        }
        else -> {
            "In $secsTotal sec"
        }
    }
}

@Composable
fun NoteItemRow(
    item: NoteItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showDeleteDialog by remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showDeleteDialog) {
        com.example.ui.components.ConfirmationDialog(
            title = "Delete note",
            message = "Are you sure you want to delete this note?",
            onConfirm = onDelete,
            onDismiss = { showDeleteDialog = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassMorphic(RoundedCornerShape(16.dp))
            .clickable { onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE3F2FD).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.Description, contentDescription = "Note Type", tint = Color(0xFF64B5F6))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                text = if (item.content.trim().isEmpty()) "Empty note" else item.content, 
                fontSize = 10.sp, 
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), 
                maxLines = 1, 
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                DropdownMenuItem(
                    text = { Text("Open & Edit", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { expanded = false; onOpen() },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, tint = Cyan400) }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { expanded = false; showDeleteDialog = true },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

@Composable
fun PinnedVoiceNotePlayer(
    note: VoiceNote,
    driveViewModel: DriveViewModel,
    libraryViewModel: LibraryViewModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val downloadingFiles by driveViewModel.downloadingFiles.collectAsState()
    val isDownloading = downloadingFiles[note.id] != null

    val fileExists = remember(note.uriString) {
        try {
            val uriParsed = android.net.Uri.parse(note.uriString)
            if (uriParsed.scheme == "content") {
                context.contentResolver.openAssetFileDescriptor(uriParsed, "r")?.use { 
                    it.length > 0 
                } ?: false
            } else {
                val path = uriParsed.path
                if (path != null) {
                    val file = java.io.File(path)
                    file.exists() && file.length() > 0
                } else false
            }
        } catch (e: Exception) {
            false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Cyan500.copy(alpha = 0.2f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Cyan400,
                    strokeWidth = 2.dp
                )
            } else if (!fileExists && note.driveFileId != null) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = Cyan400,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Cyan400,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        val cachedDurationText = remember(note.durationMillis) {
            if (note.durationMillis > 0L) {
                val totalSecs = note.durationMillis / 1000
                val m = totalSecs / 60
                val s = totalSecs % 60
                String.format("%02d:%02d", m, s)
            } else {
                "--:--"
            }
        }
        var totalDurationText by remember { mutableStateOf(cachedDurationText) }
        LaunchedEffect(note.durationMillis) {
            if (note.durationMillis > 0L) {
                val totalSecs = note.durationMillis / 1000
                val m = totalSecs / 60
                val s = totalSecs % 60
                totalDurationText = String.format("%02d:%02d", m, s)
            }
        }

        LaunchedEffect(fileExists, note.uriString) {
            if (fileExists) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    var retriever: android.media.MediaMetadataRetriever? = null
                    try {
                        retriever = android.media.MediaMetadataRetriever()
                        val uriParsed = android.net.Uri.parse(note.uriString)
                        val path = uriParsed.path
                        if (uriParsed.scheme != "content" && path != null) {
                            retriever.setDataSource(path)
                        } else {
                            retriever.setDataSource(context, uriParsed)
                        }
                        val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val timeInMillis = time?.toLongOrNull() ?: 0L
                        if (timeInMillis > 0L) {
                            val totalSecs = timeInMillis / 1000
                            val m = totalSecs / 60
                            val s = totalSecs % 60
                            totalDurationText = String.format("%02d:%02d", m, s)
                        }
                    } catch(e: Exception) {
                        android.util.Log.e("MetadataRetriever", "Failed to retrieve duration", e)
                    } finally {
                        try {
                            retriever?.release()
                        } catch(e: Exception) {}
                    }
                }
            }
        }

        Text(
            text = totalDurationText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AudioPlayDialog(
    note: VoiceNote,
    driveViewModel: DriveViewModel,
    libraryViewModel: LibraryViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val voiceNotes by libraryViewModel.voiceNotes.collectAsState()
    val currentNote = voiceNotes.find { it.id == note.id } ?: note
    
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableIntStateOf(0) }
    var currentPosition by remember { mutableIntStateOf(0) }
    
    val downloadingFiles by driveViewModel.downloadingFiles.collectAsState()
    val isDownloading = downloadingFiles[currentNote.id] != null

    val uri = android.net.Uri.parse(currentNote.uriString)
    
    var fileExists by remember(currentNote.uriString) {
        val uriParsed = android.net.Uri.parse(currentNote.uriString)
        val exists = try {
            if (uriParsed.scheme == "content") {
                context.contentResolver.openAssetFileDescriptor(uriParsed, "r")?.use { 
                    it.length > 0 
                } ?: false
            } else {
                val path = uriParsed.path
                if (path != null) {
                    val file = java.io.File(path)
                    file.exists() && file.length() > 0
                } else false
            }
        } catch (e: Exception) {
            false
        }
        mutableStateOf(exists)
    }

    val playAudio = {
        if (fileExists) {
            try {
                if (mediaPlayer == null) {
                    mediaPlayer = android.media.MediaPlayer().apply {
                        setDataSource(context, uri)
                        prepare()
                        duration = this.duration
                        setOnCompletionListener {
                            isPlaying = false
                            progress = 0f
                            currentPosition = 0
                        }
                    }
                }
                mediaPlayer?.start()
                isPlaying = true
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error playing audio", android.widget.Toast.LENGTH_SHORT).show()
                isPlaying = false
            }
        }
    }

    val pauseAudio = {
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(currentNote.uriString) {
        mediaPlayer?.release()
        mediaPlayer = null
        currentPosition = 0
        progress = 0f
        isPlaying = false
    }

    LaunchedEffect(isDownloading) {
        if (!isDownloading) {
            val uriParsed = android.net.Uri.parse(currentNote.uriString)
            fileExists = try {
                if (uriParsed.scheme == "content") {
                    context.contentResolver.openAssetFileDescriptor(uriParsed, "r")?.use { 
                        it.length > 0 
                    } ?: false
                } else {
                    val path = uriParsed.path
                    if (path != null) {
                        val file = java.io.File(path)
                        file.exists() && file.length() > 0
                    } else false
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    LaunchedEffect(fileExists) {
        if (fileExists) {
            playAudio()
        } else if (currentNote.driveFileId != null && !isDownloading) {
            driveViewModel.downloadFileFromDrive(context, libraryViewModel, currentNote.driveFileId, currentNote.title, currentNote.id) { }
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && mediaPlayer != null) {
                try {
                    if (mediaPlayer?.isPlaying == true) {
                        currentPosition = mediaPlayer?.currentPosition ?: 0
                        if (duration > 0) {
                            progress = currentPosition.toFloat() / duration.toFloat()
                        }
                    }
                } catch (e: Exception) {}
                kotlinx.coroutines.delay(50)
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            // Apply native back press handling
            BackHandler(enabled = true) {
                onDismiss()
            }
            
            GlassBackground(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .border(
                        1.dp,
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        ),
                        RoundedCornerShape(32.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Cyan400
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = currentNote.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Note",
                            tint = Cyan400,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (!fileExists && currentNote.driveFileId != null && !isDownloading) {
                                    driveViewModel.downloadFileFromDrive(context, libraryViewModel, currentNote.driveFileId, currentNote.title, currentNote.id) { }
                                } else if (fileExists) {
                                    if (isPlaying) {
                                        pauseAudio()
                                    } else {
                                        playAudio()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Cyan500.copy(alpha = 0.2f))
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Cyan400,
                                    strokeWidth = 2.dp
                                )
                            } else if (!fileExists && currentNote.driveFileId != null) {
                                Icon(Icons.Default.Download, contentDescription = "Download", tint = Cyan400)
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Cyan400,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPlaying) "Playing now" else if (isDownloading) "Downloading..." else "Ready to play",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Cyan400
                                )
                            )
                            val formattedDate = remember(currentNote.createdAt) {
                                val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
                                sdf.format(java.util.Date(currentNote.createdAt))
                            }
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = String.format("%02d:%02d", (currentPosition / 1000) / 60, (currentPosition / 1000) % 60),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.requiredWidth(42.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        var isDraggingSlider by remember { mutableStateOf(false) }
                        var wasPlayingBeforeDrag by remember { mutableStateOf(false) }
                        Slider(
                            value = progress,
                            onValueChange = { newProgress ->
                                if (!isDraggingSlider) {
                                    isDraggingSlider = true
                                    wasPlayingBeforeDrag = isPlaying
                                    if (isPlaying) {
                                        mediaPlayer?.pause()
                                        isPlaying = false
                                    }
                                }
                                progress = newProgress
                                if (duration > 0) {
                                    val newPos = (duration * progress).toInt()
                                    currentPosition = newPos
                                    mediaPlayer?.seekTo(newPos)
                                }
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                if (wasPlayingBeforeDrag) {
                                    mediaPlayer?.start()
                                    isPlaying = true
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Cyan400,
                                activeTrackColor = Cyan400,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        
                        val displayDuration = if (duration > 0) duration else currentNote.durationMillis.toInt()
                        Text(
                            text = String.format("%02d:%02d", (displayDuration / 1000) / 60, (displayDuration / 1000) % 60),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.requiredWidth(42.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

