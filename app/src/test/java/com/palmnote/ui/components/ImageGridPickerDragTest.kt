package com.palmnote.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「拖拽只在可拖区间启动」的真实手势钉（2026-11-06）。
 *
 * 背景：槽位行的 pointerInput 挂在整行上，onDragStart 按按压点 x 反推格子号——
 * 首图独立成槽后第 0 格是封面（不可拖），此前的 coerceIn(dragMin, dragMax) 会把
 * 按在封面/空槽上的长按「挪」到相邻可拖格：手指在封面上、抬起的却是附件 1。
 * 修复 = 越界即不启动；配套的 onDrag 守卫保证未启动的长按连 (-1, x) 事件也不发。
 *
 * 坐标自定位：以第 1 格的拖拽把手节点为锚（把手只出现在可拖格上），
 * 封面格 = 把手中心左移一个步长，不对手算布局做任何假设。
 * 夹具是合成的纯色块图，不含任何用户照片。
 *
 * 不开 `@GraphicsMode(NATIVE)`：本用例只断言重排事件（语义层），不看像素。带 NATIVE
 * 时长按手势会走原生图形路径，在 CI（Linux）上把 Robolectric 的 nativeruntime 打死：
 * 测试 JVM 直接 exit 134，日志只有 `Class not found: java/nio/CharBuffer` /
 * `JniConstants F`（2026-11-06 实测，崩点就落在本类第二个用例开始后约 100ms）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class ImageGridPickerDragTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val reorderEvents = mutableListOf<Pair<Int, Int>>()
    private var density by mutableStateOf(Density(1f))

    /** 3 张图（封面 + 2 附件，dragMin=1 / dragMax=2），自定位坐标无需手算布局。 */
    private fun launchPicker() {
        val paths = (1..3).map { n ->
            val file = File(tempFolder.root, "img$n.png")
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).let { bmp ->
                bmp.eraseColor(android.graphics.Color.LTGRAY)
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            }
            file.absolutePath
        }
        compose.setContent {
            density = LocalDensity.current
            com.palmnote.ui.theme.PalmNoteTheme {
                Box(Modifier.width(320.dp)) {
                    ImageGridPicker(
                        title = "物品图片",
                        images = paths,
                        accentColor = Color(0xFF0891B2),
                        hint = "",
                        cropPurpose = CropPurpose.ASSET,
                        additionalCropPurpose = CropPurpose.ASSET_DETAIL,
                        firstSlotLabel = "首图",
                        otherSlotLabel = "附件",
                        independentCoverSlot = true,
                        onAddImage = { _, _ -> },
                        onRemoveImage = {},
                        onReorderImages = { from, to -> reorderEvents.add(from to to) }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * 拖拽把手（第 1 格）中心在根坐标系的像素位置，与一个槽位步长（槽宽 + 间距）。
     * 把手恒出现在可拖格上，按它定位即可，不依赖任何布局细节。
     */
    private fun handleAnchor(): Pair<Offset, Float> {
        val node: SemanticsNode =
            compose.onAllNodesWithContentDescription("长按拖动排序")[0].fetchSemanticsNode()
        val center = with(density) {
            Offset(
                node.positionInRoot.x + node.size.width / 2f,
                node.positionInRoot.y + node.size.height / 2f
            )
        }
        val rootWidth = compose.onRoot().fetchSemanticsNode().size.width.toFloat()
        val spacingPx = with(density) { 8.dp.toPx() }
        val slotWidthPx = (rootWidth - spacingPx * 3) / 4f
        return center to (slotWidthPx + spacingPx)
    }

    /** 长按 [center] 向右拖（跨过 0.5 步长换位阈值，且越过 500ms 长按阈值后才动）。 */
    private fun longPressDragRight(center: Offset, stepPx: Float) {
        compose.onRoot().performTouchInput {
            down(center)
            advanceEventTime(1_000)
            val perMove = (stepPx * 0.6f / 6f).coerceAtLeast(20f)
            repeat(6) {
                moveBy(Offset(perMove, 0f))
                advanceEventTime(40)
            }
            up()
        }
        compose.waitForIdle()
    }

    @Test
    fun `长按封面格不产生任何重排事件`() {
        launchPicker()
        val (handleCenter, stepPx) = handleAnchor()
        // 封面格 = 可拖的第 1 格左移一个步长
        longPressDragRight(Offset(handleCenter.x - stepPx, handleCenter.y), stepPx)
        // 旧 coerce 行为会发出 (-1, 1)（未启动的 drag 被 onDrag 挪进可拖区间）；
        // 修复后封面格长按彻底无事件
        assertTrue("封面格不应触发重排：$reorderEvents", reorderEvents.isEmpty())
    }

    @Test
    fun `长按附件格照常重排`() {
        launchPicker()
        val (handleCenter, stepPx) = handleAnchor()
        longPressDragRight(handleCenter, stepPx)
        // 正例对照：证明长按→拖拽→换位链路本身是通的，上一条不是「测试空转绿」。
        // 同步注入流不重组，跨多次阈值都以上一次的格子号结算，故只断言「全是 1→2」
        // 而不断言次数（次数是注入节奏的产物，不是契约）。
        assertTrue(
            "附件格应触发 1→2 重排：$reorderEvents",
            reorderEvents.isNotEmpty() && reorderEvents.all { it.first == 1 && it.second == 2 }
        )
    }
}
