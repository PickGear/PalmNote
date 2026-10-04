package com.palmnote.data.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.palmnote.app.R
import com.palmnote.domain.util.AppLogger
import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import com.palmnote.ui.life.parseLunarFlag
import com.palmnote.data.db.entity.LifeReport
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.model.ReminderSpec
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.FocusRecordRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeReportRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.util.BuiltinTemplates
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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

    /**
     * 通知的深链入口：复用**桌面组件那条既有通路**（`WIDGET_TAB` / `WIDGET_ITEM_ID`
     * → `MainActivity.handleWidgetIntent` 写入 `pendingLifeDetailItemId`）。
     * 于是点提醒直接跳到那条记录的详情，而不是只把应用拉到前台。
     */
    private fun lifeDetailPendingIntent(itemId: Long): PendingIntent =
        PendingIntent.getActivity(
            applicationContext,
            (7_400_100 + itemId).toInt(),
            Intent(applicationContext, com.palmnote.MainActivity::class.java)
                .putExtra("WIDGET_TAB", "life")
                .putExtra("WIDGET_ITEM_ID", itemId.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * 倒计时类提醒的公共尾巴：**深链到该记录** + **「标记完成」动作**。
     *
     * 动作复用桌面组件的 `TodoToggleReceiver`（它已经会把 ACTIVE↔COMPLETED 翻转并刷新小组件），
     * 所以用户不必先打开应用 —— 这是 todo / 提醒类 app 的标配（TickTick、Apple 提醒都是这样）。
     */
    private fun countdownExtras(
        itemId: Long
    ): Pair<PendingIntent, List<com.palmnote.ui.notification.NotificationHelper.Action>> =
        lifeDetailPendingIntent(itemId) to listOf(
            com.palmnote.ui.notification.NotificationHelper.Action(
                applicationContext.getString(R.string.notification_action_complete),
                com.palmnote.ui.widget.TodoToggleReceiver.togglePendingIntent(applicationContext, itemId)
            )
        )

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
        tryGenerateMonthlyReport()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun checkDailyReminder() {
        com.palmnote.ui.notification.NotificationHelper.show(
            applicationContext,
            com.palmnote.ui.notification.NotificationHelper.CHANNEL_CHECKIN,
            applicationContext.getString(R.string.notification_daily_title),
            applicationContext.getString(R.string.notification_daily_message)
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
                applicationContext.getString(R.string.notification_bill_title),
                applicationContext.getString(R.string.notification_bill_message)
            )
        }
    }

    private suspend fun checkCountUpMilestones() {
        val today = LocalDate.now()
        val milestoneDays = listOf(100L, 200L, 365L, 500L, 750L, 1000L)
        reminderTargets(ReminderSpec.Kind.MILESTONE).forEach { target ->
            itemRepo.getActiveItemsByTemplate(target.templateId, 200).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    if (!obj.recordReminderOn()) return@forEach
                    val startMs = obj.dateMs(target.spec, "start_date", "startDate") ?: return@forEach
                    val start = millisToLocalDate(startMs)
                    val days = ChronoUnit.DAYS.between(start, today)
                    if (days in milestoneDays) {
                        com.palmnote.ui.notification.NotificationHelper.show(
                            applicationContext,
                            com.palmnote.ui.notification.NotificationHelper.CHANNEL_LIFE,
                            applicationContext.getString(R.string.notification_milestone_title),
                            applicationContext.resources.getQuantityString(
                                R.plurals.notification_milestone_message, days.toInt(), item.title, days
                            )
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e("LifeDailyCheck", "check failed for " + item.title, e)
                }
            }
        }
    }

    private suspend fun checkCountdownExpiry() {
        val today = LocalDate.now()
        val advanceDays = pm.birthdayReminderAdvanceDays.first()
        reminderTargets(ReminderSpec.Kind.COUNTDOWN).forEach { target ->
            itemRepo.getActiveItemsByTemplate(target.templateId, 200).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    if (!obj.recordReminderOn()) return@forEach
                    val targetMs = obj.dateMs(target.spec, "targetDate", "target_date") ?: return@forEach
                    val date = millisToLocalDate(targetMs)
                    val daysLeft = ChronoUnit.DAYS.between(today, date)
                    when {
                        daysLeft == 0L -> {
                            val (contentIntent, actions) = countdownExtras(item.id)
                            com.palmnote.ui.notification.NotificationHelper.show(
                                applicationContext,
                                com.palmnote.ui.notification.NotificationHelper.CHANNEL_REMINDER,
                                applicationContext.getString(R.string.notification_countdown_today_title),
                                applicationContext.getString(R.string.notification_countdown_today_message, item.title),
                                contentIntent = contentIntent,
                                actions = actions
                            )
                        }
                        daysLeft in 1..advanceDays.toLong() -> {
                            val (contentIntent, actions) = countdownExtras(item.id)
                            com.palmnote.ui.notification.NotificationHelper.show(
                                applicationContext,
                                com.palmnote.ui.notification.NotificationHelper.CHANNEL_REMINDER,
                                applicationContext.getString(R.string.notification_countdown_soon_title),
                                applicationContext.resources.getQuantityString(
                                    R.plurals.notification_countdown_soon_message,
                                    daysLeft.toInt(),
                                    item.title,
                                    daysLeft
                                ),
                                contentIntent = contentIntent,
                                actions = actions
                            )
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("LifeDailyCheck", "check failed for " + item.title, e)
                }
            }
        }
    }

    private suspend fun checkBirthdayReminders() {
        val today = LocalDate.now()
        val advanceDays = pm.birthdayReminderAdvanceDays.first()
        reminderTargets(ReminderSpec.Kind.BIRTHDAY).forEach { target ->
            itemRepo.getActiveItemsByTemplate(target.templateId, 200).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    if (!obj.recordReminderOn()) return@forEach
                    val dateMsValue = obj.dateMs(target.spec, "date", "birthday_date") ?: return@forEach
                    val birthDate = millisToLocalDate(dateMsValue)
                    // 农历生日按农历月-日反算今年的公历日（与详情页的农历锚点展示同口径）；
                    // 反算失败（如当年无对应闰月）回退公历锚点，别静默丢提醒
                    val isLunar = parseLunarFlag((obj["lunar"] as? JsonPrimitive)?.content)
                    val nextBirthday = if (isLunar) {
                        nextLunarBirthday(birthDate, today) ?: toThisYear(birthDate, today)
                    } else {
                        toThisYear(birthDate, today)
                    }
                    val diff = ChronoUnit.DAYS.between(today, if (nextBirthday.isAfter(today) || nextBirthday == today) nextBirthday else nextBirthday.plusYears(1))
                    if (diff in 0..advanceDays.toLong()) {
                        com.palmnote.ui.notification.NotificationHelper.show(
                            applicationContext,
                            com.palmnote.ui.notification.NotificationHelper.CHANNEL_REMINDER,
                            applicationContext.getString(R.string.notification_birthday_title),
                            applicationContext.getString(R.string.notification_birthday_message, item.title)
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e("LifeDailyCheck", "check failed for " + item.title, e)
                }
            }
        }
    }

    private suspend fun checkAnniversaryReminders() {
        val today = LocalDate.now()
        val advanceDays = pm.anniversaryReminderAdvanceDays.first()
        reminderTargets(ReminderSpec.Kind.ANNIVERSARY).forEach { target ->
            itemRepo.getActiveItemsByTemplate(target.templateId, 200).first().forEach { item ->
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    if (!obj.recordReminderOn()) return@forEach
                    val dateMsValue = obj.dateMs(target.spec, "date") ?: return@forEach
                    val anniDate = millisToLocalDate(dateMsValue)
                    val nextAnni = toThisYear(anniDate, today)
                    val diff = ChronoUnit.DAYS.between(today, if (nextAnni.isAfter(today) || nextAnni == today) nextAnni else nextAnni.plusYears(1))
                    if (diff in 0..advanceDays.toLong()) {
                        val years = ChronoUnit.YEARS.between(anniDate, today).coerceAtLeast(0)
                        com.palmnote.ui.notification.NotificationHelper.show(
                            applicationContext,
                            com.palmnote.ui.notification.NotificationHelper.CHANNEL_REMINDER,
                            applicationContext.getString(R.string.notification_anniversary_title),
                            applicationContext.getString(R.string.notification_anniversary_message, item.title, years)
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e("LifeDailyCheck", "check failed for " + item.title, e)
                }
            }
        }
    }

    private suspend fun checkSubscriptionBilling() {
        val today = LocalDate.now()
        // SUBSCRIPTION 不看日期字段：扣费日是「几号」数字键 + 周期文本键（无配置键可覆盖，走 kind 固定键）
        for (target in reminderTargets(ReminderSpec.Kind.SUBSCRIPTION)) {
            for (item in itemRepo.getActiveItemsByTemplate(target.templateId, 200).first()) {
                try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    if (!obj.recordReminderOn()) continue
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
                            applicationContext.getString(R.string.notification_subscription_title),
                            applicationContext.getString(R.string.notification_subscription_message, item.title, price)
                        )
                        // 回写 lastBilledDate，防止同日/同周期重复提醒
                        val newFields = JsonObject(obj + ("lastBilledDate" to JsonPrimitive(today.atStartOfDay(zone).toInstant().toEpochMilli().toString())))
                        itemRepo.updateFieldsData(item.id, newFields.toString())
                    }
                } catch (e: Exception) {
                    AppLogger.e("LifeDailyCheck", "check failed for " + item.title, e)
                }
            }
        }
    }

    // ---- 提醒目标的解析（§提醒显式化）：配置优先，图标兜底 ----

    /** 一条提醒目标：模板 id + 显式提醒配置。 */
    private data class ReminderTarget(val templateId: Long, val spec: ReminderSpec)

    /**
     * 取某类型的提醒目标：模板 [ReminderSpec] 配置驱动；
     * 没配置的按图标兜底推断（迁移回填前的存量安装，行为与显式化之前一致）。
     */
    private suspend fun reminderTargets(kind: ReminderSpec.Kind): List<ReminderTarget> =
        templateRepo.getAllVisibleTemplates().first().mapNotNull { tpl ->
            val spec = ReminderSpec.fromJson(tpl.reminderConfig) ?: legacySpecOf(tpl)
            if (spec?.kind == kind && spec.enabled) ReminderTarget(tpl.id, spec) else null
        }

    /** 图标 → 提醒配置（存量安装的兜底识别路径）。 */
    private fun legacySpecOf(tpl: LifeTemplate): ReminderSpec? = when (tpl.icon) {
        ICON_COUNT_UP -> ReminderSpec(ReminderSpec.Kind.MILESTONE, dateKey = "start_date")
        ICON_COUNTDOWN -> ReminderSpec(ReminderSpec.Kind.COUNTDOWN, dateKey = "targetDate")
        ICON_BIRTHDAY -> ReminderSpec(ReminderSpec.Kind.BIRTHDAY, dateKey = "date")
        ICON_ANNIVERSARY -> ReminderSpec(ReminderSpec.Kind.ANNIVERSARY, dateKey = "date")
        ICON_SUBSCRIPTION -> ReminderSpec(ReminderSpec.Kind.SUBSCRIPTION)
        else -> if (tpl.name.contains(BuiltinTemplates.SUBSCRIPTION_KEYWORD)) {
            ReminderSpec(ReminderSpec.Kind.SUBSCRIPTION)
        } else {
            null
        }
    }

    /** 记录级提醒开关：条目 fieldsData 的 `reminder` 布尔字段，缺省视为开启（显式 false 才跳过）。 */
    private fun JsonObject.recordReminderOn(): Boolean =
        (this["reminder"] as? JsonPrimitive)?.booleanOrNull ?: true

    /** 取日期毫秒：先用配置键，再回退 kind 的历史键（老数据键名）。 */
    private fun JsonObject.dateMs(spec: ReminderSpec, vararg legacyKeys: String): Long? =
        (listOfNotNull(spec.dateKey) + legacyKeys).firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
        }

    private suspend fun tryGenerateMonthlyReport() {
        val today = LocalDate.now()
        if (today.dayOfMonth != 1) return
        val zone = zone
        val monthStart = today.minusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val existing = reportRepo.getReport("MONTHLY", monthStart)
        if (existing != null) return
        val focusMinutes = try {
            focusRepo.getTodayTotalMinutes(monthStart, monthEnd)
        } catch (_: Exception) { 0 }
        reportRepo.insertReport(LifeReport(type = "MONTHLY", periodStart = monthStart, periodEnd = monthEnd, reportData = """{"focusMinutes":$focusMinutes}"""))
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

    companion object {
        /** 唯一任务名：调度（PalmNoteApp）与恢复前取消（BackupViewModel）共用 */
        const val UNIQUE_WORK_NAME = "life_daily_check"

        // 图标是全模块的身份键；提醒现已由模板 reminderConfig 显式驱动，
        // 这些常量只作为「存量安装未回填配置」时的兜底识别键（legacySpecOf）。
        const val ICON_COUNT_UP = "trending_up"
        const val ICON_COUNTDOWN = "timer_off"
        const val ICON_BIRTHDAY = "cake"
        const val ICON_ANNIVERSARY = "celebration"
        const val ICON_SUBSCRIPTION = "subscriptions"
    }
}

/**
 * 农历生日 → 今年的下一次公历日期（含今天）：按生日的农历月-日反算今年，
 * 今年已过则取明年；当年无对应月份（如闰月）返回 null，调用方回退公历锚点。
 * 顶层 internal 仅为可测试。
 */
internal fun nextLunarBirthday(solarBirth: LocalDate, today: LocalDate): LocalDate? {
    val thisYear = lunarToSolar(solarBirth, today.year) ?: return null
    return if (!thisYear.isBefore(today)) thisYear else lunarToSolar(solarBirth, today.year + 1)
}

/** 公历生日的农历月-日 → [year] 年的公历日期；换算失败返回 null。 */
internal fun lunarToSolar(solarBirth: LocalDate, year: Int): LocalDate? = runCatching {
    // 用 fromYmd 而非 fromDate：锚定的是「本地日历日」本身，不吃 JVM/设备时区
    val anchor = Solar.fromYmd(solarBirth.year, solarBirth.monthValue, solarBirth.dayOfMonth).lunar
    // lunar-java 约定：闰月传负数月份
    // 闰月生日按通行约定：当年有此闰月按闰月过，没有则按对应平月过
    val solar = runCatching { Lunar.fromYmd(year, anchor.month, anchor.day).solar }
        .getOrNull()
        ?: if (anchor.month < 0) Lunar.fromYmd(year, -anchor.month, anchor.day).solar else null
    solar?.let { LocalDate.of(it.year, it.month, it.day) }
}.getOrNull()

