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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v22 设计稿落地回归（对照 `docs/mockups/widgets/v22/index.html`）：
 *  1) 小档（2×1 / 4×1）内容不被卡片裁切（待办 hero+点 / 打卡 hero+点）；
 *  2) 金额统一带货币符号（订阅胶囊 / 接下来 / 金额工具）；
 *  3) 概览 hero 是「今日支出」，副信息给笔数与收入（v22 口径，无逾期 tile）。
 *
 * 与 [WidgetV4AlignRenderTest] 一样把 RemoteViews 真实 inflate 成 View 树后断言。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-xxhdpi")
class WidgetDesignGapFixTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(views: RemoteViews): View = views.apply(context, FrameLayout(context))

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

    // ── A1：小档裁切检查（v37 收敛 2×1 档下线，此组随档位移除） ──

    // ── A2：金额统一带货币符号（修复前是裸数字「25」） ──

    @Test
    fun `订阅胶囊金额带货币符号`() {
        val rows = listOf(SubscriptionWidgetProvider.Renewal("云盘会员", "25", 4))
        val root = render(SubscriptionWidgetProvider().bindViews(context, 1, rows, 340, 148))
        assertTrue(
            "订阅行金额应带货币符号，实际：${texts(root)}",
            texts(root).contains("¥25")
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

    // ── A3：概览 hero 是今日支出，副信息给笔数与收入 ──

    private fun dashboardRoot(): View {
        val snapshot = DashboardWidgetProvider.DashboardSnapshot(
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
        return render(DashboardWidgetProvider().bindViews(context, 1, snapshot, 340, 148))
    }

    @Test
    fun `概览 hero 是今日支出且副信息给笔数`() {
        val root = dashboardRoot()
        assertEquals("¥128", findById<TextView>(root, R.id.widget_dash_hero)?.text)
        assertTrue(
            "副信息应含 3 笔，实际：${texts(root)}",
            texts(root).any { it.contains("3 笔") }
        )
    }

    @Test
    fun `概览头部带今日与星期`() {
        assertTrue(texts(dashboardRoot()).contains("今日 · 周四"))
    }

    @Test
    fun `概览三枚胶囊是待办打卡订阅`() {
        val root = dashboardRoot()
        val labels = texts(root)
        assertTrue(labels.contains("待办"))
        assertTrue(labels.contains("打卡"))
        assertTrue(labels.contains("订阅"))
        assertTrue("待办胶囊副信息应是共 5 项", labels.contains("共 5 项"))
        assertTrue("打卡胶囊应是 2/5", labels.contains("2/5"))
    }
}
