package com.palmnote.ui.bills

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.Color
import com.palmnote.ui.components.CategoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 支出分类的取值口径。
 *
 * 回归点：预算页的「添加分类预算」原来只列数据库里的自定义分类，没建过自定义分类的用户
 * 会看到一张空列表。这里钉住「没有自定义分类时也必须返回内置预设」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ExpenseCategoryItemsTest {

    private fun custom(name: String) = CategoryItem(name, Icons.Outlined.Settings, Color.Gray)

    @Test
    fun `没有自定义分类时仍返回内置预设`() {
        val items = resolveExpenseCategoryItems(
            custom = emptyList(),
            presetOverrides = emptyMap(),
            usageCounts = emptyMap()
        )
        assertTrue("内置预设不能为空，否则分类预算选不到任何分类", items.size > 20)
        assertTrue(items.any { it.name == "餐饮" })
    }

    @Test
    fun `自定义分类追加在预设里并参与结果`() {
        val items = resolveExpenseCategoryItems(
            custom = listOf(custom("我的分类")),
            presetOverrides = emptyMap(),
            usageCounts = emptyMap()
        )
        assertTrue(items.any { it.name == "我的分类" })
        assertTrue(items.any { it.name == "餐饮" })
    }

    @Test
    fun `与预设同名的自定义分类只保留一条`() {
        val items = resolveExpenseCategoryItems(
            custom = listOf(custom("餐饮")),
            presetOverrides = emptyMap(),
            usageCounts = emptyMap()
        )
        assertEquals(1, items.count { it.name == "餐饮" })
    }

    @Test
    fun `被停用的预设不出现在选项里`() {
        val items = resolveExpenseCategoryItems(
            custom = emptyList(),
            presetOverrides = mapOf("preset_EXPENSE_餐饮" to """{"enabled":false}"""),
            usageCounts = emptyMap()
        )
        assertFalse(items.any { it.name == "餐饮" })
    }

    @Test
    fun `坏 JSON 不会让分类消失`() {
        val items = resolveExpenseCategoryItems(
            custom = emptyList(),
            presetOverrides = mapOf("preset_EXPENSE_餐饮" to "{not json"),
            usageCounts = emptyMap()
        )
        assertTrue(items.any { it.name == "餐饮" })
    }

    @Test
    fun `按使用频次降序排列`() {
        val items = resolveExpenseCategoryItems(
            custom = emptyList(),
            presetOverrides = emptyMap(),
            usageCounts = mapOf("交通" to 99)
        )
        assertEquals("交通", items.first().name)
    }

    @Test
    fun `收入分类走同一套规则`() {
        val items = resolveIncomeCategoryItems(
            custom = emptyList(),
            presetOverrides = emptyMap(),
            usageCounts = emptyMap()
        )
        assertTrue(items.isNotEmpty())
        assertTrue(items.any { it.name == "工资" })
    }
}
