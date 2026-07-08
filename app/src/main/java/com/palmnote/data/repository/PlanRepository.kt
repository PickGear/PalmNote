package com.palmnote.data.repository

import com.palmnote.data.db.dao.PlanDao
import com.palmnote.data.db.entity.Plan
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.PlanRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanRepository @Inject constructor(
    private val planDao: PlanDao
) : PlanRepository {
    override fun getAllPlans(): Flow<List<Plan>> = planDao.getAllPlans()

    override suspend fun getPlanById(id: Long): Plan? = planDao.getPlanById(id)

    override suspend fun insertPlan(plan: Plan): Long = planDao.insertPlan(plan)

    override suspend fun updatePlan(plan: Plan) = planDao.updatePlan(plan)

    override suspend fun softDeletePlan(id: Long) = planDao.softDeletePlan(id)
}
