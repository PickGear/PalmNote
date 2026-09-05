package com.palmnote.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class ThemePackage(
    val id: String,
    val lightPrimary: Color,
    val darkPrimary: Color
)

object ThemePackages {

    val packages = listOf(
        ThemePackage("cyan", Color(0xFF0891B2), Color(0xFF22D3EE)),
        ThemePackage("green", Color(0xFF2D4A3E), Color(0xFF7BC4A0)),
        ThemePackage("blue", Color(0xFF1565C0), Color(0xFF64B5F6)),
        ThemePackage("purple", Color(0xFF6A1B9A), Color(0xFFBA68C8)),
        ThemePackage("orange", Color(0xFFE65100), Color(0xFFFF8A65))
    )

    fun getById(id: String): ThemePackage {
        if (id.startsWith("#")) {
            val color = try { Color(android.graphics.Color.parseColor(id)) } catch (_: Exception) { packages.first().lightPrimary }
            val darkColor = color.copy(alpha = 0.85f).copy(
                red = (color.red * 1.3f).coerceAtMost(1f),
                green = (color.green * 1.3f).coerceAtMost(1f),
                blue = (color.blue * 1.3f).coerceAtMost(1f)
            )
            return ThemePackage(id, color, darkColor)
        }
        return packages.firstOrNull { it.id == id } ?: packages.first()
    }

    fun derivePrimaryContainer(lightPrimary: Color): Color = lightPrimary.copy(alpha = 0.12f)

    fun deriveOnPrimaryContainer(lightPrimary: Color): Color = lightPrimary

    fun lightScheme(primary: Color, background: Color = BackgroundLight) = lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = derivePrimaryContainer(primary),
        onPrimaryContainer = deriveOnPrimaryContainer(primary),
        secondary = AccentOrange,
        onSecondary = Color.White,
        secondaryContainer = AccentOrange.copy(alpha = 0.12f),
        onSecondaryContainer = AccentOrange,
        tertiary = StatusActive,
        onTertiary = Color.White,
        background = background,
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

    fun darkScheme(primary: Color, background: Color = BackgroundDark) = darkColorScheme(
        primary = primary,
        onPrimary = Color.Black,
        primaryContainer = primary.copy(alpha = 0.15f),
        onPrimaryContainer = primary,
        secondary = DarkSecondary,
        onSecondary = Color.Black,
        secondaryContainer = DarkSecondary.copy(alpha = 0.15f),
        onSecondaryContainer = DarkSecondary,
        tertiary = DarkSuccess,
        onTertiary = Color.Black,
        background = background,
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
}
