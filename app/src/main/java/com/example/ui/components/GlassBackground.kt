package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
    val isDark = true
    
    if (drawBackgroundAndCircles) {
        val bgColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
        val circleColor1 = if (isDark) Color(0xFF6366F1).copy(alpha = 0.15f) else Color(0xFF6366F1).copy(alpha = 0.1f) // Indigo
        val circleColor2 = if (isDark) Color(0xFFD946EF).copy(alpha = 0.15f) else Color(0xFFD946EF).copy(alpha = 0.08f) // Fuchsia
        val circleColor3 = if (isDark) Color(0xFFA855F7).copy(alpha = 0.15f) else Color(0xFFA855F7).copy(alpha = 0.08f) // Purple
        val circleColor4 = if (isDark) Color(0xFF0EA5E9).copy(alpha = 0.1f) else Color(0xFF0EA5E9).copy(alpha = 0.05f) // Sky Blue
        val glowColor = if (isDark) Color(0xFF8B5CF6).copy(alpha = 0.1f) else Color(0xFF8B5CF6).copy(alpha = 0.05f) // Violet

        Box(
            modifier = modifier
                .background(bgColor)
                .drawWithCache {
                    val width = size.width
                    val height = size.height
                    
                    if (width > 0f && height > 0f) {
                        val maxDim = maxOf(width, height)
                        
                        val brush1 = Brush.radialGradient(
                            colors = listOf(circleColor1, Color.Transparent),
                            center = Offset(width * 0.85f, height * 0.15f),
                            radius = maxDim * 0.8f
                        )
                        val brush2 = Brush.radialGradient(
                            colors = listOf(circleColor2, Color.Transparent),
                            center = Offset(width * 0.1f, height * 0.2f),
                            radius = maxDim * 0.75f
                        )
                        val brush3 = Brush.radialGradient(
                            colors = listOf(circleColor3, Color.Transparent),
                            center = Offset(width * 0.85f, height * 0.85f),
                            radius = maxDim * 0.85f
                        )
                        val brush4 = Brush.radialGradient(
                            colors = listOf(circleColor4, Color.Transparent),
                            center = Offset(width * 0.15f, height * 0.8f),
                            radius = maxDim * 0.7f
                        )
                        val brushGlow = Brush.radialGradient(
                            colors = listOf(glowColor, Color.Transparent),
                            center = Offset(width * 0.5f, height * 0.5f),
                            radius = maxDim * 0.65f
                        )
                        
                        onDrawBehind {
                            drawCircle(brush = brush1, radius = maxDim * 0.8f, center = Offset(width * 0.85f, height * 0.15f))
                            drawCircle(brush = brush2, radius = maxDim * 0.75f, center = Offset(width * 0.1f, height * 0.2f))
                            drawCircle(brush = brush3, radius = maxDim * 0.85f, center = Offset(width * 0.85f, height * 0.85f))
                            drawCircle(brush = brush4, radius = maxDim * 0.7f, center = Offset(width * 0.15f, height * 0.8f))
                            drawCircle(brush = brushGlow, radius = maxDim * 0.65f, center = Offset(width * 0.5f, height * 0.5f))
                        }
                    } else {
                        onDrawBehind {}
                    }
                }
        ) {
            content()
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}
