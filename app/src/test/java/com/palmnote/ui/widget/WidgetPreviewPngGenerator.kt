package com.palmnote.ui.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.data.db.dao.HeldCategoryCount
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.LifeItem
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 选择器静态预览图（`previewImage`）生成器：真实布局 + 样例数据离屏渲染（v22）。
 *
 * 默认跳过（CI 不跑），重出预览图时手动触发：
 * ```
 * WIDGET_PREVIEWS=1 ./gradlew --no-daemon :app:testDebugUnitTest --tests "*WidgetPreviewPngGenerator*"
 * ```
 * 产出 `app/build/widget-previews/`（浅色）与 `app/build/widget-previews-night/`（深色）下的 PNG，
 * 每个形态**两张**：`widget_preview_x.png`（内容层）与 `widget_preview_x_surface.png`（面板层，
 * 见 [render] 的说明），逐张目检后分别拷进 `app/src/main/res/drawable-nodpi/` 与
 * `app/src/main/res/drawable-night-nodpi/`。
 *
 * 画布按**设计稿网格**给（3× 密度）：4×2 = 340×148dp（1020×444px）、2×2 = 160×160dp（480×480px）、
 * 4×1 = 340×74dp（1020×222px）、2×1 = 160×74dp（480×222px）。
 * v22 起目录只做五档（4×2 / 2×2 / 4×1 / 2×1 / 4×4），其余尺寸由桌面拖拽归档。
 * 语言取 zh：预览文案是用户看到的界面文案，不跟测试机 locale 走。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPreviewPngGenerator {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 预览里的账单强调色 = 稿 V22 账单族色 c5 橙（与线上 accentOf 无覆盖时的默认一致）。 */
    private val accent = WidgetData.AccentTheme(
        accent = WidgetV22.family(context, 4).d,
        onAccent = 0xFFFFFFFF.toInt()
    )
    private var outDir = File("build/widget-previews")

    init {
        // 预览图断言与设计稿全部按默认（中文）strings 口径 —— 生成环境 locale 跟宿主走,必须钉死
        java.util.Locale.setDefault(java.util.Locale.SIMPLIFIED_CHINESE)
    }

    @Test
    fun `重出全部小组件预览图`() {
        assumeTrue("仅手动触发：WIDGET_PREVIEWS=1", System.getenv("WIDGET_PREVIEWS") == "1")
        renderAll(outDir)
    }

    /** 深色版：同一批组件、同一批数据，只换资源限定符（夜间取 `values-night` 的卡片底色）。 */
    @Test
    @Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-night-xxhdpi")
    fun `重出全部小组件预览图深色版`() {
        assumeTrue("仅手动触发：WIDGET_PREVIEWS=1", System.getenv("WIDGET_PREVIEWS") == "1")
        renderAll(File("build/widget-previews-night"))
    }

    private fun renderAll(dir: File) {
        outDir = dir
        outDir.mkdirs()

        // ── 目录形态（info 的 previewImage 指向这些） ──
        render("bill", 1020, 444, billViews())
        render("dashboard", 1020, 444, dashboardViews())
        render("todo", 1020, 444, todoViews())
        render("habit", 1020, 444, habitViews())
        render("counter", 1020, 444, counterViews())
        render("next_up", 1020, 444, nextUpViews())
        render("subscription", 1020, 444, subscriptionViews())
        render("asset", 1020, 444, assetViews())
        render("shortcuts", 480, 480, shortcutsViews())
        render("net_worth", 480, 480, netWorthSquareViews())
        // 4×1 档：快捷横排四格
        // 快捷 4×2 八格
        render("shortcuts_full", 1020, 444, ShortcutsWidgetProvider().bindViews(context, 1, 340, 148))
        render("next_up_wide", 1020, 444, NextUpWidgetProvider().bindViews(context, 1, nextUpFocus(), 340, 148))

        render("shortcuts_wide", 1020, 222, ShortcutsWidgetProvider().bindViews(context, 1, 340, 74))
        renderRemainingTiers()
        renderTallTier()

        // ── 入口卡专用图 ──
        // 目录卡只允许 4×2 / 2×2 两种画布（2026-10-21 用户定），入口预览必须按同一画布出图。
        // 只出**内容层**：卡底由画廊按主题垫。
        renderEntry("bill", 1020, 444, billViews())
        renderEntry("subscription", 1020, 444, subscriptionViews())
        renderEntry("asset", 1020, 444, assetViews())
        renderEntry("counter", 480, 480, counterSquareViews())
        renderEntry("next_up", 480, 480, nextUpSquareViews())
        renderEntry("todo", 1020, 444, todoViews())
        renderEntry("habit", 1020, 444, habitViews())
        renderEntry("net_worth", 480, 480, netWorthSquareViews())
        renderEntry("dashboard", 1020, 444, dashboardViews())
        renderEntry("shortcuts", 480, 480, shortcutsViews())
    }

    /** 其余各档（2×2 / 横条 / 迷你）：目录 previewImage 与弹窗翻页共用，逐张核对溢出。 */
    /** 4×4 仪表盘档：十族全走真 4×4 分发（v37 §02；压缩态不在此列，预览只出 340×340）。 */
    private fun renderTallTier() {
        render(
            "dashboard_tall", 1020, 1020,
            DashboardWidgetProvider().bindViews(context, 1, dashboardSnapshot(), 340, 340)
        )
        render("bill_tall", 1020, 1020, BillWidgetProvider().bindTall4x4(context, 1, billSnapshot()))
        render("todo_tall", 1020, 1020, TodoWidgetProvider().bindViews(context, 1, todoBoard(), 340, 340))
        render("habit_tall", 1020, 1020, HabitWidgetProvider().bindViews(context, 1, habitRows(), 340, 340))
        render("counter_tall", 1020, 1020, LifeCounterWidgetProvider().bindViews(context, 1, goalCounters(), 340, 340))
        render(
            "next_up_tall", 1020, 1020,
            NextUpWidgetProvider().bindViews(context, 1, nextUpFocus(), 340, 340, events = nextUpEvents())
        )
        render("net_worth_tall", 1020, 1020, NetWorthWidgetProvider().bindViews(
            context, 1, totalBalance = 3_418_000, liabs = 682_000, widthDp = 340, monthDelta = 67_020, heightDp = 340,
            monthlyNet = netWorthMonthlySeries()
        ))
        render(
            "subscription_tall", 1020, 1020,
            SubscriptionWidgetProvider().bindViews(context, 1, emptyList(), 340, 340, entries = subEntries())
        )
        render(
            "asset_tall", 1020, 1020,
            AssetWidgetProvider().bindViews(context, 1, assetCategoriesTall(), 340, 340, recent = assetRecent())
        )
        render("shortcuts_tall", 1020, 1020, ShortcutsWidgetProvider().bindViews(context, 1, 340, 340))
    }

    private fun renderRemainingTiers() {
        render("bill_square", 480, 480, BillWidgetProvider().bindSquareViews(context, 1, billSnapshot(), accent))
        render("bill_bar", 1020, 222, BillWidgetProvider().bindBarViews(context, 1, billSnapshot(), accent))
        render("todo_square", 480, 480, todoSquareViews())
        render("todo_bar", 1020, 222, TodoWidgetProvider().bindViews(context, 1, todoBoard(), 340, 74))
        render("habit_square", 480, 480, habitSquareViews())
        render("habit_bar", 1020, 222, HabitWidgetProvider().bindViews(context, 1, habitRows(), 340, 74))
        render("counter_square", 480, 480, counterSquareViews())
        render("counter_bar", 1020, 222, counterBarViews())
        render("next_up_bar", 1020, 222, nextUpBarViews())
        // 「接下来」2×2 档（base provider）的目录/选择器预览：base 的 info 是 2×2，
        // 早先漏出这一档，目录里 2×2 与 4×2 用的是同一张 4×2 图。
        render("next_up_square", 480, 480, nextUpSquareViews())
        render("subscription_square", 480, 480, subscriptionSquareViews())
        render("subscription_bar", 1020, 222, subscriptionBarViews())
        render("asset_square", 480, 480, assetSquareViews())
        render("asset_bar", 1020, 222, assetBarViews())
        render("dashboard_square", 480, 480, dashboardSquareViews())
        render("dashboard_bar", 1020, 222, dashboardBarViews())
        render("net_worth_wide", 1020, 444, netWorthWideViews())
        render("net_worth_bar", 1020, 222, netWorthBarViews())
    }

    // ── 各组件：与线上 onUpdateAsync 同源（同样的 bind + 同样的运行时着色） ──

    /**
     * 记账样例（稿 §02 数值）：本月支出 ¥8,200 / 收入 ¥12k / 预算 ¥10k；
     * 近 7 天 [193,126,328,92,235,420,128] 元（峰值 ¥420 在周五、今天 ¥128，与 hero 同数）；
     * 近 12 个月（旧→新）末月 = 本月 8.2k；分类 top4 对齐稿的 minichips。
     */
    private fun billSnapshot() = BillWidgetProvider.BillSnapshot(
        monthlyExpense = 820_000,
        monthlyIncome = 1_200_000,
        budget = Budget(yearMonth = "2026-10", totalBudget = 1_000_000),
        topCategory = "餐饮",
        dailyExpense = listOf(19_300, 12_600, 32_800, 9_200, 23_500, 42_000, 12_800),
        monthlySeries = listOf(
            344_400, 311_600, 451_000, 393_600, 492_000, 426_400,
            533_000, 475_600, 574_000, 508_400, 615_000, 820_000
        ),
        topCategories = listOf(
            com.palmnote.data.db.dao.CategoryTotal("餐饮", 320_000),
            com.palmnote.data.db.dao.CategoryTotal("交通", 180_000),
            com.palmnote.data.db.dao.CategoryTotal("购物", 150_000),
            com.palmnote.data.db.dao.CategoryTotal("其他", 170_000)
        )
    )

    private fun billViews(): RemoteViews = BillWidgetProvider().bindViews(context, 1, billSnapshot(), accent)

    /**
     * 概览样例（稿 §02 数值，日期按生成当天）：今日支出 ¥128 · 3 笔 · 笔均 ¥43；
     * KPI 待办 3/5 · 打卡 2/5 · 订阅 11/12（19 天后）· 预算 82%（剩 ¥1.8k）；
     * 近 7 天支出与记账族同数（峰值 ¥420 周五、今天 ¥128）。
     */
    private fun dashboardSnapshot() = DashboardWidgetProvider.DashboardSnapshot(
        todayExpense = 12_800,
        todayIncome = 0,
        todayExpenseCount = 3,
        weekday = weekdayLabel(previewToday),
        todoRemaining = 3,
        todoTotal = 5,
        habitDone = 2,
        habitTotal = 5,
        subDateText = "11/12",
        subDaysLeft = 19,
        dailyExpense = listOf(19_300, 12_600, 32_800, 9_200, 23_500, 42_000, 12_800),
        budgetUsedPercent = 82,
        budgetRemainText = "剩 ¥1.8k"
    )

    private fun dashboardViews(): RemoteViews =
        DashboardWidgetProvider().bindViews(context, 1, dashboardSnapshot(), 340, 148)

    private fun dashboardSquareViews(): RemoteViews =
        DashboardWidgetProvider().bindViews(context, 1, dashboardSnapshot(), 160, 160)

    private fun dashboardBarViews(): RemoteViews =
        DashboardWidgetProvider().bindViews(context, 1, dashboardSnapshot(), 340, 74)


    /**
     * 待办样例（稿 §02，日期按生成当天）：今日 5 项、2 项已完成 → hero「3 项」；
     * 4×2 三行 = 交房租（已勾 10:00）/ 取快递 18:00 / 写周报 21:00，footer 最近 = 取快递；
     * 近 7 天完成 [3,2,3,3,2,3,2]（本周合计 15），基准日前 12 天起每天有完成（连续天数口径）。
     */
    private fun todoBoard() = WidgetData.TodoBoard(
        items = buildList {
            // 历史完成（按到期日归天，与打卡同口径）：10/13~10/17 各 1 笔，10/18~10/23 按 [3,2,3,3,2,3]
            val history = listOf(1, 1, 1, 1, 1, 3, 2, 3, 3, 2, 3)
            history.forEachIndexed { index, count ->
                val day = previewToday.minusDays((history.size - index).toLong())
                repeat(count) { i ->
                    add(
                        LifeItem(
                            id = 100L + index * 10 + i,
                            templateId = 1,
                            title = "整理记录",
                            status = "COMPLETED",
                            dueDate = dayAt(day, 20, 0)
                        )
                    )
                }
            }
            addAll(todayTodoItems())
        },
        todayItems = todayTodoItems(),
        todayDone = 2,
        todayTotal = 5
    )

    /** 今天的 5 项待办（2 完成；剩余 3 项按时间排，4×2 取前三行对稿）。 */
    private fun todayTodoItems() = listOf(
        LifeItem(id = 1, templateId = 1, title = "交房租", status = "COMPLETED", dueDate = todayAt(10, 0)),
        LifeItem(id = 2, templateId = 1, title = "取快递", status = "ACTIVE", dueDate = todayAt(18, 0)),
        LifeItem(id = 3, templateId = 1, title = "写周报", status = "ACTIVE", dueDate = todayAt(21, 0)),
        LifeItem(id = 4, templateId = 1, title = "复盘", status = "ACTIVE", dueDate = todayAt(21, 30)),
        LifeItem(id = 5, templateId = 1, title = "冥想", status = "COMPLETED", dueDate = todayAt(22, 0))
    )

    private fun todoViews(): RemoteViews =
        TodoWidgetProvider().bindViews(context, 1, todoBoard(), 340, 148)

    private fun todoSquareViews(): RemoteViews =
        TodoWidgetProvider().bindViews(context, 1, todoBoard(), 160, 160)



    /**
     * 打卡样例（稿 §02 数值，日期按生成当天）：5 个习惯、今日 2 个完成（早起/运动）；
     * 本周合计 15、最佳 早起 7/7（次佳 阅读 5/7）、最长连击 12；
     * 近 7 天完成 [3,4,5,2,4,3,2]（峰值 5 在周二、今天 2）。
     */
    private fun habitRows(): List<HabitWidgetProvider.HabitRow> = listOf(
        HabitWidgetProvider.HabitRow(
            templateId = 11, name = "早起", checked = true, streak = 12,
            recent = listOf(true, true, true, true, true, true, true),
            monthCount = 18, longest = 21, weekHits = 7
        ),
        HabitWidgetProvider.HabitRow(
            templateId = 12, name = "阅读", checked = false, streak = 5,
            recent = listOf(true, true, true, true, true, true, false),
            monthCount = 8, longest = 9, weekHits = 5
        ),
        HabitWidgetProvider.HabitRow(
            templateId = 13, name = "运动", checked = true, streak = 3,
            recent = listOf(true, true, true, false, true, true, true),
            monthCount = 4, longest = 7, weekHits = 3
        ),
        HabitWidgetProvider.HabitRow(
            templateId = 14, name = "冥想", checked = false, streak = 2,
            recent = listOf(false, true, true, false, false, false, false),
            monthCount = 6, longest = 5, weekHits = 0
        ),
        HabitWidgetProvider.HabitRow(
            templateId = 15, name = "早睡", checked = false, streak = 0,
            recent = listOf(false, false, true, false, true, false, false),
            monthCount = 3, longest = 4, weekHits = 0
        )
    )

    private fun habitViews(): RemoteViews =
        HabitWidgetProvider().bindViews(context, 1, habitRows(), 340, 148)

    private fun nextUpSquareViews(): RemoteViews =
        NextUpWidgetProvider().bindViews(context, 1, nextUpFocus(), 160, 160)

    private fun habitSquareViews(): RemoteViews =
        HabitWidgetProvider().bindViews(context, 1, habitRows(), 160, 160)



    /** 计数器（v22 起是目标进度口径）：主计数 喝水 6/8 + 副计数 咖啡 2 次 + 烟 0 支。 */
    private fun goalCounters(): List<LifeCounterWidgetProvider.GoalCounter> = listOf(
        LifeCounterWidgetProvider.GoalCounter(
            name = "今日喝水", current = 6.0, target = 8.0, unit = "杯", fraction = 0.75f, decreasing = false
        ),
        LifeCounterWidgetProvider.GoalCounter(
            name = "咖啡", current = 2.0, target = null, unit = "次", fraction = null, decreasing = false
        ),
        LifeCounterWidgetProvider.GoalCounter(
            name = "烟", current = 0.0, target = null, unit = "支", fraction = null, decreasing = false
        )
    )

    private fun counterViews(): RemoteViews =
        LifeCounterWidgetProvider().bindViews(context, 1, goalCounters(), 340, 148)

    private fun counterSquareViews(): RemoteViews =
        LifeCounterWidgetProvider().bindViews(context, 1, goalCounters(), 160, 160)

    private fun counterBarViews(): RemoteViews =
        LifeCounterWidgetProvider().bindViews(context, 1, goalCounters(), 340, 74)


    /**
     * 接下来样例（稿 §02，日期按生成当天）：焦点 = 爸爸生日（基准日 + 8 天，还有 8 天、
     * 建项至今 62%、事件日 09:00）；4×4 日历条覆盖事件日前 9 天到事件日（今天描边、事件日实底）。
     */
    private fun nextUpFocus(): NextUpWidgetProvider.NextUpFocus {
        val target = previewToday.plusDays(8)
        return NextUpWidgetProvider.NextUpFocus(
            kind = NextUpWidgetProvider.Kind.COUNTDOWN,
            title = "爸爸生日",
            meta = target.toString(),
            bigValue = "8",
            progress = 0.62f,
            reminder = "09:00",
            daysLeft = 8,
            targetDate = target
        )
    }

    /**
     * 4×4 底部四行明细（稿 §02 数值）：预约体检 / 还信用卡（同日 10/25，按时刻排）/
     * 团队周会 10/28 / 爸爸生日 11/1（全天）。行色轮换 c1/c4/c2/c5 由 Provider 给，
     * 这里只提供日期/名称/时刻。
     */
    private fun nextUpEvents(): List<NextUpWidgetProvider.UpcomingEvent> = listOf(
        NextUpWidgetProvider.UpcomingEvent(previewToday.plusDays(1), "预约体检", "09:00", 0),
        NextUpWidgetProvider.UpcomingEvent(previewToday.plusDays(1), "还信用卡", "10:00", 3),
        NextUpWidgetProvider.UpcomingEvent(previewToday.plusDays(4), "团队周会", "14:00", 1),
        NextUpWidgetProvider.UpcomingEvent(previewToday.plusDays(8), "爸爸生日", null, 4)
    )

    private fun nextUpViews(): RemoteViews =
        NextUpWidgetProvider().bindViews(context, 1, nextUpFocus(), 340, 148)

    private fun nextUpBarViews(): RemoteViews =
        NextUpWidgetProvider().bindViews(context, 1, nextUpFocus(), 340, 74)


    /** 订阅（v22）：三笔待扣（首笔 0 天 → 提色高亮）。 */
    private fun renewals(): List<SubscriptionWidgetProvider.Renewal> = listOf(
        SubscriptionWidgetProvider.Renewal("云盘会员", "25", 3),
        SubscriptionWidgetProvider.Renewal("视频会员", "15", 5),
        SubscriptionWidgetProvider.Renewal("音乐会员", "8", 7)
    )

    /**
     * 订阅 4×4 样例（稿 §02，日期按生成当天）：云盘 ¥25/月 12 号、视频 ¥15/月 17 号
     * （均已于 9 月扣过 → 下个 10/12、10/17）、网盘 ¥120/年 2 月扣（峰在 2027-02）。
     * 投影（未来 6 个月）：[40,40,40,40,160,40] 元；年度（12 个月）= ¥600。
     */
    /**
     * 订阅 4×4 样例：三笔——两笔月付（其中一笔扣费日排在**基准日之后几天**，
     * 这样「本月」KPI 与柱图首月非 0，不会摆出一条空的当月柱）、一笔年付（制造峰月）。
     * 日期一律相对 [previewToday] 推，不写死年月日。
     */
    private fun subEntries(): List<SubscriptionWidgetProvider.SubEntry> = listOf(
        SubscriptionWidgetProvider.SubEntry(
            name = "云盘会员", priceText = "¥25", amount = 25.0,
            billingDay = (previewToday.dayOfMonth + 2).coerceAtMost(28), cycle = "monthly",
            lastBilled = previewToday.minusMonths(1)
        ),
        SubscriptionWidgetProvider.SubEntry(
            name = "视频会员", priceText = "¥15", amount = 15.0,
            billingDay = (previewToday.dayOfMonth + 7).coerceAtMost(29), cycle = "monthly",
            lastBilled = previewToday.minusMonths(1)
        ),
        SubscriptionWidgetProvider.SubEntry(
            name = "网盘年度", priceText = "¥120", amount = 120.0,
            billingDay = (previewToday.dayOfMonth + 4).coerceAtMost(30), cycle = "yearly",
            lastBilled = previewToday.minusMonths(8)
        )
    )

    private fun subscriptionViews(): RemoteViews =
        SubscriptionWidgetProvider().bindViews(context, 1, renewals(), 340, 148)

    private fun subscriptionSquareViews(): RemoteViews =
        SubscriptionWidgetProvider().bindViews(context, 1, renewals(), 160, 160)

    private fun subscriptionBarViews(): RemoteViews =
        SubscriptionWidgetProvider().bindViews(context, 1, renewals(), 340, 74)


    /** 物品（v22）：4 个分类共 11 件，分布对齐设计稿 §5 样例（5/3/2/1 → seg 46/27/18/9%）。 */
    private fun assetCategories(): List<HeldCategoryCount> = listOf(
        HeldCategoryCount("DIGITAL", 5),
        HeldCategoryCount("SPORTS", 3),
        HeldCategoryCount("FURNITURE", 2),
        HeldCategoryCount("BOOKS", 1)
    )

    /**
     * 物品 4×4 样例（稿 §02，日期按生成当天）：38 件 · 12 类，最多 数码 8；
     * 分段条 = 前五（21/16/13/11/8%）+ 其余灰（31%）；12 格 = 数码8/服饰6/家具5/图书4/
     * 家电3/美妆3/厨具2/工具2/运动1/文具1/宠物1/交通2。
     */
    private fun assetCategoriesTall(): List<HeldCategoryCount> = listOf(
        HeldCategoryCount("DIGITAL", 8),
        HeldCategoryCount("CLOTHING", 6),
        HeldCategoryCount("FURNITURE", 5),
        HeldCategoryCount("BOOKS", 4),
        HeldCategoryCount("APPLIANCE", 3),
        HeldCategoryCount("COSMETICS", 3),
        HeldCategoryCount("FOOD", 2),
        HeldCategoryCount("TOOLS", 2),
        HeldCategoryCount("SPORTS", 1),
        HeldCategoryCount("STATIONERY", 1),
        HeldCategoryCount("PET", 1),
        HeldCategoryCount("TRANSPORT", 2)
    )

    /** 物品 4×4 的 footer「最近添加」样例（稿 §02：iPhone 充电线 10/22）。 */
    private fun assetRecent() = AssetRecent("iPhone 充电线", "10/22")

    private fun assetViews(): RemoteViews =
        AssetWidgetProvider().bindViews(context, 1, assetCategories(), 340, 148)

    private fun assetSquareViews(): RemoteViews =
        AssetWidgetProvider().bindViews(context, 1, assetCategories(), 160, 160)

    private fun assetBarViews(): RemoteViews =
        AssetWidgetProvider().bindViews(context, 1, assetCategories(), 340, 74)


    private fun shortcutsViews(): RemoteViews =
        ShortcutsWidgetProvider().bindViews(context, 1, 160, 160)

    /**
     * 净资产样例：稿 §5/§6 数值 —— 净值 ¥34,180（2×2 折「¥3.4万」）、资产 ¥41,000、负债 ¥6,820、
     * 涨跌 ▲2.0%（月净收入 67,020 ÷ 期初净值 3,350,980）。
     */
    private fun netWorthSquareViews(): RemoteViews = NetWorthWidgetProvider().bindViews(
        context, 1, totalBalance = 3_418_000, liabs = 682_000, widthDp = 160, monthDelta = 67_020, heightDp = 160
    )

    private fun netWorthWideViews(): RemoteViews = NetWorthWidgetProvider().bindViews(
        context, 1, totalBalance = 3_418_000, liabs = 682_000, widthDp = 340, monthDelta = 67_020, heightDp = 148
    )

    private fun netWorthBarViews(): RemoteViews = NetWorthWidgetProvider().bindViews(
        context, 1, totalBalance = 3_418_000, liabs = 682_000, widthDp = 340, monthDelta = 67_020, heightDp = 74
    )

    /**
     * 净资产 4×4 的近 12 个月月末净值序列（稿 §02 数值，旧→新，末位 = 当前净值 3_418_000）：
     * 柱高对稿 35/42/48/45/58/65/62/75/82/78/90/100%，末柱（当前月）青 + 「34.2k」标。
     */
    private fun netWorthMonthlySeries(): List<Long> = listOf(
        1_198_000, 1_436_000, 1_640_000, 1_538_000, 1_983_000, 2_222_000,
        2_120_000, 2_563_000, 2_803_000, 2_667_000, 3_076_000, 3_418_000
    )

    /**
     * 预览图基准日 = **生成当天**。
     *
     * 曾经钉死成设计稿的 2026-10-24，但 provider 内部一律 `LocalDate.now()`（头部日期、柱图轴、
     * 日历条 today 格、待办「今天」桶都是），fixture 钉死就会随天数漂出矛盾：跨过午夜后头部是
     * 10/25、待办近 7 天末柱却是 10/24 的 0，预览图自相矛盾。改成跟随当天后两边同一基准，
     * 稿里的样例结构（数值、构图、KPI 口径）不变，只有日期字符串跟着走。
     */
    private val previewToday: LocalDate = LocalDate.now()

    /** 「周六」这种星期文案（与 DashboardSnapshot.weekday 的取值格式一致）。 */
    private fun weekdayLabel(day: LocalDate): String =
        "周" + "一二三四五六日"[day.dayOfWeek.value - 1]

    /** 预览基准日 HH:mm 的 epoch 毫秒（待办行的时间列用）。 */
    private fun todayAt(hour: Int, minute: Int): Long =
        previewToday.atTime(hour, minute).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 任意一天 HH:mm 的 epoch 毫秒（待办历史完成行的归天用）。 */
    private fun dayAt(day: LocalDate, hour: Int, minute: Int): Long =
        day.atTime(hour, minute).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * 一张形态出**四层**（某层全透明就不出图）：
     * - `widget_preview_x.png` = **内容层**（文字/图标/进度），预览里恒按实色画；
     * - `widget_preview_x_surface.png` = **面板层**（中性底，跟着不透明度淡出）；
     * - `widget_preview_x_blocks.png` = **色砖层**（会盖住卡面的内层色砖）；
     * - `widget_preview_x_chip.png` = **胶囊层**（语义 tint 胶囊的底）。
     *
     * 各层都从**同一棵视图树**量出来（抹掉用 alpha=0，不动尺寸/内边距），像素才对得齐。
     */
    private fun renderEntry(name: String, width: Int, height: Int, views: RemoteViews) {
        writePng("widget_entry_$name", width, height, views) { }
    }

    private fun render(name: String, width: Int, height: Int, views: RemoteViews) {
        // 系统选择器用的「整卡」图：卡底 + 全部内容（`android:previewImage` 指这张）
        writePng("widget_preview_${name}_card", width, height, views, keepCardBg = true) { }
        writePng("widget_preview_$name", width, height, views) { root -> hideSurfaces(root) }
        writePng("widget_preview_${name}_surface", width, height, views) { root -> hideContent(root) }
        writePng("widget_preview_${name}_blocks", width, height, views, skipIfEmpty = true) { root ->
            hideBlocks(root)
        }
        writePng("widget_preview_${name}_chip", width, height, views, skipIfEmpty = true) { root ->
            hideChip(root)
        }
    }

    private fun writePng(
        fileName: String,
        width: Int,
        height: Int,
        views: RemoteViews,
        skipIfEmpty: Boolean = false,
        keepCardBg: Boolean = false,
        mutate: (View) -> Unit
    ) {
        val parent = FrameLayout(context)
        val view = views.apply(context, parent)
        // 卡底默认清成透明：应用内画廊的预览由 Compose 按用户所选背景色垫底，PNG 只保留内容。
        // 但系统选择器（`android:previewImage`）是把整张图当成品画的，没有卡底就是「漂浮的文字」，
        // 所以另出一份 [keepCardBg] 的 `_card` 图给 info 文件用。
        if (!keepCardBg) (view.findViewById<View>(R.id.widget_layout) ?: view).background = null
        mutate(view)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        if (skipIfEmpty && !bitmap.hasVisiblePixel()) {
            File(outDir, "$fileName.png").delete()
            return
        }
        // `_card` 只给系统选择器的 `previewImage` 用：那里按 ~200dp 宽显示，3 倍图纯属白占体积
        // （40 张 ×2 套 ≈ 3.5MB），降到 2/3（等效 2 倍图）后肉眼无差、体积减半
        val output = if (keepCardBg) {
            Bitmap.createScaledBitmap(bitmap, width * 2 / 3, height * 2 / 3, true)
        } else {
            bitmap
        }
        val file = File(outDir, "$fileName.png")
        FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("widget preview -> ${file.absolutePath} (${file.length()} bytes)")
    }

    /** 色砖层是否真的画了东西：全透明的 PNG 不进 res（大多数形态没有色砖）。 */
    private fun Bitmap.hasVisiblePixel(): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (getPixel(x, y) ushr 24 != 0) return true
            }
        }
        return false
    }

    /** 内容层：把面板层的东西抹掉（alpha 0，尺寸/内边距不变，两层才对得齐）。 */
    private fun hideSurfaces(view: View) {
        if (WidgetToneSweep.isSurface(view, context)) view.background?.alpha = 0
        if (view is ImageView && view.id in WidgetOpacityFade.blockIds) view.imageAlpha = 0
        if (view.id in WidgetToneSweep.chipIds) view.background?.alpha = 0
        forEachChild(view) { hideSurfaces(it) }
    }

    /** 面板层：只留中性底（色砖已单独一层），文字/图标/语义色底全抹掉。 */
    private fun hideContent(view: View) {
        if (!WidgetToneSweep.isSurface(view, context)) view.background?.alpha = 0
        when {
            view is ImageView -> view.imageAlpha = 0
            view is TextView -> hideText(view)
        }
        forEachChild(view) { hideContent(it) }
    }

    /** 色砖层：只留「会盖住卡面的内层色砖」，其余底色/文字/图标全抹掉。 */
    private fun hideBlocks(view: View) {
        view.background?.alpha = 0
        when {
            view is ImageView && view.id !in WidgetOpacityFade.blockIds -> view.imageAlpha = 0
            view is TextView -> hideText(view)
        }
        forEachChild(view) { hideBlocks(it) }
    }

    /** 胶囊层：只留语义 tint 胶囊的**底**，其余全抹掉；文字归内容层。 */
    private fun hideChip(view: View) {
        if (view.id !in WidgetToneSweep.chipIds) view.background?.alpha = 0
        when {
            view is ImageView -> view.imageAlpha = 0
            view is TextView -> hideText(view)
        }
        forEachChild(view) { hideChip(it) }
    }

    /** 抹掉文字：`setTextColor(TRANSPARENT)` 管不到 `ForegroundColorSpan`，span 原地换透明。 */
    private fun hideText(view: TextView) {
        val text = view.text
        if (text is Spanned) {
            val copy = SpannableString(text)
            copy.getSpans(0, copy.length, ForegroundColorSpan::class.java).forEach { span ->
                copy.setSpan(
                    ForegroundColorSpan(Color.TRANSPARENT),
                    copy.getSpanStart(span),
                    copy.getSpanEnd(span),
                    copy.getSpanFlags(span)
                )
            }
            view.setText(copy, TextView.BufferType.SPANNABLE)
        }
        view.setTextColor(Color.TRANSPARENT)
    }

    private inline fun forEachChild(view: View, block: (View) -> Unit) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) block(view.getChildAt(i))
        }
    }
}
