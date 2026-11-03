package com.palmnote.ui.components

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 旋转与裁切的方向一致性钉。
 *
 * 显示图是转过方向的，但高清裁切会从 `sourceUri` 重新解码出**未旋转**的原图 ——
 * 不把原图补转到同一方向，用户点旋转就等于白转（2026-11-03 真机反馈「旋转后应用不生效」：
 * 裁出来的位置错、方向还是原方向）。
 *
 * 夹具用合成的纯色块位图，不含任何用户照片。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = Application::class)
class ImageCropRotationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 2×1 横条：左红右蓝。顺时针转 90° 后应是 1×2、上红下蓝。 */
    private fun redBlueStrip(): Bitmap {
        val bmp = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, Color.RED)
        bmp.setPixel(1, 0, Color.BLUE)
        return bmp
    }

    @Test
    fun `顺时针旋转 90 度后像素按顺时针映射`() {
        val rotated = rotateBitmapQuarter(redBlueStrip(), 1)
        assertEquals("宽高应互换", 1, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals("原左侧的红转到上方", Color.RED, rotated.getPixel(0, 0))
        assertEquals("原右侧的蓝转到下方", Color.BLUE, rotated.getPixel(0, 1))
    }

    @Test
    fun `旋转 0 次与 4 次都返回原对象`() {
        val src = redBlueStrip()
        // 返回同一实例：不必要地新建位图既浪费内存，也让「转了四圈」的判断变复杂
        assertTrue(rotateBitmapQuarter(src, 0) === src)
        assertTrue(rotateBitmapQuarter(src, 4) === src)
        assertTrue(rotateBitmapQuarter(src, -4) === src)
    }

    @Test
    fun `旋转后裁切，输出是旋转方向的内容`() = runBlocking {
        // 原图 40×20 横排：左半红、右半蓝
        val src = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) {
            for (x in 0 until 40) {
                src.setPixel(x, y, if (x < 20) Color.RED else Color.BLUE)
            }
        }
        val file = File(tempFolder.root, "source.png")
        file.outputStream().use { src.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // 用户点过 1 次旋转 → 显示图 20×40、上红下蓝。
        // 确认走修复后的路径：重解码（未旋转原图）→ 补转 turns=1 → 整图裁切
        val decoded = decodeImageForCrop(context, Uri.fromFile(file))
        assertTrue("应能解码出原图", decoded != null)
        assertEquals("重解码拿到的是未旋转原图（横图）", 40, decoded!!.width)

        val oriented = rotateBitmapQuarter(decoded, 1)
        val spec = cropSpecOf(CropPurpose.ASSET_DETAIL)
        val path = cropBitmapToFile(context, oriented, spec, Rect(0f, 0f, 1f, 1f))
        assertTrue("应落盘成功", path != null)

        val out = BitmapFactory.decodeFile(path)
        assertTrue("产物应能解码", out != null)
        assertTrue("输出应是竖图，实际 ${out!!.width}×${out.height}", out.height > out.width)

        val top = out.getPixel(out.width / 2, 2)
        val bottom = out.getPixel(out.width / 2, out.height - 3)
        assertTrue(
            "上半应偏红，实际 ${Integer.toHexString(top)}",
            Color.red(top) > 140 && Color.blue(top) < 110
        )
        assertTrue(
            "下半应偏蓝，实际 ${Integer.toHexString(bottom)}",
            Color.blue(bottom) > 140 && Color.red(bottom) < 110
        )
    }
}
