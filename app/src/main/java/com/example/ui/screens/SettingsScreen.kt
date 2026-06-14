package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ui.components.GlassBackground
import com.example.ui.components.glassMorphic

@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel? = null,
    libraryViewModel: LibraryViewModel,
    driveViewModel: DriveViewModel? = null,
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fallbackFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<AuthState>(AuthState.Idle) }
    val authState by (authViewModel?.uiState ?: fallbackFlow).collectAsState()
    
    LaunchedEffect(Unit) {
        authViewModel?.checkExistingGoogleAccount(context)
    }

    val prefs = remember { context.getSharedPreferences("SettingsPrefs", android.content.Context.MODE_PRIVATE) }
    var activeEditField by remember { mutableStateOf<String?>(null) } // "name", "status", "bio"
    var editDialogValue by remember { mutableStateOf("") }
    var isEditingProfile by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

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

    var selectedImageUriForCrop by remember { mutableStateOf<android.net.Uri?>(null) }
    var isPhotoViewerOpen by remember { mutableStateOf(false) }
    var showPhotoEditOptions by remember { mutableStateOf(false) }
    var showOfflineMessage by remember { mutableStateOf(false) }
    
    var isSavingProfilePic by remember { mutableStateOf(false) }
    var isRemovingProfilePic by remember { mutableStateOf(false) }
    
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = selectedImageUriForCrop != null || isPhotoViewerOpen || showPhotoEditOptions || showLogoutDialog || isEditingProfile) {
        if (selectedImageUriForCrop != null) {
            selectedImageUriForCrop = null
        } else if (showPhotoEditOptions) {
            showPhotoEditOptions = false
        } else if (isPhotoViewerOpen) {
            isPhotoViewerOpen = false
        } else if (showLogoutDialog) {
            showLogoutDialog = false
        } else if (isEditingProfile) {
            isEditingProfile = false
        }
    }

    if (showLogoutDialog) {
        com.example.ui.components.ConfirmationDialog(
            title = "Log Out",
            message = "Are you sure you want to log out?",
            confirmText = "Log Out",
            onConfirm = {
                libraryViewModel.clearFiles(context)
                authViewModel?.signOut(context) 
                driveViewModel?.signOut(context)
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                selectedImageUriForCrop = uri
            }
        }
    )

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
                    bottom = 0.dp
                )
                .padding(horizontal = 16.dp)
        ) {
            // Scrollable Settings List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
            ) {
                // Header inside the list so it scrolls
                item {
                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Accounts and more",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // PROFILE SECTION
                item {
                    if (authState is AuthState.Success) {
                        val user = authState as AuthState.Success
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .glassMorphic(RoundedCornerShape(24.dp))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                                Text(
                                    text = "Profile",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.example.ui.theme.Cyan400,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Avatar display and picker
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(com.example.ui.theme.Cyan400)
                                            .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                            .clickable {
                                                isPhotoViewerOpen = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!user.profilePic.isNullOrEmpty()) {
                                            val picUrl = user.profilePic
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
                                        } else {
                                            Text(
                                                text = user.displayName?.firstOrNull()?.uppercase() ?: "U",
                                                color = Color.White,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.displayName ?: "User",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = user.email,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "👉 ${if (user.bio.isNullOrBlank()) "No bio set" else user.bio}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                Spacer(modifier = Modifier.height(12.dp))
    
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isEditingProfile,
                                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Editable Fields List
                                        ProfileEditRow(
                                            label = "Username",
                                            value = if (user.displayName.isNullOrBlank()) "No username set" else user.displayName,
                                            onClick = {
                                                editDialogValue = user.displayName ?: ""
                                                activeEditField = "name"
                                            }
                                        )
        
                                        ProfileEditRow(
                                            label = "Status Message",
                                            value = if (user.statusMsg.isNullOrBlank()) "No status set" else user.statusMsg,
                                            onClick = {
                                                editDialogValue = user.statusMsg ?: ""
                                                activeEditField = "status"
                                            }
                                        )
        
                                        ProfileEditRow(
                                            label = "Bio",
                                            value = if (user.bio.isNullOrBlank()) "No bio set" else user.bio,
                                            onClick = {
                                                editDialogValue = user.bio ?: ""
                                                activeEditField = "bio"
                                            }
                                        )
      
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                              
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Button(
                                            onClick = { isEditingProfile = !isEditingProfile },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer, 
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            modifier = Modifier.weight(1.2f),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isEditingProfile) "Done" else "Edit Profile", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                        
                                        Spacer(modifier = Modifier.width(16.dp))
                                        
                                        Button(
                                            onClick = { 
                                                if (isOnline.value) {
                                                    showOfflineMessage = false
                                                    showLogoutDialog = true 
                                                } else {
                                                    showOfflineMessage = true
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer, 
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            modifier = Modifier.weight(1f),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Logout", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (showOfflineMessage) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Please connect to internet to log out", 
                                            fontSize = 12.sp, 
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // IF SOMEHOW SETTINGS IS ACCESSED WHILE NOT LOGGED IN
                        Text(
                            text = "Please log in to view settings.",
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // INSTRUCTIONS SECTION
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .glassMorphic(RoundedCornerShape(24.dp))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                            Text(
                                text = "Instructions",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.Cyan400,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "• Hey there! Welcome to Scholar Space! This app safely connects to your Google Drive to keep all your study files synced and secure.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Use the 'Library' tab to manage your stuff. You can upload, download, and organize all your folders and files there.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Jump into the 'Notes' tab whenever you need to jot down quick thoughts or ideas—they'll back up automatically.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Check out the 'Calendar' to track events, reminders, timers and day counters. Keep these updated to help you stay on track!",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
    
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Text(
                                text = "⚠️ Do Not Modify Drive Folder",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please DO NOT edit or delete the 'Scholar Space' folder in your Google Drive, especially the hidden app data folder. Modifying it will break your app sync and corrupt data.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // ABOUT & SUPPORT SECTION
                item {
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .glassMorphic(RoundedCornerShape(24.dp))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                            Text(
                                text = "About & Support",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.Cyan400,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { 
                                    try {
                                        uriHandler.openUri("https://razorpay.me/@scholarspace") 
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "No browser app was found to open this link.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Support the Developer", fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { 
                                    try {
                                        uriHandler.openUri("https://scholarspace.xyz") 
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "No browser app was found to open this link.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Visit Website", color = MaterialTheme.colorScheme.onSurface)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { 
                                    try {
                                        val bodyStr = """
                                            Device Info (Model, Android Version):
                                            
                                            
                                            App Version:
                                            
                                            
                                            Description:
                                            
                                            
                                            Upload Attachments:
                                            (Please attach any screenshots or screen recordings to this email)
                                        """.trimIndent()
                                        
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                            data = android.net.Uri.parse("mailto:app.scholarspace@gmail.com")
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Topic: ")
                                            putExtra(android.content.Intent.EXTRA_TEXT, bodyStr)
                                        }
                                        intent.setPackage("com.google.android.gm")
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            intent.setPackage(null)
                                            context.startActivity(android.content.Intent.createChooser(intent, "Send Feedback"))
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "No email app found.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Feedback, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send Feedback", color = MaterialTheme.colorScheme.onSurface)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedButton(
                                onClick = { 
                                    if (isCheckingUpdate) return@OutlinedButton
                                    isCheckingUpdate = true
                                    coroutineScope.launch {
                                        val updateInfo = com.example.utils.UpdateChecker.checkForUpdates(
                                            githubOwner = "kushagarwal2580",
                                            githubRepo = "Scholar-Space",
                                            currentVersion = com.example.BuildConfig.VERSION_NAME
                                        )
                                        isCheckingUpdate = false
                                        if (updateInfo != null && updateInfo.isUpdateAvailable) {
                                            android.widget.Toast.makeText(context, "New version v${updateInfo.latestVersion} available! Downloading...", android.widget.Toast.LENGTH_LONG).show()
                                            try {
                                                uriHandler.openUri(updateInfo.downloadUrl)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "No browser found.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else if (updateInfo != null) {
                                            android.widget.Toast.makeText(context, "App is up to date (v${com.example.BuildConfig.VERSION_NAME}).", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Failed to check for updates.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isCheckingUpdate) {
                                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Checking...", color = MaterialTheme.colorScheme.onSurface)
                                } else {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Check for Updates", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "App Version v${com.example.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // MULTI-FIELD PROFILE EDIT DIALOG
    if (activeEditField != null && authState is AuthState.Success) {
        val user = authState as AuthState.Success
        val field = activeEditField!!
        var validationError by remember(activeEditField) { mutableStateOf<String?>(null) }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { activeEditField = null },
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
                            text = "Edit " + field.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        OutlinedTextField(
                            value = editDialogValue,
                            onValueChange = { 
                                editDialogValue = it
                                validationError = null
                            },
                            label = { Text(field.replaceFirstChar { it.uppercase() }) },
                            singleLine = field != "bio",
                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                            isError = validationError != null,
                            supportingText = {
                                if (validationError != null) {
                                    Text(text = validationError!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { activeEditField = null; authViewModel?.clearErrorState() }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                if (field == "name" && editDialogValue.trim().isEmpty()) {
                                    validationError = "Username is mandatory"
                                    return@TextButton
                                }
                                val newNickname = if (field == "name") editDialogValue else user.displayName ?: ""
                                val newPhoto = if (field == "photo") editDialogValue else user.profilePic
                                val newPhone = user.phone
                                val newBio = if (field == "bio") editDialogValue else user.bio
                                val newStatus = if (field == "status") editDialogValue else user.statusMsg
                                
                                authViewModel?.updateProfile(
                                    nickname = newNickname,
                                    profilePic = newPhoto,
                                    phone = newPhone,
                                    bio = newBio,
                                    statusMsg = newStatus
                                )
                                activeEditField = null
                            }) {
                                Text("Save", color = com.example.ui.theme.Cyan400)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- FULL SCREEN PHOTO VIEWER OVERLAY ---
    val successUser = authState as? AuthState.Success
    androidx.compose.animation.AnimatedVisibility(
        visible = successUser != null && isPhotoViewerOpen,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut()
    ) {
        if (successUser != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { isPhotoViewerOpen = false },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = successUser != null && isPhotoViewerOpen,
                    enter = androidx.compose.animation.scaleIn(initialScale = 0.8f),
                    exit = androidx.compose.animation.scaleOut(targetScale = 0.8f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Large circular avatar preview
                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .clip(CircleShape)
                                .background(com.example.ui.theme.Cyan400)
                                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            val r = size.width / 2f
                                            val dx = offset.x - r
                                            val dy = offset.y - r
                                            if (dx * dx + dy * dy > r * r) {
                                                isPhotoViewerOpen = false
                                            }
                                        },
                                        onLongPress = { offset ->
                                            val r = size.width / 2f
                                            val dx = offset.x - r
                                            val dy = offset.y - r
                                            if (dx * dx + dy * dy <= r * r) {
                                                showPhotoEditOptions = true
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!successUser.profilePic.isNullOrEmpty()) {
                                    val picUrl = successUser.profilePic
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
                                        contentDescription = "Profile Picture Large",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = successUser.displayName?.firstOrNull()?.uppercase() ?: "U",
                                        color = Color.White,
                                        fontSize = 110.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Text(
                                text = "Tap and Hold to change",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                modifier = Modifier.pointerInput(Unit) {
                                    detectTapGestures(onTap = { /* Consume tap */ })
                                }
                            )
                        }
                    }
                }
            }
        }

    // --- PHOTO OPTIONS DIALOG (Remove / Upload) ---
    if (successUser != null && showPhotoEditOptions) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showPhotoEditOptions = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                GlassBackground(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(32.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text("Profile Photo Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPhotoEditOptions = false
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                                .padding(vertical = 12.dp, horizontal = 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(40.dp).background(com.example.ui.theme.Cyan500.copy(alpha=0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Upload Photo",
                                    tint = com.example.ui.theme.Cyan400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Upload photo", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Pick a photo from your gallery", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (!successUser.profilePic.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showPhotoEditOptions = false
                                        isPhotoViewerOpen = false
                                        authViewModel?.updateProfile(
                                            nickname = successUser.displayName ?: "",
                                            profilePic = null,
                                            phone = successUser.phone,
                                            bio = successUser.bio,
                                            statusMsg = successUser.statusMsg
                                        )
                                        isRemovingProfilePic = true
                                    }
                                    .padding(vertical = 12.dp, horizontal = 0.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(40.dp).background(Color(0xFFEF4444).copy(alpha=0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Photo",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Remove photo", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFFEF4444))
                                    Text("Delete current profile picture", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showPhotoEditOptions = false }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- PHOTO CUSTOM INTERACTIVE ZOOM/PAN CROPPER ---
    if (successUser != null && selectedImageUriForCrop != null) {
        var imgScale by remember { mutableStateOf(1f) }
        var imgOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val context = androidx.compose.ui.platform.LocalContext.current
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val boxSizePx = 280f * density

        var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        androidx.compose.runtime.LaunchedEffect(selectedImageUriForCrop) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(selectedImageUriForCrop!!)
                    val decoded = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bitmap = decoded
                } catch(e: Exception) { e.printStackTrace() }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                val bW = bitmap!!.width.toFloat()
                val bH = bitmap!!.height.toFloat()
                val baseScale = maxOf(boxSizePx / bW, boxSizePx / bH)
                val scaledW = bW * baseScale
                val scaledH = bH * baseScale

                val transformState = androidx.compose.foundation.gestures.rememberTransformableState { zoomChange, offsetChange, _ ->
                    imgScale = (imgScale * zoomChange).coerceIn(1f, 4f)
                    
                    // Bounding logic
                    val maxOffsetX = ((scaledW * imgScale - boxSizePx) / 2f).coerceAtLeast(0f)
                    val maxOffsetY = ((scaledH * imgScale - boxSizePx) / 2f).coerceAtLeast(0f)
                    
                    imgOffset = androidx.compose.ui.geometry.Offset(
                        x = (imgOffset.x + offsetChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                        y = (imgOffset.y + offsetChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformState),
                    contentAlignment = Alignment.Center
                ) {
                    // Underlay: the actual scaled image
                    androidx.compose.foundation.Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Image to crop",
                        modifier = Modifier
                            .size(
                                width = (scaledW / density).dp,
                                height = (scaledH / density).dp
                            )
                            .graphicsLayer(
                                scaleX = imgScale,
                                scaleY = imgScale,
                                translationX = imgOffset.x,
                                translationY = imgOffset.y
                            ),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )

                    // Overlay: dark background with a transparent circle cutout
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasSize = size
                        val circleRadius = boxSizePx / 2f
                        val centerOffset = androidx.compose.ui.geometry.Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                        val circleRect = androidx.compose.ui.geometry.Rect(
                            center = centerOffset,
                            radius = circleRadius
                        )
                        val squareRect = androidx.compose.ui.geometry.Rect(
                            center = centerOffset,
                            radius = canvasSize.width / 2f
                        )
                        
                        // Path 1: Pitch black everywhere EXCEPT the square cutout
                        val outerPath = androidx.compose.ui.graphics.Path().apply {
                            addRect(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, canvasSize))
                            addRect(squareRect)
                            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                        }
                        
                        drawPath(
                            path = outerPath,
                            color = Color.Black
                        )
                        
                        // Path 2: Dim black inside the square EXCEPT the circle cutout
                        val innerPath = androidx.compose.ui.graphics.Path().apply {
                            addRect(squareRect)
                            addOval(circleRect)
                            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                        }
                        
                        drawPath(
                            path = innerPath,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                        
                        // Circle border
                        drawCircle(
                            color = Color.White,
                            radius = circleRadius,
                            center = centerOffset,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                        )
                    }
                }
            } else {
                androidx.compose.material3.CircularProgressIndicator(color = com.example.ui.theme.Cyan400)
            }

            // Top Header: "Edit your photo"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 100.dp, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "Edit your photo",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom controls: Cancel and Save only
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 64.dp, start = 32.dp, end = 32.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "Pinch to zoom • Drag to adjust",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            selectedImageUriForCrop = null
                            isPhotoViewerOpen = false
                        }
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            if (bitmap != null) {
                                val savedImageUriString = cropAndSaveImage(
                                    context = context,
                                    uri = selectedImageUriForCrop!!,
                                    scale = imgScale,
                                    offset = imgOffset,
                                    boxSizePx = boxSizePx,
                                    fileName = "profile_pic_${System.currentTimeMillis()}.jpg"
                                )
                                if (savedImageUriString != null) {
                                    authViewModel?.updateProfile(
                                        nickname = successUser.displayName ?: "",
                                        profilePic = savedImageUriString,
                                        phone = successUser.phone,
                                        bio = successUser.bio,
                                        statusMsg = successUser.statusMsg
                                    )
                                    isSavingProfilePic = true
                                }
                            }
                            selectedImageUriForCrop = null
                            isPhotoViewerOpen = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.example.ui.theme.Cyan400,
                            contentColor = Color.Black
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        Text("Save Photo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (isSavingProfilePic || isRemovingProfilePic) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                GlassBackground(
                    modifier = Modifier
                        .wrapContentSize()
                        .clip(RoundedCornerShape(32.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(color = com.example.ui.theme.Cyan400)
                        Text(
                            text = if (isSavingProfilePic) "Saving..." else "Removing...",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(isSavingProfilePic) {
        if (isSavingProfilePic) {
            kotlinx.coroutines.delay(3000L)
            isSavingProfilePic = false
        }
    }

    LaunchedEffect(isRemovingProfilePic) {
        if (isRemovingProfilePic) {
            kotlinx.coroutines.delay(3000L)
            isRemovingProfilePic = false
        }
    }

}

@Composable
fun ProfileEditRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Edit field",
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}
