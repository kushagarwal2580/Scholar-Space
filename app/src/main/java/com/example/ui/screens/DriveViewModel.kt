package com.example.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.DriveService
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import okio.source

class DriveViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("DriveAccounts", Context.MODE_PRIVATE)

    private var currentAppUserEmail: String? = null

    private val _accounts = MutableStateFlow<List<String>>(
        prefs.getStringSet("accounts", emptySet())?.toList() ?: emptyList()
    )
    val accounts: StateFlow<List<String>> = _accounts

    private val _activeAccount = MutableStateFlow<String?>(null)
    val activeAccount: StateFlow<String?> = _activeAccount

    fun setAppUserEmail(context: Context, appEmail: String?) {
        currentAppUserEmail = appEmail
        if (appEmail != null) {
            // Automatically use the app's Google account for Drive
            val linkedDriveEmail = appEmail 
            _activeAccount.value = linkedDriveEmail
            _isConnected.value = true
            fetchDriveStorage(context, linkedDriveEmail)
            setupScholarSpaceFolder(context, linkedDriveEmail)
        } else {
            _activeAccount.value = null
            _isConnected.value = false
            _storageUsage.value = 0
            _storageLimit.value = 1
            _scholarSpaceFolderId.value = null
            
            // Clear last synced account when logging out
            val prefs = context.getSharedPreferences("ScholarSpacePrefs", Context.MODE_PRIVATE)
            prefs.edit().remove("last_synced_account").apply()
        }
    }

    private val _storageUsage = MutableStateFlow<Long>(0)
    val storageUsage: StateFlow<Long> = _storageUsage

    private val _storageLimit = MutableStateFlow<Long>(1)
    val storageLimit: StateFlow<Long> = _storageLimit

    private val _isConnected = MutableStateFlow<Boolean>(_activeAccount.value != null)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _recoverableAuthIntent = MutableStateFlow<Intent?>(null)
    val recoverableAuthIntent: StateFlow<Intent?> = _recoverableAuthIntent
    
    private val _uploadingFiles = MutableStateFlow<Map<String, Float>>(emptyMap())
    val uploadingFiles: StateFlow<Map<String, Float>> = _uploadingFiles
    
    private val _downloadingFiles = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadingFiles: StateFlow<Map<String, Float>> = _downloadingFiles

    private val uploadJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val downloadJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    fun cancelUpload(fileId: String) {
        uploadJobs[fileId]?.cancel()
        uploadJobs.remove(fileId)
        _uploadingFiles.value = _uploadingFiles.value - fileId
    }

    fun cancelDownload(itemId: String) {
        downloadJobs[itemId]?.cancel()
        downloadJobs.remove(itemId)
        _downloadingFiles.value = _downloadingFiles.value - itemId
    }

    fun clearRecoverableAuthIntent() {
        _recoverableAuthIntent.value = null
    }

    init {
        val account = GoogleSignIn.getLastSignedInAccount(application)
        if (account != null && account.email != null) {
            _activeAccount.value = account.email
            _isConnected.value = true
        }

        _activeAccount.value?.let { email ->
            fetchDriveStorage(application, email)
            setupScholarSpaceFolder(application, email)
        }
    }

    // Drive Service Setup
    private val json = kotlinx.serialization.json.Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
        isLenient = true
    }
    private val driveService by lazy {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .client(OkHttpClient.Builder().addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }).build())
                .build()
            retrofit.create(DriveService::class.java)
        } catch (e: Exception) {
            Log.e("DriveViewModel", "Failed to init Retrofit", e)
            null
        }
    }

    fun getSignInIntent(context: Context): android.content.Intent {
        val webClientId = com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    fun handleSignInResult(context: Context, intent: android.content.Intent?) {
        try {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null && account.email != null) {
                handleSignInEmail(context, account.email!!)
            } else {
                android.widget.Toast.makeText(context, "Failed to get Google account", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Sign in cancelled or failed", android.widget.Toast.LENGTH_SHORT).show()
            Log.e("DriveViewModel", "Sign in failed", e)
        }
    }

    fun handleSignInEmail(context: Context, email: String?) {
        if (email != null) {
            val currentAccounts = _accounts.value.toMutableSet()
            currentAccounts.add(email)
            
            val editor = prefs.edit()
                .putStringSet("accounts", currentAccounts)
                
            if (currentAppUserEmail != null) {
                editor.putString("drive_for_$currentAppUserEmail", email)
            }
            editor.apply()
                
            _accounts.value = currentAccounts.toList()
            _activeAccount.value = email
            _isConnected.value = true
            fetchDriveStorage(context, email)
            setupScholarSpaceFolder(context, email)
        }
    }

    fun switchAccount(context: Context, email: String) {
        if (_accounts.value.contains(email)) {
            if (currentAppUserEmail != null) {
                prefs.edit().putString("drive_for_$currentAppUserEmail", email).apply()
            }
            _activeAccount.value = email
            _isConnected.value = true
            fetchDriveStorage(context, email)
            setupScholarSpaceFolder(context, email)
        }
    }

    private val _scholarSpaceFolderId = MutableStateFlow<String?>(null)
    val scholarSpaceFolderId: StateFlow<String?> = _scholarSpaceFolderId

    private fun setupScholarSpaceFolder(context: Context, email: String) {
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        android.accounts.Account(email, "com.google"),
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                
                val searchResponse = driveService?.searchFiles("Bearer $token", "name = 'Scholar Space' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                
                if (searchResponse?.files?.isNotEmpty() == true) {
                    _scholarSpaceFolderId.value = searchResponse.files[0].id
                } else {
                    val metadata = mapOf(
                        "name" to "Scholar Space",
                        "mimeType" to "application/vnd.google-apps.folder"
                    )
                    val newFolder = driveService?.createFolder("Bearer $token", metadata)
                    _scholarSpaceFolderId.value = newFolder?.id
                }
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                Log.w("DriveViewModel", "UserRecoverableAuthException in setupScholarSpaceFolder", e)
                _recoverableAuthIntent.value = e.intent
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to setup Scholar Space folder", e)
            }
        }
    }

    fun fetchDriveStorage(context: Context, email: String) {
        val prefs = context.getSharedPreferences("DriveStoragePrefs", Context.MODE_PRIVATE)
        val loadedUsage = prefs.getLong("storageUsage", -1L)
        val loadedLimit = prefs.getLong("storageLimit", -1L)
        if (loadedUsage != -1L && loadedLimit != -1L) {
            _storageUsage.value = loadedUsage
            _storageLimit.value = loadedLimit
        }

        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        android.accounts.Account(email, "com.google"),
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                
                val response = driveService?.getAbout("Bearer $token")
                if (response?.storageQuota != null) {
                    val usage = response.storageQuota.usage?.toLongOrNull() ?: 0L
                    val limit = response.storageQuota.limit?.toLongOrNull() ?: 1L
                    _storageUsage.value = usage
                    _storageLimit.value = limit
                    
                    prefs.edit().putLong("storageUsage", usage).putLong("storageLimit", limit).apply()
                } else {
                    throw IllegalStateException("Drive API response was null")
                }
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                Log.w("DriveViewModel", "UserRecoverableAuthException in fetchDriveStorage", e)
                _recoverableAuthIntent.value = e.intent
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to fetch drive storage", e)
                
                val isOnline = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).let { cm ->
                    val nw = cm.activeNetwork
                    if (nw == null) false
                    else {
                        val actNw = cm.getNetworkCapabilities(nw)
                        actNw?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    }
                }
                
                if (loadedUsage == -1L || loadedLimit == -1L) {
                    if (isOnline) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Drive API error: using placeholder storage data", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    _storageLimit.value = 15L * 1024 * 1024 * 1024 // 15 GB
                    _storageUsage.value = 3L * 1024 * 1024 * 1024 // 3 GB 
                }
            }
        }
    }

    fun uploadFileToDrive(context: Context, uri: Uri, mimeType: String, fileName: String, fileId: String? = null, libraryViewModel: LibraryViewModel? = null, inAppData: Boolean = false, createdAt: Long? = null) {
        val email = _activeAccount.value ?: return
        
        val job = viewModelScope.launch {
            if (fileId != null) {
                _uploadingFiles.value = _uploadingFiles.value + (fileId to -1f)
            }
            try {
                // Wait for folder id if not ready
                var baseFolderId = _scholarSpaceFolderId.value
                if (baseFolderId == null) {
                    setupScholarSpaceFolder(context, email)
                    kotlinx.coroutines.delay(1500)
                    baseFolderId = _scholarSpaceFolderId.value
                }
                
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        android.accounts.Account(email, "com.google"),
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                
                var parentFolderId = baseFolderId
                if (inAppData && baseFolderId != null) {
                    val appDataSearch = driveService?.searchFiles("Bearer $token", "name = 'App Data' and '$baseFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                    if (appDataSearch?.files?.isNotEmpty() == true) {
                        parentFolderId = appDataSearch.files[0].id
                    } else {
                        val appDataMetadata = com.example.data.api.DriveFile(
                            name = "App Data",
                            mimeType = "application/vnd.google-apps.folder",
                            parents = listOf(baseFolderId)
                        )
                        val newFolder = driveService?.createDriveFile("Bearer $token", appDataMetadata)
                        parentFolderId = newFolder?.id
                    }
                }
                
                withContext(Dispatchers.IO) {
                    val requestBody = object : okhttp3.RequestBody() {
                        override fun contentType() = mimeType.toMediaTypeOrNull()
                        override fun writeTo(sink: okio.BufferedSink) {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                sink.writeAll(stream.source())
                            }
                        }
                    }
                    val filePart = okhttp3.MultipartBody.Part.createFormData("file", fileName, requestBody)
                    
                    val createdTimeString = createdAt?.let {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        sdf.format(java.util.Date(it))
                    }
                    val createdTimeJson = if (createdTimeString != null) ",\n                            \"createdTime\": \"$createdTimeString\"" else ""

                    val metadataJson = """
                        {
                            "name": "$fileName",
                            "parents": ${if (parentFolderId != null) "[\"$parentFolderId\"]" else "[]"}$createdTimeJson
                        }
                    """.trimIndent()
                    val metadataBody = okhttp3.RequestBody.create("application/json; charset=UTF-8".toMediaType(), metadataJson)
                    val metadataPart = okhttp3.MultipartBody.Part.createFormData("metadata", null, metadataBody)
                    
                    val response = driveService?.uploadFile("Bearer $token", metadataPart, filePart)
                    withContext(Dispatchers.Main) {
                        if (response != null && response.id != null) {
                            if (fileId != null) {
                                libraryViewModel?.updateDriveFileId(fileId, response.id)
                            }
                            android.widget.Toast.makeText(context, "Uploaded to Drive", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Upload failed: Invalid response", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                Log.w("DriveViewModel", "UserRecoverableAuthException in uploadFileToDrive", e)
                _recoverableAuthIntent.value = e.intent
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to upload file to drive", e)
                withContext(Dispatchers.Main) {
                    val message = if (e is java.net.UnknownHostException || 
                        e is java.net.ConnectException || 
                        e is java.net.SocketTimeoutException || 
                        e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                        e.message?.contains("googleapis.com", ignoreCase = true) == true) {
                        "Files will be uploaded to drive when connected to internet"
                    } else {
                        "Upload failed: ${e.message}"
                    }
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                if (fileId != null) {
                    _uploadingFiles.value = _uploadingFiles.value - fileId
                    uploadJobs.remove(fileId)
                }
            }
        }
        if (fileId != null) {
            uploadJobs[fileId] = job
        }
    }

    fun deleteAllDataFromDrive(context: Context) {
        val email = _activeAccount.value ?: return
        val folderId = _scholarSpaceFolderId.value ?: return
        
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        android.accounts.Account(email, "com.google"),
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                
                driveService?.deleteFile("Bearer $token", folderId)
                _scholarSpaceFolderId.value = null
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                Log.w("DriveViewModel", "UserRecoverableAuthException in deleteAllDataFromDrive", e)
                _recoverableAuthIntent.value = e.intent
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to delete entire Scholar Space folder from drive", e)
            }
        }
    }

    fun downloadFileFromDrive(context: Context, libraryViewModel: LibraryViewModel, fileId: String, fileName: String, itemId: String, onComplete: (Boolean) -> Unit) {
        val email = _activeAccount.value ?: return
        val job = viewModelScope.launch {
            _downloadingFiles.value = _downloadingFiles.value + (itemId to -1f)
            try {
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        android.accounts.Account(email, "com.google"),
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                
                val responseBody = driveService?.downloadFile("Bearer $token", fileId)
                if (responseBody != null) {
                    _downloadingFiles.value = _downloadingFiles.value + (itemId to 0f)
                    val safeTitle = fileName.replace("/", "_").replace("\\", "_")
                    val localFile = java.io.File(context.filesDir, "${java.util.UUID.randomUUID()}_$safeTitle")
                    withContext(Dispatchers.IO) {
                        val inputStream = responseBody.byteStream()
                        val outputStream = java.io.FileOutputStream(localFile)
                        val totalBytes = responseBody.contentLength()
                        val buffer = ByteArray(8 * 1024)
                        var bytesCopied = 0L
                        var bytes = inputStream.read(buffer)
                        while (bytes >= 0) {
                            kotlinx.coroutines.yield()
                            outputStream.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            if (totalBytes > 0) {
                                _downloadingFiles.value = _downloadingFiles.value + (itemId to (bytesCopied.toFloat() / totalBytes))
                            }
                            bytes = inputStream.read(buffer)
                        }
                        inputStream.close()
                        outputStream.close()
                    }
                    val isVoiceNote = libraryViewModel.voiceNotes.value.any { it.id == itemId }
                    val audioExts = listOf(".m4a", ".mp4", ".mp3")
                    val isAudioFiles = isVoiceNote || audioExts.any { fileName.endsWith(it) } || fileName.replace("/", "_").replace("\\", "_").endsWith(".m4a")

                    val uri = if (isAudioFiles) {
                        android.net.Uri.fromFile(localFile)
                    } else {
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            localFile
                        )
                    }
                    
                    withContext(Dispatchers.Main) {
                        libraryViewModel.updateFileUri(itemId, uri, context)
                        onComplete(true)
                    }
                } else {
                    withContext(Dispatchers.Main) { onComplete(false) }
                }
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to download file from drive", e)
                withContext(Dispatchers.Main) { onComplete(false) }
            } finally {
                _downloadingFiles.value = _downloadingFiles.value - itemId
                downloadJobs.remove(itemId)
            }
        }
        downloadJobs[itemId] = job
    }

    fun deleteFileFromDrive(context: Context, fileName: String, driveFileId: String? = null) {
        val email = _activeAccount.value ?: return
        val parentFolderId = _scholarSpaceFolderId.value
        
        val prefs = context.getSharedPreferences("DriveOfflineDeletes", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(fileName, true).apply()
        if (driveFileId != null) {
            prefs.edit().putBoolean(driveFileId, true).apply()
        }
        
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        android.accounts.Account(email, "com.google"),
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                
                if (driveFileId != null) {
                    driveService?.deleteFile("Bearer $token", driveFileId)
                } else {
                    // Search for the file in the "Scholar Space" folder
                    val query = if (parentFolderId != null) {
                        "name = '$fileName' and '$parentFolderId' in parents and trashed = false"
                    } else {
                        "name = '$fileName' and trashed = false"
                    }
                    val searchResponse = driveService?.searchFiles("Bearer $token", query)
                    searchResponse?.files?.forEach { file ->
                        file.id?.let {
                            driveService?.deleteFile("Bearer $token", it)
                        }
                    }
                }
                prefs.edit().remove(fileName).remove(driveFileId ?: "").apply()
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                Log.w("DriveViewModel", "UserRecoverableAuthException in deleteFileFromDrive", e)
                _recoverableAuthIntent.value = e.intent
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to delete file from drive", e)
            }
        }
    }

    fun renameFileInDrive(context: Context, driveFileId: String, newName: String) {
        val email = _activeAccount.value ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                    context,
                    android.accounts.Account(email, "com.google"),
                    "oauth2:https://www.googleapis.com/auth/drive.file"
                )
                val fileMetadata = mapOf("name" to newName)
                driveService?.updateDriveFile("Bearer $token", driveFileId, fileMetadata = fileMetadata)
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to rename file in drive", e)
            }
        }
    }

    private val _isMetadataSyncing = MutableStateFlow(false)
    val isMetadataSyncing: StateFlow<Boolean> = _isMetadataSyncing

    private val _isDataSyncing = MutableStateFlow(false)
    val isDataSyncing: StateFlow<Boolean> = _isDataSyncing

    private var metadataSyncJob: kotlinx.coroutines.Job? = null

    fun triggerMetadataUpload(
        context: Context,
        authViewModel: AuthViewModel,
        libraryViewModel: LibraryViewModel
    ) {
        metadataSyncJob?.cancel()
        metadataSyncJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // Debounce for 2 seconds
            if (!_isMetadataSyncing.value) {
                syncMetadata(context, authViewModel, libraryViewModel, isUpload = true)
            } else {
                // If currently syncing, wait a bit and try again
                kotlinx.coroutines.delay(3000)
                syncMetadata(context, authViewModel, libraryViewModel, isUpload = true)
            }
        }
    }

    fun syncMetadata(
        context: Context,
        authViewModel: AuthViewModel,
        libraryViewModel: LibraryViewModel,
        isUpload: Boolean,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val email = _activeAccount.value
        if (email == null) {
            onComplete(false)
            return
        }
        
        val prefs = context.getSharedPreferences("ScholarSpacePrefs", Context.MODE_PRIVATE)
        val lastSyncedAccount = prefs.getString("last_synced_account", "")
        val isRelogin = lastSyncedAccount != email
        
        viewModelScope.launch {
            if (_isMetadataSyncing.value) {
                onComplete(false)
                return@launch
            }
            _isMetadataSyncing.value = true
            try {
                val token = withContext(Dispatchers.IO) {
                    com.google.android.gms.auth.GoogleAuthUtil.getToken(
                        context,
                        android.accounts.Account(email, "com.google"),
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                
                // 1. Ensure Scholar Space folder exists
                var scholarFolderId = _scholarSpaceFolderId.value
                if (scholarFolderId == null) {
                    val searchResponse = driveService?.searchFiles("Bearer $token", "name = 'Scholar Space' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                    if (searchResponse?.files?.isNotEmpty() == true) {
                        scholarFolderId = searchResponse.files[0].id
                        _scholarSpaceFolderId.value = scholarFolderId
                    } else {
                        val metadata = mapOf(
                            "name" to "Scholar Space",
                            "mimeType" to "application/vnd.google-apps.folder"
                        )
                        val newFolder = driveService?.createFolder("Bearer $token", metadata)
                        scholarFolderId = newFolder?.id
                        _scholarSpaceFolderId.value = scholarFolderId
                    }
                }
                
                if (scholarFolderId == null) {
                    _isMetadataSyncing.value = false
                    onComplete(false)
                    return@launch
                }
                
                // 2. Ensure App Data folder exists inside Scholar Space
                var appDataFolderId: String? = null
                val appDataSearch = driveService?.searchFiles("Bearer $token", "name = 'App Data' and '$scholarFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                if (appDataSearch?.files?.isNotEmpty() == true) {
                    appDataFolderId = appDataSearch.files[0].id
                } else {
                    val appDataMetadata = com.example.data.api.DriveFile(
                        name = "App Data",
                        mimeType = "application/vnd.google-apps.folder",
                        parents = listOf(scholarFolderId)
                    )
                    val newFolder = driveService?.createDriveFile("Bearer $token", appDataMetadata)
                    appDataFolderId = newFolder?.id
                }
                
                if (appDataFolderId == null) {
                    _isMetadataSyncing.value = false
                    onComplete(false)
                    return@launch
                }
                
                // 3. Search for scholarspace_metadata.json inside App Data
                val fileName = "scholarspace_metadata.json"
                val fileSearch = driveService?.searchFiles("Bearer $token", "name = '$fileName' and '$appDataFolderId' in parents and trashed = false")
                val driveFileId = fileSearch?.files?.firstOrNull()?.id
                
                if (isUpload) {
                    // Gather up-to-date local data
                    val appState = libraryViewModel.getAppStateData()
                    val authState = authViewModel.uiState.value
                    
                    val syncData = if (authState is AuthState.Success) {
                        ScholarSpaceSyncData(
                            email = authState.email,
                            username = authState.displayName,
                            profilePic = authState.profilePic,
                            phone = authState.phone,
                            bio = authState.bio,
                            statusMsg = authState.statusMsg,
                            appState = appState
                        )
                    } else {
                        ScholarSpaceSyncData(
                            email = email,
                            appState = appState
                        )
                    }
                    
                    val jsonString = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        json.encodeToString(ScholarSpaceSyncData.serializer(), syncData)
                    }
                    
                    // Upload JSON content
                    val requestBody = okhttp3.RequestBody.create(
                        "application/json; charset=UTF-8".toMediaTypeOrNull(),
                        jsonString
                    )
                    val filePart = okhttp3.MultipartBody.Part.createFormData("file", fileName, requestBody)
                    
                    val metadataJson = """
                        {
                            "name": "$fileName",
                            "parents": ["$appDataFolderId"]
                        }
                    """.trimIndent()
                    val metadataBody = okhttp3.RequestBody.create(
                        "application/json; charset=UTF-8".toMediaType(),
                        metadataJson
                    )
                    val metadataPart = okhttp3.MultipartBody.Part.createFormData("metadata", null, metadataBody)
                    
                    // Upload or Update JSON content
                    val response = if (driveFileId != null) {
                        try {
                            driveService?.updateFileContent("Bearer $token", driveFileId, metadataPart, filePart)
                        } catch (e: Exception) {
                            Log.w("DriveViewModel", "Could not update file, trying create instead", e)
                            driveService?.uploadFile("Bearer $token", metadataPart, filePart)
                        }
                    } else {
                        driveService?.uploadFile("Bearer $token", metadataPart, filePart)
                    }
                    if (response != null) {
                        Log.i("DriveViewModel", "Successfully uploaded user metadata & app state to Drive")
                    }
                    
                    // Upload Profile Picture if exists or delete if removed
                    if (authState is AuthState.Success) {
                        val localProfilePic = authState.profilePic
                        val profName = "profile_pic.jpg"
                        try {
                            val profSearch = driveService?.searchFiles("Bearer $token", "name = '$profName' and '$appDataFolderId' in parents and trashed = false")
                            val profDriveId = profSearch?.files?.firstOrNull()?.id

                            if (localProfilePic != null && localProfilePic.startsWith("file:")) {
                                val fileSrc = try {
                                    java.io.File(java.net.URI(localProfilePic))
                                } catch(e: Exception) {
                                    val fallbackPath = android.net.Uri.parse(localProfilePic).path
                                    if (fallbackPath != null) java.io.File(fallbackPath) else null
                                }
                                if (fileSrc != null && fileSrc.exists()) {
                                    val profReq = okhttp3.RequestBody.create("image/jpeg".toMediaTypeOrNull(), fileSrc)
                                    val profPart = okhttp3.MultipartBody.Part.createFormData("file", profName, profReq)
                                    
                                    val profMeta = """
                                        {
                                            "name": "$profName",
                                            "parents": ["$appDataFolderId"]
                                        }
                                    """.trimIndent()
                                    val profMetaBody = okhttp3.RequestBody.create("application/json; charset=UTF-8".toMediaType(), profMeta)
                                    val profMetaPart = okhttp3.MultipartBody.Part.createFormData("metadata", null, profMetaBody)
                                    
                                    if (profDriveId != null) {
                                        try {
                                            driveService?.updateFileContent("Bearer $token", profDriveId, profMetaPart, profPart)
                                        } catch (e: Exception) {
                                            driveService?.uploadFile("Bearer $token", profMetaPart, profPart)
                                        }
                                    } else {
                                        driveService?.uploadFile("Bearer $token", profMetaPart, profPart)
                                    }
                                }
                            } else if (localProfilePic.isNullOrEmpty()) {
                                if (profDriveId != null) {
                                    try { driveService?.deleteFile("Bearer $token", profDriveId) } catch(e: Exception){}
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DriveViewModel", "Failed to upload or remove profile pic", e)
                        }
                    }
                    
                    onComplete(true)
                } else {
                    // Download and restore from Drive
                    if (driveFileId != null) {
                        val responseBody = driveService?.downloadFile("Bearer $token", driveFileId)
                        val jsonString = withContext(Dispatchers.IO) {
                            responseBody?.string()
                        }
                        if (!jsonString.isNullOrBlank()) {
                            val syncData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    json.decodeFromString(ScholarSpaceSyncData.serializer(), jsonString)
                                } catch (e: Exception) {
                                    android.util.Log.e("DriveViewModel", "JSON decode error. Maybe corrupted.", e)
                                    null
                                }
                            }
                            
                            if (syncData != null) {
                                val localLastModified = libraryViewModel.getLastModifiedLocally()
                                if (!isRelogin && syncData.appState.timestamp < localLastModified) {
                                    Log.i("DriveViewModel", "Local state is newer than Drive state, uploading instead")
                                    _isMetadataSyncing.value = false
                                    syncMetadata(context, authViewModel, libraryViewModel, isUpload = true, onComplete = onComplete)
                                    return@launch
                                }
                            
                            // 1. Restore local profile info if logged in successfully
                            val currentAuth = authViewModel.uiState.value
                            var finalProfilePic = if (currentAuth is AuthState.Success) currentAuth.profilePic else null
                            if (finalProfilePic == null) {
                                val syncPic = syncData.profilePic
                                if (syncPic != null) {
                                    finalProfilePic = syncPic
                                }
                            }
                            
                            // Download Profile Picture if exists in Drive
                            var downloadedProfilePic = false
                            try {
                                val profName = "profile_pic.jpg"
                                val profSearch = driveService?.searchFiles("Bearer $token", "name = '$profName' and '$appDataFolderId' in parents and trashed = false")
                                val profDriveId = profSearch?.files?.firstOrNull()?.id
                                if (profDriveId != null) {
                                    val profResp = driveService?.downloadFile("Bearer $token", profDriveId)
                                    val bytes = profResp?.bytes()
                                    if (bytes != null) {
                                        val profFile = java.io.File(context.filesDir, "profile_pic_downloaded.jpg")
                                        val outStream = java.io.FileOutputStream(profFile)
                                        outStream.write(bytes)
                                        outStream.flush()
                                        outStream.close()
                                        finalProfilePic = profFile.toURI().toString()
                                        downloadedProfilePic = true
                                    }
                                }
                            } catch(e: Exception) { Log.e("DriveViewModel", "Failed to download profile pic", e) }
                            
                            // If it's a local file URI but we didn't just download it, ensure it actually exists
                            if (finalProfilePic != null && finalProfilePic!!.startsWith("file:") && !downloadedProfilePic) {
                                try {
                                    val f = java.io.File(java.net.URI(finalProfilePic!!))
                                    if (!f.exists()) {
                                        finalProfilePic = null
                                    }
                                } catch(e: Exception) {
                                    val fallbackPath = android.net.Uri.parse(finalProfilePic!!).path
                                    if (fallbackPath == null || !java.io.File(fallbackPath).exists()) {
                                        finalProfilePic = null
                                    }
                                }
                            }
                            
                            if (currentAuth is AuthState.Success) {
                                val currentPicNorm = currentAuth.profilePic?.replace("file:/", "file:///")?.replace("file//////", "file:///")
                                val newPicNorm = finalProfilePic?.replace("file:/", "file:///")?.replace("file//////", "file:///")
                                
                                val newNickname = syncData.username ?: currentAuth.displayName ?: "Unknown"
                                val newPhone = syncData.phone ?: currentAuth.phone
                                val newBio = syncData.bio ?: currentAuth.bio
                                val newStatusMsg = syncData.statusMsg ?: currentAuth.statusMsg
                                
                                if (newNickname != currentAuth.displayName || newBio != currentAuth.bio || newStatusMsg != currentAuth.statusMsg || currentPicNorm != newPicNorm) {
                                    authViewModel.updateProfile(
                                        nickname = newNickname,
                                        profilePic = finalProfilePic,
                                        phone = newPhone,
                                        bio = newBio,
                                        statusMsg = newStatusMsg
                                    )
                                }
                            }
                            
                            // 2. Restore app state (timers, reminders, day counters, isDarkMode, library files, etc.)
                            libraryViewModel.restoreAppState(syncData.appState, isRelogin)
                            
                            if (currentAuth is AuthState.Success) {
                                val prefs = context.getSharedPreferences("ScholarSpacePrefs", android.content.Context.MODE_PRIVATE)
                                val lastSyncedAccount = prefs.getString("last_synced_account", "")
                                
                                if (currentAuth.email != lastSyncedAccount) {
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "Synced back files, settings, timers, reminders & counters from Google Drive!", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    prefs.edit().putString("last_synced_account", currentAuth.email).apply()
                                }
                            }
                            Log.i("DriveViewModel", "Successfully downloaded and restored metadata from Drive")
                            onComplete(true)
                            } else {
                                Log.e("DriveViewModel", "Failed to decode syncData from JSON")
                                onComplete(false)
                            }
                        } else {
                            onComplete(false)
                        }
                    } else {
                        // Remote file doesn't exist yet, push the current local state up on initial log in so it initializes remote backup
                        Log.i("DriveViewModel", "No remote metadata file found, initializing with local state")
                        _isMetadataSyncing.value = false // reset flag so nested call works
                        syncMetadata(context, authViewModel, libraryViewModel, isUpload = true, onComplete = onComplete)
                    }
                }
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                Log.w("DriveViewModel", "UserRecoverableAuthException in syncMetadata", e)
                _recoverableAuthIntent.value = e.intent
                onComplete(false)
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to sync metadata with Drive", e)
                onComplete(false)
            } finally {
                _isMetadataSyncing.value = false
            }
        }
    }

    fun createFolderInDrive(context: Context, folderName: String, localParentId: String?, localFolderId: String, libraryViewModel: LibraryViewModel) {
        val email = _activeAccount.value ?: return
        viewModelScope.launch {
            try {
                // Determine Drive parent ID
                var parentFolderId = _scholarSpaceFolderId.value
                if (parentFolderId == null) {
                    setupScholarSpaceFolder(context, email)
                    kotlinx.coroutines.delay(1500)
                    parentFolderId = _scholarSpaceFolderId.value
                }
                if (localParentId != null) {
                    val pFolder = libraryViewModel.getFiles().find { it.id == localParentId }
                    if (pFolder?.driveFileId != null) {
                        parentFolderId = pFolder.driveFileId
                    }
                }
                
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        android.accounts.Account(email, "com.google"),
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                
                val metadata = mapOf(
                    "name" to folderName,
                    "mimeType" to "application/vnd.google-apps.folder",
                )
                
                // Add parents to metadata if we have one
                val folderMetadata = com.example.data.api.DriveFile(
                    name = folderName,
                    mimeType = "application/vnd.google-apps.folder",
                    parents = if (parentFolderId != null) listOf(parentFolderId) else null
                )
                
                val newFolder = driveService?.createDriveFile("Bearer $token", folderMetadata)
                if (newFolder?.id != null) {
                    libraryViewModel.updateDriveFileId(localFolderId, newFolder.id)
                }
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to create folder in drive", e)
            }
        }
    }

    fun moveFileInDrive(context: Context, fileId: String, newLocalParentId: String?, oldLocalParentId: String?, libraryViewModel: LibraryViewModel) {
        val email = _activeAccount.value ?: return
        viewModelScope.launch {
            try {
                val item = libraryViewModel.getFiles().find { it.id == fileId } ?: return@launch
                val driveFileId = item.driveFileId ?: return@launch
                
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        android.accounts.Account(email, "com.google"),
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }
                
                var newParentFolderId = _scholarSpaceFolderId.value
                if (newParentFolderId == null) {
                    setupScholarSpaceFolder(context, email)
                    kotlinx.coroutines.delay(1500)
                    newParentFolderId = _scholarSpaceFolderId.value
                }
                if (newLocalParentId != null) {
                    val pFolder = libraryViewModel.getFiles().find { it.id == newLocalParentId }
                    if (pFolder?.driveFileId != null) {
                        newParentFolderId = pFolder.driveFileId
                    }
                }
                
                var oldParentFolderId = _scholarSpaceFolderId.value
                if (oldLocalParentId != null) {
                    val pFolder = libraryViewModel.getFiles().find { it.id == oldLocalParentId }
                    if (pFolder?.driveFileId != null) {
                        oldParentFolderId = pFolder.driveFileId
                    }
                }
                
                // updateDriveFile with addParents and removeParents
                if (newParentFolderId != null) {
                    driveService?.updateDriveFile(
                        "Bearer $token", 
                        driveFileId, 
                        addParents = newParentFolderId, 
                        removeParents = oldParentFolderId,
                        fileMetadata = emptyMap()
                    )
                }
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to move file in drive", e)
            }
        }
    }

    fun syncDriveData(context: Context, libraryViewModel: LibraryViewModel, onComplete: () -> Unit = {}) {
        val email = _activeAccount.value
        val parentFolderId = _scholarSpaceFolderId.value
        
        if (email == null || parentFolderId == null) {
            onComplete()
            return
        }

        if (_isDataSyncing.value) {
            onComplete()
            return
        }

        viewModelScope.launch {
            _isDataSyncing.value = true
            try {
                val token = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        android.accounts.Account(email, "com.google"),
                        "oauth2:https://www.googleapis.com/auth/drive.file"
                    )
                }

                val searchResponse = driveService?.searchFiles(
                    "Bearer $token",
                    "'$parentFolderId' in parents and trashed = false"
                )
                
                val appDataSearch = driveService?.searchFiles(
                    "Bearer $token",
                    "name = 'App Data' and '$parentFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                )
                var appDataFolderId: String? = null
                if (appDataSearch?.files?.isNotEmpty() == true) {
                    appDataFolderId = appDataSearch.files[0].id
                }

                val appDataFilesResponse = appDataFolderId?.let {
                    driveService?.searchFiles("Bearer $token", "'$it' in parents and trashed = false")
                }
                
                val fetchedVoiceNotes = appDataFilesResponse?.files?.mapNotNull { file ->
                    val fileName = file.name ?: return@mapNotNull null
                    val existingVoiceNote = libraryViewModel.voiceNotes.value.find { it.driveFileId == file.id || it.title == fileName.removeSuffix(".m4a") }
                    if (existingVoiceNote == null && file.mimeType?.startsWith("audio/") == true) {
                        try {
                            val parsedTime = try {
                                file.createdTime?.let {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    sdf.parse(it)?.time
                                } ?: System.currentTimeMillis()
                            } catch (e: Exception) { System.currentTimeMillis() }
                            
                            com.example.ui.screens.VoiceNote(
                                id = java.util.UUID.randomUUID().toString(),
                                title = fileName.removeSuffix(".m4a"),
                                uriString = "",
                                createdAt = parsedTime,
                                driveFileId = file.id
                            )
                        } catch (e: Exception) { null }
                    } else if (existingVoiceNote != null && existingVoiceNote.driveFileId == null) {
                        libraryViewModel.updateVoiceNoteDriveId(existingVoiceNote.id, file.id!!)
                        null
                    } else null
                } ?: emptyList()

                val fetchedItems = searchResponse?.files?.mapNotNull { file ->
                    val fileName = file.name ?: return@mapNotNull null
                    if (fileName.equals("App Data", ignoreCase = true)) {
                        return@mapNotNull null
                    }
                    val prefs = context.getSharedPreferences("DriveOfflineDeletes", Context.MODE_PRIVATE)
                    val wasDeletedOffline = prefs.getBoolean(fileName, false) || prefs.getBoolean(file.id ?: "", false)
                    if (wasDeletedOffline && file.id != null) {
                        try {
                            driveService?.deleteFile("Bearer $token", file.id)
                            prefs.edit().remove(fileName).remove(file.id).apply()
                        } catch (e: Exception) { Log.e("DriveViewModel", "Failed to resolve offline delete", e) }
                        return@mapNotNull null
                    }
                    
                    val existingById = libraryViewModel.getFiles().find { it.driveFileId == file.id }
                    val existingByTitle = libraryViewModel.getFiles().find { it.title == fileName && it.driveFileId == null }
                    
                    if (existingById != null) {
                        // File exists locally. If title is different, it means we renamed it offline!
                        if (existingById.title != fileName) {
                            // Rename in Drive to match local
                            try {
                                val fileMetadata = mapOf("name" to existingById.title)
                                driveService?.updateDriveFile("Bearer $token", file.id!!, fileMetadata = fileMetadata)
                            } catch (e: Exception) { Log.e("DriveViewModel", "Failed to resolve offline rename", e) }
                        }
                        return@mapNotNull null
                    } else if (existingByTitle != null) {
                        // We found a local file with same name but no drive ID. Link them!
                        libraryViewModel.updateDriveFileId(existingByTitle.id, file.id!!)
                        return@mapNotNull null
                    } else {
                        // We found a file in Drive that is NOT local. It could be a new file from another device, or an old file user deleted locally.
                        // To allow multi-device sync, we assume it's a new file and add it locally. 
                        // If they deleted it offline, it will reappear (trade-off to ensure we don't delete other devices' files).
                        try {
                            val ext = fileName.substringAfterLast('.', "")
                            val mimeType = file.mimeType ?: "application/octet-stream"
                            val icon = if (ext in listOf("png","jpg","jpeg","gif", "webp", "bmp") || mimeType.startsWith("image/")) {
                                Icons.Default.Image
                            } else if (ext in listOf("mp3", "m4a", "wav") || mimeType.startsWith("audio/")) {
                                Icons.Default.Audiotrack
                            } else if (ext in listOf("mp4", "mkv", "avi") || mimeType.startsWith("video/")) {
                                Icons.Default.Movie
                            } else if (ext == "pdf" || mimeType == "application/pdf") {
                                Icons.Default.Description
                            } else if (ext in listOf("doc", "docx", "txt") || mimeType.contains("word") || mimeType.contains("document")) {
                                Icons.Default.Description
                            } else if (ext in listOf("xls", "xlsx", "csv") || mimeType.contains("spreadsheet") || mimeType.contains("excel")) {
                                Icons.Default.List
                            } else if (ext in listOf("ppt", "pptx") || mimeType.contains("presentation") || mimeType.contains("powerpoint")) {
                                Icons.Default.PlayArrow
                            } else {
                                Icons.Default.Description
                            }
                            
                            val parsedTime = try {
                                file.createdTime?.let {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    sdf.parse(it)?.time
                                } ?: System.currentTimeMillis()
                            } catch (e: Exception) { System.currentTimeMillis() }
                            
                            val subtitleDate = try {
                                val sdfOut = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                sdfOut.format(java.util.Date(parsedTime))
                            } catch (e: Exception) { "" }
                            
                            val fileSize = file.size?.toLongOrNull()
                            
                            LibraryItem(
                                id = java.util.UUID.randomUUID().toString(),
                                title = fileName,
                                subtitle = subtitleDate,
                                icon = icon,
                                iconTint = com.example.ui.theme.Cyan400,
                                iconBg = com.example.ui.theme.Cyan500.copy(alpha = 0.2f),
                                tags = emptyList(),
                                uri = null,
                                parentId = null,
                                lastAccessedAt = parsedTime,
                                driveFileId = file.id,
                                fileSize = fileSize
                            )
                        } catch (e: Exception) {
                            Log.e("DriveViewModel", "Failed to get metadata $fileName", e)
                            null
                        }
                    }
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    if (fetchedItems.isNotEmpty()) {
                        libraryViewModel.addLibraryItems(fetchedItems)
                    }
                    if (fetchedVoiceNotes.isNotEmpty()) {
                        libraryViewModel.addVoiceNotes(fetchedVoiceNotes)
                    }
                    
                    // Upload locally created files that are missing drive IDs
                    val pendingUploads = libraryViewModel.getFiles().filter { it.driveFileId == null && !it.isFolder && it.uri != null }
                    for (file in pendingUploads) {
                        val mime = context.contentResolver.getType(file.uri!!) ?: "*/*"
                        uploadFileToDrive(context, file.uri!!, mime, file.title, file.id, libraryViewModel, false, file.lastAccessedAt)
                    }
                    
                    // Upload locally created VoiceNotes that are missing drive IDs
                    val pendingVoiceNotes = libraryViewModel.voiceNotes.value.filter { it.driveFileId == null && it.uriString.isNotBlank() }
                    for (vn in pendingVoiceNotes) {
                        try {
                            val uri = android.net.Uri.parse(vn.uriString)
                            uploadFileToDrive(context, uri, "audio/mp4", "${vn.title}.m4a", vn.id, libraryViewModel, true, vn.createdAt)
                        } catch (e: Exception) { }
                    }

                    onComplete()
                }
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                Log.w("DriveViewModel", "UserRecoverableAuthException in syncDriveData", e)
                _recoverableAuthIntent.value = e.intent
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to sync Drive data", e)
                withContext(Dispatchers.Main) { onComplete() }
            } finally {
                _isDataSyncing.value = false
            }
        }
    }

    fun removeAccount(context: Context, email: String) {
        val currentAccounts = _accounts.value.toMutableSet()
        currentAccounts.remove(email)
        
        val edit = prefs.edit().putStringSet("accounts", currentAccounts)
        
        if (_activeAccount.value == email) {
            val newActive = currentAccounts.firstOrNull()
            if (currentAppUserEmail != null) {
                if (newActive != null) {
                    edit.putString("drive_for_$currentAppUserEmail", newActive)
                } else {
                    edit.remove("drive_for_$currentAppUserEmail")
                }
            }
            _activeAccount.value = newActive
            if (newActive != null) {
                fetchDriveStorage(context, newActive)
                setupScholarSpaceFolder(context, newActive)
            } else {
                _isConnected.value = false
                _storageUsage.value = 0
                _storageLimit.value = 1
                try {
                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                    val defaultClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                    defaultClient.signOut()
                    defaultClient.revokeAccess()
                } catch (e: Exception) {}
            }
        }
        edit.apply()
        _accounts.value = currentAccounts.toList()
    }

    fun signOut(context: Context) {
        metadataSyncJob?.cancel()
        
        viewModelScope.launch {
            try {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
                    .build()
                val client = GoogleSignIn.getClient(context, gso)
                client.signOut()
                client.revokeAccess()
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to sign out of Google Drive client", e)
            }
            try {
                val defaultClient = GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN)
                defaultClient.signOut()
                defaultClient.revokeAccess()
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to sign out of default Google client", e)
            }
            prefs.edit().clear().apply()
            _accounts.value = emptyList()
            _activeAccount.value = null
            _isConnected.value = false
            _storageUsage.value = 0
            _storageLimit.value = 1
        }
    }
}
