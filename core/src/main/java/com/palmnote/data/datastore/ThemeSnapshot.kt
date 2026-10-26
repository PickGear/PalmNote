package com.palmnote.data.datastore

import androidx.datastore.preferences.core.Preferences

/**
 * 主题 / 壁纸偏好的**同步快照**，只服务于「首帧」。
 *
 * 背景（用户报的缺陷）：换成壁纸后，每次打开 App 顶栏都会「变暗一下」。
 *
 * 主题与壁纸偏好平时走 Flow（`combine` 后 `collectAsStateWithLifecycle`），但 Flow 的第一帧
 * 还没到，UI 只能用 `initialValue` 顶上。原来的 `initialValue` 是写死的默认值 —— 也就是
 * 「没有壁纸」。于是首帧 `hasWallpaper = false`，`colorScheme.background` 是不透明的
 * [com.palmnote.ui.theme.BackgroundLight]，偏好随后到达才变透明、露出壁纸；屏幕上最大的一块
 * 「背景色区域」是顶栏，于是它先亮后暗闪一下。只有配了壁纸才看得见，与报告完全吻合。
 *
 * 这个类就是把「偏好到达后」的值提前算好交给首帧，让首帧与后续帧一致。
 *
 * 注意：它**不是** Flow 的替代品，只是 Flow 到达前的一次性兜底。
 */
data class ThemeSnapshot(
    val themeMode: String,
    val themeColor: String,
    val wallpaperStyle: String,
    val wallpaperOpacity: Float,
    val wallpaperBlur: Float,
    val wallpaperCustomUri: String,
    val wallpaperCustomColor: String
)

/**
 * 从一份偏好快照解析出首帧用的主题 / 壁纸值，默认值与对应的 Flow 完全一致
 * （`themeMode` / `themeColor` / `wallpaper*` 那几行）——两边一旦不一致，首帧又会跳变。
 *
 * 做成「吃 `Preferences` 的纯函数」是为了可测：不必起 DataStore 就能钉住每个键与默认值
 * （同 [resolveReminderAdvanceDays] 的写法）。
 */
fun resolveThemeSnapshot(prefs: Preferences): ThemeSnapshot = ThemeSnapshot(
    themeMode = prefs[PreferencesManager.THEME_MODE] ?: "SYSTEM",
    themeColor = prefs[PreferencesManager.THEME_COLOR] ?: PreferencesManager.DEFAULT_THEME_COLOR,
    wallpaperStyle = prefs[PreferencesManager.WALLPAPER_STYLE] ?: PreferencesManager.DEFAULT_WALLPAPER_STYLE,
    wallpaperOpacity = prefs[PreferencesManager.WALLPAPER_OPACITY] ?: PreferencesManager.DEFAULT_WALLPAPER_OPACITY,
    wallpaperBlur = prefs[PreferencesManager.WALLPAPER_BLUR] ?: PreferencesManager.DEFAULT_WALLPAPER_BLUR,
    wallpaperCustomUri = prefs[PreferencesManager.WALLPAPER_CUSTOM_URI] ?: "",
    wallpaperCustomColor = prefs[PreferencesManager.WALLPAPER_CUSTOM_COLOR]
        ?: PreferencesManager.DEFAULT_WALLPAPER_CUSTOM_COLOR
)
