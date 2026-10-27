package com.palmnote.ui.theme

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
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
import java.util.concurrent.atomic.AtomicReference

/**
 * 自定义壁纸：**同一个来源路径换一张图，壁纸必须真的换过来**。
 *
 * 用户报的缺陷是「换了壁纸，再换一次不生效」。根因有两层，都在解码链路上：
 *
 * 1. 自定义图片固定覆盖写到同一个文件（`filesDir/wallpaper_custom.jpg`），所以第二次选图时
 *    `wallpaper_custom_uri` 存的字符串**和上次一模一样**。只写这一个键，下游的 MutableState
 *    判等直接跳过 → 整条链路都不会重组。
 * 2. 就算重组了也没用：`produceState` 是异步的，它的 key 只有 `source` 与屏幕尺寸 —— 两者都没变，
 *    effect 不会重启，屏幕上的还是第一次解码出来的旧位图；进程级缓存的 key 同样只有 source，
 *    也必然命中旧结果。
 *
 * 修复是引入「代数」：每选一次图 +1，作为解码结果的失效凭据。缓存 key 与 `produceState` key
 * 都带上它，换图 → 代数变 → 必然 miss → 立刻重解。
 *
 * 测法：同一 URI、同一尺寸，只把代数从 0 推到 1，同时把底层文件换成另一张图 —— 记录的壁纸位图
 * 像素必须跟着变。旧代码在这里连一次解码都不会触发，`bitmaps` 从头到尾是同一个对象。
 *
 * 用 `content://` + `registerInputStreamSupplier` 提供图片而不走文件路径：`decodeWallpaperBitmap`
 * 按「以 / 开头即文件路径」分流，而 Robolectric 跑在 Windows 上时 `absolutePath` 是 `C:\...`
 * 开头，会被误判成 URI 分支。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomWallpaperRegenerationTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `同一来源路径换图后按代数重新解码，壁纸换成新图`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val uri = Uri.parse("content://com.palmnote.test/wallpaper_regenerate.png")

        // 两张颜色不同的图。用 AtomicReference 是因为解码跑在 IO 线程读这份内容。
        // 用 supplier：解码要做两遍（量尺寸 + 真解码），每次 openInputStream 都得拿到全新的流。
        val current = AtomicReference(solidPng(0xFFFF0000.toInt()))
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(current.get()) }

        var generation by mutableStateOf(0)
        val bitmaps = mutableListOf<ImageBitmap?>()
        val seenGens = mutableListOf<Int>()
        compose.setContent {
            PalmNoteTheme(
                darkTheme = false,
                wallpaperStyle = "custom",
                wallpaperCustomUri = uri.toString(),
                wallpaperCustomGen = generation
            ) {
                seenGens += generation
                bitmaps += LocalWallpaperData.current.bitmap
            }
        }

        // 等第一张解出来
        compose.waitUntil(timeoutMillis = 15_000) {
            bitmaps.last().let { it != null && pixel(it) != 0 }
        }
        val before = pixel(bitmaps.last())

        // 第二次换图：来源路径不变、只有文件内容变了 —— 旧代码在这里什么都不动
        compose.runOnUiThread {
            current.set(solidPng(0xFF00FF00.toInt()))
            generation = 1
        }
        compose.waitForIdle()
        assertTrue("代数没有进到组合里：界面根本没收到「换了图」的通知", seenGens.last() == 1)
        compose.waitUntil(timeoutMillis = 15_000) {
            pixel(bitmaps.last()) != before
        }

        assertNotEquals(
            "第二次换壁纸后画面没换过来：来源路径不变时只有代数变化才能触发重新解码",
            before,
            pixel(bitmaps.last())
        )
    }

    private fun pixel(bitmap: ImageBitmap?): Int =
        bitmap?.asAndroidBitmap()?.getPixel(4, 4) ?: 0

    private fun solidPng(argb: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        for (x in 0 until 8) for (y in 0 until 8) bitmap.setPixel(x, y, argb)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }
}
