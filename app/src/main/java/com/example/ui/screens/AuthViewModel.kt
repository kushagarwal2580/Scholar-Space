package com.example.ui.screens

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.repository.UserRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val userRepository: UserRepository, private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthState>(AuthState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    fun setSyncing(syncing: Boolean) {
        _isSyncing.value = syncing
    }

    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    init {
        checkExistingGoogleAccount(context)
    }


    fun checkExistingGoogleAccount(context: Context) {
        viewModelScope.launch {
            if (_uiState.value !is AuthState.Success) {
                val loggedInEmail = prefs.getString("loggedInEmail", null)
                if (loggedInEmail != null && loggedInEmail.contains("@")) {
                    val existingUser = userRepository.getUserByEmail(loggedInEmail)
                    if (existingUser != null) {
                        _uiState.value = AuthState.Success(
                            email = existingUser.email,
                            displayName = existingUser.username,
                            profilePic = existingUser.profilePic,
                            phone = existingUser.phone,
                            bio = existingUser.bio,
                            statusMsg = existingUser.statusMsg
                        )
                    } else {
                        prefs.edit().remove("loggedInEmail").apply()
                        _uiState.value = AuthState.Idle
                    }
                } else {
                    // Fallback to legacy GoogleSignIn
                    val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null && account.email != null) {
                        val existingUser = userRepository.getUserByEmail(account.email!!)
                        val displayName = existingUser?.username ?: account.displayName ?: account.email!!
                        val profilePic = existingUser?.profilePic // Remove fallback to Google Profile Pic
                        _uiState.value = AuthState.Success(
                            email = account.email!!,
                            displayName = displayName,
                            profilePic = profilePic,
                            phone = existingUser?.phone,
                            bio = existingUser?.bio,
                            statusMsg = existingUser?.statusMsg
                        )
                        userRepository.saveGoogleUser(account.email!!, account.displayName, null)
                        prefs.edit().putString("loggedInEmail", account.email).apply()
                    } else {
                        _uiState.value = AuthState.Idle
                    }
                }
            }
        }
    }

    fun getSignInIntent(context: Context): android.content.Intent {
        val webClientId = com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    fun handleSignInResult(intent: android.content.Intent?) {
        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            try {
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(intent)
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                
                val email = account?.email
                val idToken = account?.idToken
                
                if (email != null && idToken != null) {
                    val displayName = account.displayName ?: email.split("@")[0]
                    val fbCred = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                    com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(fbCred).addOnCompleteListener { fbTask ->
                        viewModelScope.launch {
                            if (fbTask.isSuccessful) {
                                var existingUser = userRepository.getUserByEmail(email)
                                if (existingUser == null) {
                                    userRepository.registerUser(displayName, email, null, "")
                                    existingUser = userRepository.getUserByEmail(email)
                                }
                                prefs.edit().putString("loggedInEmail", email).apply()
                                _isSyncing.value = true // Trigger syncing screen
                                _uiState.value = AuthState.Success(
                                    email = email,
                                    displayName = existingUser?.username ?: displayName,
                                    profilePic = existingUser?.profilePic,
                                    phone = existingUser?.phone,
                                    bio = existingUser?.bio,
                                    statusMsg = existingUser?.statusMsg
                                )
                            } else {
                                // Local fallback
                                var existingUserLocal = userRepository.getUserByEmail(email)
                                if (existingUserLocal == null) {
                                    userRepository.registerUser(displayName, email, null, "")
                                    existingUserLocal = userRepository.getUserByEmail(email)
                                }
                                if (existingUserLocal != null) {
                                    prefs.edit().putString("loggedInEmail", email).apply()
                                    _isSyncing.value = true
                                    _uiState.value = AuthState.Success(
                                        email = email,
                                        displayName = existingUserLocal.username ?: displayName,
                                        profilePic = existingUserLocal.profilePic,
                                        phone = existingUserLocal.phone,
                                        bio = existingUserLocal.bio,
                                        statusMsg = existingUserLocal.statusMsg
                                    )
                                } else {
                                    _uiState.value = AuthState.Error("Failed to create local account", true)
                                }
                            }
                        }
                    }
                } else {
                    _uiState.value = AuthState.Error("Unexpected result from Google Sign In.", true)
                }
            } catch (e: Exception) {
                if (e is com.google.android.gms.common.api.ApiException && e.statusCode == com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                    _uiState.value = AuthState.Idle
                    Log.d("AuthViewModel", "Sign in cancelled by user")
                } else {
                    Log.e("AuthViewModel", "Sign in failed", e)
                    _uiState.value = AuthState.Error("Sign in failed: ${e.localizedMessage}", true)
                }
            }
        }
    }

    fun handleLegacySignIn(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            try {
                val email = account.email ?: return@launch
                val displayName = account.displayName ?: email
                
                val existingUser = userRepository.getUserByEmail(email)
                if (existingUser != null) {
                    prefs.edit().putString("loggedInEmail", email).apply()
                    _uiState.value = AuthState.Success(
                        email = email,
                        displayName = existingUser.username ?: displayName,
                        profilePic = existingUser.profilePic,
                        phone = existingUser.phone,
                        bio = existingUser.bio,
                        statusMsg = existingUser.statusMsg
                    )
                } else {
                    _uiState.value = AuthState.Error("NO_ACCOUNT_FOUND", true)
                }
            } catch (e: Exception) {
                _uiState.value = AuthState.Error("Sign in failed: ${e.localizedMessage ?: "Unknown error"}", true)
            }
        }
    }

    fun updateNickname(nickname: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is AuthState.Success) {
                userRepository.updateNickname(currentState.email, nickname)
                _uiState.value = AuthState.Success(
                    email = currentState.email,
                    displayName = nickname,
                    profilePic = currentState.profilePic,
                    phone = currentState.phone,
                    bio = currentState.bio,
                    statusMsg = currentState.statusMsg
                )
            }
        }
    }

    fun updateProfile(
        nickname: String,
        profilePic: String?,
        phone: String?,
        bio: String?,
        statusMsg: String?
    ) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is AuthState.Success) {
                userRepository.updateProfile(
                    email = currentState.email,
                    nickname = nickname,
                    profilePic = profilePic,
                    phone = phone,
                    bio = bio,
                    statusMsg = statusMsg
                )
                _uiState.value = AuthState.Success(
                    email = currentState.email,
                    displayName = nickname,
                    profilePic = profilePic,
                    phone = phone,
                    bio = bio,
                    statusMsg = statusMsg
                )
            }
        }
    }

    fun deleteAccount(context: Context, passwordToVerify: String) {
        viewModelScope.launch {
            val email = prefs.getString("loggedInEmail", null) ?: return@launch
            _uiState.value = AuthState.Loading
            try {
                val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                performAccountDeletion(context, email, fbUser)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Delete account failed", e)
                _uiState.value = AuthState.Error("Failed to delete account: ${e.localizedMessage}")
            }
        }
    }

    private fun performAccountDeletion(context: Context, email: String, fbUser: com.google.firebase.auth.FirebaseUser?) {
        viewModelScope.launch {
            try {
                // Delete local profile pictures
                val filesDir = context.filesDir
                val files = filesDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.name.startsWith("profile_pic_") || file.name == "profile_pic.jpg") {
                            file.delete()
                        }
                    }
                }
                
                userRepository.deleteUserByEmail(email)
                
                fbUser?.delete()?.addOnCompleteListener { task -> 
                    // Ignore errors, we still sign out locally
                }
                
                val credentialManager = androidx.credentials.CredentialManager.create(context)
                credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
                com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                
                prefs.edit().remove("loggedInEmail").apply()
                _uiState.value = AuthState.Idle
            } catch (e: Exception) {
                 _uiState.value = AuthState.Error("Failed to complete account deletion.")
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            try {
                // Delete local profile pictures
                val filesDir = context.filesDir
                val files = filesDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.name.startsWith("profile_pic_") || file.name == "profile_pic.jpg") {
                            file.delete()
                        }
                    }
                }
                
                val credentialManager = CredentialManager.create(context)
                credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
                com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                prefs.edit().remove("loggedInEmail").apply()
                _uiState.value = AuthState.Idle
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Sign out failed", e)
                // Even if it fails, clear local state
                try {
                    com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                } catch (e2: Exception) {}
                prefs.edit().remove("loggedInEmail").apply()
                _uiState.value = AuthState.Idle
            }
        }
    }

    private var _generatedOtp: String? = null
    val generatedOtp: String? get() = _generatedOtp

    private var _resetIdentifier: String? = null
    val resetIdentifier: String? get() = _resetIdentifier

    private val _otpSentMessage = MutableStateFlow<String?>(null)
    val otpSentMessage = _otpSentMessage.asStateFlow()

    fun clearErrorState() {
        if (_uiState.value is AuthState.Error) {
            _uiState.value = AuthState.Idle
        }
        _otpSentMessage.value = null
    }

    fun clearOtpMessage() {
        _otpSentMessage.value = null
    }

    fun signUpWithGoogleAndDetails(context: Context, username: String, phone: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            if (username.isBlank() || password.isBlank()) {
                _uiState.value = AuthState.Error("Please fill in username and password.")
                return@launch
            }
            if (password.length < 8) {
                _uiState.value = AuthState.Error("Password must be at least 8 characters long.")
                return@launch
            }
            try {
                val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                if (webClientId.isBlank() || webClientId == "MY_GOOGLE_WEB_CLIENT_ID") {
                   _uiState.value = AuthState.Error("Google Web Client ID is not configured.", true)
                   return@launch
                }
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    var email = googleIdTokenCredential.id
                    try {
                        val tokenParts = googleIdTokenCredential.idToken.split(".")
                        if (tokenParts.size >= 2) {
                            val payloadString = String(android.util.Base64.decode(tokenParts[1], android.util.Base64.URL_SAFE))
                            val jsonObject = org.json.JSONObject(payloadString)
                            val extractedEmail = jsonObject.optString("email")
                            if (extractedEmail.isNotBlank()) {
                                email = extractedEmail
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AuthViewModel", "Failed to parse JWT", e)
                    }
                    val displayName = googleIdTokenCredential.displayName

                    com.google.firebase.auth.FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val fbCred = com.google.firebase.auth.GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                                task.result?.user?.linkWithCredential(fbCred)
                                
                                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(username)
                                    .build()
                                task.result?.user?.updateProfile(profileUpdates)
                                
                                viewModelScope.launch {
                                    val existingUser = userRepository.getUserByEmail(email)
                                    if (existingUser == null) {
                                        userRepository.registerUser(username, email, phone.takeIf { it.isNotBlank() }, password)
                                    } else {
                                        userRepository.updatePasswordByEmail(email, password)
                                        userRepository.updatePhoneByEmail(email, phone)
                                    }
                                    prefs.edit().putString("loggedInEmail", email).apply()
                                    _uiState.value = AuthState.Success(
                                        email = email,
                                        displayName = username,
                                        profilePic = existingUser?.profilePic,
                                        phone = phone.takeIf { it.isNotBlank() },
                                        bio = existingUser?.bio,
                                        statusMsg = existingUser?.statusMsg
                                    )
                                }
                            } else {
                                _uiState.value = AuthState.Error(task.exception?.localizedMessage ?: "Registration failed.")
                            }
                        }
                } else {
                    _uiState.value = AuthState.Error("Unexpected credential type.", true)
                }
            } catch (e: Exception) {
                if (e.localizedMessage?.contains("16") == true || e.localizedMessage?.contains("cancel", ignoreCase = true) == true) {
                    _uiState.value = AuthState.Idle
                } else {
                    _uiState.value = AuthState.Error("Sign up via Google failed: ${e.localizedMessage}", true)
                }
            }
        }
    }

    fun signUpWithCredentials(username: String, email: String, phone: String?, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            if (username.isBlank() || email.isBlank() || password.isBlank()) {
                _uiState.value = AuthState.Error("Please fill in username, email and password.")
                return@launch
            }
            if (password.length < 8) {
                _uiState.value = AuthState.Error("Password must be at least 8 characters long.")
                return@launch
            }
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = task.result?.user
                            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                .setDisplayName(username)
                                .build()
                            user?.updateProfile(profileUpdates)
                            
                            viewModelScope.launch {
                                userRepository.registerUser(username, email, phone?.takeIf { it.isNotBlank() }, password)
                                prefs.edit().putString("loggedInEmail", email).apply()
                                _uiState.value = AuthState.Success(
                                    email = email,
                                    displayName = username,
                                    phone = phone?.takeIf { it.isNotBlank() }
                                )
                            }
                        } else {
                            _uiState.value = AuthState.Error(task.exception?.localizedMessage ?: "Registration failed.")
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = AuthState.Error("Failed to initialize Firebase Auth: ${e.localizedMessage}")
            }
        }
    }

    fun signInWithCredentials(identifier: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            if (identifier.isBlank() || password.isBlank()) {
                _uiState.value = AuthState.Error("Please enter email/phone and password.")
                return@launch
            }
            try {
                var emailToLogin = identifier
                if (!identifier.contains("@")) {
                    val user = userRepository.getUserByPhone(identifier)
                    if (user != null) {
                        emailToLogin = user.email
                    } else {
                        _uiState.value = AuthState.Error("No account found with this phone number. Please sign up or use email.", false)
                        return@launch
                    }
                }
                
                com.google.firebase.auth.FirebaseAuth.getInstance().signInWithEmailAndPassword(emailToLogin, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val fbUser = task.result?.user
                            val email = fbUser?.email ?: emailToLogin
                            val displayName = fbUser?.displayName ?: "User"
                            
                            viewModelScope.launch {
                                val localUser = userRepository.getUserByEmail(email)
                                if (localUser == null) {
                                    userRepository.registerUser(displayName, email, if(!identifier.contains("@")) identifier else null, password)
                                }
                                prefs.edit().putString("loggedInEmail", email).apply()
                                _uiState.value = AuthState.Success(
                                    email = email,
                                    displayName = localUser?.username ?: displayName,
                                    profilePic = localUser?.profilePic,
                                    phone = localUser?.phone,
                                    bio = localUser?.bio,
                                    statusMsg = localUser?.statusMsg
                                )
                            }
                        } else {
                            viewModelScope.launch {
                                // Fallback: if Firebase login fails but local password matches, log them in!
                                val localUser = userRepository.getUserByEmail(emailToLogin)
                                if (localUser != null && localUser.password == password) {
                                    prefs.edit().putString("loggedInEmail", emailToLogin).apply()
                                    _uiState.value = AuthState.Success(
                                        email = emailToLogin,
                                        displayName = localUser.username,
                                        profilePic = localUser.profilePic,
                                        phone = localUser.phone,
                                        bio = localUser.bio,
                                        statusMsg = localUser.statusMsg
                                    )
                                } else {
                                    _uiState.value = AuthState.Error(task.exception?.localizedMessage ?: "Login failed.")
                                }
                            }
                        }
                    }
            } catch(e: Exception) {
                 _uiState.value = AuthState.Error("Login via Firebase failed: ${e.localizedMessage}")
            }
        }
    }

    private var verificationId: String? = null
    private var autoVerifiedCredential: com.google.firebase.auth.PhoneAuthCredential? = null

    fun sendPhoneOtp(activity: android.app.Activity, phone: String) {
        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            if (phone.isBlank()) {
                _uiState.value = AuthState.Error("Please enter phone number.")
                return@launch
            }
            try {
                autoVerifiedCredential = null
                val options = com.google.firebase.auth.PhoneAuthOptions.newBuilder(com.google.firebase.auth.FirebaseAuth.getInstance())
                    .setPhoneNumber(phone)
                    .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                            autoVerifiedCredential = credential
                            _uiState.value = AuthState.Idle
                            _otpSentMessage.value = "Phone number automatically verified! Please proceed by clicking the verify button (you can leave OTP blank)."
                        }
                        override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                            _uiState.value = AuthState.Error(e.localizedMessage ?: "Verification failed, please ensure Billing is enabled on your Firebase console")
                        }
                        override fun onCodeSent(
                            verId: String,
                            token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
                        ) {
                            verificationId = verId
                            _uiState.value = AuthState.Idle
                            _otpSentMessage.value = "OTP sent to $phone"
                        }
                    })
                    .build()
                com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(options)
            } catch (e: Exception) {
                _uiState.value = AuthState.Error("Failed to send OTP: ${e.localizedMessage}")
            }
        }
    }

    fun verifyPhoneOtpOnly(code: String, onSuccess: () -> Unit) {
        if (verificationId == null && autoVerifiedCredential == null) {
            _uiState.value = AuthState.Error("Please request OTP first.")
            return
        }
        _uiState.value = AuthState.Loading
        
        try {
            val credential = autoVerifiedCredential ?: com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId!!, code)
            com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        _uiState.value = AuthState.Idle
                        _otpSentMessage.value = null
                        onSuccess()
                    } else {
                        _uiState.value = AuthState.Error(task.exception?.localizedMessage ?: "Invalid OTP")
                    }
                }
        } catch (e: Exception) {
            _uiState.value = AuthState.Error("An error occurred: ${e.localizedMessage}")
        }
    }

    fun verifyOtpAndSignUp(code: String, phone: String, username: String, email: String, password: String) {
        if (verificationId == null && autoVerifiedCredential == null) {
            _uiState.value = AuthState.Error("Please request OTP first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            if (password.length < 8) {
                _uiState.value = AuthState.Error("Password must be at least 8 characters long.")
                return@launch
            }
            try {
                val credential = autoVerifiedCredential ?: com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId!!, code)
                com.google.firebase.auth.FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = task.result?.user
                            user?.linkWithCredential(credential)?.addOnCompleteListener { linkTask ->
                                if (linkTask.isSuccessful) {
                                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                        .setDisplayName(username).build()
                                    user.updateProfile(profileUpdates)
                                    viewModelScope.launch {
                                        userRepository.registerUser(username, email, phone, password)
                                        prefs.edit().putString("loggedInEmail", email).apply()
                                        _uiState.value = AuthState.Success(
                                            email = email,
                                            displayName = username,
                                            profilePic = null,
                                            phone = phone,
                                            bio = "",
                                            statusMsg = ""
                                        )
                                        _otpSentMessage.value = null
                                    }
                                } else {
                                    user.delete() // Cleanup if OTP is invalid or already linked
                                    _uiState.value = AuthState.Error(linkTask.exception?.localizedMessage ?: "Invalid OTP or phone already linked.")
                                }
                            }
                        } else {
                            _uiState.value = AuthState.Error(task.exception?.localizedMessage ?: "Sign up failed.")
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = AuthState.Error("An error occurred: ${e.localizedMessage}")
            }
        }
    }

    fun verifyOtpAndResetPassword(code: String, phone: String, newPassword: String) {
        if (verificationId == null && autoVerifiedCredential == null) {
            _uiState.value = AuthState.Error("Please request OTP first.")
            return
        }
        if (newPassword.length < 8) {
            _uiState.value = AuthState.Error("New password must be at least 8 characters long.")
            return
        }
        _uiState.value = AuthState.Loading
        try {
            val credential = autoVerifiedCredential ?: com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId!!, code)
            com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = task.result?.user
                        user?.updatePassword(newPassword)?.addOnCompleteListener { pwTask ->
                            if (pwTask.isSuccessful) {
                                _uiState.value = AuthState.Idle
                                _otpSentMessage.value = "Password reset successful! Please log in."
                                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                            } else {
                                _uiState.value = AuthState.Error(pwTask.exception?.localizedMessage ?: "Failed to reset password.")
                            }
                        }
                        
                        viewModelScope.launch {
                            try {
                                userRepository.updatePasswordByPhone(phone, newPassword)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    } else {
                        _uiState.value = AuthState.Error(task.exception?.localizedMessage ?: "Invalid OTP.")
                    }
                }
        } catch (e: Exception) {
            _uiState.value = AuthState.Error("An error occurred: ${e.localizedMessage}")
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            if (email.isBlank()) {
                _uiState.value = AuthState.Error("Please enter your email.")
                return@launch
            }
            
            val user = userRepository.getUserByEmail(email)
            if (user == null) {
                _uiState.value = AuthState.Error("No account found with this email.")
                return@launch
            }

            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            _uiState.value = AuthState.Idle
                            _otpSentMessage.value = "Password reset link sent to $email"
                        } else {
                            _uiState.value = AuthState.Error(task.exception?.localizedMessage ?: "Failed to send reset email.")
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = AuthState.Error("Failed to initialize Firebase Auth: ${e.localizedMessage}")
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is AuthState.Success) {
                _uiState.value = AuthState.Loading
                val email = currentState.email
                try {
                    val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    if (fbUser != null && fbUser.email == email) {
                        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, oldPassword)
                        fbUser.reauthenticate(credential).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                fbUser.updatePassword(newPassword).addOnCompleteListener { pwTask ->
                                    if (pwTask.isSuccessful) {
                                        viewModelScope.launch {
                                            userRepository.updatePasswordByEmail(email, newPassword)
                                            _uiState.value = currentState
                                            _otpSentMessage.value = "Password updated successfully!"
                                        }
                                    } else {
                                        _uiState.value = AuthState.Error(pwTask.exception?.localizedMessage ?: "Failed to update password.")
                                        _uiState.value = currentState // Reset back on error acknowledge
                                    }
                                }
                            } else {
                                _uiState.value = AuthState.Error("Incorrect old password.")
                                viewModelScope.launch { kotlinx.coroutines.delay(2000); _uiState.value = currentState }
                            }
                        }
                    } else {
                        // Local update only
                        val user = userRepository.getUserByEmail(email)
                        if (user != null && user.password == oldPassword) {
                            userRepository.updatePasswordByEmail(email, newPassword)
                            _uiState.value = currentState
                            _otpSentMessage.value = "Password updated successfully!"
                        } else {
                            _uiState.value = AuthState.Error("Incorrect old password.")
                            kotlinx.coroutines.delay(2000)
                            _uiState.value = currentState
                        }
                    }
                } catch (e: Exception) {
                     _uiState.value = AuthState.Error("An error occurred: ${e.message}")
                     kotlinx.coroutines.delay(2000)
                     _uiState.value = currentState
                }
            }
        }
    }

    // Complete password setup removed
}

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    // Remove RequiresPasswordSetup completely
    data class Success(
        val email: String,
        val displayName: String?,
        val profilePic: String? = null,
        val phone: String? = null,
        val bio: String? = null,
        val statusMsg: String? = null
    ) : AuthState
    data class Error(val message: String, val isGoogleAuthError: Boolean = false) : AuthState
}

class AuthViewModelFactory(private val userRepository: UserRepository, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(userRepository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
