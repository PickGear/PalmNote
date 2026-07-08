package com.palmnote.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeReport
import com.palmnote.domain.repository.*
import com.palmnote.data.datastore.PreferencesManager
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
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val dailyEnabled = preferencesManager.dailyReminderEnabled.first()
            if (dailyEnabled) {
                checkDailyReminder()
            }
            checkBillReminder()
            checkCountUpMilestones()
            checkCountdownExpiry()
            checkBirthdayReminders()
            checkAnniversaryReminders()
            checkSubscriptionBilling()
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
        val billReminderEnabled = preferencesManager.billReminderEnabled.first()
        if (!billReminderEnabled) return
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val bills = billRepo.getBillsByDate(todayStart).first()
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
            itemRepo.getActiveItemsByTemplate(tpl.id, Int.MAX_VALUE).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val startDateStr = (obj["start_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                        ?: (obj["startDate"] as? JsonPrimitive)?.content?.toLongOrNull()
                    if (startDateStr != null) {
                        val start = LocalDate.ofEpochDay(startDateStr / 86400000L)
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
        val advanceDays = preferencesManager.birthdayReminderAdvanceDays.first()
        val tpls = templateRepo.getAllTemplates().first().filter { it.name.contains("\u5012\u8BA1\u65F6") }
        tpls.forEach { tpl ->
            itemRepo.getActiveItemsByTemplate(tpl.id, Int.MAX_VALUE).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val dateStr = (obj["targetDate"] as? JsonPrimitive)?.content?.toLongOrNull()
                        ?: (obj["target_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                    if (dateStr != null) {
                        val target = LocalDate.ofEpochDay(dateStr / 86400000L)
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
        val advanceDays = preferencesManager.birthdayReminderAdvanceDays.first()
        val tpls = templateRepo.getAllTemplates().first().filter { it.name.contains("\u751F\u65E5") }
        tpls.forEach { tpl ->
            itemRepo.getActiveItemsByTemplate(tpl.id, Int.MAX_VALUE).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val dateStr = (obj["date"] as? JsonPrimitive)?.content?.toLongOrNull()
                        ?: (obj["birthday_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                    if (dateStr != null) {
                        val birthDate = LocalDate.ofEpochDay(dateStr / 86400000L)
                        val nextBirthday = birthDate.withYear(today.year)
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
        val advanceDays = preferencesManager.anniversaryReminderAdvanceDays.first()
        val tpls = templateRepo.getAllTemplates().first().filter { it.name.contains("\u7EAA\u5FF5\u65E5") }
        tpls.forEach { tpl ->
            itemRepo.getActiveItemsByTemplate(tpl.id, Int.MAX_VALUE).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val dateStr = (obj["date"] as? JsonPrimitive)?.content?.toLongOrNull()
                    if (dateStr != null) {
                        val anniDate = LocalDate.ofEpochDay(dateStr / 86400000L)
                        val nextAnni = anniDate.withYear(today.year)
                        val diff = ChronoUnit.DAYS.between(today, if (nextAnni.isAfter(today) || nextAnni == today) nextAnni else nextAnni.plusYears(1))
                        if (diff in 0..advanceDays.toLong()) {
                            val years = ChronoUnit.YEARS.between(anniDate, today)
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
        tpls.forEach { tpl ->
            itemRepo.getActiveItemsByTemplate(tpl.id, Int.MAX_VALUE).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    val billingDay = (obj["billingDay"] as? JsonPrimitive)?.content?.toIntOrNull()
                        ?: (obj["billing_day"] as? JsonPrimitive)?.content?.toIntOrNull()
                    val lastBilled = (obj["lastBilledDate"] as? JsonPrimitive)?.content?.toLongOrNull()
                    val cycle = (obj["billingCycle"] as? JsonPrimitive)?.content ?: "monthly"
                        if (billingDay != null && today.dayOfMonth == billingDay) {
                        if (lastBilled != null) {
                            val lastBilledDate = LocalDate.ofEpochDay(lastBilled / 86400000L)
                            val monthsBetween = ChronoUnit.MONTHS.between(lastBilledDate, today)
                            when (cycle) {
                                "monthly" -> if (monthsBetween < 1) return@forEach
                                "yearly" -> if (monthsBetween < 12) return@forEach
                                "quarterly" -> if (monthsBetween < 3) return@forEach
                            }
                        }
                        val price = (obj["price"] as? JsonPrimitive)?.content ?: ""
                        com.palmnote.ui.notification.NotificationHelper.show(
                            applicationContext,
                            com.palmnote.ui.notification.NotificationHelper.CHANNEL_REMINDER,
                            applicationContext.getString(com.palmnote.R.string.notification_subscription_title),
                            applicationContext.getString(com.palmnote.R.string.notification_subscription_message, item.title, price)
                        )
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private suspend fun tryGenerateWeeklyReport() {
        val today = LocalDate.now()
        if (today.dayOfWeek != DayOfWeek.MONDAY) return
        val weekStart = today.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val weekEnd = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val existing = reportRepo.getReport("WEEKLY", weekStart)
        if (existing != null) return
        val focusMinutes = try {
            focusRepo.getTodayTotalMinutes(weekStart, weekEnd)
        } catch (_: Exception) { 0 }
        reportRepo.insertReport(LifeReport(type = "WEEKLY", periodStart = weekStart, periodEnd = weekEnd, reportData = """{"focusMinutes":$focusMinutes}"""))
    }
}
