package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

fun Modifier.glassMorphic(
    shape: Shape = RoundedCornerShape(16.dp),
    isDark: Boolean = true
): Modifier = composed {
    val bgColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.4f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.5f)
    
    this
        .clip(shape)
        .background(bgColor)
        .border(1.dp, borderColor, shape)
}
