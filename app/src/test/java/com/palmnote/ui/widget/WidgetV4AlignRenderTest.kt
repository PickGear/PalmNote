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
import com.palmnote.data.db.entity.LifeItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V4 设计稿对齐补测（第 2 批，从 [WidgetRemoteViewsRenderTest] 拆出控制类体积）：
 * 计数器描边环日历条与矮档收起、习惯今天列贯穿淡底 + 虚线环、快捷入口 1×1；
 * 以及设计稿残差修复：日期行带星期、待办已完成绿勾盒 + 删除线、习惯 1×3 今天未打卡色族描边环。
 * 同样是把 RemoteViews 真实 inflate 成 View 树后断言。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetV4AlignRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(views: RemoteViews): View {
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

    // ── 倒计时组件：日历条描边环（.dates .on）+ 矮档收起 ──

    @Test
    fun `倒计时焦点卡把目标日高亮在日历条上`() {
        // 日历条 7 格：目标日在前 5 天、后 1 天的位置上（稿子里 27…1、2，1 高亮）
        val target = LocalDate.of(2026, 11, 1)
        val event = LifeCounterWidgetProvider.CounterEvent(itemId = 1, name = "爸爸生日", daysLeft = 23, targetDate = target)
        val root = render(LifeCounterWidgetProvider().counterViews(context, 1, listOf(event), isLarge = false))

        assertEquals("27", findById<TextView>(root, R.id.widget_counter_date_0)?.text)
        assertEquals("1", findById<TextView>(root, R.id.widget_counter_date_5)?.text)
        assertEquals("2", findById<TextView>(root, R.id.widget_counter_date_6)?.text)
        // 目标日那格才有描边环，且运行时着色族色；稿子 .dates .on = 同色文字 + 加粗、不改字号
        val family = WidgetData.colorFamily(context, 3)
        val targetBg = findById<ImageView>(root, R.id.widget_counter_date_bg_5)
        assertEquals(View.VISIBLE, targetBg?.visibility)
        assertNotNull(targetBg?.colorFilter)
        assertEquals(View.GONE, findById<View>(root, R.id.widget_counter_date_bg_4)?.visibility)
        val targetText = findById<TextView>(root, R.id.widget_counter_date_5)
        assertEquals(family.hue, targetText?.currentTextColor)
        assertTrue((targetText?.paintFlags ?: 0) and android.graphics.Paint.FAKE_BOLD_TEXT_FLAG != 0)
        // 非目标格：次级色、不加粗、同字号
        val normalText = findById<TextView>(root, R.id.widget_counter_date_4)
        assertEquals(context.getColor(R.color.widget_v2_text_secondary), normalText?.currentTextColor)
        assertEquals(0, (normalText?.paintFlags ?: 0) and android.graphics.Paint.FAKE_BOLD_TEXT_FLAG)
        assertEquals(normalText?.textSize, targetText?.textSize)
        assertTrue(texts(root).any { it.contains("爸爸生日") })
        // 稿子 3×2 说明行是「2026年11月1日 · 周日」——日期带星期（窄版一直有、全尺寸此前缺）
        assertTrue(findById<TextView>(root, R.id.widget_event_date)?.text?.toString()?.contains("·") == true)
    }

    @Test
    fun `倒计时焦点卡高度不足时收起日历条`() {
        val event = LifeCounterWidgetProvider.CounterEvent(1, "妈妈生日", 12, LocalDate.now().plusDays(12))

        val compact = render(
            LifeCounterWidgetProvider().counterViews(context, 1, listOf(event), isLarge = false, compactHeight = true)
        )
        // 收的是装饰性日历条；「一行主数据 + 一行说明」里那行说明（目标日期）保留
        assertEquals(View.GONE, findById<View>(compact, R.id.widget_counter_dates)?.visibility)
        assertEquals(View.VISIBLE, findById<View>(compact, R.id.widget_event_date)?.visibility)
        // 名称与天数保留
        assertTrue(texts(compact).any { it.contains("妈妈生日") })

        val full = render(LifeCounterWidgetProvider().counterViews(context, 1, listOf(event), isLarge = false))
        assertEquals(View.VISIBLE, findById<View>(full, R.id.widget_counter_dates)?.visibility)
    }

    // ── 习惯组件：今天列贯穿淡底（.tc）+ 未打卡虚线环 ──

    @Test
    fun `习惯周历今天列贯穿淡底且未打卡给虚线环`() {
        val rows = listOf(
            HabitWidgetProvider.HabitRow(
                templateId = 11, name = "跑步", checked = true, streak = 3,
                recent = listOf(true, true, false, true, true, false, true)
            ),
            HabitWidgetProvider.HabitRow(
                templateId = 12, name = "阅读", checked = false, streak = 0,
                recent = listOf(true, false, true, true, false, true, false)
            )
        )
        val root = render(HabitWidgetProvider().bindViews(context, 1, rows))

        // 列头今天那格：淡底（着第一色族淡色）+ 该族主色数字，不再是实底胶囊白字
        val family0 = WidgetData.colorFamily(context, 0)
        assertNotNull(findById<ImageView>(root, R.id.widget_habit_head_6_bg)?.colorFilter)
        assertEquals(family0.hue, findById<TextView>(root, R.id.widget_habit_head_6)?.currentTextColor)

        val grid = requireNotNull(findById<ViewGroup>(root, R.id.widget_habit_grid))
        assertEquals(2, grid.childCount)

        // 打了卡的行：今天方柱可见，虚线环收起，列底已着淡色；行名是主色（稿子行名继承 --txt）
        val checkedRow = grid.getChildAt(0)
        assertEquals(View.VISIBLE, findById<View>(checkedRow, R.id.widget_habit_dot_6)?.visibility)
        assertEquals(View.GONE, findById<View>(checkedRow, R.id.widget_habit_dot_today_ring)?.visibility)
        assertNotNull(findById<ImageView>(checkedRow, R.id.widget_habit_dot_today_bg)?.colorFilter)
        assertEquals(
            context.getColor(R.color.widget_v2_text_primary),
            findById<TextView>(checkedRow, R.id.widget_habit_row_name)?.currentTextColor
        )

        // 没打卡的行：方柱让位，原位给虚线环（着文字三级色）
        val uncheckedRow = grid.getChildAt(1)
        assertEquals(View.GONE, findById<View>(uncheckedRow, R.id.widget_habit_dot_6)?.visibility)
        val ring = findById<ImageView>(uncheckedRow, R.id.widget_habit_dot_today_ring)
        assertEquals(View.VISIBLE, ring?.visibility)
        assertNotNull(ring?.colorFilter)
    }

    // ── 快捷入口：1×1 纯入口 ──

    @Test
    fun `快捷入口缩成1×1时只放记一笔`() {
        val root = render(ShortcutsWidgetProvider().bindSmallViews(context, 1))

        assertEquals(context.getString(R.string.widget_add_bill), texts(root).single())
        // 淡彩圆底 + 同族加号都有色
        assertNotNull(findById<ImageView>(root, R.id.widget_sc_small_bg)?.colorFilter)
        assertNotNull(findById<ImageView>(root, R.id.widget_sc_small_icon)?.colorFilter)
    }

    // ── 设计稿残差修复：待办已完成明细 / 习惯 1×3 今天空心环 ──

    @Test
    fun `待办已完成明细给绿勾盒与删除线`() {
        val board = WidgetData.TodoBoard(
            items = listOf(
                LifeItem(templateId = 1, title = "采购配件", status = "COMPLETED"),
                LifeItem(templateId = 1, title = "取快递", status = "ACTIVE")
            ),
            todayDone = 1,
            todayTotal = 2
        )
        val root = render(TodoWidgetProvider().bindViews(context, 1, board))

        // 勾盒（静态绿，无需着色）+ 文字三级色 + 删除线
        assertEquals("采购配件", findById<TextView>(root, R.id.widget_todo_done_text)?.text)
        assertNotNull(findById<ImageView>(root, R.id.widget_todo_done_check_bg))
        assertEquals(
            context.getColor(R.color.widget_v2_text_tertiary),
            findById<TextView>(root, R.id.widget_todo_done_text)?.currentTextColor
        )
        assertTrue(
            (findById<TextView>(root, R.id.widget_todo_done_text)?.paintFlags ?: 0) and
                android.graphics.Paint.STRIKE_THRU_TEXT_FLAG != 0
        )
    }

    @Test
    fun `习惯1×3今天未打卡给色族描边环`() {
        val rows = listOf(
            HabitWidgetProvider.HabitRow(
                templateId = 11, name = "阅读", checked = false, streak = 0,
                recent = listOf(false, true, true, false, true, true, false)
            )
        )
        val root = render(HabitWidgetProvider().bindNarrowViews(context, 1, rows))

        // 今天（第 7 格）未打卡：走描边环分支、着色族色；历史格照常着色（打卡族色 / 未打卡轨道灰）
        assertNotNull(findById<ImageView>(root, R.id.widget_habit_narrow_dot_6)?.colorFilter)
        assertNotNull(findById<ImageView>(root, R.id.widget_habit_narrow_dot_1)?.colorFilter)
        assertNotNull(findById<ImageView>(root, R.id.widget_habit_narrow_dot_0)?.colorFilter)
    }
}
