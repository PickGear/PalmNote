package com.palmnote.ui.life

import android.app.Application
import com.palmnote.data.worker.nextLunarBirthday
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 「每年重复」的读数语义。
 *
 * 这一层要钉住两件事：
 * 1. **滚动口径**：生日/纪念日过去之后，「剩余天数」仍显示「还有 N 天」，而不是负值；
 * 2. **与提醒同源**：农历分支必须复用 `nextLunarBirthday` —— 两处一旦漂移，
 *    用户会看到「提醒说还有 3 天、详情页说还有 368 天」这种自相矛盾。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class YearlyRepeatTest {

    private val today = LocalDate.of(2026, 10, 4)

    @Test
    fun `公历：未到算今年，已过滚明年，当天就是当天`() {
        val later = LocalDate.of(1990, 12, 20)
        assertEquals(LocalDate.of(2026, 12, 20), nextYearlyOccurrence(later, today, lunar = false))

        val passed = LocalDate.of(1990, 3, 8)
        assertEquals(LocalDate.of(2027, 3, 8), nextYearlyOccurrence(passed, today, lunar = false))

        val onTheDay = LocalDate.of(1990, 10, 4)
        assertEquals(today, nextYearlyOccurrence(onTheDay, today, lunar = false))
    }

    /** 2/29 出生的人在平年按 2/28 过 —— 与提醒 Worker 的 `toThisYear` 同一口径。 */
    @Test
    fun `公历：2 月 29 在平年落到 2 月 28`() {
        val leapBirthday = LocalDate.of(2000, 2, 29)
        assertEquals(
            LocalDate.of(2027, 2, 28),
            nextYearlyOccurrence(leapBirthday, LocalDate.of(2026, 3, 1), lunar = false)
        )
    }

    @Test
    fun `农历分支与提醒共用同一实现`() {
        val lunarBirthday = LocalDate.of(1990, 8, 16)
        assertEquals(
            nextLunarBirthday(lunarBirthday, today),
            nextYearlyOccurrence(lunarBirthday, today, lunar = true)
        )
    }

    @Test
    fun `开了每年重复后 ELAPSED 不再给出负数`() {
        val config = FieldConfig(
            key = "remainDays", label = "剩余天数", type = FieldType.ELAPSED,
            options = listOf("targetDate")
        )
        // 目标日已过 5 天
        val obj = JsonObject(mapOf("targetDate" to JsonPrimitive(today.minusDays(5).toString())))

        assertEquals(-5.0, DerivedFields.eval(obj, config, today)!!, 0.0001)

        val rolled = DerivedFields.eval(obj, config, today, YearlyRepeat(lunar = false))
        assertTrue("滚动后应表示「还有」：$rolled", rolled!! > 0)
        // 滚到明年的同一天：天数 = 一年差 5 天
        assertEquals(360.0, rolled, 0.0001)
    }

    @Test
    fun `未开每年重复时行为一字不变（仍带符号）`() {
        val config = FieldConfig(key = "days", label = "已经过", type = FieldType.ELAPSED, options = listOf("start"))
        val obj = JsonObject(mapOf("start" to JsonPrimitive(today.minusDays(3).toString())))
        assertEquals(-3.0, DerivedFields.eval(obj, config, today)!!, 0.0001)
    }

    @Test
    fun `卡片路径也按每年重复滚动`() {
        val configJson =
            """[{"key":"targetDate","label":"目标日期","type":"DATE","showInCard":false},""" +
                """{"key":"remainDays","label":"剩余天数","type":"ELAPSED","options":["targetDate"],"showInCard":true}]"""
        val data = """{"targetDate":"${today.minusDays(5)}"}"""

        val plain = cardFieldValues(configJson, data, today).first { it.label == "剩余天数" }
        assertEquals(-5L, plain.days)

        val rolled = cardFieldValues(configJson, data, today, repeatYearly = true)
            .first { it.label == "剩余天数" }
        assertEquals(360L, rolled.days)
    }
}
