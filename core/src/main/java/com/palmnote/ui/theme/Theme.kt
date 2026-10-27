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
import com.palmnote.ui.components.toComposeColor

val LocalIsDarkTheme = staticCompositionLocalOf { false }
val LocalThemeColor = staticCompositionLocalOf { Color(0xFF0891B2) }

// 自定义壁纸异步解码：IO 线程 + 按屏幕尺寸降采样，结果按「来源 + 代数」缓存，
// 避免在组合中同步解码大图（主线程卡顿 + 数十 MB 内存峰值）
@Composable
private fun rememberCustomWallpaperBitmap(source: String, generation: Int): ImageBitmap? {
    if (source.isEmpty()) return null
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val reqWidth = metrics.widthPixels
    val reqHeight = metrics.heightPixels
    // 精确命中（旋转 / 分屏重建）优先；只按来源兜底一次：换图后新代数还没解出来时先用上一张顶着，
    // 等解码完再替换 —— 否则这一窗内 style 退回 "none"、顶栏又会先亮后暗闪一下。
    val cached = CustomWallpaperCache.get(source, generation) ?: CustomWallpaperCache.getStale(source)
    return produceState<ImageBitmap?>(cached, source, generation, reqWidth, reqHeight) {
        val decoded = decodeWallpaperBitmap(context, source, reqWidth, reqHeight)
        if (decoded != null) {
            CustomWallpaperCache.put(source, generation, decoded)
            value = decoded
        }
        // 解码失败（文件被删 / 瞬时 OOM）时**不**把 value 置空：initialValue 里那张还在，
        // 保留它总比让壁纸凭空消失强 —— 后者会让透明背景忽然变实，正是这次要修的观感。
    }.value
}

/**
 * 自定义壁纸解码结果的**进程级缓存**（只保留最近一条）。
 *
 * 为什么需要它：`produceState` 是异步的，每次 Activity 重建（暖启动回前台、旋转、分屏）都会
 * 重新解码一次，解码完成前首帧拿不到位图 —— `rememberWallpaperData` 会把 style 退回 "none"，
 * `colorScheme.background` 因此从不透明底色变成透明，顶栏（屏幕上最大的一块背景色区域）
 * 就「暗一下」。命中缓存后首帧直接有图，与「预设/纯色壁纸」的修复（首帧用
 * [com.palmnote.data.datastore.PreferencesManager.themeSnapshot]）合起来覆盖全部壁纸类型。
 *
 * 设计取舍：
 * - **只当首帧兜底，不替代解码**：自定义壁纸固定存到 `filesDir/wallpaper_custom.jpg`
 *   （见 GeneralSettingsScreen），**换图时 source 路径不变、只有文件内容变了**。所以缓存 key 不能
 *   只按 source —— 那样第二次换图会命中旧结果，新图要等进程重启才生效（正是这次要修的缺陷）。
 *   带上代数后，换图 → 代数 +1 → key 必然 miss → 立刻重解；旧结果只在「新代数还没解完」那一窗里
 *   当首帧兜底（`getStale`），避免顶栏又闪一次。
 * - **key 按「来源 + 代数」，不含屏幕尺寸**：旋转 / 分屏会让要求尺寸变化（宽高互换），若把尺寸
 *   算进 key，那一次重建必然 miss —— 偏偏那一刻最需要它。尺寸不符只顶一两帧，随后的解码
 *   会换成新尺寸的位图。
 * - **只缓存成功结果、只留一条**：壁纸是单份的，换壁纸写新代数，旧的自然淘汰；
 *   失败（文件被删 / URI 失效）不缓存，下次仍重试。
 *
 * 内存：缓存持有的是「屏幕上正在显示的那张」位图本身（同一个对象），不是额外复制的一份；
 * 代价只是 Activity 销毁后它仍活到进程结束。这是为「每次重建都不闪」付的账。
 */
private object CustomWallpaperCache {
    @Volatile
    private var cached: Entry? = null

    private data class Entry(val source: String, val generation: Int, val bitmap: ImageBitmap)

    fun get(source: String, generation: Int): ImageBitmap? =
        cached?.takeIf { it.source == source && it.generation == generation }?.bitmap

    /** 只按来源取（忽略代数）：换图后新代数尚未解出时，用上一张顶住首帧，避免顶栏闪一下。 */
    fun getStale(source: String): ImageBitmap? = cached?.takeIf { it.source == source }?.bitmap

    fun put(source: String, generation: Int, bitmap: ImageBitmap) {
        cached = Entry(source, generation, bitmap)
    }
}

@Composable
private fun rememberWallpaperData(
    style: String,
    opacity: Float,
    blur: Float,
    customUri: String,
    customGen: Int,
    customColor: String,
    darkTheme: Boolean
): WallpaperData {
    val customBitmap = if (style == "custom") rememberCustomWallpaperBitmap(customUri, customGen) else null
    return when (style) {
        "none" -> WallpaperData(style = "none")
        "custom" -> customBitmap?.let {
            WallpaperData(bitmap = it, style = "custom", opacity = opacity, blur = blur)
        } ?: WallpaperData(style = "none")
        "color" -> {
            val color = customColor.toComposeColor(androidx.compose.ui.graphics.Color.White)
            WallpaperData(solidColor = color, style = "color", opacity = opacity, blur = blur)
        }
        else -> {
            val preset = WallpaperPresets.getById(style)
            if (preset != null) {
                WallpaperData(
                    solidColor = if (darkTheme) preset.darkColor else preset.lightColor,
                    style = style,
                    opacity = opacity,
                    blur = blur
                )
            } else {
                WallpaperData(style = "none")
            }
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
    wallpaperCustomGen: Int = 0,
    wallpaperCustomColor: String = "#FFFFFF",
    content: @Composable () -> Unit
) {
    val wallpaperData = rememberWallpaperData(
        style = wallpaperStyle,
        opacity = wallpaperOpacity,
        blur = wallpaperBlur,
        customUri = wallpaperCustomUri,
        customGen = wallpaperCustomGen,
        customColor = wallpaperCustomColor,
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
