package com.palmnote.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalIsDarkTheme = staticCompositionLocalOf { false }
val LocalSwitchColor = staticCompositionLocalOf { PrimaryGreen }

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = PrimaryGreenLight,
    onPrimaryContainer = PrimaryGreen,
    secondary = AccentOrange,
    onSecondary = Color.White,
    secondaryContainer = AccentOrange.copy(alpha = 0.12f),
    onSecondaryContainer = AccentOrange,
    tertiary = StatusActive,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    error = ErrorLight,
    onError = Color.White,
    outline = OutlineLight,
    outlineVariant = SurfaceVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.Black,
    primaryContainer = DarkPrimary.copy(alpha = 0.15f),
    onPrimaryContainer = DarkPrimary,
    secondary = DarkSecondary,
    onSecondary = Color.Black,
    secondaryContainer = DarkSecondary.copy(alpha = 0.15f),
    onSecondaryContainer = DarkSecondary,
    tertiary = DarkSuccess,
    onTertiary = Color.Black,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    error = ErrorDark,
    onError = Color.Black,
    outline = OutlineDark,
    outlineVariant = SurfaceVariantDark
)

@Composable
fun PalmNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes
    ) {
        CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
            content()
        }
    }
}

@Composable
fun ColorScheme.warning(): Color = if (LocalIsDarkTheme.current) DarkWarning else Warning
