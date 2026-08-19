package com.palmnote.domain.repository

import com.palmnote.data.db.entity.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getAllBudgets(): Flow<List<Budget>>
    suspend fun getBudgetByMonth(yearMonth: String): Budget?
    fun getBudgetByMonthFlow(yearMonth: String): Flow<Budget?>
    fun getLatestBudget(): Flow<Budget?>
    suspend fun insertBudget(budget: Budget): Long
    suspend fun updateBudget(budget: Budget)
    suspend fun deleteBudget(budget: Budget)
    suspend fun deleteBudgetById(id: Long)
}
