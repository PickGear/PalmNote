package com.palmnote.domain.repository

import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.util.DateUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

class BillDailySummaryTest {

    private val originalTimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun ts(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()

    private fun bill(date: Long, amount: Long, type: String) = Bill(
        amount = amount, type = type, category = "", date = date, yearMonth = ""
    )

    private fun dayOf(daily: com.palmnote.data.db.dao.DailySummary): Int =
        DateUtils.millisToLocalDate(daily.date).dayOfMonth

    @Test
    fun `groups full timestamps by local day including early morning bills`() {
        // 凌晨 03:00 的账按 UTC 折算是前一天（7/4 19:00），但必须归到本地日 7/5
        val bills = listOf(
            bill(ts(2026, 7, 5, 14, 30), 1000, "EXPENSE"),
            bill(ts(2026, 7, 5, 3, 0), 2000, "EXPENSE"),
            bill(ts(2026, 7, 5, 9, 0), 500, "INCOME"),
            bill(ts(2026, 7, 6, 10, 0), 700, "EXPENSE"),
        )

        val daily = bills.groupToDailySummaries()

        assertEquals(2, daily.size)
        val day5 = daily.first { dayOf(it) == 5 }
        val day6 = daily.first { dayOf(it) == 6 }
        assertEquals(3000, day5.expense) // 14:30 的 1000 + 凌晨 03:00 的 2000
        assertEquals(500, day5.income)
        assertEquals(700, day6.expense)
    }

    @Test
    fun `same day bills merge into one summary sorted ascending`() {
        val bills = listOf(
            bill(ts(2026, 7, 6, 10, 0), 700, "EXPENSE"),
            bill(ts(2026, 7, 5, 9, 0), 500, "EXPENSE"),
        )

        val daily = bills.groupToDailySummaries()

        assertEquals(2, daily.size)
        assertEquals(5, dayOf(daily[0]))
        assertEquals(6, dayOf(daily[1]))
    }

    @Test
    fun `empty list returns empty summaries`() {
        assertEquals(0, emptyList<Bill>().groupToDailySummaries().size)
    }
}
