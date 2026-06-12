package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassBackground
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Slate400

@Composable
fun MoveFileDialog(
    initialFolderId: String?,
    itemsToMove: Set<String>,
    libraryViewModel: LibraryViewModel,
    onDismiss: () -> Unit,
    onMoveConfirm: (String?) -> Unit
) {
    var currentDestId by remember { mutableStateOf<String?>(null) }
    var showCreateFolderLocal by remember { mutableStateOf(false) }
    var newFolderNameLocal by remember { mutableStateOf("") }

    val allFiles by libraryViewModel.allFiles.collectAsState()
    val allFolders = remember(allFiles) {
        allFiles.filter { it.isFolder && !it.title.equals("App Data", ignoreCase = true) }
    }
    
    val currentFolders = allFolders.filter { it.parentId == currentDestId && !itemsToMove.contains(it.id) }
    
    val isDestInvalid = remember(currentDestId, itemsToMove, allFolders) {
        if (currentDestId == null) return@remember false
        var curr: String? = currentDestId
        while (curr != null) {
            if (itemsToMove.contains(curr)) return@remember true
            curr = allFolders.find { it.id == curr }?.parentId
        }
        false
    }
    
    val currentPath = remember(currentDestId, allFolders) {
        val path = mutableListOf<LibraryItem>()
        var currId = currentDestId
        while (currId != null) {
            val folder = allFolders.find { it.id == currId }
            if (folder != null) {
                path.add(0, folder)
                currId = folder.parentId
            } else {
                currId = null
            }
        }
        path
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.padding(16.dp).fillMaxHeight(0.8f)) {
            GlassBackground(
                modifier = Modifier
                    .fillMaxSize()
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
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Move items to...",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { showCreateFolderLocal = true }) {
                            Icon(Icons.Default.Add, contentDescription = "New Folder", tint = Cyan400)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Breadcrumbs / Current Location
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { 
                                val parent = if (currentDestId == null) null else allFolders.find { it.id == currentDestId }?.parentId
                                currentDestId = parent
                            },
                            enabled = currentDestId != null,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Back",
                                tint = if (currentDestId != null) Cyan400 else Slate400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Library",
                                fontSize = 14.sp,
                                color = if (currentDestId == null) Cyan400 else Slate400,
                                fontWeight = if (currentDestId == null) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.clickable { currentDestId = null }
                            )
                            
                            currentPath.forEach { folder ->
                                Text(
                                    text = " > ",
                                    fontSize = 12.sp,
                                    color = Slate400
                                )
                                Text(
                                    text = folder.title,
                                    fontSize = 14.sp,
                                    color = if (currentDestId == folder.id) Cyan400 else Slate400,
                                    fontWeight = if (currentDestId == folder.id) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { currentDestId = folder.id }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))

                    // Folder List
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (currentFolders.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No subfolders",
                                        color = Slate400,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            items(currentFolders) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { currentDestId = folder.id }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(folder.iconBg, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = folder.iconTint)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = folder.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isDestInvalid) {
                            Text(
                                "Cannot move a folder into itself or its own subfolders",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel", color = Slate400)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onMoveConfirm(currentDestId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan400, contentColor = Color.Black),
                                shape = CircleShape,
                                enabled = !isDestInvalid
                            ) {
                                Text("Move Here", color = Color.Black)
                            }
                        }
                    }
                }
            }
        }

        if (showCreateFolderLocal) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showCreateFolderLocal = false; newFolderNameLocal = "" },
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
                        Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                            Text(text = "New Folder", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = newFolderNameLocal,
                                onValueChange = { newFolderNameLocal = it },
                                label = { Text("Folder Name") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showCreateFolderLocal = false; newFolderNameLocal = "" }) {
                                    Text("Cancel", color = Slate400)
                                }
                                TextButton(onClick = {
                                    if (newFolderNameLocal.isNotBlank()) {
                                        libraryViewModel.createFolder(newFolderNameLocal, currentDestId)
                                        showCreateFolderLocal = false
                                        newFolderNameLocal = ""
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
    }
}
