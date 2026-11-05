package com.palmnote.data.repository

import androidx.room.withTransaction
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.AccountBookDao
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.BudgetDao
import com.palmnote.data.db.entity.Budget
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetProgress
import com.palmnote.domain.model.BudgetScope
import com.palmnote.domain.model.DemoOrigin
import com.palmnote.domain.repository.BudgetRepository
import com.palmnote.domain.util.BudgetCalculator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * 预算的演示口径与 bills / assets / wallets 同构：按 `demoOrigin` 分流。
 * 真实模式只看 `NONE`；演示模式只看 `SEED` / `USER`。
 *
 * 支出侧同一口径：演示模式只统计**示例账本**的账单（真实账本的账单仍在库里，
 * 不按账本收口就会把用户真实支出算进示例预算），真实模式统计全部账本。
 *
 * 写入在仓库层归一化来源（与 BillRepositoryImpl / AssetRepositoryImpl 的拦截同位）：
 * 真实模式一律 `NONE`；演示模式下新预算标 `USER`，已有的 `SEED` 原样保留。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val billDao: BillDao,
    private val accountBookDao: AccountBookDao,
    private val appDatabase: AppDatabase,
    private val preferencesManager: PreferencesManager
) : BudgetRepository {

    override fun getBudgets(): Flow<List<Budget>> =
        preferencesManager.lifeDemoMode.flatMapLatest { demo ->
            budgetDao.getAllBudgets().map { list ->
                list.filter { (it.demoOrigin != DemoOrigin.NONE) == demo }
            }
        }

    override fun getProgresses(): Flow<List<BudgetProgress>> =
        // 跨天 tick 合在源头：过了午夜（周/月/年窗口都可能整体换一期），
        // 重订阅内层流才能拿到新窗口。只在日期真正变化时发，不做定时轮询。
        combine(preferencesManager.lifeDemoMode, dayTickFlow()) { demo, _ -> demo }
            .flatMapLatest { demo -> progressFlow(demo) }

    private fun progressFlow(demo: Boolean): Flow<List<BudgetProgress>> = flow {
        val demoBook = if (demo) accountBookDao.demoBookId() else null
        // 演示模式却拿不到示例账本 → 给空，绝不回落到真实支出
        if (demo && demoBook == null) {
            emit(emptyList())
            return@flow
        }
        val now = System.currentTimeMillis()
        val week = BudgetCalculator.windowOf(BudgetPeriod.WEEK, now)
        val month = BudgetCalculator.windowOf(BudgetPeriod.MONTH, now)
        val year = BudgetCalculator.windowOf(BudgetPeriod.YEAR, now)
        emitAll(
            combine(
                budgetDao.getAllBudgets(),
                billDao.getBudgetSpendRows(week.first, week.second, demoBook),
                billDao.getBudgetSpendRows(month.first, month.second, demoBook),
                billDao.getBudgetSpendRows(year.first, year.second, demoBook)
            ) { budgets, weekRows, monthRows, yearRows ->
                BudgetCalculator.compute(
                    budgets.filter { (it.demoOrigin != DemoOrigin.NONE) == demo },
                    mapOf(
                        BudgetPeriod.WEEK to weekRows,
                        BudgetPeriod.MONTH to monthRows,
                        BudgetPeriod.YEAR to yearRows
                    ),
                    now
                )
            }
        )
    }

    override suspend fun getBudget(
        period: BudgetPeriod,
        scope: BudgetScope,
        bookId: Long?,
        category: String?
    ): Budget? {
        val demo = preferencesManager.lifeDemoMode.first()
        return budgetDao.findBudget(period.name, scope.name, bookId, category, demo)
    }

    override suspend fun saveBudget(budget: Budget): Long {
        // 演示态在事务外取：事务块只包「查 → 更新/插入」这一段，缩短持锁时间，
        // 也避免在 Room 事务里再挂起等待 DataStore。
        val demo = preferencesManager.lifeDemoMode.first()
        val origin = when {
            !demo -> DemoOrigin.NONE
            budget.demoOrigin == DemoOrigin.SEED -> DemoOrigin.SEED
            else -> DemoOrigin.USER
        }
        val now = System.currentTimeMillis()
        // 先查后插必须整体在事务里：并发保存同一维度时，两个「查空 → 各自插入」
        // 会留下两行同维度预算，且表上没有唯一索引兜底，重复行永久存在。
        return appDatabase.withTransaction {
            val existing = budgetDao.findBudget(
                budget.period, budget.scope, budget.bookId, budget.category, demo
            )
            if (existing != null) {
                // 原地更新：id / 来源 / 创建时间都保留，只覆盖额度与提醒设置
                budgetDao.updateBudget(
                    budget.copy(
                        id = existing.id,
                        demoOrigin = existing.demoOrigin,
                        createdAt = existing.createdAt,
                        updatedAt = now
                    )
                )
                existing.id
            } else {
                budgetDao.insertBudget(
                    budget.copy(id = 0, demoOrigin = origin, createdAt = now, updatedAt = now)
                )
            }
        }
    }

    override suspend fun deleteBudget(id: Long) = budgetDao.deleteBudgetById(id)

    /**
     * 跨午夜信号：只在日期真正变化时发射。
     *
     * 与首页看板同一口径：单次等待封顶 1 分钟，深睡/冻结导致 delay 被推迟时
     * 醒来最多一分钟自愈——否则过夜后周/月窗口会一直停在昨天。
     */
    private fun dayTickFlow(): Flow<Unit> = flow {
        var last = LocalDate.now()
        emit(Unit)
        while (currentCoroutineContext().isActive) {
            val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay()
            val wait = Duration.between(LocalDateTime.now(), nextMidnight).toMillis()
            delay(wait.coerceIn(1_000L, 60_000L))
            val now = LocalDate.now(ZoneId.systemDefault())
            if (now != last) {
                last = now
                emit(Unit)
            }
        }
    }
}
