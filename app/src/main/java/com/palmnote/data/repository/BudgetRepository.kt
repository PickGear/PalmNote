package com.palmnote.data.repository

import com.palmnote.data.db.dao.BudgetDao
import com.palmnote.data.db.entity.Budget
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.BudgetRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao
) : BudgetRepository {
    override fun getAllBudgets(): Flow<List<Budget>> = budgetDao.getAllBudgets()

    override suspend fun getBudgetByMonth(yearMonth: String): Budget? =
        budgetDao.getBudgetByMonth(yearMonth)

    override fun getBudgetByMonthFlow(yearMonth: String): Flow<Budget?> =
        budgetDao.getBudgetByMonthFlow(yearMonth)

    override fun getLatestBudget(): Flow<Budget?> = budgetDao.getLatestBudget()

    override suspend fun insertBudget(budget: Budget): Long = budgetDao.insertBudget(budget)

    override suspend fun updateBudget(budget: Budget) = budgetDao.updateBudget(budget)

    override suspend fun deleteBudget(budget: Budget) = budgetDao.deleteBudget(budget)

    override suspend fun deleteBudgetById(id: Long) = budgetDao.deleteBudgetById(id)
}
