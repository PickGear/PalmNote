package com.palmnote.di

import android.content.Context
import androidx.room.Room
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.*
import com.palmnote.data.db.migration.MIGRATION_1_2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideAssetDao(database: AppDatabase): AssetDao = database.assetDao()

    @Provides
    fun provideBillDao(database: AppDatabase): BillDao = database.billDao()

    @Provides
    fun provideGoalDao(database: AppDatabase): GoalDao = database.goalDao()

    @Provides
    fun provideGoalCheckInDao(database: AppDatabase): GoalCheckInDao = database.goalCheckInDao()

    @Provides
    fun provideAnniversaryDao(database: AppDatabase): AnniversaryDao = database.anniversaryDao()

    @Provides
    fun provideMomentDao(database: AppDatabase): MomentDao = database.momentDao()

    @Provides
    fun provideCategoryMappingDao(database: AppDatabase): CategoryMappingDao = database.categoryMappingDao()

    @Provides
    fun provideUsageRecordDao(database: AppDatabase): UsageRecordDao = database.usageRecordDao()

    @Provides
    fun provideBudgetDao(database: AppDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun provideRecurringTemplateDao(database: AppDatabase): RecurringTemplateDao = database.recurringTemplateDao()

    @Provides
    fun provideCategoryConfigDao(database: AppDatabase): CategoryConfigDao = database.categoryConfigDao()

    @Provides
    fun provideCustomTagDao(database: AppDatabase): CustomTagDao = database.customTagDao()

    @Provides
    fun provideWalletDao(database: AppDatabase): WalletDao = database.walletDao()

    @Provides
    fun provideAccountBookDao(database: AppDatabase): AccountBookDao = database.accountBookDao()

    @Provides
    fun providePlanListDao(database: AppDatabase): PlanListDao = database.planListDao()

    @Provides
    fun providePlanListItemDao(database: AppDatabase): PlanListItemDao = database.planListItemDao()

    @Provides
    fun providePlanDao(database: AppDatabase): PlanDao = database.planDao()

    @Provides
    fun provideLifeTemplateDao(database: AppDatabase): LifeTemplateDao = database.lifeTemplateDao()

    @Provides
    fun provideLifeItemDao(database: AppDatabase): LifeItemDao = database.lifeItemDao()

    @Provides
    fun provideCrossLinkDao(database: AppDatabase): CrossLinkDao = database.crossLinkDao()

    @Provides
    fun provideAchievementDao(database: AppDatabase): AchievementDao = database.achievementDao()

    @Provides
    fun provideLifeReportDao(database: AppDatabase): LifeReportDao = database.lifeReportDao()

    @Provides
    fun provideLegacyDao(database: AppDatabase): LegacyDao = database.legacyDao()

    @Provides
    fun provideFocusRecordDao(database: AppDatabase): FocusRecordDao = database.focusRecordDao()

    @Provides
    fun provideTodoItemDao(database: AppDatabase): TodoItemDao = database.todoItemDao()

    @Provides
    fun provideMoodDiaryDao(database: AppDatabase): MoodDiaryDao = database.moodDiaryDao()

    @Provides
    fun provideLifeMomentDao(database: AppDatabase): LifeMomentDao = database.lifeMomentDao()
}
