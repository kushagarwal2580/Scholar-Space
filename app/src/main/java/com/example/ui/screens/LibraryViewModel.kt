package com.example.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.os.Environment
import android.widget.Toast

@Serializable
data class PersistedLibraryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val subtitle: String = "",
    val tags: List<String> = emptyList(),
    val uriString: String? = null,
    val isFolder: Boolean = false,
    val parentId: String? = null,
    val lastAccessedAt: Long = 0L,
    val driveFileId: String? = null,
    val fileSize: Long? = null,
    val iconName: String? = null
)

@Serializable
data class Reminder(val id: String = java.util.UUID.randomUUID().toString(), val text: String = "", val time: String = "", val isNotified: Boolean = false, val isPinned: Boolean = false)

@Serializable
data class NoteItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

@Serializable
data class VoiceNote(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val uriString: String = "",
    val driveFileId: String? = null,
    val isUploaded: Boolean = false,
    val fileLengthBytes: Long = 0L,
    val durationMillis: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class StopwatchItem(
    val id: Int = 0, // Should be generated
    val title: String = "",
    val isPinned: Boolean = false,
    val startTime: Long = 0L,
    val isRunning: Boolean = false,
    val elapsedMillis: Long = 0L
)

@Serializable
data class AppStateData(
    val files: List<PersistedLibraryItem> = emptyList(),
    val dayCounters: List<DayCounter> = emptyList(),
    val timers: List<TimerItem> = emptyList(),
    val isDarkMode: Boolean = true,
    val reminders: Map<String, List<Reminder>> = emptyMap(),
    val notes: List<NoteItem> = emptyList(),
    val voiceNotes: List<VoiceNote> = emptyList(),
    val stopwatches: List<StopwatchItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val pinnedNote1Id: String? = null,
    val pinnedNote2Id: String? = null
)

@Serializable
data class ScholarSpaceSyncData(
    val email: String = "",
    val username: String? = null,
    val profilePic: String? = null,
    val phone: String? = null,
    val bio: String? = null,
    val statusMsg: String? = null,
    val appState: AppStateData = AppStateData(),
    val timestamp: Long = System.currentTimeMillis()
)

data class LibraryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color,
    val tags: List<String>,
    val uri: Uri? = null,
    val isFolder: Boolean = false,
    val parentId: String? = null,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val driveFileId: String? = null,
    val fileSize: Long? = null
)

data class FolderState(
    val currentFolderId: String?,
    val files: List<LibraryItem>
)

@Serializable
data class DayCounter(
    val id: Int = 0, 
    val title: String = "", 
    val daysLeft: Int = 0, 
    val isPinned: Boolean = false, 
    val lastNotifiedDay: Int = -1,
    val targetDateMillis: Long = 0L
)

@Serializable
data class TimerItem(val id: Int = 0, val title: String = "", val durationMinutes: Int = 0, val durationSeconds: Int = 0, val timeRemaining: Int = 0, val isRunning: Boolean = false, val isPinned: Boolean = false)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        @Volatile
        var activeInstance: LibraryViewModel? = null
    }

    private val prefs = application.getSharedPreferences("LibraryPrefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true; isLenient = true; explicitNulls = false }

    var onStateChangedListener: (() -> Unit)? = null

    var isAppInForeground = false
        private set

    private fun adjustTimersForElapsedTime() {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonStr = prefs.getString("app_state", null)
            if (jsonStr != null) {
                try {
                    val data = json.decodeFromString<AppStateData>(jsonStr)
                    val elapsedSeconds = ((System.currentTimeMillis() - data.timestamp) / 1000).toInt()
                    val calendarElapsedDays = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.Instant.ofEpochMilli(data.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                        java.time.LocalDate.now()
                    ).toInt()
                    val nowDate = java.time.LocalDate.now()

                    _timers.value = data.timers.map {
                        if (it.isRunning) {
                            val newTime = (it.timeRemaining - elapsedSeconds).coerceAtLeast(0)
                            val isStillRunning = newTime > 0
                            it.copy(timeRemaining = newTime, isRunning = isStillRunning)
                        } else {
                            it
                        }
                    }
                    _stopwatches.value = data.stopwatches.map {
                        if (it.isRunning) {
                            val elapsedSinceLastSave = System.currentTimeMillis() - data.timestamp
                            val totalElapsed = it.elapsedMillis + elapsedSinceLastSave
                            it.copy(
                                elapsedMillis = totalElapsed,
                                startTime = System.currentTimeMillis() - totalElapsed
                            )
                        } else {
                            it
                        }
                    }

                    _dayCounters.value = data.dayCounters.map {
                        if (it.targetDateMillis > 0L) {
                            val targetDate = java.time.Instant.ofEpochMilli(it.targetDateMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(nowDate, targetDate).toInt().coerceAtLeast(0)
                            it.copy(daysLeft = daysRemaining)
                        } else if (calendarElapsedDays > 0) {
                            it.copy(daysLeft = (it.daysLeft - calendarElapsedDays).coerceAtLeast(0), targetDateMillis = nowDate.plusDays((it.daysLeft - calendarElapsedDays).coerceAtLeast(0).toLong()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                        } else {
                            it.copy(targetDateMillis = nowDate.plusDays(it.daysLeft.toLong()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    fun onAppResume() {
        isAppInForeground = true
        // Comment out loadData() to avoid race condition where resuming from file picker 
        // overwrites the freshly added LibraryItem from memory with older JSON cache.
        // loadData() 
        
        // Adjust timers based on elapsed time without wiping out memory state
        adjustTimersForElapsedTime()
        
        // Cancel all active running notifications immediately
        val app = getApplication<Application>()
        _timers.value.forEach {
            if (it.isRunning) {
                com.example.ui.notifications.NotificationHelper.cancelNotification(app, 10000 + it.id)
            }
        }
        _stopwatches.value.forEach {
            if (it.isRunning) {
                com.example.ui.notifications.NotificationHelper.cancelNotification(app, it.id)
            }
        }
        
        if (_currentTab.value == "calendar") {
            clearStaticNotifications()
        }
        
        updateActiveTimersService()
    }

    fun onAppPause() {
        isAppInForeground = false
        saveData()
        
        // Show/update notifications for running items immediately so they appear instantly when minimizing
        val app = getApplication<Application>()
        _timers.value.forEach {
            if (it.isRunning) {
                com.example.ui.notifications.NotificationHelper.updateTimerNotification(app, it)
            }
        }
        _stopwatches.value.forEach {
            if (it.isRunning) {
                com.example.ui.notifications.NotificationHelper.updateStopwatchNotification(app, it)
            }
        }
        
        try {
            com.example.ui.notifications.NotificationReceiver.scheduleNextAlarm(app)
        } catch (e: Exception) {
            android.util.Log.e("LibraryViewModel", "Failed to schedule background reminder alarm on pause", e)
        }
        
        updateActiveTimersService()
    }

    fun getAppStateData(): AppStateData {
        val persistedFiles = _allFiles.value.map {
            PersistedLibraryItem(
                id = it.id,
                title = it.title,
                subtitle = it.subtitle,
                tags = it.tags,
                uriString = it.uri?.toString(),
                isFolder = it.isFolder,
                parentId = it.parentId,
                lastAccessedAt = it.lastAccessedAt,
                driveFileId = it.driveFileId,
                fileSize = it.fileSize,
                iconName = it.icon.name
            )
        }
        val defaultTimers = _timers.value.map { it.copy(timeRemaining = it.durationMinutes * 60 + it.durationSeconds, isRunning = false) }
        val defaultStopwatches = _stopwatches.value.map { it.copy(elapsedMillis = 0L, startTime = 0L, isRunning = false) }
        
        return AppStateData(
            files = persistedFiles,
            dayCounters = _dayCounters.value,
            timers = defaultTimers,
            isDarkMode = _isDarkMode.value,
            reminders = _allReminders.value,
            notes = _notes.value,
            voiceNotes = _voiceNotes.value,
            stopwatches = defaultStopwatches,
            timestamp = System.currentTimeMillis(),
            pinnedNote1Id = _pinnedNote1Id.value,
            pinnedNote2Id = _pinnedNote2Id.value
        )
    }

    fun restoreAppState(data: AppStateData, isRelogin: Boolean = false) {
        if (isRelogin) {
            _timers.value = data.timers
            _stopwatches.value = data.stopwatches
        }
        
        // We intentionally do not restore `timers` and `stopwatches` from Google Drive
        // on regular app restarts to prevent overwriting active background timers/stopwatches.
        _dayCounters.value = data.dayCounters
        _isDarkMode.value = data.isDarkMode
        _allReminders.value = data.reminders
        _notes.value = data.notes
        _voiceNotes.value = data.voiceNotes
        _pinnedNote1Id.value = data.pinnedNote1Id
        _pinnedNote2Id.value = data.pinnedNote2Id

        
        val restoredFiles = data.files.map { p ->
            val ext = p.title.substringAfterLast('.', "").lowercase()
            
            val icon = if (p.iconName != null) {
                when (p.iconName) {
                    "Filled.Image" -> Icons.Default.Image
                    "Filled.Audiotrack" -> Icons.Default.Audiotrack
                    "Filled.Movie" -> Icons.Default.Movie
                    "Filled.Description" -> Icons.Default.Description
                    "Filled.List" -> Icons.Default.List
                    "Filled.PlayArrow" -> Icons.Default.PlayArrow
                    "Filled.Folder" -> Icons.Default.Folder
                    else -> Icons.Default.Description
                }
            } else if (p.isFolder) {
                Icons.Default.Folder
            } else if (ext in listOf("png","jpg","jpeg","gif", "webp", "bmp")) {
                Icons.Default.Image
            } else if (ext in listOf("mp3", "m4a", "wav")) {
                Icons.Default.Audiotrack
            } else if (ext in listOf("mp4", "mkv", "avi")) {
                Icons.Default.Movie
            } else if (ext == "pdf") {
                Icons.Default.Description
            } else if (ext in listOf("doc", "docx", "txt", "rtf")) {
                Icons.Default.Description
            } else if (ext in listOf("xls", "xlsx", "csv")) {
                Icons.Default.List
            } else if (ext in listOf("ppt", "pptx")) {
                Icons.Default.PlayArrow
            } else {
                Icons.Default.Description
            }
            
            val rawUri = p.uriString?.let { Uri.parse(it) }
            val validatedUri = verifyUri(rawUri)

            LibraryItem(
                id = p.id,
                title = p.title,
                subtitle = p.subtitle,
                icon = icon,
                iconTint = Cyan400,
                iconBg = Cyan500.copy(alpha = 0.2f),
                tags = p.tags,
                uri = validatedUri,
                isFolder = p.isFolder,
                parentId = p.parentId,
                lastAccessedAt = if (p.lastAccessedAt > 0L) p.lastAccessedAt else System.currentTimeMillis(),
                driveFileId = p.driveFileId,
                fileSize = p.fileSize
            )
        }
        _allFiles.value = restoredFiles
        saveData()
    }

    private val _currentTab = MutableStateFlow("dashboard")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()
    private val tabBackStack = java.util.Stack<String>()
    private val _canNavigateBackTab = MutableStateFlow(false)
    val canNavigateBackTab: StateFlow<Boolean> = _canNavigateBackTab.asStateFlow()

    private fun updateCanNavigateBackTab() {
        _canNavigateBackTab.value = !tabBackStack.isEmpty()
    }

    private val _isEditingNote = MutableStateFlow(false)
    val isEditingNote: StateFlow<Boolean> = _isEditingNote.asStateFlow()

    fun setEditingNote(editing: Boolean) {
        _isEditingNote.value = editing
    }

    private val _openNewNoteDirectly = MutableStateFlow(false)
    val openNewNoteDirectly: StateFlow<Boolean> = _openNewNoteDirectly.asStateFlow()

    fun triggerOpenNewNoteDirectly() {
        _openNewNoteDirectly.value = true
        setCurrentTab("notes")
    }

    fun clearOpenNewNoteDirectly() {
        _openNewNoteDirectly.value = false
    }

    private val _openNoteIdDirectly = MutableStateFlow<String?>(null)
    val openNoteIdDirectly: StateFlow<String?> = _openNoteIdDirectly.asStateFlow()

    fun triggerOpenNoteDirectly(noteId: String) {
        _openNoteIdDirectly.value = noteId
        setCurrentTab("notes")
    }

    fun clearOpenNoteDirectly() {
        _openNoteIdDirectly.value = null
    }

    fun createNoteDocxFile(file: File, title: String, content: String) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            // [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            val contentTypesBytes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".toByteArray(Charsets.UTF_8)
            zos.write(contentTypesBytes)
            zos.closeEntry()

            // _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            val relsBytes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            zos.write(relsBytes)
            zos.closeEntry()

            // word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            val xmlEscapedTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
            val xmlEscapedContent = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;").replace("\n", "</w:t><w:br/><w:t>")
            val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p>
      <w:pPr>
        <w:pStyle w:val="Heading1"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:sz w:val="36"/>
          <w:szCs w:val="36"/>
          <w:bold/>
        </w:rPr>
        <w:t>${xmlEscapedTitle}</w:t>
      </w:r>
    </w:p>
    <w:p/>
    <w:p>
      <w:r>
        <w:t>${xmlEscapedContent}</w:t>
      </w:r>
    </w:p>
  </w:body>
</w:document>""".trimIndent()
            zos.write(docXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    fun saveNoteToDevice(context: Context, note: NoteItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                val safeTitle = note.title.trim().ifBlank { "Untitled Note" }.replace("[\\/?:*\"<>|]".toRegex(), "_")
                val destFile = File(downloadsDir, "$safeTitle.docx")
                
                createNoteDocxFile(destFile, note.title.ifBlank { "Untitled Note" }, note.content)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved note as Word file to Downloads ($safeTitle.docx)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shareNote(context: Context, note: NoteItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sharedTempDir = File(context.filesDir, "shared_temp")
                if (sharedTempDir.exists()) {
                    sharedTempDir.deleteRecursively()
                }
                sharedTempDir.mkdirs()
                
                val safeTitle = note.title.trim().ifBlank { "Untitled Note" }.replace("[\\/?:*\"<>|]".toRegex(), "_")
                val tempFile = File(sharedTempDir, "$safeTitle.docx")
                
                createNoteDocxFile(tempFile, note.title.ifBlank { "Untitled Note" }, note.content)
                
                val sharedUri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", tempFile
                )
                
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        putExtra(Intent.EXTRA_STREAM, sharedUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Note"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error sharing note: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun shareMultipleItems(context: Context, itemIds: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sharedTempDir = File(context.filesDir, "shared_temp")
                if (sharedTempDir.exists()) {
                    sharedTempDir.deleteRecursively()
                }
                sharedTempDir.mkdirs()
                
                val uris = java.util.ArrayList<android.net.Uri>()
                
                itemIds.forEach { id ->
                    val note = _notes.value.find { it.id == id }
                    if (note != null) {
                        val safeTitle = note.title.trim().ifBlank { "Untitled Note" }.replace("[\\/?:*\"<>|]".toRegex(), "_")
                        val tempFile = File(sharedTempDir, "$safeTitle.docx")
                        createNoteDocxFile(tempFile, note.title.ifBlank { "Untitled Note" }, note.content)
                        val sharedUri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", tempFile
                        )
                        uris.add(sharedUri)
                    } else {
                        val voiceNote = _voiceNotes.value.find { it.id == id }
                        if (voiceNote != null) {
                            val uriStr = voiceNote.uriString
                            if (uriStr.isNotEmpty()) {
                                val uri = android.net.Uri.parse(uriStr)
                                val path = uri.path
                                if (path != null) {
                                    val localFile = File(path)
                                    if (localFile.exists()) {
                                        val rawTitle = voiceNote.title.trim().ifBlank { "Voice Note" }
                                        val titleWithoutExt = if (rawTitle.endsWith(".m4a", ignoreCase = true)) {
                                            rawTitle.substring(0, rawTitle.length - 4)
                                        } else {
                                            rawTitle
                                        }
                                        val safeTitle = titleWithoutExt.replace("[\\/?:*\"<>|]".toRegex(), "_")
                                        val tempFile = File(sharedTempDir, "$safeTitle.m4a")
                                        localFile.copyTo(tempFile, overwrite = true)
                                        val sharedUri = androidx.core.content.FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", tempFile
                                        )
                                        uris.add(sharedUri)
                                    }
                                }
                            }
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (uris.isEmpty()) return@withContext
                    
                    if (uris.size == 1) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "*/*"
                            putExtra(Intent.EXTRA_STREAM, uris.first())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Note"))
                    } else {
                        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Notes"))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error sharing items: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveMultipleItems(context: Context, itemIds: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                var savedCount = 0
                itemIds.forEach { id ->
                    val note = _notes.value.find { it.id == id }
                    if (note != null) {
                        val safeTitle = note.title.trim().ifBlank { "Untitled Note" }.replace("[\\/?:*\"<>|]".toRegex(), "_")
                        val destFile = File(downloadsDir, "$safeTitle.docx")
                        createNoteDocxFile(destFile, note.title.ifBlank { "Untitled Note" }, note.content)
                        savedCount++
                    } else {
                        val voiceNote = _voiceNotes.value.find { it.id == id }
                        if (voiceNote != null) {
                            val uriStr = voiceNote.uriString
                            if (uriStr.isNotEmpty()) {
                                val uri = android.net.Uri.parse(uriStr)
                                val path = uri.path
                                if (path != null) {
                                    val localFile = File(path)
                                    if (localFile.exists()) {
                                        val rawTitle = voiceNote.title.trim().ifBlank { "Voice Note" }
                                        val titleWithoutExt = if (rawTitle.endsWith(".m4a", ignoreCase = true)) {
                                            rawTitle.substring(0, rawTitle.length - 4)
                                        } else {
                                            rawTitle
                                        }
                                        val safeTitle = titleWithoutExt.replace("[\\/?:*\"<>|]".toRegex(), "_")
                                        val destFile = File(downloadsDir, "$safeTitle.m4a")
                                        localFile.copyTo(destFile, overwrite = true)
                                        savedCount++
                                    }
                                }
                            }
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved $savedCount items to Downloads", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save items: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val _dayCounters = MutableStateFlow<List<DayCounter>>(emptyList())
    val dayCounters: StateFlow<List<DayCounter>> = _dayCounters.asStateFlow()

    private val _timers = MutableStateFlow<List<TimerItem>>(emptyList())
    val timers: StateFlow<List<TimerItem>> = _timers.asStateFlow()
    
    private val _hasAcknowledgedBackgroundRun = MutableStateFlow(prefs.getBoolean("hasAcknowledgedBackgroundRun", false))
    val hasAcknowledgedBackgroundRun: StateFlow<Boolean> = _hasAcknowledgedBackgroundRun.asStateFlow()

    fun acknowledgeBackgroundRun() {
        prefs.edit().putBoolean("hasAcknowledgedBackgroundRun", true).apply()
        _hasAcknowledgedBackgroundRun.value = true
    }

    private fun updateActiveTimersService() {
        val hasRunning = _timers.value.any { it.isRunning && it.timeRemaining > 0 } || _stopwatches.value.any { it.isRunning }
        val app = getApplication<Application>()
        val shouldServiceRun = hasRunning && !isAppInForeground
        
        if (shouldServiceRun) {
            if (!com.example.services.ActiveTimersService.isServiceRunning) {
                val serviceIntent = android.content.Intent(app, com.example.services.ActiveTimersService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    app.startForegroundService(serviceIntent)
                } else {
                    app.startService(serviceIntent)
                }
            }
        } else {
            val serviceIntent = android.content.Intent(app, com.example.services.ActiveTimersService::class.java)
            app.stopService(serviceIntent)
            com.example.ui.notifications.NotificationHelper.cancelNotification(app, com.example.services.ActiveTimersService.NOTIFICATION_ID)
        }
    }

    private val _allFiles = MutableStateFlow<List<LibraryItem>>(emptyList())
    val allFiles: StateFlow<List<LibraryItem>> = _allFiles.asStateFlow()



    private val _allReminders = MutableStateFlow<Map<String, List<Reminder>>>(emptyMap())
    val allReminders: StateFlow<Map<String, List<Reminder>>> = _allReminders.asStateFlow()

    private val _notes = MutableStateFlow<List<NoteItem>>(emptyList())
    val notes: StateFlow<List<NoteItem>> = _notes.asStateFlow()

    private val _voiceNotes = MutableStateFlow<List<VoiceNote>>(emptyList())
    val voiceNotes: StateFlow<List<VoiceNote>> = _voiceNotes.asStateFlow()

    private val _stopwatches = MutableStateFlow<List<StopwatchItem>>(emptyList())
    val stopwatches: StateFlow<List<StopwatchItem>> = _stopwatches.asStateFlow()

    private val _pinnedNote1Id = MutableStateFlow<String?>(null)
    val pinnedNote1Id: StateFlow<String?> = _pinnedNote1Id.asStateFlow()

    private val _pinnedNote2Id = MutableStateFlow<String?>(null)
    val pinnedNote2Id: StateFlow<String?> = _pinnedNote2Id.asStateFlow()

    fun addNote(title: String, content: String) {
        val newNote = NoteItem(title = title, content = content)
        _notes.value = _notes.value + newNote
        saveData()
    }

    fun updateNote(id: String, title: String, content: String) {
        var changed = false
        _notes.value = _notes.value.map {
            if (it.id == id && (it.title != title || it.content != content)) {
                changed = true
                it.copy(title = title, content = content, createdAt = System.currentTimeMillis())
            } else {
                it
            }
        }
        if (changed) {
            saveData()
        }
    }

    fun addVoiceNote(title: String, uri: Uri, driveViewModel: DriveViewModel? = null, context: android.content.Context? = null) {
        val id = java.util.UUID.randomUUID().toString()
        var duration = 0L
        if (context != null) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                val path = uri.path
                if (uri.scheme != "content" && path != null) {
                    retriever.setDataSource(path)
                } else {
                    retriever.setDataSource(context, uri)
                }
                val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = time?.toLongOrNull() ?: 0L
                retriever.release()
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Failed to retrieve duration in addVoiceNote", e)
            }
        }
        val voiceNote = VoiceNote(id = id, title = title, uriString = uri.toString(), durationMillis = duration)
        _voiceNotes.value = _voiceNotes.value + voiceNote
        saveData()
        if (driveViewModel != null && context != null) {
            driveViewModel.uploadFileToDrive(context, uri, "audio/mp4", "$title.m4a", id, this, true, voiceNote.createdAt)
        }
    }

    fun addVoiceNotes(notes: List<VoiceNote>) {
        val currentIds = _voiceNotes.value.map { it.id }.toSet()
        val newNotes = notes.filter { it.id !in currentIds }
        if (newNotes.isNotEmpty()) {
            _voiceNotes.value = _voiceNotes.value + newNotes
            saveData()
        }
    }

    fun updateVoiceNoteDriveId(id: String, driveId: String) {
        _voiceNotes.value = _voiceNotes.value.map {
            if (it.id == id) it.copy(driveFileId = driveId) else it
        }
        saveData()
    }
    
    fun updateVoiceNoteUri(id: String, uri: Uri) {
        _voiceNotes.value = _voiceNotes.value.map {
            if (it.id == id) it.copy(uriString = uri.toString()) else it
        }
        saveData()
    }

    fun deleteVoiceNote(id: String) {
        _voiceNotes.value = _voiceNotes.value.filter { it.id != id }
        if (_pinnedNote1Id.value == id) _pinnedNote1Id.value = null
        if (_pinnedNote2Id.value == id) _pinnedNote2Id.value = null
        saveData()
    }

    fun renameVoiceNote(id: String, newTitle: String) {
        _voiceNotes.value = _voiceNotes.value.map {
            if (it.id == id) it.copy(title = newTitle) else it
        }
        saveData()
    }

    fun addStopwatch(title: String) {
        val newId = (_stopwatches.value.maxOfOrNull { it.id } ?: 0) + 1
        _stopwatches.value = _stopwatches.value + StopwatchItem(id = newId, title = title)
        saveData()
    }

    fun removeStopwatch(id: Int) {
        _stopwatches.value = _stopwatches.value.filter { it.id != id }
        saveData()
    }
    
    fun toggleStopwatch(id: Int) {
        val now = System.currentTimeMillis()
        _stopwatches.value = _stopwatches.value.map {
            if (it.id == id) {
                if (it.isRunning) {
                   it.copy(isRunning = false, elapsedMillis = now - it.startTime)
                } else {
                   it.copy(isRunning = true, startTime = now - it.elapsedMillis)
                }
            } else it
        }
        val updated = _stopwatches.value.find { it.id == id }
        if (updated != null) {
            val app = getApplication<Application>()
            if (isAppInForeground) {
                com.example.ui.notifications.NotificationHelper.cancelNotification(app, id)
            } else {
                com.example.ui.notifications.NotificationHelper.updateStopwatchNotification(app, updated)
            }
        }
        updateActiveTimersService()
        saveData()
    }
    
    fun resetStopwatch(id: Int) {
        _stopwatches.value = _stopwatches.value.map {
            if (it.id == id) it.copy(startTime = 0L, isRunning = false, elapsedMillis = 0L) else it
        }
        val app = getApplication<Application>()
        com.example.ui.notifications.NotificationHelper.cancelNotification(app, id)
        saveData()
    }

    fun togglePinStopwatch(id: Int) {
        val target = _stopwatches.value.find { it.id == id } ?: return
        val newPinnedState = !target.isPinned
        unpinAllEvents()
        if (newPinnedState) {
            _stopwatches.value = _stopwatches.value.map {
                if (it.id == id) it.copy(isPinned = true) else it
            }
        }
        saveData()
    }

    fun deleteNote(id: String) {
        _notes.value = _notes.value.filter { it.id != id }
        if (_pinnedNote1Id.value == id) _pinnedNote1Id.value = null
        if (_pinnedNote2Id.value == id) _pinnedNote2Id.value = null
        saveData()
    }

    fun pinNoteToSlot(slot: Int, noteId: String?) {
        if (slot == 1) {
            _pinnedNote1Id.value = noteId
        } else if (slot == 2) {
            _pinnedNote2Id.value = noteId
        }
        // sync isPinned for backward-compatibility
        _notes.value = _notes.value.map { note ->
            val shouldBePinned = (note.id == _pinnedNote1Id.value || note.id == _pinnedNote2Id.value)
            note.copy(isPinned = shouldBePinned)
        }
        saveData()
    }

    fun togglePinNote(id: String) {
        _notes.value = _notes.value.map {
            if (it.id == id) it.copy(isPinned = !it.isPinned) else it
        }
        saveData()
    }

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _resetDashboardTrigger = MutableStateFlow(0L)
    val resetDashboardTrigger: StateFlow<Long> = _resetDashboardTrigger.asStateFlow()

    val triggerScan = MutableStateFlow(0L)
    val triggerUpload = MutableStateFlow(0L)
    val triggerVoiceRecorder = MutableStateFlow(0L)
    val showCreateFolderDialog = MutableStateFlow(false)
    val isFabExpanded = MutableStateFlow(false)
    val isSearchActive = MutableStateFlow(false)

    private val _resetLibraryTrigger = MutableStateFlow(0L)
    val resetLibraryTrigger: StateFlow<Long> = _resetLibraryTrigger.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        activeInstance = this
        loadData()
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                tickTimers()
            }
        }
        try {
            com.example.ui.notifications.NotificationReceiver.scheduleNextAlarm(application)
        } catch (e: Exception) {
            android.util.Log.e("LibraryViewModel", "Failed to schedule background reminder alarm", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (activeInstance == this) {
            activeInstance = null
        }
    }

    fun getLastModifiedLocally(): Long {
        val jsonStr = prefs.getString("app_state", null) ?: return 0L
        return try {
            json.decodeFromString<AppStateData>(jsonStr).timestamp
        } catch (e: Exception) {
            0L
        }
    }

    private fun saveData() {
        try {
            val persistedFiles = _allFiles.value.map {
                PersistedLibraryItem(
                    id = it.id,
                    title = it.title,
                    subtitle = it.subtitle,
                    tags = it.tags,
                    uriString = it.uri?.toString(),
                    isFolder = it.isFolder,
                    parentId = it.parentId,
                    lastAccessedAt = it.lastAccessedAt,
                    driveFileId = it.driveFileId,
                    fileSize = it.fileSize,
                    iconName = it.icon.name
                )
            }
            val savedStopwatches = _stopwatches.value.map {
                if (it.isRunning) {
                    it.copy(elapsedMillis = System.currentTimeMillis() - it.startTime)
                } else {
                    it
                }
            }
            val data = AppStateData(
                files = persistedFiles,
                dayCounters = _dayCounters.value,
                timers = _timers.value,
                isDarkMode = _isDarkMode.value,
                reminders = _allReminders.value,
                notes = _notes.value,
                voiceNotes = _voiceNotes.value,
                stopwatches = savedStopwatches,
                timestamp = System.currentTimeMillis(),
                pinnedNote1Id = _pinnedNote1Id.value,
                pinnedNote2Id = _pinnedNote2Id.value
            )
            prefs.edit().putString("app_state", json.encodeToString(data)).apply()
            onStateChangedListener?.invoke()
            try {
                com.example.ui.notifications.NotificationReceiver.scheduleNextAlarm(getApplication())
            } catch (ex: Exception) {
                android.util.Log.e("LibraryViewModel", "Error rescheduling alarm on save", ex)
            }
        } catch (e: Exception) {
            android.util.Log.e("LibraryViewModel", "Error saving data", e)
        }
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val jsonStr = prefs.getString("app_state", null)
            if (jsonStr != null) {
                try {
                    val data = json.decodeFromString<AppStateData>(jsonStr)
                    
                    // Adjust timers for elapsed time
                    val elapsedSeconds = ((System.currentTimeMillis() - data.timestamp) / 1000).toInt()
                    val elapsedDays = elapsedSeconds / (24 * 3600)
                    
                    val adjustedTimers = data.timers.map {
                        if (it.isRunning) {
                            val newTime = (it.timeRemaining - elapsedSeconds).coerceAtLeast(0)
                            val isStillRunning = newTime > 0
                            if (!isStillRunning && it.timeRemaining > 0) {
                                // If it finished while we were away
                                showNotification("Timer Complete", "Your ${it.durationMinutes}-minute timer has finished.")
                            }
                            it.copy(timeRemaining = newTime, isRunning = isStillRunning)
                        } else {
                            it
                        }
                    }
                    
                    val savedDate = java.time.Instant.ofEpochMilli(data.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    val nowDate = java.time.LocalDate.now()
                    val calendarElapsedDays = java.time.temporal.ChronoUnit.DAYS.between(savedDate, nowDate).toInt()
                    
                    val adjustedDayCounters = data.dayCounters.map {
                        if (it.targetDateMillis > 0L) {
                            val targetDate = java.time.Instant.ofEpochMilli(it.targetDateMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(nowDate, targetDate).toInt().coerceAtLeast(0)
                            it.copy(daysLeft = daysRemaining)
                        } else if (calendarElapsedDays > 0) {
                            it.copy(daysLeft = (it.daysLeft - calendarElapsedDays).coerceAtLeast(0), targetDateMillis = nowDate.plusDays((it.daysLeft - calendarElapsedDays).coerceAtLeast(0).toLong()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                        } else {
                            it.copy(targetDateMillis = nowDate.plusDays(it.daysLeft.toLong()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                        }
                    }
                    
                    val restoredFiles = data.files.map { p ->
                        val ext = p.title.substringAfterLast('.', "").lowercase()
                        
                        val icon = if (p.iconName != null) {
                            when (p.iconName) {
                                "Filled.Image" -> Icons.Default.Image
                                "Filled.Audiotrack" -> Icons.Default.Audiotrack
                                "Filled.Movie" -> Icons.Default.Movie
                                "Filled.Description" -> Icons.Default.Description
                                "Filled.List" -> Icons.Default.List
                                "Filled.PlayArrow" -> Icons.Default.PlayArrow
                                "Filled.Folder" -> Icons.Default.Folder
                                else -> Icons.Default.Description
                            }
                        } else if (p.isFolder) {
                            Icons.Default.Folder
                        } else if (ext in listOf("png","jpg","jpeg","gif", "webp", "bmp")) {
                            Icons.Default.Image
                        } else if (ext in listOf("mp3", "m4a", "wav")) {
                            Icons.Default.Audiotrack
                        } else if (ext in listOf("mp4", "mkv", "avi")) {
                            Icons.Default.Movie
                        } else if (ext == "pdf") {
                            Icons.Default.Description
                        } else if (ext in listOf("doc", "docx", "txt", "rtf")) {
                            Icons.Default.Description
                        } else if (ext in listOf("xls", "xlsx", "csv")) {
                            Icons.Default.List
                        } else if (ext in listOf("ppt", "pptx")) {
                            Icons.Default.PlayArrow
                        } else {
                            Icons.Default.Description
                        }
                        
                        val rawUri = p.uriString?.let { Uri.parse(it) }
                        val validatedUri = verifyUri(rawUri)

                        LibraryItem(
                            id = p.id,
                            title = p.title,
                            subtitle = p.subtitle,
                            icon = icon,
                            iconTint = Cyan400,
                            iconBg = Cyan500.copy(alpha = 0.2f),
                            tags = p.tags,
                            uri = validatedUri,
                            isFolder = p.isFolder,
                            parentId = p.parentId,
                            lastAccessedAt = if (p.lastAccessedAt > 0L) p.lastAccessedAt else System.currentTimeMillis(),
                            driveFileId = p.driveFileId,
                            fileSize = p.fileSize
                        )
                    }
                    
                    val adjustedStopwatches = data.stopwatches.map {
                        if (it.isRunning) {
                            val elapsedSinceSave = System.currentTimeMillis() - data.timestamp
                            val totalElapsed = it.elapsedMillis + elapsedSinceSave
                            it.copy(
                                elapsedMillis = totalElapsed,
                                startTime = System.currentTimeMillis() - totalElapsed
                            )
                        } else {
                            it
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _timers.value = adjustedTimers
                        _dayCounters.value = adjustedDayCounters
                        _isDarkMode.value = data.isDarkMode
                        _allReminders.value = data.reminders
                        _notes.value = data.notes
                        _pinnedNote1Id.value = data.pinnedNote1Id
                        _pinnedNote2Id.value = data.pinnedNote2Id
                        _stopwatches.value = adjustedStopwatches
                        _voiceNotes.value = data.voiceNotes
                        _allFiles.value = restoredFiles
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LibraryViewModel", "Error loading data", e)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _allFiles.value = emptyList()
                }
            }
        }
    }

    fun addTimer(title: String, hours: Int, minutes: Int, seconds: Int) {
        val totalSeconds = hours * 3600 + minutes * 60 + seconds
        val newId = (_timers.value.maxOfOrNull { it.id } ?: 0) + 1
        _timers.value = _timers.value + TimerItem(newId, title, minutes + hours * 60, seconds, totalSeconds, false)
        saveData()
    }

    fun removeTimer(id: Int) {
        _timers.value = _timers.value.filter { it.id != id }
        saveData()
    }
    
    fun toggleTimer(id: Int) {
        _timers.value = _timers.value.map {
            if (it.id == id) it.copy(isRunning = !it.isRunning) else it
        }
        val updated = _timers.value.find { it.id == id }
        if (updated != null) {
            val app = getApplication<Application>()
            if (isAppInForeground) {
                com.example.ui.notifications.NotificationHelper.cancelNotification(app, 10000 + id)
            } else {
                com.example.ui.notifications.NotificationHelper.updateTimerNotification(app, updated)
            }
        }
        updateActiveTimersService()
        saveData()
    }
    
    fun resetTimer(id: Int) {
        _timers.value = _timers.value.map {
            if (it.id == id) it.copy(timeRemaining = it.durationMinutes * 60 + it.durationSeconds, isRunning = false) else it
        }
        val app = getApplication<Application>()
        com.example.ui.notifications.NotificationHelper.cancelNotification(app, 10000 + id)
        saveData()
    }

    private var lastReminderCheckTime = 0L

    fun tickTimers() {
        if (!isAppInForeground && com.example.services.ActiveTimersService.isServiceRunning) {
            return
        }
        var completedTimer = false
        _timers.value = _timers.value.map {
            if (it.isRunning && it.timeRemaining > 0) {
                val newRemaining = it.timeRemaining - 1
                val updated = if (newRemaining == 0) {
                    completedTimer = true
                    val displayName = if (it.title.isNotBlank()) it.title else "${it.durationMinutes}-minute timer"
                    showNotification("Timer Complete", "$displayName has finished.")
                    it.copy(timeRemaining = 0, isRunning = false)
                } else {
                    it.copy(timeRemaining = newRemaining)
                }
                val app = getApplication<Application>()
                if (isAppInForeground) {
                    if (updated.isRunning) {
                        com.example.ui.notifications.NotificationHelper.cancelNotification(app, 10000 + updated.id)
                    }
                } else {
                    com.example.ui.notifications.NotificationHelper.updateTimerNotification(app, updated)
                }
                updated
            }
            else if (it.isRunning && it.timeRemaining <= 0) {
                completedTimer = true
                it.copy(timeRemaining = 0, isRunning = false)
            }
            else it
        }
        if (completedTimer) saveData()

        val nowMs = System.currentTimeMillis()
        _stopwatches.value = _stopwatches.value.map {
            if (it.isRunning) {
                val updated = it.copy(elapsedMillis = nowMs - it.startTime)
                val app = getApplication<Application>()
                if (isAppInForeground) {
                    com.example.ui.notifications.NotificationHelper.cancelNotification(app, updated.id)
                } else {
                    com.example.ui.notifications.NotificationHelper.updateStopwatchNotification(app, updated)
                }
                updated
            } else it
        }

        checkRemindersAndCounters()
        updateActiveTimersService()
    }

    private fun checkRemindersAndCounters() {
        val nowMillis = System.currentTimeMillis()
        if (nowMillis - lastReminderCheckTime < 10000) return
        lastReminderCheckTime = nowMillis

        val zoneId = java.time.ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zoneId)

        var reminderChanged = false
        val newReminders = _allReminders.value.toMutableMap()
        
        _allReminders.value.forEach { (dateKey, remindersList) ->
            val dateMillis = dateKey.toLongOrNull()
            if (dateMillis != null) {
                val localDate = java.time.Instant.ofEpochMilli(dateMillis)
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate()
                
                val updatedList = remindersList.map { reminder ->
                    if (!reminder.isNotified) {
                        try {
                            val parts = reminder.time.split(" ")
                            if (parts.size == 2) {
                                val timeParts = parts[0].split(":")
                                if (timeParts.size == 2) {
                                    var hour = timeParts[0].toIntOrNull() ?: 0
                                    val minute = timeParts[1].toIntOrNull() ?: 0
                                    if (parts[1].uppercase(java.util.Locale.US) == "PM" && hour < 12) hour += 12
                                    if (parts[1].uppercase(java.util.Locale.US) == "AM" && hour == 12) hour = 0
                                    
                                    val scheduledDateTime = localDate.atTime(hour, minute).atZone(zoneId)
                                    if (!now.isBefore(scheduledDateTime)) {
                                        showNotification("Reminder", reminder.text)
                                        reminderChanged = true
                                        return@map reminder.copy(isNotified = true)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("LibraryViewModel", "Error parsing reminder time", e)
                        }
                    }
                    reminder
                }
                if (updatedList != remindersList) {
                    newReminders[dateKey] = updatedList
                    reminderChanged = true
                }
            }
        }
        
        if (reminderChanged) {
            _allReminders.value = newReminders
            saveData()
        }
        
        var counterChanged = false
        val newCounters = _dayCounters.value.map { counter ->
            val targetDate = java.time.Instant.ofEpochMilli(if (counter.targetDateMillis > 0L) counter.targetDateMillis else now.toInstant().toEpochMilli()).atZone(zoneId).toLocalDate()
            val computedDaysLeft = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), targetDate).toInt().coerceAtLeast(0)
            
            val daysChanged = computedDaysLeft != counter.daysLeft
            val updatedCounter = if (daysChanged) {
                counterChanged = true
                counter.copy(daysLeft = computedDaysLeft)
            } else {
                counter
            }
            
            if (updatedCounter.daysLeft > 0 && updatedCounter.lastNotifiedDay != now.dayOfYear && now.hour >= 9) {
                showNotification("Day Counter", "${updatedCounter.daysLeft} days left for ${updatedCounter.title}")
                counterChanged = true
                updatedCounter.copy(lastNotifiedDay = now.dayOfYear)
            } else {
                updatedCounter
            }
        }
        if (counterChanged) {
            _dayCounters.value = newCounters
            saveData()
        }
    }

    private fun showNotification(title: String, message: String) {
        val app = getApplication<Application>()
        com.example.ui.notifications.NotificationHelper.showCustomNotification(app, title, message)
    }

    fun clearStaticNotifications() {
        val app = getApplication<Application>()
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.cancel(30001) // Day Counter
        manager.cancel(30002) // Reminder
        manager.cancel(30003) // Timer Complete
    }

    fun clearAllNotifications() {
        val app = getApplication<Application>()
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.cancelAll()
        clearStaticNotifications()
    }

    fun addDayCounter(title: String, daysLeft: Int) {
        val newId = (_dayCounters.value.maxOfOrNull { it.id } ?: 0) + 1
        val targetDateMillis = java.time.LocalDate.now().plusDays(daysLeft.toLong()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        _dayCounters.value = _dayCounters.value + DayCounter(newId, title, daysLeft, targetDateMillis = targetDateMillis)
        saveData()
    }

    fun removeDayCounter(id: Int) {
        _dayCounters.value = _dayCounters.value.filter { it.id != id }
        saveData()
    }

    private fun unpinAllEvents() {
        _dayCounters.value = _dayCounters.value.map { it.copy(isPinned = false) }
        _timers.value = _timers.value.map { it.copy(isPinned = false) }
        _stopwatches.value = _stopwatches.value.map { it.copy(isPinned = false) }
        _allReminders.value = _allReminders.value.mapValues { (_, list) -> 
            list.map { it.copy(isPinned = false) }
        }
    }

    fun togglePinDayCounter(id: Int) {
        val target = _dayCounters.value.find { it.id == id } ?: return
        val newPinnedState = !target.isPinned
        unpinAllEvents()
        if (newPinnedState) {
            _dayCounters.value = _dayCounters.value.map {
                if (it.id == id) it.copy(isPinned = true) else it
            }
        }
        saveData()
    }

    fun togglePinTimer(id: Int) {
        val target = _timers.value.find { it.id == id } ?: return
        val newPinnedState = !target.isPinned
        unpinAllEvents()
        if (newPinnedState) {
            _timers.value = _timers.value.map {
                if (it.id == id) it.copy(isPinned = true) else it
            }
        }
        saveData()
    }

    fun togglePinReminder(dateMillis: Long, reminderId: String) {
        val dateKey = dateMillis.toString()
        val list = _allReminders.value[dateKey] ?: return
        val target = list.find { it.id == reminderId } ?: return
        val newPinnedState = !target.isPinned
        unpinAllEvents()
        if (newPinnedState) {
            val current = _allReminders.value.toMutableMap()
            current[dateKey] = current[dateKey]!!.map {
                if (it.id == reminderId) it.copy(isPinned = true) else it
            }
            _allReminders.value = current
        }
        saveData()
    }

    fun setCurrentTab(tab: String) {
        val oldTab = _currentTab.value
        if (oldTab != tab) {
            if (tabBackStack.isEmpty() || tabBackStack.peek() != oldTab) {
                tabBackStack.push(oldTab)
                updateCanNavigateBackTab()
            }
        }
        _currentTab.value = tab
        if (tab == "calendar") {
            clearStaticNotifications()
        }
    }

    fun navigateBackTab(): Boolean {
        while (!tabBackStack.isEmpty()) {
            val prevTab = tabBackStack.pop()
            updateCanNavigateBackTab()
            if (prevTab != _currentTab.value) {
                _currentTab.value = prevTab
                if (prevTab == "calendar") {
                    clearStaticNotifications()
                }
                return true
            }
        }
        return false
    }

    fun triggerResetDashboard() {
        _resetDashboardTrigger.value = System.currentTimeMillis()
    }

    fun triggerResetLibrary() {
        _resetLibraryTrigger.value = System.currentTimeMillis()
        setCurrentFolderId(null)
    }

    fun toggleTheme(isDark: Boolean) {
        _isDarkMode.value = isDark
        saveData()
    }

    fun setCurrentFolderId(id: String?) {
        _currentFolderId.value = id
        if (id != null) {
            touchItem(id)
        }
    }

    fun navigateUp() {
        val currentFolder = _allFiles.value.find { it.id == _currentFolderId.value }
        _currentFolderId.value = currentFolder?.parentId
    }

    val currentFiles: StateFlow<List<LibraryItem>> = combine(_allFiles, _currentFolderId) { files, folderId ->
        files.filter { it.parentId == folderId && !it.title.equals("App Data", ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentFolderState: StateFlow<FolderState> = combine(_allFiles, _currentFolderId) { files, folderId ->
        val folderFiles = files.filter { it.parentId == folderId && !it.title.equals("App Data", ignoreCase = true) }
        FolderState(folderId, folderFiles)
    }.stateIn(viewModelScope, SharingStarted.Lazily, FolderState(null, emptyList()))

    fun createFolder(name: String, parentId: String?): String {
        val newId = UUID.randomUUID().toString()
        val newFolder = LibraryItem(
            id = newId,
            title = name,
            subtitle = "Folder",
            icon = Icons.Default.Folder,
            iconTint = Cyan400,
            iconBg = Cyan500.copy(alpha = 0.2f),
            tags = listOf(),
            isFolder = true,
            parentId = parentId
        )
        _allFiles.value = listOf(newFolder) + _allFiles.value
        saveData()
        return newId
    }

    fun addReminder(dateMillis: Long, reminder: Reminder) {
        val dateKey = dateMillis.toString()
        val current = _allReminders.value.toMutableMap()
        val list = current[dateKey]?.toMutableList() ?: mutableListOf()
        list.add(reminder)
        current[dateKey] = list
        _allReminders.value = current
        saveData()
    }

    fun removeReminder(dateMillis: Long, reminder: Reminder) {
        val dateKey = dateMillis.toString()
        val current = _allReminders.value.toMutableMap()
        val list = current[dateKey]?.toMutableList() ?: return
        list.remove(reminder)
        if (list.isEmpty()) {
            current.remove(dateKey)
        } else {
            current[dateKey] = list
        }
        _allReminders.value = current
        saveData()
    }

    fun deleteFile(id: String) {
        _allFiles.value = _allFiles.value.filter { it.id != id }
        saveData()
    }

    fun renameFile(id: String, newName: String) {
        _allFiles.value = _allFiles.value.map {
            if (it.id == id) it.copy(title = newName) else it
        }
        saveData()
    }

    fun updateDriveFileId(id: String, driveFileId: String) {
        _allFiles.value = _allFiles.value.map {
            if (it.id == id) it.copy(driveFileId = driveFileId) else it
        }
        _voiceNotes.value = _voiceNotes.value.map {
            if (it.id == id) it.copy(driveFileId = driveFileId, isUploaded = true) else it
        }
        saveData()
    }

    fun updateFileUri(id: String, uri: Uri?, context: android.content.Context? = null) {
        var updated = false
        _allFiles.value = _allFiles.value.map {
            if (it.id == id) { updated = true; it.copy(uri = uri) } else it
        }
        if (!updated) {
            _voiceNotes.value = _voiceNotes.value.map {
                if (it.id == id) {
                    var duration = it.durationMillis
                    if (context != null && uri != null && duration == 0L) {
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            val path = uri.path
                            if (uri.scheme != "content" && path != null) {
                                retriever.setDataSource(path)
                            } else {
                                retriever.setDataSource(context, uri)
                            }
                            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            duration = time?.toLongOrNull() ?: 0L
                            retriever.release()
                        } catch (e: Exception) {
                            android.util.Log.e("LibraryViewModel", "Failed to retrieve duration in updateFileUri", e)
                        }
                    }
                    it.copy(uriString = uri?.toString() ?: "", durationMillis = duration)
                } else {
                    it
                }
            }
        }
        saveData()
    }

    fun updateLastAccessedAt(id: String) {
        _allFiles.value = _allFiles.value.map {
            if (it.id == id) it.copy(lastAccessedAt = System.currentTimeMillis()) else it
        }
        saveData()
    }

    fun cleanUpOldLocalFiles(context: Context) {
        val sevenDaysInMillis = 7L * 24 * 60 * 60 * 1000
        val threshold = System.currentTimeMillis() - sevenDaysInMillis
        
        var modified = false
        _allFiles.value = _allFiles.value.map { item ->
            if (!item.isFolder && item.uri != null && item.driveFileId != null && item.lastAccessedAt < threshold) {
                // Safely delete only the internal app data file
                try {
                    val uriString = item.uri.toString()
                    if (uriString.startsWith("content://${context.packageName}")) {
                        context.contentResolver.delete(item.uri, null, null)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LibraryViewModel", "Failed to delete old file", e)
                }
                modified = true
                item.copy(uri = null)
            } else {
                item
            }
        }
        if (modified) saveData()
    }

    fun moveFileToFolder(id: String, newParentId: String?) {
        _allFiles.value = _allFiles.value.map {
            if (it.id == id) it.copy(parentId = newParentId) else it
        }
        saveData()
    }

    fun moveFilesToFolder(ids: Set<String>, newParentId: String?) {
        _allFiles.value = _allFiles.value.map {
            if (ids.contains(it.id)) it.copy(parentId = newParentId) else it
        }
        saveData()
    }

    fun getItemsInFolder(folderId: String): List<LibraryItem> {
        return _allFiles.value.filter { it.parentId == folderId }
    }

    fun getFolders(): List<LibraryItem> {
        return _allFiles.value.filter { it.isFolder && !it.title.equals("App Data", ignoreCase = true) }
    }

    fun clearFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete internal application cache and files
            listOf(
                context.filesDir,
                context.cacheDir,
                context.codeCacheDir
            ).forEach { dir ->
                if (dir.exists()) {
                    dir.listFiles()?.forEach { 
                        if (it.name != "auth_prefs.xml") {
                             it.deleteRecursively()
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                val app = getApplication<android.app.Application>()
                _timers.value.forEach {
                    com.example.ui.notifications.NotificationHelper.cancelNotification(app, 10000 + it.id)
                }
                _stopwatches.value.forEach {
                    com.example.ui.notifications.NotificationHelper.cancelNotification(app, it.id)
                }
                updateActiveTimersService()

                _allFiles.value = emptyList()
                _timers.value = emptyList()
                _stopwatches.value = emptyList()
                _dayCounters.value = emptyList()
                _allReminders.value = emptyMap()
                _notes.value = emptyList()
                _voiceNotes.value = emptyList()
                _pinnedNote1Id.value = null
                _pinnedNote2Id.value = null
                _currentFolderId.value = null
                _viewingItem.value = null
                _isDarkMode.value = true
                _isEditingNote.value = false
                _openNewNoteDirectly.value = false
                _openNoteIdDirectly.value = null
                
                // Clear ONLY specific user preferences, NOT all shared prefs or the directory
                listOf("LibraryPrefs", "SettingsPrefs", "ScholarSpacePrefs", "auth_prefs").forEach { name ->
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                }
                
                // Clear User Database
                context.deleteDatabase("user_database")
                
                // Do NOT call saveData() here to avoid recreating cleared files.
            }
        }
    }
    
    fun getFiles(): List<LibraryItem> {
        return _allFiles.value
    }
    
    fun addLibraryItems(items: List<LibraryItem>) {
        val current = _allFiles.value.toMutableList()
        var changed = false
        for (item in items) {
            if (current.none { it.title == item.title && !it.isFolder }) {
                current.add(0, item)
                changed = true
            }
        }
        if (changed) {
            _allFiles.value = current
            saveData()
        }
    }

    fun addTextFile(text: String) {
        val title = if (text.length > 20) text.take(20) + "..." else text
        val newItem = LibraryItem(
            id = UUID.randomUUID().toString(),
            title = "Shared Text: $title",
            subtitle = "Just now",
            icon = Icons.Default.Description,
            iconTint = Cyan400,
            iconBg = Cyan500.copy(alpha = 0.2f),
            tags = listOf("Text", "Shared"),
            parentId = _currentFolderId.value
        )
        _allFiles.value = listOf(newItem) + _allFiles.value
        saveData()
    }

    fun addFileFromBitmap(context: Context, bitmap: android.graphics.Bitmap) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = java.io.File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    addFileFromUri(context, uri)
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Error saving bitmap", e)
            }
        }
    }

    fun addFileFromUri(context: Context, uri: Uri, overrideName: String? = null, onUpload: ((Uri, String, String, String) -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Try to guess name
            var title = overrideName ?: "Unknown File"
            var icon = Icons.Default.Description
            var ext = ""
            var finalUri = uri

            try {
                var fileSize: Long? = null
                if (overrideName == null) {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (displayNameIndex != -1) {
                                title = it.getString(displayNameIndex)
                            }
                            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIndex != -1 && !it.isNull(sizeIndex)) {
                                fileSize = it.getLong(sizeIndex)
                            }
                        }
                    }
                }
                if (title == "Unknown File") {
                    title = uri.lastPathSegment ?: "Unknown File"
                }

                ext = title.substringAfterLast('.', "")
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                if (mimeType.startsWith("image/") || ext in listOf("png","jpg","jpeg","gif")) {
                    icon = Icons.Default.Image
                } else if (mimeType.startsWith("audio/")) {
                    icon = Icons.Default.Audiotrack
                } else if (mimeType.startsWith("video/")) {
                    icon = Icons.Default.Movie
                } else if (mimeType == "application/pdf" || ext == "pdf") {
                    icon = Icons.Default.Description
                } else if (mimeType.contains("word") || mimeType.contains("document") || ext in listOf("doc", "docx", "txt", "rtf")) {
                    icon = Icons.Default.Description
                } else if (mimeType.contains("spreadsheet") || mimeType.contains("excel") || ext in listOf("xls", "xlsx", "csv")) {
                    icon = Icons.Default.List
                } else if (mimeType.contains("presentation") || mimeType.contains("powerpoint") || ext in listOf("ppt", "pptx")) {
                    icon = Icons.Default.PlayArrow
                } else {
                    icon = Icons.Default.Description
                }

                // Copy to local app storage to persist data
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val safeTitle = title.replace("/", "_").replace("\\", "_")
                    val file = java.io.File(context.filesDir, "${UUID.randomUUID()}_$safeTitle")
                    val outputStream = java.io.FileOutputStream(file)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    finalUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    if (fileSize == null) {
                        fileSize = file.length()
                    }
                }
                
                val newItem = LibraryItem(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    subtitle = "",
                    icon = icon,
                    iconTint = Cyan400,
                    iconBg = Cyan500.copy(alpha = 0.2f),
                    tags = emptyList(),
                    uri = finalUri,
                    parentId = _currentFolderId.value,
                    fileSize = fileSize
                )
                _allFiles.value = listOf(newItem) + _allFiles.value
                saveData()

                onUpload?.invoke(finalUri, mimeType, title, newItem.id)

            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Error processing file uri", e)
            }
        }
    }
    
    fun saveToDevice(context: Context, item: LibraryItem, driveViewModel: DriveViewModel? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                if (item.isFolder) {
                    val destZip = File(downloadsDir, "${item.title}.zip")
                    ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                        zipFolderRecursive(context, item, "", zos)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved folder to Downloads (${item.title}.zip)", Toast.LENGTH_SHORT).show()
                    }
                } else if (item.uri != null) {
                    val uriName = item.uri?.lastPathSegment ?: ""
                    val cleanUriName = if (uriName.length > 37 && uriName[36] == '_') {
                        uriName.substring(37)
                    } else {
                        uriName
                    }
                    val name = if (item.title.contains(".")) {
                        item.title
                    } else if (cleanUriName.contains(".")) {
                        cleanUriName
                    } else {
                        item.title
                    }
                    val destFile = File(downloadsDir, name)
                    context.contentResolver.openInputStream(item.uri!!)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved file to Downloads ($name)", Toast.LENGTH_SHORT).show()
                    }
                } else if (item.driveFileId != null && driveViewModel != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Downloading file to save...", Toast.LENGTH_SHORT).show()
                    }
                    driveViewModel.downloadFileFromDrive(context, this@LibraryViewModel, item.driveFileId, item.title, item.id) {
                        val fromFiles = _allFiles.value.find { it.id == item.id }
                        val fromVoiceNotes = _voiceNotes.value.find { it.id == item.id }
                        val updatedItem = fromFiles ?: fromVoiceNotes?.let { vn ->
                            if (vn.uriString.isNotEmpty()) item.copy(uri = android.net.Uri.parse(vn.uriString)) else null
                        }
                        
                        if (updatedItem?.uri != null) {
                            saveToDevice(context, updatedItem, driveViewModel)
                        } else {
                            Toast.makeText(context, "Failed to download file.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun zipFolderRecursive(context: Context, folder: LibraryItem, pathPrefix: String, zos: ZipOutputStream) {
        val children = _allFiles.value.filter { it.parentId == folder.id }
        for (child in children) {
            val entryName = if (pathPrefix.isEmpty()) child.title else "$pathPrefix/${child.title}"
            if (child.isFolder) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
                zipFolderRecursive(context, child, entryName, zos)
            } else if (child.uri != null) {
                try {
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        zos.putNextEntry(ZipEntry(entryName))
                        input.copyTo(zos)
                        zos.closeEntry()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun shareFile(context: Context, item: LibraryItem, driveViewModel: DriveViewModel? = null) {
        if (item.isFolder) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val zipFile = File(context.filesDir, "shared_folder_${item.id}.zip")
                    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                        zipFolderRecursive(context, item, "", zos)
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", zipFile
                    )
                    withContext(Dispatchers.Main) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Folder"))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error sharing folder: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (item.uri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val sharedTempDir = File(context.filesDir, "shared_temp")
                    if (sharedTempDir.exists()) {
                        sharedTempDir.deleteRecursively()
                    }
                    sharedTempDir.mkdirs()
                    
                    val uriName = item.uri?.lastPathSegment ?: ""
                    val cleanUriName = if (uriName.length > 37 && uriName[36] == '_') {
                        uriName.substring(37)
                    } else {
                        uriName
                    }
                    val name = if (item.title.contains(".")) {
                        item.title
                    } else if (cleanUriName.contains(".")) {
                        cleanUriName
                    } else {
                        item.title
                    }
                    
                    val tempFile = File(sharedTempDir, name)
                    context.contentResolver.openInputStream(item.uri!!)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val sharedUri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", tempFile
                    )
                    
                    withContext(Dispatchers.Main) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = context.contentResolver.getType(item.uri!!) ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, sharedUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share File"))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (item.driveFileId != null && driveViewModel != null) {
            Toast.makeText(context, "Downloading file to share...", Toast.LENGTH_SHORT).show()
            driveViewModel.downloadFileFromDrive(context, this, item.driveFileId, item.title, item.id) {
                val fromFiles = _allFiles.value.find { it.id == item.id }
                val fromVoiceNotes = _voiceNotes.value.find { it.id == item.id }
                val updatedItem = fromFiles ?: fromVoiceNotes?.let { vn ->
                    if (vn.uriString.isNotEmpty()) item.copy(uri = android.net.Uri.parse(vn.uriString)) else null
                }
                
                if (updatedItem?.uri != null) {
                    shareFile(context, updatedItem, driveViewModel)
                } else {
                    Toast.makeText(context, "Failed to download file.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Mock share for default items
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Hey, check out this file: ${item.title}")
            }
            context.startActivity(Intent.createChooser(intent, "Share File text"))
        }
    }

    private fun touchItem(id: String) {
        val list = _allFiles.value.map {
            if (it.id == id) it.copy(lastAccessedAt = System.currentTimeMillis()) else it
        }
        _allFiles.value = list
        saveData()
    }

    fun removeFromRecents(id: String) {
        val list = _allFiles.value.map {
            if (it.id == id) it.copy(lastAccessedAt = 0L) else it
        }
        _allFiles.value = list
        saveData()
    }

    private val _viewingItem = MutableStateFlow<LibraryItem?>(null)
    val viewingItem: StateFlow<LibraryItem?> = _viewingItem.asStateFlow()
    
    fun setViewingItem(item: LibraryItem?) {
        _viewingItem.value = item
    }

    fun openFile(context: Context, item: LibraryItem, driveViewModel: DriveViewModel? = null, onDefaultFallback: (LibraryItem) -> Unit) {
        touchItem(item.id)
        
        val actualUri = if (item.uri != null) verifyUri(item.uri) else null
        
        if (actualUri == null && item.driveFileId != null && driveViewModel != null && !item.isFolder) {
            android.widget.Toast.makeText(context, "Downloading from Drive...", android.widget.Toast.LENGTH_SHORT).show()
            driveViewModel.downloadFileFromDrive(context, this, item.driveFileId, item.title, item.id) { success ->
                if (success) {
                    val fromFiles = _allFiles.value.find { it.id == item.id }
                    val fromVoiceNotes = _voiceNotes.value.find { it.id == item.id }
                    val updatedItem = fromFiles ?: fromVoiceNotes?.let { vn ->
                        if (vn.uriString.isNotEmpty()) item.copy(uri = android.net.Uri.parse(vn.uriString)) else null
                    }
                    
                    if (updatedItem != null) {
                        _viewingItem.value = updatedItem
                    } else {
                        android.widget.Toast.makeText(context, "Failed to open file", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "Failed to download from Drive", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } else if (actualUri != null) {
            _viewingItem.value = item.copy(uri = actualUri)
        } else {
            _viewingItem.value = item
        }
    }

    private fun verifyUri(uri: Uri?): Uri? {
        if (uri == null) return null
        return try {
            val app = getApplication<Application>()
            if (uri.scheme == "content") {
                app.contentResolver.openInputStream(uri)?.use { }
                uri
            } else if (uri.scheme == "file") {
                val path = uri.path
                if (path != null && java.io.File(path).exists()) uri else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

