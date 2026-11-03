package com.palmnote.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 裁剪页顶栏与比例选择器的离屏渲染核对。
 *
 * 两条用户可见的约束：
 * 1. 标题「调整图片」要相对**整条顶栏**居中 —— 右侧有两个动作（旋转 + 重置）、左侧只有一个，
 *    早先用 `Row.weight` 分剩余空间居中，标题整体偏左（2026-11-04 用户反馈截图）。
 * 2. 自由比例场景要能选常见比例，而不是只剩「自由」一个死标记。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class ImageCropChromeRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun renderTopBar() {
        compose.setContent {
            PalmNoteTheme {
                // 固定 360dp 宽，居中与否才能算得准
                Box(Modifier.width(360.dp)) {
                    CropTopBar(enabled = true, onCancel = {}, onRotate = {}, onReset = {})
                }
            }
        }
    }

    @Test
    fun `顶栏标题相对整条栏水平居中`() {
        renderTopBar()
        // 裁掉状态栏影响：onRoot 就是上面那个 360dp 宽的 Box
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText("调整图片").getUnclippedBoundsInRoot()
        val rootCenter = (root.left.value + root.right.value) / 2f
        val titleCenter = (title.left.value + title.right.value) / 2f
        // 容差 1dp：文字测量与布局取整会有不到 1px 的差
        assertEquals("标题中心应与顶栏中心重合", rootCenter, titleCenter, 1f)
    }

    @Test
    fun `顶栏左右动作都可点且文案完整`() {
        renderTopBar()
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText("重置").assertIsDisplayed()
        // 旋转是纯图标按钮，用无障碍描述定位：存在即说明不是被挤压掉了
        compose.onNodeWithContentDescription("旋转图片").assertExists()
    }

    @Test
    fun `比例选择器列出常见比例并回调正确的比例值`() {
        var picked: Float? = Float.NaN
        compose.setContent {
            PalmNoteTheme { RatioSelector(selected = null, onSelect = { picked = it }) }
        }
        listOf("自由比例", "1:1", "4:3", "3:4", "16:9", "9:16").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }

        compose.onNodeWithText("1:1").performClick()
        assertEquals(1f, picked!!, 0.001f)
        compose.onNodeWithText("4:3").performClick()
        assertEquals(4f / 3f, picked!!, 0.001f)
        compose.onNodeWithText("9:16").performClick()
        assertEquals(9f / 16f, picked!!, 0.001f)
        // 点回「自由」回调 null（不锁定）
        compose.onNodeWithText("自由比例").performClick()
        assertEquals(null, picked)
    }

    /** 遮罩离屏渲染组合不崩（绘制正确性靠真机目检）；setContent 一条用例只能调一次。 */
    @Test
    fun `矩形遮罩可渲染`() = renderOverlay(circular = false)

    @Test
    fun `圆形遮罩可渲染`() = renderOverlay(circular = true)

    private fun renderOverlay(circular: Boolean) {
        compose.setContent {
            PalmNoteTheme {
                Box(Modifier.width(320.dp)) {
                    CropOverlay(frame = Rect(40f, 40f, 280f, 280f), circular = circular)
                }
            }
        }
        compose.onRoot().assertIsDisplayed()
    }
}
