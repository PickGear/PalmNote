package com.palmnote.data

/**
 * 记账 / 物品模块的演示种子数据（类型化对象图，编译期检查）。
 *
 * ## 为什么不用 CSV 资产
 * v15 及以前种子以「导出格式 CSV」随包内置、播种时按列位解析——列位错一格就整批
 * 静默跳过（v12 实翻过车：购买日期读到获取方式，物品页全空），且格式与导出器互为耦合。
 * 改为 Kotlin 对象后字段拼写错误即编译失败，解析器整体消失。
 *
 * ## 日期为什么用相对锚点
 * 账单的 `daysAgo + 时刻`、物品/保修/保质期的 `daysAgo / inDays` 都在**播种那一刻**
 * 才换算成绝对时间——无论哪天开演示，示例都落在「最近」，不再出现放置久了
 * 示例日期过期的问题（演示交接文档记录的「静态快照」取舍就此销账）。
 *
 * ## 内容口径
 * 一眼假的中性样例：通用物品名（与随包示例图自洽）、虚构商户、无序列号、圆整金额。
 * 分类键必须是内置键（与 `BillCategoryData` / `assetCategoryItems` 的 name 一致），
 * 由 `DemoDataIntegrityTest` 守卫。
 */

/** 演示账单种子。 */
internal data class DemoBillSeed(
    /** 距今天数（0 = 今天）。 */
    val daysAgo: Int,
    val hour: Int,
    val minute: Int,
    /** 金额（分）。 */
    val amount: Long,
    /** EXPENSE / INCOME / TRANSFER。 */
    val type: String,
    /** 分类内部键；转账行为空串。 */
    val category: String,
    /** [demoWallets] 下标。 */
    val walletIndex: Int,
    /** 转账目标钱包下标。 */
    val toWalletIndex: Int? = null,
    val merchant: String = "",
    val location: String = "",
    val note: String = "",
    val isReimbursable: Boolean = false
)

/**
 * 种子日期 → 实际落库日期：`today - daysAgo`，但**钳进本月**（早于 1 日的压回月初起算）。
 *
 * 预算预警按自然月取数：示例账单必须全部落在**本月**，否则月初开演示时大半账单
 * 划进上个月，¥3,000 预算的 80% 预警卡没戏可演（旧数据全部挤在 8 天窗口里，天然满足）。
 * 月初几天多笔并存是可接受的演示折衷；时刻（hour/minute）不受影响。
 */
internal fun DemoBillSeed.resolveDate(today: java.time.LocalDate): java.time.LocalDate =
    today.withDayOfMonth((today.dayOfMonth - daysAgo).coerceAtLeast(1))

/** 演示物品种子。保修 / 保质期用相对天数锚点，播种时换算为绝对日期。 */
internal data class DemoAssetSeed(
    val name: String,
    val category: String,
    /** 购入价（分）。 */
    val price: Long,
    /** 购入距今天数（日均成本与「已使用天数」的锚点）。 */
    val acquiredDaysAgo: Long,
    /** assets/demo/ 下的语义化文件名；空 = 详情页落分类色占位。 */
    val images: List<String> = emptyList(),
    val acquisitionType: String = "PURCHASE",
    val brand: String = "",
    val model: String = "",
    val location: String = "",
    val room: String = "",
    val channel: String = "",
    val description: String = "",
    /** 成色：NEW / GOOD。 */
    val condition: String = "GOOD",
    /** 保修到期（距今天数）。 */
    val warrantyExpireInDays: Long? = null,
    /** 保质期录法一：直接给到期日（距今天数）。 */
    val shelfLifeExpireInDays: Long? = null,
    /** 保质期录法二：生产日期（距今天数）+ 时长。 */
    val shelfLifeProducedDaysAgo: Long? = null,
    val shelfLifeDurationValue: Int? = null,
    val shelfLifeDurationUnit: String? = null
)

/**
 * 示例账单：22 笔中性样例，覆盖最近两周。
 *
 * 结构性覆盖照旧——转账（支付宝 → 微信零钱）、可报销（出差打车）、三类收入
 * （工资 / 兼职 / 理财）、四个钱包都有戏份；大额房租 + 工资让净资产与预算预警有戏可演。
 */
internal val demoBillSeeds = listOf(
    // 最近三天
    DemoBillSeed(0, 8, 10, 600, "EXPENSE", "餐饮", 3, merchant = "早餐铺"),
    DemoBillSeed(0, 12, 20, 2300, "EXPENSE", "餐饮", 0, merchant = "快餐店"),
    DemoBillSeed(1, 9, 5, 400, "EXPENSE", "交通", 0, merchant = "公交"),
    DemoBillSeed(1, 15, 30, 1200, "EXPENSE", "饮品", 0, merchant = "奶茶店"),
    DemoBillSeed(2, 19, 40, 5800, "EXPENSE", "社交", 1, merchant = "朋友聚餐", location = "商圈"),
    DemoBillSeed(3, 10, 15, 4500, "EXPENSE", "购物", 0, merchant = "日用超市"),
    DemoBillSeed(3, 21, 5, 1500, "EXPENSE", "交通", 1, merchant = "网约车", note = "出差打车", isReimbursable = true),
    DemoBillSeed(4, 20, 0, 4000, "EXPENSE", "娱乐", 0, merchant = "电影院"),
    // 一周内
    DemoBillSeed(5, 10, 5, 50000, "TRANSFER", "", 1, toWalletIndex = 0),
    DemoBillSeed(5, 17, 45, 2600, "EXPENSE", "零食", 0, merchant = "零食店"),
    DemoBillSeed(6, 11, 30, 2200, "EXPENSE", "餐饮", 3, merchant = "面馆"),
    DemoBillSeed(6, 13, 20, 12000, "INCOME", "兼职", 1, merchant = "线上兼职"),
    // 上周
    DemoBillSeed(7, 16, 20, 12900, "EXPENSE", "服饰", 2, merchant = "服装店"),
    DemoBillSeed(8, 18, 10, 8800, "EXPENSE", "社交", 0, merchant = "生日礼物"),
    DemoBillSeed(9, 8, 40, 2300, "EXPENSE", "购物", 1, merchant = "水果店"),
    DemoBillSeed(9, 20, 30, 180000, "EXPENSE", "居住", 2, merchant = "房租"),
    DemoBillSeed(10, 14, 0, 19900, "EXPENSE", "数码", 2, merchant = "电商平台"),
    DemoBillSeed(10, 19, 30, 1500, "EXPENSE", "饮品", 0, merchant = "咖啡店"),
    DemoBillSeed(11, 10, 0, 650000, "INCOME", "工资", 2),
    DemoBillSeed(12, 12, 0, 3600, "EXPENSE", "交通", 3, merchant = "出租车"),
    DemoBillSeed(8, 9, 30, 880, "INCOME", "理财", 1),
    DemoBillSeed(13, 20, 15, 6600, "EXPENSE", "红包", 0, merchant = "亲友红包")
)

/**
 * 示例物品：13 件中性样例。
 *
 * 覆盖演示模式要演的全部状态：双图（长篇小说）、「生产日期 + 时长」保质期（手电钻 /
 * 鲜奶茶）、「直接到期日」保质期（苹果）、保修（手电钻）、转赠获取（猫咪）、
 * 抽签获取（多肉盆栽）、随身携带（智能手机）。
 */
internal val demoAssetSeeds = listOf(
    DemoAssetSeed(
        name = "羽毛球拍", category = "SPORTS", price = 89_900, acquiredDaysAgo = 500,
        images = listOf("demo_item_badminton.webp"), location = "书房", channel = "电商平台"
    ),
    DemoAssetSeed(
        name = "公路自行车", category = "SPORTS", price = 1_280_000, acquiredDaysAgo = 800,
        images = listOf("demo_item_bike.webp"), location = "客厅", channel = "线下门店"
    ),
    DemoAssetSeed(
        name = "微单相机", category = "DIGITAL", price = 499_900, acquiredDaysAgo = 60,
        images = listOf("demo_item_camera.webp"), location = "卧室", channel = "电商平台"
    ),
    DemoAssetSeed(
        name = "多肉盆栽", category = "PLANTS", price = 2_500, acquiredDaysAgo = 160,
        acquisitionType = "LOTTERY", images = listOf("demo_item_succulent.webp"), location = "书房"
    ),
    DemoAssetSeed(
        name = "游戏本", category = "DIGITAL", price = 699_900, acquiredDaysAgo = 500,
        images = listOf("demo_item_laptop.webp"), location = "书房", channel = "电商平台",
        description = "16G+512G"
    ),
    DemoAssetSeed(
        name = "手电钻", category = "TOOLS", price = 49_900, acquiredDaysAgo = 120,
        images = listOf("demo_item_drill.webp"), location = "书房", channel = "网购",
        warrantyExpireInDays = 3_800, shelfLifeProducedDaysAgo = 100,
        shelfLifeDurationValue = 10, shelfLifeDurationUnit = "YEAR"
    ),
    DemoAssetSeed(
        name = "智能手机", category = "DIGITAL", price = 499_900, acquiredDaysAgo = 30,
        images = listOf("demo_item_phone.webp"), location = "随身携带", channel = "电商平台",
        description = "8G+256G", condition = "NEW"
    ),
    DemoAssetSeed(
        name = "平板电脑", category = "DIGITAL", price = 299_900, acquiredDaysAgo = 20,
        images = listOf("demo_item_tablet.webp"), location = "卧室", channel = "电商平台"
    ),
    DemoAssetSeed(
        name = "外套", category = "CLOTHING", price = 17_900, acquiredDaysAgo = 15,
        images = listOf("demo_item_jacket.webp"), location = "卧室", channel = "网购"
    ),
    DemoAssetSeed(
        name = "长篇小说", category = "BOOKS", price = 5_900, acquiredDaysAgo = 160,
        images = listOf("demo_item_book_1.webp", "demo_item_book_2.webp"),
        location = "书房", channel = "电商平台"
    ),
    DemoAssetSeed(
        name = "鲜奶茶", category = "FOOD", price = 2_300, acquiredDaysAgo = 1,
        images = listOf("demo_item_drink.webp"), channel = "线下门店",
        shelfLifeProducedDaysAgo = 1, shelfLifeDurationValue = 1, shelfLifeDurationUnit = "DAY"
    ),
    DemoAssetSeed(
        name = "苹果", category = "FOOD", price = 500, acquiredDaysAgo = 1,
        images = listOf("demo_item_apple.webp"), location = "厨房", channel = "线下门店",
        shelfLifeExpireInDays = 5
    ),
    DemoAssetSeed(
        name = "猫咪", category = "PET", price = 8_800, acquiredDaysAgo = 200,
        acquisitionType = "GIFT", images = listOf("demo_item_cat.webp"), location = "客厅",
        description = "家里的小猫", model = "奶牛猫"
    )
)
