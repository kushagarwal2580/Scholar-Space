package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

@Composable
fun animateColorScheme(target: ColorScheme): ColorScheme {
    val duration = 600
    val primary by animateColorAsState(target.primary, tween(duration))
    val onPrimary by animateColorAsState(target.onPrimary, tween(duration))
    val primaryContainer by animateColorAsState(target.primaryContainer, tween(duration))
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, tween(duration))
    val inversePrimary by animateColorAsState(target.inversePrimary, tween(duration))
    val secondary by animateColorAsState(target.secondary, tween(duration))
    val onSecondary by animateColorAsState(target.onSecondary, tween(duration))
    val secondaryContainer by animateColorAsState(target.secondaryContainer, tween(duration))
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, tween(duration))
    val tertiary by animateColorAsState(target.tertiary, tween(duration))
    val onTertiary by animateColorAsState(target.onTertiary, tween(duration))
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, tween(duration))
    val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, tween(duration))
    val background by animateColorAsState(target.background, tween(duration))
    val onBackground by animateColorAsState(target.onBackground, tween(duration))
    val surface by animateColorAsState(target.surface, tween(duration))
    val onSurface by animateColorAsState(target.onSurface, tween(duration))
    val surfaceVariant by animateColorAsState(target.surfaceVariant, tween(duration))
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, tween(duration))
    val surfaceTint by animateColorAsState(target.surfaceTint, tween(duration))
    val inverseSurface by animateColorAsState(target.inverseSurface, tween(duration))
    val inverseOnSurface by animateColorAsState(target.inverseOnSurface, tween(duration))
    val error by animateColorAsState(target.error, tween(duration))
    val onError by animateColorAsState(target.onError, tween(duration))
    val errorContainer by animateColorAsState(target.errorContainer, tween(duration))
    val onErrorContainer by animateColorAsState(target.onErrorContainer, tween(duration))
    val outline by animateColorAsState(target.outline, tween(duration))
    val outlineVariant by animateColorAsState(target.outlineVariant, tween(duration))
    val scrim by animateColorAsState(target.scrim, tween(duration))

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim
    )
}
