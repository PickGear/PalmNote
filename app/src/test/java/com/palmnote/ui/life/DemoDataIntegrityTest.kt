package com.palmnote.ui.life

import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.asset.assetCategoryItems
import com.palmnote.ui.bills.expenseCategoryItems
import com.palmnote.ui.bills.incomeCategoryItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **演示数据完整性审计**（把一次性的人工审计脚本固化成测试）。
 *
 * 演示数据 v11 起来自真实导出（掌记_数据.zip，以 CSV 资产随包内置），
 * 本测试守住两条底线：
 * 1. 内置演示 CSV 必须存在且可解析，列数与导出格式一致（列序由播种器按位置取字段）；
 * 2. 演示账单分类必须在 app 内置分类表内（否则落到未知分类的兜底色）。
 *
 * 生活侧已不再播种演示内容（生活页未打磨），无对应断言。
 */
class DemoDataIntegrityTest {

    /** 与 [com.palmnote.data.WealthDemoSeeder] 同规则的 quote 感知 CSV 解析。 */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && !inQuotes -> inQuotes = true
                ch == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    /** 引号内含换行的字段会把一条记录拆成多行：按引号奇偶把物理行重新拼回。 */
    private fun readDemoCsv(name: String): List<List<String>> {
        val text = File("src/main/assets/demo/$name").readText().removePrefix("\uFEFF")
        val logical = mutableListOf<String>()
        val buf = StringBuilder()
        text.lines().forEach { line ->
            if (buf.isNotEmpty()) buf.append('\n')
            buf.append(line.trim())
            if (buf.count { it == '"' } % 2 == 0) {
                logical.add(buf.toString())
                buf.clear()
            }
        }
        buf.takeIf { it.isNotEmpty() }?.let { logical.add(it.toString()) }
        return logical.map(::parseCsvLine).filter { it.firstOrNull()?.isNotBlank() == true }
    }

    @Test
    fun `demo csv assets must exist and stay parseable`() {
        val bills = readDemoCsv("user_bills.csv")
        val assets = readDemoCsv("user_assets.csv")
        // 首行是表头，其余为数据行；两种样式的纸样与汇总表都吃这两份数据
        assertTrue("演示账单 CSV 为空", bills.size > 1)
        assertTrue("演示物品 CSV 为空", assets.size > 1)
        bills.drop(1).forEach { row ->
            assertTrue("账单行列数异常（${row.size}）：${row.take(6)}", row.size >= 34)
        }
        assets.drop(1).forEach { row ->
            assertTrue("物品行列数异常（${row.size}）：${row.take(6)}", row.size >= 52)
        }
    }

    @Test
    fun `wealth demo categories must use built-in keys`() {
        // 演示账单存的是分类的**内部键**（与 BillCategoryData 的 name 一致的中文常量），
        // 不是本地化后的显示名 —— 存显示名会让英文环境下按键查图标/颜色全部落空。
        val expenseKeys = expenseCategoryItems.map { it.name }
        val incomeKeys = incomeCategoryItems.map { it.name }

        readDemoCsv("user_bills.csv")
            .filter { it.firstOrNull() == "账单" }
            .forEach { c ->
                val type = c[11]
                val category = c[12]
                val allowed = if (type == "INCOME") incomeKeys else expenseKeys
                assertTrue(
                    "演示账单「${c[21]}」分类键「$category」不在内置分类内",
                    category in allowed
                )
            }
    }

    @Test
    fun `demo prefs keys stay in sync with seeders`() {
        // 播种器的版本号都必须 > 0（0 表示从未播种，会让 ensureSeeded 判定失效）
        assertTrue(com.palmnote.data.LifeDemoSeeder.SEED_VERSION > 0)
        assertTrue(com.palmnote.data.WealthDemoSeeder.SEED_VERSION > 0)
        // 引用一下 PreferencesManager 的演示偏好，保证改名时编译期就断
        val p = PreferencesManager::class.java
        assertTrue(p.methods.any { it.name == "setLifeCategoryCompact" })
    }
}
