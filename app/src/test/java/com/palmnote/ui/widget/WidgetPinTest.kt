package com.palmnote.ui.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.ui.settings.CATALOG_CELL_TAG
import com.palmnote.ui.settings.CATALOG_LIST_TAG
import com.palmnote.ui.settings.PREVIEW_IMAGE_TAG
import com.palmnote.ui.settings.SEARCH_FIELD_TAG
import com.palmnote.ui.settings.WidgetPinScreen
import com.palmnote.ui.theme.PalmNoteTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 添加页（**只在应用内一键添加**，不再讲系统的手动添加路径）一屏版式：
 * - 首屏是按用途分组的组件列表（记账 / 生活 / 效率 / 资产 / 其他），
 *   每张卡 = 大预览 + 名称 + 说明 + 尺寸切换胶囊 + 添加按钮；
 * - 同组件的多个尺寸形态合并进一张卡，点尺寸胶囊预览实时切换；
 * - 添加按钮直接在卡上，点一下就落桌；顶部搜索框按名称过滤；
 * - 目录覆盖全部 22 个组件（11 尺寸形态 + 11 基类）、Provider 不重复、
 *   标题/描述/预览资源可解析，且每组都登记了格数标签、设计尺寸与分组；
 * - manifest 覆盖：22 个 receiver 全部声明（系统选择器按 manifest 的 label 区分同族形态）；
 * - 两个分支都显式锁定（不依赖阴影默认值）：启动器支持 pin → 按钮可用；不支持 → 按钮禁用 + 整页兜底文案。
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

    /** 所有「添加到桌面」按钮（未添加时按钮文案统一是它）。 */
    private fun addButtons() =
        compose.onAllNodesWithText(context.getString(R.string.widget_pin_add))

    private fun firstPreviewHeightPx(): Int =
        compose.onAllNodesWithTag(PREVIEW_IMAGE_TAG)[0].fetchSemanticsNode().size.height

    @Test
    fun `目录覆盖 22 个尺寸形态且资源都存在`() {
        assertEquals(22, WidgetPin.entries.size)
        assertEquals(22, WidgetPin.entries.map { it.providerClass }.distinct().size)
        WidgetPin.entries.forEach { entry ->
            // 引用不存在会直接抛 NotFoundException
            context.getString(entry.titleRes)
            context.getString(entry.descRes)
            context.resources.getResourceEntryName(entry.previewLayoutRes)
            // 每个形态都要有格数标签、设计尺寸与分组，缺一个预览卡就少一条信息（没登记会静默兜底）
            assertTrue(
                "缺格数标签：${entry.providerClass.simpleName}",
                WidgetPin.cellsOf(entry).isNotBlank()
            )
            assertTrue(
                "缺设计尺寸：${entry.providerClass.simpleName}",
                WidgetPin.designSizeOf(entry) != null
            )
            assertNotNull("缺分组：${entry.providerClass.simpleName}", WidgetPin.groupOf(entry))
        }
    }

    @Test
    fun `22 个形态折叠成 11 个组件并归 5 组`() {
        val catalog = WidgetPin.catalog()
        assertEquals(5, catalog.size)
        val components = catalog.flatMap { it.components }
        assertEquals(11, components.size)
        // 每个组件至少 1 个尺寸，合计 22；同组件 titleRes 相同（族键），不同组件不同
        assertTrue(components.all { it.entries.isNotEmpty() })
        assertEquals(22, components.sumOf { it.entries.size })
        assertEquals(11, components.map { it.familyKey }.distinct().size)
    }

    @Test
    fun `分组把记账类排在最前`() {
        // 分组顺序是唯一的引导：最常用的记账类占首组首位，用户不用先滚一段才找到
        val catalog = WidgetPin.catalog()
        assertEquals(WidgetPin.WidgetGroup.BILLING, catalog.first().group)
        val firstComponent = catalog.first().components.first()
        assertEquals(
            context.getString(WidgetPin.entries.first().titleRes),
            context.getString(firstComponent.titleRes)
        )
    }

    @Test
    fun `manifest 声明全部 22 个 receiver 且带 label`() {
        // 目录只是应用自己的账本，落桌和系统选择器认的是 manifest：
        // 漏一个 receiver 组件就发不出更新，同族形态又全靠 android:label 区分
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE),
            0
        )
        WidgetPin.entries.forEach { entry ->
            val name = entry.providerClass.name
            val info = receivers.firstOrNull { it.activityInfo.name == name }
            assertNotNull("manifest 缺少 receiver：$name", info)
            val activityInfo = info!!.activityInfo
            val hasLabel = activityInfo.labelRes != 0 || activityInfo.nonLocalizedLabel != null
            assertTrue("receiver 缺 android:label：$name", hasLabel)
        }
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

        // 首屏就有账单卡：卡里有名称
        val first = WidgetPin.entries.first()
        compose.onAllNodesWithText(context.getString(first.titleRes)).assertCountEquals(1)
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

        // 前提提示在列表下方就先讲清楚（全组件通用的前提）
        compose.onNodeWithText(context.getString(R.string.widget_page_unsupported)).assertExists()
        // 所有添加按钮禁用
        val buttons = addButtons().fetchSemanticsNodes()
        assertTrue("至少应看到一个可见的添加按钮", buttons.isNotEmpty())
        buttons.forEach { node ->
            assertTrue("pin unsupported 时添加按钮应禁用", node.config.getOrNull(SemanticsProperties.Disabled) != null)
        }
    }

    @Test
    fun `启动器支持时按钮可用`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }

        val buttons = addButtons().fetchSemanticsNodes()
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

    // ── 一屏版式：分组 / 尺寸切换 / 搜索 / 读屏 ──

    @Test
    fun `首屏首卡是账单且能滚到列表深处`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        // 首卡 = 账单：最常用的记账类占头位，不用先滚一段才找到
        // （卡的合成读屏描述 = 名称 + 尺寸标，首卡内容即目录第一条）
        val first = WidgetPin.entries.first()
        val firstCellDesc = "${context.getString(first.titleRes)}，${WidgetPin.cellsOf(first)}"
        compose.onAllNodesWithTag(CATALOG_CELL_TAG)[0].assert(hasContentDescription(firstCellDesc))
        // 列表可滚：滚到密码本（资产组，在列表深处）证明不是只摆了首屏几张卡
        val vaultTitle = context.getString(R.string.widget_vault_title)
        compose.onNodeWithTag(CATALOG_LIST_TAG).performScrollToNode(hasText(vaultTitle))
        compose.onNodeWithText(vaultTitle).assertExists()
    }

    @Test
    fun `卡片有读屏描述`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        val first = WidgetPin.entries.first()
        // 卡：名称 + 尺寸标合成一条描述（未添加时就这两项，图与卡本身无从辨认）
        val cellDesc = "${context.getString(first.titleRes)}，${WidgetPin.cellsOf(first)}"
        compose.onNode(hasContentDescription(cellDesc)).assertExists()
    }

    @Test
    fun `点尺寸胶囊切换预览`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        // 搜出多尺寸组件（待办清单 = 4×3 + 2×1 + 4×1）；搜索词走资源，英文 locale 下也成立
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_todo_title))
        // 默认选中基类 4×3（高 268 比例，预览最高）
        val before = firstPreviewHeightPx()
        // 点 2×1（160×74，明显更矮）
        compose.onNodeWithText("2×1").performClick()
        val after = firstPreviewHeightPx()
        assertTrue("切换尺寸后预览应变矮：before=$before after=$after", after < before)
    }

    @Test
    fun `搜索过滤组件`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        // 搜密码本 → 只剩密码本一个组件卡（搜索框里也有同样的字，用卡片数而不是标题文本判定）
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_vault_title))
        compose.onAllNodesWithTag(CATALOG_CELL_TAG).assertCountEquals(1)
        // 不匹配的账单卡应消失
        compose.onAllNodesWithText(context.getString(R.string.widget_bill_title)).assertCountEquals(0)
    }

    @Test
    fun `小米权限条只在没成功落桌过时出现`() {
        // 「桌面快捷方式」权限没有公开查询法，但成功落桌过就是权限已开的实证：那之后不再打扰
        assertTrue(WidgetPin.showMiuiPermStrip(pinSupported = true, isMiui = true, pinOk = false))
        assertFalse(WidgetPin.showMiuiPermStrip(pinSupported = true, isMiui = true, pinOk = true))
        assertFalse(WidgetPin.showMiuiPermStrip(pinSupported = true, isMiui = false, pinOk = false))
        assertFalse(WidgetPin.showMiuiPermStrip(pinSupported = false, isMiui = true, pinOk = false))
    }

    @Test
    fun `落桌核对期按钮转确认中并禁用`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        // 点首张卡的添加（先把列表滚到按钮——首卡偏高，按钮可能在首屏视口外）：
        // 返回值只说明通道可用，核对期（最多 5×1.5s）内必须锁住按钮，防止连发确认框
        compose.onNodeWithTag(CATALOG_LIST_TAG)
            .performScrollToNode(hasText(context.getString(R.string.widget_pin_add)))
        addButtons()[0].performClick()
        val confirming = context.getString(R.string.widget_page_add_confirming)
        compose.onNodeWithText(confirming).assertIsDisplayed()
        compose.onNodeWithText(confirming).assertIsNotEnabled()
    }

    // ── 预览尺寸：按组件设计尺寸**等比**摆放 ──
    // 卡内预览盒固定高 110dp、宽 = 屏宽 - 列表边距 - 卡片内边。断言值按统一系数算：
    // scale = min(可用宽/340, 可用高/268)。同时锁住「尺寸不随屏幕密度变」
    // （同一条在 2x / 3x 两种密度下量，结果必须一致）。
    // 393dp 屏：可用宽 = 393 - 16×2（列表边距）- 12×2（卡片内边）= 337dp；可用高 = 110dp

    @Test
    @Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
    fun `预览按设计尺寸等比摆放 3x 屏`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        val first = WidgetPin.entries.first()
        val (designW, designH) = WidgetPin.designSizeOf(first)!!
        val scale = previewScale()
        compose.onAllNodesWithTag(PREVIEW_IMAGE_TAG)[0]
            .assertWidthIsEqualTo((designW * scale).dp)
            .assertHeightIsEqualTo((designH * scale).dp)
    }

    @Test
    @Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
    fun `预览尺寸不随屏幕密度变 2x 屏`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        val first = WidgetPin.entries.first()
        val (designW, _) = WidgetPin.designSizeOf(first)!!
        compose.onAllNodesWithTag(PREVIEW_IMAGE_TAG)[0]
            .assertWidthIsEqualTo((designW * previewScale()).dp)
    }

    private companion object {
        const val PREVIEW_WIDTH_DP = 337f
        const val PREVIEW_HEIGHT_DP = 110f

        /** 设计稿最大卡（4×3）的尺寸，全目录统一缩放基准。 */
        const val DESIGN_MAX_WIDTH = 340f
        const val DESIGN_MAX_HEIGHT = 268f

        /** 预览卡实际的统一缩放系数（与 WidgetPinScreen 里的算法一致）。 */
        fun previewScale(): Float =
            minOf(PREVIEW_WIDTH_DP / DESIGN_MAX_WIDTH, PREVIEW_HEIGHT_DP / DESIGN_MAX_HEIGHT)
    }
}
