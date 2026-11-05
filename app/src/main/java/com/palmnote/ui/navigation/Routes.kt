package com.palmnote.ui.navigation

import kotlinx.serialization.Serializable

/** 外层 NavHost 的类型安全路由 */
@Serializable
data object MainTabs

@Serializable
data object TabDashboard

@Serializable
data object TabAsset

@Serializable
data object TabBill

@Serializable
data object TabLife

@Serializable
data class AssetDetail(val assetId: Long)

@Serializable
data class AddAsset(val assetId: Long? = null)

@Serializable
data class AddBill(val billId: Long? = null, val selectedDate: Long? = null)

@Serializable
data class BillDetail(val billId: Long)

/**
 * 预算页。[period] 是要停在哪个周期页签（`BudgetPeriod.name`），空串 = 月。
 *
 * 首页预警卡会带上预警那一条自己的周期：周预算的预警点进去却停在月页签，
 * 等于「看到超支提示、进去找不到那条预算」。
 */
@Serializable
data class Budget(val period: String = "")

@Serializable
data class Report(val selectedBookId: Long = -1L, val bookName: String = "")

/** 导入 / 导出枢纽（两张入口卡）。导入页与导出页都从这里进，返回也回到这里。 */
@Serializable
data object BillImportHub

/**
 * 导入流程页（解析 → 预览 → 结果）。
 *
 * [fileUri] 非空 = 从枢纽页「文件导入」卡直选文件进来：进页即解析，跳过 IDLE 首屏
 * （2026-11-06 定案：点卡直接导入文件，不用再进入导入页）。空 = 常规进入，先给
 * 「选择账单文件」首屏（重试/继续导入等路径仍从这里走自己的文件选择器）。
 */
@Serializable
data class BillImport(val fileUri: String? = null)

/** 导出账单流水：独立页（导入/导出枢纽页的第二张卡进入）。 */
@Serializable
data object BillExport

/** 报销管理：待报销汇总与逐笔标记。 */
@Serializable
data object Reimbursement

@Serializable
data class Category(val type: String = "ASSET")

@Serializable
data object Settings

@Serializable
data object About

@Serializable
data object PrivacyPolicy

@Serializable
data object OpenSourceLicense

@Serializable
data object VersionHistory

@Serializable
data object RecycleBin

@Serializable
data object Wallet

@Serializable
data class WalletEdit(val walletId: Long? = null)

@Serializable
data object DataClear

@Serializable
data object Search

@Serializable
data object AccountBookManage

@Serializable
data object Backup

@Serializable
data object DataExchange

@Serializable
data object GeneralSettings

/** 小票样式选择：两种纸样全尺寸展示，点选切换。 */
@Serializable
data object ReceiptStyleSettings

@Serializable
data object ReminderSettings

@Serializable
data object ManageCategory

@Serializable
data object DataStorage

@Serializable
data object AppLockSettings

@Serializable
data object WidgetSettings

@Serializable
data object Vault

@Serializable
data class VaultDetail(val entryId: Long)

@Serializable
data class VaultEdit(val entryId: Long? = null)

@Serializable
data object VaultSettings
