package com.palmnote.ui.components

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 位图所有权钉：`cropBitmapToFile` 只允许回收**它自己新建**的位图。
 *
 * 背景：`Bitmap.createBitmap(src, 0, 0, w, h)` 在「整图 + 无变换」时按 AOSP 实现会
 * `return source` —— 也就是原样返回入参。旧实现在 finally 里无条件 `cropped.recycle()`，
 * 于是自由比例场景（一打开就是整图）点「使用这张」会把裁剪页正在显示的位图回收掉，
 * 下一帧重绘直接崩「trying to use a recycled bitmap」。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = Application::class)
class ImageCropOwnershipTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 造一张不透明的纯色图（JPEG 源图的实际形态：无 alpha）。 */
    private fun opaqueBitmap(width: Int, height: Int, color: Int = Color.GREEN): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        bmp.setHasAlpha(false)
        return bmp
    }

    @Test
    fun `整图裁剪不回收调用方的位图`() = runBlocking {
        val src = opaqueBitmap(60, 40)
        // ASSET_DETAIL 是自由比例、长边 1600：60x40 不会被缩放，裁剪区域又正好是整图，
        // 正是 createBitmap 原样返回入参的那条路径
        val path = cropBitmapToFile(context, src, cropSpecOf(CropPurpose.ASSET_DETAIL), Rect(0f, 0f, 1f, 1f))

        assertTrue("应落盘成功", path != null)
        assertFalse("不能回收调用方还在显示的位图", src.isRecycled)
        // 还能继续读像素：Compose 下一帧要拿它重绘
        assertEquals(Color.GREEN, src.getPixel(1, 1))
    }

    @Test
    fun `整图裁剪的产物内容正确`() = runBlocking {
        val src = opaqueBitmap(60, 40, Color.GREEN)
        val path = cropBitmapToFile(context, src, cropSpecOf(CropPurpose.ASSET_DETAIL), Rect(0f, 0f, 1f, 1f))!!
        val out = BitmapFactory.decodeFile(path)
        assertTrue("产物应能解码", out != null)
        assertEquals("尺寸应保持原图（不缩放）", 60, out!!.width)
        assertEquals(40, out.height)
        val pixel = out.getPixel(30, 20)
        assertTrue("中间像素应是绿色，实际 ${Integer.toHexString(pixel)}", Color.green(pixel) > 140)
    }

    @Test
    fun `需要缩放时也不回收调用方的位图`() = runBlocking {
        val src = opaqueBitmap(60, 40)
        // 物品首图 1:1 / 1024：既要裁剪又要放大，走 scaled !== cropped 的另一条路径
        val path = cropBitmapToFile(context, src, cropSpecOf(CropPurpose.ASSET), Rect(0.1f, 0.1f, 0.9f, 0.9f))

        assertTrue("应落盘成功", path != null)
        assertFalse("缩放路径同样不能回收入参", src.isRecycled)
    }
}
