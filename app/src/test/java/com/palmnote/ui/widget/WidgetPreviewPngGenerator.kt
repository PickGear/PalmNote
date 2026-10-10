package com.palmnote.ui.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.Goal
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
 * 画布按**真机实测尺寸**给（3× 密度）：393dp 宽的机器上 1 格宽 ≈ 82dp、1 格高 ≈ 74dp，
 * 所以 4 格宽 = 340dp、3 格宽 = 245dp、2 格宽 = 160dp、1 格宽 = 82dp。
 * 之前一律用 240×160dp，比真机窄得多，预览里的文字会被挤到截断、大档那一行还会被判成小档。
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

        render("bill", 735, 444, billViews())
        // 迷你档不进选择器（没有 previewImage），这里出图只为目检
        render("bill_mini", 480, 222, billMiniViews())
        // 概览是 4×3：真机格子比 240×160dp 画布高，画布加高，否则第二行会被裁掉
        render("dashboard", 1020, 666, dashboardViews())
        render("todo", 1020, 666, todoViews())
        render("habit", 1020, 666, habitViews())
        render("counter", 1020, 666, counterViews())
        render("asset", 735, 444, assetViews())
        render("shortcuts", 480, 444, shortcutsViews())
        // 总资产是 2×1：按真实格子给画布
        render("net_worth", 480, 222, netWorthViews())
        render("subscription", 735, 444, subscriptionViews())
        render("vault", 246, 222, vaultViews())
    }

    // ── 各组件：与线上 onUpdateAsync 同源（同样的 bind + 同样的运行时着色） ──

    private fun billSnapshot() = BillWidgetProvider.BillSnapshot(
        monthlyExpense = 820_000,
        monthlyIncome = 5_000_000,
        budget = Budget(yearMonth = "2026-10", totalBudget = 1_000_000),
        topCategory = "餐饮",
        dailyExpense = listOf(12_000, 4_500, 38_000, 0, 21_500, 62_000, 15_800)
    )

    private fun billViews(): RemoteViews = BillWidgetProvider().bindViews(context, 1, billSnapshot(), accent)

    private fun billMiniViews(): RemoteViews =
        BillWidgetProvider().bindMiniViews(context, 1, billSnapshot(), accent)

    private fun dashboardViews(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
        DashboardWidgetProvider().bindSnapshot(
            context,
            views,
            DashboardWidgetProvider.DashboardSnapshot(
                dateText = "10月6日 周二",
                budgetCardAmount = WidgetData.formatMoneyCompact(context, 172_000),
                budgetCardSub = context.getString(R.string.widget_budget_remaining),
                goalPct = "64%",
                goalName = "跑步 5km",
                todoRemaining = 2,
                anniversaryTitle = "妈妈生日",
                anniversaryDays = 12,
                budgetPercent = 82,
                goalPercent = 64
            )
        )
        val families = (0..3).map { WidgetData.colorFamily(context, it) }
        views.setInt(R.id.widget_dashboard_icon_bg, "setColorFilter", accent.accent)
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
        // 三层环：轨道同色相淡化、进度弧实色（样例 82 / 64 / 67）
        listOf(
            Triple(R.id.widget_dashboard_budget_ring_track, R.id.widget_dashboard_budget_ring, 82),
            Triple(R.id.widget_dashboard_goal_ring_track, R.id.widget_dashboard_goal_ring, 64),
            Triple(R.id.widget_dashboard_todo_ring_track, R.id.widget_dashboard_todo_ring, 67)
        ).forEachIndexed { index, (trackId, arcId, percent) ->
            val hue = families[index].hue
            views.setInt(trackId, "setColorFilter", (hue and 0x00FFFFFF) or (0x42 shl 24))
            views.setInt(arcId, "setColorFilter", hue)
            views.setInt(arcId, "setImageLevel", percent * 100)
        }
        views.setTextViewText(R.id.widget_dashboard_ring_text, "82%")
        return views
    }

    /**
     * 待办组件改滚动列表后，离屏渲染拿不到列表内容（ListView 由桌面填充），
     * 所以预览出选择器实际用的静态预览布局。
     */
    private fun todoViews(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_todo_preview).apply {
            setInt(R.id.widget_todo_ring, "setColorFilter", accent.accent)
            setInt(R.id.widget_todo_ring, "setImageLevel", 50 * 100)
            setTextViewText(R.id.widget_todo_ring_text, "50%")
        }

    /** 习惯 4×3：样例取 3 条（4 条会超出 240×160dp 画布被裁）。 */
    private fun habitViews(): RemoteViews = HabitWidgetProvider().bindViews(
        context,
        1,
        listOf(
            HabitWidgetProvider.HabitRow(
                templateId = 11, name = "跑步 5km", checked = true, streak = 12,
                recent = listOf(true, true, false, true, true, true, true)
            ),
            HabitWidgetProvider.HabitRow(
                templateId = 12, name = "阅读 30min", checked = false, streak = 3,
                recent = listOf(false, true, false, true, true, false, false)
            ),
            HabitWidgetProvider.HabitRow(
                templateId = 13, name = "喝水 8 杯", checked = false, streak = 0,
                recent = listOf(false, false, false, true, false, false, false)
            )
        ),
        accent = accent
    )

    private fun counterViews(): RemoteViews {
        // 固定基准日而不是 now()：否则每次重出预览图，倒计时里的日期都会跟着当天漂，
        // 预览图（要提交进仓库的）就永远在变，diff 里全是噪声
        val today = LocalDate.of(2026, 10, 9)
        val views = LifeCounterWidgetProvider().counterViews(
            context,
            1,
            listOf(
                // 12 天后生日：本期已过 353/365 ≈ 96%
                LifeCounterWidgetProvider.CounterEvent(1, "妈妈生日", 12, today.plusDays(12), progressPercent = 96),
                LifeCounterWidgetProvider.CounterEvent(2, "结婚纪念日", 48, today.plusDays(48), progressPercent = 87)
            ),
            isLarge = false
        )
        views.setInt(R.id.widget_counter_focus_bg, "setColorFilter", accent.accent)
        return views
    }

    private fun assetViews(): RemoteViews =
        AssetWidgetProvider().bindViews(context, 1, heldCount = 6, totalValue = 1_285_000)

    /** 总资产 2×1：样例值取「账户余额合计」。 */
    private fun netWorthViews(): RemoteViews =
        NetWorthWidgetProvider().bindViews(context, 1, totalBalance = 1_285_000, widthDp = 110)

    /** 订阅提醒 3×2：样例取三条近期扣费。 */
    private fun subscriptionViews(): RemoteViews = SubscriptionWidgetProvider().bindViews(
        context,
        1,
        listOf(
            SubscriptionWidgetProvider.Renewal("云盘会员", "25", 4),
            SubscriptionWidgetProvider.Renewal("视频会员", "15", 12),
            SubscriptionWidgetProvider.Renewal("音乐会员", "8", 23)
        )
    )

    private fun shortcutsViews(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_shortcuts_unified)
        views.setInt(R.id.widget_sc_add_bg, "setColorFilter", accent.accent)
        return views
    }

    /** 密码本已缩为 1×1，预览出的是缩档布局（与 info 的 previewLayout 同源）。 */
    private fun vaultViews(): RemoteViews = VaultWidgetProvider().bindSmallViews(context, 1, totalCount = 24)

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
}
