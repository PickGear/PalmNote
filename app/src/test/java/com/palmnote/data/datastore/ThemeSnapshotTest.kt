package com.palmnote.data.datastore

import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首帧主题 / 壁纸快照的取值核对。
 *
 * 这份快照存在的唯一理由是「首帧不能与后续帧不一样」（见 [ThemeSnapshot] 的类注释）：
 * 用户报了「换壁纸后每次打开 App 顶栏暗一下」，根因就是首帧拿了写死的默认值（= 没壁纸）。
 *
 * 所以这里钉两件事：
 * 1. **默认值必须与对应 Flow 的默认值逐个相同** —— 漏一个（比如把 customUri 默认写成 null）
 *    首帧就会和 Flow 到达后的值不一致，闪变原样复现；
 * 2. **键不能串位** —— 七个键值长得很像，写反了不报错，只是首帧错一下，靠人眼看很难发现。
 */
class ThemeSnapshotTest {

    @Test
    fun `空偏好时每一项都落到与 Flow 一致的默认值`() {
        val snapshot = resolveThemeSnapshot(preferencesOf())

        assertEquals("SYSTEM", snapshot.themeMode)
        assertEquals(PreferencesManager.DEFAULT_THEME_COLOR, snapshot.themeColor)
        assertEquals(PreferencesManager.DEFAULT_WALLPAPER_STYLE, snapshot.wallpaperStyle)
        assertEquals(PreferencesManager.DEFAULT_WALLPAPER_OPACITY, snapshot.wallpaperOpacity)
        assertEquals(PreferencesManager.DEFAULT_WALLPAPER_BLUR, snapshot.wallpaperBlur)
        assertEquals("", snapshot.wallpaperCustomUri)
        assertEquals(PreferencesManager.DEFAULT_WALLPAPER_CUSTOM_COLOR, snapshot.wallpaperCustomColor)
    }

    @Test
    fun `已设过的偏好逐项读出，七个键不能串位`() {
        val prefs = preferencesOf(
            PreferencesManager.THEME_MODE to "DARK",
            PreferencesManager.THEME_COLOR to "purple",
            PreferencesManager.WALLPAPER_STYLE to "custom",
            PreferencesManager.WALLPAPER_OPACITY to 0.6f,
            PreferencesManager.WALLPAPER_BLUR to 12f,
            PreferencesManager.WALLPAPER_CUSTOM_URI to "/data/user/0/com.palmnote/files/wallpaper_custom.jpg",
            PreferencesManager.WALLPAPER_CUSTOM_COLOR to "#123456"
        )

        val snapshot = resolveThemeSnapshot(prefs)

        assertEquals("DARK", snapshot.themeMode)
        assertEquals("purple", snapshot.themeColor)
        assertEquals("custom", snapshot.wallpaperStyle)
        assertEquals(0.6f, snapshot.wallpaperOpacity, 0f)
        assertEquals(12f, snapshot.wallpaperBlur, 0f)
        assertEquals("/data/user/0/com.palmnote/files/wallpaper_custom.jpg", snapshot.wallpaperCustomUri)
        assertEquals("#123456", snapshot.wallpaperCustomColor)
    }

    @Test
    fun `0 与 0 透明度都是有效值，不能被当成没设过`() {
        // blur = 0 表示「不模糊」，opacity = 0 表示「全透」——两者都是用户能选到的正常档位
        val snapshot = resolveThemeSnapshot(
            preferencesOf(
                PreferencesManager.WALLPAPER_BLUR to 0f,
                PreferencesManager.WALLPAPER_OPACITY to 0f
            )
        )

        assertEquals(0f, snapshot.wallpaperBlur, 0f)
        assertEquals(0f, snapshot.wallpaperOpacity, 0f)
    }
}
