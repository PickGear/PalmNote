package com.palmnote.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 演示数据的**本地化层**：演示内容（标题、备注、商户、物品名、清单项…）是固定精品集，
 * 但其中文文案不能原样出现在英文界面里——否则英文用户看到的是「晨跑 / 罗技 G903 无线鼠标」。
 *
 * 做法：种子写入前把文案按当前应用语言过一遍映射（[EN]）。映射覆盖演示数据里**全部**中文串，
 * 由 `DemoTextsCoverageTest` 守住完整性（新增演示内容忘了翻译会直接报红）。
 *
 * 说明：英文侧是**等价的虚构内容**（不是直译腔）——演示数据本就是虚构的，英文用户看到的
 * 应该是一套自然可信的英文样例，而不是"翻译过来的中文"。
 */
internal object DemoTexts {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 当前应用语言是否为中文（含 zh-CN / zh-TW）。 */
    fun isChinese(context: Context): Boolean =
        context.resources.configuration.locales[0].language.startsWith("zh")

    /**
     * 当前应用语言的**语言码**（`zh` / `en`），用于记录"演示数据是按哪种语言播种的"。
     *
     * 只取语言码、不取完整 BCP-47 标签：演示文案只分中英两套，拿 `en-US` 与 `en` 去比
     * 会被判成"语言变了"而白重播一次——重播会复原演示期对示例的改动，是有真实代价的。
     */
    fun currentLocaleLanguage(context: Context): String =
        context.resources.configuration.locales[0].language

    /** 单条文案本地化：中文环境原样返回，其他语言查映射表，未命中则原样返回（由覆盖测试兜底）。 */
    fun localize(context: Context, raw: String): String =
        if (isChinese(context)) raw else EN[raw] ?: raw

    /** fieldsData 里的文本值本地化：只替换映射表命中的字符串值，结构与其他值保持原样。 */
    fun localizeJson(context: Context, fieldsData: String): String {
        if (isChinese(context)) return fieldsData
        val obj = runCatching { json.decodeFromString<JsonObject>(fieldsData) }.getOrNull() ?: return fieldsData
        return JsonObject(obj.mapValues { (_, v) -> localizeElement(v) }).toString()
    }

    private fun localizeElement(el: JsonElement): JsonElement = when (el) {
        is JsonPrimitive -> if (el.isString) JsonPrimitive(EN[el.content] ?: el.content) else el
        is JsonObject -> JsonObject(el.mapValues { (_, v) -> localizeElement(v) })
        is JsonArray -> JsonArray(el.map { localizeElement(it) })
    }

    /** 供覆盖测试读取：英文映射表的键集合。 */
    val mappedKeys: Set<String> get() = EN.keys

    // ───────────────────────── 中文 → 英文映射（演示数据全量） ─────────────────────────

    private val EN: Map<String, String> = mapOf(
        // ── 生活：计划类 ──
        "云盘会员" to "Cloud storage",
        "待办" to "To-do",
        "交水电费" to "Pay utilities",
        "交房租" to "Pay rent",
        "买猫砂" to "Buy cat litter",
        "猫粮" to "Cat food",
        "抽纸" to "Tissues",
        "鲜牛奶" to "Fresh milk",
        "鸡蛋" to "Eggs",
        "整理书桌" to "Tidy the desk",
        "收拾书桌" to "Tidy the desk",
        "取快递（丰巢 3 号柜）" to "Pick up parcel (locker 3)",
        "回复季度邮件" to "Reply to quarterly emails",
        "预约牙医" to "Book a dentist appointment",
        "净水器滤芯" to "Water filter cartridge",
        "把拖了很久的事做完" to "Finish the long-postponed task",
        "年假前收尾" to "Wrap up before annual leave",
        "周末囤货" to "Weekend grocery run",
        "周末去菜市场" to "Weekend market run",
        "跑鞋" to "Running shoes",
        "买房首付" to "House down payment",
        "写重构方案" to "Write the refactor plan",
        "项目收尾，节奏终于慢下来了。" to "Wrapping up the project — the pace finally slowed down.",
        "预约和缴费都处理完了，心里轻松不少。" to "Appointments and bills are all done; feels lighter.",

        // ── 生活：时间类 ──
        "戒烟" to "Quit smoking",
        "坚持100天,坚持一年" to "100 days, then a full year",
        "妈妈生日" to "Mom's birthday",
        "爸爸" to "Dad",
        "在一起" to "Our anniversary",
        "每年回到第一次见面的那家咖啡馆。" to "Back to the café where we first met, every year.",
        "订一束康乃馨，蛋糕提前两天订" to "Order carnations; cake two days ahead",

        // ── 生活：记录类 ──
        "晨跑" to "Morning run",
        "晨跑 30 分钟" to "30-minute morning run",
        "阅读 20 页" to "Read 20 pages",
        "喝水 8 杯" to "8 glasses of water",
        "睡前读书" to "Reading before bed",
        "工作日早上 7 点" to "Weekday mornings at 7",
        "全天提醒" to "All-day reminder",
        "每月一本，慢慢读" to "One book a month, unhurried",
        "坚持过、断过、又续上" to "Kept it up, lapsed, picked it back up",
        "学 Kotlin" to "Learning Kotlin",
        "Kotlin 进阶课" to "Advanced Kotlin course",
        "Kotlin 进阶书" to "Advanced Kotlin book",
        "协程调度器那一章。" to "The chapter on coroutine dispatchers.",
        "协程一章要重看一遍。" to "Worth rereading the coroutines chapter.",
        "读论文" to "Reading papers",
        "两篇论文，各做了摘要。" to "Two papers, summarized both.",
        "整理账单" to "Sorting out the bills",
        "早上下了点雨，把阳台的花搬进来。\n下午整理旧照片，翻到 2019 年那次旅行。\n晚上试着把冰箱清了一遍。" to
            "A little rain in the morning, so the balcony flowers came inside.\n" +
            "Afternoon with old photos — found the 2019 trip.\n" +
            "Tried clearing out the fridge in the evening.",
        "把上月的账单核对了一遍。" to "Went through last month's bills.",
        "番茄钟 25/5，四轮后长休息 20 分。" to "Pomodoro 25/5, long break after four rounds.",
        "把 OrderService 的依赖顺序理了一遍。" to "Reordered dependencies in OrderService.",
        "杭州 4 日" to "Hangzhou, 4 days",
        "杭州" to "Hangzhou",
        "西湖" to "West Lake",
        "西湖 · 断桥" to "West Lake · Broken Bridge",
        "灵隐寺" to "Lingyin Temple",
        "灵隐寺 · 飞来峰" to "Lingyin Temple · Feilai Peak",
        "西溪湿地" to "Xixi Wetland",
        "良渚博物院" to "Liangzhu Museum",
        "自驾" to "By car",
        "步行" to "On foot",
        "打车" to "Taxi",
        "地铁" to "Metro",
        "交通" to "Transport",
        "同行人" to "Travel companion",
        "我" to "Me",
        "第 1-8 节" to "Chapters 1-8",
        "第 9-16 节" to "Chapters 9-16",
        "阳台的花" to "Balcony flowers",
        "把阳台的花搬进来" to "Bring the balcony flowers inside",
        "雨天" to "Rainy day",
        "雨天的咖啡馆" to "Café on a rainy day",
        "雨中的咖啡馆" to "Café in the rain",
        "在咖啡馆坐了一下午，雨停了才走。" to "Sat in the café all afternoon, left when the rain stopped.",
        "傍晚的散步" to "Evening walk",
        "吃完晚饭沿着河边慢慢走了一圈。" to "A slow loop along the river after dinner.",
        "周末爬山" to "Weekend hike",
        "山顶风很大，值了。" to "Windy at the top — worth it.",
        "早起的空气很好" to "Early morning air is the best",
        "七点出门，路上比平时安静很多。" to "Out at seven; the streets were quieter than usual.",
        "换了一条通勤路线" to "Tried a new commute route",
        "提前一站下车，走过去反而更快。" to "Got off a stop early — walking was faster.",
        "收到朋友的明信片" to "Postcard from a friend",
        "邮戳很特别，准备夹进正在读的书里。" to "The postmark is lovely; keeping it in my current book.",
        "把积了几天的纸和书都归了位。" to "Finally shelved the piled-up papers and books.",
        "下班路上躲雨，顺手记下今天的一点小事。" to "Sheltering from the rain on the way home; jotting down the day.",
        "买到了刚上市的橘子，顺手记一下。" to "Found the season's first tangerines — noting it down.",
        "原本想去超市，后来在家把冰箱清了一遍。" to "Meant to hit the supermarket; cleaned out the fridge instead.",
        "看完一部老电影" to "Watched an old film",
        "重看了一遍《海街日记》，还是很喜欢。" to "Rewatched Our Little Sister — still love it.",
        "加完班的一点点感想" to "A few thoughts after overtime",
        "连着三天加班，今天早点睡。" to "Three late nights in a row — sleeping early tonight.",
        "临时改了计划" to "Plans changed at the last minute",
        "还不错" to "Pretty good",
        "还行" to "Okay",
        "开心" to "Happy",
        "平静" to "Calm",
        "有点累" to "A bit tired",
        "焦虑" to "Anxious",
        "疲惫" to "Exhausted",
        "《置身事内》" to "Economics in Practice",
        "《置身事内》在读" to "Reading Economics in Practice",
        "了解了政府在经济发展中的角色，很多现象一下说得通了。" to "Seeing the state's role in growth made a lot of things click.",
        "兰小欢" to "A. Hart",
        "体重 78.4 kg" to "Weight 78.4 kg",
        "体重 77.6 kg" to "Weight 77.6 kg",
        "力量训练" to "Strength training",
        "拉伸 10 分钟" to "10-minute stretch",
        "跑步 3km" to "Run 3 km",

        // ── 生活：字段值 / 标签 ──
        "家里" to "Home",
        "家" to "Home",
        "公司" to "Office",
        "书房" to "Study",
        "卧室" to "Bedroom",
        "客厅" to "Living room",
        "玄关" to "Entryway",
        "工位" to "Desk",
        "晴" to "Sunny",
        "阴" to "Overcast",
        "雨" to "Rain",
        "天气" to "Weather",
        "品名" to "Item",
        "数量" to "Qty",
        "单价" to "Unit price",
        "金额" to "Amount",
        "费用" to "Fee",
        "地点" to "Place",
        "天" to "Day",
        "本" to "Book",
        "杯" to "Glass",
        "次" to "Time",
        "页" to "Page",
        "已买" to "Bought",
        "扣费日" to "Billing date",
        "日用" to "Household",
        "食品" to "Groceries",
        "健康" to "Health",
        "学习" to "Study",
        "工作" to "Work",
        "社交" to "Social",
        "其他" to "Other",
        "经济,社科" to "Economics, social science",
        "工作日早上 7 点" to "Weekday mornings at 7",

        // ── 记账：商户 / 备注 ──
        "房东" to "Landlord",
        "房租" to "Rent",
        "公司" to "Employer",
        "工资" to "Salary",
        "话费" to "Phone bill",
        "加油" to "Fuel",
        "中国石化" to "Sinopec",
        "中国移动" to "China Mobile",
        "杭州地铁" to "Hangzhou Metro",
        "哈啰单车" to "HelloBike",
        "滴滴出行" to "DiDi",
        "川味小馆" to "Sichuan Kitchen",
        "老乡鸡" to "Laoxiangji",
        "楼下包子铺" to "Baozi shop downstairs",
        "菜市场" to "Food market",
        "山姆会员店" to "Sam's Club",
        "名创优品" to "MINISO",
        "花鸟市场" to "Flower & bird market",
        "市口腔医院" to "City Dental Hospital",
        "人民邮电出版社" to "Posts & Telecom Press",
        "网易云音乐" to "NetEase Cloud Music",
        "淘票票" to "Taopiaopiao",
        "滔搏运动" to "Topsports",
        "余额宝" to "Yu'ebao",
        "和朋友吃饭" to "Dinner with a friend",
        "周末囤货" to "Weekend grocery run",
        "年费续订" to "Annual renewal",
        "月度收益" to "Monthly yield",
        "退货退款" to "Refund",
        "洗牙" to "Dental cleaning",
        "《奥本海默》重映" to "Oppenheimer re-release",

        // ── 物品 ──
        "罗技 G903 无线鼠标" to "Logitech G903 Wireless Mouse",
        "罗技" to "Logitech",
        "宜家 MARKUS 椅" to "IKEA MARKUS Chair",
        "宜家" to "IKEA",
        "富士 X-T5" to "Fujifilm X-T5",
        "富士" to "Fujifilm",
        "机械键盘" to "Mechanical keyboard",
        "背光 87 键" to "Backlit, 87 keys",
        "瑜伽垫" to "Yoga mat",
        "棉质防滑" to "Cotton, non-slip",
        "亚马逊" to "Amazon",

        // ── 钱包 ──
        "微信零钱" to "WeChat Balance",
        "支付宝" to "Alipay",
        "现金" to "Cash",
        "招商银行储蓄卡" to "CMB Debit Card",
        "招商银行" to "China Merchants Bank"
    )
}
