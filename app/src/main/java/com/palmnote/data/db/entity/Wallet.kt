package com.palmnote.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.palmnote.ui.theme.AppIcon

/**
 * 钱包/账户
 * 支持自定义账户类型、图标、颜色、余额统计
 */
@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String, // 账户名称：现金、微信、支付宝、银行卡等
    val type: String = "CASH", // CASH, E_WALLET, BANK_CARD, CREDIT_CARD, INVESTMENT, OTHER
    @ColumnInfo(defaultValue = "Payments")
    val icon: AppIcon = AppIcon.Payments, // 图标
    val color: String = "#4CAF50", // 颜色
    val bankName: String = "", // 银行名称（银行卡类型）
    val cardNumber: String = "", // 卡号后四位
    val initialBalance: Double = 0.0, // 初始余额
    val currentBalance: Double = 0.0, // 当前余额（自动计算）
    val currency: String = "CNY", // 币种
    val isDefault: Boolean = false, // 默认账户
    val isEnabled: Boolean = true, // 是否启用
    val sortOrder: Int = 0,
    val description: String = "",
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val typeText: String
        get() = when (type) {
            "CASH" -> "现金"
            "E_WALLET" -> "电子钱包"
            "BANK_CARD" -> "银行卡"
            "CREDIT_CARD" -> "信用卡"
            "INVESTMENT" -> "投资账户"
            "OTHER" -> "其他"
            else -> "其他"
        }

    val displayCardNumber: String
        get() = if (cardNumber.isNotEmpty()) "****$cardNumber" else ""

    val displayName: String
        get() = when {
            bankName.isNotEmpty() && cardNumber.isNotEmpty() -> "$bankName $displayCardNumber"
            else -> name
        }
}
