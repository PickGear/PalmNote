package com.palmnote.domain.repository

import com.palmnote.data.db.entity.Plan
import kotlinx.coroutines.flow.Flow

interface PlanRepository {
    fun getAllPlans(): Flow<List<Plan>>
    suspend fun getPlanById(id: Long): Plan?
    suspend fun insertPlan(plan: Plan): Long
    suspend fun updatePlan(plan: Plan)
    suspend fun deletePlan(id: Long)
}
