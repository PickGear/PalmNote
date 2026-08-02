package com.palmnote.di

import android.content.Context
import androidx.room.Room
import com.palmnote.data.LifeDataSeeder
import com.palmnote.data.backup.BackupRepository
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.AccountBookDao
import com.palmnote.data.db.dao.AchievementDao
import com.palmnote.data.db.dao.AnniversaryDao
import com.palmnote.data.db.dao.AssetDao
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.BudgetDao
import com.palmnote.data.db.dao.CategoryConfigDao
import com.palmnote.data.db.dao.CategoryMappingDao
import com.palmnote.data.db.dao.CrossLinkDao
import com.palmnote.data.db.dao.CustomTagDao
import com.palmnote.data.db.dao.FocusRecordDao
import com.palmnote.data.db.dao.GoalCheckInDao
import com.palmnote.data.db.dao.GoalDao
import com.palmnote.data.db.dao.LegacyDao
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeMomentDao
import com.palmnote.data.db.dao.LifeReportDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.dao.MomentDao
import com.palmnote.data.db.dao.MoodDiaryDao
import com.palmnote.data.db.dao.PlanDao
import com.palmnote.data.db.dao.PlanListDao
import com.palmnote.data.db.dao.PlanListItemDao
import com.palmnote.data.db.dao.RecurringTemplateDao
import com.palmnote.data.db.dao.TodoItemDao
import com.palmnote.data.db.dao.UsageRecordDao
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.db.migration.MIGRATION_1_2
import com.palmnote.data.db.migration.MIGRATION_2_3
import com.palmnote.data.db.migration.MIGRATION_3_4
import com.palmnote.data.db.migration.MIGRATION_4_5
import com.palmnote.data.export.CsvDataExporter
import com.palmnote.data.lock.AppLockManager
import com.palmnote.data.repository.AchievementRepositoryImpl
import com.palmnote.data.repository.CrossLinkRepositoryImpl
import com.palmnote.data.repository.FocusRecordRepositoryImpl
import com.palmnote.data.repository.LifeItemRepositoryImpl
import com.palmnote.data.repository.LifeReportRepositoryImpl
import com.palmnote.data.repository.LifeTemplateRepositoryImpl
import com.palmnote.data.sync.CalendarSyncManager
import com.palmnote.domain.repository.AchievementRepository
import com.palmnote.domain.repository.AnniversaryRepository
import com.palmnote.domain.repository.CrossLinkRepository
import com.palmnote.domain.repository.FocusRecordRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeReportRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.repository.WalletRepository
import com.palmnote.domain.service.TriggerEngine
import com.palmnote.domain.service.TriggerEventBus
import dagger.Module
import dagger.Provides
import dagger.Binds
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideAssetDao(db: AppDatabase): AssetDao = db.assetDao()
    @Provides fun provideBillDao(db: AppDatabase): BillDao = db.billDao()
    @Provides fun provideGoalDao(db: AppDatabase): GoalDao = db.goalDao()
    @Provides fun provideGoalCheckInDao(db: AppDatabase): GoalCheckInDao = db.goalCheckInDao()
    @Provides fun provideAnniversaryDao(db: AppDatabase): AnniversaryDao = db.anniversaryDao()
    @Provides fun provideMomentDao(db: AppDatabase): MomentDao = db.momentDao()
    @Provides fun provideCategoryMappingDao(db: AppDatabase): CategoryMappingDao = db.categoryMappingDao()
    @Provides fun provideUsageRecordDao(db: AppDatabase): UsageRecordDao = db.usageRecordDao()
    @Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideRecurringTemplateDao(db: AppDatabase): RecurringTemplateDao = db.recurringTemplateDao()
    @Provides fun provideCategoryConfigDao(db: AppDatabase): CategoryConfigDao = db.categoryConfigDao()
    @Provides fun provideCustomTagDao(db: AppDatabase): CustomTagDao = db.customTagDao()
    @Provides fun provideWalletDao(db: AppDatabase): WalletDao = db.walletDao()
    @Provides fun provideAccountBookDao(db: AppDatabase): AccountBookDao = db.accountBookDao()
    @Provides fun providePlanListDao(db: AppDatabase): PlanListDao = db.planListDao()
    @Provides fun providePlanListItemDao(db: AppDatabase): PlanListItemDao = db.planListItemDao()
    @Provides fun providePlanDao(db: AppDatabase): PlanDao = db.planDao()
    @Provides fun provideLifeTemplateDao(db: AppDatabase): LifeTemplateDao = db.lifeTemplateDao()
    @Provides fun provideLifeItemDao(db: AppDatabase): LifeItemDao = db.lifeItemDao()
    @Provides fun provideCrossLinkDao(db: AppDatabase): CrossLinkDao = db.crossLinkDao()
    @Provides fun provideAchievementDao(db: AppDatabase): AchievementDao = db.achievementDao()
    @Provides fun provideLifeReportDao(db: AppDatabase): LifeReportDao = db.lifeReportDao()
    @Provides fun provideLegacyDao(db: AppDatabase): LegacyDao = db.legacyDao()
    @Provides fun provideFocusRecordDao(db: AppDatabase): FocusRecordDao = db.focusRecordDao()
    @Provides fun provideTodoItemDao(db: AppDatabase): TodoItemDao = db.todoItemDao()
    @Provides fun provideMoodDiaryDao(db: AppDatabase): MoodDiaryDao = db.moodDiaryDao()
    @Provides fun provideLifeMomentDao(db: AppDatabase): LifeMomentDao = db.lifeMomentDao()

    @Provides fun provideVaultDao(db: AppDatabase): com.palmnote.feature.vault.VaultDao = db.vaultDao()

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager =
        PreferencesManager(context)

    @Provides
    @Singleton
    fun provideAppLockManager(
        @ApplicationContext context: Context,
        preferencesManager: PreferencesManager
    ): AppLockManager = AppLockManager(context, preferencesManager)

    @Provides
    @Singleton
    fun provideCsvDataExporter(
        @ApplicationContext context: Context,
        db: AppDatabase,
        preferencesManager: PreferencesManager
    ): CsvDataExporter = CsvDataExporter(context, db, preferencesManager)

    @Provides
    @Singleton
    fun provideBackupRepository(
        @ApplicationContext context: Context,
        db: AppDatabase
    ): BackupRepository = BackupRepository(context, db)

    @Provides
    @Singleton
    fun provideCalendarSyncManager(
        @ApplicationContext context: Context,
        anniversaryRepository: AnniversaryRepository
    ): CalendarSyncManager = CalendarSyncManager(context, anniversaryRepository)

    @Provides
    @Singleton
    fun provideTriggerEngine(
        @ApplicationContext context: Context,
        lifeItemRepository: LifeItemRepository,
        crossLinkRepository: CrossLinkRepository,
        @ApplicationScope scope: CoroutineScope
    ): TriggerEngine = TriggerEngine(context, javax.inject.Provider { lifeItemRepository }, crossLinkRepository, scope)

    @Provides
    @Singleton
    fun provideTriggerEventBus(triggerEngine: TriggerEngine): TriggerEventBus =
        TriggerEventBus(triggerEngine)

    @Provides
    @Singleton
    fun provideLifeDataSeeder(
        lifeTemplateRepository: LifeTemplateRepository,
        appDatabase: AppDatabase
    ): LifeDataSeeder =
        LifeDataSeeder(lifeTemplateRepository, appDatabase)

    @Provides
    @Singleton
    @JvmSuppressWildcards fun provideCachedWallets(
        walletRepository: WalletRepository,
        @ApplicationScope scope: CoroutineScope
    ): StateFlow<List<Wallet>> =
        walletRepository.getEnabledWallets().stateIn(scope, SharingStarted.Eagerly, emptyList())

    @Provides
    @Singleton
    @JvmSuppressWildcards fun provideCachedCategoryConfigs(
        categoryConfigRepository: com.palmnote.data.repository.CategoryConfigRepositoryImpl,
        @ApplicationScope scope: CoroutineScope
    ): StateFlow<List<CategoryConfig>> =
        categoryConfigRepository.getAllCategories().stateIn(scope, SharingStarted.Eagerly, emptyList())

    @Provides
    @Singleton
    @JvmSuppressWildcards fun provideCachedAccountBooks(
        accountBookRepository: com.palmnote.data.repository.AccountBookRepositoryImpl,
        @ApplicationScope scope: CoroutineScope
    ): StateFlow<List<AccountBook>> =
        accountBookRepository.getAllBooks().stateIn(scope, SharingStarted.Eagerly, emptyList())
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // 注意：data 实现类与 domain 接口同名（如 data.BillRepository → domain.BillRepository）。
    // Hilt 的 @Binds 遇到同名返回/参数类型会触发 javapoet canonicalName 崩溃，
    // 因此这里用 @Provides 在方法体内显式构造，签名只暴露接口类型。

    @Provides @Singleton
    fun provideAssetRepository(dao: AssetDao): com.palmnote.domain.repository.AssetRepository =
        com.palmnote.data.repository.AssetRepositoryImpl(dao)

    @Provides @Singleton
    fun provideBillRepository(
        dao: BillDao,
        walletDao: WalletDao,
        appDatabase: AppDatabase
    ): com.palmnote.domain.repository.BillRepository =
        com.palmnote.data.repository.BillRepositoryImpl(dao, walletDao, appDatabase)

    @Provides @Singleton
    fun provideBudgetRepository(dao: BudgetDao): com.palmnote.domain.repository.BudgetRepository =
        com.palmnote.data.repository.BudgetRepositoryImpl(dao)

    @Provides @Singleton
    fun provideGoalRepository(dao: GoalDao, checkInDao: GoalCheckInDao): com.palmnote.domain.repository.GoalRepository =
        com.palmnote.data.repository.GoalRepositoryImpl(dao, checkInDao)

    @Provides @Singleton
    fun provideAnniversaryRepository(dao: AnniversaryDao): com.palmnote.domain.repository.AnniversaryRepository =
        com.palmnote.data.repository.AnniversaryRepositoryImpl(dao)

    @Provides @Singleton
    fun provideMomentRepository(dao: MomentDao): com.palmnote.domain.repository.MomentRepository =
        com.palmnote.data.repository.MomentRepositoryImpl(dao)

    @Provides @Singleton
    fun provideCategoryConfigRepository(
        @ApplicationContext context: Context,
        dao: CategoryConfigDao,
        customTagDao: CustomTagDao,
        appDatabase: AppDatabase
    ): com.palmnote.domain.repository.CategoryConfigRepository =
        com.palmnote.data.repository.CategoryConfigRepositoryImpl(context, dao, customTagDao, appDatabase)

    @Provides @Singleton
    fun provideCategoryMappingRepository(dao: CategoryMappingDao): com.palmnote.domain.repository.CategoryMappingRepository =
        com.palmnote.data.repository.CategoryMappingRepositoryImpl(dao)

    @Provides @Singleton
    fun provideUsageRecordRepository(dao: UsageRecordDao): com.palmnote.domain.repository.UsageRecordRepository =
        com.palmnote.data.repository.UsageRecordRepositoryImpl(dao)

    @Provides @Singleton
    fun provideWalletRepository(
        dao: WalletDao,
        billDao: BillDao,
        appDatabase: AppDatabase,
        @ApplicationContext context: Context
    ): com.palmnote.domain.repository.WalletRepository =
        com.palmnote.data.repository.WalletRepositoryImpl(dao, billDao, appDatabase, context)

    @Provides @Singleton
    fun provideAccountBookRepository(
        dao: AccountBookDao,
        billDao: BillDao,
        appDatabase: AppDatabase,
        @ApplicationContext context: Context
    ): com.palmnote.domain.repository.AccountBookRepository =
        com.palmnote.data.repository.AccountBookRepositoryImpl(dao, billDao, appDatabase, context)

    @Provides @Singleton
    fun providePlanListRepository(
        planListDao: PlanListDao,
        planListItemDao: PlanListItemDao
    ): com.palmnote.domain.repository.PlanListRepository =
        com.palmnote.data.repository.PlanListRepositoryImpl(planListDao, planListItemDao)

    @Provides @Singleton
    fun providePlanRepository(dao: PlanDao): com.palmnote.domain.repository.PlanRepository =
        com.palmnote.data.repository.PlanRepositoryImpl(dao)

    @Provides @Singleton
    fun provideMoodDiaryRepository(dao: MoodDiaryDao): com.palmnote.domain.repository.MoodDiaryRepository =
        com.palmnote.data.repository.MoodDiaryRepositoryImpl(dao)

    @Provides @Singleton
    fun provideLifeMomentRepository(dao: LifeMomentDao): com.palmnote.domain.repository.LifeMomentRepository =
        com.palmnote.data.repository.LifeMomentRepositoryImpl(dao)

    @Provides @Singleton
    fun provideLifeTemplateRepository(dao: LifeTemplateDao): LifeTemplateRepository =
        LifeTemplateRepositoryImpl(dao)

    @Provides @Singleton
    fun provideLifeItemRepository(dao: LifeItemDao): LifeItemRepository =
        LifeItemRepositoryImpl(dao)

    @Provides @Singleton
    fun provideCrossLinkRepository(dao: CrossLinkDao): CrossLinkRepository =
        CrossLinkRepositoryImpl(dao)

    @Provides @Singleton
    fun provideAchievementRepository(dao: AchievementDao): AchievementRepository =
        AchievementRepositoryImpl(dao)

    @Provides @Singleton
    fun provideFocusRecordRepository(dao: FocusRecordDao): FocusRecordRepository =
        FocusRecordRepositoryImpl(dao)

    @Provides @Singleton
    fun provideLifeReportRepository(dao: LifeReportDao): LifeReportRepository =
        LifeReportRepositoryImpl(dao)
}
