package com.palmnote.data

import java.time.LocalDate

/**
 * 记账 / 物品模块的演示数据定义（对应 `LifeDemoData`，同一套「真实行 + 标记」模型）。
 *
 * ## 为什么走「示例账本并存」而不是生活页那套互斥
 *
 * 生活页演示是「探索模式」——开着演示只看示例，像新用户；
 * 但记账动的是**用户的钱**：任何「看不到自己真实账单」的行为都是红线。
 * 所以示例账本按「独立账本并存」处理：
 * 与真实数据一起参与统计（净资产会包含示例），账本页一眼可见、关演示即整批移除。
 *
 * [daysAgo] 相对今天生成，保证任何一天打开演示都像「最近在记」。
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

internal data class DemoBill(
    val walletIndex: Int,
    val amount: Long,
    val type: String,
    /**
     * 内置分类的**内部键**（中文常量，与 `BillCategoryData` 的 name 一致）。
     * 不能存本地化后的显示名——那样英文环境下按键查图标/颜色会全部失效（真机截图里分类图标变红叉即此）。
     */
    val categoryKey: String,
    val merchant: String,
    val daysAgo: Int,
    val hour: Int = 12,
    val note: String = "",
    /** 小票照片（assets/demo 文件名）；空 = 无图。 */
    val receipt: String? = null
)

/**
 * 示例账单：近 30 天、覆盖吃 / 行 / 购 / 居家 / 健康 / 娱乐六类，
 * 含固定支出（房租）与收入（工资），让统计页的构成图有层次。
 */
internal val demoBills = listOf(
    DemoBill(2, 320_000, "EXPENSE", DemoBillCategoryKeys.HOUSING, "房东", 6, 10, "房租", null),
    DemoBill(2, 10_400, "EXPENSE", DemoBillCategoryKeys.FOOD, "川味小馆", 2, 19, "和朋友吃饭", "demo_receipt_dinner.jpg"),
    DemoBill(1, 4_700, "EXPENSE", DemoBillCategoryKeys.FOOD, "Manner Coffee", 1, 9, "", "demo_receipt_cafe.jpg"),
    DemoBill(0, 18_950, "EXPENSE", DemoBillCategoryKeys.SHOPPING, "山姆会员店", 3, 16, "周末囤货", "demo_receipt_market.jpg"),
    DemoBill(1, 27_700, "EXPENSE", DemoBillCategoryKeys.TRANSPORT, "中国石化", 5, 18, "加油", "demo_receipt_gas.jpg"),
    DemoBill(1, 2_600, "EXPENSE", DemoBillCategoryKeys.TRANSPORT, "杭州地铁", 1, 8, "", null),
    DemoBill(0, 3_500, "EXPENSE", DemoBillCategoryKeys.TRANSPORT, "哈啰单车", 4, 18, "", null),
    DemoBill(1, 12_800, "EXPENSE", DemoBillCategoryKeys.FOOD, "老乡鸡", 7, 12, "", null),
    DemoBill(1, 25_000, "EXPENSE", DemoBillCategoryKeys.ENTERTAINMENT, "淘票票", 8, 20, "《奥本海默》重映", null),
    DemoBill(0, 6_800, "EXPENSE", DemoBillCategoryKeys.HOUSING, "花鸟市场", 9, 11, "阳台的花", null),
    DemoBill(1, 15_600, "EXPENSE", DemoBillCategoryKeys.MEDICAL, "市口腔医院", 12, 10, "洗牙", null),
    DemoBill(2, 45_000, "EXPENSE", DemoBillCategoryKeys.EDUCATION, "人民邮电出版社", 14, 21, "Kotlin 进阶书", null),
    DemoBill(1, 8_900, "EXPENSE", DemoBillCategoryKeys.SHOPPING, "名创优品", 16, 15, "", null),
    DemoBill(0, 2_000, "EXPENSE", DemoBillCategoryKeys.FOOD, "楼下包子铺", 17, 7, "", null),
    DemoBill(1, 19_900, "EXPENSE", DemoBillCategoryKeys.ENTERTAINMENT, "网易云音乐", 20, 10, "年费续订", null),
    DemoBill(2, 128_000, "EXPENSE", DemoBillCategoryKeys.SHOPPING, "滔搏运动", 22, 14, "跑鞋", null),
    DemoBill(1, 5_600, "EXPENSE", DemoBillCategoryKeys.TRANSPORT, "滴滴出行", 25, 23, "", null),
    DemoBill(0, 5_800, "EXPENSE", DemoBillCategoryKeys.COMMUNICATION, "中国移动", 8, 9, "话费", null),
    DemoBill(3, 3_600, "EXPENSE", DemoBillCategoryKeys.FOOD, "菜市场", 27, 8, "", null),
    DemoBill(2, 2_680_000, "INCOME", DemoBillCategoryKeys.SALARY, "公司", 10, 10, "工资", null),
    DemoBill(2, 52_000, "INCOME", DemoBillCategoryKeys.INVESTMENT, "余额宝", 21, 9, "月度收益", null),
    DemoBill(1, 3_900, "INCOME", DemoBillCategoryKeys.REFUND, "名创优品", 15, 14, "退货退款", null)
)

internal data class DemoAsset(
    val name: String,
    val category: String,
    val brand: String,
    val model: String,
    val price: Long,
    val daysAgo: Int,
    val location: String,
    val room: String,
    val photo: String? = null
)

/** 示例物品：覆盖数码 / 服饰 / 乐器，带购入价与位置，物品页与「资产分布」卡都有内容。 */
internal val demoAssets = listOf(
    DemoAsset("富士 X-T5", "PHOTOGRAPHY", "富士", "X-T5", 1_199_000, 210, "家", "书房", "demo_asset_camera.jpg"),
    DemoAsset("AudioQuest NightHawk", "DIGITAL", "AudioQuest", "NightHawk Carbon", 398_000, 210, "家", "书房", "demo_asset_headphone.jpg"),
    DemoAsset("机械键盘", "DIGITAL", "", "背光 87 键", 49_900, 400, "家", "书房", "demo_asset_keyboard.jpg"),
    DemoAsset("ASICS Gel-Cumulus 22", "SPORTS", "ASICS", "Gel-Cumulus 22", 89_000, 60, "家", "玄关", "demo_asset_shoes.jpg"),
    DemoAsset("Walden D310e", "MUSICAL", "Walden", "D310e", 128_000, 520, "家", "客厅", "demo_asset_guitar.jpg"),
    DemoAsset("Kindle Paperwhite", "BOOKS", "亚马逊", "Paperwhite 5", 99_800, 700, "家", "卧室", "demo_asset_kindle.jpg"),
    DemoAsset("Levoit LV-H133", "APPLIANCE", "Levoit", "LV-H133", 179_900, 260, "家", "客厅", "demo_asset_purifier.jpg"),
    DemoAsset("瑜伽垫", "SPORTS", "", "棉质防滑", 8_900, 90, "家", "卧室", "demo_asset_yoga.jpg"),
    DemoAsset("宜家 MARKUS 椅", "FURNITURE", "宜家", "MARKUS", 79_900, 380, "家", "书房", "demo_asset_chair.jpg"),
    DemoAsset("罗技 G903 无线鼠标", "DIGITAL", "罗技", "G903 Lightspeed", 69_900, 150, "公司", "工位", "demo_asset_mouse.jpg")
)

/**
 * 示例账单的日期落点（[hour] 点）。
 *
 * **近 20 天的账单映射进「本月」**（1 日至今），更早的留在上月——原实现按"近 30 天"平铺，
 * 月初打开演示时 22 条里 19 条落到上月，首页「本月支出 / 收入」几乎是空的（真机实测：
 * 10-04 打开显示支出 ¥366.50、收入 ¥0、本月超支），演示该让当月结构可见。
 * 映射用取模压到本月已过天数：保留相对疏密，任何一天打开本月都有内容；
 * 留 4 条在上月，是为了统计页的月度趋势不至于只剩一根柱。
 */
internal fun demoBillDate(daysAgo: Int, hour: Int, today: LocalDate): Long {
    val daysIntoMonth = (today.dayOfMonth - 1).coerceAtLeast(1)
    val effectiveDaysAgo = if (daysAgo <= 20) daysAgo % daysIntoMonth else daysAgo
    return today.minusDays(effectiveDaysAgo.toLong())
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .plusHours(hour.toLong())
        .toInstant()
        .toEpochMilli()
}
