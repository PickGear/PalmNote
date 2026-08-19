package com.palmnote.domain.util

import android.content.Context
import androidx.core.os.LocaleListCompat
import com.palmnote.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Date utilities — internal implementation uses java.time.
 * Public API signatures are unchanged for backward compatibility.
 */
object DateUtils {
    const val MILLIS_PER_DAY = 86400000L

    // 动态读取当前系统时区，避免固定一次导致用户中途改时区后日期逻辑错乱
    private val zone: ZoneId get() = ZoneId.systemDefault()

    // ── Formatters (replacing SimpleDateFormat — thread-safe) ──

    private val YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM")
    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    // ── Locale-aware formatters ──

    private fun getDisplayDatePattern(): String =
        if (Locale.getDefault().language == "zh") "MM月dd日" else "MMM dd"

    private fun getDisplayYearDatePattern(): String =
        if (Locale.getDefault().language == "zh") "yyyy年MM月dd日" else "MMM dd, yyyy"

    private fun getDisplayMonthPattern(): String =
        if (Locale.getDefault().language == "zh") "yyyy年MM月" else "MMM yyyy"

    // ── Helpers ──

    fun millisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    // 合理毫秒时间戳窗口:1971-01-01 ~ 2100-01-01,过滤掉 NUMBER 字段的普通数值
    private const val MIN_PLAUSIBLE_MILLIS = 31_536_000_000L
    private const val MAX_PLAUSIBLE_MILLIS = 4_102_444_800_000L

    fun isPlausibleMillis(millis: Long): Boolean =
        millis in MIN_PLAUSIBLE_MILLIS..MAX_PLAUSIBLE_MILLIS

    private fun millisToLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    // ── Public API ──

    fun formatYearMonth(timestamp: Long): String =
        millisToLocalDate(timestamp).format(YEAR_MONTH_FMT)

    fun formatDate(timestamp: Long): String =
        millisToLocalDate(timestamp).format(DATE_FMT)

    fun formatDateTime(timestamp: Long): String =
        millisToLocalDateTime(timestamp).format(DATE_TIME_FMT)

    @Deprecated("Use formatDisplayDate(context, timestamp) for proper i18n", ReplaceWith("formatDisplayDate(context, timestamp)"))
    fun formatDisplayDate(timestamp: Long): String =
        millisToLocalDate(timestamp).format(DateTimeFormatter.ofPattern(getDisplayDatePattern()))

    fun formatDisplayDate(context: Context, timestamp: Long): String =
        millisToLocalDate(timestamp).format(DateTimeFormatter.ofPattern(context.getString(R.string.date_format_display)))

    @Deprecated("Use formatDisplayYearDate(context, timestamp) for proper i18n", ReplaceWith("formatDisplayYearDate(context, timestamp)"))
    fun formatDisplayYearDate(timestamp: Long): String =
        millisToLocalDate(timestamp).format(DateTimeFormatter.ofPattern(getDisplayYearDatePattern()))

    fun formatDisplayYearDate(context: Context, timestamp: Long): String =
        millisToLocalDate(timestamp).format(DateTimeFormatter.ofPattern(context.getString(R.string.date_format_display_year)))

    fun formatDisplayFullDate(context: Context, timestamp: Long): String =
        millisToLocalDate(timestamp).format(DateTimeFormatter.ofPattern(context.getString(R.string.date_format_display_full)))

    @Deprecated("Use formatDisplayMonth(context, yearMonth) for proper i18n", ReplaceWith("formatDisplayMonth(context, yearMonth)"))
    fun formatDisplayMonth(yearMonth: String): String {
        val ym = YearMonth.parse(yearMonth, YEAR_MONTH_FMT)
        return ym.format(DateTimeFormatter.ofPattern(getDisplayMonthPattern()))
    }

    fun formatDisplayMonth(context: Context, yearMonth: String): String {
        val ym = YearMonth.parse(yearMonth, YEAR_MONTH_FMT)
        return ym.format(DateTimeFormatter.ofPattern(context.getString(R.string.date_format_display_month)))
    }

    fun getCurrentYearMonth(): String =
        LocalDate.now(zone).format(YEAR_MONTH_FMT)

    fun getTodayStart(): Long =
        LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    fun getDaysUntil(targetDate: Long): Int {
        val now = LocalDate.now(zone)
        val target = millisToLocalDate(targetDate)
        return java.time.temporal.ChronoUnit.DAYS.between(now, target).toInt()
    }

    fun getDaysSince(targetDate: Long): Int {
        val now = LocalDate.now(zone)
        val target = millisToLocalDate(targetDate)
        return java.time.temporal.ChronoUnit.DAYS.between(target, now).toInt()
    }

    @Deprecated("Use formatDisplayDateWithWeekday(context, timestamp) for proper i18n", ReplaceWith("formatDisplayDateWithWeekday(context, timestamp)"))
    fun formatDisplayDateWithWeekday(timestamp: Long): String {
        val ld = millisToLocalDate(timestamp)
        val month = ld.monthValue
        val day = ld.dayOfMonth
        val locale = Locale.getDefault()
        val weekDays = if (locale.language == "zh") {
            arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
        } else {
            arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        }
        val weekday = weekDays[ld.dayOfWeek.value % 7]
        return if (locale.language == "zh") {
            "${month}月${day}日 $weekday"
        } else {
            val monthName = ld.month.getDisplayName(TextStyle.SHORT, locale)
            "$monthName $day, $weekday"
        }
    }

    fun formatDisplayDateWithWeekday(context: Context, timestamp: Long): String {
        val ld = millisToLocalDate(timestamp)
        val day = ld.dayOfMonth
        val weekdayResId = when (ld.dayOfWeek) {
            DayOfWeek.SUNDAY -> R.string.date_weekday_sunday
            DayOfWeek.MONDAY -> R.string.date_weekday_monday
            DayOfWeek.TUESDAY -> R.string.date_weekday_tuesday
            DayOfWeek.WEDNESDAY -> R.string.date_weekday_wednesday
            DayOfWeek.THURSDAY -> R.string.date_weekday_thursday
            DayOfWeek.FRIDAY -> R.string.date_weekday_friday
            DayOfWeek.SATURDAY -> R.string.date_weekday_saturday
        }
        val weekday = context.getString(weekdayResId)
        val locale = LocaleListCompat.getDefault().get(0) ?: Locale.getDefault()
        val monthName = ld.month.getDisplayName(TextStyle.SHORT, locale)
        return context.getString(R.string.date_format_weekday_full, monthName, day, weekday)
    }

    private val BILL_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    fun formatTimeOnly(timestamp: Long): String =
        millisToLocalDateTime(timestamp).format(BILL_TIME_FMT)

    private val DISPLAY_DATE_TIME_FMT =
        DateTimeFormatter.ofPattern("MM月dd日 HH:mm")

    fun formatDisplayDateTime(timestamp: Long): String =
        millisToLocalDateTime(timestamp).format(DISPLAY_DATE_TIME_FMT)

    /**
     * 格式化账单日期：若时间恰好为 00:00（CSV 导入/无时间），仅显示日期；
     * 否则显示日期 + 时间，如 "3月4日 14:30"。
     */
    fun formatBillDate(context: Context, timestamp: Long): String {
        val ldt = millisToLocalDateTime(timestamp)
        val dateStr = ldt.toLocalDate()
            .format(DateTimeFormatter.ofPattern(context.getString(R.string.date_format_display)))
        return if (ldt.hour == 0 && ldt.minute == 0 && ldt.second == 0) {
            dateStr
        } else {
            "$dateStr ${ldt.format(BILL_TIME_FMT)}"
        }
    }

    /**
     * 保留 [original] 的时分秒，应用到 [newDateMidnight] 所在日期。
     * 用于日期选择器更换日期时保留原有时刻。
     */
    fun preserveTimeOfDay(original: Long, newDateMidnight: Long): Long {
        val origLdt = millisToLocalDateTime(original)
        val newLdt = Instant.ofEpochMilli(newDateMidnight)
            .atZone(zone).toLocalDate()
            .atTime(origLdt.toLocalTime())
        return newLdt.atZone(zone).toInstant().toEpochMilli()
    }

    fun getDayOfMonth(timestamp: Long): Int =
        millisToLocalDate(timestamp).dayOfMonth

    fun toMillis(yearMonth: String, dayOfMonth: Int): Long {
        val ym = YearMonth.parse(yearMonth, YEAR_MONTH_FMT)
        return ym.atDay(dayOfMonth).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun getWeekStart(): Long =
        LocalDate.now(zone)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun getWeekEnd(): Long =
        LocalDate.now(zone)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .atTime(23, 59, 59, 999_000_000)
            .atZone(zone).toInstant().toEpochMilli()

    fun getWeekStartForDate(date: Long): Long =
        millisToLocalDate(date)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun getWeekEndForDate(date: Long): Long =
        millisToLocalDate(date)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .atTime(23, 59, 59, 999_000_000)
            .atZone(zone).toInstant().toEpochMilli()

    @Deprecated("Use formatWeekRange(context, startDate, endDate) for proper i18n", ReplaceWith("formatWeekRange(context, startDate, endDate)"))
    fun formatWeekRange(startDate: Long, endDate: Long): String {
        val start = millisToLocalDate(startDate)
        val end = millisToLocalDate(endDate)
        val startDay = start.dayOfMonth
        val endDay = end.dayOfMonth
        val locale = Locale.getDefault()
        return if (locale.language == "zh") {
            val startMonth = start.monthValue
            val endMonth = end.monthValue
            if (startMonth == endMonth) {
                "${startMonth}月${startDay}日-${endDay}日"
            } else {
                "${startMonth}月${startDay}日-${endMonth}月${endDay}日"
            }
        } else {
            val startMonth = start.month.getDisplayName(TextStyle.SHORT, locale)
            val endMonth = end.month.getDisplayName(TextStyle.SHORT, locale)
            if (start.monthValue == end.monthValue) {
                "$startMonth $startDay-$endDay"
            } else {
                "$startMonth $startDay - $endMonth $endDay"
            }
        }
    }

    fun formatWeekRange(context: Context, startDate: Long, endDate: Long): String {
        val start = millisToLocalDate(startDate)
        val end = millisToLocalDate(endDate)
        val startDay = start.dayOfMonth
        val endDay = end.dayOfMonth
        val locale = LocaleListCompat.getDefault().get(0) ?: Locale.getDefault()
        val startMonth = start.month.getDisplayName(TextStyle.SHORT, locale)
        val endMonth = end.month.getDisplayName(TextStyle.SHORT, locale)
        return if (start.monthValue == end.monthValue) {
            context.getString(R.string.date_format_week_range_same, startMonth, startDay, endDay)
        } else {
            context.getString(R.string.date_format_week_range_diff, startMonth, startDay, endMonth, endDay)
        }
    }

    @Deprecated("Use getWeekdayNames(context) for proper i18n", ReplaceWith("getWeekdayNames(context)"))
    fun getWeekdayNames(): List<String> {
        val locale = Locale.getDefault()
        return if (locale.language == "zh") {
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        } else {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        }
    }

    fun getWeekdayNames(context: Context): List<String> = listOf(
        context.getString(R.string.date_weekday_short_mon),
        context.getString(R.string.date_weekday_short_tue),
        context.getString(R.string.date_weekday_short_wed),
        context.getString(R.string.date_weekday_short_thu),
        context.getString(R.string.date_weekday_short_fri),
        context.getString(R.string.date_weekday_short_sat),
        context.getString(R.string.date_weekday_short_sun)
    )
}
