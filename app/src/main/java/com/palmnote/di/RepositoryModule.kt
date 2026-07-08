package com.palmnote.di

import com.palmnote.data.repository.AccountBookRepository
import com.palmnote.data.repository.AnniversaryRepository
import com.palmnote.data.repository.AssetRepository
import com.palmnote.data.repository.BillRepository
import com.palmnote.data.repository.BudgetRepository
import com.palmnote.data.repository.CategoryConfigRepository
import com.palmnote.data.repository.CategoryMappingRepository
import com.palmnote.data.repository.GoalRepository
import com.palmnote.data.repository.MomentRepository
import com.palmnote.data.repository.PlanListRepository
import com.palmnote.data.repository.PlanRepository
import com.palmnote.data.repository.UsageRecordRepository
import com.palmnote.data.repository.WalletRepository
import com.palmnote.data.repository.AchievementRepositoryImpl
import com.palmnote.data.repository.CrossLinkRepositoryImpl
import com.palmnote.data.repository.FocusRecordRepositoryImpl
import com.palmnote.data.repository.LifeItemRepositoryImpl
import com.palmnote.data.repository.LifeReportRepositoryImpl
import com.palmnote.data.repository.LifeTemplateRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAccountBookRepository(impl: com.palmnote.data.repository.AccountBookRepository): com.palmnote.domain.repository.AccountBookRepository

    @Binds @Singleton
    abstract fun bindAnniversaryRepository(impl: com.palmnote.data.repository.AnniversaryRepository): com.palmnote.domain.repository.AnniversaryRepository

    @Binds @Singleton
    abstract fun bindAssetRepository(impl: com.palmnote.data.repository.AssetRepository): com.palmnote.domain.repository.AssetRepository

    @Binds @Singleton
    abstract fun bindBillRepository(impl: com.palmnote.data.repository.BillRepository): com.palmnote.domain.repository.BillRepository

    @Binds @Singleton
    abstract fun bindBudgetRepository(impl: com.palmnote.data.repository.BudgetRepository): com.palmnote.domain.repository.BudgetRepository

    @Binds @Singleton
    abstract fun bindCategoryConfigRepository(impl: com.palmnote.data.repository.CategoryConfigRepository): com.palmnote.domain.repository.CategoryConfigRepository

    @Binds @Singleton
    abstract fun bindCategoryMappingRepository(impl: com.palmnote.data.repository.CategoryMappingRepository): com.palmnote.domain.repository.CategoryMappingRepository

    @Binds @Singleton
    abstract fun bindGoalRepository(impl: com.palmnote.data.repository.GoalRepository): com.palmnote.domain.repository.GoalRepository

    @Binds @Singleton
    abstract fun bindMomentRepository(impl: com.palmnote.data.repository.MomentRepository): com.palmnote.domain.repository.MomentRepository

    @Binds @Singleton
    abstract fun bindPlanListRepository(impl: com.palmnote.data.repository.PlanListRepository): com.palmnote.domain.repository.PlanListRepository

    @Binds @Singleton
    abstract fun bindPlanRepository(impl: com.palmnote.data.repository.PlanRepository): com.palmnote.domain.repository.PlanRepository

    @Binds @Singleton
    abstract fun bindUsageRecordRepository(impl: com.palmnote.data.repository.UsageRecordRepository): com.palmnote.domain.repository.UsageRecordRepository

    @Binds @Singleton
    abstract fun bindWalletRepository(impl: com.palmnote.data.repository.WalletRepository): com.palmnote.domain.repository.WalletRepository

    @Binds @Singleton
    abstract fun bindLifeTemplateRepository(impl: com.palmnote.data.repository.LifeTemplateRepositoryImpl): com.palmnote.domain.repository.LifeTemplateRepository

    @Binds @Singleton
    abstract fun bindLifeItemRepository(impl: com.palmnote.data.repository.LifeItemRepositoryImpl): com.palmnote.domain.repository.LifeItemRepository

    @Binds @Singleton
    abstract fun bindCrossLinkRepository(impl: com.palmnote.data.repository.CrossLinkRepositoryImpl): com.palmnote.domain.repository.CrossLinkRepository

    @Binds @Singleton
    abstract fun bindAchievementRepository(impl: com.palmnote.data.repository.AchievementRepositoryImpl): com.palmnote.domain.repository.AchievementRepository

    @Binds @Singleton
    abstract fun bindFocusRecordRepository(impl: com.palmnote.data.repository.FocusRecordRepositoryImpl): com.palmnote.domain.repository.FocusRecordRepository

    @Binds @Singleton
    abstract fun bindLifeReportRepository(impl: com.palmnote.data.repository.LifeReportRepositoryImpl): com.palmnote.domain.repository.LifeReportRepository

    @Binds @Singleton
    abstract fun bindTodoRepository(impl: com.palmnote.data.repository.TodoRepositoryImpl): com.palmnote.domain.repository.TodoRepository

    @Binds @Singleton
    abstract fun bindTodoItemRepository(impl: com.palmnote.data.repository.TodoItemRepository): com.palmnote.domain.repository.TodoItemRepository

    @Binds @Singleton
    abstract fun bindMoodDiaryRepository(impl: com.palmnote.data.repository.MoodDiaryRepository): com.palmnote.domain.repository.MoodDiaryRepository

    @Binds @Singleton
    abstract fun bindLifeMomentRepository(impl: com.palmnote.data.repository.LifeMomentRepository): com.palmnote.domain.repository.LifeMomentRepository
}
