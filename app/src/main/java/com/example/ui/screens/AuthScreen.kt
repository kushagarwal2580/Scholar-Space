package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassBackground
import com.example.ui.components.glassMorphic
import androidx.compose.ui.text.style.TextAlign
import android.content.Context
import android.content.ContextWrapper

fun Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun AuthScreen(authViewModel: AuthViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authState by authViewModel.uiState.collectAsState(initial = AuthState.Loading)

    LaunchedEffect(Unit) {
        authViewModel.checkExistingGoogleAccount(context)
    }

    if (authState is AuthState.Loading) {
        LoginSplashScreen()
        return
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showOfflineMessage by remember { mutableStateOf(false) }
    
    val isOnline = remember { 
        mutableStateOf(
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).let { cm ->
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
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
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

    LaunchedEffect(authState) {
        if (authState is AuthState.Error) {
            errorMessage = (authState as AuthState.Error).message
        } else {
             errorMessage = null
        }
    }

    GlassBackground(
        modifier = Modifier.fillMaxSize(),
        drawBackgroundAndCircles = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFF0F172A).copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                    contentDescription = "App Logo",
                    modifier = Modifier.requiredSize(150.dp),
                    tint = Color.Unspecified
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scholar Space",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 32.dp),
                letterSpacing = 1.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassMorphic(RoundedCornerShape(32.dp))
                    .padding(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sign In",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "NOTE: Signing in with Google is necessary to seamlessly connect your Google Drive, where your files will be securely stored.",
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { 
                                if (isOnline.value) {
                                    showOfflineMessage = false
                                    authViewModel.signInWithGoogle(context) 
                                } else {
                                    showOfflineMessage = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.example.ui.theme.Cyan400.copy(alpha=0.15f),
                                contentColor = com.example.ui.theme.Cyan400
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.Cyan400.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_google),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(20.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Sign in with Google", color = com.example.ui.theme.Cyan400, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        
                        if (showOfflineMessage) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Please connect to internet", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    
                    if (authState is AuthState.Loading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = com.example.ui.theme.Cyan400)
                    }
                }
            }
        }
    }
}

@Composable
fun LoginSplashScreen() {
    GlassBackground(
        modifier = Modifier.fillMaxSize(),
        drawBackgroundAndCircles = true
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = com.example.ui.theme.Cyan400,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}


