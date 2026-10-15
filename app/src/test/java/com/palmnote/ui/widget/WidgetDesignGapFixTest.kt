package com.palmnote.ui.widget

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.data.db.entity.LifeItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 设计稿差距修复回归（对照 `dist/widget-design-v2/design-v4.html`）：
 *  1) 2×1 极简档三行不再被卡片裁切（待办「下一项」/ 习惯「本周完成率」）；
 *  2) 金额统一带货币符号（订阅行 / 极简 Hero / 1×2 竖条 / 接下来）；
 *  3) 概览待办 tile 二级信息「逾期 N 项 · 明日 M 项」。
 *
 * 与 [WidgetV4AlignRenderTest] 一样把 RemoteViews 真实 inflate 成 View 树后断言。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-xxhdpi")
class WidgetDesignGapFixTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(views: RemoteViews): View = views.apply(context, FrameLayout(context))

    /** 按设计稿网格渲染：2×1 = 160×74dp，3× 密度 → 480×222px（与预览图同尺）。 */
    private fun renderFixed(views: RemoteViews, widthPx: Int, heightPx: Int): View {
        val root = views.apply(context, FrameLayout(context))
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

    // ── A1：2×1 极简档三行都在卡片内（修复前第三行被切掉半行） ──

    @Test
    fun `待办极简档下一项不被裁切`() {
        val board = WidgetData.TodoBoard(
            items = listOf(LifeItem(templateId = 1, title = "整理物品库存", status = "ACTIVE")),
            todayDone = 1,
            todayTotal = 2
        )
        val views = TodoWidgetProvider().bindMinimalViews(context, 1, board, LocalDate.now())
        views.setTextViewText(R.id.widget_todo_min_next, "下一项 整理物品库存 · 18:00")
        views.setViewVisibility(R.id.widget_todo_min_next, View.VISIBLE)
        val root = renderFixed(views, 480, 222)
        val next = findById<TextView>(root, R.id.widget_todo_min_next)
        assertTrue(
            "极简档第三行被裁切 top=${next?.top} bottom=${next?.bottom} 卡高=${root.height}",
            next != null && next.top >= 0 && next.bottom <= root.height
        )
    }

    @Test
    fun `习惯极简档本周完成率不被裁切`() {
        val rows = listOf(
            HabitWidgetProvider.HabitRow(
                templateId = 11, name = "跑步", checked = true, streak = 3,
                recent = listOf(true, true, false, true, true, false, true)
            )
        )
        val root = renderFixed(HabitWidgetProvider().bindMinimalViews(context, 1, rows), 480, 222)
        val week = findById<TextView>(root, R.id.widget_habit_min_week)
        assertTrue(
            "极简档第三行被裁切 top=${week?.top} bottom=${week?.bottom} 卡高=${root.height}",
            week != null && week.top >= 0 && week.bottom <= root.height
        )
    }

    // ── A2：金额统一带货币符号（修复前是裸数字「25」） ──

    @Test
    fun `订阅行金额带货币符号`() {
        val rows = listOf(SubscriptionWidgetProvider.Renewal("云盘会员", "25", 4))
        val root = render(SubscriptionWidgetProvider().bindViews(context, 1, rows))
        assertEquals("¥25", findById<TextView>(root, R.id.widget_subscription_item_price)?.text)
    }

    @Test
    fun `订阅极简档与竖条档金额带货币符号`() {
        val rows = listOf(SubscriptionWidgetProvider.Renewal("云盘会员", "25", 4))
        val mini = render(SubscriptionWidgetProvider().minimalViews(context, 1, rows))
        assertEquals("¥25", findById<TextView>(mini, R.id.widget_subscription_min_amount)?.text)
        val narrow = render(SubscriptionWidgetProvider().narrowViews(context, 1, rows))
        assertTrue(
            findById<TextView>(narrow, R.id.widget_subscription_narrow_next)?.text?.contains("¥25") == true
        )
    }

    @Test
    fun `接下来金额带货币符号`() {
        val focus = NextUpWidgetProvider().pickFocus(
            emptyList(),
            listOf(SubscriptionWidgetProvider.Renewal("云盘会员", "25", 1)),
            emptyList()
        )
        assertEquals("¥25", focus?.bigValue)
    }

    @Test
    fun `金额工具原样保留已带符号的原文`() {
        assertEquals("¥25", WidgetHelper.moneyText("25"))
        assertEquals("¥25.5", WidgetHelper.moneyText("25.5"))
        assertEquals("¥25", WidgetHelper.moneyText("¥25"))
        assertEquals("", WidgetHelper.moneyText(null))
        assertEquals("", WidgetHelper.moneyText("   "))
    }

    // ── A3：概览待办 tile 标签「今日待办」+ 二级「逾期 N 项 · 明日 M 项」 ──

    private fun dashboardRoot(overdue: Int, tomorrow: Int, doneToday: Int = 2): View {
        val views = RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
        DashboardWidgetProvider().bindSnapshot(
            context,
            views,
            DashboardWidgetProvider.DashboardSnapshot(
                weekTodoCount = 5,
                budgetCardAmount = "¥1800",
                budgetCardSub = "预算剩余",
                goalPct = "64%",
                goalName = "跑步 5km",
                goalCounts = "今日 3.2 / 5 km",
                todoRemaining = 2,
                todoDoneToday = doneToday,
                overdueCount = overdue,
                tomorrowCount = tomorrow,
                anniversaryTitle = "爸爸生日",
                anniversaryDays = 20,
                anniversaryDateText = "11/1 · 周日",
                budgetPercent = 82
            )
        )
        return render(views)
    }

    @Test
    fun `概览待办标签为今日待办`() {
        assertTrue(texts(dashboardRoot(overdue = 1, tomorrow = 1)).contains("今日待办"))
    }

    @Test
    fun `概览待办二级信息逾期与明日合并展示`() {
        assertEquals(
            "逾期 1 项 · 明日 1 项",
            findById<TextView>(dashboardRoot(overdue = 1, tomorrow = 1), R.id.widget_dashboard_todo_overdue)?.text
        )
        assertEquals(
            "明日 2 项",
            findById<TextView>(dashboardRoot(overdue = 0, tomorrow = 2), R.id.widget_dashboard_todo_overdue)?.text
        )
        assertEquals(
            "今日已完成 2 项",
            findById<TextView>(dashboardRoot(overdue = 0, tomorrow = 0), R.id.widget_dashboard_todo_overdue)?.text
        )
    }
}
