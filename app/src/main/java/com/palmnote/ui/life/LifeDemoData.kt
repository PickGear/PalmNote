package com.palmnote.ui.life

/**
 * 演示模式的**示例内容清单**（生活页专用；记账 / 资产等页面后续照此扩展）。
 *
 * 定位：给第一次使用的用户一份「原来可以这样记」的参考 —— 所以每条都是
 * **真实可查、可编辑、可删除的记录**，由 [com.palmnote.data.LifeDemoSeeder] 落成
 * 真正的 `life_items` 行（挂在内置模板上，字段值按各模板的字段定义写），
 * 而不是画在界面上的假数据。
 *
 * ⚠️ **内容取自设计稿**（2026-09-22 照抄）：
 * 买房首付 / 周末囤货 / 待办 / 杭州 4 日 /《置身事内》/ Kotlin 进阶课 / 妈妈生日 / 戒烟 /
 * 爸爸 / 在一起 / 晨跑 / 心情 / 日记 / iCloud+ —— 每个模板的示例就是设计稿里那一屏的内容，
 * 这样用户第一次进来看到的就是「设计稿的样子」，而不是另一套自造的假数据。
 *
 * 合计 **61 条**（一次性记录 13 + 日常记录 48）；全部落在**今天及之前**（`daysAgo ≥ 0`），不出现「未来日期已有记录」。
 * 复合字段的编码见 `FieldContract`（清单 `{"v":1,"items":[…]}`，表格 `{"v":1,"columns":[…],"rows":[…]}`）。
 */
internal data class LifeDemoItem(
    /** 内置模板的身份键（= 种子 icon 名，见 `LifeDataSeeder.lifeTemplateSeeds`）。 */
    val templateIcon: String,
    val title: String,
    /** 字段值 JSON，键名取自该模板的 `fieldsConfig`。 */
    val fieldsData: String = "{}",
    /** 距今天的天数（0 = 今天），决定 createdAt / dueDate 落点。 */
    val daysAgo: Int = 0,
    val hour: Int = 9,
    val minute: Int = 0,
    val status: String = "ACTIVE",
    /** 有截止日的（待办 / 倒计时）距今天的天数；null = 无截止日。 */
    val dueInDays: Int? = null,
    /** IMAGE 字段演示图：字段 key → assets/demo/ 文件名（播种器物化到 filesDir 后写绝对路径）。 */
    val images: Map<String, String> = emptyMap()
)

internal object LifeDemoData {

    /** 示例条目：一次性记录 13 + 日常记录 48 = 61。 */
    val items: List<LifeDemoItem> = listOf(
        // ── 一次性记录（设计稿 dtl_01–10 / dtl_14）──
        // dtl_01 存钱：目标 ¥300,000 / 已存 ¥96,000 / 目标日 2027-06-30
        LifeDemoItem(
            "savings",
            "买房首付",
            """{"targetAmount":300000,"currentAmount":96000,"deadline":"+270d"}""",
            daysAgo = 219,
            hour = 20,
            minute = 10
        ),
        // dtl_02 购物：预算 ¥800 / 已花 ¥496，明细 4 行（鲜牛奶已买）
        LifeDemoItem(
            "shopping_cart",
            "周末囤货",
            """{"budget":800,"spent":496,"store":"山姆会员店","category":["食品","日用","其他"],""" +
                """"items_detail":{"v":1,"columns":[{"key":"name","label":"品名","type":"TEXT"},""" +
                """{"key":"price","label":"单价","type":"CURRENCY"},{"key":"qty","label":"数量","type":"NUMBER"},""" +
                """{"key":"bought","label":"已买","type":"BOOLEAN"}],"rows":""" +
                """[["鲜牛奶","12","2","true"],["鸡蛋","18","1","false"],""" +
                """["猫粮","129","1","false"],["抽纸","35","3","false"]]}}""",
            daysAgo = 3,
            hour = 15,
            minute = 40
        ),
        // dtl_03 待办：7 项子任务（含 1 项逾期、2 项已完成）
        LifeDemoItem(
            "checklist",
            "待办",
            """{"deadline":"+0d","subtasks":{"v":1,"items":[{"text":"交房租","done":false},""" +
                """{"text":"预约牙医","done":false},{"text":"回复季度邮件","done":false},""" +
                """{"text":"买猫砂","done":true},{"text":"取快递（丰巢 3 号柜）","done":false},""" +
                """{"text":"整理书桌","done":false},{"text":"交水电费","done":true}]}}""",
            daysAgo = 1,
            hour = 9,
            minute = 5,
            dueInDays = 0
        ),
        // dtl_04 旅行：还有 12 天出发，预算 ¥4,000，同行 2 人，行程 4 段
        LifeDemoItem(
            "flight",
            "杭州 4 日",
            """{"destination":"杭州","budget":4000,"booked":1800,"companions":["我","同行人"],""" +
                """"startDate":"+12d","endDate":"+15d","itinerary":{"v":1,"columns":""" +
                """[{"key":"day","label":"天","type":"TEXT"},{"key":"place","label":"地点","type":"TEXT"},""" +
                """{"key":"traffic","label":"交通","type":"TEXT"},{"key":"cost","label":"费用","type":"CURRENCY"}],""" +
                """"rows":[["D1","西湖 · 断桥","步行","0"],["D2","灵隐寺 · 飞来峰","打车","35"],""" +
                """["D3","西溪湿地","地铁","8"],["D4","良渚博物院","自驾","60"]]},""" +
                """"route":{"v":1,"connect":true,"route":[""" +
                """{"name":"西湖","lat":30.247,"lng":120.149,"note":"D1"}, """ +
                """{"name":"灵隐寺","lat":30.241,"lng":120.101,"note":"D2"}, """ +
                """{"name":"西溪湿地","lat":30.270,"lng":120.068,"note":"D3"}, """ +
                """{"name":"良渚博物院","lat":30.393,"lng":120.040,"note":"D4"}]}}""",
            daysAgo = 8,
            hour = 21,
            minute = 5,
            dueInDays = 12,
            images = mapOf("photos" to "demo_westlake.jpg")
        ),
        // dtl_05 阅读：读到 147 / 350 页，评分 4 / 5
        LifeDemoItem(
            "menu_book",
            "《置身事内》",
            """{"totalPages":350,"currentPage":147,"rating":4,"author":"兰小欢","tags":"经济,社科","excerpt":"了解了政府在经济发展中的角色，很多现象一下说得通了。"}""",
            daysAgo = 76,
            hour = 22,
            minute = 40,
            images = mapOf("cover" to "demo_cover.jpg")
        ),
        // dtl_06 学习：8 / 24 节，单次 45 分钟
        LifeDemoItem(
            "school",
            "Kotlin 进阶课",
            """{"courseName":"Kotlin 进阶课","totalLessons":24,"completedLessons":8,"duration":45,"rating":5,""" +
                """"notes":"协程一章要重看一遍。",""" +
                """"lessons":{"v":1,"items":[{"text":"第 1-8 节","done":true},{"text":"第 9-16 节","done":false}]}}""",
            daysAgo = 103,
            hour = 20,
            minute = 0
        ),
        // dtl_07 倒计时：还有 87 天
        LifeDemoItem(
            "timer_off",
            "妈妈生日",
            """{"targetDate":"+87d","reminder":true,"note":"订一束康乃馨，蛋糕提前两天订"}""",
            daysAgo = 10,
            hour = 9,
            minute = 30,
            dueInDays = 87,
            images = mapOf("photo" to "demo_gift.jpg")
        ),
        // dtl_08 正数日：已经 365 天
        LifeDemoItem(
            "trending_up",
            "戒烟",
            """{"start_date":"-365d","reminder":true,"milestones":"坚持100天,坚持一年"}""",
            daysAgo = 365,
            hour = 7,
            minute = 0,
            images = mapOf("photo" to "demo_run.jpg")
        ),
        // dtl_09 生日（农历）：还有 23 天
        LifeDemoItem(
            "cake",
            "爸爸",
            """{"date":"+23d","reminder":true,"person":"爸爸","lunar":true}""",
            daysAgo = 300,
            hour = 9,
            minute = 30,
            dueInDays = 23,
            images = mapOf("avatar" to "demo_avatar.jpg")
        ),
        // dtl_10 纪念日：还有 45 天，第 3 年
        LifeDemoItem(
            "celebration",
            "在一起",
            """{"date":"+45d","note":"每年回到第一次见面的那家咖啡馆。"}""",
            daysAgo = 1095,
            hour = 12,
            minute = 0,
            dueInDays = 45
        ),
        // 倒计时（设计稿无独立张，保留为同模板的第二条演示记录）
        LifeDemoItem(
            "timer_off",
            "年假前收尾",
            """{"targetDate":"+30d","reminder":true}""",
            daysAgo = 2,
            hour = 11,
            minute = 0,
            dueInDays = 30
        ),
        // dtl_14 订阅：¥25 / 月，下次扣费 4 天后
        LifeDemoItem(
            "subscriptions",
            "iCloud+",
            """{"price":21,"billingCycle":"monthly","nextBilling":"+4d",""" +
                """"url":"https://www.icloud.com/settings/","billingHistory":{"v":1,""" +
                """"columns":[{"key":"date","label":"扣费日","type":"DATE"},""" +
                """{"key":"amount","label":"金额","type":"CURRENCY"}],""" +
                """"rows":[["2026-08-26","21"],["2026-07-26","21"],["2026-06-26","21"]]}}""",
            daysAgo = 295,
            hour = 8,
            minute = 0
        ),
        // 周报月报已于 v1.27 退役（报告改由**统计页**承担）—— 不再造示例条目。
        // 物品维护（设计稿无此张，沿用既有示例）
        LifeDemoItem(
            "build",
            "净水器滤芯",
            """{"item":"净水器滤芯","boughtAt":"-4d","cycle":90}""",
            daysAgo = 4,
            hour = 19,
            minute = 30,
            images = mapOf("photo" to "demo_filter.jpg")
        ),

        // ── 日常记录（可跨天重复，体现「每天记一点」）──
        // dtl_11 打卡「晨跑」：连续 21 天 / 目标 30 天（21 个连续天，currentStreak 随天数递减；
        // 连击由真实打卡记录算出 = 21，与设计意图一致；每天 7:05）
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":1}""",
            daysAgo = 20,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":2}""",
            daysAgo = 19,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":3}""",
            daysAgo = 18,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":4}""",
            daysAgo = 17,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":5}""",
            daysAgo = 16,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":6}""",
            daysAgo = 15,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":7}""",
            daysAgo = 14,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":8}""",
            daysAgo = 13,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":9}""",
            daysAgo = 12,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":10}""",
            daysAgo = 11,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":11}""",
            daysAgo = 10,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":12}""",
            daysAgo = 9,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":13}""",
            daysAgo = 8,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":14}""",
            daysAgo = 7,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":15}""",
            daysAgo = 6,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":16}""",
            daysAgo = 5,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":17}""",
            daysAgo = 4,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":18}""",
            daysAgo = 3,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":19}""",
            daysAgo = 2,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":20}""",
            daysAgo = 1,
            hour = 7,
            minute = 5
        ),
        LifeDemoItem(
            "calendar_month",
            "晨跑",
            """{"targetDays":30,"currentStreak":21}""",
            daysAgo = 0,
            hour = 7,
            minute = 0
        ),

        // dtl_12 心情：最新一条 = 平静 / 精力 70% / 影响因素 工作·睡眠
        LifeDemoItem(
            "mood",
            "还不错",
            """{"mood":"平静","energy":70,"factors":["工作","健康"],"note":"项目收尾，节奏终于慢下来了。"}""",
            daysAgo = 0,
            hour = 9,
            minute = 12
        ),
        LifeDemoItem(
            "mood",
            "有点累",
            """{"mood":"疲惫","energy":45,"factors":["工作"]}""",
            daysAgo = 3,
            hour = 23,
            minute = 15
        ),
        LifeDemoItem(
            "mood",
            "开心",
            """{"mood":"开心","energy":85,"factors":["社交"]}""",
            daysAgo = 8,
            hour = 20,
            minute = 5
        ),
        LifeDemoItem(
            "mood",
            "平静",
            """{"mood":"平静","energy":65,"factors":["健康"]}""",
            daysAgo = 11,
            hour = 21,
            minute = 45
        ),
        LifeDemoItem(
            "mood",
            "焦虑",
            """{"mood":"焦虑","energy":35,"factors":["工作","学习"]}""",
            daysAgo = 14,
            hour = 22,
            minute = 10
        ),
        LifeDemoItem(
            "mood",
            "还行",
            """{"mood":"平静","energy":60,"factors":["天气"]}""",
            daysAgo = 17,
            hour = 21,
            minute = 30
        ),

        // dtl_13 日记：最新一条正文照设计稿
        LifeDemoItem(
            "book",
            "把阳台的花搬进来",
            """{"weather":"阴","mood":"平静","content":""" +
                """"早上下了点雨，把阳台的花搬进来。\n下午整理旧照片，翻到 2019 年那次旅行。\n晚上试着把冰箱清了一遍。",""" +
                """"location":"家里"}""",
            daysAgo = 0,
            hour = 21,
            minute = 30,
            images = mapOf("photos" to "demo_flower.jpg")
        ),
        LifeDemoItem(
            "book",
            "傍晚的散步",
            """{"weather":"晴","mood":"平静","content":"吃完晚饭沿着河边慢慢走了一圈。"}""",
            daysAgo = 1,
            hour = 20,
            minute = 10,
            images = mapOf("photos" to "demo_sunset.jpg")
        ),
        LifeDemoItem(
            "book",
            "雨中的咖啡馆",
            """{"weather":"雨","mood":"平静","content":"在咖啡馆坐了一下午，雨停了才走。"}""",
            daysAgo = 2,
            hour = 16,
            minute = 20
        ),
        LifeDemoItem(
            "book",
            "收拾书桌",
            """{"weather":"阴","mood":"平静","content":"把积了几天的纸和书都归了位。"}""",
            daysAgo = 3,
            hour = 19,
            minute = 35
        ),
        LifeDemoItem(
            "book",
            "雨天的咖啡馆",
            """{"weather":"雨","mood":"平静","content":"下班路上躲雨，顺手记下今天的一点小事。"}""",
            daysAgo = 4,
            hour = 16,
            minute = 20
        ),
        LifeDemoItem(
            "book",
            "看完一部老电影",
            """{"weather":"晴","mood":"开心","content":"重看了一遍《海街日记》，还是很喜欢。"}""",
            daysAgo = 6,
            hour = 22,
            minute = 0
        ),
        LifeDemoItem(
            "book",
            "早起的空气很好",
            """{"weather":"晴","mood":"开心","content":"七点出门，路上比平时安静很多。"}""",
            daysAgo = 7,
            hour = 7,
            minute = 40
        ),
        LifeDemoItem(
            "book",
            "周末爬山",
            """{"weather":"晴","mood":"开心","content":"山顶风很大，值了。"}""",
            daysAgo = 8,
            hour = 19,
            minute = 50
        ),
        LifeDemoItem(
            "book",
            "临时改了计划",
            """{"weather":"阴","mood":"平静","content":"原本想去超市，后来在家把冰箱清了一遍。"}""",
            daysAgo = 9,
            hour = 18,
            minute = 25
        ),
        LifeDemoItem(
            "book",
            "收到朋友的明信片",
            """{"weather":"晴","mood":"开心","content":"邮戳很特别，准备夹进正在读的书里。"}""",
            daysAgo = 10,
            hour = 20,
            minute = 5
        ),
        LifeDemoItem(
            "book",
            "把拖了很久的事做完",
            """{"weather":"雨","mood":"平静","content":"预约和缴费都处理完了，心里轻松不少。"}""",
            daysAgo = 11,
            hour = 17,
            minute = 45
        ),
        LifeDemoItem(
            "book",
            "加完班的一点点感想",
            """{"weather":"阴","mood":"疲惫","content":"连着三天加班，今天早点睡。"}""",
            daysAgo = 12,
            hour = 22,
            minute = 5
        ),
        LifeDemoItem(
            "book",
            "周末去菜市场",
            """{"weather":"晴","mood":"开心","content":"买到了刚上市的橘子，顺手记一下。"}""",
            daysAgo = 13,
            hour = 10,
            minute = 30
        ),
        LifeDemoItem(
            "book",
            "换了一条通勤路线",
            """{"weather":"阴","mood":"平静","content":"提前一站下车，走过去反而更快。"}""",
            daysAgo = 14,
            hour = 8,
            minute = 20
        ),

        // dtl_16 专注：今天三次会话照稿；备注挂在当前任务那一条上
        LifeDemoItem("timer", "写重构方案", """{"duration":2700000,"note":"把 OrderService 的依赖顺序理了一遍。"}""", daysAgo = 0, hour = 9, minute = 30),
        LifeDemoItem("timer", "读论文", """{"duration":1500000,"note":"两篇论文，各做了摘要。"}""", daysAgo = 0, hour = 10, minute = 15),
        LifeDemoItem(
            "timer",
            "写重构方案",
            """{"duration":3000000,"note":"番茄钟 25/5，四轮后长休息 20 分。"}""",
            daysAgo = 0,
            hour = 14,
            minute = 0
        ),
        LifeDemoItem("timer", "整理账单", """{"duration":2100000,"note":"把上月的账单核对了一遍。"}""", daysAgo = 10, hour = 15, minute = 45),
        LifeDemoItem("timer", "学 Kotlin", """{"duration":1800000,"note":"协程调度器那一章。"}""", daysAgo = 14, hour = 20, minute = 0),

        // 身体记录（设计稿无此张，沿用既有示例；weight 参与 BMI 派生）
        // 身高体重用一组通用且内部自洽的数值（BMI ≈ 22.9 → 22.7，处于正常区间，两次记录呈自然下降）——
        // 体征是最敏感的个人信息，示例里不该出现任何看起来像"某个真人"的具体数据。
        LifeDemoItem(
            "fitness_center",
            "体重 78.4 kg",
            """{"weight":78.4,"sleep":7.5,"height":185}""",
            daysAgo = 6,
            hour = 8,
            minute = 0
        ),
        LifeDemoItem(
            "fitness_center",
            "体重 77.6 kg",
            """{"weight":77.6,"sleep":8,"height":185,"nextCheckup":"+60d",""" +
                """"exercise":{"v":1,"items":[{"text":"跑步 3km","done":true},""" +
                """{"text":"拉伸 10 分钟","done":true},{"text":"力量训练","done":false}]}}""",
            daysAgo = 1,
            hour = 8,
            minute = 2
        )
    )
}
