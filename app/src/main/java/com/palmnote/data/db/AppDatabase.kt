package com.palmnote.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.palmnote.data.db.converter.Converters
import com.palmnote.data.db.dao.*
import com.palmnote.data.db.entity.*

@Database(
    entities = [
        Asset::class, Bill::class, Goal::class, GoalCheckIn::class, Anniversary::class,
        Moment::class, CategoryMapping::class, UsageRecord::class, Budget::class,
        RecurringTemplate::class, CategoryConfig::class, CustomTag::class, Wallet::class,
        AccountBook::class, PlanList::class, PlanListItem::class, Plan::class,
        LifeTemplate::class, LifeItem::class, CrossLink::class, Achievement::class,
        LifeReport::class, TodoItem::class, LifeMoment::class, MoodDiary::class, FocusRecord::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun billDao(): BillDao
    abstract fun goalDao(): GoalDao
    abstract fun goalCheckInDao(): GoalCheckInDao
    abstract fun anniversaryDao(): AnniversaryDao
    abstract fun momentDao(): MomentDao
    abstract fun categoryMappingDao(): CategoryMappingDao
    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTemplateDao(): RecurringTemplateDao
    abstract fun categoryConfigDao(): CategoryConfigDao
    abstract fun customTagDao(): CustomTagDao
    abstract fun walletDao(): WalletDao
    abstract fun accountBookDao(): AccountBookDao
    abstract fun planListDao(): PlanListDao
    abstract fun planListItemDao(): PlanListItemDao
    abstract fun planDao(): PlanDao
    abstract fun lifeTemplateDao(): LifeTemplateDao
    abstract fun lifeItemDao(): LifeItemDao
    abstract fun crossLinkDao(): CrossLinkDao
    abstract fun achievementDao(): AchievementDao
    abstract fun lifeReportDao(): LifeReportDao
    abstract fun legacyDao(): LegacyDao
    abstract fun focusRecordDao(): FocusRecordDao
    abstract fun todoItemDao(): TodoItemDao
    abstract fun moodDiaryDao(): MoodDiaryDao
    abstract fun lifeMomentDao(): LifeMomentDao

    companion object {
        const val DATABASE_NAME = "palmnote_db"
    }
}
