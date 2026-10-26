package com.palmnote.ui.theme

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 自定义壁纸：**Activity 重建后的首帧必须已经是透明背景**。
 *
 * 这是用户报的「打开 App 顶栏暗一下」里自定义图片那一半：解码在 IO 线程异步做，若首帧
 * 拿不到位图，`rememberWallpaperData` 会把 style 退回 "none" → `colorScheme.background` 先是
 * 不透明底色、解码完再变透明。屏幕上最大的一块背景色区域（顶栏）因此先亮后暗。
 *
 * `remember` 在 Activity 重建（暖启动回前台 / 旋转 / 分屏）时全部失效，所以每次重建都会
 * 重新解码 —— 这正是「每次打开 App 都闪」的原因。修复手段是把解码结果放进进程级缓存，
 * 让重建后的首帧直接有图。
 *
 * 测法：先正常解一次（填缓存），再把壁纸来源清空、切回来 —— 组合里的 `produceState` 随之
 * 销毁并重建，等价于 Activity 重建。此时记录**第一次**组合得到的背景色：必须已经是透明的。
 *
 * 用 `content://` + `registerInputStream` 提供图片而不走文件路径：`decodeWallpaperBitmap`
 * 按「以 / 开头即文件路径」分流，而 Robolectric 跑在 Windows 上时 `absolutePath` 是 `C:\...`
 * 开头，会被误判成 URI 分支。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomWallpaperFirstFrameTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `重建后首帧就直接拿到已缓存的壁纸，不再先亮后暗`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val png = tinyPng()
        assertTrue("测试用的 PNG 没编出来", png.size > 8 && png[1].toInt() == 'P'.code && png[2].toInt() == 'N'.code)

        val uri = Uri.parse("content://com.palmnote.test/wallpaper_first_frame.png")
        // 用 supplier：解码要做两遍（量尺寸 + 真解码），每次 openInputStream 都得拿到全新的流
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(png) }
        val source = uri.toString()

        var wallpaperSource by mutableStateOf(source)
        val backgrounds = mutableListOf<Color>()
        compose.setContent {
            PalmNoteTheme(darkTheme = false, wallpaperStyle = "custom", wallpaperCustomUri = wallpaperSource) {
                backgrounds += MaterialTheme.colorScheme.background
            }
        }

        // 首次解码是异步的：等它完成（背景变透明即说明解码结果已就位并写进了缓存）
        compose.waitUntil(timeoutMillis = 10_000) { backgrounds.lastOrNull() == Color.Transparent }

        // 模拟 Activity 重建：切走让 produceState 离开组合，再切回来让它重建
        backgrounds.clear()
        compose.runOnUiThread { wallpaperSource = "" }
        compose.waitForIdle()
        backgrounds.clear()
        compose.runOnUiThread { wallpaperSource = source }
        compose.waitForIdle()

        assertEquals(
            "重建后的首帧就该是透明背景（缓存里的位图已就位）；若这里是不透明底色，" +
                "说明首帧又退回了 style=\"none\"，顶栏会再闪一次",
            Color.Transparent,
            backgrounds.first()
        )
    }

    private fun tinyPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }
}
