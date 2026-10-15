package com.palmnote.ui.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.data.db.dao.HeldCategoryCount
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.LifeItem
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 选择器静态预览图（`previewImage`）生成器：真实布局 + 样例数据离屏渲染。
 *
 * 默认跳过（CI 不跑），重出预览图时手动触发：
 * ```
 * WIDGET_PREVIEWS=1 ./gradlew --no-daemon :app:testDebugUnitTest --tests "*WidgetPreviewPngGenerator*"
 * ```
 * 产出 `app/build/widget-previews/`（浅色）与 `app/build/widget-previews-night/`（深色）下的 PNG，
 * 逐张目检后分别拷进 `app/src/main/res/drawable-nodpi/` 与 `app/src/main/res/drawable-night-nodpi/`。
 * 两份同名，系统按当前明暗自动挑一份，页面代码不用管。
 *
 * 画布按**设计稿网格**给（3× 密度，与 `dist/widget-design-v2/design-v4.html` 的
 * `.s11/.s21/.s22/.s32/.s41/.s43` 一一对应：1 格宽 82dp、1 格高 74dp），
 * 这样选择器里看到的预览比例和设计稿完全一致：
 * 3 格宽 = 245dp、4 格宽 = 340dp、2 格宽 = 160dp、1 格 = 82dp；
 * 3×2 = 245×148、4×3 = 340×268、2×1 = 160×74、2×2 = 160×160、1×1 = 82×82、4×1 = 340×74。
 * 之前用过 240×160dp 和 258×190dp 两版，前者窄到挤截断文字，后者比设计稿矮胖
 * （1.36 vs 1.66 宽高比），用户对比设计稿一眼就看出比例不对。
 * 语言取 zh：预览文案是用户看到的界面文案，不跟测试机 locale 走。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPreviewPngGenerator {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val accent = WidgetData.AccentTheme(accent = 0xFF0891B2.toInt(), onAccent = 0xFFFFFFFF.toInt())
    /** 输出目录：浅色一套、深色一套，由 [renderAll] 按当前限定符指定。 */
    private var outDir = File("build/widget-previews")

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

        // 3×2 = 245×148dp：设计稿 s32 网格（下同，×3 密度换算成 px）
        render("bill", 735, 444, billViews())
        // 迷你档不进选择器（没有 previewImage），这里出图只为目检（2×1 = 160×74dp）
        render("bill_mini", 480, 222, billMiniViews())
        // 4×3 = 340×268dp：设计稿 s43 网格
        render("dashboard", 1020, 804, dashboardViews())
        render("todo", 1020, 804, todoViews())
        render("habit", 1020, 804, habitViews())
        // 倒计时是 3×2（稿子口径）：画布按 3 格宽给
        render("counter", 735, 444, counterViews())
        render("asset", 735, 444, assetViews())
        // 2×2 = 160×160dp、4×1 = 340×74dp：设计稿 s22 / s41
        render("shortcuts", 480, 480, shortcutsViews())
        render("shortcuts_wide", 1020, 222, shortcutsWideViews())
        // 总资产是 2×1 = 160×74dp：设计稿 s21
        render("net_worth", 480, 222, netWorthViews())
        render("next_up", 735, 444, nextUpViews())
        render("subscription", 735, 444, subscriptionViews())
        // 1×1 = 82×82dp：设计稿 s11
        render("vault", 246, 246, vaultViews())
    }

    // ── 各组件：与线上 onUpdateAsync 同源（同样的 bind + 同样的运行时着色） ──

    private fun billSnapshot() = BillWidgetProvider.BillSnapshot(
        monthlyExpense = 820_000,
        monthlyIncome = 5_000_000,
        budget = Budget(yearMonth = "2026-10", totalBudget = 1_000_000),
        topCategory = "餐饮",
        dailyExpense = listOf(12_000, 4_500, 38_000, 0, 21_500, 62_000, 15_800)
    )

    private fun billViews(): RemoteViews = BillWidgetProvider().bindViews(context, 1, billSnapshot())

    private fun billMiniViews(): RemoteViews =
        BillWidgetProvider().bindMiniViews(context, 1, billSnapshot(), accent)

    private fun dashboardViews(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
        DashboardWidgetProvider().bindSnapshot(
            context,
            views,
            DashboardWidgetProvider.DashboardSnapshot(
                weekTodoCount = 5,
                budgetCardAmount = WidgetData.formatMoneyCompact(context, 180_000),
                budgetCardSub = context.getString(R.string.widget_budget_remaining),
                goalPct = "64%",
                goalName = "跑步 5km",
                goalCounts = "今日 3.2 / 5 km",
                todoRemaining = 2,
                todoDoneToday = 2,
                overdueCount = 1,
                anniversaryTitle = "爸爸生日",
                anniversaryDays = 20,
                anniversaryDateText = "11/1 · 周日",
                budgetPercent = 82
            )
        )
        val families = (0..3).map { WidgetData.colorFamily(context, it) }
        views.setInt(R.id.widget_dashboard_icon_bg, "setColorFilter", accent.accent)
        views.setTextColor(R.id.widget_dashboard_week_todo, families[0].hue)
        listOf(
            Triple(R.id.widget_dashboard_budget_tint, R.id.widget_dashboard_budget_dot, R.id.widget_dashboard_budget),
            Triple(R.id.widget_dashboard_goal_tint, R.id.widget_dashboard_goal_dot, R.id.widget_dashboard_goal_pct),
            Triple(R.id.widget_dashboard_todo_tint, R.id.widget_dashboard_todo_dot, R.id.widget_dashboard_todo),
            Triple(R.id.widget_dashboard_anniversary_tint, R.id.widget_dashboard_anniversary_dot, R.id.widget_dashboard_days)
        ).forEachIndexed { index, (tintId, dotId, valueId) ->
            views.setInt(tintId, "setColorFilter", families[index].tint)
            views.setInt(dotId, "setColorFilter", families[index].hue)
            views.setTextColor(valueId, families[index].hue)
        }
        // 预算 tile 的迷你用量条（样例 82%）
        views.setInt(R.id.widget_dashboard_budget_bar_track, "setColorFilter", families[0].hue)
        views.setInt(R.id.widget_dashboard_budget_bar_track, "setImageLevel", 10_000 / 4)
        views.setInt(R.id.widget_dashboard_budget_bar_fill, "setColorFilter", families[0].hue)
        views.setInt(R.id.widget_dashboard_budget_bar_fill, "setImageLevel", 82 * 100)
        // 目标 tile 的迷你进度条（样例 64%），与运行时 bindViews 同款着色
        views.setInt(R.id.widget_dashboard_goal_bar_track, "setColorFilter", families[1].hue)
        views.setInt(R.id.widget_dashboard_goal_bar_track, "setImageLevel", 10_000 / 4)
        views.setInt(R.id.widget_dashboard_goal_bar_fill, "setColorFilter", families[1].hue)
        views.setInt(R.id.widget_dashboard_goal_bar_fill, "setImageLevel", 64 * 100)
        return views
    }

    /**
     * 待办组件改滚动列表后，离屏渲染拿不到列表内容（ListView 由桌面填充），
     * 所以预览出选择器实际用的静态预览布局。
     */
    private fun todoViews(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_todo_preview)

    /** 习惯 4×3：样例取 3 条（4 条会超出 240×160dp 画布被裁）。 */
    private fun habitViews(): RemoteViews = HabitWidgetProvider().bindViews(
        context,
        1,
        listOf(
            // 样例必须自洽：recent 图案 ↔ streak（从今天往回连着的格数）、
            // weekHits（今天周一 → 本周只有今天）、monthCount/longest ≥ 可信下限，
            // 否则预览里会出现「方柱亮着却写 0/7」这类穿帮
            HabitWidgetProvider.            HabitRow(
                templateId = 11, name = "跑步 5km", checked = true, streak = 12,
                recent = listOf(true, true, false, true, true, true, true),
                monthCount = 18, longest = 21, weekHits = 1
            ),
            HabitWidgetProvider.HabitRow(
                templateId = 12, name = "阅读 30min", checked = false, streak = 2,
                recent = listOf(false, false, false, true, true, true, false),
                monthCount = 8, longest = 5, weekHits = 0
            ),
            HabitWidgetProvider.HabitRow(
                templateId = 13, name = "喝水 8 杯", checked = false, streak = 0,
                recent = listOf(false, false, false, true, false, false, false),
                monthCount = 4, longest = 1, weekHits = 0
            ),
            HabitWidgetProvider.HabitRow(
                templateId = 14, name = "早睡", checked = true, streak = 6,
                recent = listOf(false, true, true, true, true, true, true),
                monthCount = 15, longest = 9, weekHits = 1
            ),
            HabitWidgetProvider.HabitRow(
                templateId = 15, name = "冥想 10 分钟", checked = false, streak = 2,
                recent = listOf(true, false, true, false, true, true, false),
                monthCount = 7, longest = 3, weekHits = 0
            )
        )
    )

    private fun counterViews(): RemoteViews {
        // 固定基准日而不是 now()：否则每次重出预览图，倒计时里的日期都会跟着当天漂，
        // 预览图（要提交进仓库的）就永远在变，diff 里全是噪声
        val today = LocalDate.of(2026, 10, 9)
        val views = LifeCounterWidgetProvider().counterViews(
            context,
            1,
            listOf(
                // 爸爸生日 20 天：与概览卡同一事件同一数（设计稿跨卡对齐）
                LifeCounterWidgetProvider.CounterEvent(1, "爸爸生日", 20, today.plusDays(20)),
                LifeCounterWidgetProvider.CounterEvent(2, "妈妈生日", 40, today.plusDays(40))
            ),
            isLarge = false
        )
        return views
    }

    /** 物品 3×2：样例与静态预览布局同源（6 个分类、共 11 件，分布对齐设计稿 4/3/1/1/1/1）。 */
    private fun assetViews(): RemoteViews = AssetWidgetProvider().bindViews(
        context,
        1,
        listOf(
            // 用 DB 里的规范代码（组件按 getCategoryName 翻译成中文，写中文名会落成「其他」）
            HeldCategoryCount("DIGITAL", 4),
            HeldCategoryCount("SPORTS", 3),
            HeldCategoryCount("FURNITURE", 1),
            HeldCategoryCount("BOOKS", 1),
            HeldCategoryCount("MUSICAL", 1),
            HeldCategoryCount("PHOTOGRAPHY", 1)
        )
    )

    /**
     * 总资产 2×1：样例值取「账户余额合计」；画布 160×74dp（设计稿 s21），
     * 高度要按设计稿给足，否则三段式的第三行会被当成「缩到最小格」收起。
     */
    private fun netWorthViews(): RemoteViews = NetWorthWidgetProvider().bindViews(
        context,
        1,
        totalBalance = 1_285_000,
        widthDp = 160,
        monthDelta = 26_400,
        heightDp = 74
    )

    /** 订阅提醒 3×2：样例取四笔近期扣费（首笔当天 → 红色「即将扣费」，与设计稿一致）。 */
    private fun subscriptionViews(): RemoteViews = SubscriptionWidgetProvider().bindViews(
        context,
        1,
        listOf(
            SubscriptionWidgetProvider.Renewal("云盘会员", "25", 0),
            SubscriptionWidgetProvider.Renewal("视频会员", "15", 5),
            SubscriptionWidgetProvider.Renewal("音乐会员", "8", 9),
            SubscriptionWidgetProvider.Renewal("云存储", "6", 13)
        )
    )

    private fun shortcutsViews(): RemoteViews = ShortcutsWidgetProvider().bindViews(context, 1)

    /** 快捷入口 4×1 横条：同一批数据，只切布局档（目检用）。 */
    private fun shortcutsWideViews(): RemoteViews = ShortcutsWidgetProvider().bindViews(context, 1, wide = true)

    /** 密码本已缩为 1×1 纯入口（图标 + 名称），预览出的是缩档布局（与 info 的 previewLayout 同源）。 */
    private fun vaultViews(): RemoteViews = VaultWidgetProvider().bindSmallViews(context, 1)

    private fun render(name: String, width: Int, height: Int, views: RemoteViews) {
        val parent = FrameLayout(context)
        val view = views.apply(context, parent)
        // 按画布尺寸定死测量：带 weight 的组件（快捷入口 2×2、概览 4×3）要铺满画布才像真机，
        // 所以画布本身要按组件的真实格子给足高度（见上面各 render 调用的尺寸），否则会裁掉内容。
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File(outDir, "widget_preview_$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("widget preview -> ${file.absolutePath} (${file.length()} bytes)")
    }

    private fun nextUpViews(): RemoteViews = NextUpWidgetProvider().bindViews(
        context,
        1,
        NextUpWidgetProvider.NextUpFocus(
            kind = NextUpWidgetProvider.Kind.RENEWAL,
            // 与订阅卡首笔同源（云盘会员 ¥25、当天扣费，设计稿「待扣费」卡口径）
            title = "云盘会员",
            meta = LocalDate.now().format(DateTimeFormatter.ofPattern("M/d")),
            bigValue = "¥25"
        )
    )
}
