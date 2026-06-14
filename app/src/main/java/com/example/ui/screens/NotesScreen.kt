package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.launch
import androidx.compose.foundation.combinedClickable
import com.example.ui.components.GlassBackground
import com.example.ui.components.glassMorphic
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Indigo500
import java.text.SimpleDateFormat
import java.util.*
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    libraryViewModel: LibraryViewModel,
    driveViewModel: DriveViewModel,
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val notes by libraryViewModel.notes.collectAsState()
    val voiceNotes by libraryViewModel.voiceNotes.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }
    
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var showEditNoteDialog by remember { mutableStateOf(false) }

    var isSelectionMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var selectedNotes by androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver<Set<String>, List<String>>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf<Set<String>>(emptySet()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showSyncCompleteMessage by remember { mutableStateOf(false) }
    var syncMessageText by remember { mutableStateOf("Sync complete") }
    var syncMessageColor by remember { mutableStateOf(Cyan400) }
    
    val context = LocalContext.current
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

    val coroutineScope = rememberCoroutineScope()
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "NotesRefreshRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "NotesRefreshRotationAngle"
    )

    val openNewNoteDirectly by libraryViewModel.openNewNoteDirectly.collectAsState()
    val openNoteIdDirectly by libraryViewModel.openNoteIdDirectly.collectAsState()
    
    androidx.activity.compose.BackHandler(enabled = isSelectionMode) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedNotes = emptySet()
        }
    }
    
    LaunchedEffect(openNewNoteDirectly) {
        if (openNewNoteDirectly) {
            noteTitle = ""
            noteContent = ""
            showAddNoteDialog = true
            libraryViewModel.clearOpenNewNoteDirectly()
        }
    }

    LaunchedEffect(openNoteIdDirectly, notes) {
        val noteId = openNoteIdDirectly
        if (noteId != null) {
            val note = notes.find { it.id == noteId }
            if (note != null) {
                editingNoteId = note.id
                noteTitle = note.title
                noteContent = note.content
                showEditNoteDialog = true
            }
            libraryViewModel.clearOpenNoteDirectly()
        }
    }

    LaunchedEffect(showAddNoteDialog, showEditNoteDialog) {
        libraryViewModel.setEditingNote(showAddNoteDialog || showEditNoteDialog)
    }

    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.trim().isEmpty()) {
            notes
        } else {
            notes.filter {
                it.title.contains(searchQuery, ignoreCase = true) || 
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val filteredVoiceNotes = remember(voiceNotes, searchQuery) {
        if (searchQuery.trim().isEmpty()) {
            voiceNotes
        } else {
            voiceNotes.filter {
                it.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scrollState = androidx.compose.foundation.rememberScrollState()
    
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }
    
    val minHt = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp

    // Notes flat list layout
    Box(modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { focusManager.clearFocus() }) {
        GlassBackground(
            modifier = Modifier
                .fillMaxSize(),
            drawBackgroundAndCircles = false
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = 0.dp // Scroll underneath the floating navigation bar
                    )
            ) {
                val minHt = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 0.dp)
                        .verticalScroll(scrollState)
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { focusManager.clearFocus() }
                        .heightIn(min = minHt),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NotesHeader(
                        isSelectionMode = isSelectionMode,
                        selectedNotes = selectedNotes,
                        filteredNotes = filteredNotes,
                        filteredVoiceNotes = filteredVoiceNotes,
                        isRefreshing = isRefreshing,
                        showSyncCompleteMessage = showSyncCompleteMessage,
                        syncMessageText = syncMessageText,
                        syncMessageColor = syncMessageColor,
                        driveViewModel = driveViewModel,
                        rotation = rotation,
                        coroutineScope = coroutineScope,
                        onSelectionChange = { isSelectionMode = it; if (!it) selectedNotes = emptySet() },
                        onSelectedNotesChange = { selectedNotes = it },
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
                        libraryViewModel = libraryViewModel
                    )

                    // Notes List
                    if (filteredNotes.isNotEmpty()) {
                        if (filteredVoiceNotes.isNotEmpty()) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("Text Notes", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Cyan400)
                            }
                        }
                        filteredNotes.forEach { note ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                NoteCard(
                                    note = note,
                                    libraryViewModel = libraryViewModel,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedNotes.contains(note.id),
                                    onClick = {
                                        if (isSelectionMode) {
                                            val newSelection = selectedNotes.toMutableSet()
                                            if (newSelection.contains(note.id)) newSelection.remove(note.id) else newSelection.add(note.id)
                                            selectedNotes = newSelection
                                        } else {
                                            editingNoteId = note.id
                                            noteTitle = note.title
                                            noteContent = note.content
                                            showEditNoteDialog = true
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedNotes = setOf(note.id)
                                        }
                                    },
                                    onDeleteClick = { libraryViewModel.deleteNote(note.id) }
                                )
                            }
                        }
                    }

                    // Voice Notes List
                    if (filteredVoiceNotes.isNotEmpty()) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("Voice Notes", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Cyan400)
                        }
                        filteredVoiceNotes.forEach { note ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                VoiceNoteCard(
                                    note = note, 
                                    onDelete = { 
                                        libraryViewModel.deleteVoiceNote(note.id)
                                        if (driveViewModel.isConnected.value && note.driveFileId != null) {
                                            driveViewModel.deleteFileFromDrive(context, note.title, note.driveFileId)
                                        }
                                    },
                                    libraryViewModel = libraryViewModel,
                                    driveViewModel = driveViewModel,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedNotes.contains(note.id),
                                    onClick = {
                                        if (isSelectionMode) {
                                            val newSelection = selectedNotes.toMutableSet()
                                            if (newSelection.contains(note.id)) newSelection.remove(note.id) else newSelection.add(note.id)
                                            selectedNotes = newSelection
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedNotes = setOf(note.id)
                                        }
                                    },
                                    onDownloadRequest = {
                                        if (!isOnline.value) {
                                            syncMessageText = "Please connect to internet"
                                            syncMessageColor = androidx.compose.ui.graphics.Color(0xFFEF4444)
                                            showSyncCompleteMessage = true
                                            coroutineScope.launch { kotlinx.coroutines.delay(3000); showSyncCompleteMessage = false }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Empty State
                    if (filteredNotes.isEmpty() && filteredVoiceNotes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .heightIn(min = 360.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Cyan400.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No matching notes" else "No notes yet",
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
                    }

                    Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 96.dp))
                }
        }
    }

        // Floating Action Button in same position as other tabs
        val isFabExpanded by libraryViewModel.isFabExpanded.collectAsState()
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
        
        if (searchQuery.isEmpty()) {
            if (isFabExpanded) {
                androidx.compose.foundation.layout.Box(
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
                onScanClick = null,
                onUploadClick = null,
                onFolderClick = null,
                onNoteClick = {
                    libraryViewModel.isFabExpanded.value = false
                    noteTitle = ""
                    noteContent = ""
                    showAddNoteDialog = true
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

        // Full Screen Editor / Visual screen overlay for Add Note
        androidx.compose.animation.AnimatedVisibility(
            visible = showAddNoteDialog,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) + 
                    androidx.compose.animation.scaleIn(
                        initialScale = 0.95f, 
                        animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                    ),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(250)) + 
                   androidx.compose.animation.scaleOut(
                        targetScale = 0.95f, 
                        animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                   ),
            modifier = Modifier.fillMaxSize()
        ) {
            FullScreenNoteEditor(
                title = noteTitle,
                content = noteContent,
                onTitleChange = { noteTitle = it },
                onContentChange = { noteContent = it },
                isNewNote = true,
                onBack = { showAddNoteDialog = false },
                onCancel = { showAddNoteDialog = false },
                onSave = {
                    if (noteTitle.trim().isEmpty()) {
                        android.widget.Toast.makeText(context, "Heading is mandatory", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        libraryViewModel.addNote(noteTitle, noteContent)
                        showAddNoteDialog = false
                    }
                },
                libraryViewModel = libraryViewModel,
                noteItem = NoteItem(title = noteTitle, content = noteContent)
            )
        }

        // Full Screen Editor / Visual screen overlay for Edit Note
        androidx.compose.animation.AnimatedVisibility(
            visible = showEditNoteDialog && editingNoteId != null,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) + 
                    androidx.compose.animation.scaleIn(
                        initialScale = 0.95f, 
                        animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                    ),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(250)) + 
                   androidx.compose.animation.scaleOut(
                        targetScale = 0.95f, 
                        animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                   ),
            modifier = Modifier.fillMaxSize()
        ) {
            val noteItem = notes.find { it.id == editingNoteId }
            if (noteItem != null) {
                FullScreenNoteEditor(
                    title = noteTitle,
                    content = noteContent,
                    onTitleChange = { noteTitle = it },
                    onContentChange = { noteContent = it },
                    isNewNote = false,
                    onBack = { showEditNoteDialog = false },
                    onCancel = { showEditNoteDialog = false },
                    onSave = {
                        if (noteTitle.trim().isEmpty()) {
                            android.widget.Toast.makeText(context, "Heading is mandatory", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            libraryViewModel.updateNote(noteItem.id, noteTitle, noteContent)
                            showEditNoteDialog = false
                        }
                    },
                    libraryViewModel = libraryViewModel,
                    noteItem = noteItem
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullScreenNoteEditor(
    title: String,
    content: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    isNewNote: Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    libraryViewModel: LibraryViewModel,
    noteItem: NoteItem
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        com.example.ui.components.ConfirmationDialog(
            title = "Delete note",
            message = "Are you sure you want to delete this note?",
            onConfirm = {
                libraryViewModel.deleteNote(noteItem.id)
                onBack()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    val initialTitle = remember { title }
    val initialContent = remember { content }

    val hasChanges = remember(title, content, initialTitle, initialContent) {
        title != initialTitle || content != initialContent
    }

    androidx.activity.compose.BackHandler {
        if (hasChanges) {
            showUnsavedChangesDialog = true
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = false) {} // block click propagation
    ) {
        GlassBackground(
            modifier = Modifier.fillMaxSize(),
            drawBackgroundAndCircles = true
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = 96.dp) // Leave spacer room for cancel/save at the bottom
            ) {
                // Header Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (hasChanges) {
                            showUnsavedChangesDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (!isNewNote) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        showMenu = false
                                        val currentNote = noteItem.copy(title = title, content = content)
                                        libraryViewModel.shareNote(context, currentNote)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save to device", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        showMenu = false
                                        val currentNote = noteItem.copy(title = title, content = content)
                                        libraryViewModel.saveNoteToDevice(context, currentNote)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    },
                                    leadingIcon = { 
                                        Icon(
                                            imageVector = Icons.Default.Delete, 
                                            contentDescription = null, 
                                            tint = MaterialTheme.colorScheme.error
                                        ) 
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Heading Title
                TextField(
                    value = title,
                    onValueChange = onTitleChange,
                    placeholder = { 
                        Text(
                            "Heading",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold
                        ) 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Cyan400
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Content Text Editor
                TextField(
                    value = content,
                    onValueChange = onContentChange,
                    placeholder = { 
                        Text(
                            "Note content goes here...",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                            fontSize = 16.sp
                        ) 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Cyan400
                    )
                )
            }
        }

        // Bottom Action buttons (Cancel / Save)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isNewNote) {
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                ) {
                    Text("Cancel", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = Color.Black),
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Save", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        // Unsaved changes alert dialog
        if (showUnsavedChangesDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showUnsavedChangesDialog = false },
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
                                text = "Do you want to save your edits?",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { 
                                    showUnsavedChangesDialog = false
                                    onCancel() // Don't save, just cancel/discard
                                }) {
                                    Text("Don't Save", color = Color(0xFF94A3B8), fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Button(
                                    onClick = {
                                        showUnsavedChangesDialog = false
                                        onSave()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = Color.Black),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("Save", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
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
fun NoteCard(
    note: NoteItem,
    libraryViewModel: LibraryViewModel,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDeleteClick: () -> Unit
) {
    val isDark = true
    val context = LocalContext.current
    var showCardMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val pinnedNote1Id by libraryViewModel.pinnedNote1Id.collectAsState()
    val pinnedNote2Id by libraryViewModel.pinnedNote2Id.collectAsState()
    val isPinned = note.id == pinnedNote1Id || note.id == pinnedNote2Id

    if (showDeleteDialog) {
        com.example.ui.components.ConfirmationDialog(
            title = "Delete note",
            message = "Are you sure you want to delete this note?",
            onConfirm = onDeleteClick,
            onDismiss = { showDeleteDialog = false }
        )
    }
    
    val formattedDate = remember(note.createdAt) {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        sdf.format(Date(note.createdAt))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassMorphic(RoundedCornerShape(24.dp), isDark = isDark)
            .let { if (isSelected) it.border(1.dp, Cyan400, RoundedCornerShape(24.dp)).background(Cyan500.copy(alpha = 0.1f), RoundedCornerShape(24.dp)) else it }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPinned) Cyan400 else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { showCardMenu = true },
                        modifier = Modifier.fillMaxSize(),
                        enabled = !isSelectionMode
                    ) {
                        if (!isSelectionMode) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Note Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    DropdownMenu(
                        expanded = showCardMenu,
                        onDismissRequest = { showCardMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showCardMenu = false
                                libraryViewModel.shareNote(context, note)
                            },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        DropdownMenuItem(
                            text = { Text("Save to device", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showCardMenu = false
                                libraryViewModel.saveNoteToDevice(context, note)
                            },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showCardMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = note.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Create,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun NotesHeader(
    isSelectionMode: Boolean,
    selectedNotes: Set<String>,
    filteredNotes: List<NoteItem>,
    filteredVoiceNotes: List<VoiceNote>,
    isRefreshing: Boolean,
    showSyncCompleteMessage: Boolean,
    syncMessageText: String,
    syncMessageColor: androidx.compose.ui.graphics.Color,
    driveViewModel: DriveViewModel,
    rotation: Float,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onSelectionChange: (Boolean) -> Unit,
    onSelectedNotesChange: (Set<String>) -> Unit,
    onRefreshingChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    searchQuery: String,
    libraryViewModel: LibraryViewModel
) {
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
                        text = "${selectedNotes.size} selected",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val allItemsCount = filteredNotes.size + filteredVoiceNotes.size
                    val allSelected = selectedNotes.size == allItemsCount && allItemsCount > 0
                    Text(
                        text = "All", 
                        color = MaterialTheme.colorScheme.onBackground, 
                        fontSize = 16.sp,
                        modifier = Modifier.clickable {
                            if (!allSelected) {
                                onSelectedNotesChange(filteredNotes.map { f -> f.id }.toSet() + filteredVoiceNotes.map { f -> f.id }.toSet())
                            }
                            else {
                                onSelectedNotesChange(emptySet())
                            }
                        }.padding(start = 8.dp, end = 4.dp)
                    )
                    androidx.compose.material3.Checkbox(
                        checked = allSelected,
                        onCheckedChange = { 
                            if (it) onSelectedNotesChange(filteredNotes.map { f -> f.id }.toSet() + filteredVoiceNotes.map { f -> f.id }.toSet())
                            else onSelectedNotesChange(emptySet())
                        },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Cyan400)
                    )
                    
                    var showBulkMenu by remember { mutableStateOf(false) }
                    var showBulkDeleteDialog by remember { mutableStateOf(false) }

                    if (showBulkDeleteDialog) {
                        com.example.ui.components.ConfirmationDialog(
                            title = "Delete multiple notes",
                            message = "Are you sure you want to delete the selected items?",
                            onConfirm = {
                                selectedNotes.forEach { id ->
                                    if (filteredNotes.any { it.id == id }) {
                                        libraryViewModel.deleteNote(id)
                                    } else if (filteredVoiceNotes.any { it.id == id }) {
                                        libraryViewModel.deleteVoiceNote(id)
                                    }
                                }
                                onSelectionChange(false)
                            },
                            onDismiss = { showBulkDeleteDialog = false }
                        )
                    }

                    IconButton(onClick = { showBulkMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        androidx.compose.material3.DropdownMenu(
                            expanded = showBulkMenu,
                            onDismissRequest = { showBulkMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = { 
                                    showBulkMenu = false
                                    libraryViewModel.shareMultipleItems(context, selectedNotes)
                                    onSelectionChange(false)
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Save to device") },
                                onClick = { 
                                    showBulkMenu = false
                                    libraryViewModel.saveMultipleItems(context, selectedNotes)
                                    onSelectionChange(false)
                                },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Notes",
                        fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                            modifier = if (isRefreshing) Modifier.rotate(rotation) else Modifier
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
            placeholder = { Text("Search your notes...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search your notes", tint = Cyan400) },
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VoiceNoteCard(
    note: VoiceNote,
    onDelete: () -> Unit,
    libraryViewModel: LibraryViewModel,
    driveViewModel: DriveViewModel,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDownloadRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableIntStateOf(0) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var showProgressBar by remember { mutableStateOf(false) }
    
    val downloadingFiles by driveViewModel.downloadingFiles.collectAsState()
    val isDownloading = downloadingFiles[note.id] != null
    val downloadProgress = downloadingFiles[note.id]

    val pinnedNote1Id by libraryViewModel.pinnedNote1Id.collectAsState()
    val pinnedNote2Id by libraryViewModel.pinnedNote2Id.collectAsState()
    val isPinned = note.id == pinnedNote1Id || note.id == pinnedNote2Id

    // Check if file exists locally
    val uri = android.net.Uri.parse(note.uriString)
    val fileExists = try {
        val file = java.io.File(java.net.URI(note.uriString).path)
        file.exists() && file.length() > 0
    } catch(e: Exception) {
        false
    }
    
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassMorphic(RoundedCornerShape(24.dp))
            .let { if (isSelected) it.border(1.dp, Cyan400, RoundedCornerShape(24.dp)).background(Cyan500.copy(alpha = 0.1f), RoundedCornerShape(24.dp)) else it }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (!fileExists && note.driveFileId != null && !isDownloading) {
                            onDownloadRequest()
                            // Download
                            driveViewModel.downloadFileFromDrive(context, libraryViewModel, note.driveFileId, note.title, note.id) {
                                // No callback needed, observing state
                            }
                        } else if (fileExists) {
                            // Play/Pause
                            if (isPlaying) {
                                mediaPlayer?.pause()
                                isPlaying = false
                            } else {
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
                                                showProgressBar = false
                                            }
                                        }
                                    }
                                    mediaPlayer?.start()
                                    isPlaying = true
                                    showProgressBar = true
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error playing audio", android.widget.Toast.LENGTH_SHORT).show()
                                    isPlaying = false
                                }
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
                    } else if (!fileExists && note.driveFileId != null) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = Cyan400)
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Cyan400
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPinned) Cyan400 else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    
                    val formattedDate = remember(note.createdAt) {
                        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
                        sdf.format(java.util.Date(note.createdAt))
                    }
                    Text(
                        text = formattedDate,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                var showMenu by remember { mutableStateOf(false) }
                var showRenameDialog by remember { mutableStateOf(false) }
                var showDeleteDialog by remember { mutableStateOf(false) }
                var renameText by remember { mutableStateOf(note.title) }
                var renameError by remember { mutableStateOf<String?>(null) }

                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { showMenu = true },
                        enabled = !isSelectionMode
                    ) {
                        if (!isSelectionMode) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showMenu = false
                                renameText = note.title
                                renameError = null
                                showRenameDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        DropdownMenuItem(
                            text = { Text("Share", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showMenu = false
                                val item = com.example.ui.screens.LibraryItem(
                                    id = note.id,
                                    title = if (note.title.endsWith(".m4a", ignoreCase = true)) note.title else "${note.title}.m4a",
                                    subtitle = "Voice Note",
                                    icon = androidx.compose.material.icons.Icons.Default.Mic,
                                    iconTint = Cyan400,
                                    iconBg = Cyan500.copy(alpha = 0.15f),
                                    tags = emptyList(),
                                    isFolder = false,
                                    parentId = null,
                                    uri = if(fileExists) android.net.Uri.parse(note.uriString) else null,
                                    driveFileId = note.driveFileId
                                )
                                libraryViewModel.shareFile(context, item, driveViewModel)
                            },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        DropdownMenuItem(
                            text = { Text("Save to device", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showMenu = false
                                val item = com.example.ui.screens.LibraryItem(
                                    id = note.id,
                                    title = if (note.title.endsWith(".m4a", ignoreCase = true)) note.title else "${note.title}.m4a",
                                    subtitle = "Voice Note",
                                    icon = androidx.compose.material.icons.Icons.Default.Mic,
                                    iconTint = Cyan400,
                                    iconBg = Cyan500.copy(alpha = 0.15f),
                                    tags = emptyList(),
                                    isFolder = false,
                                    parentId = null,
                                    uri = if(fileExists) android.net.Uri.parse(note.uriString) else null,
                                    driveFileId = note.driveFileId
                                )
                                libraryViewModel.saveToDevice(context, item, driveViewModel)
                            },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }

                if (showRenameDialog) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showRenameDialog = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Box(modifier = Modifier.padding(24.dp)) {
                            com.example.ui.components.GlassBackground(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                            ) {
                                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                                    Text(text = "Rename Voice Note", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    androidx.compose.material3.OutlinedTextField(
                                        value = renameText,
                                        onValueChange = { 
                                            renameText = it 
                                            renameError = null
                                        },
                                        label = { Text("Title") },
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Cyan400,
                                            focusedLabelColor = Cyan400,
                                            cursorColor = Cyan400
                                        ),
                                        isError = renameError != null,
                                        supportingText = {
                                            if (renameError != null) {
                                                Text(text = renameError!!, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        androidx.compose.material3.TextButton(onClick = { showRenameDialog = false }) {
                                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                if (renameText.isBlank()) {
                                                    renameError = "Title is mandatory"
                                                } else {
                                                    libraryViewModel.renameVoiceNote(note.id, renameText.trim())
                                                    if (driveViewModel.isConnected.value && note.driveFileId != null) {
                                                        driveViewModel.renameFileInDrive(context, note.driveFileId, renameText.trim() + ".m4a")
                                                    }
                                                    showRenameDialog = false
                                                }
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = Cyan400,
                                                contentColor = androidx.compose.ui.graphics.Color.Black
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
                
                if (showDeleteDialog) {
                    com.example.ui.components.ConfirmationDialog(
                        title = "Delete Voice Note",
                        message = "Are you sure you want to delete this voice note?",
                        onConfirm = {
                            onDelete()
                            showDeleteDialog = false
                        },
                        onDismiss = { showDeleteDialog = false }
                    )
                }
            }
            
            if (showProgressBar) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format("%02d:%02d", (currentPosition / 1000) / 60, (currentPosition / 1000) % 60),
                        fontSize = 12.sp,
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
                    Text(
                        text = String.format("%02d:%02d", (duration / 1000) / 60, (duration / 1000) % 60),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.requiredWidth(42.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
