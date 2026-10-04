package com.palmnote.data.repository
import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.FieldValueExtractor
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.FieldValueDao
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.FieldValue
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.SubscriptionDueItem
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.event.EventBus
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.util.AppLogger
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.domain.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
@OptIn(ExperimentalCoroutinesApi::class)
class LifeItemRepositoryImpl @Inject constructor(
    private val dao: LifeItemDao,
    private val fieldValueDao: FieldValueDao,
    private val appDatabase: AppDatabase,
    private val templateRepo: LifeTemplateRepository,
    private val preferences: PreferencesManager,
    /** 存钱达标规则的事件源（见 [publishSavingDepositIfGrew]）。 */
    private val eventBus: EventBus
) : LifeItemRepository {
    private val permissiveJson = Json { ignoreUnknownKeys = true }
    override fun getAllItems(): Flow<List<LifeItem>> = dao.getAllItemsIncludingHidden()
    override fun getItemsByTemplate(templateId: Long): Flow<List<LifeItem>> = dao.getVisibleItemsByTemplate(templateId)
    override fun getItemsByTemplateAndStatus(
        templateId: Long,
        status: String
    ): Flow<List<LifeItem>> = dao.getVisibleItemsByTemplateAndStatus(templateId, status)
    override suspend fun getItemById(id: Long): LifeItem? = dao.getItemById(id)
    override fun getItemByIdFlow(id: Long): Flow<LifeItem?> = dao.getItemByIdFlow(id)
    override fun getActiveItemsByTemplate(
        templateId: Long,
        limit: Int
    ): Flow<List<LifeItem>> = preferences.lifeDemoMode.flatMapLatest { demo ->
        dao.getVisibleActiveItemsByTemplate(templateId, demo, LIFE_DEMO_META, limit)
    }
    override fun getItemCountByTemplate(templateId: Long): Flow<Int> = dao.getVisibleItemCountByTemplate(templateId)
    override suspend fun search(query: String): List<LifeItem> = dao.search(query)
    override suspend fun insertItem(item: LifeItem): Long = try {
        val (dueDate, dueTime) = mirrorExecutionColumns(item.templateId, item.fieldsData)
        val resolvedDue = item.dueDate ?: dueDate ?: recordFallbackDueDate(item.templateId, item.createdAt)
        val id = appDatabase.withTransaction {
            val rowId = dao.insertItem(item.copy(dueDate = resolvedDue, dueTime = item.dueTime ?: dueTime))
            // 双写（§7.1）必须与主写入**同事务**：分开写的话，两次提交之间进程被杀
            // 就会留下「主表有行、统计表没有」的不一致快照。
            syncFieldValues(rowId, item.templateId, item.fieldsData)
            rowId
        }
        id
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "insertItem failed", e)
        throw e
    }
    override suspend fun updateItem(item: LifeItem) = try {
        val (dueDate, dueTime) = mirrorExecutionColumns(item.templateId, item.fieldsData)
        val resolvedDue = item.dueDate ?: dueDate ?: recordFallbackDueDate(item.templateId, item.createdAt)
        appDatabase.withTransaction {
            dao.updateItem(
                id = item.id,
                title = item.title,
                fieldsData = item.fieldsData,
                status = item.status,
                note = item.note,
                sortOrder = item.sortOrder,
                isFavorite = item.isFavorite,
                dueDate = resolvedDue,
                dueTime = item.dueTime ?: dueTime,
                recurring = item.recurring,
                recurringEndType = item.recurringEndType,
                recurringEndCount = item.recurringEndCount,
                recurringEndDate = item.recurringEndDate,
                parentId = item.parentId,
                remindAt = item.remindAt,
                meta = item.meta
            )
            syncFieldValues(item.id, item.templateId, item.fieldsData)
        }
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "updateItem failed", e)
        throw e
    }
    override suspend fun updateStatus(id: Long, status: String) = try {
        dao.updateStatus(id, status)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "updateStatus failed", e)
        throw e
    }
    override suspend fun updateFieldsData(id: Long, fieldsData: String) = try {
        val existing = dao.getItemById(id)
        val templateId = existing?.templateId ?: -1L
        val (dueDate, dueTime) = mirrorExecutionColumns(templateId, fieldsData)
        val resolvedDue = dueDate ?: existing?.dueDate
            ?: recordFallbackDueDate(templateId, existing?.createdAt ?: 0L)
        appDatabase.withTransaction {
            dao.updateFieldsDataWithSchedule(id, fieldsData, resolvedDue, dueTime ?: existing?.dueTime)
            syncFieldValues(id, templateId, fieldsData)
        }
        publishSavingDepositIfGrew(id, templateId, existing?.fieldsData, fieldsData)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "updateFieldsData failed", e)
        throw e
    }

    /**
     * 进度**上涨**时发布 `SavingDeposit`（存钱达标规则的唯一事件源）。
     *
     * 为什么在这里：`updateFieldsData` 是"改一个字段值"的**唯一咽喉** ——
     * 详情页的 ± / 点数值就地编辑 / 快捷编辑三条路径都经过它，放在上游任一处都会漏。
     *
     * 为什么只判"上涨"：规则自己会读条目判断是否达标（`targetAmount`/`currentAmount`），
     * 这里只负责"发生了推进"；往下调进度不发布，避免误触发。
     *
     * 事件载荷的金额字段传 0：规则的判定与文案都用条目自身的数据（消费者会重读条目），
     * 而金额键名随模板而异（`targetAmount`/`budget`…），在这里猜只会制造第二套口径。
     */
    private suspend fun publishSavingDepositIfGrew(
        itemId: Long,
        templateId: Long,
        before: String?,
        after: String
    ) {
        if (before == null || templateId <= 0L) return
        val config = runCatching { templateRepo.getTemplateById(templateId)?.fieldsConfig }
            .getOrNull() ?: return
        if (!progressIncreased(config, before, after)) return
        runCatching { eventBus.publish(DomainEvent.SavingDeposit(itemId, amount = 0L, total = 0L)) }
            .onFailure { AppLogger.w("LifeItemRepo", "publish SavingDeposit failed", it) }
    }

    /**
     * 统计信源双写（总纲 §7.1）：删旧插新，与 fieldsData 同一变更生效。
     * fieldsConfig 缺失 / 解析失败时清空该条目统计行并记录，不让统计问题阻断主写入。
     */
    private suspend fun syncFieldValues(itemId: Long, templateId: Long, fieldsData: String) {
        try {
            val config = templateRepo.getTemplateById(templateId)?.fieldsConfig ?: ""
            val rows = FieldValueExtractor.extract(config, fieldsData)
            appDatabase.withTransaction {
                fieldValueDao.deleteByItem(itemId)
                if (rows.isNotEmpty()) {
                    fieldValueDao.insertAll(rows.map { FieldValue(itemId = itemId, fieldKey = it.fieldKey, type = it.type, num = it.num, text = it.text, dateMs = it.dateMs, json = it.json, idx = it.idx) })
                }
            }
        } catch (e: Exception) {
            AppLogger.e("LifeItemRepo", "syncFieldValues failed for item $itemId", e)
        }
    }
    override suspend fun setFavorite(id: Long, favorite: Boolean) = try {
        dao.setFavorite(id, favorite)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "setFavorite failed", e)
        throw e
    }

    override fun getSubscriptionsDueWithin(days: Int): Flow<List<SubscriptionDueItem>> = flow {
        val today = LocalDate.now(zone)
        val tpls = templateRepo.getAllVisibleTemplates().first()
            .filter { it.name.contains(BuiltinTemplates.SUBSCRIPTION_KEYWORD) }
        if (tpls.isEmpty()) {
            emit(emptyList<SubscriptionDueItem>())
            return@flow
        }
        // 一次查完（原来是按模板逐个查的 N+1），并且与其它出口同一套演示互斥口径：
        // 原实现直接调 dao.getActiveItemsByTemplate（**没有**演示过滤），于是演示模式关掉后
        // 示例订阅仍会列在「即将扣费」里，开着时反过来看不到真实订阅。
        // 同时去掉了原先的 LIMIT 500 静默截断。
        val rows = mutableListOf<SubscriptionDueItem>()
        val items = dao.getActiveItemsByTemplateIds(
            tpls.map { it.id },
            preferences.lifeDemoMode.first(),
            LIFE_DEMO_META
        )
        for (item in items) {
            try {
                val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                val billingDay = (obj["billingDay"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: (obj["billing_day"] as? JsonPrimitive)?.content?.toIntOrNull()
                val lastBilled = (obj["lastBilledDate"] as? JsonPrimitive)?.content?.toLongOrNull()
                val cycle = (obj["billingCycle"] as? JsonPrimitive)?.content ?: "monthly"
                if (billingDay != null) {
                    val nextDue = nextDueDate(billingDay, cycle, lastBilled, today)
                    val daysLeft = ChronoUnit.DAYS.between(today, nextDue)
                    if (daysLeft in 0..days.toLong()) {
                        val price = (obj["price"] as? JsonPrimitive)?.content ?: ""
                        rows.add(SubscriptionDueItem(item.id, item.title, price, daysLeft.toInt(), cycle))
                    }
                }
            } catch (_: Exception) { }
        }
        emit(rows.sortedBy { it.daysLeft }.take(3))
    }

    /**
     * 按模板 fieldsConfig 中字段类型镜像:DATE/DATETIME 字段值(毫秒)→ dueDate,
     * TIME 字段值("HH:mm")→ dueTime(分钟)。fieldsData 仍是展示唯一信源,执行列仅作查询索引。
     */
    private suspend fun mirrorExecutionColumns(templateId: Long, fieldsData: String): Pair<Long?, Int?> {
        if (fieldsData.isBlank() || fieldsData == "{}") return null to null
        return try {
            val obj = Json.decodeFromString<JsonObject>(fieldsData)
            val fields = templateFields(templateId)
            val dateKey = fields.firstOrNull { it.type.isDateLike() }?.key
            val timeKey = fields.firstOrNull { it.type == FieldType.TIME }?.key
            // 必须用 parseDateValueOrNull：它同时接受毫秒与 "yyyy-MM-dd"。
            // 这里原本是 content.toLongOrNull()，**只认毫秒**；而记录表单写的正是 ISO 字符串
            // （DatePillRow → LocalDate.toString()），于是 dueDate 恒为 null。再叠加
            // recordFallbackDueDate 的「模板自带 DATE 字段就不兜底」，结果是带日期字段的模板
            // （生日/纪念日/订阅下次扣费/截止日）用表单创建的记录根本不上日历、不进今日、不算逾期。
            val dueDate = dateKey?.let { DateUtils.parseDateValueOrNull((obj[it] as? JsonPrimitive)?.content) }
            val dueTime = timeKey?.let { parseTimeValue((obj[it] as? JsonPrimitive)?.content) }
            dueDate to dueTime
        } catch (_: Exception) {
            null to null
        }
    }

    /**
     * 记录类条目的**兜底日期**：`category = 记录`、且模板内**没有 DATE/DATETIME 字段**时，
     * 日期取 `createdAt` —— 即「记录发生当天」。
     *
     * 为什么需要：打卡 / 心情 / 日记 / 专注 / 身体记录这些模板的 `fieldsConfig` 里没有任何
     * 日期字段，[mirrorExecutionColumns] 自然取不到 `dueDate`，于是条目既不上日历也不进当日
     * 看板（日历空着、点进去也没有）。而记录的本质就是「某天发生过的事」，日期即写入那一刻。
     *
     * **不兜底**的两种情形：
     * - 模板自带 DATE 字段（如订阅的「下次扣费」）：日期由该字段决定，未填即用户没设；
     * - 非记录类（计划 / 时间）：「无日期」是有意义的状态（如无期限待办），不能凭空造。
     *
     * @return 兜底毫秒；不适用或模板缺失则 null。
     */
    private suspend fun recordFallbackDueDate(templateId: Long, createdAt: Long): Long? {
        val tpl = templateRepo.getTemplateById(templateId) ?: return null
        if (tpl.category != BuiltinTemplates.RECORD_CATEGORY) return null
        val hasDateField = runCatching {
            permissiveJson.decodeFromString<List<FieldConfig>>(tpl.fieldsConfig)
                .any { it.type.isDateLike() }
        }.getOrDefault(false)
        if (hasDateField) return null
        return createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
    }

    private suspend fun templateFields(templateId: Long): List<FieldConfig> = try {
        templateRepo.getTemplateById(templateId)?.fieldsConfig
            ?.let { permissiveJson.decodeFromString<List<FieldConfig>>(it) }
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseTimeValue(raw: String?): Int? {
        val asMinutes = raw?.toIntOrNull()?.takeIf { it in 0..1439 }
        val fromHhMm = raw?.split(":")?.takeIf { it.size == 2 }?.let { p ->
            val h = p[0].toIntOrNull()
            val m = p[1].toIntOrNull()
            val valid = h != null && m != null && h in 0..23 && m in 0..59
            if (valid) h * 60 + m else null
        }
        return asMinutes ?: fromHhMm
    }

    private fun FieldType.isDateLike() = this == FieldType.DATE || this == FieldType.DATETIME

    private fun nextDueDate(billingDay: Int, cycle: String, lastBilled: Long?, today: LocalDate): LocalDate {
        val cycleMonths = when (cycle) {
            "yearly" -> 12L
            "quarterly" -> 3L
            else -> 1L
        }
        if (lastBilled != null) {
            // plusMonths 自动做月末钳制（1/31 + 1个月 = 2/28）
            val last = DateUtils.millisToLocalDate(lastBilled)
            var next = last.plusMonths(cycleMonths)
            while (next.isBefore(today)) next = next.plusMonths(cycleMonths)
            return next
        }
        // 无 lastBilledDate:下一个 billingDay 出现在本月或下月
        val billDay = billingDay.coerceAtMost(today.lengthOfMonth())
        var candidate = today.withDayOfMonth(billDay)
        if (candidate.isBefore(today)) candidate = candidate.plusMonths(1)
        return candidate
    }

    private val zone: ZoneId = ZoneId.systemDefault()
    override suspend fun delete(id: Long) = try {
        dao.deleteItemCascade(id)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "softDelete failed", e)
        throw e
    }

    override fun getScheduledBetween(start: Long, end: Long): Flow<List<LifeItem>> = dao.getScheduledBetween(start, end)

    override fun getDistinctDueDatesBetween(start: Long, end: Long): Flow<List<Long>> = dao.getDistinctDueDatesBetween(start, end)

    override fun getTodoComplement(
        todayStart: Long,
        todayEnd: Long,
        todoTemplateId: Long
    ): Flow<List<LifeItem>> = dao.getTodoComplement(todayStart, todayEnd, todoTemplateId)

    override fun getSubtasks(parentId: Long): Flow<List<LifeItem>> = dao.getSubtasks(parentId)

    override fun getAnniversaryLikeItems(
        includeDemo: Boolean,
        demoMeta: String
    ): Flow<List<LifeItem>> = dao.getAnniversaryLikeItems(includeDemo, demoMeta)

    override fun searchItems(query: String): Flow<List<LifeItem>> = dao.searchItems(query)
    override fun getDayCountsBetween(
        start: Long,
        end: Long
    ): Flow<List<com.palmnote.data.db.dao.LifeDayCount>> = dao.getDayCountsBetween(start, end)
    override fun getDayCategoryCountsBetween(
        start: Long,
        end: Long
    ): Flow<List<com.palmnote.data.db.dao.LifeDayCategoryCount>> = dao.getDayCategoryCountsBetween(start, end)
}

/**
 * 进度是否**上涨**：只看模板里标了 `showAsProgress` 的那个字段
 * （存钱是 `currentAmount`、购物是 `spent`、阅读是 `currentPage`…）。
 *
 * 抽成纯函数是因为它是**存钱达标提醒的触发闸门**：判错就会"该响不响"或"乱响"。
 * 配置解析不出、字段缺失、非数值一律 false（宁可不提醒，也不误提醒）。
 */
internal fun progressIncreased(fieldsConfig: String, before: String, after: String): Boolean {
    val configs = runCatching { Json.decodeFromString<List<FieldConfig>>(fieldsConfig) }.getOrNull()
    val key = configs?.firstOrNull { it.showAsProgress && !it.disabled }?.key
    val beforeValue = key?.let { numericFieldOrNull(before, it) }
    val afterValue = key?.let { numericFieldOrNull(after, it) }
    return beforeValue != null && afterValue != null && afterValue > beforeValue
}

private fun numericFieldOrNull(fieldsData: String, key: String): Double? =
    runCatching {
        (Json.parseToJsonElement(fieldsData).jsonObject[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
    }.getOrNull()
