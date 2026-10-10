package com.palmnote.domain.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * 快捷添加的自然语言解析（克制版：只认明确的日期与时间词）：
 * 从一句话里摘出日期与时间，剩下的文本就是任务标题。
 *
 * 支持（中文优先，命中即消费）：
 * - 相对日：今天 / 明天 / 后天 / 大后天
 * - 周几：周X / 星期X（默认取下一个；「下周X」取下一周的）
 * - 绝对日：N月N日 / N月N号（今年已过则取明年）
 * - 时间：N点 / N点半 / N点M分 / HH:MM（可带 上午/早上/中午/下午/傍晚/晚上 前缀）
 *
 * 没有日期词 → 默认今天；没有时间词 → null（当天无时刻）。
 * 标题 = 原文去掉命中的日期时间词后的剩余文本（可空，调用方兜底）。
 */
object QuickEntryParser {

    data class Parsed(val date: LocalDate, val time: LocalTime?, val title: String)

    fun parse(raw: String, today: LocalDate = LocalDate.now()): Parsed? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        var date: LocalDate? = null
        var time: LocalTime? = null

        DATE_RULES.forEach { rule ->
            if (date == null) {
                rule.find(text)?.let { m ->
                    date = rule.resolve(m, today)
                    text = text.replaceFirst(m.value, " ")
                }
            }
        }
        TIME_RULES.forEach { rule ->
            if (time == null) {
                rule.find(text)?.let { m ->
                    time = rule.resolve(m)
                    text = text.replaceFirst(m.value, " ")
                }
            }
        }

        val title = text.replace(Regex("\\s+"), " ").trim()
        return Parsed(date ?: today, time, title)
    }

    // ---- 日期规则 ----

    private class DateRule(val pattern: Regex, val resolveFn: (MatchResult, LocalDate) -> LocalDate?) {
        fun find(text: String): MatchResult? = pattern.find(text)
        fun resolve(m: MatchResult, today: LocalDate): LocalDate? = resolveFn(m, today)
    }

    private val DATE_RULES = listOf(
        DateRule(Regex("(大后天|后天|明天|明日|今天|今日)")) { m, today ->
            val offset = when (m.value) {
                "大后天" -> 3L
                "后天" -> 2L
                "明天", "明日" -> 1L
                else -> 0L
            }
            today.plusDays(offset)
        },
        DateRule(Regex("(下?周|下?星期)([一二三四五六日天])")) { m, today ->
            val weekday = weekdayOf(m.groupValues[2]) ?: return@DateRule null
            val base = if (m.value.startsWith("下")) today.plusWeeks(1) else today
            base.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday)))
        },
        DateRule(Regex("(\\d{1,2})月(\\d{1,2})[日号]")) { m, today ->
            val month = m.groupValues[1].toIntOrNull() ?: return@DateRule null
            val day = m.groupValues[2].toIntOrNull() ?: return@DateRule null
            runCatching {
                val candidate = today.withMonth(month).withDayOfMonth(day)
                if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
            }.getOrNull()
        }
    )

    private fun weekdayOf(c: String): Int? = when (c) {
        "一" -> 1
        "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        "日", "天" -> 7
        else -> null
    }

    // ---- 时间规则 ----

    private class TimeRule(val pattern: Regex, val resolveFn: (MatchResult) -> LocalTime?) {
        fun find(text: String): MatchResult? = pattern.find(text)
        fun resolve(m: MatchResult): LocalTime? = resolveFn(m)
    }

    private val TIME_RULES = listOf(
        // HH:MM（无时段前缀，24 小时制）
        TimeRule(Regex("([01]?\\d|2[0-3])[:：]([0-5]\\d)")) { m ->
            runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
        },
        // N点[半|M分]（可带时段前缀）
        TimeRule(Regex("(上午|早上|中午|下午|傍晚|晚上)?\\s*(\\d{1,2})[点时]((\\d{1,2})分?|半)?")) { m ->
            var hour = m.groupValues[2].toIntOrNull() ?: return@TimeRule null
            // 注意：未参与匹配的可选组在 groupValues 里是 ""（不是 null），用 isNotEmpty 判定
            val minute = when {
                m.groupValues[3] == "半" -> 30
                m.groupValues[4].isNotEmpty() -> m.groupValues[4].toIntOrNull() ?: return@TimeRule null
                else -> 0
            }
            when (m.groupValues[1]) {
                "下午", "傍晚", "晚上" -> if (hour < 12) hour += 12
                "中午" -> if (hour < 11) hour += 12
            }
            if (hour !in 0..23 || minute !in 0..59) null else LocalTime.of(hour, minute)
        }
    )
}
