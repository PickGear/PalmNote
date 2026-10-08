package com.palmnote.data.worker

import com.palmnote.domain.model.ReminderSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 提醒通知 id 的两个性质，缺一个都会出问题：
 *
 * - **同一条提醒每天算出来必须是同一个 id** —— 否则「原地更新一条」就退化成「一天堆一条」
 *   （提前 7 天会堆出 8 条）；
 * - **不同种类 / 不同条目不能撞号** —— 撞了就是两条不同的提醒互相覆盖。
 */
class ReminderNotifyIdTest {

    private val kinds = ReminderSpec.Kind.entries

    @Test
    fun `同一条提醒每天算出来是同一个 id`() {
        kinds.forEach { kind ->
            assertEquals(
                "kind=$kind 的 id 必须稳定",
                lifeReminderNotifyId(kind, 42L),
                lifeReminderNotifyId(kind, 42L)
            )
        }
    }

    @Test
    fun `不同种类与不同条目互不撞号`() {
        val ids = kinds.flatMap { kind -> listOf(1L, 2L, 500_000L, 999_999L).map { lifeReminderNotifyId(kind, it) } }
        assertEquals("id 必须两两不同", ids.size, ids.distinct().size)
    }

    @Test
    fun `生活提醒与物品到期提醒的号段不重叠`() {
        kinds.forEach { kind ->
            assertNotEquals(
                "kind=$kind 与物品到期提醒撞号了",
                assetExpiryNotifyId(1L),
                lifeReminderNotifyId(kind, 1L)
            )
        }
    }
}
