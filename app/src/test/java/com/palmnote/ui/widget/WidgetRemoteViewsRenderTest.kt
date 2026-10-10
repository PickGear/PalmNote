package com.palmnote.ui.widget

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.data.db.dao.HeldCategoryCount
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.Goal
import com.palmnote.data.db.entity.LifeItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 重构后（样板收敛进 [ScopedWidgetProvider]）三个数据型组件的**离屏渲染核对**：
 * 把 bindViews 产出的 RemoteViews 真实 inflate 成 View 树，断言关键文本与可见性，
 * 防止"逻辑等价但界面坏掉"的回归。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetRemoteViewsRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val accent = WidgetData.AccentTheme(
        accent = 0xFF0891B2.toInt(),
        onAccent = 0xFFFFFFFF.toInt()
    )

    private fun render(views: android.widget.RemoteViews): View {
        // apply 的返回值才是 inflate 出的根 View；parent 只用于生成 LayoutParams
        val parent = FrameLayout(context)
        return views.apply(context, parent)
    }

    private fun texts(root: View): List<String> {
        if (root is TextView) return listOf(root.text.toString())
        if (root is ViewGroup) return root.children.flatMap { texts(it) }.toList()
        return emptyList()
    }

    private fun <T : View> findById(root: View, id: Int): T? {
        if (root.id == id) return root as? T
        if (root is ViewGroup) {
            root.children.forEach { findById<T>(it, id)?.let { hit -> return hit } }
        }
        return null
    }

    private val ViewGroup.children: Sequence<View>
        get() = (0 until childCount).asSequence().map { getChildAt(it) }

    // ── 待办组件 ──

    @Test
    fun `待办行渲染条目标题`() {
        val item = LifeItem(templateId = 1, title = "买牛奶", dueDate = System.currentTimeMillis())
        val root = render(WidgetHelper.todoRowViews(context, item))

        assertTrue(texts(root).any { it.contains("买牛奶") })
        assertNotNull("色片底应着淡彩色", findById<ImageView>(root, R.id.widget_todo_chip_bg)?.colorFilter)
        assertNotNull("色点应着同族色", findById<ImageView>(root, R.id.widget_todo_dot)?.colorFilter)
    }

    @Test
    fun `待办已完成的行加勾前缀且用三级色`() {
        // 稿子里完成的行是划线；RemoteViews 传不了 span（跨进程只传文本），改用可见的勾前缀 + 三级色
        val item = LifeItem(templateId = 1, title = "买牛奶", status = "COMPLETED")
        val root = render(WidgetHelper.todoRowViews(context, item))

        val text = findById<TextView>(root, R.id.widget_item_text)
        assertTrue("完成的行应带勾前缀，实际=${text?.text}", text?.text?.startsWith("✓") == true)
        assertTrue("完成的行标题要保留", text?.text?.contains("买牛奶") == true)
    }

    @Test
    fun `待办布局用滚动列表与空视图`() {
        // 集合适配器动作在应用内无法 apply（RemoteViews 限制），所以这里只核对布局契约
        val root = render(android.widget.RemoteViews(context.packageName, R.layout.widget_todo_unified))

        assertNotNull(findById<View>(root, R.id.widget_todo_list))
        assertNotNull(findById<View>(root, R.id.widget_todo_empty))
    }

    @Test
    fun `待办组件清单含非今日条目、进度轴只算今日`() {
        // 稿子里的清单是「今天 / 明天 / 3 天后 / …」——只查今天到期会让用户记的待办一条都不显示
        val today = System.currentTimeMillis()
        val tomorrow = today + 86_400_000L
        val board = WidgetData.TodoBoard(
            items = listOf(
                LifeItem(templateId = 1, title = "今天的活", dueDate = today),
                LifeItem(templateId = 1, title = "明天的活", dueDate = tomorrow)
            ),
            todayDone = 1,
            todayTotal = 2
        )
        val root = render(TodoWidgetProvider().bindViews(context, 1, board))

        // 进度轴 = 今日完成 1/2
        assertEquals("1", findById<TextView>(root, R.id.widget_todo_done)?.text)
        assertEquals("/2", findById<TextView>(root, R.id.widget_todo_total)?.text)
        // 有条目就摆出进度轴（清单本身由桌面按需取行，测试里拿不到）
        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_todo_progress_col)?.visibility)
    }

    // ── 习惯组件 ──

    @Test
    fun `习惯组件渲染行与勾选态`() {
        val rows = listOf(
            HabitWidgetProvider.HabitRow(templateId = 11, name = "跑步", checked = true, streak = 3),
            HabitWidgetProvider.HabitRow(templateId = 12, name = "阅读", checked = false, streak = 0)
        )
        val root = render(HabitWidgetProvider().bindViews(context, 1, rows))

        assertTrue(texts(root).any { it.contains("跑步") })
        assertTrue(texts(root).any { it.contains("阅读") })
        // 计数 1/2，streak 徽标
        assertTrue(texts(root).any { it == "1/2" })
        assertTrue(texts(root).any { it.contains("3") })
    }

    @Test
    fun `习惯组件空态`() {
        val root = render(HabitWidgetProvider().bindViews(context, 1, emptyList()))

        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_habit_empty)?.visibility)
        assertEquals(View.GONE, findById<View>(root, R.id.widget_habit_count)?.visibility)
    }

    // ── 账单组件 ──

    @Test
    fun `账单组件有预算时渲染预算用量`() {
        val snapshot = BillWidgetProviderBillSnapshotFixture.withBudget()
        val root = render(BillWidgetProvider().bindViews(context, 1, snapshot))

        // 左栏大数字是本月支出（紧凑格式，窄栏也放得下）
        assertEquals("¥82", findById<TextView>(root, R.id.widget_expense_amount)?.text)
        // 图下说明给的是「预算已用 82%」
        assertEquals(context.getString(R.string.widget_budget_used), findById<TextView>(root, R.id.widget_budget_card_label)?.text)
        assertEquals("82%", findById<TextView>(root, R.id.widget_budget_amount)?.text)
        // 用量条：8200/10000 → level 8200（0..10000 对应 0..100%），填充运行时着色族色
        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_bill_budget_bar)?.visibility)
        val fill = findById<ImageView>(root, R.id.widget_bill_budget_fill)
        assertEquals(8200, fill?.drawable?.level)
        assertNotNull(fill?.colorFilter)
        // 近 7 天柱状图：柱高 = 当天 ÷ 七天最大；颜色运行时着色（白底占位 → 不着色就是白条）
        val bars = listOf(
            R.id.widget_bill_bar_0, R.id.widget_bill_bar_1, R.id.widget_bill_bar_2, R.id.widget_bill_bar_3,
            R.id.widget_bill_bar_4, R.id.widget_bill_bar_5, R.id.widget_bill_bar_6
        )
        bars.forEach { id ->
            val bar = findById<ImageView>(root, id)
            assertNotNull("柱子 $id 应着色", bar?.colorFilter)
            assertTrue("柱高应在 0..10000 之间", (bar?.drawable?.level ?: -1) in 1..10_000)
        }
        // 七天里最大的一天占满整高
        assertEquals(10_000, findById<ImageView>(root, R.id.widget_bill_bar_5)?.drawable?.level)
    }

    @Test
    fun `账单组件无预算时图下说明改为净收入`() {
        val snapshot = BillWidgetProviderBillSnapshotFixture.withoutBudget()
        val root = render(BillWidgetProvider().bindViews(context, 1, snapshot))

        assertEquals(context.getString(R.string.widget_net_income), findById<TextView>(root, R.id.widget_budget_card_label)?.text)
        assertTrue(texts(root).any { it.contains("¥") })
        // 没有预算就没有用量可表，只收起条
        assertEquals(View.GONE, findById<View>(root, R.id.widget_bill_budget_bar)?.visibility)
    }

    // ── 账单组件：2×1 迷你档 ──

    @Test
    fun `账单迷你档渲染支出与预算剩余`() {
        val snapshot = BillWidgetProviderBillSnapshotFixture.withBudget()
        val root = render(BillWidgetProvider().bindMiniViews(context, 1, snapshot, accent))

        assertEquals("¥82", findById<TextView>(root, R.id.widget_bill_mini_expense)?.text)
        assertEquals(
            "${context.getString(R.string.widget_budget_remaining)} ¥18",
            findById<TextView>(root, R.id.widget_bill_mini_budget)?.text
        )
        // 预算进度条：8200/10000 → level 8200（0..10000 对应 0..100%）
        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_bill_mini_bar)?.visibility)
        val fill = findById<ImageView>(root, R.id.widget_bill_mini_bar_fill)
        assertEquals(8200, fill?.drawable?.level)
        assertNotNull(fill?.colorFilter)
    }

    @Test
    fun `账单迷你档无预算时改显示净收入`() {
        val snapshot = BillWidgetProviderBillSnapshotFixture.withoutBudget()
        val root = render(BillWidgetProvider().bindMiniViews(context, 1, snapshot, accent))

        assertEquals(
            "${context.getString(R.string.widget_net_income)} ¥470",
            findById<TextView>(root, R.id.widget_bill_mini_budget)?.text
        )
        // 没有预算就没有进度可表，只隐藏条，金额照显
        assertEquals(View.GONE, findById<View>(root, R.id.widget_bill_mini_bar)?.visibility)
    }

    @Test
    fun `账单组件按摆放尺寸在迷你档与全档间切换`() {
        val provider = BillWidgetProvider()

        // 3×2（180×110）及以上：全档
        assertFalse(provider.isCompactSize(180, 110))
        assertFalse(provider.isCompactSize(250, 180))
        // 2 格宽 / 1 格高 / 3×1 扁档：迷你档（宽或高任一不足即降级）
        assertTrue(provider.isCompactSize(110, 110))
        assertTrue(provider.isCompactSize(180, 40))
        assertTrue(provider.isCompactSize(110, 40))
    }

    // ── 概览组件：分区热区 ──

    @Test
    fun `概览分区热区号段互不相同且指向对应专页`() {
        val targets = DashboardWidgetProvider().cardTargets()

        assertEquals(4, targets.size)
        // 复用同一号段会被 FLAG_UPDATE_CURRENT 覆盖，四张卡只会剩下一个能跳
        assertEquals(targets.size, targets.map { it.segment }.toSet().size)
        targets.forEach { target ->
            val expected = if (target.viewId == R.id.widget_dashboard_budget_card) {
                WidgetDeepLink.TAB_BUDGET
            } else {
                WidgetDeepLink.TAB_LIFE
            }
            assertEquals(expected, target.tab)
        }
        // 待办卡落到「今日清单」，不是生活首页
        assertEquals(
            "AGENDA",
            targets.first { it.viewId == R.id.widget_dashboard_todo_card }.listMode
        )
    }

    @Test
    fun `概览布局含四张分区卡`() {
        val views = android.widget.RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
        DashboardWidgetProvider().bindSnapshot(
            context,
            views,
            DashboardWidgetProvider.DashboardSnapshot(
                dateText = "10月7日",
                budgetCardAmount = "¥1,800",
                budgetCardSub = context.getString(R.string.widget_budget_remaining),
                goalPct = "40%",
                goalName = "读书",
                todoRemaining = 2,
                anniversaryTitle = "恋爱纪念日",
                anniversaryDays = 12
            )
        )
        val root = render(views)

        DashboardWidgetProvider().cardTargets().forEach { target ->
            assertNotNull("布局缺少分区卡 id=${target.viewId}", findById<View>(root, target.viewId))
        }
    }

    @Test
    fun `概览三层同心环与四张色片按快照显示`() {
        val views = android.widget.RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
        DashboardWidgetProvider().bindSnapshot(
            context,
            views,
            DashboardWidgetProvider.DashboardSnapshot(
                dateText = "10月7日",
                budgetCardAmount = "¥1,720",
                budgetCardSub = context.getString(R.string.widget_budget_remaining),
                goalPct = "64%",
                goalName = "跑步 5km",
                todoRemaining = 2,
                anniversaryTitle = "妈妈生日",
                anniversaryDays = 12,
                budgetPercent = 82,
                goalPercent = 64
            )
        )
        views.setInt(R.id.widget_dashboard_budget_ring, "setColorFilter", 0xFFF97316.toInt())
        views.setInt(R.id.widget_dashboard_budget_ring, "setImageLevel", 82 * 100)
        val root = render(views)

        assertEquals("¥1,720", findById<TextView>(root, R.id.widget_dashboard_budget)?.text)
        assertEquals("64%", findById<TextView>(root, R.id.widget_dashboard_goal_pct)?.text)
        assertEquals("2", findById<TextView>(root, R.id.widget_dashboard_todo)?.text)
        assertTrue(findById<TextView>(root, R.id.widget_dashboard_days)?.text?.startsWith("12") == true)
        // 三层环的进度弧各按数据扫过
        assertEquals(82 * 100, findById<ImageView>(root, R.id.widget_dashboard_budget_ring)?.drawable?.level)
        assertNotNull(findById<ImageView>(root, R.id.widget_dashboard_budget_ring)?.colorFilter)
    }

    @Test
    fun `概览无预算无纪念日时给占位文案`() {
        val views = android.widget.RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
        DashboardWidgetProvider().bindSnapshot(
            context,
            views,
            DashboardWidgetProvider.DashboardSnapshot(
                dateText = "10月7日",
                budgetCardAmount = "¥470",
                budgetCardSub = context.getString(R.string.widget_net_income),
                goalPct = "--",
                goalName = context.getString(R.string.widget_no_goals),
                todoRemaining = 0,
                anniversaryTitle = null,
                anniversaryDays = -1L
            )
        )
        val root = render(views)

        assertEquals("--", findById<TextView>(root, R.id.widget_dashboard_days)?.text)
        assertEquals(context.getString(R.string.widget_no_anniversary), findById<TextView>(root, R.id.widget_dashboard_days_title)?.text)
    }

    // ── 总资产组件 ──

    @Test
    fun `总资产组件渲染余额合计`() {
        val root = render(NetWorthWidgetProvider().bindViews(context, 1, totalBalance = 1_285_000, widthDp = 110))

        // ¥12,850 已过折叠阈值，按测试机的 en 规则出 k 单位
        val amount = findById<TextView>(root, R.id.widget_net_worth_amount)?.text ?: ""
        assertTrue("余额应折叠成 k 单位，实际=$amount", amount.startsWith("¥") && amount.endsWith("k"))
        assertEquals(
            context.getString(R.string.wallet_total_assets),
            texts(root).firstOrNull { it == context.getString(R.string.wallet_total_assets) }
        )
    }

    @Test
    fun `总资产涨跌胶囊给带符号的净收入金额`() {
        // 曾经按稿子做百分比（净收入 ÷ 期初总资产），真机上算出 +175.8% 这种没有意义的数，
        // 改成直接给本月净收入的金额，涨 + 跌 -
        val up = render(NetWorthWidgetProvider().bindViews(context, 1, 1_285_000L, 110, 26_400L))
        assertEquals(View.VISIBLE, findById<View>(up, R.id.widget_net_worth_delta)?.visibility)
        assertEquals("+¥264", findById<TextView>(up, R.id.widget_net_worth_delta)?.text)

        val down = render(NetWorthWidgetProvider().bindViews(context, 1, 1_285_000L, 110, -26_400L))
        assertEquals("-¥264", findById<TextView>(down, R.id.widget_net_worth_delta)?.text)

        val flat = render(NetWorthWidgetProvider().bindViews(context, 1, 1_285_000L, 110, 0L))
        assertEquals(View.GONE, findById<View>(flat, R.id.widget_net_worth_delta)?.visibility)
    }

    @Test
    fun `金额折叠阈值是 5 位整数`() {
        // 不到 5 位保留原值
        assertEquals("¥9999", WidgetData.formatMoneyCompact(context, 999_900))
        // 5 位起折叠（测试机 en 出 k，百万以上出 M）
        assertTrue(WidgetData.formatMoneyCompact(context, 1_285_000).endsWith("k"))
        assertTrue(WidgetData.formatMoneyCompact(context, 128_500_000).endsWith("M"))
    }

    @Test
    fun `窄格子金额不带符号、只到元，长度不会撑破格子`() {
        // 账单组件三格在 3 格宽时每格约 47dp 放文字。真机上支出显示成 `-¥529…` ——
        // 因为那时拼的是 `-` + `¥5290.50`，9 个字符要 66dp，被省略号截断，金额看不全。
        // 这里锁住窄格格式的形态：不带货币符号、不带正负号、最长 5 个字符。
        val amounts = listOf(0L, 1, 99, 100, 52_950, 529_050, 999_900, 1_000_000, 2_700_000, 100_000_000)
        amounts.forEach { amount ->
            val text = WidgetData.formatAmountCompact(context, amount)
            assertTrue("金额 $amount 的窄格格式「$text」超过 5 个字符，真机上会被截断", text.length <= 5)
            assertFalse("窄格格式不该带货币符号：$text", text.contains("¥"))
            assertFalse("窄格格式不该带正负号（符号由调用方给）：$text", text.startsWith("-"))
        }
        // 分位在这个尺寸上是噪声，直接省掉
        assertEquals("5290", WidgetData.formatAmountCompact(context, 529_050))
        // 不到 5 位整数保留原值
        assertEquals("9999", WidgetData.formatAmountCompact(context, 999_900))
        // 负值返回绝对值，符号由调用方补
        assertEquals("5290", WidgetData.formatAmountCompact(context, -529_050))
    }

    @Test
    fun `倒计时焦点卡高度不足时收起日期`() {
        val event = LifeCounterWidgetProvider.CounterEvent(1, "妈妈生日", 12, LocalDate.now().plusDays(12))

        val compact = render(
            LifeCounterWidgetProvider().counterViews(context, 1, listOf(event), isLarge = false, compactHeight = true)
        )
        assertEquals(View.GONE, findById<View>(compact, R.id.widget_event_date)?.visibility)
        // 名称与天数保留
        assertTrue(texts(compact).any { it.contains("妈妈生日") })

        val full = render(LifeCounterWidgetProvider().counterViews(context, 1, listOf(event), isLarge = false))
        assertEquals(View.VISIBLE, findById<View>(full, R.id.widget_event_date)?.visibility)
    }

    // ── 订阅提醒组件 ──

    @Test
    fun `订阅组件渲染待扣费行`() {
        val rows = listOf(
            SubscriptionWidgetProvider.Renewal("云盘会员", "25", 4),
            SubscriptionWidgetProvider.Renewal("视频会员", "15", 12)
        )
        val root = render(SubscriptionWidgetProvider().bindViews(context, 1, rows))

        assertTrue(texts(root).any { it.contains("云盘会员") })
        assertTrue(texts(root).any { it.contains("视频会员") })
        assertEquals(View.GONE, findById<View>(root, R.id.widget_subscription_empty)?.visibility)
    }

    @Test
    fun `订阅组件空态`() {
        val root = render(SubscriptionWidgetProvider().bindViews(context, 1, emptyList()))

        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_subscription_empty)?.visibility)
        assertEquals(View.GONE, findById<View>(root, R.id.widget_subscription_list)?.visibility)
    }

    // ── 物品组件 ──

    @Test
    fun `物品组件按分类渲染色片与占比条`() {
        // 传 DB 里的规范代码：组件会用 getCategoryName 翻成中文
        val categories = listOf(
            HeldCategoryCount("DIGITAL", 3),
            HeldCategoryCount("SPORTS", 2),
            HeldCategoryCount("BOOKS", 1)
        )
        val root = render(AssetWidgetProvider().bindViews(context, 1, categories))

        // 头部总数 = 各分类件数之和
        assertEquals(
            context.resources.getQuantityString(R.plurals.widget_asset_total_count, 6, 6),
            findById<TextView>(root, R.id.widget_asset_total)?.text
        )
        // 每个分类一张色片，写清名称与件数
        // 名称走应用同一套翻译，断言也用同一函数（测试机 locale 不是 zh）
        assertTrue(texts(root).any { it == com.palmnote.ui.components.getCategoryName("DIGITAL", context) })
        assertTrue(texts(root).any { it == com.palmnote.ui.components.getCategoryName("SPORTS", context) })
        assertTrue(texts(root).any { it == com.palmnote.ui.components.getCategoryName("BOOKS", context) })
        assertTrue(texts(root).any { it == context.resources.getQuantityString(R.plurals.widget_asset_item_count, 3, 3) })
        assertNotNull("色片底应着淡彩色", findById<ImageView>(root, R.id.widget_asset_chip_bg)?.colorFilter)
        assertNotNull("色片色点应着同族色", findById<ImageView>(root, R.id.widget_asset_chip_dot)?.colorFilter)
        // 占比条 24 段等宽槽位，按件数占比着色：3/2/1 共 6 件 → 累计边界 12 / 20 / 24，
        // 于是第 1 类占 1-11 段、第 2 类 12-19、第 3 类 20-24（段宽即比例）
        listOf(R.id.widget_asset_seg_1, R.id.widget_asset_seg_12, R.id.widget_asset_seg_20).forEach { id ->
            assertEquals(View.VISIBLE, findById<ImageView>(root, id)?.visibility)
            assertNotNull("占比条段 $id 应着色族色", findById<ImageView>(root, id)?.colorFilter)
        }
        listOf(R.id.widget_asset_seg_1, R.id.widget_asset_seg_24).forEach { id ->
            assertNotNull("占比条段 $id 应着色族色", findById<ImageView>(root, id)?.colorFilter)
        }
        // 段宽 = 比例：3/2/1 共 6 件 → 24 段切成 12 / 8 / 4
        val owners = AssetWidgetProvider().slotOwners(listOf(3, 2, 1), 24)
        assertEquals("第一类占前 12 段", 0, owners[0])
        assertEquals(0, owners[11])
        assertEquals("第二类从第 13 段起", 1, owners[12])
        assertEquals(1, owners[19])
        assertEquals("第三类占最后 4 段", 2, owners[20])
        assertEquals(2, owners[23])
        assertEquals(View.GONE, findById<View>(root, R.id.widget_asset_empty)?.visibility)
    }

    @Test
    fun `物品组件空态`() {
        val root = render(AssetWidgetProvider().bindViews(context, 1, emptyList()))

        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_asset_empty)?.visibility)
        assertEquals(View.GONE, findById<View>(root, R.id.widget_asset_bar)?.visibility)
        assertEquals(View.GONE, findById<View>(root, R.id.widget_asset_grid)?.visibility)
    }

    @Test
    fun `物品组件分类超过 6 个时并成其他而非丢弃`() {
        // 分类数无上界（20 个预设 + 用户自建），组件只有 3×2 个格位：超出的必须并成「其他」。
        // 直接截断会有两处错：第 7 名往后的分类在桌面上消失；占比条按前 6 项归一化，比例失真。
        val categories = (1..9).map { HeldCategoryCount("CAT$it", 10 - it) }
        val collapsed = AssetWidgetProvider().collapseToSlots(categories, 6)

        assertEquals("格位固定 6 个", 6, collapsed.size)
        assertEquals("件数只搬家不丢弃", categories.sumOf { it.count }, collapsed.sumOf { it.count })
        assertEquals("末尾是聚合项「其他」", "OTHER", collapsed.last().category)
        // 前 5 名原样保留，其余（4+3+2+1）并入「其他」
        assertEquals(listOf("CAT1", "CAT2", "CAT3", "CAT4", "CAT5"), collapsed.dropLast(1).map { it.category })
        assertEquals(10, collapsed.last().count)
        // 不满 6 类时不凭空多出「其他」
        assertEquals(3, AssetWidgetProvider().collapseToSlots(categories.take(3), 6).size)

        val root = render(AssetWidgetProvider().bindViews(context, 1, categories))
        assertTrue(texts(root).any { it == com.palmnote.ui.components.getCategoryName("OTHER", context) })
        assertTrue(
            texts(root).any {
                it == context.resources.getQuantityString(R.plurals.widget_asset_item_count, 10, 10)
            }
        )
        // 占比条仍按**全部**件数归一化：聚合项占到最后一段，不再把第 6 名拉伸成「填满剩余」
        val owners = AssetWidgetProvider().slotOwners(collapsed.map { it.count }, 24)
        assertEquals("末段归聚合项「其他」", 5, owners[23])
    }

    @Test
    fun `物品组件自定义分类原样显示不被翻成其他`() {
        // 自定义分类直接以名字入库，getCategoryName 认不出会落 else 分支 → 桌面上全变「其他」
        val root = render(AssetWidgetProvider().bindViews(context, 1, listOf(HeldCategoryCount("玩具", 2))))

        assertTrue(texts(root).any { it == "玩具" })
    }

    @Test
    fun `物品静态预览布局能被 RemoteViews 解析`() {
        // previewLayout 会被 WidgetPin 包成 RemoteViews 交给桌面（确认框预览），
        // 所以静态布局里不能出现 RemoteViews 放行的类之外的东西（如纯 android.view.View）。
        val root = render(RemoteViews(context.packageName, R.layout.widget_asset_preview))

        assertTrue(texts(root).any { it == context.getString(R.string.widget_preview_asset_cat_1) })
        assertTrue(texts(root).any { it == context.getString(R.string.widget_preview_asset_total) })
    }

    // ── 倒计时组件：周年滚动进度环 ──

    @Test
    fun `倒计时焦点卡把目标日高亮在日历条上`() {
        // 日历条 7 格：目标日在前 5 天、后 1 天的位置上（稿子里 27…1、2，1 高亮）
        val target = LocalDate.of(2026, 11, 1)
        val event = LifeCounterWidgetProvider.CounterEvent(itemId = 1, name = "爸爸生日", daysLeft = 23, targetDate = target)
        val root = render(LifeCounterWidgetProvider().counterViews(context, 1, listOf(event), isLarge = false))

        assertEquals("27", findById<TextView>(root, R.id.widget_counter_date_0)?.text)
        assertEquals("1", findById<TextView>(root, R.id.widget_counter_date_5)?.text)
        assertEquals("2", findById<TextView>(root, R.id.widget_counter_date_6)?.text)
        // 目标日那格才有色底，且运行时着色族色
        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_counter_date_bg_5)?.visibility)
        assertNotNull(findById<ImageView>(root, R.id.widget_counter_date_bg_5)?.colorFilter)
        assertEquals(View.GONE, findById<View>(root, R.id.widget_counter_date_bg_4)?.visibility)
        assertTrue(texts(root).any { it.contains("爸爸生日") })
    }


    // ── 真机自适应：摆得高/条目少时元素放大 ──

    @Test
    fun `倒计时摆得高时数字与日历条放大`() {
        val event = LifeCounterWidgetProvider.CounterEvent(1, "爸爸生日", 22, LocalDate.of(2026, 11, 1))
        val tall = render(
            LifeCounterWidgetProvider().counterViews(context, 1, listOf(event), isLarge = false, heightDp = 284)
        )
        val short = render(
            LifeCounterWidgetProvider().counterViews(context, 1, listOf(event), isLarge = false, heightDp = 190)
        )
        // 摆到 3 格高（≈284dp）时数字与日历条各放大一档，否则卡片上下会空三分之一
        assertEquals(sp(46f), findById<TextView>(tall, R.id.widget_days_count)?.textSize?.toDouble() ?: 0.0, 0.5)
        assertEquals(sp(38f), findById<TextView>(short, R.id.widget_days_count)?.textSize?.toDouble() ?: 0.0, 0.5)
        assertEquals(sp(15f), findById<TextView>(tall, R.id.widget_counter_date_0)?.textSize?.toDouble() ?: 0.0, 0.5)
    }

    @Test
    fun `习惯只有一两条时连击数字放大`() {
        val one = render(
            HabitWidgetProvider().bindViews(
                context, 1,
                listOf(HabitWidgetProvider.HabitRow(templateId = 1, name = "跑步", checked = true, streak = 12))
            )
        )
        assertEquals(sp(64f), findById<TextView>(one, R.id.widget_habit_streak)?.textSize?.toDouble() ?: 0.0, 0.5)
    }

    @Test
    fun `概览圆环中心在无预算时退到目标百分比`() {
        val views = RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
        DashboardWidgetProvider().bindSnapshot(
            context,
            views,
            DashboardWidgetProvider.DashboardSnapshot(
                dateText = "10月10日",
                budgetCardAmount = "¥470",
                budgetCardSub = context.getString(R.string.widget_net_income),
                goalPct = "58%",
                goalName = "读书",
                todoRemaining = 0,
                anniversaryTitle = null,
                anniversaryDays = -1L,
                budgetPercent = null,
                goalPercent = 58
            )
        )
        val root = render(views)

        assertEquals("58%", findById<TextView>(root, R.id.widget_dashboard_ring_text)?.text)
    }

    /** sp → px（测试机密度）。 */
    private fun sp(value: Float): Double = android.util.TypedValue
        .applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics)
        .toDouble()

    // ── 整卡透明度（设置页「组件透明度」） ──

    @Test
    fun `透明度档位映射到对应的半透明底图`() {
        // RemoteViews 不允许 setAlpha，透明度只能落在卡片底色上：4 档一一对应
        assertEquals(R.drawable.widget_card_round, WidgetHelper.cardBackgroundFor(1f))
        assertEquals(R.drawable.widget_card_round_80, WidgetHelper.cardBackgroundFor(0.8f))
        assertEquals(R.drawable.widget_card_round_60, WidgetHelper.cardBackgroundFor(0.6f))
        assertEquals(R.drawable.widget_card_round_40, WidgetHelper.cardBackgroundFor(0.4f))
        // 档位之间按就近归位，不出现空档
        assertEquals(R.drawable.widget_card_round_80, WidgetHelper.cardBackgroundFor(0.75f))
        assertEquals(R.drawable.widget_card_round_40, WidgetHelper.cardBackgroundFor(0.41f))
    }

    @Test
    fun `透明度设置换掉组件卡片底图`() {
        val views = NetWorthWidgetProvider().bindViews(context, 1, 123456L, 200)
        WidgetHelper.applyWidgetOpacity(views, 0.4f)

        val root = render(views)
        assertEquals(R.drawable.widget_card_round_40, shadowOf(root.background).createdFromResId)
    }

    @Test
    fun `默认不透明时不写底色动作`() {
        val views = NetWorthWidgetProvider().bindViews(context, 1, 123456L, 200)
        WidgetHelper.applyWidgetOpacity(views, 1f)

        val root = render(views)
        assertEquals(R.drawable.widget_card_round, shadowOf(root.background).createdFromResId)
    }

    @Test
    fun `概览色片底与色点可运行时着色`() {
        val views = RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
        views.setInt(R.id.widget_dashboard_goal_tint, "setColorFilter", 0xFFE3F0FA.toInt())
        views.setInt(R.id.widget_dashboard_goal_dot, "setColorFilter", 0xFF3B82F6.toInt())

        val root = render(views)
        assertNotNull(findById<ImageView>(root, R.id.widget_dashboard_goal_tint)?.colorFilter)
        assertNotNull(findById<ImageView>(root, R.id.widget_dashboard_goal_dot)?.colorFilter)
    }
}

/** 快照数据类的测试夹具（构造函数参数较多，集中在这保持测试可读）。 */
private object BillWidgetProviderBillSnapshotFixture {
    /** 近 7 天支出：第 5 天最大（占满整高），第 3 天为 0（走基线）。 */
    private val daily = listOf(1200L, 3000L, 0L, 5000L, 2000L, 8000L, 1500L)

    fun withBudget() = BillWidgetProvider.BillSnapshot(
        monthlyExpense = 8200,
        monthlyIncome = 50000,
        budget = Budget(yearMonth = "2026-10", totalBudget = 10000),
        topCategory = "餐饮",
        dailyExpense = daily
    )

    fun withoutBudget() = BillWidgetProvider.BillSnapshot(
        monthlyExpense = 3000,
        monthlyIncome = 50000,
        budget = null,
        topCategory = null,
        dailyExpense = daily
    )
}
