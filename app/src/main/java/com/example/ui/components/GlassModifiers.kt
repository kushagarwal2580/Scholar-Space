package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppTheme
import com.example.ui.theme.LocalAppTheme

fun Modifier.glassMorphic(
    shape: Shape = RoundedCornerShape(16.dp),
    isDark: Boolean? = null
): Modifier = composed {
    val appTheme = LocalAppTheme.current
    val isSystemDark = isSystemInDarkTheme()
    
    val actualIsDark = isDark ?: when (appTheme) {
        AppTheme.LIGHT_GLASS -> true
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemDark
    }

    val isGlassTheme = appTheme == AppTheme.LIGHT_GLASS || (appTheme == AppTheme.SYSTEM && !isSystemDark)

    val targetBgColor = if (isGlassTheme) {
        Color.White.copy(alpha = 0.05f)
    } else {
        com.example.ui.theme.Slate800.copy(alpha = 0.8f)
    }
    
    val targetBorderColor = if (isGlassTheme) {
        Color.White.copy(alpha = 0.1f)
    } else {
        com.example.ui.theme.Slate100.copy(alpha = 0.05f)
    }

    val bgColor by androidx.compose.animation.animateColorAsState(targetBgColor, androidx.compose.animation.core.tween(600), label = "glassBg")
    val borderColor by androidx.compose.animation.animateColorAsState(targetBorderColor, androidx.compose.animation.core.tween(600), label = "glassBorder")

    this
        .graphicsLayer {
            clip = true
            this.shape = shape
        }
        .background(bgColor)
        .border(1.dp, borderColor, shape)
}
