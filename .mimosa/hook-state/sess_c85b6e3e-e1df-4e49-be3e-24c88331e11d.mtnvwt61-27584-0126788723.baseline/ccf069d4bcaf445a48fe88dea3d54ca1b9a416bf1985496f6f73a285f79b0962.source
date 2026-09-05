package com.palmnote.data.repository
import javax.inject.Inject

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeItemPagingSource
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.SubscriptionDueItem
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.util.AppLogger
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.domain.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
class LifeItemRepositoryImpl @Inject constructor(
    private val dao: LifeItemDao,
    private val templateRepo: LifeTemplateRepository
) : LifeItemRepository {
    private val permissiveJson = Json { ignoreUnknownKeys = true }
    override fun getAllItems(): Flow<List<LifeItem>> = dao.getAllItems()
    override fun getItemsByTemplate(templateId: Long): Flow<List<LifeItem>> = dao.getItemsByTemplate(templateId)
    override fun getItemsByTemplateAndStatus(templateId: Long, status: String): Flow<List<LifeItem>> =
        dao.getItemsByTemplateAndStatus(templateId, status)
    override suspend fun getItemById(id: Long): LifeItem? = dao.getItemById(id)
    override fun getItemByIdFlow(id: Long): Flow<LifeItem?> = dao.getItemByIdFlow(id)
    override fun getActiveItemsByTemplate(templateId: Long, limit: Int): Flow<List<LifeItem>> =
        dao.getActiveItemsByTemplate(templateId, limit)
    override fun getItemCountByTemplate(templateId: Long): Flow<Int> = dao.getItemCountByTemplate(templateId)
    override fun getTotalItemCount(): Flow<Int> = dao.getTotalItemCount()
    override fun getPagedItemsByTemplate(templateId: Long): Flow<PagingData<LifeItem>> =
        Pager(PagingConfig(pageSize = 20)) { LifeItemPagingSource(dao, templateId) }.flow
    override fun getPagedAllItems(): Flow<PagingData<LifeItem>> =
        Pager(PagingConfig(pageSize = 20)) { LifeItemPagingSource(dao) }.flow
    override suspend fun search(query: String): List<LifeItem> = dao.search(query)
    override suspend fun insertItem(item: LifeItem): Long = try {
        val (dueDate, dueTime) = mirrorExecutionColumns(item.templateId, item.fieldsData)
        dao.insertItem(item.copy(dueDate = item.dueDate ?: dueDate, dueTime = item.dueTime ?: dueTime))
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "insertItem failed", e)
        throw e
    }
    override suspend fun updateItem(item: LifeItem) = try {
        val (dueDate, dueTime) = mirrorExecutionColumns(item.templateId, item.fieldsData)
        dao.updateItem(
            id = item.id,
            title = item.title,
            fieldsData = item.fieldsData,
            status = item.status,
            note = item.note,
            sortOrder = item.sortOrder,
            isFavorite = item.isFavorite,
            dueDate = item.dueDate ?: dueDate,
            dueTime = item.dueTime ?: dueTime,
            recurring = item.recurring,
            recurringEndType = item.recurringEndType,
            recurringEndCount = item.recurringEndCount,
            recurringEndDate = item.recurringEndDate,
            parentId = item.parentId,
            remindAt = item.remindAt,
            meta = item.meta
        )
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
        val (dueDate, dueTime) = mirrorExecutionColumns(existing?.templateId ?: -1L, fieldsData)
        dao.updateFieldsDataWithSchedule(id, fieldsData, dueDate ?: existing?.dueDate, dueTime ?: existing?.dueTime)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "updateFieldsData failed", e)
        throw e
    }
    override suspend fun setFavorite(id: Long, favorite: Boolean) = try {
        dao.setFavorite(id, favorite)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "setFavorite failed", e)
        throw e
    }

    override fun getSubscriptionsDueWithin(days: Int): Flow<List<SubscriptionDueItem>> = flow {
        val today = LocalDate.now(zone)
        val tpls = templateRepo.getAllTemplates().first().filter { it.name.contains(BuiltinTemplates.SUBSCRIPTION_KEYWORD) }
        val rows = mutableListOf<SubscriptionDueItem>()
        for (tpl in tpls) {
            dao.getActiveItemsByTemplate(tpl.id, 500).first().forEach { item ->
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
            val dueDate = dateKey?.let { (obj[it] as? JsonPrimitive)?.content?.toLongOrNull() }
            val dueTime = timeKey?.let { parseTimeValue((obj[it] as? JsonPrimitive)?.content) }
            dueDate to dueTime
        } catch (_: Exception) {
            null to null
        }
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
        dao.deleteItem(id)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "softDelete failed", e)
        throw e
    }

    override fun getScheduledBetween(start: Long, end: Long): Flow<List<LifeItem>> =
        dao.getScheduledBetween(start, end)

    override fun getDistinctDueDatesBetween(start: Long, end: Long): Flow<List<Long>> =
        dao.getDistinctDueDatesBetween(start, end)

    override fun getTodoComplement(todayStart: Long, todayEnd: Long, todoTemplateId: Long): Flow<List<LifeItem>> =
        dao.getTodoComplement(todayStart, todayEnd, todoTemplateId)

    override fun getSubtasks(parentId: Long): Flow<List<LifeItem>> = dao.getSubtasks(parentId)

    override fun getOverdue(now: Long): Flow<List<LifeItem>> = dao.getOverdue(now)
    override fun searchItems(query: String): Flow<List<LifeItem>> = dao.searchItems(query)
}
