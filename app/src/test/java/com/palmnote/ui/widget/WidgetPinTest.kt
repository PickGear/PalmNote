package com.palmnote.ui.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.ui.settings.ADDED_BADGE_TAG
import com.palmnote.ui.settings.CATALOG_CELL_TAG
import com.palmnote.ui.settings.CATALOG_LIST_TAG
import com.palmnote.ui.settings.PREVIEW_IMAGE_TAG
import com.palmnote.ui.settings.QUICK_ADD_TAG
import com.palmnote.ui.settings.SEARCH_FIELD_TAG
import com.palmnote.ui.settings.SHEET_ADD_TAG
import com.palmnote.ui.settings.SHEET_DOT_TAG
import com.palmnote.ui.settings.SHEET_DOTS_TAG
import com.palmnote.ui.settings.SHEET_OPACITY_ROW_TAG
import com.palmnote.ui.settings.SHEET_PREVIEW_TAG
import com.palmnote.ui.settings.SHEET_SIZE_ROW_TAG
import com.palmnote.ui.settings.SHEET_TAG
import com.palmnote.ui.settings.WidgetPinScreen
import com.palmnote.ui.settings.pickedOpacityForNewInstance
import com.palmnote.ui.theme.PalmNoteTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 添加页（**只在应用内一键添加**，不再讲系统的手动添加路径）画廊版式：
 * - 首屏是按用途分组的**画廊**（记账 / 生活 / 效率 / 资产 / 其他），两列铺开，
 *   每张卡 = 组件预览（垫淡底托）+ 名称 + 尺寸标/桌面实例数 + 右下角「＋」；
 * - 点某张卡弹出**近全高的面板**（对齐各家「添加小部件」的观感）：名称 + 说明 + 大预览
 *   （桌面**真实尺寸**、浮在白底上，左右滑动换尺寸、下缘一排分页圆点）+ 尺寸胶囊
 *   + 透明度档位（有配置页的组件不摆这一行）+ 添加按钮，当场选当场落桌；卡上的「＋」不进面板，
 *   按当前尺寸直接落桌；已落桌的卡在预览右上角带小勾；
 * - 同组件的多个尺寸形态合并成一张卡，面板里换尺寸后预览实时切换；顶部搜索框按名称过滤；
 *   输入后触碰画廊 / 按 IME「搜索」键都会收起键盘，搜索时看卡不被键盘挡住；
 * - 目录覆盖全部 22 个组件（11 尺寸形态 + 11 基类）、Provider 不重复、
 *   标题/描述/预览资源可解析，且每组都登记了格数标签、设计尺寸与分组；
 * - manifest 覆盖：22 个 receiver 全部声明（系统选择器按 manifest 的 label 区分同族形态）；
 * - 两个分支都显式锁定（不依赖阴影默认值）：启动器支持 pin → 面板按钮可用；不支持 → 按钮禁用 + 整页兜底文案。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class WidgetPinTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setPinSupported(supported: Boolean) {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(supported)
    }

    private fun render() {
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
    }

    /** 点开首张画廊卡（首卡 = 账单），弹出它的底部面板。 */
    private fun openFirstTile() {
        compose.onAllNodesWithTag(CATALOG_CELL_TAG)[0].performClick()
    }

    private fun isAddDisabled(): Boolean =
        compose.onNodeWithTag(SHEET_ADD_TAG).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Disabled) != null

    /** 卡片右下角「＋」是否禁用（enabled 的按钮不带 Disabled 语义键）。 */
    private fun isQuickAddDisabled(): Boolean =
        compose.onAllNodesWithTag(QUICK_ADD_TAG)[0].fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Disabled) != null

    /** 搜索框是否持焦（输入后为 true；收起键盘/离开焦点后 Focused 键归零）。 */
    private fun isSearchFocused(): Boolean =
        compose.onNodeWithTag(SEARCH_FIELD_TAG).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Focused) == true

    /** 搜索入口在顶栏：先点搜索图标，整行搜索框才出现并可以输入。 */
    private fun openSearch() {
        compose.onNodeWithContentDescription(context.getString(R.string.search)).performClick()
    }

    /** 面板大预览当前的尺寸：换尺寸后它会跟着变（各形态的尺寸不同）。 */
    private fun previewSize(): IntSize =
        compose.onNodeWithTag(SHEET_PREVIEW_TAG).fetchSemanticsNode().size

    /** 在面板大预览上横滑一段（真指针：按下 → 位移 → 抬起）。 */
    private fun swipePreview(dx: Float) {
        compose.onNodeWithTag(SHEET_PREVIEW_TAG).performTouchInput {
            down(center)
            moveBy(Offset(dx, 0f))
            up()
        }
    }

    // ── 目录 / manifest（与版式无关） ──

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

    // ── 画廊版式：分组 / 搜索 / 面板 / 尺寸切换 ──

    @Test
    fun `页面以应用内添加为主，不再出现系统手动添加引导`() {
        setPinSupported(true)
        render()

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
    fun `首屏首卡是账单且能滚到画廊深处`() {
        setPinSupported(true)
        render()
        // 首卡 = 账单：最常用的记账类占头位，不用先滚一段才找到
        compose.onAllNodesWithText(context.getString(R.string.widget_bill_title)).assertCountEquals(1)
        // 网格可滚：滚到密码本（资产组，在画廊深处）证明不是只摆了首屏几张卡
        val vaultTitle = context.getString(R.string.widget_vault_title)
        compose.onNodeWithTag(CATALOG_LIST_TAG).performScrollToNode(hasText(vaultTitle))
        compose.onNodeWithText(vaultTitle).assertExists()
    }

    @Test
    fun `画廊按分组铺开，组标题可见`() {
        setPinSupported(true)
        render()
        // 首屏就是记账组；往下滚能看到生活组标题（组标题横跨整行，是分组的唯一提示）
        compose.onNodeWithText(context.getString(R.string.widget_group_billing)).assertExists()
        val life = context.getString(R.string.widget_group_life)
        compose.onNodeWithTag(CATALOG_LIST_TAG).performScrollToNode(hasText(life))
        compose.onNodeWithText(life).assertExists()
    }

    @Test
    fun `搜索过滤组件`() {
        setPinSupported(true)
        render()
        // 搜索框不常驻：进页面时顶栏只有图标，点开才换成搜索框（与账单 / 资产各页一致）
        compose.onAllNodesWithTag(SEARCH_FIELD_TAG).assertCountEquals(0)
        // 搜密码本 → 只剩密码本一个组件卡（搜索框里也有同样的字，用卡片数而不是标题文本判定）
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_vault_title))
        compose.onAllNodesWithTag(CATALOG_CELL_TAG).assertCountEquals(1)
        // 不匹配的账单卡应消失
        compose.onAllNodesWithText(context.getString(R.string.widget_bill_title)).assertCountEquals(0)
    }

    @Test
    fun `搜索也能匹配说明文字`() {
        setPinSupported(true)
        render()
        // 整句说明当关键词：名称里没有这几个字，能命中说明就说明说明也参与了匹配
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_bill_desc))
        compose.onAllNodesWithTag(CATALOG_CELL_TAG).assertCountEquals(1)
        compose.onNodeWithText(context.getString(R.string.widget_bill_title)).assertExists()
    }

    @Test
    fun `搜索时名称命中排在说明命中前面`() {
        setPinSupported(true)
        render()
        // 「待办」既是待办清单的名称，也出现在概览的说明里（「收支、待办、打卡一屏看完」）——
        // 名称命中的待办清单必须排在前面
        val keyword = context.getString(R.string.widget_todo_title).take(2)
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput(keyword)
        compose.onAllNodesWithTag(CATALOG_CELL_TAG)[0]
            .assert(hasText(context.getString(R.string.widget_todo_title), substring = true))
    }

    @Test
    fun `搜索无结果时给空态提示`() {
        setPinSupported(true)
        render()
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("zzz-no-such-widget")
        compose.onAllNodesWithTag(CATALOG_CELL_TAG).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.search_no_results)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_search_empty_hint)).assertExists()
    }

    @Test
    fun `单尺寸组件面板不摆尺寸行`() {
        setPinSupported(true)
        render()
        // 本月记账只有 3×2 一个形态，摆一排只有一颗胶囊的尺寸行像坏了
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_bill_title))
        openFirstTile()
        compose.onNodeWithTag(SHEET_ADD_TAG).assertExists()
        compose.onAllNodesWithTag(SHEET_SIZE_ROW_TAG).assertCountEquals(0)
    }

    @Test
    fun `多尺寸组件面板有尺寸行`() {
        setPinSupported(true)
        render()
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_todo_title))
        openFirstTile()
        compose.onNodeWithTag(SHEET_SIZE_ROW_TAG).assertExists()
    }

    @Test
    fun `面板的分页圆点数等于该组件的尺寸个数`() {
        setPinSupported(true)
        render()
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_todo_title))
        openFirstTile()
        // 圆点让「左右滑动换尺寸」看得见：点数 = 尺寸形态个数（待办 = 4×3 + 2×1 + 4×1）
        val sizes = WidgetPin.entries.count { it.titleRes == R.string.widget_todo_title }
        compose.onNodeWithTag(SHEET_DOTS_TAG).assertExists()
        compose.onAllNodesWithTag(SHEET_DOT_TAG).assertCountEquals(sizes)
    }

    @Test
    fun `单尺寸组件面板不摆分页圆点`() {
        setPinSupported(true)
        render()
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_bill_title))
        openFirstTile()
        compose.onAllNodesWithTag(SHEET_DOTS_TAG).assertCountEquals(0)
    }

    @Test
    fun `点卡片弹出面板，面板里有预览和添加按钮`() {
        setPinSupported(true)
        render()
        openFirstTile()
        compose.onNodeWithTag(SHEET_TAG).assertExists()
        compose.onNodeWithTag(SHEET_PREVIEW_TAG).assertExists()
        compose.onNodeWithTag(SHEET_ADD_TAG).assertExists()
    }

    @Test
    fun `面板里点尺寸胶囊切换大预览`() {
        setPinSupported(true)
        render()
        // 搜出多尺寸组件（待办清单 = 4×3 + 2×1 + 4×1）；搜索词走资源，英文 locale 下也成立
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_todo_title))
        openFirstTile()
        // 默认选中基类 4×3（340×268，面板里最大），点 2×1（160×74）后宽高都应该明显变小
        val before = previewSize()
        compose.onNodeWithText("2×1").performClick()
        val after = previewSize()
        assertTrue(
            "切换尺寸后大预览应变小（宽高都小）：before=$before after=$after",
            after.width < before.width && after.height < before.height
        )
    }

    @Test
    fun `面板里左滑预览换下一个尺寸，右滑换回上一个`() {
        setPinSupported(true)
        render()
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_todo_title))
        openFirstTile()
        // 手势与尺寸胶囊等效：大预览是面板里最大的一块，滑它比去够下面那排胶囊顺手
        val base = previewSize()
        swipePreview(-150f)
        val next = previewSize()
        assertTrue("左滑应换到下一个（更小的）尺寸：before=$base after=$next", next.width < base.width)
        // 右滑切回上一个：方向没搞反
        swipePreview(150f)
        assertEquals("右滑应切回原尺寸", base, previewSize())
    }

    @Test
    fun `面板里滑动位移不够就不换尺寸`() {
        setPinSupported(true)
        render()
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_todo_title))
        openFirstTile()
        // 手指按在预览上轻轻一挪（不足阈值）不算换尺寸，免得误触
        val base = previewSize()
        swipePreview(-20f)
        assertEquals("位移不到阈值不该换尺寸", base, previewSize())
    }

    @Test
    fun `卡片加号不进面板，就地走落桌核对`() {
        setPinSupported(true)
        render()
        // 快捷加号按当前尺寸直接落桌：不弹面板，核对期把加号锁住（不然连点会连发系统确认框）
        compose.onAllNodesWithTag(QUICK_ADD_TAG)[0].performClick()
        compose.onAllNodesWithTag(SHEET_TAG).assertCountEquals(0)
        assertTrue("核对期应锁住卡片加号", isQuickAddDisabled())
    }

    @Test
    fun `桌面不支持时卡片加号禁用`() {
        setPinSupported(false)
        render()
        assertTrue("pin unsupported 时加号应禁用", isQuickAddDisabled())
    }

    @Test
    fun `卡片加号触控盒达 48dp 最小触控`() {
        setPinSupported(true)
        render()
        compose.onAllNodesWithTag(QUICK_ADD_TAG)[0]
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun `已落桌的卡片带小勾角标`() {
        setPinSupported(true)
        // 首卡（账单）桌面上已有一个实例：一眼就看得出它已经在桌面上，不必进面板翻数量
        // （用 bindAppWidgetId 而不是 createWidget：后者会真的去 inflate RemoteViews）
        shadowOf(AppWidgetManager.getInstance(context))
            .bindAppWidgetId(1, ComponentName(context, BillWidgetProvider::class.java))
        render()
        // 卡片是可点的，会把子节点语义合并上去 —— 角标要按未合并树找
        compose.onAllNodesWithTag(ADDED_BADGE_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `没落桌的卡片不带小勾角标`() {
        setPinSupported(true)
        render()
        compose.onAllNodesWithTag(ADDED_BADGE_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `键盘搜索键收起键盘`() {
        setPinSupported(true)
        render()
        // 输入即获焦；此前「搜索」键没接线，点了毫无动作
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput(
            context.getString(R.string.widget_todo_title).take(2)
        )
        assertTrue("输入后搜索框应持有焦点", isSearchFocused())
        // IME「搜索」= 搜完收键盘：结果就在下面这屏，没有「提交」这一步
        compose.onNodeWithTag(SEARCH_FIELD_TAG).performImeAction()
        assertFalse("按搜索键应把焦点放掉", isSearchFocused())
    }

    @Test
    fun `点画廊把键盘收掉`() {
        setPinSupported(true)
        render()
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput(
            context.getString(R.string.widget_todo_title).take(2)
        )
        assertTrue("输入后搜索框应持有焦点", isSearchFocused())
        // 搜索时看卡要滑网格：触碰画廊（真指针 down/up）下发即收键盘，别让键盘盖着半屏卡
        compose.onAllNodesWithTag(CATALOG_CELL_TAG)[0].performTouchInput { down(center); up() }
        assertFalse("触碰画廊应收起键盘", isSearchFocused())
        compose.onNodeWithTag(SHEET_TAG).assertExists()
    }

    @Test
    fun `启动器不支持时面板按钮禁用并给整页兜底`() {
        setPinSupported(false)
        render()
        // 前提提示在画廊下方就先讲清楚（全组件通用的前提）
        compose.onNodeWithText(context.getString(R.string.widget_page_unsupported)).assertExists()
        openFirstTile()
        assertTrue("pin unsupported 时添加按钮应禁用", isAddDisabled())
    }

    @Test
    fun `启动器支持时面板按钮可用`() {
        setPinSupported(true)
        render()
        openFirstTile()
        assertTrue("pin supported 时添加按钮应可用", !isAddDisabled())
        assertNull(
            "可用按钮不该带 Disabled 语义",
            compose.onNodeWithTag(SHEET_ADD_TAG).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Disabled)
        )
    }

    @Test
    fun `落桌核对期按钮转确认中并禁用`() {
        setPinSupported(true)
        render()
        // 点首张卡进面板，再点添加：返回值只说明通道可用，核对期（最多 5×1.5s）内必须锁住按钮
        openFirstTile()
        compose.onNodeWithTag(SHEET_ADD_TAG).performClick()
        val confirming = context.getString(R.string.widget_page_add_confirming)
        compose.onNodeWithText(confirming).assertIsDisplayed()
        compose.onNodeWithText(confirming).assertIsNotEnabled()
    }

    @Test
    fun `面板里能挑透明度，带配置页的组件不摆这一行`() {
        setPinSupported(true)
        render()
        // 待办清单没有配置页：面板里先挑一档透明度，落桌时写到新实例上（各家「可编辑的小组件」都这么做）
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_todo_title))
        openFirstTile()
        compose.onNodeWithTag(SHEET_OPACITY_ROW_TAG).assertExists()
        val follow = context.getString(R.string.widget_pin_opacity_follow)
        compose.onNodeWithText(follow).assertIsSelected()
        compose.onNodeWithText("60%").performClick()
        compose.onNodeWithText("60%").assertIsSelected()
        compose.onNodeWithText(follow).assertIsNotSelected()
    }

    @Test
    fun `账单有配置页，面板里不重复摆透明度`() {
        setPinSupported(true)
        render()
        // 账单落桌后系统会拉起它的配置页（账本 + 透明度都在那儿），面板里再来一档会互相覆盖
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_bill_title))
        openFirstTile()
        compose.onAllNodesWithTag(SHEET_OPACITY_ROW_TAG).assertCountEquals(0)
    }

    @Test
    fun `面板里挑的透明度只写给没有配置页的组件`() {
        val todo = WidgetPin.entries.first { it.titleRes == R.string.widget_todo_title }
        val bill = WidgetPin.entries.first { it.titleRes == R.string.widget_bill_title }
        assertEquals(0.6f, pickedOpacityForNewInstance(todo, 0.6f))
        assertNull(
            "带配置页的组件由它自己的配置页管透明度，落桌时不再写一遍",
            pickedOpacityForNewInstance(bill, 0.6f)
        )
        assertNull("没挑（跟随默认）就什么都不写", pickedOpacityForNewInstance(todo, null))
    }

    @Test
    fun `面板占屏高九成上下（对齐各家近全高面板）`() {
        setPinSupported(true)
        render()
        openFirstTile()
        val screen = context.resources.configuration.screenHeightDp * compose.density.density
        val sheet = compose.onNodeWithTag(SHEET_TAG).fetchSemanticsNode().size.height.toFloat()
        // 可用高度还要扣掉系统栏与把手，所以拿屏高算出来的比例会略低于 0.92，取一个宽容区间
        assertTrue("面板应近全高：sheet=$sheet screen=$screen", sheet > screen * 0.80f)
        assertTrue("但不该顶满整屏：sheet=$sheet screen=$screen", sheet < screen * 0.95f)
    }

    @Test
    fun `面板大预览就是桌面真实尺寸，不再放大`() {
        setPinSupported(true)
        render()
        openSearch()
        compose.onNodeWithTag(SEARCH_FIELD_TAG)
            .performTextInput(context.getString(R.string.widget_todo_title))
        openFirstTile()
        // 待办默认基类 4×3 = 340×268dp：面板里就按这么大摆（各家「添加小部件」都是 1:1 真尺寸）
        val (w, h) = WidgetPin.designSizeOf(
            WidgetPin.entries.first { it.titleRes == R.string.widget_todo_title }
        )!!
        compose.onNodeWithTag(SHEET_PREVIEW_TAG)
            .assertWidthIsEqualTo(w.dp)
            .assertHeightIsEqualTo(h.dp)
    }

    @Test
    fun `桌面实例没有新增就不算添加成功`() = runTest {
        // requestPinAppWidget 返回 true 只代表「这个桌面支持应用内添加」（桌面缺权限时会静默失败），
        // 所以结论必须以「桌面上有没有多出一个实例」为准，不能拿返回值当成功
        assertNull(WidgetPin.awaitNewId(before = intArrayOf(1)) { intArrayOf(1) })
    }

    @Test
    fun `桌面多出新实例即算添加成功，数到就返回且带回 id`() = runTest {
        var polls = 0
        val newId = WidgetPin.awaitNewId(before = intArrayOf(1)) {
            polls++
            if (polls >= 2) intArrayOf(1, 2) else intArrayOf(1)
        }
        assertEquals("数到就该立刻返回，不必跑满全部轮次", 2, polls)
        assertEquals("新实例的 id 要带回去：面板里挑的透明度要写到它头上", 2, newId)
    }

    @Test
    fun `小米权限条只在没成功落桌过时出现`() {
        // 「桌面快捷方式」权限没有公开查询法，但成功落桌过就是权限已开的实证：那之后不再打扰
        assertTrue(WidgetPin.showMiuiPermStrip(pinSupported = true, isMiui = true, pinOk = false))
        assertFalse(WidgetPin.showMiuiPermStrip(pinSupported = true, isMiui = true, pinOk = true))
        assertFalse(WidgetPin.showMiuiPermStrip(pinSupported = true, isMiui = false, pinOk = false))
        assertFalse(WidgetPin.showMiuiPermStrip(pinSupported = false, isMiui = true, pinOk = false))
    }

    // ── 预览尺寸：按组件设计尺寸**等比**摆放 ──
    // 画廊卡预览底托外圈 = (393 - 网格两侧 16×2 - 列间 12) / 2 - 卡内 10×2 宽、126dp 高，
    // 内圈再各去 8dp 内边距；断言值按统一系数算：scale = min(内圈宽/340, 内圈高/268)。
    // 同时锁住「尺寸不随屏幕密度变」（同一条在 2x / 3x 两种密度下量，结果必须一致）。

    @Test
    @Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
    fun `预览按设计尺寸等比摆放 3x 屏`() {
        setPinSupported(true)
        render()
        val first = WidgetPin.entries.first()
        val (designW, designH) = WidgetPin.designSizeOf(first)!!
        val scale = previewScale()
        compose.onAllNodesWithTag(PREVIEW_IMAGE_TAG, useUnmergedTree = true)[0]
            .assertWidthIsEqualTo((designW * scale).dp)
            .assertHeightIsEqualTo((designH * scale).dp)
    }

    @Test
    @Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
    fun `预览尺寸不随屏幕密度变 2x 屏`() {
        setPinSupported(true)
        render()
        val first = WidgetPin.entries.first()
        val (designW, _) = WidgetPin.designSizeOf(first)!!
        compose.onAllNodesWithTag(PREVIEW_IMAGE_TAG, useUnmergedTree = true)[0]
            .assertWidthIsEqualTo((designW * previewScale()).dp)
    }

    private companion object {
        /**
         * 画廊卡预览底托的宽：393dp 屏，去掉网格两侧 16×2、列间 12，两列均分。
         * 底托就是卡身本身（卡不再套一层白卡），所以这一整块宽都归预览。
         */
        const val TILE_STAGE_WIDTH_DP = 174.5f

        /** 画廊卡预览底托的外圈高（与 WidgetPinScreen 的 PREVIEW_HEIGHT 一致）。 */
        const val TILE_STAGE_HEIGHT_DP = 126f

        /** 底托内边距（与 WidgetPinScreen 的 STAGE_PADDING 一致）：widget 只在内圈里等比摆放。 */
        const val STAGE_PADDING_DP = 10f

        /** 设计稿最大卡（4×3）的尺寸，全目录统一缩放基准。 */
        const val DESIGN_MAX_WIDTH = 340f
        const val DESIGN_MAX_HEIGHT = 268f

        /** 预览卡实际的统一缩放系数（与 WidgetPinScreen 里的算法一致：按底托内圈算，且不超过 1:1）。 */
        fun previewScale(): Float = minOf(
            (TILE_STAGE_WIDTH_DP - STAGE_PADDING_DP * 2) / DESIGN_MAX_WIDTH,
            (TILE_STAGE_HEIGHT_DP - STAGE_PADDING_DP * 2) / DESIGN_MAX_HEIGHT,
            1f
        )
    }
}
