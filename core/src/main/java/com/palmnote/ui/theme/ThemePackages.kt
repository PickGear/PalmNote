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
        ThemePackage("orange", Color(0xFFE65100), Color(0xFFFF8A65)),
        ThemePackage("red", Color(0xFFC62828), Color(0xFFEF5350)),
        ThemePackage("teal", Color(0xFF00695C), Color(0xFF4DB6AC)),
        ThemePackage("pink", Color(0xFFAD1457), Color(0xFFF06292))
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
        // 不用 M3 的「色调抬升」：主题不覆盖 surfaceTint 时它会留在基线紫色，
        // 任何带 tonalElevation 的组件（下拉菜单、弹层）都会被染成紫色。
        // 本 App 的设计本来就是平涂（各处显式 tonalElevation = 0.dp），这里把默认也置空。
        surfaceTint = Color.Transparent,
        // surfaceContainer* 这一组同理：不覆盖就留在 M3 基线调色板（浅 #ECE6F0 / 深 #2B2930，都是紫调），
        // 而 AlertDialog、ModalBottomSheet、DropdownMenu 的**容器色**恰好取自这一组 →
        // 漏掉的话弹窗/弹层整片发紫（加密账单密码弹窗就是这么来的）。App 是平涂设计，统一落到面色。
        surfaceContainerLowest = SurfaceLight,
        surfaceContainerLow = SurfaceLight,
        surfaceContainer = SurfaceLight,
        surfaceContainerHigh = SurfaceLight,
        surfaceContainerHighest = SurfaceLight,
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
        // 同 lightScheme：置空 surfaceTint，别让 M3 基线的紫色漏到抬升表面上
        surfaceTint = Color.Transparent,
        // 同 lightScheme：surfaceContainer* 必须显式落到面色，否则弹窗/弹层取到基线紫调
        surfaceContainerLowest = SurfaceDark,
        surfaceContainerLow = SurfaceDark,
        surfaceContainer = SurfaceDark,
        surfaceContainerHigh = SurfaceDark,
        surfaceContainerHighest = SurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = TextSecondaryDark,
        error = ErrorDark,
        onError = Color.Black,
        outline = OutlineDark,
        outlineVariant = SurfaceVariantDark
    )
}
