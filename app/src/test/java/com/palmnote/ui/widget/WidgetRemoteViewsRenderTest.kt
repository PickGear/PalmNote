package com.palmnote.ui.widget

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.ui.widget.WidgetPrivacy
import com.palmnote.data.db.dao.HeldCategoryCount
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.LifeItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.Locale
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v22 各组件**离屏渲染核对**：把 bindViews 产出的 RemoteViews 真实 inflate 成 View 树，
 * 断言关键文本与可见性，防止「逻辑等价但界面坏掉」的回归。
 *
 * 画布按设计稿网格给（3× 密度）：4×2 = 1020×444px、2×2 = 480×480px、4×1 = 1020×222px、2×1 = 480×222px。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetRemoteViewsRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val accent = WidgetData.AccentTheme(
        accent = 0xFF0891B2.toInt(),
        onAccent = 0xFFFFFFFF.toInt()
    )

    private fun render(views: RemoteViews): View {
        // apply 的返回值才是 inflate 出的根 View；parent 只用于生成 LayoutParams。
        val parent = FrameLayout(context)
        val root = views.apply(context, parent)
        return root.findViewById(R.id.widget_layout) ?: root
    }

    /** 按设计稿网格渲染并测量（带 weight 的组件要铺满画布才像真机）。 */
    private fun renderFixed(views: RemoteViews, widthPx: Int, heightPx: Int): View {
        val root = render(views)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, widthPx, heightPx)
        return root
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

    // ── 待办（v22） ──

    private fun todoBoard() = WidgetData.TodoBoard(
        items = listOf(LifeItem(id = 2, templateId = 1, title = "取快递", status = "ACTIVE", dueDate = todayAt(18, 0))),
        todayItems = listOf(
            LifeItem(id = 1, templateId = 1, title = "交房租", status = "COMPLETED", dueDate = todayAt(10, 0)),
            LifeItem(id = 2, templateId = 1, title = "取快递", status = "ACTIVE", dueDate = todayAt(18, 0)),
            LifeItem(id = 3, templateId = 1, title = "写周报", status = "ACTIVE", dueDate = todayAt(21, 0))
        ),
        todayDone = 2,
        todayTotal = 5
    )

    @Before
    fun forceChineseLocale() {
        // 断言全部按默认（中文）strings 写：单测 JVM 默认 locale 跟宿主走，必须钉死
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        val config = context.resources.configuration
        config.setLocale(Locale.SIMPLIFIED_CHINESE)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    @Test
    fun `待办 4x2 三行胶囊带头部今日计数`() {
        val root = renderFixed(TodoWidgetProvider().bindViews(context, 1, todoBoard(), 340, 148), 1020, 444)
        assertEquals("今天 3/5", findById<TextView>(root, R.id.widget_todo_meta)?.text)
        val labels = texts(root)
        assertTrue(labels.contains("交房租"))
        assertTrue(labels.contains("取快递"))
        assertTrue(labels.contains("写周报"))
        assertTrue("已完成行应带勾记号", labels.contains("✓"))
    }

    @Test
    fun `待办 2x2 hero 是今日未完成项数`() {
        val root = renderFixed(TodoWidgetProvider().bindViews(context, 1, todoBoard(), 160, 160), 480, 480)
        assertEquals("3 项", findById<TextView>(root, R.id.widget_todo_hero)?.text)
    }

    @Test
    fun `待办 4x1 三列读数含已完成比值`() {
        val root = render(TodoWidgetProvider().bindViews(context, 1, todoBoard(), 340, 74))
        assertEquals("2/5", findById<TextView>(root, R.id.widget_todo_bar_done)?.text)
        assertEquals("18:00", findById<TextView>(root, R.id.widget_todo_bar_next)?.text)
    }

    @Test
    fun `待办空态今天没有待办`() {
        val board = WidgetData.TodoBoard(items = emptyList(), todayItems = emptyList(), todayDone = 0, todayTotal = 0)
        val root = render(TodoWidgetProvider().bindViews(context, 1, board, 340, 148))
        assertTrue(texts(root).contains("今天没有待办"))
    }

    // ── 打卡（v22） ──

    private fun habitRows() = listOf(
        HabitWidgetProvider.HabitRow(
            templateId = 11, name = "早起", checked = true, streak = 12,
            recent = listOf(true, true, true, true, true, true, true), monthCount = 18, longest = 21, weekHits = 1
        ),
        HabitWidgetProvider.HabitRow(
            templateId = 12, name = "阅读", checked = true, streak = 5,
            recent = listOf(true, true, true, true, true, false, false), monthCount = 8, longest = 9, weekHits = 1
        ),
        HabitWidgetProvider.HabitRow(
            templateId = 13, name = "运动", checked = false, streak = 3,
            recent = listOf(true, true, true, false, false, false, false), monthCount = 4, longest = 7, weekHits = 0
        )
    )

    @Test
    fun `打卡 4x2 每习惯一条胶囊带行内七天点`() {
        val root = renderFixed(HabitWidgetProvider().bindViews(context, 1, habitRows(), 340, 148), 1020, 444)
        assertEquals("2/3", findById<TextView>(root, R.id.widget_habit_hero)?.text)
        val labels = texts(root)
        assertTrue(labels.contains("早起"))
        assertTrue(labels.contains("阅读"))
        assertTrue(labels.contains("运动"))
        // 行内七天点：三行各带一个 7 个点的点排
        val host = findById<ViewGroup>(root, R.id.widget_habit_rows_host)
        assertEquals("三行习惯", 3, host?.childCount)
        fun countImageViews(v: View?): Int = when (v) {
            is ViewGroup -> (0 until v.childCount).sumOf { countImageViews(v.getChildAt(it)) }
            is ImageView -> 1
            else -> 0
        }
        // 行内 ImageView = 胶囊底 1 + 七天点 7 = 8
        val dotRows = (0 until (host?.childCount ?: 0)).count { row ->
            val rowView = host?.getChildAt(row) as? ViewGroup
            rowView != null && (0 until rowView.childCount).sumOf { countImageViews(rowView.getChildAt(it)) } == 8
        }
        assertEquals("三行各带 7 个点的点排", 3, dotRows)
    }

    @Test
    fun `打卡 2x2 五个大点加完成项名与连续天数`() {
        val root = renderFixed(HabitWidgetProvider().bindViews(context, 1, habitRows(), 160, 160), 480, 480)
        assertEquals("2/3", findById<TextView>(root, R.id.widget_habit_hero)?.text)
        val labels = texts(root)
        assertTrue("完成项名应含早起与阅读", labels.any { it.contains("早起") && it.contains("阅读") })
        assertTrue("连续天数应着最长连击 12", labels.contains("连续 12 天"))
        assertTrue("5 个大点应可见", findById<View>(root, R.id.widget_habit_dots_host)?.visibility == View.VISIBLE)
    }

    @Test
    fun `打卡 4x1 两列带刻度`() {
        val root = render(HabitWidgetProvider().bindViews(context, 1, habitRows(), 340, 74))
        assertEquals("2/3", findById<TextView>(root, R.id.widget_habit_bar_today)?.text)
        assertEquals("12", findById<TextView>(root, R.id.widget_habit_bar_streak)?.text)
        assertEquals("1/7", findById<TextView>(root, R.id.widget_habit_bar_top)?.text)
        assertEquals("早起", findById<TextView>(root, R.id.widget_habit_bar_top_lbl)?.text)
        assertTrue("今日刻度应可见", findById<View>(root, R.id.widget_habit_bar_today_ticks)?.visibility == View.VISIBLE)
        assertTrue("焦点刻度应可见", findById<View>(root, R.id.widget_habit_bar_top_ticks)?.visibility == View.VISIBLE)
    }

    @Test
    fun `打卡空态`() {
        val root = render(HabitWidgetProvider().bindViews(context, 1, emptyList(), 340, 148))
        assertTrue(texts(root).contains("暂无习惯"))
    }

    // ── 计数器（v22 = 目标进度） ──

    private fun goalCounters() = listOf(
        LifeCounterWidgetProvider.GoalCounter(
            name = "今日喝水", current = 6.0, target = 8.0, unit = "杯", fraction = 0.75f, decreasing = false
        ),
        LifeCounterWidgetProvider.GoalCounter(
            name = "咖啡", current = 2.0, target = null, unit = null, fraction = null, decreasing = false
        ),
        LifeCounterWidgetProvider.GoalCounter(
            name = "烟", current = 0.0, target = null, unit = null, fraction = null, decreasing = false
        )
    )

    @Test
    fun `计数器 4x2 主计数带还差与两枚副计数`() {
        val root = renderFixed(LifeCounterWidgetProvider().bindViews(context, 1, goalCounters(), 340, 148), 1020, 444)
        assertEquals("今日喝水", findById<TextView>(root, R.id.widget_counter_title)?.text)
        assertEquals("6/8", findById<TextView>(root, R.id.widget_counter_hero)?.text)
        // 2026-10-25 对稿：hero 进头部行（26sp），wide 不再有 meta「还差 N」视图
        assertNull(findById<View>(root, R.id.widget_counter_meta))
        val labels = texts(root)
        assertTrue(labels.contains("咖啡"))
        assertTrue(labels.contains("烟"))
        assertTrue("八点宿主应可见", findById<View>(root, R.id.widget_counter_dots_host)?.visibility == View.VISIBLE)
    }

    @Test
    fun `计数器 4x1 三列读数与进度条`() {
        val root = render(LifeCounterWidgetProvider().bindViews(context, 1, goalCounters(), 340, 74))
        assertEquals("6/8", findById<TextView>(root, R.id.widget_counter_bar_main)?.text)
        assertEquals("2", findById<TextView>(root, R.id.widget_counter_bar_sub1)?.text)
        assertEquals("0", findById<TextView>(root, R.id.widget_counter_bar_sub2)?.text)
        // 2026-10-25 对稿：4×1 去掉标题与进度条，只剩徽章 + 三列（prog/title 的 id 已随布局删除）
        assertEquals("今日喝水", findById<TextView>(root, R.id.widget_counter_bar_main_lbl)?.text)
    }

    @Test
    fun `计数器目标值超过 8 不画点`() {
        val big = listOf(
            LifeCounterWidgetProvider.GoalCounter(
                name = "本月阅读", current = 7.0, target = 12.0, unit = "本", fraction = 0.58f, decreasing = false
            )
        )
        val root = render(LifeCounterWidgetProvider().bindViews(context, 1, big, 340, 148))
        assertEquals(View.GONE, findById<View>(root, R.id.widget_counter_dots_host)?.visibility)
    }

    @Test
    fun `计数器无目标给空态`() {
        val root = render(LifeCounterWidgetProvider().bindViews(context, 1, emptyList(), 340, 148))
        assertTrue(texts(root).contains("暂无目标"))
    }

    // ── 物品（v22） ──

    private fun assetCategories() = listOf(
        HeldCategoryCount("DIGITAL", 4),
        HeldCategoryCount("SPORTS", 3),
        HeldCategoryCount("FURNITURE", 1),
        HeldCategoryCount("BOOKS", 1),
        HeldCategoryCount("MUSICAL", 1),
        HeldCategoryCount("PHOTOGRAPHY", 1)
    )

    @Test
    fun `物品 4x2 六格图例与总数`() {
        val root = renderFixed(AssetWidgetProvider().bindViews(context, 1, assetCategories(), 340, 148), 1020, 444)
        assertEquals("11 件", findById<TextView>(root, R.id.widget_asset_total)?.text)
        val labels = texts(root)
        assertTrue(labels.contains("数码"))
        assertTrue(labels.contains("运动"))
    }

    @Test
    fun `物品分类超限时第 6 格是聚合槽`() {
        val many = List(8) { HeldCategoryCount("CAT$it", it + 1) } + HeldCategoryCount("OTHER", 3)
        val cells = buildCells(many, 6)
        assertEquals(6, cells.size)
        assertTrue("第 6 格应是聚合槽", cells.last().isMore)
        assertEquals("落选 4 类(CAT6/7/8/OTHER)共 24 件", 24, cells.last().count)
        assertEquals("聚合槽用第 6 色", MORE_FAMILY, cells.last().familyIdx)
        // 列出的件数之和 = 全部件数（与头部总数对得上）
        assertEquals(many.sumOf { it.count }, cells.sumOf { it.count })
        // 真「其他」排在前面时按自己的名字显示，不参与聚合
        val withOtherFirst = listOf(HeldCategoryCount("OTHER", 9)) + List(8) { HeldCategoryCount("CAT$it", it + 1) }
        assertTrue("真其他应占一格", buildCells(withOtherFirst, 6).any { !it.isMore && it.key == "OTHER" })
    }

    @Test
    fun `物品 2x2 是四行纯文本列表`() {
        val few = listOf(
            HeldCategoryCount("DIGITAL", 4),
            HeldCategoryCount("SPORTS", 3),
            HeldCategoryCount("FURNITURE", 1)
        )
        val root = renderFixed(AssetWidgetProvider().bindViews(context, 1, few, 160, 160), 480, 480)
        // 稿 §2-4：2×2 = 分段条 + **纯文本列表**（色点+类名+件数，无胶囊底色），
        // 「最多」行在 v37 里已被四行列表取代
        assertEquals(View.GONE, findById<View>(root, R.id.widget_asset_most_row)?.visibility)
        val host = findById<ViewGroup>(root, R.id.widget_asset_grid_host)
        assertEquals("三类就排三行", 3, host?.childCount)
        val first = host!!.getChildAt(0)
        assertEquals("数码", findById<TextView>(first, R.id.widget_asset_text_name)?.text)
        assertEquals("4", findById<TextView>(first, R.id.widget_asset_text_value)?.text)
    }

    @Test
    fun `物品 4x1 三列读数`() {
        val root = render(AssetWidgetProvider().bindViews(context, 1, assetCategories(), 340, 74))
        assertEquals("11 件", findById<TextView>(root, R.id.widget_asset_bar_total)?.text)
        assertEquals("6", findById<TextView>(root, R.id.widget_asset_bar_cats)?.text)
        assertEquals("数码 4", findById<TextView>(root, R.id.widget_asset_bar_most)?.text)
    }

    @Test
    fun `物品空态`() {
        val root = render(AssetWidgetProvider().bindViews(context, 1, emptyList(), 340, 148))
        assertTrue(texts(root).contains("暂无物品记录"))
    }

    // ── 订阅（v22） ──

    private fun renewals() = listOf(
        SubscriptionWidgetProvider.Renewal("云盘会员", "25", 0),
        SubscriptionWidgetProvider.Renewal("视频会员", "15", 5),
        SubscriptionWidgetProvider.Renewal("音乐会员", "8", 7)
    )

    @Test
    fun `订阅 4x2 三行待扣与本月合计`() {
        val root = renderFixed(SubscriptionWidgetProvider().bindViews(context, 1, renewals(), 340, 148), 1020, 444)
        // 三笔同在本月(稿口径),合计 48
        assertEquals("¥48", findById<TextView>(root, R.id.widget_subscription_month_sum)?.text)
        val labels = texts(root)
        assertTrue(labels.contains("云盘会员"))
        assertTrue(labels.contains("视频会员"))
        assertTrue(labels.contains("音乐会员"))
        assertTrue("行金额应带货币符号", labels.contains("¥25"))
    }

    @Test
    fun `订阅 4x1 三列读数与分段条`() {
        val root = render(SubscriptionWidgetProvider().bindViews(context, 1, renewals(), 340, 74))
        assertEquals("¥48", findById<TextView>(root, R.id.widget_subscription_bar_month)?.text)
        assertNotNull("下个扣费日应有值", findById<TextView>(root, R.id.widget_subscription_bar_next)?.text)
        assertEquals("¥25", findById<TextView>(root, R.id.widget_subscription_bar_amount)?.text)
        assertTrue("分段条应可见", findById<View>(root, R.id.widget_subscription_seg_host)?.visibility == View.VISIBLE)
    }

    @Test
    fun `订阅空态`() {
        val root = render(SubscriptionWidgetProvider().bindViews(context, 1, emptyList(), 340, 148))
        assertTrue(texts(root).any { it.contains("暂无") })
    }

    // ── 接下来（v22） ──

    private fun nextUpFocus(): NextUpWidgetProvider.NextUpFocus {
        val target = previewToday.plusDays(10)
        return NextUpWidgetProvider.NextUpFocus(
            kind = NextUpWidgetProvider.Kind.COUNTDOWN,
            title = "爸爸生日",
            meta = target.toString(),
            bigValue = "10",
            progress = 0.62f,
            reminder = "09:00",
            daysLeft = 10,
            targetDate = target
        )
    }

    @Test
    fun `接下来 4x2 天数进度条与说明行`() {
        val root = renderFixed(NextUpWidgetProvider().bindViews(context, 1, nextUpFocus(), 340, 148), 1020, 444)
        assertEquals("10 天", findById<TextView>(root, R.id.widget_next_hero)?.text)
        val meta = findById<TextView>(root, R.id.widget_next_meta)?.text
        assertTrue("说明行应含事件与日期：$meta", meta?.contains("爸爸生日") == true && meta.contains("11/1"))
        assertTrue("说明行应含提醒与星期：$meta", meta?.contains("提醒 09:00") == true)
        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_next_prog)?.visibility)
    }

    @Test
    fun `接下来 4x1 日历条格数等于剩余天数`() {
        val root = render(NextUpWidgetProvider().bindViews(context, 1, nextUpFocus(), 340, 74))
        assertEquals("10 天", findById<TextView>(root, R.id.widget_next_bar_left)?.text)
        assertEquals("爸爸生日", findById<TextView>(root, R.id.widget_next_bar_mid)?.text)
        val strip = findById<ViewGroup>(root, R.id.widget_next_strip_host)
        assertTrue("10 天应画日历条", strip?.visibility == View.VISIBLE)
        val stripLayout = strip?.getChildAt(0) as? ViewGroup
        assertEquals("一格一天，末格是事件日", 10, stripLayout?.childCount)
    }

    @Test
    fun `接下来日历条超过 10 天整体收起`() {
        val far = nextUpFocus().copy(daysLeft = 20, bigValue = "20")
        val root = render(NextUpWidgetProvider().bindViews(context, 1, far, 340, 74))
        assertEquals(View.GONE, findById<View>(root, R.id.widget_next_strip_host)?.visibility)
        assertEquals("20 天", findById<TextView>(root, R.id.widget_next_bar_left)?.text)
    }

    @Test
    fun `接下来待办焦点 hero 是今天`() {
        val todo = NextUpWidgetProvider.NextUpFocus(
            kind = NextUpWidgetProvider.Kind.TODO, title = "交房租", meta = null, bigValue = "", itemId = 7
        )
        val root = render(NextUpWidgetProvider().bindViews(context, 1, todo, 340, 148))
        assertEquals("今天", findById<TextView>(root, R.id.widget_next_hero)?.text)
        assertEquals("进度条应收起", View.GONE, findById<View>(root, R.id.widget_next_prog)?.visibility)
    }

    // ── 概览（v22） ──

    private fun dashboardSnapshot() = DashboardWidgetProvider.DashboardSnapshot(
        todayExpense = 12_800,
        todayIncome = 0,
        todayExpenseCount = 3,
        weekday = "周四",
        todoRemaining = 3,
        todoTotal = 5,
        habitDone = 2,
        habitTotal = 5,
        subDateText = "11/12",
        subDaysLeft = 21
    )

    @Test
    fun `概览 4x2 左 hero 右三胶囊`() {
        val root = renderFixed(DashboardWidgetProvider().bindViews(context, 1, dashboardSnapshot(), 340, 148), 1020, 444)
        assertEquals("¥128", findById<TextView>(root, R.id.widget_dash_hero)?.text)
        assertEquals("今日支出", findById<TextView>(root, R.id.widget_dash_hero_lbl)?.text)
        val labels = texts(root)
        assertTrue(labels.contains("待办"))
        assertTrue(labels.contains("打卡"))
        assertTrue(labels.contains("订阅"))
        assertTrue(labels.contains("共 5 项"))
        assertTrue(labels.contains("2/5"))
        assertTrue("订阅胶囊副信息是下次扣费日", labels.contains("11/12"))
    }

    @Test
    fun `概览 4x1 四列读数`() {
        val root = render(DashboardWidgetProvider().bindViews(context, 1, dashboardSnapshot(), 340, 74))
        assertEquals("¥128", findById<TextView>(root, R.id.widget_dash_bar_expense)?.text)
        assertEquals("3", findById<TextView>(root, R.id.widget_dash_bar_todo)?.text)
        assertEquals("2/5", findById<TextView>(root, R.id.widget_dash_bar_habit)?.text)
        assertEquals("21 天", findById<TextView>(root, R.id.widget_dash_bar_sub)?.text)
        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_dash_bar_habit_dots)?.visibility)
    }

    // ── 快捷入口（v22） ──

    @Test
    fun `快捷入口 4x2 八格`() {
        val root = renderFixed(ShortcutsWidgetProvider().bindViews(context, 1, 340, 148), 1020, 444)
        val grid = findById<ViewGroup>(root, R.id.widget_sc_grid_host)
        assertNotNull(grid)
        assertEquals("两行八格", 2, grid?.childCount)
        val labels = texts(root)
        assertTrue(labels.contains("记账"))
        assertTrue(labels.contains("净资产"))
        assertTrue(labels.contains("日历"))
    }

    @Test
    fun `快捷入口 2x2 四格带头部`() {
        val root = renderFixed(ShortcutsWidgetProvider().bindViews(context, 1, 160, 160), 480, 480)
        val grid = findById<ViewGroup>(root, R.id.widget_sc_grid_host)
        assertEquals("两行四格", 2, grid?.childCount)
        assertTrue(texts(root).contains("快捷入口"))
    }

    @Test
    fun `快捷入口 4x1 四格无头部`() {
        val root = render(ShortcutsWidgetProvider().bindViews(context, 1, 340, 74))
        assertFalse("小档不摆标题", texts(root).contains("快捷入口"))
        val grid = findById<ViewGroup>(root, R.id.widget_sc_grid_host)
        assertEquals("一行四格", 1, grid?.childCount)
    }

    // ── 账单三样式（Phase 3a 已落地，回归防拆） ──

    private fun billSnapshot() = BillWidgetProvider.BillSnapshot(
        monthlyExpense = 820_000,
        monthlyIncome = 5_000_000,
        budget = Budget(yearMonth = "2026-10", totalBudget = 1_000_000),
        topCategory = "餐饮",
        dailyExpense = listOf(12_000, 4_500, 38_000, 0, 21_500, 62_000, 15_800)
    )

    @Test
    fun `数据版渲染支出预算用量与近七天柱`() {
        val root = render(BillWidgetProvider().bindViews(context, 1, billSnapshot(), accent))
        assertEquals("¥8,200", findById<TextView>(root, R.id.widget_expense_amount)?.text)
        val labels = texts(root)
        assertTrue("应含预算标签", labels.contains("预算已用"))
        assertTrue("应含用量百分比", labels.contains("82%"))
    }

    // ── 净资产（Phase 3a 已落地，回归防拆） ──

    @Test
    fun `净资产组件渲染净值合计`() {
        val root = render(
            NetWorthWidgetProvider().bindViews(
                context, 1, totalBalance = 1_285_000, widthDp = 160, monthDelta = 26_400, heightDp = 160
            )
        )
        assertEquals("¥1.3万", findById<TextView>(root, R.id.widget_net_amount)?.text)
    }

    @Test
    fun `净资产隐藏态三样一起收`() {
        val views = NetWorthWidgetProvider().bindViews(
            context, 1, totalBalance = 1_285_000, widthDp = 340, monthDelta = 26_400, heightDp = 148,
            liabs = 6_820, hidden = true
        )
        val root = render(views)
        assertEquals(WidgetPrivacy.MASK_AMOUNT, findById<TextView>(root, R.id.widget_net_amount)?.text)
        // 隐藏态涨跌不收(收掉反而泄露「无动静」):换中性掩码 + 中性灰底
        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_net_delta)?.visibility)
        assertEquals(WidgetPrivacy.MASK_DELTA, findById<TextView>(root, R.id.widget_net_delta)?.text)
    }

    // ── 工具函数 ──

    @Test
    fun `金额折叠阈值是 5 位整数`() {
        assertEquals("¥9,999", WidgetData.formatMoneyCompact(context, 999_900))
        assertEquals("¥1万", WidgetData.formatMoneyCompact(context, 1_000_000))
        assertTrue(WidgetData.formatMoneyCompact(context, 1_285_000).startsWith("¥"))
    }

    @Test
    fun `窄格子金额不带符号只到元`() {
        assertEquals("9,999", WidgetData.formatAmountCompact(context, 999_900))
        assertEquals("1.3万", WidgetData.formatAmountCompact(context, 1_285_000))
    }

    private val previewToday = LocalDate.of(2026, 10, 22)

    private fun todayAt(hour: Int, minute: Int): Long =
        previewToday.atTime(hour, minute).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `族色板是八色轮转`() {
        val first = WidgetV22.family(context, 0).d
        val ninth = WidgetV22.family(context, 8).d
        assertEquals("第 9 族绕回第 1 族", first, ninth)
    }
}
