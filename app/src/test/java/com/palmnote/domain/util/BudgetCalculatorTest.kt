package com.palmnote.domain.util

import com.palmnote.data.db.dao.BudgetSpendRow
import com.palmnote.data.db.entity.Budget
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetScope
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预算进度换算的钉：三种口径（总额/账本/分类）× 三个周期（周/月/年）。
 *
 * 全部是纯函数（时间窗口交给既有的 DateUtils 口径），JVM 直接跑，不需要 Robolectric。
 * 重点钉住「同一条聚合数据被三种口径正确切分」——这是预算页、首页预警、通知共用的分母来源。
 */
class BudgetCalculatorTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val now: Long = LocalDateTime.of(2026, 3, 18, 12, 0).atZone(zone).toInstant().toEpochMilli()

    /** 两个账本、三个分类的聚合行。 */
    private val rows = listOf(
        BudgetSpendRow(accountBookId = 1L, category = "餐饮", total = 3000),
        BudgetSpendRow(accountBookId = 1L, category = "交通", total = 1000),
        BudgetSpendRow(accountBookId = 2L, category = "餐饮", total = 2000)
    )

    @Test
    fun `总额口径 = 全部账本全部分类求和`() {
        val budget = Budget.total(BudgetPeriod.MONTH, 10_000)
        assertEquals(6000L, BudgetCalculator.spentOf(budget, rows))
    }

    @Test
    fun `账本口径只算该账本`() {
        val budget = Budget(
            period = BudgetPeriod.MONTH.name,
            scope = BudgetScope.BOOK.name,
            bookId = 2L,
            totalBudget = 10_000
        )
        assertEquals(2000L, BudgetCalculator.spentOf(budget, rows))
    }

    @Test
    fun `分类口径跨账本合并同名分类`() {
        val budget = Budget(
            period = BudgetPeriod.MONTH.name,
            scope = BudgetScope.CATEGORY.name,
            category = "餐饮",
            totalBudget = 10_000
        )
        // 账本 1 的 3000 + 账本 2 的 2000，交通的 1000 不算
        assertEquals(5000L, BudgetCalculator.spentOf(budget, rows))
    }

    @Test
    fun `账本口径找不到该账本时为零而不是全部`() {
        val budget = Budget(
            period = BudgetPeriod.MONTH.name,
            scope = BudgetScope.BOOK.name,
            bookId = 99L,
            totalBudget = 10_000
        )
        assertEquals("不能回落成全部账本", 0L, BudgetCalculator.spentOf(budget, rows))
    }

    @Test
    fun `周窗口是周一到周日且包含当前时刻`() {
        // 2026-03-18 是周三
        val (start, end) = BudgetCalculator.windowOf(BudgetPeriod.WEEK, now)
        assertEquals(DateUtils.getWeekStartForDate(now), start)
        assertEquals(DateUtils.getWeekEndForDate(now), end)
        assertEquals("2026-03-16", LocalDate.ofInstant(java.time.Instant.ofEpochMilli(start), zone).toString())
        assertTrue("窗口必须包含 now", now in start..end)
    }

    @Test
    fun `月与年窗口覆盖整段且包含当前时刻`() {
        val (monthStart, monthEnd) = BudgetCalculator.windowOf(BudgetPeriod.MONTH, now)
        assertEquals("2026-03-01", LocalDate.ofInstant(java.time.Instant.ofEpochMilli(monthStart), zone).toString())
        assertEquals("2026-03-31", LocalDate.ofInstant(java.time.Instant.ofEpochMilli(monthEnd), zone).toString())
        assertTrue(now in monthStart..monthEnd)

        val (yearStart, yearEnd) = BudgetCalculator.windowOf(BudgetPeriod.YEAR, now)
        assertEquals("2026-01-01", LocalDate.ofInstant(java.time.Instant.ofEpochMilli(yearStart), zone).toString())
        assertTrue(now in yearStart..yearEnd)
    }

    @Test
    fun `进度计算按严重度与比例排序`() {
        val budgets = listOf(
            // 正常：餐饮 5000/10000 = 50%
            Budget(
                id = 1, period = BudgetPeriod.MONTH.name, scope = BudgetScope.CATEGORY.name,
                category = "餐饮", totalBudget = 10_000
            ),
            // 超支：总额 6000/5000 = 120%
            Budget.total(BudgetPeriod.MONTH, 5_000).copy(id = 2),
            // 达阈值：交通 1000/1200 ≈ 83%
            Budget(
                id = 3, period = BudgetPeriod.MONTH.name, scope = BudgetScope.CATEGORY.name,
                category = "交通", totalBudget = 1_200
            )
        )
        val progresses = BudgetCalculator.compute(
            budgets,
            mapOf(BudgetPeriod.MONTH to rows),
            now
        )
        assertEquals("最严重的排第一", 2L, progresses[0].budget.id)
        assertTrue(progresses[0].isOver)
        assertEquals("达阈值排第二", 3L, progresses[1].budget.id)
        assertTrue(progresses[1].reachedThreshold)
        assertFalse(progresses[1].isOver)
        assertEquals("正常排最后", 1L, progresses[2].budget.id)
        assertFalse(progresses[2].reachedThreshold)
    }

    @Test
    fun `阈值取每条预算自己的设置`() {
        val cheapThreshold = Budget(
            period = BudgetPeriod.MONTH.name, scope = BudgetScope.CATEGORY.name,
            category = "餐饮", totalBudget = 10_000, reminderThreshold = 0.4
        )
        val strict = Budget(
            period = BudgetPeriod.MONTH.name, scope = BudgetScope.CATEGORY.name,
            category = "餐饮", totalBudget = 10_000, reminderThreshold = 0.9
        )
        val rowsByName = mapOf(BudgetPeriod.MONTH to rows)
        assertTrue(
            "50% 用掉 > 40% 阈值 → 该提醒",
            BudgetCalculator.compute(listOf(cheapThreshold), rowsByName, now).single().reachedThreshold
        )
        assertFalse(
            "50% 用掉 < 90% 阈值 → 不提醒",
            BudgetCalculator.compute(listOf(strict), rowsByName, now).single().reachedThreshold
        )
    }

    @Test
    fun `只取本周期对应的聚合行`() {
        // 周预算不读月度行：同一份 rows 传在月键下，周预算应为 0
        val weekly = Budget.total(BudgetPeriod.WEEK, 1000)
        val progresses = BudgetCalculator.compute(
            listOf(weekly),
            mapOf(BudgetPeriod.MONTH to rows),
            now
        )
        assertEquals(0L, progresses.single().spent)
    }

    @Test
    fun `额度为零不产生 Infinity`() {
        val zero = Budget.total(BudgetPeriod.MONTH, 0)
        val progress = BudgetCalculator.compute(listOf(zero), mapOf(BudgetPeriod.MONTH to rows), now).single()
        assertEquals(0f, progress.ratio, 0.0001f)
        assertFalse(progress.reachedThreshold)
        assertEquals(0, progress.severity)
    }

    @Test
    fun `恰好用掉阈值比例时应达标`() {
        // 0.9f 提升成 Double 是 0.899999976：以前用 Float ratio 比 Double 阈值会漏报
        val budget = Budget(
            period = BudgetPeriod.MONTH.name,
            scope = BudgetScope.TOTAL.name,
            totalBudget = 10_000,
            reminderThreshold = 0.9
        )
        val atThreshold = listOf(BudgetSpendRow(accountBookId = 1L, category = "餐饮", total = 9_000))
        val progress = BudgetCalculator.compute(
            listOf(budget), mapOf(BudgetPeriod.MONTH to atThreshold), now
        ).single()
        assertEquals(9_000L, progress.spent)
        assertTrue("恰好 90% 应判为达标", progress.reachedThreshold)
        assertEquals(1, progress.severity)
        assertEquals(90, progress.usedPercent)
    }

    @Test
    fun `大额支出的百分比用 Double 计算不丢精度`() {
        // spent 超过 2^24 分后 Float 已无法精确表示整数分
        val budget = Budget.total(BudgetPeriod.MONTH, 1_000_000_000)
        val big = listOf(BudgetSpendRow(accountBookId = 1L, category = "餐饮", total = 167_772_171))
        val progress = BudgetCalculator.compute(
            listOf(budget), mapOf(BudgetPeriod.MONTH to big), now
        ).single()
        // 16.777% 四舍五入成 17（口径与页面 / 弹窗 / 小组件一致）
        assertEquals(17, progress.usedPercent)
        assertEquals(0.167772171, progress.spent.toDouble() / progress.limit, 0.000000001)
    }
}
