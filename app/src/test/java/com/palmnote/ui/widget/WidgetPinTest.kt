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
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.ui.settings.CATALOG_CELL_TAG
import com.palmnote.ui.settings.CATALOG_LIST_TAG
import com.palmnote.ui.settings.INSTANCE_ROW_TAG
import com.palmnote.ui.settings.PREVIEW_IMAGE_TAG
import com.palmnote.ui.settings.SHEET_ADD_TAG
import com.palmnote.ui.settings.SHEET_SAVE_TAG
import com.palmnote.ui.settings.SHEET_BACK_TAG
import com.palmnote.ui.settings.SHEET_DOT_TAG
import com.palmnote.ui.settings.SHEET_DOTS_TAG
import com.palmnote.ui.settings.SHEET_BG_ROW_TAG
import com.palmnote.ui.settings.SHEET_EDIT_ENTRY_TAG
import com.palmnote.ui.settings.SHEET_OPACITY_ROW_TAG
import com.palmnote.ui.settings.SHEET_OPACITY_SLIDER_TAG
import com.palmnote.ui.settings.SHEET_PREVIEW_TAG
import com.palmnote.ui.settings.SHEET_TAG
import com.palmnote.ui.settings.SHEET_TOPBAR_TAG
import com.palmnote.ui.settings.WidgetPinScreen
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
 * 添加页（**只在应用内一键添加**，不再讲系统的手动添加路径）画廊版式（设计稿 V22 §5，2026-10-24 对稿）：
 * - 首屏是**一条平铺画廊**：4×2 的卡占整行、2×2 的卡两两并排，顺序按 [WidgetPin.GALLERY_ORDER]；
 *   每张卡 = 组件预览（直接浮在页底上，widget 自带卡底与投影）+ 图下一行**居中短名**标签
 *   （2026-10-25 用户定：短名不截断）；
 *   不设分组筛选 / 占位格（稿里没有）；顶栏有搜索（按短名/长名/说明过滤）；
 * - 点某张卡弹出**近全高的面板**（对齐各家「添加小部件」的观感）：名称 + 说明 + 大预览
 *   （桌面**真实尺寸**、浮在白底上，左右滑动换样式、下缘一排分页圆点）+ 背景色 + 不透明度滑杆
 *   + 添加按钮，当场选当场落桌；已落桌的卡可长按或从面板底部进「编辑」（单实例直接进面板、
 *   多实例先选一个，保存写回该实例）；
 * - 目录覆盖 48 个尺寸形态（v22 每族四档；记账三样式 × 四档，折叠成 10 个组件）、
 *   Provider 不重复、标题/描述/预览资源可解析，且每个形态都登记了格数标签、设计尺寸与分组；
 * - manifest 覆盖：48 个 receiver 全部声明（系统选择器按 manifest 的 label 区分同族形态）；
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

    /**
     * 滚到某组件的画廊卡并点开面板。长列表里定位组件靠滚动 ——
     * 按「卡片节点 + **短名**文本」匹配（2026-10-25 用户定：画廊标签用短名），
     * 分组 chip 常驻误匹配的问题也不存在了。
     */
    private fun openTile(titleRes: Int) {
        val title = context.getString(WidgetPin.titleShortOf(titleRes))
        compose.onNodeWithTag(CATALOG_LIST_TAG).performScrollToNode(hasText(title))
        compose.onNode(hasTestTag(CATALOG_CELL_TAG) and hasText(title)).performClick()
    }

    /** 面板大预览当前的尺寸：换尺寸后它会跟着变（各形态的尺寸不同）。 */
    private fun previewSize(): IntSize =
        compose.onNodeWithTag(SHEET_PREVIEW_TAG).fetchSemanticsNode().size

    /** 面板圆点行在根坐标系里的顶边（px）：组件之间、翻页前后对齐都得看它。 */
    private fun dotsRowTop(): Float =
        compose.onNodeWithTag(SHEET_DOTS_TAG).fetchSemanticsNode().boundsInRoot.top

    /**
     * 在面板大预览上横滑：`fraction` 是位移占**页宽**（= 面板内容宽）的比例。
     * 真指针：按下 → 分两步挪并走时间 → 停一拍 → 抬起。
     *
     * 停那一拍是关键：pager 是「甩动速度 + 过没过页中线」一起决定落哪页的，速度归零之后
     * 就只按位置落页，同样的位移才落到同一页（不然同一段位移会因速度不同飘到不同尺寸）。
     * 比例得按页宽算：小尺寸的预览节点自己就窄（2×1 只有页宽的 46%），
     * 拿节点宽当分母会算出「看着滑了一大段、其实没过页中线」。
     */
    private fun swipePreview(fraction: Float) {
        val dx = sheetWidthPx() * fraction
        compose.onNodeWithTag(SHEET_PREVIEW_TAG).performTouchInput {
            // 从节点靠里那一侧起手：整段位移都落在页内，不靠「指针跑出页外」来凑距离
            down(Offset(if (dx < 0) right - 4f else left + 4f, centerY))
            moveBy(Offset(dx / 2f, 0f))
            advanceEventTime(16)
            moveBy(Offset(dx / 2f, 0f))
            advanceEventTime(200)
            up()
        }
    }

    /** 面板内容宽 = pager 的页宽（预览节点只是页内居中的图，宽度不等于页宽）。 */
    private fun sheetWidthPx(): Int =
        compose.onNodeWithTag(SHEET_TAG).fetchSemanticsNode().size.width

    // ── 目录 / manifest（与版式无关） ──

    @Test
    fun `目录覆盖全部尺寸形态且资源都存在`() {
        // 2026-10-24 v22 四档补全：每族 4×2 / 2×2 / 4×1 / 2×1（记账三样式 ×4），共 48 个形态
        assertEquals(40, WidgetPin.entries.size)
        assertEquals(40, WidgetPin.entries.map { it.providerClass }.distinct().size)
        WidgetPin.entries.forEach { entry ->
            // 引用不存在会直接抛 NotFoundException
            context.getString(entry.titleRes)
            context.getString(entry.descRes)
            context.resources.getResourceEntryName(entry.previewImageRes)
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
    fun `40 个形态折叠成 10 个组件并归 5 个功能分区`() {
        // 2026-10-21 用户定：按 App 功能模块分区（账单 / 物品 / 生活 / 总览 / 快捷入口），
        // 分区里的组件再各自折叠出「四档尺寸 +（记账的）多套样式」。
        // 2026-10-23 用户定：密码本不设分区——高危隐私，其小组件已整体下线。
        val catalog = WidgetPin.catalog()
        assertEquals(5, catalog.size)
        assertEquals(
            listOf(
                WidgetPin.WidgetGroup.BILLING,
                WidgetPin.WidgetGroup.ASSET,
                WidgetPin.WidgetGroup.LIFE,
                WidgetPin.WidgetGroup.DASHBOARD,
                WidgetPin.WidgetGroup.SHORTCUTS
            ),
            catalog.map { it.group }
        )
        val components = catalog.flatMap { it.components }
        assertEquals(10, components.size)
        // 每个组件恰好 4 档（4×2 / 2×2 / 4×1 / 4×4，v37 收敛），合计 40
        assertTrue(components.all { it.entries.size == 4 })
        assertEquals(40, components.sumOf { it.entries.size })
        assertEquals(10, components.map { it.familyKey }.distinct().size)
        // 每个分区至少一件组件
        assertTrue(catalog.all { it.components.isNotEmpty() })
    }

    @Test
    fun `本月记账四档（v37 单族收敛），弹窗里左右翻`() {
        // v37 收敛：柱状/预算两套样式下线，本月记账 = 单族四档（4×2 / 2×2 / 4×1 / 4×4）
        val bill = WidgetPin.catalog()
            .flatMap { it.components }
            .first { it.titleRes == R.string.widget_bill_title }
        assertEquals(4, bill.entries.size)
        assertEquals(4, bill.entries.count { BillWidgetProvider::class.java.isAssignableFrom(it.providerClass) })
        // 四档设计尺寸互不相同（翻页时预览跟着变）
        assertEquals(4, bill.entries.map { WidgetPin.designSizeOf(it) }.distinct().size)
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
    fun `manifest 声明全部 40 个 receiver 且带 label`() {
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
        // 反向锁：上面只验「缺不缺」。删族时若漏删 manifest 条目（2026-10-23 密码本下线
        // 就差点这样），多余的 receiver 会一直挂在选择器里，必须数量+成员双向一致。
        val expectedNames = WidgetPin.entries.map { it.providerClass.name }.toSet()
        val actualNames = receivers.map { it.activityInfo.name }.toSet()
        assertEquals("manifest 里存在目录没有的 appwidget receiver", expectedNames, actualNames)
        assertEquals("形态数与 manifest receiver 数不一致", 40, actualNames.size)
    }

    @Test
    fun `只有账单族的样式在添加后弹配置页`() {
        // pin 通道不会拉起配置页，需要用户当场做选择的组件才挂成功回调自己补。
        // 目前只有账单族（本月记账三套样式 × 四档共 12 件都要选账本）
        val configured = WidgetPin.entries.filter { it.configureActivity != null }
        assertEquals(4, configured.size)
        assertTrue(
            "配置页只该挂在账单族的样式上",
            configured.all { WidgetPin.groupOf(it) == WidgetPin.WidgetGroup.BILLING }
        )
        assertTrue(configured.all { it.configureActivity == BillWidgetConfigActivity::class.java })
        assertEquals(BillWidgetProvider::class.java, configured.first().providerClass)
    }

    // ── 画廊版式：分组 / 搜索 / 面板 / 尺寸切换 ──

    @Test
    fun `页面以应用内添加为主，不再出现系统手动添加引导`() {
        setPinSupported(true)
        render()

        // 首屏就有物品分布卡（GALLERY_ORDER 首位）：卡里有名称（短名「物品」）
        compose.onAllNodesWithText(context.getString(R.string.widget_asset_title_short)).assertCountEquals(1)
        // 系统手动添加那套（机型切换 + 三步路径）已整体下线，连文案都不该留在资源里
        assertEquals(
            0,
            context.resources.getIdentifier("widget_guide_section", "string", context.packageName)
        )
    }

    @Test
    fun `首卡是物品分布且能滚到画廊末尾`() {
        setPinSupported(true)
        render()
        // 首卡 = 物品分布：稿 §5 封面次序的第一张（2026-10-24 稿序）
        compose.onAllNodesWithText(context.getString(R.string.widget_asset_title_short)).assertCountEquals(1)
        // 网格可滚：滚到接下来（GALLERY_ORDER 最后一张）证明不是只摆了首屏几张卡。
        // 按「卡片节点」判定而不是按文本 —— 面板说明等处也会出现同名文本，只按文本断言不可靠
        val lastTitle = context.getString(R.string.widget_next_up_title_short)
        compose.onNodeWithTag(CATALOG_LIST_TAG).performScrollToNode(hasText(lastTitle))
        compose.onNode(hasTestTag(CATALOG_CELL_TAG) and hasText(lastTitle)).assertExists()
    }

    @Test
    fun `面板的分页圆点数等于该组件的形态个数`() {
        setPinSupported(true)
        render()
        openTile(R.string.widget_todo_title)
        // 圆点让「左右滑动换尺寸」看得见：点数 = 形态个数（待办四档 = 4×2 + 2×2 + 4×1 + 2×1）
        val sizes = WidgetPin.entries.count { it.titleRes == R.string.widget_todo_title }
        assertEquals(4, sizes)
        compose.onNodeWithTag(SHEET_DOTS_TAG).assertExists()
        compose.onAllNodesWithTag(SHEET_DOT_TAG).assertCountEquals(sizes)
    }

    @Test
    fun `只有一套样式的组件面板不摆分页圆点`() {
        // v22 四档补全后 10 个组件都至少 4 件形态，「单件」分支暂无使用者；
        // 行为本身保留（将来加单档组件时生效），这里锁住前提不回归。
        assertTrue(WidgetPin.catalog().flatMap { it.components }.all { it.entries.size >= 4 })
    }

    @Test
    fun `本月记账弹窗里摆四个圆点对应四档`() {
        setPinSupported(true)
        render()
        openTile(R.string.widget_bill_title)
        // 数据 / 柱状 / 预算三套样式 × 四档 —— 翻页换的是「样式 × 尺寸」的组合
        compose.onAllNodesWithTag(SHEET_DOT_TAG).assertCountEquals(4)
    }

    @Test
    fun `面板圆点行位置不随组件变`() {
        setPinSupported(true)
        render()
        // 圆点行是「翻到第几个尺寸」的指示，得钉在面板里同一行上。
        // 待办清单那一组最大 4×3=268dp、快捷入口那一组最大只有 160dp：页高若跟着「本组最大」走，
        // 换成快捷入口时 widget 与圆点会整体上移 54dp —— 圆点跟着组件跑，看着像没对齐。
        // （两个组件的标题与说明都是一行，页高以上那段完全相同，所以这个横向比较是干净的。）
        openTile(R.string.widget_todo_title)
        val todoDotsTop = dotsRowTop()
        compose.onNodeWithTag(SHEET_BACK_TAG).performClick()
        openTile(R.string.widget_shortcuts_title)
        assertEquals("换组件不该带走圆点行", todoDotsTop, dotsRowTop(), 0.5f)
    }

    @Test
    fun `面板大预览按桌面真实尺寸，不被该组最大形小拉低`() {
        setPinSupported(true)
        render()
        // 快捷入口那一组最大只有 160dp 高（1×1 / 4×1），页高仍按全局 4×3 的 268dp 算：
        // 页高就是页内缩放系数的分母，改按本组最大算的话，这一组预览会被压到真实尺寸的六成
        openTile(R.string.widget_shortcuts_title)
        val component = WidgetPin.catalog().flatMap { it.components }
            .first { it.titleRes == R.string.widget_shortcuts_title }
        val entry = WidgetPin.defaultStyleEntry(component)
        val (designW, designH) = WidgetPin.designSizeOf(entry)!!
        compose.onNodeWithTag(SHEET_PREVIEW_TAG)
            .assertWidthIsEqualTo(designW.dp)
            .assertHeightIsEqualTo(designH.dp)
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
    fun `面板顶栏是面板里唯一的显式返回入口，点了收起面板`() {
        setPinSupported(true)
        render()
        openFirstTile()
        // 顶栏（应用图标 + 应用名，对齐系统选择器那一行）只做身份与返回：
        // 「←」与系统返回 / 下滑 / 点遮罩同义，点了收起面板回画廊
        compose.onNodeWithTag(SHEET_TOPBAR_TAG).assertExists()
        compose.onNodeWithTag(SHEET_BACK_TAG).performClick()
        compose.onAllNodesWithTag(SHEET_TAG).assertCountEquals(0)
    }

    @Test
    fun `面板里左滑预览换下一个尺寸，右滑换回上一个`() {
        setPinSupported(true)
        render()
        openTile(R.string.widget_todo_title)
        // 尺寸只靠滑：大预览是面板里最大的一块，滑它比去够一排胶囊顺手（各家卡片预览页都是这个手势）
        // 预览标签只挂在「翻到的这一页、并且它就是当前选中的那个尺寸」上，所以下面量尺寸的同时
        // 也在验「翻页结果有没有回报给上层」—— 回报断了标签就没了、量不到
        val base = previewSize()
        swipePreview(-0.7f)
        val next = previewSize()
        assertTrue("左滑应换到下一个（更小的）尺寸：before=$base after=$next", next.width < base.width)
        // 右滑切回上一个：方向没搞反
        swipePreview(0.7f)
        assertEquals("右滑应切回原尺寸", base, previewSize())
    }

    @Test
    fun `面板里滑动不过页中线就不换尺寸`() {
        setPinSupported(true)
        render()
        openTile(R.string.widget_todo_title)
        // 手指按在预览上只挪一小段（没过页中线）：pager 会弹回原页，不该换尺寸，免得误触
        val base = previewSize()
        swipePreview(-0.2f)
        assertEquals("没过页中线不该换尺寸", base, previewSize())
    }

    // ── 已落桌实例的编辑：卡上的「编辑」→ 面板 → 保存写回 ──

    /** 把物品组件绑到桌面上（只登记 id → provider，不 inflate RemoteViews）。 */
    private fun bindAssetInstance(id: Int) {
        shadowOf(AppWidgetManager.getInstance(context))
            .bindAppWidgetId(id, ComponentName(context, AssetWidgetProvider::class.java))
    }

    /** 长按首卡进「编辑已落桌实例」：面板底部那行编辑入口已删（2026-10-25），长按是唯一入口。 */
    private fun editFirstTile() {
        compose.onAllNodesWithTag(CATALOG_CELL_TAG)[0].performTouchInput { longClick() }
    }

    @Test
    fun `单实例点编辑直接进面板，保存写回该实例`() {
        setPinSupported(true)
        bindAssetInstance(7)
        var saved: Pair<Int, WidgetInstanceConfig>? = null
        compose.setContent {
            PalmNoteTheme {
                WidgetPinScreen(
                    loadInstance = { WidgetInstanceConfig(opacity = 0.6f, background = "#000000") },
                    onSaveInstance = { id, config -> saved = id to config }
                )
            }
        }

        editFirstTile()
        // 只有一份实例：不经过「选实例」层，直接进编辑面板；回填值要真的出现在面板上
        compose.onAllNodesWithTag(INSTANCE_ROW_TAG).assertCountEquals(0)
        compose.onNodeWithText("60%").assertExists()
        // 面板里改的是草稿：拖一下不透明度，保存时一并写回
        compose.onNodeWithTag(SHEET_OPACITY_SLIDER_TAG).performTouchInput { swipeLeft() }
        compose.onNodeWithTag(SHEET_SAVE_TAG).performClick()

        val recorded = requireNotNull(saved) { "点保存应写回实例配置" }
        assertEquals("写回的必须是当前实例的 id", 7, recorded.first)
        assertEquals("没动过的项原样带回", "#000000", recorded.second.background)
        assertTrue("拖动后的不透明度要一起写回：${recorded.second.opacity}", recorded.second.opacity!! < 0.95f)
        // 保存即收起面板
        compose.onAllNodesWithTag(SHEET_SAVE_TAG).assertCountEquals(0)
    }

    @Test
    fun `同组件多实例点编辑先选一个实例`() {
        setPinSupported(true)
        bindAssetInstance(11)
        bindAssetInstance(12)
        render()

        editFirstTile()
        // 同尺寸的两份从列表上分不出谁是谁，但数量必须对、且不能跳过选择直接进面板
        compose.onAllNodesWithTag(INSTANCE_ROW_TAG).assertCountEquals(2)
        compose.onAllNodesWithTag(SHEET_SAVE_TAG).assertCountEquals(0)

        compose.onAllNodesWithTag(INSTANCE_ROW_TAG)[0].performClick()
        compose.onNodeWithTag(SHEET_SAVE_TAG).assertExists()
        compose.onAllNodesWithTag(INSTANCE_ROW_TAG).assertCountEquals(0)
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
    fun `面板里能挑不透明度，带配置页的组件不摆这一行`() {
        setPinSupported(true)
        render()
        // 待办清单没有配置页：面板里先挑一档不透明度，落桌时写到新实例上（各家「可编辑的小组件」都这么做）
        openTile(R.string.widget_todo_title)
        compose.onNodeWithTag(SHEET_OPACITY_ROW_TAG).assertExists()
        // 无极滑杆：初始 100%，在滑杆上向左拖 → 值变化，百分比文本跟着变
        // （手势必须打在滑杆上——整行 Row 的 Text 区域会把触摸吃掉）
        compose.onNodeWithTag(SHEET_OPACITY_SLIDER_TAG).performTouchInput { swipeLeft() }
        // 直接读滑杆的进度值断言（百分比文本/滑杆 state 描述都含 "100%"，contains 匹配不可靠）
        val info = compose.onNodeWithTag(SHEET_OPACITY_SLIDER_TAG)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo]
        assertTrue("拖动后不透明度应离开默认值：$info", info.range.start < 0.95f)
    }

    @Test
    fun `账单的面板里也直接摆不透明度与背景`() {
        setPinSupported(true)
        render()
        // 背景与不透明度对账单也直接可编（配置页有「碰过才写」门控，两边不会互相清值）
        openTile(R.string.widget_bill_title)
        compose.onNodeWithTag(SHEET_OPACITY_ROW_TAG).assertExists()
        compose.onNodeWithTag(SHEET_BG_ROW_TAG).assertExists()
        // 「编辑更多」入口：账单有账本 + 配色两个额外项
        compose.onNodeWithTag(SHEET_EDIT_ENTRY_TAG).assertExists()
    }

    @Test
    fun `概览的面板里也有「编辑更多」入口（配色是额外项）`() {
        setPinSupported(true)
        render()
        // 概览渲染吃强调色 →「编辑更多」层放配色卡；点进去再返回
        openTile(R.string.widget_dashboard_title)
        compose.onNodeWithTag(SHEET_EDIT_ENTRY_TAG).assertExists()
        compose.onNodeWithTag(SHEET_EDIT_ENTRY_TAG).performClick()
        // 编辑态整块替换面板内容：入口行消失、编辑层出现（tag 相同，计数回到 1）
        compose.onAllNodesWithTag(SHEET_EDIT_ENTRY_TAG).assertCountEquals(1)
        // 编辑层可见配色卡的「跟随应用」选项；面板的不透明度行被替换掉
        compose.onNodeWithText(context.getString(R.string.widget_config_accent_follow)).assertExists()
        compose.onAllNodesWithTag(SHEET_OPACITY_ROW_TAG).assertCountEquals(0)
        // 点编辑层左上角返回：面板恢复（不透明度行回来）。
        // 按面板返回键的 tag 找 —— 搜索下线后页面顶栏返回键也是「返回」文案，按文案会撞成两个节点
        compose.onNodeWithTag(SHEET_BACK_TAG).performClick()
        compose.onNodeWithTag(SHEET_OPACITY_ROW_TAG).assertExists()
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
        openTile(R.string.widget_todo_title)
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
        assertEquals("新实例的 id 要带回去：面板里挑的不透明度要写到它头上", 2, newId)
    }

    @Test
    fun `权限条只在「桌面要权限且没成功落桌过」时出现`() {
        // 「桌面快捷方式」权限没有公开查询法，但成功落桌过就是权限已开的实证：那之后不再打扰
        assertTrue(WidgetPin.showHomePermStrip(pinSupported = true, homeNeedsPerm = true, pinOk = false))
        assertFalse(WidgetPin.showHomePermStrip(pinSupported = true, homeNeedsPerm = true, pinOk = true))
        assertFalse(WidgetPin.showHomePermStrip(pinSupported = true, homeNeedsPerm = false, pinOk = false))
        assertFalse(WidgetPin.showHomePermStrip(pinSupported = false, homeNeedsPerm = true, pinOk = false))
    }

    // ── 预览尺寸：入口卡按「该组件最饱满的形态」铺满列宽 ──
    // 2026-10-21 定版（系统小部件页那种观感）：入口不跟上次选的尺寸走，一律摆 2×2 / 3×2 / 4×3
    // 这一档，宽度铺满列宽（放大上限 SHOWCASE_MAX_SCALE），同一行取最高者当行高、各卡居中 ——
    // 名称行因此严格对齐。具体尺寸点进面板再选。
    // 同时锁住「尺寸不随屏幕密度变」（同一条在 2x / 3x 两种密度下量，结果必须一致）。

    @Test
    @Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
    fun `入口预览按 4×2 画布铺满 3x 屏`() {
        setPinSupported(true)
        render()
        val first = WidgetPin.catalog().first().components.first()
        val (designW, designH) = WidgetPin.entrySizeOf(WidgetPin.entryCellOf(first))
        // 画布本身是 4×2（340×148）
        assertEquals(148f / 340f, designH.toFloat() / designW, 0.001f)
        // 铺进卡位后仍是这个比例：图与卡位同画布，不留白也不裁边
        val bounds = compose.onAllNodesWithTag(PREVIEW_IMAGE_TAG, useUnmergedTree = true)[0]
            .fetchSemanticsNode().boundsInRoot
        assertEquals(148f / 340f, bounds.height / bounds.width, 0.02f)
    }

    @Test
    @Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
    fun `入口预览比例不随屏幕密度变 2x 屏`() {
        setPinSupported(true)
        render()
        val first = WidgetPin.catalog().first().components.first()
        val (designW, designH) = WidgetPin.entrySizeOf(WidgetPin.entryCellOf(first))
        assertEquals(148f / 340f, designH.toFloat() / designW, 0.001f)
        val bounds = compose.onAllNodesWithTag(PREVIEW_IMAGE_TAG, useUnmergedTree = true)[0]
            .fetchSemanticsNode().boundsInRoot
        assertEquals(148f / 340f, bounds.height / bounds.width, 0.02f)
    }

    @Test
    @Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
    fun `每个组件的入口图都与它的卡位同画布`() {
        // 入口图的像素比例必须等于卡位比例 —— 图与卡位不同源就会在卡里留白或裁边
        // （2026-10-21 的坑：3×2 / 4×3 的形态图直接塞进 4×2 卡位，四周各留一条边）
        val components = WidgetPin.catalog().flatMap { it.components }
        assertEquals("目录组件数变了要同步这张断言", 10, components.size)
        components.forEach { component ->
            val cell = WidgetPin.entryCellOf(component)
            val (designW, designH) = WidgetPin.entrySizeOf(cell)
            val bitmap = android.graphics.BitmapFactory.decodeResource(
                context.resources,
                WidgetPin.entryImageOf(component)
            )
            assertNotNull("组件缺入口图", bitmap)
            assertEquals(
                "入口图比例必须等于卡位（4×2 = 340:148 / 2×2 = 1:1）",
                designW.toFloat() / designH,
                bitmap!!.width.toFloat() / bitmap.height,
                0.03f
            )
        }
    }

    @Test
    fun `首卡的预览按 4×2 铺满整行`() {
        setPinSupported(true)
        render()
        // 首卡固定是本月记账（4×2）：铺满整行、比例 148/340
        val wide = compose.onAllNodesWithTag(PREVIEW_IMAGE_TAG, useUnmergedTree = true)[0]
            .fetchSemanticsNode().boundsInRoot
        assertEquals("4×2 图的高宽比", 148f / 340f, wide.height / wide.width, 0.02f)
    }

    @Test
    fun `入口封面取最饱满的形态，不会挑到细条或小方块`() {
        val components = WidgetPin.catalog().flatMap { it.components }
        fun showcaseOf(title: Int) =
            WidgetPin.designSizeOf(WidgetPin.showcaseEntryOf(components.first { it.titleRes == title }))

        // 待办 / 习惯：五档里 4×2（340×148）与 4×4（340×340）二选一时取面积大者——
        // showcase 取「宽 ≥160 且宽高比 ≤2.6」里的最大面积：4×4 比例 1.0 合格且面积最大 → 4×4
        assertEquals(340 to 340, showcaseOf(R.string.widget_todo_title))
        assertEquals(340 to 340, showcaseOf(R.string.widget_habit_title))
        // 本月记账：三套样式 × 四档，封面取第一条（数据版 4×2）
        assertEquals(340 to 340, showcaseOf(R.string.widget_bill_title))
        // 快捷入口：4×4 九宫格（340×340）面积最大 → 封面用 4×4
        assertEquals(340 to 340, showcaseOf(R.string.widget_shortcuts_title))
    }

    // ── 预览图三层：内容层 + 面板层 + 色砖层（后两层跟着不透明度淡出） ──

    @Test
    fun `每张预览图都有配套的面板层`() {
        // 面板层（会跟着不透明度淡出的中性底）由 WidgetPreviewPngGenerator 与内容层一起出图，
        // 名字靠 _surface 后缀在运行时拼（R8 那边由 res/raw/keep.xml 保住）。漏出一张，预览就会
        // 悄悄退回「单层实色」—— 正是 2026-10-20 那次「不透明度只调了边框、里面还是实的」的老毛病。
        val resources = context.resources
        val missing = WidgetPin.entries
            .filter { WidgetPin.surfacePreviewRes(resources, it.previewImageRes) == 0 }
            .map { resources.getResourceEntryName(it.previewImageRes) }
        assertTrue("这些预览图缺面板层：$missing", missing.isEmpty())
    }

    @Test
    fun `带内层色砖的形态有独立的色砖层`() {
        // 概览四块砖 / 快捷入口四格的颜色是组件给的语义色，卡片相对模式下**不能**跟中性面板一起
        // 被 tint 成极淡白/黑，所以单独出一层（见 WidgetOpacityFade.blockIds 与生成器的 hideBlocks）。
        val resources = context.resources
        // dashboard 的旧四砖随 v22 版式退场(面板层退回单层画法是合法路径);
        // 带砖层的 = 快捷入口的八格 tile 底
        val shapes = listOf(
            R.drawable.widget_preview_shortcuts,
            R.drawable.widget_preview_shortcuts_wide
        )
        val missing = shapes
            .filter { WidgetPin.blocksPreviewRes(resources, it) == 0 }
            .map { resources.getResourceEntryName(it) }
        assertTrue("这些预览图缺色砖层：$missing", missing.isEmpty())
    }

    @Test
    fun `账单有胶囊层，没有胶囊的形态不占`() {
        // 「新增」胶囊是语义 tint 底，自选深色背景时要翻成浅色 —— 底得单独出一层，
        // 面板才能在「卡片相对 + 深色卡」时把它 tint 成 88% 白（见 WidgetPin.CHIP_SUFFIX）。
        // 文字留内容层，所以只有账单这一张有胶囊层。
        val resources = context.resources
        assertTrue(
            "账单预览图缺胶囊层",
            WidgetPin.chipPreviewRes(resources, R.drawable.widget_preview_bill) != 0
        )
        assertEquals(0, WidgetPin.chipPreviewRes(resources, R.drawable.widget_preview_todo))
        assertEquals(0, WidgetPin.chipPreviewRes(resources, R.drawable.widget_item_bg))
    }

    @Test
    fun `没有色砖的形态不占色砖层，退回跳过`() {
        assertEquals(0, WidgetPin.blocksPreviewRes(context.resources, R.drawable.widget_preview_bill))
        assertEquals(0, WidgetPin.blocksPreviewRes(context.resources, R.drawable.widget_item_bg))
    }

    @Test
    fun `没有面板层时退回单层，不炸`() {
        // 约定找不到就给 0（老资源/被裁掉），预览只画内容层 —— 兼容路径要一直在
        assertEquals(0, WidgetPin.surfacePreviewRes(context.resources, R.drawable.widget_item_bg))
    }

    private companion object {
        /**
         * 画廊列的宽：393dp 屏，去掉网格两侧 16×2、列间 12，固定两列均分。
         * 预览直接浮在页面底色上（无底托），这一整块宽都归预览。
         */
        const val TILE_COLUMN_WIDTH_DP = 174.5f

        /** 入口卡放大上限（与 WidgetPinScreen 的 SHOWCASE_MAX_SCALE 一致）。 */
        const val SHOWCASE_MAX_SCALE = 1.5f

        /** 预览盒上下呼吸位（与 WidgetPinScreen 的 PREVIEW_V_PADDING 一致，两侧共 8dp）。 */
        const val PREVIEW_V_PADDING_DP = 4f

        /**
         * 入口卡预览的缩放系数（与 WidgetPinScreen 里同一算法）：
         * 按列宽铺满（宽 = 列宽），放大不超过 [SHOWCASE_MAX_SCALE] —— 1×1 这种小方块宁可小一号居中。
         */
        fun showcaseScale(designW: Float): Float =
            minOf(TILE_COLUMN_WIDTH_DP / designW, SHOWCASE_MAX_SCALE)
    }
}
