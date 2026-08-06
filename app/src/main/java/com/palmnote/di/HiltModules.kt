package com.palmnote.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.palmnote.data.LifeDataSeeder
import com.palmnote.data.backup.BackupRepository
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.DbKeyStore
import com.palmnote.data.db.EncryptedOpenHelperFactory
import com.palmnote.data.db.dao.*
import com.palmnote.data.db.entity.*
import com.palmnote.data.export.CsvDataExporter
import com.palmnote.data.lock.AppLockManager
import com.palmnote.data.ocr.OcrEngine
import com.palmnote.data.ocr.PaddleOcrEngine
import com.palmnote.data.repository.*
import com.palmnote.data.sync.CalendarSyncManager
import com.palmnote.domain.repository.*
import com.palmnote.domain.service.TriggerEngine
import dagger.Module
import dagger.Provides
import dagger.Binds
import dagger.multibindings.IntoSet
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
        db: AppDatabase,
        dbKeyStore: DbKeyStore
    ): BackupRepository = BackupRepository(context, db, dbKeyStore)

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
        categoryConfigRepository: com.palmnote.domain.repository.CategoryConfigRepository,
        @ApplicationScope scope: CoroutineScope
    ): StateFlow<List<CategoryConfig>> =
        categoryConfigRepository.getAllCategories().stateIn(scope, SharingStarted.Eagerly, emptyList())

    @Provides
    @Singleton
    @JvmSuppressWildcards fun provideCachedAccountBooks(
        accountBookRepository: com.palmnote.domain.repository.AccountBookRepository,
        @ApplicationScope scope: CoroutineScope
    ): StateFlow<List<AccountBook>> =
        accountBookRepository.getAllBooks().stateIn(scope, SharingStarted.Eagerly, emptyList())
}

@Module
@InstallIn(SingletonComponent::class)
object OcrModule {

    @Provides
    fun provideOcrEngine(@ApplicationContext context: Context): OcrEngine =
        PaddleOcrEngine(context)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        dbKeyStore: DbKeyStore
    ): AppDatabase {
        val factory = EncryptedOpenHelperFactory(context, dbKeyStore)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .openHelperFactory(factory)
            .addMigrations(
                com.palmnote.data.db.migration.MIGRATION_1_2,
                com.palmnote.data.db.migration.MIGRATION_2_3,
                com.palmnote.data.db.migration.MIGRATION_3_4,
                com.palmnote.data.db.migration.MIGRATION_4_5,
                com.palmnote.data.db.migration.Migration5To6(
                    context.getDatabasePath(com.palmnote.feature.vault.VaultDatabase.DATABASE_NAME).absolutePath,
                    dbKeyStore.getOrCreateKey()
                ),
                com.palmnote.data.db.migration.MIGRATION_6_7
            )
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // FTS 全文搜索虚拟表（触发器在 createBillTriggers 中创建）
                    db.execSQL("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS bills_fts USING fts5(
                            note, merchant, tags, content='bills', content_rowid='id'
                        )
                    """)
                    com.palmnote.data.db.createBillTriggers(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // SQLCipher 的 execSQL 不支持返回结果行的语句，PRAGMA 需走 query 路径
                    db.query("PRAGMA journal_mode = WAL").close()
                    db.query("PRAGMA synchronous = NORMAL").close()
                    db.query("PRAGMA cache_size = -20000").close()
                    db.query("PRAGMA temp_store = MEMORY").close()
                }
            })
            .build()
    }

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
    @Provides fun provideBillRecycleBinDao(db: AppDatabase): BillRecycleBinDao = db.billRecycleBinDao()
    @Provides fun provideAssetRecycleBinDao(db: AppDatabase): AssetRecycleBinDao = db.assetRecycleBinDao()
}

@Module
@InstallIn(SingletonComponent::class)
object VaultDatabaseModule {

    @Provides
    @Singleton
    fun provideVaultRepository(
        dao: com.palmnote.feature.vault.VaultDao,
        keyManager: com.palmnote.feature.vault.VaultKeyManager
    ): com.palmnote.feature.vault.VaultRepository =
        com.palmnote.feature.vault.VaultRepositoryImpl(dao, keyManager)

    @Provides
    @Singleton
    fun provideVaultDatabase(
        @ApplicationContext context: Context,
        dbKeyStore: DbKeyStore
    ): com.palmnote.feature.vault.VaultDatabase {
        val factory = EncryptedOpenHelperFactory(context, dbKeyStore)
        return Room.databaseBuilder(
            context,
            com.palmnote.feature.vault.VaultDatabase::class.java,
            com.palmnote.feature.vault.VaultDatabase.DATABASE_NAME
        )
            .openHelperFactory(factory)
            .build()
    }

    @Provides
    fun provideVaultDao(db: com.palmnote.feature.vault.VaultDatabase): com.palmnote.feature.vault.VaultDao =
        db.vaultDao()
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun provideAssetRepository(
        dao: AssetDao,
        assetRecycleBinDao: AssetRecycleBinDao,
        appDatabase: AppDatabase
    ): AssetRepository =
        AssetRepositoryImpl(dao, assetRecycleBinDao, appDatabase)

    @Provides @Singleton
    fun provideBillRepository(
        dao: BillDao,
        walletDao: WalletDao,
        recycleBinDao: BillRecycleBinDao,
        appDatabase: AppDatabase
    ): BillRepository =
        BillRepositoryImpl(dao, walletDao, recycleBinDao, appDatabase)

    @Provides @Singleton
    fun provideBudgetRepository(dao: BudgetDao): BudgetRepository =
        BudgetRepositoryImpl(dao)

    @Provides @Singleton
    fun provideGoalRepository(dao: GoalDao, checkInDao: GoalCheckInDao): GoalRepository =
        GoalRepositoryImpl(dao, checkInDao)

    @Provides @Singleton
    fun provideAnniversaryRepository(dao: AnniversaryDao): AnniversaryRepository =
        AnniversaryRepositoryImpl(dao)

    @Provides @Singleton
    fun provideMomentRepository(dao: MomentDao): MomentRepository =
        MomentRepositoryImpl(dao)

    @Provides @Singleton
    fun provideCategoryConfigRepository(
        @ApplicationContext context: Context,
        dao: CategoryConfigDao,
        customTagDao: CustomTagDao,
        appDatabase: AppDatabase
    ): CategoryConfigRepository =
        CategoryConfigRepositoryImpl(context, dao, customTagDao, appDatabase)

    @Provides @Singleton
    fun provideCategoryMappingRepository(dao: CategoryMappingDao): CategoryMappingRepository =
        CategoryMappingRepositoryImpl(dao)

    @Provides @Singleton
    fun provideUsageRecordRepository(dao: UsageRecordDao): UsageRecordRepository =
        UsageRecordRepositoryImpl(dao)

    @Provides @Singleton
    fun provideWalletRepository(
        dao: WalletDao,
        billDao: BillDao,
        appDatabase: AppDatabase,
        @ApplicationContext context: Context
    ): WalletRepository =
        WalletRepositoryImpl(dao, billDao, appDatabase, context)

    @Provides @Singleton
    fun provideAccountBookRepository(
        dao: AccountBookDao,
        billDao: BillDao,
        appDatabase: AppDatabase,
        @ApplicationContext context: Context
    ): AccountBookRepository =
        AccountBookRepositoryImpl(dao, billDao, appDatabase, context)

    @Provides @Singleton
    fun providePlanListRepository(
        planListDao: PlanListDao,
        planListItemDao: PlanListItemDao
    ): PlanListRepository =
        PlanListRepositoryImpl(planListDao, planListItemDao)

    @Provides @Singleton
    fun providePlanRepository(dao: PlanDao): PlanRepository =
        PlanRepositoryImpl(dao)

    @Provides @Singleton
    fun provideMoodDiaryRepository(dao: MoodDiaryDao): MoodDiaryRepository =
        MoodDiaryRepositoryImpl(dao)

    @Provides @Singleton
    fun provideLifeMomentRepository(dao: LifeMomentDao): LifeMomentRepository =
        LifeMomentRepositoryImpl(dao)

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

@Module
@InstallIn(SingletonComponent::class)
abstract class EventModule {
    @Binds @Singleton
    abstract fun bindEventBus(impl: com.palmnote.data.event.EventBusImpl): com.palmnote.domain.event.EventBus

    @Binds @IntoSet @Singleton
    abstract fun bindTriggerConsumer(impl: com.palmnote.data.event.TriggerEventConsumer): com.palmnote.domain.event.EventConsumer
}
