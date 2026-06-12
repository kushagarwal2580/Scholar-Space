package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.Cyan500
import com.example.ui.theme.Indigo500

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    drawBackgroundAndCircles: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    if (drawBackgroundAndCircles) {
        val bgColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
        val circleColor1 = if (isDark) Indigo500.copy(alpha = 0.35f) else Indigo500.copy(alpha = 0.22f)
        val circleColor2 = if (isDark) Cyan500.copy(alpha = 0.35f) else Cyan500.copy(alpha = 0.22f)
        val circleColor3 = if (isDark) Color(0xFF9333EA).copy(alpha = 0.3f) else Color(0xFF9333EA).copy(alpha = 0.18f)
        val circleColor4 = if (isDark) Color(0xFFEC4899).copy(alpha = 0.22f) else Color(0xFFEC4899).copy(alpha = 0.12f)

        Box(modifier = modifier.background(bgColor)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val height = size.height

                if (width > 0f && height > 0f) {
                    // Top-Right Quadrant Color coverage
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(circleColor1, Color.Transparent),
                            center = Offset(width * 0.85f, height * 0.15f),
                            radius = width * 0.9f
                        ),
                        radius = width * 0.9f,
                        center = Offset(width * 0.85f, height * 0.15f)
                    )

                    // Top-Left/Mid-Left coverage
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(circleColor2, Color.Transparent),
                            center = Offset(width * 0.15f, height * 0.35f),
                            radius = width * 0.8f
                        ),
                        radius = width * 0.8f,
                        center = Offset(width * 0.15f, height * 0.35f)
                    )

                    // Bottom-Right coverage
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(circleColor3, Color.Transparent),
                            center = Offset(width * 0.8f, height * 0.8f),
                            radius = width * 0.75f
                        ),
                        radius = width * 0.75f,
                        center = Offset(width * 0.8f, height * 0.8f)
                    )

                    // Bottom-Left/Mid-Right visual balance accent
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(circleColor4, Color.Transparent),
                            center = Offset(width * 0.2f, height * 0.75f),
                            radius = width * 0.7f
                        ),
                        radius = width * 0.7f,
                        center = Offset(width * 0.2f, height * 0.75f)
                    )
                }
            }
            content()
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}
