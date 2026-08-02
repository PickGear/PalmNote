package com.palmnote.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeReport
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.FocusRecordRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeReportRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@HiltWorker
class LifeDailyCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val templateRepo: LifeTemplateRepository,
    private val itemRepo: LifeItemRepository,
    private val focusRepo: FocusRecordRepository,
    private val reportRepo: LifeReportRepository,
    private val billRepo: BillRepository,
    private val pm: PreferencesManager,
) : CoroutineWorker(context, params) {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 时间戳（毫秒）→ 系统时区的本地日期，避免按 UTC 换算导致 +8 时区差一天 */
    private fun millisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** 把日期平移到今年；2/29 在平年时落到 2/28，避免 withYear 抛异常 */
    private fun toThisYear(date: LocalDate, today: LocalDate): LocalDate =
        try { date.withYear(today.year) } catch (_: java.time.DateTimeException) { LocalDate.of(today.year, 2, 28) }

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        val timeBudgetMs = 240_000L
        fun overBudget() = System.currentTimeMillis() - startTime > timeBudgetMs
        return try {
            val dailyEnabled = pm.dailyReminderEnabled.first()
            if (dailyEnabled) checkDailyReminder()
            if (overBudget()) return Result.success()
            checkBillReminder()
            if (overBudget()) return Result.success()
            checkCountUpMilestones()
            if (overBudget()) return Result.success()
            checkCountdownExpiry()
            if (overBudget()) return Result.success()
            checkBirthdayReminders()
            if (overBudget()) return Result.success()
            checkAnniversaryReminders()
            if (overBudget()) return Result.success()
            checkSubscriptionBilling()
            if (overBudget()) return Result.success()
            tryGenerateWeeklyReport()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun checkDailyReminder() {
        com.palmnote.ui.notification.NotificationHelper.show(
            applicationContext,
            com.palmnote.ui.notification.NotificationHelper.CHANNEL_CHECKIN,
            applicationContext.getString(com.palmnote.R.string.notification_daily_title),
            applicationContext.getString(com.palmnote.R.string.notification_daily_message)
        )
    }

    private suspend fun checkBillReminder() {
        val billReminderEnabled = pm.billReminderEnabled.first()
        if (!billReminderEnabled) return
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // 账单存完整时间戳，用日期区间匹配而非精确相等，避免当天有账单仍误报"未记账"
        val bills = billRepo.getBillsByDateRange(todayStart, tomorrowStart - 1).first()
        if (bills.isEmpty()) {
            com.palmnote.ui.notification.NotificationHelper.show(
                applicationContext,
                com.palmnote.ui.notification.NotificationHelper.CHANNEL_REMINDER,
                applicationContext.getString(com.palmnote.R.string.notification_bill_title),
                applicationContext.getString(com.palmnote.R.string.notification_bill_message)
            )
        }
    }

    private suspend fun checkCountUpMilestones() {
        val today = LocalDate.now()
        val tpls = templateRepo.getAllTemplates().first().filter { it.name == "\u6B63\u6570\u65E5" }
        val milestoneDays = listOf(100L, 200L, 365L, 500L, 750L, 1000L)
        tpls.forEach { tpl ->
            itemRepo.getActiveItemsByTemplate(tpl.id, 200).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val startDateStr = (obj["start_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                        ?: (obj["startDate"] as? JsonPrimitive)?.content?.toLongOrNull()
                    if (startDateStr != null) {
                        val start = millisToLocalDate(startDateStr)
                        val days = ChronoUnit.DAYS.between(start, today)
                        if (days in milestoneDays) {
                            com.palmnote.ui.notification.NotificationHelper.show(
                                applicationContext,
                                com.palmnote.ui.notification.NotificationHelper.CHANNEL_LIFE,
                                applicationContext.getString(com.palmnote.R.string.notification_milestone_title),
                                applicationContext.getString(com.palmnote.R.string.notification_milestone_message, item.title, days)
                            )
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private suspend fun checkCountdownExpiry() {
        val today = LocalDate.now()
        val advanceDays = pm.birthdayReminderAdvanceDays.first()
        val tpls = templateRepo.getAllTemplates().first().filter { it.name.contains("\u5012\u8BA1\u65F6") }
        tpls.forEach { tpl ->
            itemRepo.getActiveItemsByTemplate(tpl.id, 200).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val dateStr = (obj["targetDate"] as? JsonPrimitive)?.content?.toLongOrNull()
                        ?: (obj["target_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                    if (dateStr != null) {
                        val target = millisToLocalDate(dateStr)
                        val daysLeft = ChronoUnit.DAYS.between(today, target)
                        when {
                            daysLeft == 0L -> com.palmnote.ui.notification.NotificationHelper.show(
                                applicationContext,
                                com.palmnote.ui.notification.NotificationHelper.CHANNEL_REMINDER,
                                applicationContext.getString(com.palmnote.R.string.notification_countdown_today_title),
                                applicationContext.getString(com.palmnote.R.string.notification_countdown_today_message, item.title)
                            )
                            daysLeft in 1..advanceDays.toLong() -> com.palmnote.ui.notification.NotificationHelper.show(
                                applicationContext,
                                com.palmnote.ui.notification.NotificationHelper.CHANNEL_REMINDER,
                                applicationContext.getString(com.palmnote.R.string.notification_countdown_soon_title),
                                applicationContext.getString(com.palmnote.R.string.notification_countdown_soon_message, item.title, daysLeft)
                            )
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private suspend fun checkBirthdayReminders() {
        val today = LocalDate.now()
        val advanceDays = pm.birthdayReminderAdvanceDays.first()
        val tpls = templateRepo.getAllTemplates().first().filter { it.name.contains("\u751F\u65E5") }
        tpls.forEach { tpl ->
            itemRepo.getActiveItemsByTemplate(tpl.id, 200).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val dateStr = (obj["date"] as? JsonPrimitive)?.content?.toLongOrNull()
                        ?: (obj["birthday_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                    if (dateStr != null) {
                        val birthDate = millisToLocalDate(dateStr)
                        val nextBirthday = toThisYear(birthDate, today)
                        val diff = ChronoUnit.DAYS.between(today, if (nextBirthday.isAfter(today) || nextBirthday == today) nextBirthday else nextBirthday.plusYears(1))
                        if (diff in 0..advanceDays.toLong()) {
                            com.palmnote.ui.notification.NotificationHelper.show(
                                applicationContext,
                                com.palmnote.ui.notification.NotificationHelper.CHANNEL_REMINDER,
                                applicationContext.getString(com.palmnote.R.string.notification_birthday_title),
                                applicationContext.getString(com.palmnote.R.string.notification_birthday_message, item.title)
                            )
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private suspend fun checkAnniversaryReminders() {
        val today = LocalDate.now()
        val advanceDays = pm.anniversaryReminderAdvanceDays.first()
        val tpls = templateRepo.getAllTemplates().first().filter { it.name.contains("\u7EAA\u5FF5\u65E5") }
        tpls.forEach { tpl ->
            itemRepo.getActiveItemsByTemplate(tpl.id, 200).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val dateStr = (obj["date"] as? JsonPrimitive)?.content?.toLongOrNull()
                    if (dateStr != null) {
                        val anniDate = millisToLocalDate(dateStr)
                        val nextAnni = toThisYear(anniDate, today)
                        val diff = ChronoUnit.DAYS.between(today, if (nextAnni.isAfter(today) || nextAnni == today) nextAnni else nextAnni.plusYears(1))
                        if (diff in 0..advanceDays.toLong()) {
                            val years = ChronoUnit.YEARS.between(anniDate, today).coerceAtLeast(0)
                            com.palmnote.ui.notification.NotificationHelper.show(
                                applicationContext,
                                com.palmnote.ui.notification.NotificationHelper.CHANNEL_REMINDER,
                                applicationContext.getString(com.palmnote.R.string.notification_anniversary_title),
                                applicationContext.getString(com.palmnote.R.string.notification_anniversary_message, item.title, years)
                            )
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private suspend fun checkSubscriptionBilling() {
        val today = LocalDate.now()
        val tpls = templateRepo.getAllTemplates().first().filter { it.name.contains("\u8BA2\u9605") }
        for (tpl in tpls) {
            for (item in itemRepo.getActiveItemsByTemplate(tpl.id, 200).first()) {
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val billingDay = (obj["billingDay"] as? JsonPrimitive)?.content?.toIntOrNull()
                        ?: (obj["billing_day"] as? JsonPrimitive)?.content?.toIntOrNull()
                    val lastBilled = (obj["lastBilledDate"] as? JsonPrimitive)?.content?.toLongOrNull()
                    val cycle = (obj["billingCycle"] as? JsonPrimitive)?.content ?: "monthly"
                    if (billingDay != null) {
                        // 31 号在小月/平年 2 月自动落到当月最后一天
                        val billDay = billingDay.coerceAtMost(today.lengthOfMonth())
                        if (today.dayOfMonth != billDay) continue
                        if (lastBilled != null) {
                            // plusMonths 自动做月末钳制（1/31 + 1个月 = 2/28），
                            // 避免 day 29/30/31 的订阅在短月被跳过
                            val lastBilledDate = millisToLocalDate(lastBilled)
                            val cycleMonths = when (cycle) {
                                "yearly" -> 12L
                                "quarterly" -> 3L
                                else -> 1L
                            }
                            val nextDue = lastBilledDate.plusMonths(cycleMonths)
                            if (today.isBefore(nextDue)) continue
                        }
                        val price = (obj["price"] as? JsonPrimitive)?.content ?: ""
                        com.palmnote.ui.notification.NotificationHelper.show(
                            applicationContext,
                            com.palmnote.ui.notification.NotificationHelper.CHANNEL_REMINDER,
                            applicationContext.getString(com.palmnote.R.string.notification_subscription_title),
                            applicationContext.getString(com.palmnote.R.string.notification_subscription_message, item.title, price)
                        )
                        // 回写 lastBilledDate，防止同日/同周期重复提醒
                        val newFields = JsonObject(obj + ("lastBilledDate" to JsonPrimitive(today.atStartOfDay(zone).toInstant().toEpochMilli().toString())))
                        itemRepo.updateFieldsData(item.id, newFields.toString())
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private suspend fun tryGenerateWeeklyReport() {
        val today = LocalDate.now()
        if (today.dayOfWeek != DayOfWeek.MONDAY) return
        val weekStart = today.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekEnd = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val existing = reportRepo.getReport("WEEKLY", weekStart)
        if (existing != null) return
        val focusMinutes = try {
            focusRepo.getTodayTotalMinutes(weekStart, weekEnd)
        } catch (_: Exception) { 0 }
        reportRepo.insertReport(LifeReport(type = "WEEKLY", periodStart = weekStart, periodEnd = weekEnd, reportData = """{"focusMinutes":$focusMinutes}"""))
    }
}
