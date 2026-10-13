package com.palmnote.ui.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.ui.settings.PREVIEW_IMAGE_TAG
import com.palmnote.ui.settings.WidgetPinScreen
import com.palmnote.ui.theme.PalmNoteTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 添加页（**只在应用内一键添加**，不再讲系统手动添加路径）：
 * - 目录覆盖全部 11 个组件、Provider 不重复、标题/描述/预览资源可解析，且每组都非空；
 * - 两个分支都显式锁定（不依赖阴影默认值）：启动器支持 pin → 按钮可用；不支持 → 按钮禁用 + 整页兜底文案；
 * - 透明度是「默认外观」，改的是全局默认值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetPinTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setPinSupported(supported: Boolean) {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(supported)
    }

    /** 横向滑动行 + 纵向列表都会虚拟化，屏幕上只 compose 一部分：断言「至少有一个可见按钮」而非总数。 */
    private fun visibleAddButtons() =
        compose.onAllNodesWithText(context.getString(R.string.widget_pin_add)).fetchSemanticsNodes()

    @Test
    fun `目录覆盖 11 个组件且资源都存在`() {
        assertEquals(11, WidgetPin.entries.size)
        assertEquals(11, WidgetPin.entries.map { it.providerClass }.distinct().size)
        WidgetPin.entries.forEach { entry ->
            // 引用不存在会直接抛 NotFoundException
            context.getString(entry.titleRes)
            context.getString(entry.descRes)
            context.resources.getResourceEntryName(entry.previewLayoutRes)
        }
    }

    @Test
    fun `轮播顺序把记账类排在最前`() {
        // 版式改成一次一张的轮播后没有分组标题了，顺序就是唯一的引导：
        // 最常用的记账类放最前，用户不用先滑一段才找到
        assertEquals(BillWidgetProvider::class.java, WidgetPin.entries.first().providerClass)
        assertTrue(
            "总资产应紧跟账单",
            WidgetPin.entries.take(2).any { it.providerClass == NetWorthWidgetProvider::class.java }
        )
    }

    @Test
    fun `只有账单组件在添加后弹配置页`() {
        // pin 通道不会拉起配置页，需要用户当场做选择的组件才挂成功回调自己补。
        // 目前只有账单（要选账本）；其余组件加完就完事，想调透明度可以之后长按进设置。
        val configured = WidgetPin.entries.filter { it.configureActivity != null }
        assertEquals(1, configured.size)
        assertEquals(BillWidgetProvider::class.java, configured.first().providerClass)
        assertEquals(BillWidgetConfigActivity::class.java, configured.first().configureActivity)
    }

    @Test
    fun `页面以应用内添加为主，不再出现系统手动添加引导`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }

        // 第一页就是账单：名称与说明都该在
        val first = WidgetPin.entries.first()
        compose.onNodeWithText(context.getString(first.titleRes)).assertExists()
        compose.onNodeWithText(context.getString(first.descRes)).assertExists()
        // 系统手动添加那套（机型切换 + 三步路径）已整体下线，连文案都不该留在资源里
        assertEquals(
            0,
            context.resources.getIdentifier("widget_guide_section", "string", context.packageName)
        )
    }

    @Test
    fun `启动器不支持时按钮禁用并给整页兜底`() {
        setPinSupported(false)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }

        compose.onNodeWithText(context.getString(R.string.widget_page_unsupported)).assertExists()
        val buttons = visibleAddButtons()
        assertTrue("至少应看到一个可见的添加按钮", buttons.isNotEmpty())
        buttons.forEach { node ->
            assertTrue("pin unsupported 时添加按钮应禁用", node.config.getOrNull(SemanticsProperties.Disabled) != null)
        }
    }

    @Test
    fun `启动器支持时按钮可用`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }

        val buttons = visibleAddButtons()
        assertTrue("至少应看到一个可见的添加按钮", buttons.isNotEmpty())
        buttons.forEach { node ->
            assertTrue("pin supported 时添加按钮应可用", node.config.getOrNull(SemanticsProperties.Disabled) == null)
        }
    }

    @Test
    fun `桌面实例数没变就不算添加成功`() = runTest {
        // requestPinAppWidget 返回 true 只代表「这个桌面支持应用内添加」（桌面缺权限时会静默失败），
        // 所以结论必须以「桌面实例数有没有变多」为准，不能拿返回值当成功
        assertFalse(WidgetPin.awaitIncrease(before = 1) { 1 })
    }

    @Test
    fun `桌面实例数变多即算添加成功，数到就返回`() = runTest {
        var polls = 0
        val added = WidgetPin.awaitIncrease(before = 0) {
            polls++
            if (polls >= 2) 1 else 0
        }
        assertTrue("数量变多应判为已添加", added)
        assertEquals("数到就该立刻返回，不必跑满全部轮次", 2, polls)
    }

    // ── 预览尺寸：按组件设计尺寸**等比**摆放 ──
    // 目录里的卡不再各自拉满预览区宽度（那样 1×1 和 4×3 一样宽、比例失真），而是全部乘
    // 同一个系数：maxScale = min(可用宽/340, 可用高/268)。断言值按这个式子算出来，
    // 同时锁住「尺寸不随屏幕密度变」（同一条在 2x / 3x 两种密度下量，结果必须一致）。
    // 393dp 屏：可用宽 = 393 - 24×2（页边距）- 16×2（预览区留白）= 313dp；可用高 = 272 - 32 = 240dp

    @Test
    @Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
    fun `预览按设计尺寸等比摆放 3x 屏`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        val (designW, designH) = WidgetPin.designSizeOf(WidgetPin.entries.first())!!
        val scale = previewScale()
        compose.onNodeWithTag(PREVIEW_IMAGE_TAG)
            .assertWidthIsEqualTo((designW * scale).dp)
            .assertHeightIsEqualTo((designH * scale).dp)
    }

    @Test
    @Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
    fun `预览尺寸不随屏幕密度变 2x 屏`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        val (designW, _) = WidgetPin.designSizeOf(WidgetPin.entries.first())!!
        compose.onNodeWithTag(PREVIEW_IMAGE_TAG).assertWidthIsEqualTo((designW * previewScale()).dp)
    }

    private companion object {
        const val PREVIEW_WIDTH_DP = 313f
        const val PREVIEW_HEIGHT_DP = 240f

        /** 设计稿最大卡（4×3）的尺寸，全目录统一缩放基准。 */
        const val DESIGN_MAX_WIDTH = 340f
        const val DESIGN_MAX_HEIGHT = 268f

        /** 预览卡实际的统一缩放系数（与 WidgetPinScreen 里的算法一致）。 */
        fun previewScale(): Float =
            minOf(PREVIEW_WIDTH_DP / DESIGN_MAX_WIDTH, PREVIEW_HEIGHT_DP / DESIGN_MAX_HEIGHT)
    }
}
