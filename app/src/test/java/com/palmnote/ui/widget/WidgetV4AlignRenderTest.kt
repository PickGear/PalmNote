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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.util.Locale
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v22 设计稿对齐补测（从 [WidgetRemoteViewsRenderTest] 拆出控制类体积）：
 * 日期行带星期、待办已完成绿勾盒、快捷入口 1×1 缩档仍是「记一笔」。
 * 同样是把 RemoteViews 真实 inflate 成 View 树后断言。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetV4AlignRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun render(views: RemoteViews): View {
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

    private val previewToday = LocalDate.of(2026, 10, 22)

    // ── 日期行带星期（接下来 4×1 的日期列） ──

    @Before
    fun forceChineseLocale() {
        // 断言全部按默认（中文）strings 写：单测 JVM 默认 locale 跟宿主走，必须钉死
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        val config = context.resources.configuration
        config.setLocale(Locale.SIMPLIFIED_CHINESE)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    @Test
    fun `接下来日期列带月份日与星期`() {
        val target = previewToday.plusDays(10)
        val focus = NextUpWidgetProvider.NextUpFocus(
            kind = NextUpWidgetProvider.Kind.COUNTDOWN,
            title = "爸爸生日",
            meta = target.toString(),
            bigValue = "10",
            progress = 0.62f,
            daysLeft = 10,
            targetDate = target
        )
        val root = render(NextUpWidgetProvider().bindViews(context, 1, focus, 340, 74))
        val date = findById<TextView>(root, R.id.widget_next_bar_right)?.text
        assertTrue("日期列应形如 11/1 周日，实际：$date", date?.startsWith("11/1 ") == true)
    }

    // ── 待办已完成行是绿勾盒 ──

    @Test
    fun `待办已完成行带勾记号未完成行不带`() {
        fun board(status: String) = WidgetData.TodoBoard(
            items = emptyList(),
            todayItems = listOf(
                LifeItem(
                    id = 1, templateId = 1, title = "交房租", status = status,
                    dueDate = previewToday.atTime(10, 0)
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            ),
            todayDone = if (status == "COMPLETED") 1 else 0,
            todayTotal = 1
        )
        val done = render(TodoWidgetProvider().bindViews(context, 1, board("COMPLETED"), 340, 148))
        assertTrue("已完成行应带勾记号", texts(done).contains("✓"))
        val active = render(TodoWidgetProvider().bindViews(context, 1, board("ACTIVE"), 340, 148))
        assertTrue("未完成行不应带勾记号", !texts(active).contains("✓"))
    }

    // ── 快捷入口 1×1 缩档仍是「记一笔」纯入口 ──

    @Test
    fun `快捷入口窄过两格退化成记一笔`() {
        val root = render(ShortcutsWidgetProvider().bindSmallViews(context, 1))
        assertNotNull("缩档应有布局根", root.findViewById<View>(R.id.widget_layout))
        assertTrue(texts(root).contains("记一笔"))
    }

    // ── 族色板 c6 粉是聚合槽专用色 ──

    @Test
    fun `聚合槽族色是第 6 色粉`() {
        assertEquals(5, MORE_FAMILY)
        val pink = WidgetV22.family(context, MORE_FAMILY).d
        assertEquals(context.getColor(R.color.widget_v22_c6), pink)
    }
}
