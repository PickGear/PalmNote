package com.palmnote.ui.widget

import android.app.Application
import android.appwidget.AppWidgetManager
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
 * 产出 `app/build/widget-previews/` 下的 PNG，逐张目检后拷进 `app/src/main/res/drawable-nodpi/`。
 *
 * 画布沿用既有约定 720×480（3× 密度 = 240×160dp）；1×1 的密码本用正方形 480×480。
 * 语言取 zh：预览文案是用户看到的界面文案，不跟测试机 locale 走。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPreviewPngGenerator {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = AppWidgetManager.getInstance(context)
    private val accent = WidgetData.AccentTheme(accent = 0xFF0891B2.toInt(), onAccent = 0xFFFFFFFF.toInt())
    private val outDir = File("build/widget-previews")

    @Test
    fun `重出全部小组件预览图`() {
        assumeTrue("仅手动触发：WIDGET_PREVIEWS=1", System.getenv("WIDGET_PREVIEWS") == "1")
        outDir.mkdirs()

        render("bill", 720, 480, billViews())
        // 迷你档不进选择器（没有 previewImage），这里出图只为目检
        render("bill_mini", 390, 180, billMiniViews())
        // 概览是 4×3：真机格子比 240×160dp 画布高，画布加高，否则第二行会被裁掉
        render("dashboard", 720, 640, dashboardViews())
        render("todo", 720, 480, todoViews())
        render("habit", 720, 480, habitViews())
        render("counter", 720, 480, counterViews())
        render("asset", 720, 480, assetViews())
        render("shortcuts", 720, 480, shortcutsViews())
        render("vault", 480, 480, vaultViews())
    }

    // ── 各组件：与线上 onUpdateAsync 同源（同样的 bind + 同样的运行时着色） ──

    private fun billSnapshot() = BillWidgetProvider.BillSnapshot(
        monthlyExpense = 820_000,
        monthlyIncome = 5_000_000,
        budget = Budget(yearMonth = "2026-10", totalBudget = 1_000_000),
        topCategory = "餐饮"
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
                budgetCardAmount = "¥1,720",
                budgetCardSub = context.getString(R.string.widget_budget_remaining),
                goalPct = "64%",
                goalName = "跑步 5km",
                todoRemaining = 2,
                anniversaryTitle = "妈妈生日",
                anniversaryDays = 12
            )
        )
        views.setInt(R.id.widget_dashboard_budget_bg, "setColorFilter", accent.accent)
        views.setInt(R.id.widget_dashboard_goal_bg, "setColorFilter", WidgetData.SEMANTIC_GOAL)
        views.setViewVisibility(R.id.widget_stats_row2, View.VISIBLE)
        return views
    }

    private fun todoViews(): RemoteViews {
        val today = System.currentTimeMillis()
        return TodoWidgetProvider().bindViews(
            context,
            manager,
            1,
            listOf(
                LifeItem(templateId = 1, title = "买牛奶", dueDate = today),
                LifeItem(templateId = 1, title = "交房租", dueDate = today),
                LifeItem(templateId = 1, title = "预约体检", status = "COMPLETED", dueDate = today)
            ),
            accent
        )
    }

    private fun habitViews(): RemoteViews = HabitWidgetProvider().bindViews(
        context,
        1,
        listOf(
            Goal(id = 11, title = "跑步 5km", streak = 12),
            Goal(id = 12, title = "阅读 30min", streak = 3),
            Goal(id = 13, title = "喝水 8 杯", streak = 0),
            Goal(id = 14, title = "冥想", streak = 7)
        ),
        checkedIds = setOf(11L, 14L),
        accent = accent
    )

    private fun counterViews(): RemoteViews {
        val today = LocalDate.now()
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
