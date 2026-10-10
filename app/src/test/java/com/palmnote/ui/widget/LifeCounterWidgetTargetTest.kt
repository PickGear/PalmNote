package com.palmnote.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 倒计时小组件的**目标日**口径。
 *
 * 这条正是原先 TODO 卡住的点：生日 / 纪念日的日期是**过去**的（1990-08-16…），
 * 直接算 `today → 锚点` 永远是负数 → 条目一条都不显示，与组件说明
 * 「倒计时 / 生日 / 纪念日」自相矛盾。现在按**下一次周年**滚。
 */
class LifeCounterWidgetTargetTest {

    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun `显式倒计时就用锚点当天`() {
        val future = LocalDate.of(2026, 12, 20)
        assertEquals(future, counterTargetDate(future, today, yearly = false, lunar = false))
        // 已过的锚点原样返回，由调用方按 daysLeft >= 0 过滤掉
        val past = LocalDate.of(2026, 3, 1)
        assertEquals(past, counterTargetDate(past, today, yearly = false, lunar = false))
    }

    @Test
    fun `生日与纪念日滚到下一次周年`() {
        // 今年还没到 → 今年
        assertEquals(
            LocalDate.of(2026, 12, 20),
            counterTargetDate(LocalDate.of(1990, 12, 20), today, yearly = true, lunar = false)
        )
        // 今年已过 → 明年（这样 daysLeft 永远 >= 0，条目才会显示）
        assertEquals(
            LocalDate.of(2027, 3, 8),
            counterTargetDate(LocalDate.of(1990, 3, 8), today, yearly = true, lunar = false)
        )
    }
}
