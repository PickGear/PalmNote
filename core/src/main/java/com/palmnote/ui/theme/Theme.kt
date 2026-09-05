package com.palmnote.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.palmnote.data.wallpaper.WallpaperPresets
import com.palmnote.data.wallpaper.decodeWallpaperBitmap

val LocalIsDarkTheme = staticCompositionLocalOf { false }
val LocalThemeColor = staticCompositionLocalOf { Color(0xFF0891B2) }

@Deprecated("Use LocalThemeColor instead", ReplaceWith("LocalThemeColor"))
val LocalSwitchColor = staticCompositionLocalOf { PrimaryGreen }

// 自定义壁纸异步解码：IO 线程 + 按屏幕尺寸降采样，结果按来源缓存，
// 避免在组合中同步解码大图（主线程卡顿 + 数十 MB 内存峰值）
@Composable
private fun rememberCustomWallpaperBitmap(source: String): ImageBitmap? {
    if (source.isEmpty()) return null
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val reqWidth = metrics.widthPixels
    val reqHeight = metrics.heightPixels
    return produceState<ImageBitmap?>(initialValue = null, source, reqWidth, reqHeight) {
        value = decodeWallpaperBitmap(context, source, reqWidth, reqHeight)
    }.value
}

@Composable
private fun rememberWallpaperData(
    style: String,
    opacity: Float,
    blur: Float,
    customUri: String,
    darkTheme: Boolean
): WallpaperData {
    val customBitmap = if (style == "custom") rememberCustomWallpaperBitmap(customUri) else null
    return when (style) {
        "none" -> WallpaperData(style = "none")
        "custom" -> customBitmap?.let {
            WallpaperData(bitmap = it, style = "custom", opacity = opacity, blur = blur)
        } ?: WallpaperData(style = "none")
        else -> {
            val preset = WallpaperPresets.getById(style)
            if (preset != null) {
                WallpaperData(
                    gradientColors = if (darkTheme) preset.darkColors else preset.lightColors,
                    style = style,
                    opacity = opacity,
                    blur = blur
                )
            } else WallpaperData(style = "none")
        }
    }
}

@Composable
fun PalmNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: Color = Color(0xFF0891B2),
    wallpaperStyle: String = "none",
    wallpaperOpacity: Float = 1f,
    wallpaperBlur: Float = 0f,
    wallpaperCustomUri: String = "",
    content: @Composable () -> Unit
) {
    val wallpaperData = rememberWallpaperData(
        style = wallpaperStyle,
        opacity = wallpaperOpacity,
        blur = wallpaperBlur,
        customUri = wallpaperCustomUri,
        darkTheme = darkTheme
    )

    val hasWallpaper = wallpaperData.style != "none"
    val lightBg = if (hasWallpaper) Color.Transparent else BackgroundLight
    val darkBg = if (hasWallpaper) Color.Transparent else BackgroundDark

    val colorScheme = if (darkTheme) ThemePackages.darkScheme(themeColor, darkBg) else ThemePackages.lightScheme(themeColor, lightBg)

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

    CompositionLocalProvider(
        LocalWallpaperData provides wallpaperData
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes
        ) {
            CompositionLocalProvider(
                LocalIsDarkTheme provides darkTheme,
                LocalThemeColor provides themeColor
            ) {
                content()
            }
        }
    }
}

@Composable
fun ColorScheme.warning(): Color = if (LocalIsDarkTheme.current) DarkWarning else Warning
