package com.palmnote.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.palmnote.data.LifeDataSeeder
import com.palmnote.data.backup.BackupRepository
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.AssetDao
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.GoalDao
import com.palmnote.data.db.dao.GoalCheckInDao
import com.palmnote.data.db.dao.AnniversaryDao
import com.palmnote.data.db.dao.MomentDao
import com.palmnote.data.db.dao.CategoryMappingDao
import com.palmnote.data.db.dao.UsageRecordDao
import com.palmnote.data.db.dao.BudgetDao
import com.palmnote.data.db.dao.RecurringTemplateDao
import com.palmnote.data.db.dao.CategoryConfigDao
import com.palmnote.data.db.dao.CustomTagDao
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.dao.AccountBookDao
import com.palmnote.data.db.dao.PlanListDao
import com.palmnote.data.db.dao.PlanListItemDao
import com.palmnote.data.db.dao.PlanDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.CrossLinkDao
import com.palmnote.data.db.dao.AchievementDao
import com.palmnote.data.db.dao.LifeReportDao
import com.palmnote.data.db.dao.LegacyDao
import com.palmnote.data.db.dao.FocusRecordDao
import com.palmnote.data.db.dao.TodoItemDao
import com.palmnote.data.db.dao.MoodDiaryDao
import com.palmnote.data.db.dao.LifeMomentDao
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.db.migration.MIGRATION_1_2
import com.palmnote.data.db.migration.MIGRATION_2_3
import com.palmnote.data.export.CsvDataExporter
import com.palmnote.data.lock.AppLockManager
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
import com.palmnote.data.repository.TodoRepositoryImpl
import com.palmnote.data.repository.TodoItemRepository
import com.palmnote.data.repository.MoodDiaryRepository
import com.palmnote.data.repository.LifeMomentRepository
import com.palmnote.data.sync.CalendarSyncManager
import com.palmnote.data.worker.AutoBackupWorker
import com.palmnote.data.worker.LifeDailyCheckWorker
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.CrossLinkRepository
import com.palmnote.domain.repository.AchievementRepository
import com.palmnote.domain.repository.FocusRecordRepository
import com.palmnote.domain.repository.LifeReportRepository
import com.palmnote.domain.repository.TodoRepository
import com.palmnote.domain.service.TriggerEngine
import com.palmnote.domain.service.TriggerEventBus
import com.palmnote.ui.asset.AssetViewModel
import com.palmnote.ui.backup.BackupViewModel
import com.palmnote.ui.bills.BillViewModel
import com.palmnote.ui.bills.BillDetailViewModel
import com.palmnote.ui.bills.BillImportViewModel
import com.palmnote.ui.bills.ReportViewModel as BillReportViewModel
import com.palmnote.ui.dashboard.DashboardViewModel
import com.palmnote.ui.life.LifeViewModel
import com.palmnote.ui.life.common.TplDispViewModel
import com.palmnote.ui.life.common.GenericListViewModel
import com.palmnote.ui.life.common.CreateItemViewModel
import com.palmnote.ui.life.common.ItemDetailViewModel
import com.palmnote.ui.life.common.TemplateCreateViewModel
import com.palmnote.ui.life.common.LinkSelectorViewModel
import com.palmnote.ui.life.time.countup.CountUpViewModel
import com.palmnote.ui.life.time.countdown.CountdownViewModel
import com.palmnote.ui.life.time.birthday.BirthdayViewModel
import com.palmnote.ui.life.time.anniversary.AnniversaryViewModel
import com.palmnote.ui.life.plan.saving.SavingPlanViewModel
import com.palmnote.ui.life.plan.reading.ReadingPlanViewModel
import com.palmnote.ui.life.plan.travel.TravelPlanViewModel
import com.palmnote.ui.life.plan.study.StudyPlanViewModel
import com.palmnote.ui.life.plan.shopping.ShoppingPlanViewModel
import com.palmnote.ui.life.plan.subscription.SubscriptionViewModel
import com.palmnote.ui.life.plan.todo.TodoViewModel
import com.palmnote.ui.life.record.focus.FocusViewModel
import com.palmnote.ui.life.record.habit.HabitViewModel
import com.palmnote.ui.life.record.habit.AchievementViewModel
import com.palmnote.ui.life.record.mood.MoodViewModel
import com.palmnote.ui.life.record.journal.JournalViewModel
import com.palmnote.ui.life.record.report.ReportViewModel as LifeReportViewModel
import com.palmnote.ui.search.SearchViewModel
import com.palmnote.ui.settings.SettingsViewModel
import com.palmnote.ui.settings.CategoryViewModel
import com.palmnote.ui.settings.WalletViewModel
import com.palmnote.ui.settings.DataClearViewModel
import com.palmnote.ui.settings.RecycleBinViewModel
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Provider

class AppContainer(private val application: Application) {

    private val context: Context = application.applicationContext

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    val assetDao: AssetDao by lazy { database.assetDao() }
    val billDao: BillDao by lazy { database.billDao() }
    val goalDao: GoalDao by lazy { database.goalDao() }
    val goalCheckInDao: GoalCheckInDao by lazy { database.goalCheckInDao() }
    val anniversaryDao: AnniversaryDao by lazy { database.anniversaryDao() }
    val momentDao: MomentDao by lazy { database.momentDao() }
    val categoryMappingDao: CategoryMappingDao by lazy { database.categoryMappingDao() }
    val usageRecordDao: UsageRecordDao by lazy { database.usageRecordDao() }
    val budgetDao: BudgetDao by lazy { database.budgetDao() }
    val recurringTemplateDao: RecurringTemplateDao by lazy { database.recurringTemplateDao() }
    val categoryConfigDao: CategoryConfigDao by lazy { database.categoryConfigDao() }
    val customTagDao: CustomTagDao by lazy { database.customTagDao() }
    val walletDao: WalletDao by lazy { database.walletDao() }
    val accountBookDao: AccountBookDao by lazy { database.accountBookDao() }
    val planListDao: PlanListDao by lazy { database.planListDao() }
    val planListItemDao: PlanListItemDao by lazy { database.planListItemDao() }
    val planDao: PlanDao by lazy { database.planDao() }
    val lifeTemplateDao: LifeTemplateDao by lazy { database.lifeTemplateDao() }
    val lifeItemDao: LifeItemDao by lazy { database.lifeItemDao() }
    val crossLinkDao: CrossLinkDao by lazy { database.crossLinkDao() }
    val achievementDao: AchievementDao by lazy { database.achievementDao() }
    val lifeReportDao: LifeReportDao by lazy { database.lifeReportDao() }
    val legacyDao: LegacyDao by lazy { database.legacyDao() }
    val focusRecordDao: FocusRecordDao by lazy { database.focusRecordDao() }
    val todoItemDao: TodoItemDao by lazy { database.todoItemDao() }
    val moodDiaryDao: MoodDiaryDao by lazy { database.moodDiaryDao() }
    val lifeMomentDao: LifeMomentDao by lazy { database.lifeMomentDao() }

    val preferencesManager: PreferencesManager by lazy { PreferencesManager(context) }
    val appLockManager: AppLockManager by lazy { AppLockManager(context, preferencesManager) }
    val csvDataExporter: CsvDataExporter by lazy { CsvDataExporter(context, database, preferencesManager) }
    val backupRepository: BackupRepository by lazy { BackupRepository(context, database) }
    val calendarSyncManager: CalendarSyncManager by lazy { CalendarSyncManager(context, anniversaryRepository) }
    val triggerEngine: TriggerEngine by lazy { TriggerEngine(context, Provider { lifeItemRepository }, crossLinkRepository) }
    val eventBus: TriggerEventBus by lazy { TriggerEventBus(triggerEngine) }
    val lifeDataSeeder: LifeDataSeeder by lazy { LifeDataSeeder(lifeTemplateRepository) }

    val assetRepository: AssetRepository by lazy { AssetRepository(assetDao) }
    val billRepository: BillRepository by lazy { BillRepository(billDao) }
    val budgetRepository: BudgetRepository by lazy { BudgetRepository(budgetDao) }
    val goalRepository: GoalRepository by lazy { GoalRepository(goalDao) }
    val anniversaryRepository: AnniversaryRepository by lazy { AnniversaryRepository(anniversaryDao) }
    val momentRepository: MomentRepository by lazy { MomentRepository(momentDao) }
    val categoryConfigRepository: CategoryConfigRepository by lazy { CategoryConfigRepository(context, categoryConfigDao, customTagDao) }
    val cachedCategoryConfigs = categoryConfigRepository.getAllCategories()
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyList())
    val categoryMappingRepository: CategoryMappingRepository by lazy { CategoryMappingRepository(categoryMappingDao) }
    val usageRecordRepository: UsageRecordRepository by lazy { UsageRecordRepository(usageRecordDao) }
    val walletRepository: WalletRepository by lazy { WalletRepository(walletDao, context) }
    val cachedWallets = walletRepository.getEnabledWallets()
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyList())

    val accountBookRepository: AccountBookRepository by lazy { AccountBookRepository(accountBookDao, context) }
    val cachedAccountBooks = accountBookRepository.getAllBooks()
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyList())
    val planListRepository: PlanListRepository by lazy { PlanListRepository(planListDao, planListItemDao) }
    val planRepository: PlanRepository by lazy { PlanRepository(planDao) }
    val lifeTemplateRepository: LifeTemplateRepository by lazy { LifeTemplateRepositoryImpl(lifeTemplateDao) }
    val lifeItemRepository: LifeItemRepository by lazy { LifeItemRepositoryImpl(lifeItemDao) }
    val crossLinkRepository: CrossLinkRepository by lazy { CrossLinkRepositoryImpl(crossLinkDao) }
    val achievementRepository: AchievementRepository by lazy { AchievementRepositoryImpl(achievementDao) }
    val focusRecordRepository: FocusRecordRepository by lazy { FocusRecordRepositoryImpl(focusRecordDao) }
    val lifeReportRepository: LifeReportRepository by lazy { LifeReportRepositoryImpl(lifeReportDao) }
    val todoRepository: TodoRepository by lazy { TodoRepositoryImpl(legacyDao) }
    val todoItemRepository: TodoItemRepository by lazy { TodoItemRepository(todoItemDao) }
    val moodDiaryRepository: MoodDiaryRepository by lazy { MoodDiaryRepository(moodDiaryDao) }
    val lifeMomentRepository: LifeMomentRepository by lazy { LifeMomentRepository(lifeMomentDao) }

    fun dashboardViewModel() = DashboardViewModel(assetRepository, billRepository, budgetRepository, goalRepository, anniversaryRepository, preferencesManager)
    fun assetViewModel() = AssetViewModel(application, assetRepository, usageRecordRepository, billRepository, cachedCategoryConfigs, preferencesManager)
    fun billViewModel() = BillViewModel(context, cachedWallets, cachedCategoryConfigs, cachedAccountBooks, billRepository, budgetRepository, walletRepository, accountBookRepository, preferencesManager)
    fun lifeViewModel() = LifeViewModel(application, lifeTemplateRepository, lifeItemRepository, goalRepository, focusRecordRepository, lifeMomentRepository, moodDiaryRepository)
    fun backupViewModel() = BackupViewModel(context, database)
    fun billDetailViewModel() = BillDetailViewModel(billRepository)
    fun billImportViewModel() = BillImportViewModel(context, billRepository, cachedWallets)
    fun billReportViewModel() = BillReportViewModel(billRepository)
    fun searchViewModel() = SearchViewModel(assetRepository, billRepository, goalRepository, anniversaryRepository, momentRepository)
    fun categoryViewModel() = CategoryViewModel(cachedCategoryConfigs, categoryConfigRepository, preferencesManager, assetRepository, billRepository)
    fun dataClearViewModel() = DataClearViewModel(database)
    fun walletViewModel() = WalletViewModel(walletRepository, billRepository)
    fun recycleBinViewModel() = RecycleBinViewModel(assetRepository, billRepository, goalRepository, anniversaryRepository, momentRepository)
    fun anniversaryViewModel() = AnniversaryViewModel(context, lifeItemRepository, lifeTemplateRepository)
    fun birthdayViewModel() = BirthdayViewModel(context, lifeItemRepository, lifeTemplateRepository)
    fun countUpViewModel() = CountUpViewModel(context, lifeItemRepository, lifeTemplateRepository)
    fun countdownViewModel() = CountdownViewModel(context, lifeItemRepository, lifeTemplateRepository)
    fun createItemViewModel() = CreateItemViewModel(application, lifeItemRepository, lifeTemplateRepository, eventBus)
    fun lifeReportViewModel() = LifeReportViewModel(lifeReportRepository)
    fun savingPlanViewModel() = SavingPlanViewModel(context, lifeItemRepository, lifeTemplateRepository)
    fun journalViewModel() = JournalViewModel(context, legacyDao)
    fun readingPlanViewModel() = ReadingPlanViewModel(context, lifeItemRepository, lifeTemplateRepository)
    fun travelPlanViewModel() = TravelPlanViewModel(context, lifeItemRepository, lifeTemplateRepository)
    fun achievementViewModel() = AchievementViewModel(achievementRepository)
    fun studyPlanViewModel() = StudyPlanViewModel(context, lifeItemRepository, lifeTemplateRepository)
    fun habitViewModel() = HabitViewModel(context, goalRepository, goalCheckInDao)
    fun tplDispViewModel() = TplDispViewModel(lifeTemplateRepository)
    fun todoViewModel() = TodoViewModel(context, lifeItemRepository)
    fun itemDetailViewModel() = ItemDetailViewModel(lifeItemRepository, lifeTemplateRepository, crossLinkRepository, eventBus)
    fun focusViewModel() = FocusViewModel(context, focusRecordRepository)
    fun linkSelectorViewModel() = LinkSelectorViewModel(billRepository, assetRepository, crossLinkRepository)
    fun moodViewModel() = MoodViewModel(context, legacyDao)
    fun shoppingPlanViewModel() = ShoppingPlanViewModel(context, lifeItemRepository, lifeTemplateRepository)
    fun subscriptionViewModel() = SubscriptionViewModel(lifeItemRepository, lifeTemplateRepository)
    fun templateCreateViewModel() = TemplateCreateViewModel(lifeTemplateRepository)
    fun genericListViewModel() = GenericListViewModel(lifeItemRepository)
    fun settingsViewModel() = SettingsViewModel(context, preferencesManager, csvDataExporter, calendarSyncManager, assetRepository, goalRepository, momentRepository, anniversaryRepository, appLockManager)
}
