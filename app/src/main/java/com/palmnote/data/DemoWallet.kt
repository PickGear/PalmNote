package com.palmnote.data

/**
 * 记账 / 物品模块的演示数据定义。
 *
 * v16 起示例账单 / 物品为**类型化种子**（[demoBillSeeds] / [demoAssetSeeds]，
 * 见 WealthDemoSeed.kt）——不再经 CSV 资产与列位解析。
 *
 * ## 为什么走「打标并存」而不是互斥
 *
 * 记账动的是**用户的钱**：任何「看不到自己真实账单」的行为都是红线。
 * 所以示例账单按「打标并存」处理：
 * 与真实数据一起参与统计，账单页一眼可见、关演示即整批移除。
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
