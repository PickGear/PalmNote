package com.palmnote.data.repository
import javax.inject.Inject

import com.palmnote.data.db.dao.BudgetDao
import com.palmnote.data.db.entity.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.palmnote.domain.model.DemoOrigin
import com.palmnote.domain.repository.BudgetRepository
/**
 * 预算的演示口径与 bills / assets / wallets 同构：按 `demoOrigin` 分流。
 * 预算没有账本维度、也没有 `isDemo` 位，v20 起靠 `demoOrigin` 一列承担全部隔离：
 * 演示模式只看 `SEED` / `USER`（播种的示例预算 + 演示期自建），真实模式只看 `NONE`。
 *
 * 写入在仓库层归一化来源（与 BillRepositoryImpl / AssetRepositoryImpl 的拦截同位）：
 * 真实模式一律 `NONE`；演示模式下新预算标 `USER`，已有的 `SEED` 原样保留——
 * 改动示例预算仍随关演示一起清掉，不会把示例预算「洗」成用户数据。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val preferencesManager: com.palmnote.data.datastore.PreferencesManager
) : BudgetRepository {
    override fun getAllBudgets(): Flow<List<Budget>> =
        preferencesManager.lifeDemoMode.flatMapLatest { demo ->
            budgetDao.getAllBudgets().map { list ->
                list.filter { (it.demoOrigin != DemoOrigin.NONE) == demo }
            }
        }

    override suspend fun getBudgetByMonth(yearMonth: String): Budget? {
        val demo = preferencesManager.lifeDemoMode.first()
        return budgetDao.getBudgetByMonth(yearMonth)?.takeIf { (it.demoOrigin != DemoOrigin.NONE) == demo }
    }

    override fun getBudgetByMonthFlow(yearMonth: String): Flow<Budget?> =
        preferencesManager.lifeDemoMode.flatMapLatest { demo ->
            budgetDao.getBudgetByMonthFlow(yearMonth).map { b ->
                b?.takeIf { (it.demoOrigin != DemoOrigin.NONE) == demo }
            }
        }

    override fun getLatestBudget(): Flow<Budget?> =
        preferencesManager.lifeDemoMode.flatMapLatest { demo ->
            budgetDao.getLatestBudget().map { b ->
                b?.takeIf { (it.demoOrigin != DemoOrigin.NONE) == demo }
            }
        }

    override suspend fun insertBudget(budget: Budget): Long {
        val demo = preferencesManager.lifeDemoMode.first()
        val origin = when {
            !demo -> DemoOrigin.NONE
            budget.demoOrigin == DemoOrigin.SEED -> DemoOrigin.SEED
            else -> DemoOrigin.USER
        }
        return budgetDao.insertBudget(budget.copy(demoOrigin = origin))
    }

    override suspend fun updateBudget(budget: Budget) = budgetDao.updateBudget(budget)

    override suspend fun deleteBudget(budget: Budget) = budgetDao.deleteBudget(budget)

    override suspend fun deleteBudgetById(id: Long) = budgetDao.deleteBudgetById(id)
}
