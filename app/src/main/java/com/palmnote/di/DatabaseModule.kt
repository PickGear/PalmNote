package com.palmnote.di

import android.content.Context
import androidx.room.Room
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.*
import com.palmnote.data.db.migration.MIGRATION_1_2
import com.palmnote.data.db.migration.MIGRATION_2_3
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    @Provides @Singleton
    fun provideAssetDao(database: AppDatabase): AssetDao = database.assetDao()

    @Provides @Singleton
    fun provideBillDao(database: AppDatabase): BillDao = database.billDao()

    @Provides @Singleton
    fun provideGoalDao(database: AppDatabase): GoalDao = database.goalDao()

    @Provides @Singleton
    fun provideGoalCheckInDao(database: AppDatabase): GoalCheckInDao = database.goalCheckInDao()

    @Provides @Singleton
    fun provideAnniversaryDao(database: AppDatabase): AnniversaryDao = database.anniversaryDao()

    @Provides @Singleton
    fun provideMomentDao(database: AppDatabase): MomentDao = database.momentDao()

    @Provides @Singleton
    fun provideCategoryMappingDao(database: AppDatabase): CategoryMappingDao = database.categoryMappingDao()

    @Provides @Singleton
    fun provideUsageRecordDao(database: AppDatabase): UsageRecordDao = database.usageRecordDao()

    @Provides @Singleton
    fun provideBudgetDao(database: AppDatabase): BudgetDao = database.budgetDao()

    @Provides @Singleton
    fun provideRecurringTemplateDao(database: AppDatabase): RecurringTemplateDao = database.recurringTemplateDao()

    @Provides @Singleton
    fun provideCategoryConfigDao(database: AppDatabase): CategoryConfigDao = database.categoryConfigDao()

    @Provides @Singleton
    fun provideCustomTagDao(database: AppDatabase): CustomTagDao = database.customTagDao()

    @Provides @Singleton
    fun provideWalletDao(database: AppDatabase): WalletDao = database.walletDao()

    @Provides @Singleton
    fun provideAccountBookDao(database: AppDatabase): AccountBookDao = database.accountBookDao()

    @Provides @Singleton
    fun providePlanListDao(database: AppDatabase): PlanListDao = database.planListDao()

    @Provides @Singleton
    fun providePlanListItemDao(database: AppDatabase): PlanListItemDao = database.planListItemDao()

    @Provides @Singleton
    fun providePlanDao(database: AppDatabase): PlanDao = database.planDao()

    @Provides @Singleton
    fun provideLifeTemplateDao(database: AppDatabase): LifeTemplateDao = database.lifeTemplateDao()

    @Provides @Singleton
    fun provideLifeItemDao(database: AppDatabase): LifeItemDao = database.lifeItemDao()

    @Provides @Singleton
    fun provideCrossLinkDao(database: AppDatabase): CrossLinkDao = database.crossLinkDao()

    @Provides @Singleton
    fun provideAchievementDao(database: AppDatabase): AchievementDao = database.achievementDao()

    @Provides @Singleton
    fun provideLifeReportDao(database: AppDatabase): LifeReportDao = database.lifeReportDao()

    @Provides @Singleton
    fun provideLegacyDao(database: AppDatabase): LegacyDao = database.legacyDao()

    @Provides @Singleton
    fun provideFocusRecordDao(database: AppDatabase): FocusRecordDao = database.focusRecordDao()

    @Provides @Singleton
    fun provideTodoItemDao(database: AppDatabase): TodoItemDao = database.todoItemDao()

    @Provides @Singleton
    fun provideMoodDiaryDao(database: AppDatabase): MoodDiaryDao = database.moodDiaryDao()

    @Provides @Singleton
    fun provideLifeMomentDao(database: AppDatabase): LifeMomentDao = database.lifeMomentDao()
}
