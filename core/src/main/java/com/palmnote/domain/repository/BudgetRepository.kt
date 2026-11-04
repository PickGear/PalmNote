package com.palmnote.domain.repository

import com.palmnote.data.db.entity.Budget
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetProgress
import com.palmnote.domain.model.BudgetScope
import kotlinx.coroutines.flow.Flow

/**
 * 预算模板 + 进度。
 *
 * 预算是「长期有效」的规则（周/月/年 × 全部/账本/分类），不是每月一行；
 * 进度由 [getProgresses] 按当前周期窗口算好。
 */
interface BudgetRepository {
    /** 当前演示口径下的全部预算模板。 */
    fun getBudgets(): Flow<List<Budget>>

    /**
     * 全部预算在**当前周期**的进度，已按「最严重优先」排序（首条即最该提醒的）。
     * 支出按账本 × 分类聚合后换算，三种口径共用同一份聚合结果。
     */
    fun getProgresses(): Flow<List<BudgetProgress>>

    /** 取某维度的模板（同维度最多一条）。 */
    suspend fun getBudget(
        period: BudgetPeriod,
        scope: BudgetScope,
        bookId: Long? = null,
        category: String? = null
    ): Budget?

    /**
     * 新增或更新一条预算：同维度已存在时**原地更新**（保留 id 与来源），否则新建。
     * 返回落库后的 id。
     */
    suspend fun saveBudget(budget: Budget): Long

    suspend fun deleteBudget(id: Long)
}
