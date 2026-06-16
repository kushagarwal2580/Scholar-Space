package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import com.example.ui.components.GlassBackground
import com.example.ui.components.glassMorphic
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloat

@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel,
    driveViewModel: com.example.ui.screens.DriveViewModel,
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    
    var selectedFiles by androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver<Set<String>, List<String>>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf<Set<String>>(emptySet()) }
    
    var isSelectionMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val isFabExpanded by libraryViewModel.isFabExpanded.collectAsState()
    
    val currentFiles by libraryViewModel.currentFiles.collectAsState()
    val currentFolderId by libraryViewModel.currentFolderId.collectAsState()
    val folderState by libraryViewModel.currentFolderState.collectAsState()
    val currentTab by libraryViewModel.currentTab.collectAsState()
    
    var lastAnimatedFolderId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(libraryViewModel.currentFolderId.value) }

    var showDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogContent by remember { mutableStateOf("") }
    
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    
    var isRefreshing by remember { mutableStateOf(false) }
    var showSyncCompleteMessage by remember { mutableStateOf(false) }
    var syncMessageText by remember { mutableStateOf("Sync complete") }
    var syncMessageColor by remember { mutableStateOf(Cyan400) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
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
    
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val rotationState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ), label = "rotation"
    )

    val coroutineScope = rememberCoroutineScope()
    
    val uploadingFiles by driveViewModel.uploadingFiles.collectAsState()
    val downloadingFiles by driveViewModel.downloadingFiles.collectAsState()
    
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

    androidx.activity.compose.BackHandler(enabled = isSelectionMode || currentFolderId != null) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedFiles = emptySet()
        } else {
            libraryViewModel.navigateUp()
        }
    }

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

    var showRenameDialog by remember { mutableStateOf(false) }
    var fileToRename by remember { mutableStateOf<LibraryItem?>(null) }
    var newName by remember { mutableStateOf("") }
    
    var showMoveDialog by remember { mutableStateOf(false) }
    var fileToMove by remember { mutableStateOf<LibraryItem?>(null) }
    val resetTrigger by libraryViewModel.resetLibraryTrigger.collectAsState()
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0L) {
            searchQuery = ""
            showDialog = false
            showCreateFolderDialog = false
            showRenameDialog = false
            showMoveDialog = false
        }
    }


    val filteredFiles = folderState.files.filter {
        it.title.contains(searchQuery, ignoreCase = true) || it.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    GlassBackground(
        modifier = Modifier.fillMaxSize(),
        drawBackgroundAndCircles = false
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { focusManager.clearFocus() }
        ) {
            Box(
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
        // Main Library UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            var downloadingFileId by remember { mutableStateOf<String?>(null) }
            
            val scrollState = androidx.compose.foundation.rememberScrollState()
            
            LaunchedEffect(scrollState.isScrollInProgress) {
                if (scrollState.isScrollInProgress) {
                    focusManager.clearFocus()
                }
            }
            
            val minHt = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp + 1.dp
            
            val stateFilteredFiles = filteredFiles.sortedByDescending { it.isFolder }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { focusManager.clearFocus() },
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = folderState,
                    contentKey = { it.currentFolderId ?: "root" },
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
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = androidx.compose.ui.Alignment.TopCenter,
                    label = "folder_contents_transition"
                ) { targetFolderState ->
                    val targetFilteredFiles = targetFolderState.files.filter {
                        it.title.contains(searchQuery, ignoreCase = true) || it.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) }
                    }.sortedByDescending { it.isFolder }

                    val innerScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(innerScrollState).heightIn(min = minHt).padding(bottom = innerPadding.calculateBottomPadding() + 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                            LibraryHeader(
                                isSelectionMode = isSelectionMode,
                                selectedFiles = selectedFiles,
                                filteredFiles = stateFilteredFiles,
                                isRefreshing = isRefreshing,
                                showSyncCompleteMessage = showSyncCompleteMessage,
                                syncMessageText = syncMessageText,
                                syncMessageColor = syncMessageColor,
                                currentFolderId = currentFolderId,
                                libraryViewModel = libraryViewModel,
                                driveViewModel = driveViewModel,
                                rotation = { rotationState.value },
                                coroutineScope = coroutineScope,
                                onSelectionChange = { isSelectionMode = it; if (!it) selectedFiles = emptySet() },
                                onSelectedFilesChange = { selectedFiles = it },
                                onRefreshingChange = { 
                                    isRefreshing = it
                                    if (it) {
                                        showSyncCompleteMessage = false
                                        coroutineScope.launch {
                                            if (driveViewModel.isConnected.value && isOnline.value) {
                                                driveViewModel.syncDriveData(context, libraryViewModel) {
                                                    isRefreshing = false
                                                    syncMessageText = "Sync complete"
                                                    syncMessageColor = Cyan400
                                                    showSyncCompleteMessage = true
                                                    coroutineScope.launch {
                                                        kotlinx.coroutines.delay(3000)
                                                        showSyncCompleteMessage = false
                                                    }
                                                }
                                            } else {
                                                kotlinx.coroutines.delay(500)
                                                isRefreshing = false
                                                syncMessageText = "Please connect to internet"
                                                syncMessageColor = androidx.compose.ui.graphics.Color(0xFFEF4444)
                                                showSyncCompleteMessage = true
                                                kotlinx.coroutines.delay(3000)
                                                showSyncCompleteMessage = false
                                            }
                                        }
                                    }
                                },
                                onSearchQueryChange = { searchQuery = it },
                                searchQuery = searchQuery,
                                onMoveClick = { showMoveDialog = true }
                            )
                        
                        if (targetFilteredFiles.isEmpty()) {
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 360.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = Cyan400.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) "No matching files" else "No files yet",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) "Try editing your query" else "Tap the '+' icon to add some!",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 32.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                            
                        } else {
                            targetFilteredFiles.forEach { file ->
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                ) {
                                    LibraryListItem(
                                item = file,
                                    uploadProgress = uploadingFiles[file.id],
                                    downloadProgress = downloadingFiles[file.id],
                                    isSelected = selectedFiles.contains(file.id),
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedFiles = setOf(file.id)
                                        }
                                    },
                                    onDelete = { 
                                        libraryViewModel.deleteFile(file.id) 
                                        if (driveViewModel.isConnected.value) {
                                            driveViewModel.deleteFileFromDrive(context, file.title, file.driveFileId)
                                        }
                                    },
                                    onRename = { 
                                        fileToRename = file
                                        val isFolder = file.isFolder
                                        val lastDotIdx = if (!isFolder) file.title.lastIndexOf('.') else -1
                                        newName = if (lastDotIdx > 0) file.title.substring(0, lastDotIdx) else file.title
                                        showRenameDialog = true 
                                    },
                                    onMove = { fileToMove = file; showMoveDialog = true },
                                    onShare = { libraryViewModel.shareFile(context, file, driveViewModel) },
                                    onSaveToDevice = { libraryViewModel.saveToDevice(context, file, driveViewModel) },
                                    onCancelUpload = { driveViewModel.cancelUpload(file.id) },
                                    onCancelDownload = { driveViewModel.cancelDownload(file.id) },
                                    folderChildren = if (file.isFolder) libraryViewModel.getItemsInFolder(file.id) else null,
                                    showOptions = !isSelectionMode,
                                    onClick = { 
                                        if (isSelectionMode) {
                                            if (selectedFiles.contains(file.id)) {
                                                selectedFiles = selectedFiles - file.id
                                            } else {
                                                selectedFiles = selectedFiles + file.id
                                            }
                                        } else {
                                            if (file.isFolder) {
                                                libraryViewModel.setCurrentFolderId(file.id)
                                            } else {
                                                if (downloadingFiles[file.id] == null) {
                                                    if (file.uri == null && file.driveFileId != null) {
                                                        if (!isOnline.value) {
                                                            syncMessageText = "Please connect to internet"
                                                            syncMessageColor = androidx.compose.ui.graphics.Color(0xFFEF4444)
                                                            showSyncCompleteMessage = true
                                                            coroutineScope.launch { kotlinx.coroutines.delay(3000); showSyncCompleteMessage = false }
                                                        } else {
                                                            driveViewModel.downloadFileFromDrive(context, libraryViewModel, file.driveFileId, file.title, file.id) { }
                                                        }
                                                    } else {
                                                        libraryViewModel.openFile(context, file) {}
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                                }
                            }
                        }
                    }

            }
        }
        }

        val triggerScan by libraryViewModel.triggerScan.collectAsState()
        val triggerUpload by libraryViewModel.triggerUpload.collectAsState()
        val triggerVoiceRecorder by libraryViewModel.triggerVoiceRecorder.collectAsState()
        val showCreateFolderDialogGlobal by libraryViewModel.showCreateFolderDialog.collectAsState()
        var showVoiceRecorder by remember { mutableStateOf(false) }
        
        LaunchedEffect(showCreateFolderDialogGlobal) {
            if (showCreateFolderDialogGlobal) {
                showCreateFolderDialog = true
                libraryViewModel.showCreateFolderDialog.value = false
            }
        }
        
        LaunchedEffect(triggerVoiceRecorder) {
            if (triggerVoiceRecorder > 0L) {
                showVoiceRecorder = true
            }
        }
        
        LaunchedEffect(triggerScan) {
            if (triggerScan > 0L) {
                val activity = context.findActivity() ?: return@LaunchedEffect
                val scannerOptions = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setResultFormats(
                        com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                        com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                    )
                    .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                val scanner = com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(scannerOptions)
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

        if (searchQuery.isEmpty()) {
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
                    val scannerOptions = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(true)
                        .setResultFormats(
                            com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                            com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                        )
                        .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build()
                    val scanner = com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(scannerOptions)
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
                expanded = isFabExpanded,
                onExpandedChange = { libraryViewModel.isFabExpanded.value = it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp + innerPadding.calculateBottomPadding(), end = 24.dp)
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
                            OutlinedTextField(
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
                                        val newId = libraryViewModel.createFolder(newFolderName, currentFolderId)
                                        if (driveViewModel.isConnected.value) {
                                            driveViewModel.createFolderInDrive(context, newFolderName, currentFolderId, newId, libraryViewModel)
                                        }
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

        if (showRenameDialog && fileToRename != null) {
            val file = fileToRename!!
            val isFolder = file.isFolder
            val lastDotIdx = if (!isFolder) file.title.lastIndexOf('.') else -1
            val extension = if (lastDotIdx > 0) file.title.substring(lastDotIdx) else ""
            var renameError by remember { mutableStateOf<String?>(null) }
            
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showRenameDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.padding(24.dp)) {
                    GlassBackground(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(32.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = if (isFolder) "Rename Folder" else "Rename File",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { 
                                    newName = it 
                                    renameError = null
                                },
                                placeholder = { Text("Name") },
                                label = { Text("Name") },
                                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                                suffix = if (extension.isNotEmpty()) {
                                    {
                                        Text(
                                            text = extension,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else null,
                                modifier = Modifier.fillMaxWidth(),
                                isError = renameError != null,
                                supportingText = {
                                    if (renameError != null) {
                                        Text(text = renameError!!, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showRenameDialog = false }) {
                                    Text("Cancel", color = Slate400)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = { 
                                    if (newName.trim().isEmpty()) {
                                        renameError = "File name is mandatory"
                                    } else {
                                        val finalName = if (extension.isNotEmpty() && !newName.endsWith(extension)) {
                                            newName.trim() + extension
                                        } else {
                                            newName.trim()
                                        }
                                        libraryViewModel.renameFile(file.id, finalName)
                                        showRenameDialog = false 
                                    }
                                }) {
                                    Text("Save", color = Cyan400)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showMoveDialog && (fileToMove != null || selectedFiles.isNotEmpty())) {
            val itemsToMove = if (selectedFiles.isNotEmpty()) selectedFiles else setOf(fileToMove!!.id)
            MoveFileDialog(
                initialFolderId = null,
                itemsToMove = itemsToMove,
                libraryViewModel = libraryViewModel,
                onDismiss = { 
                    showMoveDialog = false
                    fileToMove = null
                },
                onMoveConfirm = { destFolderId ->
                    val oldParentsMap = itemsToMove.associateWith { id -> 
                        libraryViewModel.getFiles().find { it.id == id }?.parentId 
                    }
                    libraryViewModel.moveFilesToFolder(itemsToMove, destFolderId)
                    if (driveViewModel.isConnected.value) {
                        itemsToMove.forEach { fileId ->
                            val oldLocalParentId = oldParentsMap[fileId]
                            driveViewModel.moveFileInDrive(context, fileId, destFolderId, oldLocalParentId, libraryViewModel)
                        }
                    }
                    showMoveDialog = false
                    fileToMove = null
                    selectedFiles = emptySet()
                    isSelectionMode = false
                }
            )
        }

        if (showDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(dialogTitle) },
                text = { 
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.verticalScroll(scrollState).padding(4.dp)) {
                        Text(dialogContent) 
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        // Viewer dialog removed
        }
    }
}

// LibraryItem moved to LibraryViewModel.kt

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryListItem(
    item: LibraryItem,
    uploadProgress: Float? = null,
    downloadProgress: Float? = null,
    isSelected: Boolean = false,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onSaveToDevice: () -> Unit,
    onCancelUpload: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    folderChildren: List<LibraryItem>? = null,
    showOptions: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        com.example.ui.components.ConfirmationDialog(
            title = if (item.isFolder) "Delete folder" else "Delete file",
            message = if (item.isFolder) "Are you sure you want to delete this folder?" else "Are you sure you want to delete this file?",
            onConfirm = onDelete,
            onDismiss = { showDeleteDialog = false }
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val itemExists by androidx.compose.runtime.produceState(initialValue = false, item.uri) {
        val uri = item.uri
        if (uri != null && !item.isFolder) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    if (uri.scheme == "content") {
                        context.contentResolver.openInputStream(uri)?.use { true } ?: false
                    } else if (uri.scheme == "file") {
                        val path = uri.path
                        if (path != null) java.io.File(path).exists() else false
                    } else { false }
                } catch (e: Exception) { false }
            }
        } else {
            value = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassMorphic(RoundedCornerShape(16.dp))
            .let { if (isSelected) it.border(1.dp, Cyan400, RoundedCornerShape(16.dp)).background(Cyan500.copy(alpha = 0.1f), RoundedCornerShape(16.dp)) else it }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (item.isFolder) Color(0xFF6366F1).copy(alpha = 0.2f) else item.iconBg),
            contentAlignment = Alignment.Center
        ) {
            if (uploadProgress != null || downloadProgress != null) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Cyan400,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = item.icon, 
                    contentDescription = "File Type", 
                    tint = if (item.isFolder) Color(0xFF6366F1) else item.iconTint
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val dynamicSubtitle = if (item.isFolder) {
                    if (folderChildren == null || folderChildren.isEmpty()) {
                        "0 items"
                    } else {
                        val numFiles = folderChildren.count { !it.isFolder }
                        val numFolders = folderChildren.count { it.isFolder }
                        buildString {
                            if (numFiles > 0) append("$numFiles file${if (numFiles > 1) "s" else ""}")
                            if (numFiles > 0 && numFolders > 0) append(" and ")
                            if (numFolders > 0) append("$numFolders folder${if (numFolders > 1) "s" else ""}")
                        }
                    }
                } else {
                    val loc = if (item.uri != null && itemExists) "Saved on device" else if (item.driveFileId != null) "Drive" else "Local"
                    val fmt = item.title.substringAfterLast('.', "File").uppercase()
                    val sz = item.fileSize?.let { bytes ->
                        if (bytes < 1024) "$bytes B" else if (bytes < 1024 * 1024) "${bytes / 1024} KB" else String.format(java.util.Locale.US, "%.1f MB", bytes.toFloat() / (1024 * 1024))
                    } ?: "Unknown size"
                    listOf(loc, fmt, sz).joinToString(", ")
                }
                Text(text = dynamicSubtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.tags.isNotEmpty() || uploadProgress != null || downloadProgress != null) {
                    Text(text = "•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val tagText = buildString {
                        if (downloadProgress != null) append("Downloading...")
                        else if (uploadProgress != null) append("Uploading...")
                        else append(item.tags.joinToString(", "))
                    }
                    Text(text = tagText, fontSize = 10.sp, color = Cyan400, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        
        if (showOptions) {
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
                        text = { Text("Share", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = { expanded = false; onShare() },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                    DropdownMenuItem(
                        text = { Text("Save to device", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = { expanded = false; onSaveToDevice() },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = { expanded = false; onRename() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                    DropdownMenuItem(
                        text = { Text("Move", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = { expanded = false; onMove() },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
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
}

private data class PathSegment(val id: String?, val title: String)

@Composable
fun LibraryHeader(
    isSelectionMode: Boolean,
    selectedFiles: Set<String>,
    filteredFiles: List<LibraryItem>,
    isRefreshing: Boolean,
    showSyncCompleteMessage: Boolean,
    syncMessageText: String,
    syncMessageColor: androidx.compose.ui.graphics.Color,
    currentFolderId: String?,
    libraryViewModel: LibraryViewModel,
    driveViewModel: DriveViewModel,
    rotation: () -> Float,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onSelectionChange: (Boolean) -> Unit,
    onSelectedFilesChange: (Set<String>) -> Unit,
    onRefreshingChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    searchQuery: String,
    onMoveClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // App Bar
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { 
                        onSelectionChange(false)
                    }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        text = "${selectedFiles.size} selected",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val allSelected = selectedFiles.size == filteredFiles.size && filteredFiles.isNotEmpty()
                    Text(
                        text = "All", 
                        color = MaterialTheme.colorScheme.onBackground, 
                        fontSize = 16.sp,
                        modifier = Modifier.clickable {
                            if (!allSelected) onSelectedFilesChange(filteredFiles.map { f -> f.id }.toSet())
                            else onSelectedFilesChange(emptySet())
                        }.padding(start = 8.dp, end = 4.dp)
                    )
                    androidx.compose.material3.Checkbox(
                        checked = allSelected,
                        onCheckedChange = { 
                            if (it) onSelectedFilesChange(filteredFiles.map { f -> f.id }.toSet())
                            else onSelectedFilesChange(emptySet())
                        },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Cyan400)
                    )
                    
                    var showBulkMenu by remember { mutableStateOf(false) }
                    var showBulkDeleteDialog by remember { mutableStateOf(false) }
                    
                    if (showBulkDeleteDialog) {
                        com.example.ui.components.ConfirmationDialog(
                            title = "Delete multiple files",
                            message = "Are you sure you want to delete the selected files?",
                            onConfirm = {
                                selectedFiles.forEach { id ->
                                    libraryViewModel.deleteFile(id)
                                    if (driveViewModel.isConnected.value) {
                                        filteredFiles.find { it.id == id }?.let { f ->
                                            driveViewModel.deleteFileFromDrive(context, f.title, f.driveFileId)
                                        }
                                    }
                                }
                                onSelectionChange(false)
                            },
                            onDismiss = { showBulkDeleteDialog = false }
                        )
                    }

                    IconButton(onClick = { showBulkMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options", tint = Slate400)
                        
                        androidx.compose.material3.DropdownMenu(
                            expanded = showBulkMenu,
                            onDismissRequest = { showBulkMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = { 
                                    showBulkMenu = false
                                    filteredFiles.find { it.id == selectedFiles.first() }?.let { file ->
                                        libraryViewModel.shareFile(context, file, driveViewModel)
                                    }
                                    onSelectionChange(false)
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Save to device") },
                                onClick = { 
                                    showBulkMenu = false
                                    selectedFiles.forEach { id -> 
                                        filteredFiles.find { it.id == id }?.let { file ->
                                            libraryViewModel.saveToDevice(context, file, driveViewModel)
                                        }
                                    }
                                    onSelectionChange(false)
                                },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                            )

                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Move") },
                                onClick = { 
                                    showBulkMenu = false
                                    onMoveClick()
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) }
                            )

                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { 
                                    showBulkMenu = false
                                    showBulkDeleteDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentFolderId != null) {
                        IconButton(onClick = { libraryViewModel.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    
                    val pathSegments = remember(currentFolderId, filteredFiles) {
                        val segments = mutableListOf<PathSegment>()
                        segments.add(PathSegment(null, "Library"))
                        
                        if (currentFolderId != null) {
                            val foldersTemp = mutableListOf<PathSegment>()
                            var currId = currentFolderId
                            while (currId != null) {
                                val folder = libraryViewModel.getFolders().find { f -> f.id == currId }
                                if (folder != null) {
                                    foldersTemp.add(0, PathSegment(folder.id, folder.title))
                                    currId = folder.parentId
                                } else {
                                    currId = null
                                }
                            }
                            segments.addAll(foldersTemp)
                        }
                        segments
                    }

                    val pathScrollState = rememberScrollState()
                    LaunchedEffect(pathSegments.size, currentFolderId) {
                        pathScrollState.animateScrollTo(pathScrollState.maxValue)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(pathScrollState),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            pathSegments.forEachIndexed { index, segment ->
                                if (index > 0) {
                                    Text(
                                        text = ">",
                                        fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Light,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                }
                                
                                val isLast = index == pathSegments.size - 1
                                Text(
                                    text = segment.title,
                                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                                    fontSize = 26.sp,
                                    fontWeight = if (isLast) FontWeight.Normal else FontWeight.Light,
                                    color = if (isLast) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .clickable {
                                            if (!isLast) {
                                                libraryViewModel.setCurrentFolderId(segment.id)
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isConnected by driveViewModel.isConnected.collectAsState()
                    
                    IconButton(onClick = {
                        if (isConnected) {
                            onRefreshingChange(true)
                        } else {
                            android.widget.Toast.makeText(context, "Connect to Google Drive first", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        val iconTint = when {
                            isRefreshing -> Cyan400
                            showSyncCompleteMessage -> Color(0xFF4ADE80)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(
                            imageVector = if (showSyncCompleteMessage) Icons.Default.Check else Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = iconTint,
                            modifier = if (isRefreshing) Modifier.graphicsLayer { rotationZ = rotation() } else Modifier
                        )
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { onSearchQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 0.dp),
            placeholder = { Text("Search files & folders...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search files and folders", tint = Cyan400) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan500,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )
        
        androidx.compose.animation.AnimatedVisibility(
            visible = showSyncCompleteMessage,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Text(
                text = syncMessageText,
                color = syncMessageColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
