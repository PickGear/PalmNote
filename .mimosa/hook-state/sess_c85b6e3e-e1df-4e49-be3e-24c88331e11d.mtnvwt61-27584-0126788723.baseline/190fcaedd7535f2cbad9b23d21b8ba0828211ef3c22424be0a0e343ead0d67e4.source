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

@Serializable
data object Budget

@Serializable
data class Report(val selectedBookId: Long = -1L, val bookName: String = "")

@Serializable
data object BillImport

@Serializable
data class Category(val type: String = "ASSET")

@Serializable
data object Settings

@Serializable
data object About

@Serializable
data object PrivacyPolicy

@Serializable
data object TermsOfService

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
data object GeneralSettings

@Serializable
data object ReminderSettings

@Serializable
data object ManageCategory

@Serializable
data object DataStorage

@Serializable
data object AppLockSettings

@Serializable
data object Vault

@Serializable
data class VaultDetail(val entryId: Long)

@Serializable
data class VaultEdit(val entryId: Long? = null)

@Serializable
data object VaultSettings
