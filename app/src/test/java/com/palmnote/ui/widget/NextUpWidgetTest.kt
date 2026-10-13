package com.palmnote.ui.widget

import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.data.db.entity.LifeItem
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「接下来」焦点卡（pickFocus 优先级）与逐实例配色覆盖（readAccentTheme 覆盖参数）的验收。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class NextUpWidgetTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val provider = NextUpWidgetProvider()

    // ── pickFocus 优先级 ──

    private fun todo(id: Long, status: String = "ACTIVE") = LifeItem(
        id = id, templateId = 1, title = "交房租", status = status,
        dueDate = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    @Test
    fun `今天的待办压过订阅与倒数`() {
        val focus = provider.pickFocus(
            todos = listOf(todo(1)),
            renewals = listOf(SubscriptionWidgetProvider.Renewal("视频会员", "¥25", daysLeft = 1)),
            counters = listOf(LifeCounterWidgetProvider.CounterEvent(2, "妈妈生日", 5, java.time.LocalDate.now()))
        )
        assertEquals(NextUpWidgetProvider.Kind.TODO, focus?.kind)
        assertEquals("交房租", focus?.title)
        assertEquals(1L, focus?.itemId)
    }

    @Test
    fun `没有待办时 3 天内的订阅优先于倒数`() {
        val focus = provider.pickFocus(
            todos = emptyList(),
            renewals = listOf(SubscriptionWidgetProvider.Renewal("视频会员", "¥25", daysLeft = 2)),
            counters = listOf(LifeCounterWidgetProvider.CounterEvent(2, "妈妈生日", 5, java.time.LocalDate.now()))
        )
        assertEquals(NextUpWidgetProvider.Kind.RENEWAL, focus?.kind)
        assertEquals("视频会员", focus?.title)
        assertEquals("¥25", focus?.bigValue)
    }

    @Test
    fun `超过窗口期的订阅不参选，落到倒数`() {
        val focus = provider.pickFocus(
            todos = emptyList(),
            renewals = listOf(SubscriptionWidgetProvider.Renewal("视频会员", "¥25", daysLeft = 10)),
            counters = listOf(LifeCounterWidgetProvider.CounterEvent(2, "妈妈生日", 5, java.time.LocalDate.now()))
        )
        assertEquals(NextUpWidgetProvider.Kind.COUNTDOWN, focus?.kind)
        assertEquals("5", focus?.bigValue)
    }

    @Test
    fun `全空时焦点为空`() {
        assertEquals(null, provider.pickFocus(emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun `pickFocus 不做状态过滤——已完成待办的剔除是调用方职责`() {
        // onUpdateAsync 只喂未完成条目；这里钉住「pickFocus 取给定列表的第一条」，
        // 谁要是把过滤挪进 pickFocus 或忘了在调用方过滤，这条会先红。
        val focus = provider.pickFocus(
            todos = listOf(todo(1, status = "COMPLETED"), todo(2)),
            renewals = emptyList(),
            counters = emptyList()
        )
        assertEquals(1L, focus?.itemId)
    }

    // ── bindViews 渲染 ──

    private fun render(views: android.widget.RemoteViews): View {
        val parent = FrameLayout(context)
        return views.apply(context, parent)
    }

    private fun texts(root: View): List<String> {
        if (root is TextView) return listOf(root.text.toString())
        if (root is ViewGroup) return (0 until root.childCount).flatMap { texts(root.getChildAt(it)) }
        return emptyList()
    }

    @Test
    fun `待办焦点渲染标题与完成动作`() {
        val root = render(provider.bindViews(
            context, 1, provider.pickFocus(listOf(todo(9)), emptyList(), emptyList())
        ))
        assertTrue(texts(root).contains("交房租"))
        assertTrue(texts(root).contains(context.getString(R.string.widget_next_up_kind_todo)))
        assertTrue(texts(root).contains(context.getString(R.string.widget_next_up_due_today)))
        assertTrue(texts(root).contains(context.getString(R.string.widget_next_up_done)))
    }

    @Test
    fun `空焦点渲染空态并藏内容`() {
        val root = render(provider.bindViews(context, 1, null))
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_next_up_empty)?.visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_next_up_content)?.visibility)
    }

    // ── 逐实例配色覆盖 ──

    @Test
    fun `readAccentTheme 覆盖参数强制指定主题包`() = kotlinx.coroutines.test.runTest {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        val prefs = com.palmnote.data.datastore.PreferencesManager(context, scope)
        try {
            val overridden = WidgetData.readAccentTheme(context, prefs, overrideThemeId = "red")
            // 红色主题包的亮色主色；默认主题是青色（0xFF0891B2），两者必不相等
            assertEquals(0xFFC62828.toInt(), overridden.accent)
        } finally {
            scope.cancel()
        }
    }
}
