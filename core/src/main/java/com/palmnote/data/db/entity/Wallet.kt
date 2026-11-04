package com.palmnote.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.palmnote.ui.theme.AppIcon

/**
 * 钱包/账户
 * 支持自定义账户类型、图标、颜色、余额统计
 *
 * 余额口径（2026-11-05，issue #51）：[TYPE_CREDIT_CARD] 钱包的
 * `initialBalance`/`currentBalance` 存「欠款」，**正数 = 欠钱**；
 * 其余类型存「存款」。app 模块的记账联动（BillRepositoryImpl）
 * 与导入重算（CsvDataExporter）按该语义反向调整信用卡钱包。
 */
@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String, // 账户名称：现金、微信、支付宝、银行卡等
    val type: String = TYPE_CASH, // CASH, E_WALLET, BANK_CARD, CREDIT_CARD, INVESTMENT, OTHER
    @ColumnInfo(defaultValue = "Payments")
    val icon: AppIcon = AppIcon.Payments, // 图标
    val color: String = "#4CAF50", // 颜色
    val bankName: String = "", // 银行名称（银行卡类型）
    val cardNumber: String = "", // 卡号后四位
    val initialBalance: Long = 0, // 初始余额（分）
    val currentBalance: Long = 0, // 当前余额（分）
    val currency: String = "CNY", // 币种
    val isDefault: Boolean = false, // 默认账户
    val isEnabled: Boolean = true, // 是否启用
    val sortOrder: Int = 0,
    val description: String = "",
    /**
     * 演示数据标记（v14）：示例账本 / 示例钱包 / 示例账单 / 示例物品。
     * 演示模式关闭时按此列整批物理删除；CSV 导出排除（示例不进「我的数据」）。
     */
    @ColumnInfo(defaultValue = "0")
    val isDemo: Boolean = false,
    /**
     * 演示数据来源（v19）：示例种子 / 演示期自建 / 真实数据。
     * 见 [com.palmnote.domain.model.DemoOrigin]。
     */
    @androidx.room.ColumnInfo(defaultValue = "NONE")
    val demoOrigin: com.palmnote.domain.model.DemoOrigin = com.palmnote.domain.model.DemoOrigin.NONE,
    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_CASH = "CASH"
        const val TYPE_E_WALLET = "E_WALLET"
        const val TYPE_BANK_CARD = "BANK_CARD"
        const val TYPE_CREDIT_CARD = "CREDIT_CARD"
        const val TYPE_INVESTMENT = "INVESTMENT"
        const val TYPE_OTHER = "OTHER"
    }
}
