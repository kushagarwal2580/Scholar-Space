package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SyncingScreen(onFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    LaunchedEffect(Unit) {
        delay(5000) // Show for 5 seconds as requested
        onFinished()
    }

    com.example.ui.components.GlassBackground(
        modifier = Modifier.fillMaxSize(),
        drawBackgroundAndCircles = true
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale)
            ) {
                // Google colored circular animation concept
                // Outer ring with multiple segments
                Canvas(modifier = Modifier.size(120.dp).rotate(rotation)) {
                    val strokeWidth = 10.dp.toPx()
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF4285F4), // Blue
                                Color(0xFFEA4335), // Red
                                Color(0xFFFBBC05), // Yellow
                                Color(0xFF34A853), // Green
                                Color(0xFF4285F4)  // Blue
                            )
                        ),
                        style = Stroke(width = strokeWidth)
                    )
                }
                
                // Pulsing dot in the middle with Scholar Space theme
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            Brush.linearGradient(listOf(Cyan400, Cyan300)),
                            CircleShape
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Syncing with Drive",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Please wait while we update your space...",
                color = Slate400,
                fontSize = 14.sp
            )
        }
    }
}
}
