package com.palmnote.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「图片始终盖住选框」不变量的真实手势钉。
 *
 * 背景（2026-11-04 两张真机截图）：单看每个手势事件的夹取都对，但**双指并发**——
 * 一指压在四角手柄上拖框、另一指同时按在图片上平移——时，图片拖动事件夹取用的是
 * 本帧捕获的旧 `frame`，框已经被手柄那路改大了，按旧框夹出来的 offset 对新框越界，
 * 框就伸到图外。注入两条并发的指针流复现；修复后两条流里每个事件都按实时框夹取。
 *
 * 夹具是合成的纯色块图，不含任何用户照片。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class ImageCropGestureTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var imageRect = Rect.Zero
    private var frameRect = Rect.Zero

    /** 图片必须完整盖住选框：四条边都要包住（0.5px 容差吸收取整尾差）。 */
    private fun assertImageCoversFrame() {
        val img = imageRect
        val fr = frameRect
        assertTrue("图片未盖住选框：image=$img frame=$fr", img.width >= fr.width - 0.5f)
        assertTrue("图片未盖住选框：image=$img frame=$fr", img.height >= fr.height - 0.5f)
        assertTrue("左缘漏出：image=$img frame=$fr", img.left <= fr.left + 0.5f)
        assertTrue("上缘漏出：image=$img frame=$fr", img.top <= fr.top + 0.5f)
        assertTrue("右缘漏出：image=$img frame=$fr", img.right >= fr.right - 0.5f)
        assertTrue("下缘漏出：image=$img frame=$fr", img.bottom >= fr.bottom - 0.5f)
    }

    /** 1600×1000 纯色图进自由比例（账单附件）裁剪页，等几何就绪。 */
    private fun launchFreeRatioCrop() {
        val bmp = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.LTGRAY)
        val file = File(tempFolder.root, "source.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = Uri.fromFile(file)

        compose.setContent {
            com.palmnote.ui.theme.PalmNoteTheme {
                ImageCropDialogContent(
                    sourceUri = uri,
                    purpose = CropPurpose.BILL,
                    onCancel = {},
                    onDone = {},
                    saving = false,
                    onSavingChange = {},
                    onGeometryChange = { img, fr ->
                        imageRect = img
                        frameRect = fr
                    }
                )
            }
        }
        compose.waitUntil("几何未就绪") { frameRect != Rect.Zero }
        // waitUntil 可能因 SideEffect 读到「已写入但尚未重组渲染」的状态而提前返回，
        // 先等树落定，否则首个手势会打在旧一帧的手柄位置上
        compose.waitForIdle()
        compose.waitForIdle()
        assertImageCoversFrame()
    }

    /** 单指拖四角手柄：从框的右下角按下，按 [delta] 分步拖动。 */
    private fun cornerDrag(delta: Offset) {
        compose.onRoot().performTouchInput {
            down(Offset(frameRect.right, frameRect.bottom))
            repeat(8) { moveBy(delta / 8f) }
            up()
        }
        compose.waitForIdle()
    }

    /** 单指拖图片：从框中心附近（避开四角手柄热区）按下平移。 */
    private fun dragImage(delta: Offset) {
        compose.onRoot().performTouchInput {
            down(Offset(frameRect.center.x, frameRect.center.y))
            repeat(10) { moveBy(delta / 10f) }
            up()
        }
        compose.waitForIdle()
    }

    @Test
    fun `探针_撑框后拖图`() {
        launchFreeRatioCrop()
        cornerDrag(Offset(120f, 70f))
        assertImageCoversFrame()
        dragImage(Offset(60f, -40f))
        assertImageCoversFrame()
    }

    @Test
    fun `单指顺序操作全程图片盖住选框`() {
        launchFreeRatioCrop()
        // 拖图 → 缩框 → 再拖图 → 撑回框 → 再拖图：9fecd85 修的顺序路径
        dragImage(Offset(80f, 60f))
        assertImageCoversFrame()
        cornerDrag(Offset(-80f, -50f))
        assertImageCoversFrame()
        dragImage(Offset(-120f, 90f))
        assertImageCoversFrame()
        cornerDrag(Offset(120f, 70f))
        assertImageCoversFrame()
        dragImage(Offset(60f, -40f))
        assertImageCoversFrame()
    }

    @Test
    fun `拖角松手后选框回最大并放大所选区域`() {
        launchFreeRatioCrop()
        val maxFrame = frameRect
        val imageBefore = imageRect
        // 向内拖角收小选框（收小是临时的取景动作）
        cornerDrag(Offset(-70f, -40f))
        // 松手收口：选框回最大居中
        assertTrue(
            "松手后选框应回最大 frame=$frameRect max=$maxFrame",
            kotlin.math.abs(frameRect.width - maxFrame.width) < 1f &&
                kotlin.math.abs(frameRect.height - maxFrame.height) < 1f
        )
        // 图片按所选区域放大（不是停在 1x 的小框）
        assertTrue(
            "松手后图片应放大 image=$imageRect before=$imageBefore",
            imageRect.width > imageBefore.width + 1f
        )
        assertImageCoversFrame()
    }

    @Test
    fun `一指拖角收小选框同时另一指拖图片，松手收口后图片仍盖住选框`() {
        launchFreeRatioCrop()
        val maxFrame = frameRect
        // 双指并发：p0 压在右下角手柄向内拖（框临时变小），p1 同时按在图片上平移。
        // 注入的整条流在一次 setContent 的同步块里跑完，期间不发生重组——
        // 与真机上「同一帧内两路事件交错」的最坏情况等价。
        compose.onRoot().performTouchInput {
            val corner = Offset(maxFrame.right, maxFrame.bottom)
            val panStart = Offset(maxFrame.center.x, maxFrame.center.y)
            down(0, corner)
            down(1, panStart)
            repeat(4) {
                moveBy(0, Offset(-15f, -10f))
                moveBy(1, Offset(-15f, 10f))
            }
            // p0 先松手：收口在 p1 仍按住时触发，随后 p1 的平移要按新几何夹取
            up(0)
            up(1)
        }
        compose.waitForIdle()
        assertImageCoversFrame()
        // 收口后框回最大居中
        assertTrue(
            "收口后选框应回最大 frame=$frameRect max=$maxFrame",
            kotlin.math.abs(frameRect.width - maxFrame.width) < 1f
        )
    }

    @Test
    fun `捏合缩放与双击变焦全程图片盖住选框`() {
        launchFreeRatioCrop()
        val center = Offset(imageRect.center.x, imageRect.center.y)
        // 放大 → 拖 → 缩回
        compose.onRoot().performTouchInput {
            pinch(
                Offset(center.x - 80f, center.y), Offset(center.x + 80f, center.y),
                Offset(center.x + 80f, center.y + 40f), Offset(center.x + 200f, center.y + 40f)
            )
        }
        compose.waitForIdle()
        assertImageCoversFrame()
        dragImage(Offset(100f, 60f))
        assertImageCoversFrame()
        compose.onRoot().performTouchInput {
            pinch(
                Offset(center.x + 80f, center.y), Offset(center.x - 80f, center.y),
                Offset(center.x + 200f, center.y + 40f), Offset(center.x + 80f, center.y + 40f)
            )
        }
        compose.waitForIdle()
        assertImageCoversFrame()
        // 双击放大 → 拖 → 双击还原
        compose.onRoot().performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertImageCoversFrame()
        dragImage(Offset(-60f, 30f))
        assertImageCoversFrame()
        compose.onRoot().performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertImageCoversFrame()
    }

    @Test
    fun `换比例与旋转全程图片盖住选框`() {
        launchFreeRatioCrop()
        dragImage(Offset(60f, 40f))
        // 只点视口内的胶囊（320 宽的测试视口里 16:9/9:16 中心在根外，注入打不到）
        compose.onNodeWithText("1:1").performClick()
        compose.waitForIdle()
        assertImageCoversFrame()
        compose.onNodeWithText("自由比例").performClick()
        compose.waitForIdle()
        assertImageCoversFrame()
        compose.onNodeWithContentDescription("旋转图片").performClick()
        compose.waitForIdle()
        assertImageCoversFrame()
        dragImage(Offset(-40f, 30f))
        assertImageCoversFrame()
        compose.onNodeWithContentDescription("旋转图片").performClick()
        compose.waitForIdle()
        assertImageCoversFrame()
    }
}
