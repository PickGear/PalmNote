package com.palmnote.ui.bills

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.palmnote.R
import com.palmnote.ui.theme.AppIcon

data class WalletPreset(
    @StringRes val nameRes: Int,
    val type: String,
    val icon: AppIcon,
    val color: Color,
    val isDefault: Boolean = false
)

val walletPresets = listOf(
    WalletPreset(R.string.wallet_type_cash, "CASH", AppIcon.Payments, Color(0xFF4CAF50), isDefault = true),
    WalletPreset(R.string.wallet_type_wechat, "E_WALLET", AppIcon.MoreHoriz, Color(0xFF07C160)),
    WalletPreset(R.string.wallet_type_alipay, "E_WALLET", AppIcon.AccountBalance, Color(0xFF1677FF)),
    WalletPreset(R.string.wallet_type_debit_card, "BANK_CARD", AppIcon.CreditCard, Color(0xFF2196F3)),
    WalletPreset(R.string.wallet_type_credit_card, "CREDIT_CARD", AppIcon.CreditCard, Color(0xFF9C27B0)),
    WalletPreset(R.string.wallet_type_investment, "INVESTMENT", AppIcon.TrendingUp, Color(0xFFFF9800)),
    WalletPreset(R.string.wallet_type_top_up, "TOP_UP", AppIcon.Payments, Color(0xFF00BCD4)),
    WalletPreset(R.string.wallet_type_other, "OTHER", AppIcon.AccountBalance, Color(0xFF9E9E9E))
)

val walletTypeResIds = mapOf(
    "CASH" to R.string.wallet_type_cash,
    "E_WALLET" to R.string.wallet_type_e_wallet,
    "BANK_CARD" to R.string.wallet_type_bank_card,
    "CREDIT_CARD" to R.string.wallet_type_credit_card,
    "INVESTMENT" to R.string.wallet_type_investment,
    "TOP_UP" to R.string.wallet_type_top_up,
    "OTHER" to R.string.wallet_type_other
)

val walletColorOptions = listOf(
    Color(0xFF4CAF50), // Green
    Color(0xFF07C160), // WeChat Green
    Color(0xFF1677FF), // Alipay Blue
    Color(0xFF2196F3), // Blue
    Color(0xFF9C27B0), // Purple
    Color(0xFFFF9800), // Orange
    Color(0xFF00BCD4), // Cyan
    Color(0xFFE91E63), // Pink
    Color(0xFF795548), // Brown
    Color(0xFF607D8B), // Blue Grey
    Color(0xFFF44336), // Red
    Color(0xFF3F51B5), // Indigo
    Color(0xFF009688), // Teal
    Color(0xFFFF5722), // Deep Orange
    Color(0xFF9E9E9E), // Grey
    Color(0xFF333333)  // Dark
)
