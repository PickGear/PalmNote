package com.palmnote.ui.bills

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.BillType
import com.palmnote.domain.repository.BillRepository
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 导出时间范围的口径守卫：预设区间必须与自然月/自然年对齐、含首尾整天；
 * 统计与导出都只认这个区间，且一样排除演示账单。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class BillExportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)

    private lateinit var repo: BillRepository
    private lateinit var bills: MutableStateFlow<List<Bill>>
    private lateinit var viewModel: BillExportViewModel

    private fun dayMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun bill(date: LocalDate, isDemo: Boolean = false, amount: Long = 1000) = Bill(
        amount = amount,
        type = BillType.EXPENSE,
        category = "餐饮",
        date = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        yearMonth = "%04d-%02d".format(date.year, date.monthValue),
        accountBookId = 1,
        walletId = 1,
        isDemo = isDemo
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        bills = MutableStateFlow(emptyList())
        repo = mockk(relaxed = true)
        every { repo.getAllBills() } returns bills
        viewModel = BillExportViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            repo,
            MutableStateFlow<List<Wallet>>(emptyList()),
            MutableStateFlow<List<AccountBook>>(emptyList())
        )
        dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `all preset has no bounds`() {
        assertNull(viewModel.boundsOf(ExportRangePreset.ALL))
    }

    @Test
    fun `this month covers first to last day inclusive`() {
        val bounds = viewModel.boundsOf(ExportRangePreset.THIS_MONTH)!!
        val first = today.withDayOfMonth(1)
        val last = today.withDayOfMonth(today.lengthOfMonth())
        assertEquals(dayMillis(first), bounds.startMillis)
        // 最后一刻 = 次日零点前一毫秒
        assertEquals(dayMillis(last.plusDays(1)) - 1, bounds.endMillis)
    }

    @Test
    fun `last month covers previous calendar month`() {
        val bounds = viewModel.boundsOf(ExportRangePreset.LAST_MONTH)!!
        val lastMonth = today.minusMonths(1)
        assertEquals(dayMillis(lastMonth.withDayOfMonth(1)), bounds.startMillis)
        assertEquals(
            dayMillis(lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).plusDays(1)) - 1,
            bounds.endMillis
        )
    }

    @Test
    fun `last three months span three calendar months including current`() {
        val bounds = viewModel.boundsOf(ExportRangePreset.LAST_3_MONTHS)!!
        assertEquals(dayMillis(today.withDayOfMonth(1).minusMonths(2)), bounds.startMillis)
        // 终点也必须落在本月最后一天的 23:59:59.999，而不是今天的最后一刻
        val lastDay = today.withDayOfMonth(today.lengthOfMonth())
        assertEquals(dayMillis(lastDay.plusDays(1)) - 1, bounds.endMillis)
    }

    @Test
    fun `this year starts at january first`() {
        val bounds = viewModel.boundsOf(ExportRangePreset.THIS_YEAR)!!
        assertEquals(dayMillis(today.withDayOfYear(1)), bounds.startMillis)
        // 终点 = 12 月 31 日 23:59:59.999（含首尾整天）
        val lastDay = today.withDayOfYear(today.lengthOfYear())
        assertEquals(dayMillis(lastDay.plusDays(1)) - 1, bounds.endMillis)
    }

    @Test
    fun `custom range normalizes reversed input`() {
        val a = dayMillis(today.minusDays(10))
        val b = dayMillis(today)
        viewModel.setCustomRange(b, a)
        val bounds = viewModel.boundsOf()!!
        assertEquals(a, bounds.startMillis)
        assertEquals(b, bounds.endMillis)
    }

    @Test
    fun `file name embeds range and falls back to plain name for all`() {
        assertTrue(viewModel.fileName().endsWith(".csv"))
        viewModel.setCustomRange(dayMillis(today.minusDays(2)), dayMillis(today))
        val name = viewModel.fileName()
        assertTrue("文件名要带起止日期：$name", name.contains("palmnote_bills_"))
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").withZone(zone)
        assertTrue("应含起始日：$name", name.contains(fmt.format(java.time.Instant.ofEpochMilli(dayMillis(today.minusDays(2))))))
        assertTrue("应含结束日：$name", name.contains(fmt.format(java.time.Instant.ofEpochMilli(dayMillis(today)))))
    }

    @Test
    fun `range filter keeps only bills inside bounds and skips demo`() {
        viewModel.selectPreset(ExportRangePreset.THIS_MONTH)
        val inside = bill(today)
        val outside = bill(today.withDayOfMonth(1).minusDays(1)) // 上月末
        val demo = bill(today, isDemo = true)

        val kept = viewModel.billsInRange(listOf(inside, outside, demo))

        assertEquals(1, kept.size)
        assertEquals(inside.date, kept.single().date)
    }

    @Test
    fun `all preset keeps every non-demo bill`() {
        val kept = viewModel.billsInRange(
            listOf(bill(today), bill(today.minusYears(2)), bill(today, isDemo = true))
        )
        assertEquals(2, kept.size)
    }

    @Test
    fun `switching range marks loading before the new summary arrives`() {
        // 切范围时不能让旧条数继续当「当前范围」显示；统计一开始就应回到 loading
        viewModel.selectPreset(ExportRangePreset.THIS_MONTH)
        assertTrue("切范围后应立刻回到统计中", viewModel.state.value.loading)
        dispatcher.scheduler.advanceUntilIdle()
    }
}
