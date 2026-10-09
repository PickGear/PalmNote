package com.palmnote.ui.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
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
    fun `待办组件渲染条目与页脚统计`() {
        val todos = listOf(
            LifeItem(templateId = 1, title = "买牛奶", dueDate = System.currentTimeMillis()),
            LifeItem(templateId = 1, title = "交房租", status = "COMPLETED", dueDate = System.currentTimeMillis())
        )
        val manager = AppWidgetManager.getInstance(context)
        val root = render(TodoWidgetProvider().bindViews(context, manager, 1, todos, accent))

        assertTrue(texts(root).any { it.contains("买牛奶") })
        assertTrue(texts(root).any { it.contains("交房租") })
        // 页脚：共 2 条 · 剩 1
        val footer = findById<TextView>(root, R.id.widget_todo_footer)
        assertEquals(2, Regex("\\d+").findAll(footer?.text ?: "").count())
    }

    @Test
    fun `待办组件空态`() {
        val manager = AppWidgetManager.getInstance(context)
        val root = render(TodoWidgetProvider().bindViews(context, manager, 1, emptyList(), accent))

        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_todo_empty)?.visibility)
        assertEquals(View.GONE, findById<View>(root, R.id.widget_todo_footer)?.visibility)
    }

    // ── 习惯组件 ──

    @Test
    fun `习惯组件渲染行与勾选态`() {
        val habits = listOf(
            Goal(id = 11, title = "跑步", streak = 3),
            Goal(id = 12, title = "阅读", streak = 0)
        )
        val root = render(HabitWidgetProvider().bindViews(context, 1, habits, checkedIds = setOf(11L), accent))

        assertTrue(texts(root).any { it.contains("跑步") })
        assertTrue(texts(root).any { it.contains("阅读") })
        // 计数 1/2，streak 徽标
        assertTrue(texts(root).any { it == "1/2" })
        assertTrue(texts(root).any { it.contains("3") })
    }

    @Test
    fun `习惯组件空态`() {
        val root = render(HabitWidgetProvider().bindViews(context, 1, emptyList(), emptySet(), accent))

        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_habit_empty)?.visibility)
        assertEquals(View.GONE, findById<View>(root, R.id.widget_habit_count)?.visibility)
    }

    // ── 账单组件 ──

    @Test
    fun `账单组件有预算时渲染预算卡`() {
        val snapshot = BillWidgetProviderBillSnapshotFixture.withBudget()
        val root = render(BillWidgetProvider().bindViews(context, 1, snapshot, accent))

        val income = findById<TextView>(root, R.id.widget_income_amount)?.text ?: ""
        val expense = findById<TextView>(root, R.id.widget_expense_amount)?.text ?: ""
        assertTrue(income.startsWith("+"))
        assertTrue(expense.startsWith("-"))
        assertEquals(View.VISIBLE, findById<View>(root, R.id.widget_budget_progress)?.visibility)
        // 横幅进度：8200/10000 → 82%（setInt 反射写不进去，须走 setProgressBar）
        assertEquals(82, findById<ProgressBar>(root, R.id.widget_budget_progress)?.progress)
        // 横幅底必须着主题色：漏了这步白底卡上就是白底白字，整条横幅不可见
        assertNotNull(
            findById<ImageView>(root, R.id.widget_budget_banner_bg)?.colorFilter
        )
    }

    @Test
    fun `账单组件无预算时渲染结余卡且进度条隐藏`() {
        val snapshot = BillWidgetProviderBillSnapshotFixture.withoutBudget()
        val root = render(BillWidgetProvider().bindViews(context, 1, snapshot, accent))

        assertEquals(View.GONE, findById<View>(root, R.id.widget_budget_progress)?.visibility)
        assertTrue(texts(root).any { it.startsWith("+") || it.contains("¥") })
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

    // ── 倒计时组件：周年滚动进度环 ──

    @Test
    fun `倒计时焦点卡按滚动进度显示进度环`() {
        val event = LifeCounterWidgetProvider.CounterEvent(
            itemId = 1,
            name = "妈妈生日",
            daysLeft = 25,
            targetDate = LocalDate.now().plusDays(25),
            progressPercent = 93
        )
        val root = render(LifeCounterWidgetProvider().counterViews(context, 1, listOf(event), isLarge = false))

        val ring = findById<ProgressBar>(root, R.id.widget_counter_ring)
        assertEquals(View.VISIBLE, ring?.visibility)
        assertEquals(93, ring?.progress)
        assertTrue(texts(root).any { it.contains("妈妈生日") })
    }

    @Test
    fun `倒计时焦点卡一次性事件不显示进度环`() {
        val event = LifeCounterWidgetProvider.CounterEvent(
            itemId = 2,
            name = "考试",
            daysLeft = 100,
            targetDate = LocalDate.now().plusDays(100)
        )
        val root = render(LifeCounterWidgetProvider().counterViews(context, 1, listOf(event), isLarge = false))

        assertEquals(View.GONE, findById<ProgressBar>(root, R.id.widget_counter_ring)?.visibility)
    }
}

/** 快照数据类的测试夹具（构造函数参数较多，集中在这保持测试可读）。 */
private object BillWidgetProviderBillSnapshotFixture {
    fun withBudget() = BillWidgetProvider.BillSnapshot(
        monthlyExpense = 8200,
        monthlyIncome = 50000,
        budget = Budget(yearMonth = "2026-10", totalBudget = 10000),
        topCategory = "餐饮"
    )

    fun withoutBudget() = BillWidgetProvider.BillSnapshot(
        monthlyExpense = 3000,
        monthlyIncome = 50000,
        budget = null,
        topCategory = null
    )
}
