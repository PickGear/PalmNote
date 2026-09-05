package com.palmnote.domain.repository

import androidx.paging.PagingData
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.model.SubscriptionDueItem
import kotlinx.coroutines.flow.Flow

interface LifeItemRepository {
    fun getAllItems(): Flow<List<LifeItem>>
    fun getItemsByTemplate(templateId: Long): Flow<List<LifeItem>>
    fun getItemsByTemplateAndStatus(templateId: Long, status: String): Flow<List<LifeItem>>
    suspend fun getItemById(id: Long): LifeItem?
    fun getItemByIdFlow(id: Long): Flow<LifeItem?>
    fun getActiveItemsByTemplate(templateId: Long, limit: Int): Flow<List<LifeItem>>
    fun getItemCountByTemplate(templateId: Long): Flow<Int>
    fun getTotalItemCount(): Flow<Int>
    fun getPagedItemsByTemplate(templateId: Long): Flow<PagingData<LifeItem>>
    fun getPagedAllItems(): Flow<PagingData<LifeItem>>
    suspend fun search(query: String): List<LifeItem>
    suspend fun insertItem(item: LifeItem): Long
    suspend fun updateItem(item: LifeItem)
    suspend fun updateStatus(id: Long, status: String)
    suspend fun updateFieldsData(id: Long, fieldsData: String)
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun delete(id: Long)

    /**
     * 订阅提醒:近 [days] 天内到期的 ACTIVE 订阅,按到期日升序。
     * 复用 LifeDailyCheckWorker 的到期算法(billingDay + cycle + lastBilledDate,短月钳制),
     * 只读计算,不回写 lastBilledDate。
     */
    fun getSubscriptionsDueWithin(days: Int): Flow<List<SubscriptionDueItem>>

    /** 今日看板聚合：dueDate 落在 [start, end) 内、未归档的条目。 */
    fun getScheduledBetween(start: Long, end: Long): Flow<List<LifeItem>>

    /** 周历标记点：范围内 distinct 的 dueDate（毫秒）。 */
    fun getDistinctDueDatesBetween(start: Long, end: Long): Flow<List<Long>>

    /** 待办卡补集：待办模板、非今日（逾期/未来/无日期）、未完成、非子任务。 */
    fun getTodoComplement(todayStart: Long, todayEnd: Long, todoTemplateId: Long): Flow<List<LifeItem>>

    /** 计划页子任务。 */
    fun getSubtasks(parentId: Long): Flow<List<LifeItem>>

    /** 逾期反馈。 */
    fun getOverdue(now: Long): Flow<List<LifeItem>>

    /** 全量搜索：标题 / 备注 / 字段值全文。 */
    fun searchItems(query: String): Flow<List<LifeItem>>
}
