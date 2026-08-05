package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.palmnote.data.db.EncryptedOpenHelperFactory
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * v5 → v6：金额表重建 + 软删除列迁移 + 密码本表迁移。
 *
 * 密码本在 v5 时位于主库 `vault_entries`，v6 起迁移到独立库 `palmnote_vault.db`。
 * 迁移时会先把主库残留的旧密码本数据搬运到独立库（best-effort，任何失败都回退为仅删除），
 * 再删除主库旧表以满足 Room schema 校验——避免静默丢失 v5 阶段的数据。
 *
 * 测试环境（无 vault 路径/密钥）构造 [MIGRATION_5_6] 走仅删除分支。
 */
class Migration5To6(
    private val vaultDbPath: String?,
    private val vaultKey: ByteArray?
) : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {


        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_assets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `subCategory` TEXT NOT NULL, `brand` TEXT NOT NULL, `model` TEXT NOT NULL, `purchasePrice` INTEGER NOT NULL, `acquisitionType` TEXT NOT NULL, `acquisitionDate` INTEGER, `status` TEXT NOT NULL, `costMode` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `useCount` INTEGER NOT NULL, `totalUsageHours` REAL NOT NULL, `location` TEXT NOT NULL, `room` TEXT NOT NULL, `purchaseChannel` TEXT NOT NULL, `warrantyExpireDate` INTEGER, `insuranceExpireDate` INTEGER, `insuranceCompany` TEXT NOT NULL, `insurancePolicyNo` TEXT NOT NULL, `images` TEXT NOT NULL, `description` TEXT NOT NULL, `condition` TEXT NOT NULL, `serialNumber` TEXT NOT NULL, `receiptPath` TEXT NOT NULL, `depreciationRate` REAL NOT NULL, `currentValue` INTEGER NOT NULL, `maintenanceIntervalDays` INTEGER NOT NULL, `lastMaintenanceDate` INTEGER, `nextMaintenanceDate` INTEGER, `maintenanceNotes` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `tags` TEXT NOT NULL, `linkedBillId` INTEGER, `linkedMomentId` INTEGER, `retireDate` INTEGER, `retireReason` TEXT NOT NULL, `lostDate` INTEGER, `lostReason` TEXT NOT NULL, `soldDate` INTEGER, `soldPrice` INTEGER, `soldChannel` TEXT, `soldToWhom` TEXT, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_assets (`id`, `name`, `category`, `subCategory`, `brand`, `model`, `purchasePrice`, `acquisitionType`, `acquisitionDate`, `status`, `costMode`, `quantity`, `useCount`, `totalUsageHours`, `location`, `room`, `purchaseChannel`, `warrantyExpireDate`, `insuranceExpireDate`, `insuranceCompany`, `insurancePolicyNo`, `images`, `description`, `condition`, `serialNumber`, `receiptPath`, `depreciationRate`, `currentValue`, `maintenanceIntervalDays`, `lastMaintenanceDate`, `nextMaintenanceDate`, `maintenanceNotes`, `isFavorite`, `tags`, `linkedBillId`, `linkedMomentId`, `retireDate`, `retireReason`, `lostDate`, `lostReason`, `soldDate`, `soldPrice`, `soldChannel`, `soldToWhom`, `sortOrder`, `createdAt`, `updatedAt`) SELECT `id`, `name`, `category`, `subCategory`, `brand`, `model`, `purchasePrice`, `acquisitionType`, `acquisitionDate`, `status`, `costMode`, `quantity`, `useCount`, `totalUsageHours`, `location`, `room`, `purchaseChannel`, `warrantyExpireDate`, `insuranceExpireDate`, `insuranceCompany`, `insurancePolicyNo`, `images`, `description`, `condition`, `serialNumber`, `receiptPath`, `depreciationRate`, `currentValue`, `maintenanceIntervalDays`, `lastMaintenanceDate`, `nextMaintenanceDate`, `maintenanceNotes`, `isFavorite`, `tags`, `linkedBillId`, `linkedMomentId`, `retireDate`, `retireReason`, `lostDate`, `lostReason`, `soldDate`, `soldPrice`, `soldChannel`, `soldToWhom`, `sortOrder`, `createdAt`, `updatedAt` FROM assets""")
        db.execSQL("""DROP TABLE assets""")
        db.execSQL("""ALTER TABLE new_assets RENAME TO assets""")
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_assets_status` ON `assets` (`status`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_assets_category` ON `assets` (`category`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_assets_warrantyExpireDate` ON `assets` (`warrantyExpireDate`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_assets_nextMaintenanceDate` ON `assets` (`nextMaintenanceDate`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_assets_insuranceExpireDate` ON `assets` (`insuranceExpireDate`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_assets_isFavorite` ON `assets` (`isFavorite`)
            """
        )

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_bills` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` INTEGER NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `subCategory` TEXT NOT NULL, `note` TEXT NOT NULL, `date` INTEGER NOT NULL, `yearMonth` TEXT NOT NULL, `timeOfDay` TEXT NOT NULL, `accountBookId` INTEGER NOT NULL, `walletId` INTEGER, `toWalletId` INTEGER, `paymentMethod` TEXT NOT NULL, `merchant` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `location` TEXT NOT NULL, `tags` TEXT NOT NULL, `images` TEXT NOT NULL, `linkedAssetId` INTEGER, `linkType` TEXT, `recurringId` INTEGER, `recurringFrequency` TEXT NOT NULL, `splitGroupId` TEXT NOT NULL, `isReimbursable` INTEGER NOT NULL, `isReimbursed` INTEGER NOT NULL, `reimbursedDate` INTEGER, `isTaxDeductible` INTEGER NOT NULL, `latitude` REAL, `longitude` REAL, `isAutoGenerated` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_bills (`id`, `amount`, `type`, `category`, `subCategory`, `note`, `date`, `yearMonth`, `timeOfDay`, `accountBookId`, `walletId`, `toWalletId`, `paymentMethod`, `merchant`, `transactionId`, `location`, `tags`, `images`, `linkedAssetId`, `linkType`, `recurringId`, `recurringFrequency`, `splitGroupId`, `isReimbursable`, `isReimbursed`, `reimbursedDate`, `isTaxDeductible`, `latitude`, `longitude`, `isAutoGenerated`, `createdAt`, `updatedAt`) SELECT `id`, `amount`, `type`, `category`, `subCategory`, `note`, `date`, `yearMonth`, `timeOfDay`, `accountBookId`, `walletId`, `toWalletId`, `paymentMethod`, `merchant`, `transactionId`, `location`, `tags`, `images`, `linkedAssetId`, `linkType`, `recurringId`, `recurringFrequency`, `splitGroupId`, `isReimbursable`, `isReimbursed`, `reimbursedDate`, `isTaxDeductible`, `latitude`, `longitude`, `isAutoGenerated`, `createdAt`, `updatedAt` FROM bills""")
        db.execSQL("""DROP TABLE bills""")
        db.execSQL("""ALTER TABLE new_bills RENAME TO bills""")
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_bills_yearMonth_type` ON `bills` (`yearMonth`, `type`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_bills_accountBookId_yearMonth` ON `bills` (`accountBookId`, `yearMonth`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_bills_type` ON `bills` (`type`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_bills_date` ON `bills` (`date`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_bills_isReimbursable_isReimbursed` ON `bills` (`isReimbursable`, `isReimbursed`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_bills_recurringId` ON `bills` (`recurringId`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `index_bills_category` ON `bills` (`category`)
            """
        )

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `goalType` TEXT NOT NULL, `totalCount` INTEGER NOT NULL, `currentCount` INTEGER NOT NULL, `unit` TEXT NOT NULL, `frequency` TEXT NOT NULL, `targetPerPeriod` INTEGER NOT NULL, `currentPeriodCount` INTEGER NOT NULL, `periodStartDate` INTEGER, `deadline` INTEGER, `startDate` INTEGER NOT NULL, `priority` TEXT NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL DEFAULT 'Flag', `streak` INTEGER NOT NULL, `longestStreak` INTEGER NOT NULL, `lastCheckInDate` INTEGER, `totalCheckInDays` INTEGER NOT NULL, `currentPeriodStart` INTEGER NOT NULL, `currentPeriodEnd` INTEGER NOT NULL, `direction` TEXT NOT NULL, `initialValue` INTEGER NOT NULL, `reminderEnabled` INTEGER NOT NULL, `reminderTime` TEXT NOT NULL, `linkedAssetId` INTEGER, `isPublic` INTEGER NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_goals (`id`, `title`, `description`, `category`, `goalType`, `totalCount`, `currentCount`, `unit`, `frequency`, `targetPerPeriod`, `currentPeriodCount`, `periodStartDate`, `deadline`, `startDate`, `priority`, `color`, `icon`, `streak`, `longestStreak`, `lastCheckInDate`, `totalCheckInDays`, `currentPeriodStart`, `currentPeriodEnd`, `direction`, `initialValue`, `reminderEnabled`, `reminderTime`, `linkedAssetId`, `isPublic`, `notes`, `createdAt`, `updatedAt`) SELECT `id`, `title`, `description`, `category`, `goalType`, `totalCount`, `currentCount`, `unit`, `frequency`, `targetPerPeriod`, `currentPeriodCount`, `periodStartDate`, `deadline`, `startDate`, `priority`, `color`, `icon`, `streak`, `longestStreak`, `lastCheckInDate`, `totalCheckInDays`, `currentPeriodStart`, `currentPeriodEnd`, `direction`, `initialValue`, `reminderEnabled`, `reminderTime`, `linkedAssetId`, `isPublic`, `notes`, `createdAt`, `updatedAt` FROM goals""")
        db.execSQL("""DROP TABLE goals""")
        db.execSQL("""ALTER TABLE new_goals RENAME TO goals""")
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_goal_category` ON `goals` (`category`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_goal_deadline` ON `goals` (`deadline`)
            """
        )

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_anniversaries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `solarDate` INTEGER NOT NULL, `isLunar` INTEGER NOT NULL, `lunarYear` INTEGER, `lunarMonth` INTEGER, `lunarDay` INTEGER, `lunarLeapMonth` INTEGER NOT NULL, `type` TEXT NOT NULL, `personName` TEXT NOT NULL, `personRelation` TEXT NOT NULL, `isYearly` INTEGER NOT NULL, `displayMode` TEXT NOT NULL, `multiRemindJson` TEXT NOT NULL, `reminderTime` TEXT NOT NULL, `notificationEnabled` INTEGER NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL DEFAULT 'Favorite', `emoji` TEXT NOT NULL DEFAULT '', `linkedMomentId` INTEGER, `isPinned` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_anniversaries (`id`, `title`, `description`, `solarDate`, `isLunar`, `lunarYear`, `lunarMonth`, `lunarDay`, `lunarLeapMonth`, `type`, `personName`, `personRelation`, `isYearly`, `displayMode`, `multiRemindJson`, `reminderTime`, `notificationEnabled`, `color`, `icon`, `emoji`, `linkedMomentId`, `isPinned`, `createdAt`, `updatedAt`) SELECT `id`, `title`, `description`, `solarDate`, `isLunar`, `lunarYear`, `lunarMonth`, `lunarDay`, `lunarLeapMonth`, `type`, `personName`, `personRelation`, `isYearly`, `displayMode`, `multiRemindJson`, `reminderTime`, `notificationEnabled`, `color`, `icon`, `emoji`, `linkedMomentId`, `isPinned`, `createdAt`, `updatedAt` FROM anniversaries""")
        db.execSQL("""DROP TABLE anniversaries""")
        db.execSQL("""ALTER TABLE new_anniversaries RENAME TO anniversaries""")

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_moments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `images` TEXT NOT NULL, `videoPath` TEXT NOT NULL, `audioPath` TEXT NOT NULL, `mood` TEXT NOT NULL, `weather` TEXT NOT NULL, `temperature` INTEGER NOT NULL, `location` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `tags` TEXT NOT NULL, `category` TEXT NOT NULL, `privacy` TEXT NOT NULL, `isPinned` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `wordCount` INTEGER NOT NULL, `linkedAssetId` INTEGER, `linkedAnniversaryId` INTEGER, `linkedGoalId` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_moments (`id`, `title`, `content`, `timestamp`, `images`, `videoPath`, `audioPath`, `mood`, `weather`, `temperature`, `location`, `latitude`, `longitude`, `tags`, `category`, `privacy`, `isPinned`, `isFavorite`, `wordCount`, `linkedAssetId`, `linkedAnniversaryId`, `linkedGoalId`, `createdAt`, `updatedAt`) SELECT `id`, `title`, `content`, `timestamp`, `images`, `videoPath`, `audioPath`, `mood`, `weather`, `temperature`, `location`, `latitude`, `longitude`, `tags`, `category`, `privacy`, `isPinned`, `isFavorite`, `wordCount`, `linkedAssetId`, `linkedAnniversaryId`, `linkedGoalId`, `createdAt`, `updatedAt` FROM moments""")
        db.execSQL("""DROP TABLE moments""")
        db.execSQL("""ALTER TABLE new_moments RENAME TO moments""")

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_account_books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL DEFAULT 'MenuBook', `color` TEXT NOT NULL, `description` TEXT NOT NULL, `bookType` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, `isAllBooks` INTEGER NOT NULL, `isHidden` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_account_books (`id`, `name`, `icon`, `color`, `description`, `bookType`, `sortOrder`, `isDefault`, `isAllBooks`, `isHidden`, `createdAt`, `updatedAt`) SELECT `id`, `name`, `icon`, `color`, `description`, `bookType`, `sortOrder`, `isDefault`, `isAllBooks`, `isHidden`, `createdAt`, `updatedAt` FROM account_books""")
        db.execSQL("""DROP TABLE account_books""")
        db.execSQL("""ALTER TABLE new_account_books RENAME TO account_books""")

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_plan_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `emoji` TEXT NOT NULL DEFAULT 'Assignment', `description` TEXT NOT NULL, `dueDate` INTEGER, `template` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_plan_lists (`id`, `title`, `emoji`, `description`, `dueDate`, `template`, `isCompleted`, `sortOrder`, `createdAt`, `updatedAt`) SELECT `id`, `title`, `emoji`, `description`, `dueDate`, `template`, `isCompleted`, `sortOrder`, `createdAt`, `updatedAt` FROM plan_lists""")
        db.execSQL("""DROP TABLE plan_lists""")
        db.execSQL("""ALTER TABLE new_plan_lists RENAME TO plan_lists""")

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL DEFAULT 'Flag', `category` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_plans (`id`, `name`, `icon`, `category`, `sortOrder`, `createdAt`, `updatedAt`) SELECT `id`, `name`, `icon`, `category`, `sortOrder`, `createdAt`, `updatedAt` FROM plans""")
        db.execSQL("""DROP TABLE plans""")
        db.execSQL("""ALTER TABLE new_plans RENAME TO plans""")

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_life_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` TEXT NOT NULL, `description` TEXT NOT NULL, `fieldsConfig` TEXT NOT NULL, `layoutType` TEXT NOT NULL, `availableLayouts` TEXT NOT NULL, `statusFlowConfig` TEXT NOT NULL, `linkConfig` TEXT NOT NULL, `isBuiltin` INTEGER NOT NULL, `isHidden` INTEGER NOT NULL, `isSpecial` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_life_templates (`id`, `name`, `category`, `icon`, `color`, `description`, `fieldsConfig`, `layoutType`, `availableLayouts`, `statusFlowConfig`, `linkConfig`, `isBuiltin`, `isHidden`, `isSpecial`, `sortOrder`, `createdAt`, `updatedAt`) SELECT `id`, `name`, `category`, `icon`, `color`, `description`, `fieldsConfig`, `layoutType`, `availableLayouts`, `statusFlowConfig`, `linkConfig`, `isBuiltin`, `isHidden`, `isSpecial`, `sortOrder`, `createdAt`, `updatedAt` FROM life_templates""")
        db.execSQL("""DROP TABLE life_templates""")
        db.execSQL("""ALTER TABLE new_life_templates RENAME TO life_templates""")
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_template_category` ON `life_templates` (`category`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_template_visible` ON `life_templates` (`isHidden`)
            """
        )

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_life_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateId` INTEGER NOT NULL, `title` TEXT NOT NULL, `fieldsData` TEXT NOT NULL, `status` TEXT NOT NULL, `note` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_life_items (`id`, `templateId`, `title`, `fieldsData`, `status`, `note`, `sortOrder`, `isFavorite`, `createdAt`, `updatedAt`) SELECT `id`, `templateId`, `title`, `fieldsData`, `status`, `note`, `sortOrder`, `isFavorite`, `createdAt`, `updatedAt` FROM life_items""")
        db.execSQL("""DROP TABLE life_items""")
        db.execSQL("""ALTER TABLE new_life_items RENAME TO life_items""")
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_items_template_status` ON `life_items` (`templateId`, `status`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_items_template` ON `life_items` (`templateId`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_items_created` ON `life_items` (`createdAt`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_items_status` ON `life_items` (`status`)
            """
        )

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_todo_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `dueDate` INTEGER, `priority` TEXT NOT NULL, `category` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `planId` INTEGER, `parentId` INTEGER, `attachments` TEXT NOT NULL, `recurring` TEXT NOT NULL, `recurringEndType` TEXT NOT NULL, `recurringEndCount` INTEGER NOT NULL, `recurringEndDate` INTEGER, `lifeItemId` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_todo_items (`id`, `title`, `isCompleted`, `dueDate`, `priority`, `category`, `sortOrder`, `planId`, `parentId`, `attachments`, `recurring`, `recurringEndType`, `recurringEndCount`, `recurringEndDate`, `lifeItemId`, `createdAt`, `updatedAt`) SELECT `id`, `title`, `isCompleted`, `dueDate`, `priority`, `category`, `sortOrder`, `planId`, `parentId`, `attachments`, `recurring`, `recurringEndType`, `recurringEndCount`, `recurringEndDate`, `lifeItemId`, `createdAt`, `updatedAt` FROM todo_items""")
        db.execSQL("""DROP TABLE todo_items""")
        db.execSQL("""ALTER TABLE new_todo_items RENAME TO todo_items""")
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_todo_due` ON `todo_items` (`dueDate`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_todo_plan` ON `todo_items` (`planId`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_todo_life_item` ON `todo_items` (`lifeItemId`)
            """
        )

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_life_moments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `imageUri` TEXT, `date` INTEGER NOT NULL, `tags` TEXT NOT NULL, `mood` TEXT, `locationName` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `weather` TEXT NOT NULL, `temperature` INTEGER NOT NULL, `isMarkdown` INTEGER NOT NULL, `category` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `lifeItemId` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_life_moments (`id`, `title`, `content`, `imageUri`, `date`, `tags`, `mood`, `locationName`, `latitude`, `longitude`, `weather`, `temperature`, `isMarkdown`, `category`, `isFavorite`, `lifeItemId`, `createdAt`, `updatedAt`) SELECT `id`, `title`, `content`, `imageUri`, `date`, `tags`, `mood`, `locationName`, `latitude`, `longitude`, `weather`, `temperature`, `isMarkdown`, `category`, `isFavorite`, `lifeItemId`, `createdAt`, `updatedAt` FROM life_moments""")
        db.execSQL("""DROP TABLE life_moments""")
        db.execSQL("""ALTER TABLE new_life_moments RENAME TO life_moments""")
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_moment_date` ON `life_moments` (`date`)
            """
        )
        db.execSQL(
            """
CREATE INDEX IF NOT EXISTS `idx_moment_life_item` ON `life_moments` (`lifeItemId`)
            """
        )

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_recurring_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amount` INTEGER NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `frequency` TEXT NOT NULL, `dayOfMonth` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `lastGeneratedDate` INTEGER, `nextGenerateDate` INTEGER, `autoGenerate` INTEGER NOT NULL, `remindBeforeDays` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_recurring_templates (`id`, `name`, `amount`, `type`, `category`, `frequency`, `dayOfMonth`, `dayOfWeek`, `isActive`, `lastGeneratedDate`, `nextGenerateDate`, `autoGenerate`, `remindBeforeDays`, `note`, `createdAt`, `updatedAt`) SELECT `id`, `name`, `amount`, `type`, `category`, `frequency`, `dayOfMonth`, `dayOfWeek`, `isActive`, `lastGeneratedDate`, `nextGenerateDate`, `autoGenerate`, `remindBeforeDays`, `note`, `createdAt`, `updatedAt` FROM recurring_templates""")
        db.execSQL("""DROP TABLE recurring_templates""")
        db.execSQL("""ALTER TABLE new_recurring_templates RENAME TO recurring_templates""")

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_custom_tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `icon` TEXT NOT NULL DEFAULT 'Flag', `usageCount` INTEGER NOT NULL, `applicableTypes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_custom_tags (`id`, `name`, `color`, `icon`, `usageCount`, `applicableTypes`, `createdAt`, `updatedAt`) SELECT `id`, `name`, `color`, `icon`, `usageCount`, `applicableTypes`, `createdAt`, `updatedAt` FROM custom_tags""")
        db.execSQL("""DROP TABLE custom_tags""")
        db.execSQL("""ALTER TABLE new_custom_tags RENAME TO custom_tags""")

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `new_wallets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `icon` TEXT NOT NULL DEFAULT 'Payments', `color` TEXT NOT NULL, `bankName` TEXT NOT NULL, `cardNumber` TEXT NOT NULL, `initialBalance` INTEGER NOT NULL, `currentBalance` INTEGER NOT NULL, `currency` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """
        )
        db.execSQL("""INSERT INTO new_wallets (`id`, `name`, `type`, `icon`, `color`, `bankName`, `cardNumber`, `initialBalance`, `currentBalance`, `currency`, `isDefault`, `isEnabled`, `sortOrder`, `description`, `createdAt`, `updatedAt`) SELECT `id`, `name`, `type`, `icon`, `color`, `bankName`, `cardNumber`, `initialBalance`, `currentBalance`, `currency`, `isDefault`, `isEnabled`, `sortOrder`, `description`, `createdAt`, `updatedAt` FROM wallets""")
        db.execSQL("""DROP TABLE wallets""")
        db.execSQL("""ALTER TABLE new_wallets RENAME TO wallets""")

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `bills_recycle_bin` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `originalId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `subCategory` TEXT NOT NULL, `note` TEXT NOT NULL, `date` INTEGER NOT NULL, `yearMonth` TEXT NOT NULL, `timeOfDay` TEXT NOT NULL, `accountBookId` INTEGER NOT NULL, `walletId` INTEGER, `toWalletId` INTEGER, `paymentMethod` TEXT NOT NULL, `merchant` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `location` TEXT NOT NULL, `tags` TEXT NOT NULL, `images` TEXT NOT NULL, `linkedAssetId` INTEGER, `linkType` TEXT, `recurringId` INTEGER, `recurringFrequency` TEXT NOT NULL, `splitGroupId` TEXT NOT NULL, `isReimbursable` INTEGER NOT NULL, `isReimbursed` INTEGER NOT NULL, `reimbursedDate` INTEGER, `isTaxDeductible` INTEGER NOT NULL, `latitude` REAL, `longitude` REAL, `isAutoGenerated` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER NOT NULL)
            """
        )

        db.execSQL(
            """
CREATE TABLE IF NOT EXISTS `assets_recycle_bin` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `originalId` INTEGER NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `subCategory` TEXT NOT NULL, `brand` TEXT NOT NULL, `model` TEXT NOT NULL, `purchasePrice` INTEGER NOT NULL, `acquisitionType` TEXT NOT NULL, `acquisitionDate` INTEGER, `status` TEXT NOT NULL, `costMode` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `useCount` INTEGER NOT NULL, `totalUsageHours` REAL NOT NULL, `location` TEXT NOT NULL, `room` TEXT NOT NULL, `purchaseChannel` TEXT NOT NULL, `warrantyExpireDate` INTEGER, `insuranceExpireDate` INTEGER, `insuranceCompany` TEXT NOT NULL, `insurancePolicyNo` TEXT NOT NULL, `images` TEXT NOT NULL, `description` TEXT NOT NULL, `condition` TEXT NOT NULL, `serialNumber` TEXT NOT NULL, `receiptPath` TEXT NOT NULL, `depreciationRate` REAL NOT NULL, `currentValue` INTEGER NOT NULL, `maintenanceIntervalDays` INTEGER NOT NULL, `lastMaintenanceDate` INTEGER, `nextMaintenanceDate` INTEGER, `maintenanceNotes` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `tags` TEXT NOT NULL, `linkedBillId` INTEGER, `linkedMomentId` INTEGER, `retireDate` INTEGER, `retireReason` TEXT NOT NULL, `lostDate` INTEGER, `lostReason` TEXT NOT NULL, `soldDate` INTEGER, `soldPrice` INTEGER, `soldChannel` TEXT, `soldToWhom` TEXT, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER NOT NULL)
            """
        )

        preserveLegacyVault(db)

        db.execSQL(
            """
CREATE TRIGGER IF NOT EXISTS auto_yearmonth_insert AFTER INSERT ON bills
BEGIN
    UPDATE bills SET yearMonth = strftime('%Y-%m', datetime(NEW.date / 1000, 'unixepoch', 'localtime'))
    WHERE id = NEW.id;
END
            """
        )
        db.execSQL(
            """
CREATE TRIGGER IF NOT EXISTS auto_yearmonth_update AFTER UPDATE OF date ON bills
BEGIN
    UPDATE bills SET yearMonth = strftime('%Y-%m', datetime(NEW.date / 1000, 'unixepoch', 'localtime'))
    WHERE id = NEW.id;
END
            """
        )
        db.execSQL(
            """
CREATE TRIGGER IF NOT EXISTS bills_fts_ai AFTER INSERT ON bills BEGIN
    INSERT INTO bills_fts(rowid, note, merchant, tags)
    VALUES (new.id, new.note, new.merchant, new.tags);
END
            """
        )
        db.execSQL(
            """
CREATE TRIGGER IF NOT EXISTS bills_fts_ad AFTER DELETE ON bills BEGIN
    INSERT INTO bills_fts(bills_fts, rowid, note, merchant, tags)
    VALUES ('delete', old.id, old.note, old.merchant, old.tags);
END
            """
        )
        db.execSQL(
            """
CREATE TRIGGER IF NOT EXISTS bills_fts_au AFTER UPDATE ON bills BEGIN
    INSERT INTO bills_fts(bills_fts, rowid, note, merchant, tags)
    VALUES ('delete', old.id, old.note, old.merchant, old.tags);
    INSERT INTO bills_fts(rowid, note, merchant, tags)
    VALUES (new.id, new.note, new.merchant, new.tags);
END
            """
        )

    }

    /**
     * 主库旧密码本数据 → 独立库 best-effort 搬运，然后无条件删除主库旧表（Room schema 校验要求）。
     * 任何异常（如测试环境无 SQLCipher native 库）都回退为仅删除，绝不阻塞迁移。
     */
    private fun preserveLegacyVault(db: SupportSQLiteDatabase) {
        val path = vaultDbPath
        val key = vaultKey
        try {
            if (path != null && key != null) {
                var hasRows = false
                db.query("SELECT COUNT(*) FROM vault_entries").use { c ->
                    if (c.moveToFirst()) hasRows = c.getInt(0) > 0
                }
                if (hasRows) {
                    EncryptedOpenHelperFactory.ensureLibraryLoaded()
                    SQLiteDatabase.openOrCreateDatabase(path, key, null, null).use { vault ->
                        vault.execSQL(
                            """CREATE TABLE IF NOT EXISTS vault_entries (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                title TEXT NOT NULL, username TEXT NOT NULL, passwordEncrypted BLOB NOT NULL,
                                url TEXT NOT NULL, notes TEXT NOT NULL, category TEXT NOT NULL,
                                createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                            )"""
                        )
                        // 目标库已有条目（迁移曾部分执行后重试/崩溃恢复）则跳过，避免重复插入
                        var vaultHasRows = false
                        vault.query("SELECT COUNT(*) FROM vault_entries").use { vc ->
                            if (vc.moveToFirst()) vaultHasRows = vc.getInt(0) > 0
                        }
                        if (vaultHasRows) return@use
                        val stmt = vault.compileStatement(
                            "INSERT INTO vault_entries (title, username, passwordEncrypted, url, notes, category, createdAt, updatedAt) VALUES (?,?,?,?,?,?,?,?)"
                        )
                        db.query("SELECT title, username, passwordEncrypted, url, notes, category, createdAt, updatedAt FROM vault_entries").use { c ->
                            while (c.moveToNext()) {
                                stmt.clearBindings()
                                stmt.bindString(1, c.getString(0))
                                stmt.bindString(2, c.getString(1))
                                stmt.bindBlob(3, c.getBlob(2))
                                stmt.bindString(4, c.getString(3))
                                stmt.bindString(5, c.getString(4))
                                stmt.bindString(6, c.getString(5))
                                stmt.bindLong(7, c.getLong(6))
                                stmt.bindLong(8, c.getLong(7))
                                stmt.execute()
                            }
                        }
                        stmt.close()
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("Migration5To6", "legacy vault copy failed, legacy data dropped", t)
        }
        db.execSQL("DROP TABLE IF EXISTS vault_entries")
    }
}

/** 测试/兜底实例：无 vault 路径与密钥时仅删除旧表。 */
val MIGRATION_5_6: Migration = Migration5To6(null, null)
