package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
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
    onSurfaceVariant = Slate400
  )

@Composable
fun MyApplicationTheme(
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      dynamicDarkColorScheme(LocalContext.current)
    } else {
      DarkColorScheme
    }

  val localDensity = androidx.compose.ui.platform.LocalDensity.current
  val customDensity = androidx.compose.ui.unit.Density(localDensity.density, 1.0f)

  MaterialTheme(colorScheme = colorScheme, typography = Typography) {
      androidx.compose.runtime.CompositionLocalProvider(
          androidx.compose.ui.platform.LocalDensity provides customDensity,
          content = content
      )
  }
}
