package com.palmnote.ui.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 深链登记处的不变量：
 * - 号段必须互不相同 —— Intent extras 不参与 filterEquals，撞号会被 FLAG_UPDATE_CURRENT
 *   互相覆盖，点所有组件都跳到同一页；
 * - Intent 的键与值必须与消费端（`MainActivity.handleWidgetIntent`）对得上。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetDeepLinkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 反射取 [WidgetDeepLink] 里指定前缀的 Int 常量：新增号段自动纳入检查，不用手工维护清单。 */
    private fun intConstants(prefix: String): Map<String, Int> =
        WidgetDeepLink::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.name.startsWith(prefix) }
            .associate { field ->
                field.isAccessible = true
                field.name to (field.get(null) as Int)
            }

    @Test
    fun `深链号段互不相同`() {
        val segments = intConstants("SEG_")
        assertTrue("号段数量异常：${segments.size}", segments.size >= 20)

        val collisions = segments.entries
            .groupBy({ it.value }, { it.key })
            .filterValues { it.size > 1 }
        assertTrue("号段撞号：$collisions", collisions.isEmpty())
    }

    @Test
    fun `目标页取值不与号段混用`() {
        // TAB_* 是字符串、SEG_* 是 Int，前缀分开只是为了可读；这里防的是有人把号段写进 TAB_
        val tabs = WidgetDeepLink::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.name.startsWith("TAB_") }
            .map { it.isAccessible = true; it.get(null) as String }
        assertTrue("目标页取值不应为空", tabs.isNotEmpty())
        assertEquals(tabs.size, tabs.distinct().size)
    }

    @Test
    fun `组件深链 Intent 的键与值与消费端一致`() {
        val pending = WidgetHelper.createPendingIntent(
            context,
            WidgetDeepLink.SEG_BILL + 7,
            WidgetDeepLink.TAB_BILL
        )

        val saved = shadowOf(pending).savedIntent
        assertEquals(WidgetDeepLink.TAB_BILL, saved.getStringExtra(WidgetDeepLink.KEY_TAB))
        // 这里根本没有组件 id：写 EXTRA_APPWIDGET_ID 只会让消费端误以为那是组件 id
        assertNull(saved.extras?.get(AppWidgetManager.EXTRA_APPWIDGET_ID))
    }

    @Test
    fun `清单模式深链带模式键且不带组件 id`() {
        val pending = WidgetHelper.createLifeListPendingIntent(
            context,
            WidgetDeepLink.SEG_TODO_ADD + 7,
            "AGENDA"
        )

        val saved = shadowOf(pending).savedIntent
        assertEquals(WidgetDeepLink.TAB_LIFE, saved.getStringExtra(WidgetDeepLink.KEY_TAB))
        assertEquals("AGENDA", saved.getStringExtra(WidgetDeepLink.KEY_LIST_MODE))
        assertNull(saved.extras?.get(AppWidgetManager.EXTRA_APPWIDGET_ID))
    }

    @Test
    fun `记录详情深链带条目 id`() {
        val pending = WidgetHelper.createLifeEventPendingIntent(context, 42L)

        val saved = shadowOf(pending).savedIntent
        assertEquals(WidgetDeepLink.TAB_LIFE, saved.getStringExtra(WidgetDeepLink.KEY_TAB))
        assertEquals("42", saved.getStringExtra(WidgetDeepLink.KEY_ITEM_ID))
    }
}
