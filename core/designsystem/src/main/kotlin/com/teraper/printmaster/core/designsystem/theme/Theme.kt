package com.teraper.printmaster.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalPmColors = staticCompositionLocalOf { LightPmColors }
private val LocalPmSpacing = staticCompositionLocalOf { PmSpacing() }
private val LocalPmShapes = staticCompositionLocalOf { PmShapes() }

@Composable
fun PmTheme(content: @Composable () -> Unit) {
    val colors = LightPmColors
    // Material components (text fields, dialogs, nav bar) take their colors from
    // the same tokens, so they match our own components.
    val scheme = lightColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.primary,
        secondaryContainer = colors.primaryContainer,
        onSecondaryContainer = colors.primary,
        background = colors.background,
        onBackground = colors.ink,
        surface = colors.surface,
        onSurface = colors.ink,
        surfaceVariant = colors.surfaceMuted,
        onSurfaceVariant = colors.inkMuted,
        surfaceContainer = colors.surface,
        outline = colors.outlineStrong,
        outlineVariant = colors.outline,
        error = colors.error,
    )
    CompositionLocalProvider(
        LocalPmColors provides colors,
        LocalPmSpacing provides PmSpacing(),
        LocalPmShapes provides PmShapes(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = PmTypography, content = content)
    }
}

object PmTheme {
    val colors: PmColors
        @Composable @ReadOnlyComposable get() = LocalPmColors.current
    val spacing: PmSpacing
        @Composable @ReadOnlyComposable get() = LocalPmSpacing.current
    val shapes: PmShapes
        @Composable @ReadOnlyComposable get() = LocalPmShapes.current
}
