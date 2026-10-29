package com.palmnote.data

/**
 * 记账 / 物品模块的演示数据定义。
 *
 * v11 起示例账单 / 物品来自**真实导出**（掌记_数据.zip，用户提供，2026-10-30）：
 * 以 CSV 资产随包内置（assets/demo/user_bills.csv / user_assets.csv），
 * 播种时按导出列序解析（见 [WealthDemoSeeder] 的列位映射与日期平移说明）。
 *
 * ## 为什么走「打标并存」而不是互斥
 *
 * 记账动的是**用户的钱**：任何「看不到自己真实账单」的行为都是红线。
 * 所以示例账单按「打标并存」处理：
 * 与真实数据一起参与统计，账单页一眼可见、关演示即整批移除。
 *
 * 示例钱包保留原有定义：导出里没有钱包表，账单引用的钱包（1/2/3）按固定映射
 * 回到示例钱包（1→现金 2→微信零钱 3→支付宝，与真实截图的支付渠道一致）。
 */
internal data class DemoWallet(
    val name: String,
    val type: String,
    /** 当前余额（分）。 */
    val balance: Long,
    val color: String,
    val bankName: String = "",
    val cardNumber: String = ""
)

/** 示例钱包：覆盖现金 / 电子钱包 / 银行卡三类，让净资产卡有结构可看。 */
internal val demoWallets = listOf(
    DemoWallet("微信零钱", "E_WALLET", 128_650, "#07C160"),
    DemoWallet("支付宝", "E_WALLET", 364_200, "#1677FF"),
    DemoWallet("招商银行储蓄卡", "BANK_CARD", 2_845_000, "#C62828", bankName = "招商银行", cardNumber = "6214"),
    DemoWallet("现金", "CASH", 42_000, "#8D6E63")
)

/** 导出里的钱包 id（1/2/3）→ 示例钱包下标（现金 / 微信零钱 / 支付宝）。 */
internal fun walletIndexById(walletId: Long): Int = when (walletId) {
    1L -> 3
    2L -> 0
    else -> 1
}
