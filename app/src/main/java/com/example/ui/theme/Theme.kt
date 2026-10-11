package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkGlassColorScheme = darkColorScheme(
    primary = Cyan400,
    secondary = Cyan400,
    tertiary = Cyan300,
    background = Slate900,
    surface = Slate800,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Slate100,
    onSurface = Slate100,
    surfaceVariant = Slate800.copy(alpha = 0.6f),
    onSurfaceVariant = Slate400,
    error = Red500
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo600,
    secondary = Indigo500,
    tertiary = Cyan500,
    background = Slate100,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Slate900,
    onSurface = Slate900,
    surfaceVariant = Slate300.copy(alpha = 0.6f),
    onSurfaceVariant = Slate700,
    error = Red500
)

private val NormalDarkColorScheme =  darkColorScheme(
    primary = Cyan400,
    secondary = Cyan400,
    tertiary = Cyan300,
    background = Slate900,
    surface = Slate800,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Slate100,
    onSurface = Slate100,
    surfaceVariant = Slate800.copy(alpha = 0.8f),
    onSurfaceVariant = Slate400,
    error = Red500
)

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    
    val colorScheme = when (appTheme) {
        AppTheme.LIGHT_GLASS -> DarkGlassColorScheme
        AppTheme.DARK -> NormalDarkColorScheme
        AppTheme.SYSTEM -> if (isSystemDark) NormalDarkColorScheme else DarkGlassColorScheme
    }

    val animatedColorScheme = animateColorScheme(colorScheme)
    
    val localDensity = androidx.compose.ui.platform.LocalDensity.current
    val customDensity = androidx.compose.ui.unit.Density(localDensity.density, 1.0f)

    MaterialTheme(colorScheme = animatedColorScheme, typography = Typography) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides customDensity,
            LocalAppTheme provides appTheme,
            content = content
        )
    }
}
