package com.palmnote.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.palmnote.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 计数文案的复数守卫。
 *
 * 起因：lint 报出 132 条 PluralsCandidate —— 英文下数量为 1 会渲染成 "1 items" / "1 days"。
 * 主路径上的那批已改成 `<plurals>`，本测试**直接断言资源解析结果**（不是看代码像不像），
 * 防止以后有人"顺手"把它们改回 `<string>`。
 *
 * 中文没有复数形式，只给 other 一档；英文 one/other 必须都对。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class PluralResourcesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "en")
    fun `english singular and plural both read correctly`() {
        val res = context.resources
        assertEquals("1 item", res.getQuantityString(R.plurals.asset_count, 1, 1))
        assertEquals("2 items", res.getQuantityString(R.plurals.asset_count, 2, 2))
        assertEquals("1 entry", res.getQuantityString(R.plurals.bill_count, 1, 1))
        assertEquals("3 entries", res.getQuantityString(R.plurals.bill_count, 3, 3))
        assertEquals("1 day left", res.getQuantityString(R.plurals.dashboard_days_until, 1, 1))
        assertEquals("22 days left", res.getQuantityString(R.plurals.dashboard_days_until, 22, 22))
        assertEquals("Used 1 day", res.getQuantityString(R.plurals.asset_used_days, 1, 1))
        assertEquals("Used 150 days", res.getQuantityString(R.plurals.asset_used_days, 150, 150))
        assertEquals("1 book", res.getQuantityString(R.plurals.account_book_count_format, 1, 1))
        assertEquals("2 books", res.getQuantityString(R.plurals.account_book_count_format, 2, 2))
        // 第二批：小组件 / 生活分类页 / 保险库 / 通知
        assertEquals("1 day", res.getQuantityString(R.plurals.widget_days_later, 1, 1))
        assertEquals("3 days", res.getQuantityString(R.plurals.widget_days_later, 3, 3))
        assertEquals("1 item", res.getQuantityString(R.plurals.life_category_detail_count, 1, 1))
        assertEquals("Locked. Retry in 1 second.", res.getQuantityString(R.plurals.vault_locked_out, 1, 1))
        assertEquals("Off · 1 record kept", res.getQuantityString(R.plurals.life_template_closed_kept, 1, 1))
        // 这条的撇号在原资源里是 \' 转义（同一个 plurals 里两种写法混用会让 AAPT2 崩），
        // 取出来应当是未转义的普通撇号；而资源里的双引号是**保留空白用的标记**、会被编译器剥掉，
        // 所以实际文本里没有引号（原串本来就是这样）
        assertEquals(
            "You've kept Morning run going for 1 day!",
            res.getQuantityString(R.plurals.notification_milestone_message, 1, "Morning run", 1)
        )
    }

    @Test
    @Config(qualifiers = "zh")
    fun `chinese keeps its single form`() {
        val res = context.resources
        assertEquals("1 件", res.getQuantityString(R.plurals.asset_count, 1, 1))
        assertEquals("2 件", res.getQuantityString(R.plurals.asset_count, 2, 2))
        assertEquals("还有1天", res.getQuantityString(R.plurals.dashboard_days_until, 1, 1))
        // 中文只给 other 一档，数量为 1 时也必须原样输出
        assertEquals("1天后", res.getQuantityString(R.plurals.widget_days_later, 1, 1))
        assertEquals("1 条", res.getQuantityString(R.plurals.bill_count, 1, 1))
    }
}
