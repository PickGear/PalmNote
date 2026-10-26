package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.widget.RemoteViews
import com.palmnote.app.R
import kotlinx.coroutines.delay

/**
 * 设置内添加小组件：走系统 requestPinAppWidget 通道（API 26+，minSdk 26 无需版本判断）。
 * App 无法静默往桌面放组件 —— 点击后由启动器弹系统确认框，确认后才落到桌面；
 * 每次请求只添加一个实例，桌面可以放同种组件的多份。
 * 启动器不支持时（isRequestPinAppWidgetSupported == false）界面降级为「从桌面添加」的引导文案。
 */
// LargeClass：这是纯数据目录（entries/三张映射表 + pin 通道），行数随四档形态数自然增长；
// 拆成多个 object 只会把同一张表的相关行搬到别处，不降低复杂度。
@Suppress("LargeClass")
object WidgetPin {

    /** 面板层资源名的后缀：`widget_preview_bill` → `widget_preview_bill_surface`。 */
    internal const val SURFACE_SUFFIX = "_surface"

    /**
     * 内层色砖层（概览四块砖 / 快捷入口四格）的后缀：颜色由组件给（色族 tint），
     * 只跟着实例不透明度淡出，不属于中性面板。单独一层是为了让面板层能当**白模**用
     * （见 [SURFACE_SUFFIX] 与卡片相对方案）。
     */
    internal const val BLOCKS_SUFFIX = "_blocks"

    /**
     * **胶囊层**的后缀：`widget_preview_bill` → `widget_preview_bill_chip`。
     *
     * 收的是语义 tint 胶囊（账单「新增」，id 见 `WidgetToneSweep.chipIds`）的**底**：
     * 它是语义色、不进中性扫色，但自选深色背景时要翻成浅色胶囊（2026-10-20 反馈）。
     * 与面板层同形、两种用法：默认卡底按原色叠（此时不透明度必为 100%，不用淡出）；
     * 卡片相对 + 深色卡时当白模，tint 成 88% 白叠在卡底上 —— 与真机换上的
     * `widget_pill_chip` 同值。文字留在内容层（预览里恒是深色那支，白胶囊上可读）。
     */
    internal const val CHIP_SUFFIX = "_chip"

    /**
     * 预览图的**面板层**（会跟着不透明度淡出的中性底），由 `WidgetPreviewPngGenerator`
     * 与内容层一起出图。
     *
     * 名字是运行时拼的，R8 的资源压缩会把它当无用资源删掉 —— `res/raw/keep.xml` 里显式保住了。
     * 返回 0 = 没有这一层（老资源/已裁掉），预览退回「卡底 + 内容」的单层画法。
     *
     * 同一张图**两种用法**：默认卡底按原色叠（不透明度当 alpha）；卡片相对时把它当白模，
     * tint 成极淡白/黑再按固定 6% 叠在卡底上（见 `WidgetToneSweep.surfaceOverlay`）。
     */
    internal fun surfacePreviewRes(resources: Resources, previewRes: Int): Int =
        previewLayerRes(resources, previewRes, SURFACE_SUFFIX)

    /** 内层色砖层；没有这层的形态（大多数）返回 0，预览跳过。 */
    internal fun blocksPreviewRes(resources: Resources, previewRes: Int): Int =
        previewLayerRes(resources, previewRes, BLOCKS_SUFFIX)

    /** 胶囊层（语义 tint 胶囊的底）；没有这层的形态返回 0，预览跳过。 */
    internal fun chipPreviewRes(resources: Resources, previewRes: Int): Int =
        previewLayerRes(resources, previewRes, CHIP_SUFFIX)

    private fun previewLayerRes(resources: Resources, previewRes: Int, suffix: String): Int =
        resources.getIdentifier(
            resources.getResourceEntryName(previewRes) + suffix,
            "drawable",
            resources.getResourcePackageName(previewRes)
        )

    /** 目录里的一行：桌面上的哪个组件、用哪张缩略图/卡面图与文案。 */
    data class Entry(
        val providerClass: Class<*>,
        val previewImageRes: Int,
        val titleRes: Int,
        val descRes: Int,
        /** 组件有配置页时填这里：pin 成功后要自己把它打开（见 requestPin 注释）。 */
        val configureActivity: Class<*>? = null,
        /** 渲染吃强调色（「编辑更多」层放配色卡）：账单/概览/接下来。 */
        val accentEditable: Boolean = false
    )

    /**
     * 目录分区：**按 App 的功能模块划**（2026-10-21 用户定：「按 app 功能模块分区，物品、账单、生活、
     * 总览、快捷入口……，分区里面还可以分」）—— 顺序与底部 tab 一致。
     * **密码本不设分区**：高危隐私，其小组件已整体下线（不再登记任何形态）。
     * 每个分区内的组件（本月记账、总资产…）再各自折叠出「一件主样式 + 多件备选样式」。
     */
    enum class WidgetGroup(val titleRes: Int) {
        BILLING(R.string.widget_group_billing),
        ASSET(R.string.widget_group_asset),
        LIFE(R.string.widget_group_life),
        DASHBOARD(R.string.widget_group_dashboard),
        SHORTCUTS(R.string.widget_group_shortcuts),

        /** 兜底：没登记分组的形态落这里（当前 40 个形态都已登记，目录里不会出现这个分区）。 */
        OTHER(R.string.widget_group_other),
    }

    /**
     * 目录里的一个**组件**：同基类的多个尺寸形态合并为一组（如「今日待办」= 4×3 + 2×1 + 4×1）。
     * [entries] 首位是基类形态，即默认选中的尺寸。
     */
    data class CatalogComponent(
        /** 族键：同族尺寸形态共享 titleRes，用它把一组的多个尺寸认成一个组件。 */
        val familyKey: Int,
        val titleRes: Int,
        /** 画廊标签行的短名（2026-10-25 用户定：半宽卡上长名截断，画廊一律用短名）。 */
        val titleShortRes: Int,
        val descRes: Int,
        val entries: List<Entry>,
        val configureActivity: Class<*>?,
        /** 渲染吃强调色（「编辑更多」层放配色卡）：账单/概览/接下来。 */
        val accentEditable: Boolean = false,
    ) {
        /** 该组件支持的所有尺寸标签（与 [entries] 一一对应）。 */
        val cells: List<String> get() = entries.map { cellsOf(it) }
    }

    /** 目录的一个分组：组标题 + 该组的组件列表。 */
    data class CatalogGroup(
        val group: WidgetGroup,
        val components: List<CatalogComponent>,
    )

    // 缩略图与选择器 previewImage 同源（真实布局离屏渲染），
    // 确认框预览布局与选择器 previewLayout 同源：确认框里看到的就是添加后的样子
    // 顺序即目录里的顺序，按分组排（记账 → 生活 → 其他）
    val entries: List<Entry> = listOf(
        Entry(
            BillWidgetProvider::class.java,
            R.drawable.widget_preview_bill,
            R.string.widget_bill_title,
            R.string.widget_bill_desc,
            configureActivity = BillWidgetConfigActivity::class.java,
            accentEditable = true
        ),
        // 数据版的其余档位（v37 四档：4×2 / 2×2 / 4×1 / 4×4，弹窗里左右滑切换）
        Entry(
            BillSquare::class.java,
            R.drawable.widget_preview_bill_square,
            R.string.widget_bill_title,
            R.string.widget_bill_desc,
            configureActivity = BillWidgetConfigActivity::class.java,
            accentEditable = true
        ),
        Entry(
            BillBar::class.java,
            R.drawable.widget_preview_bill_bar,
            R.string.widget_bill_title,
            R.string.widget_bill_desc,
            configureActivity = BillWidgetConfigActivity::class.java,
            accentEditable = true
        ),
        Entry(
            NetWorthWidgetProvider::class.java,
            R.drawable.widget_preview_net_worth,
            R.string.widget_net_worth_title,
            R.string.widget_net_worth_desc,
        ),
        Entry(
            NetWorthWide::class.java,
            R.drawable.widget_preview_net_worth_wide,
            R.string.widget_net_worth_title,
            R.string.widget_net_worth_desc,
        ),
        Entry(
            NetWorthBar::class.java,
            R.drawable.widget_preview_net_worth_bar,
            R.string.widget_net_worth_title,
            R.string.widget_net_worth_desc,
        ),
        Entry(
            TodoWidgetProvider::class.java,
            R.drawable.widget_preview_todo,
            R.string.widget_todo_title,
            R.string.widget_todo_desc,
        ),
        Entry(
            TodoSquare::class.java,
            R.drawable.widget_preview_todo_square,
            R.string.widget_todo_title,
            R.string.widget_todo_desc,
        ),
        Entry(
            TodoWide::class.java,
            R.drawable.widget_preview_todo_bar,
            R.string.widget_todo_title,
            R.string.widget_todo_desc,
        ),
        Entry(
            HabitWidgetProvider::class.java,
            R.drawable.widget_preview_habit,
            R.string.widget_habit_title,
            R.string.widget_habit_desc,
        ),
        Entry(
            HabitSquare::class.java,
            R.drawable.widget_preview_habit_square,
            R.string.widget_habit_title,
            R.string.widget_habit_desc,
        ),
        Entry(
            HabitWide::class.java,
            R.drawable.widget_preview_habit_bar,
            R.string.widget_habit_title,
            R.string.widget_habit_desc,
        ),
        Entry(
            LifeCounterWidgetProvider::class.java,
            R.drawable.widget_preview_counter,
            R.string.widget_counter_title,
            R.string.widget_counter_desc,
        ),
        Entry(
            CounterSquare::class.java,
            R.drawable.widget_preview_counter_square,
            R.string.widget_counter_title,
            R.string.widget_counter_desc,
        ),
        Entry(
            CounterBar::class.java,
            R.drawable.widget_preview_counter_bar,
            R.string.widget_counter_title,
            R.string.widget_counter_desc,
        ),
        Entry(
            NextUpWidgetProvider::class.java,
            R.drawable.widget_preview_next_up_square,
            R.string.widget_next_up_title,
            R.string.widget_next_up_desc,
            accentEditable = true
        ),
        Entry(
            NextUpWide::class.java,
            R.drawable.widget_preview_next_up_wide,
            R.string.widget_next_up_title,
            R.string.widget_next_up_desc,
            accentEditable = true
        ),
        Entry(
            NextUpBar::class.java,
            R.drawable.widget_preview_next_up_bar,
            R.string.widget_next_up_title,
            R.string.widget_next_up_desc,
            accentEditable = true
        ),
        Entry(
            SubscriptionWidgetProvider::class.java,
            R.drawable.widget_preview_subscription,
            R.string.widget_subscription_title,
            R.string.widget_subscription_desc,
        ),
        Entry(
            SubscriptionSquare::class.java,
            R.drawable.widget_preview_subscription_square,
            R.string.widget_subscription_title,
            R.string.widget_subscription_desc,
        ),
        Entry(
            SubscriptionBar::class.java,
            R.drawable.widget_preview_subscription_bar,
            R.string.widget_subscription_title,
            R.string.widget_subscription_desc,
        ),
        Entry(
            DashboardWidgetProvider::class.java,
            R.drawable.widget_preview_dashboard,
            R.string.widget_dashboard_title,
            R.string.widget_dashboard_desc,
            accentEditable = true
        ),
        Entry(
            DashboardSquare::class.java,
            R.drawable.widget_preview_dashboard_square,
            R.string.widget_dashboard_title,
            R.string.widget_dashboard_desc,
            accentEditable = true
        ),
        Entry(
            DashboardBar::class.java,
            R.drawable.widget_preview_dashboard_bar,
            R.string.widget_dashboard_title,
            R.string.widget_dashboard_desc,
            accentEditable = true
        ),
        Entry(
            AssetWidgetProvider::class.java,
            R.drawable.widget_preview_asset,
            R.string.widget_asset_title,
            R.string.widget_asset_desc,
        ),
        Entry(
            AssetSquare::class.java,
            R.drawable.widget_preview_asset_square,
            R.string.widget_asset_title,
            R.string.widget_asset_desc,
        ),
        Entry(
            AssetBar::class.java,
            R.drawable.widget_preview_asset_bar,
            R.string.widget_asset_title,
            R.string.widget_asset_desc,
        ),
        Entry(
            ShortcutsWidgetProvider::class.java,
            R.drawable.widget_preview_shortcuts,
            R.string.widget_shortcuts_title,
            R.string.widget_shortcuts_desc,
        ),
        Entry(
            ShortcutsWide::class.java,
            R.drawable.widget_preview_shortcuts_wide,
            R.string.widget_shortcuts_title,
            R.string.widget_shortcuts_desc,
        ),
        Entry(
            ShortcutsFull::class.java,
            R.drawable.widget_preview_shortcuts_full,
            R.string.widget_shortcuts_title,
            R.string.widget_shortcuts_desc,
        ),
        // v37 4×4 仪表盘档（每族一件；弹窗里滑到最后一页）
        Entry(
            DashboardTall::class.java,
            R.drawable.widget_preview_dashboard_tall,
            R.string.widget_dashboard_title,
            R.string.widget_dashboard_desc,
            accentEditable = true
        ),
        Entry(
            BillTall::class.java,
            R.drawable.widget_preview_bill_tall,
            R.string.widget_bill_title,
            R.string.widget_bill_desc,
            configureActivity = BillWidgetConfigActivity::class.java,
            accentEditable = true
        ),
        Entry(
            TodoTall::class.java,
            R.drawable.widget_preview_todo_tall,
            R.string.widget_todo_title,
            R.string.widget_todo_desc,
        ),
        Entry(
            HabitTall::class.java,
            R.drawable.widget_preview_habit_tall,
            R.string.widget_habit_title,
            R.string.widget_habit_desc,
        ),
        Entry(
            CounterTall::class.java,
            R.drawable.widget_preview_counter_tall,
            R.string.widget_counter_title,
            R.string.widget_counter_desc,
        ),
        Entry(
            NextUpTall::class.java,
            R.drawable.widget_preview_next_up_tall,
            R.string.widget_next_up_title,
            R.string.widget_next_up_desc,
            accentEditable = true
        ),
        Entry(
            NetWorthTall::class.java,
            R.drawable.widget_preview_net_worth_tall,
            R.string.widget_net_worth_title,
            R.string.widget_net_worth_desc,
        ),
        Entry(
            SubscriptionTall::class.java,
            R.drawable.widget_preview_subscription_tall,
            R.string.widget_subscription_title,
            R.string.widget_subscription_desc,
        ),
        Entry(
            AssetTall::class.java,
            R.drawable.widget_preview_asset_tall,
            R.string.widget_asset_title,
            R.string.widget_asset_desc,
        ),
        Entry(
            ShortcutsTall::class.java,
            R.drawable.widget_preview_shortcuts_tall,
            R.string.widget_shortcuts_title,
            R.string.widget_shortcuts_desc,
        ),
    )


    /**
     * 组件占几格（与各自 `*_info.xml` 的 targetCell* 一致），目录里当尺寸标签用。
     * 横滑行里卡片高度是统一的，尺寸只能靠标签说，不然 2×1 和 4×3 看起来一样大。
     */
    private val CELLS: Map<Class<*>, String> = mapOf(
        BillWidgetProvider::class.java to "4×2",
        BillSquare::class.java to "2×2",
        BillBar::class.java to "4×1",
        NetWorthWidgetProvider::class.java to "2×2",
        NetWorthWide::class.java to "4×2",
        NetWorthBar::class.java to "4×1",
        TodoWidgetProvider::class.java to "4×2",
        TodoWide::class.java to "4×1",
        TodoSquare::class.java to "2×2",
        HabitWidgetProvider::class.java to "4×2",
        HabitWide::class.java to "4×1",
        HabitSquare::class.java to "2×2",
        LifeCounterWidgetProvider::class.java to "4×2",
        CounterSquare::class.java to "2×2",
        CounterBar::class.java to "4×1",
        NextUpWidgetProvider::class.java to "2×2",
        NextUpWide::class.java to "4×2",
        NextUpBar::class.java to "4×1",
        SubscriptionWidgetProvider::class.java to "4×2",
        SubscriptionSquare::class.java to "2×2",
        SubscriptionBar::class.java to "4×1",
        DashboardWidgetProvider::class.java to "4×2",
        DashboardSquare::class.java to "2×2",
        DashboardBar::class.java to "4×1",
        AssetWidgetProvider::class.java to "4×2",
        AssetSquare::class.java to "2×2",
        AssetBar::class.java to "4×1",
        ShortcutsWidgetProvider::class.java to "2×2",
        ShortcutsFull::class.java to "4×2",
        ShortcutsWide::class.java to "4×1",
        DashboardTall::class.java to "4×4",
        BillTall::class.java to "4×4",
        TodoTall::class.java to "4×4",
        HabitTall::class.java to "4×4",
        CounterTall::class.java to "4×4",
        NextUpTall::class.java to "4×4",
        NetWorthTall::class.java to "4×4",
        SubscriptionTall::class.java to "4×4",
        AssetTall::class.java to "4×4",
        ShortcutsTall::class.java to "4×4",
    )

    /** 尺寸标签（如 `3×2`）；没登记就返回空串，标签自动隐藏。 */
    fun cellsOf(entry: Entry): String = CELLS[entry.providerClass].orEmpty()

    /**
     * 每个形态属于哪个分区（与 [WidgetGroup] 对应，按 App 功能模块划）：
     * - 账单：本月记账、总资产、订阅提醒（都是钱的事）；
     * - 物品：物品分布；
     * - 生活：待办、打卡、计数器、接下来；
     * - 总览 / 快捷入口：各自一件组件（密码本不参与，其小组件已下线）。
     */
    private val GROUP_OF: Map<Class<*>, WidgetGroup> = mapOf(
        BillWidgetProvider::class.java to WidgetGroup.BILLING,
        BillSquare::class.java to WidgetGroup.BILLING,
        BillBar::class.java to WidgetGroup.BILLING,
        NetWorthWidgetProvider::class.java to WidgetGroup.BILLING,
        NetWorthWide::class.java to WidgetGroup.BILLING,
        NetWorthBar::class.java to WidgetGroup.BILLING,
        SubscriptionWidgetProvider::class.java to WidgetGroup.BILLING,
        SubscriptionSquare::class.java to WidgetGroup.BILLING,
        SubscriptionBar::class.java to WidgetGroup.BILLING,
        AssetWidgetProvider::class.java to WidgetGroup.ASSET,
        AssetSquare::class.java to WidgetGroup.ASSET,
        AssetBar::class.java to WidgetGroup.ASSET,
        TodoWidgetProvider::class.java to WidgetGroup.LIFE,
        TodoWide::class.java to WidgetGroup.LIFE,
        TodoSquare::class.java to WidgetGroup.LIFE,
        HabitWidgetProvider::class.java to WidgetGroup.LIFE,
        HabitWide::class.java to WidgetGroup.LIFE,
        HabitSquare::class.java to WidgetGroup.LIFE,
        NextUpWidgetProvider::class.java to WidgetGroup.LIFE,
        NextUpWide::class.java to WidgetGroup.LIFE,
        NextUpBar::class.java to WidgetGroup.LIFE,
        LifeCounterWidgetProvider::class.java to WidgetGroup.LIFE,
        CounterSquare::class.java to WidgetGroup.LIFE,
        CounterBar::class.java to WidgetGroup.LIFE,
        DashboardWidgetProvider::class.java to WidgetGroup.DASHBOARD,
        DashboardSquare::class.java to WidgetGroup.DASHBOARD,
        DashboardBar::class.java to WidgetGroup.DASHBOARD,
        ShortcutsWidgetProvider::class.java to WidgetGroup.SHORTCUTS,
        ShortcutsFull::class.java to WidgetGroup.SHORTCUTS,
        ShortcutsWide::class.java to WidgetGroup.SHORTCUTS,
        DashboardTall::class.java to WidgetGroup.DASHBOARD,
        BillTall::class.java to WidgetGroup.BILLING,
        TodoTall::class.java to WidgetGroup.LIFE,
        HabitTall::class.java to WidgetGroup.LIFE,
        CounterTall::class.java to WidgetGroup.LIFE,
        NextUpTall::class.java to WidgetGroup.LIFE,
        NetWorthTall::class.java to WidgetGroup.BILLING,
        SubscriptionTall::class.java to WidgetGroup.BILLING,
        AssetTall::class.java to WidgetGroup.ASSET,
        ShortcutsTall::class.java to WidgetGroup.SHORTCUTS,
    )

    /** 形态所属分组；没登记归 OTHER（与 [GROUP_OF] 一致，测试可据此锁住分组完整）。 */
    fun groupOf(entry: Entry): WidgetGroup = GROUP_OF[entry.providerClass] ?: WidgetGroup.OTHER

    /**
     * 把各尺寸形态折叠成「分组 → 组件」两级目录：
     * 同族尺寸形态（共享 titleRes）合并成一个 [CatalogComponent]，基类形态在首位即默认尺寸。
     * 分组顺序 = [WidgetGroup] 声明顺序，组内组件顺序 = 目录里的先后。
     */
    /**
     * 画廊默认顺序（2026-10-24 稿序）：物品分布 → 本月记账 → 净资产 + 快捷入口（2×2 对）→
     * 订阅提醒 → 待办 → 打卡 → 概览 → 计数 → 接下来。覆盖旧的「通栏全在前」排序 ——
     * 稿 §5 里 2×2 是成对夹在 4×2 中间的,按尺寸分组反而对不上稿。
     */
    val GALLERY_ORDER: List<Int> = listOf(
        R.string.widget_asset_title,
        R.string.widget_bill_title,
        R.string.widget_net_worth_title,
        R.string.widget_shortcuts_title,
        R.string.widget_subscription_title,
        R.string.widget_todo_title,
        R.string.widget_habit_title,
        R.string.widget_dashboard_title,
        R.string.widget_counter_title,
        R.string.widget_next_up_title,
    )

    /** 稿序序号;表外组件排最后(保持相对顺序)。 */
    fun gallerySortIndex(titleRes: Int): Int =
        GALLERY_ORDER.indexOf(titleRes).takeIf { it >= 0 } ?: Int.MAX_VALUE

    /**
     * 画廊标签行的短名（2026-10-25 用户定：半宽卡上长名截断，画廊一律用短名）。
     * 长名（[titleRes]）仍是 manifest label 与面板标题的口径，两套并存。
     */
    private val TITLE_SHORT_OF: Map<Int, Int> = mapOf(
        R.string.widget_bill_title to R.string.widget_bill_title_short,
        R.string.widget_net_worth_title to R.string.widget_net_worth_title_short,
        R.string.widget_dashboard_title to R.string.widget_dashboard_title_short,
        R.string.widget_shortcuts_title to R.string.widget_shortcuts_title_short,
        R.string.widget_next_up_title to R.string.widget_next_up_title_short,
        R.string.widget_todo_title to R.string.widget_todo_title_short,
        R.string.widget_counter_title to R.string.widget_counter_title_short,
        R.string.widget_asset_title to R.string.widget_asset_title_short,
        R.string.widget_subscription_title to R.string.widget_subscription_title_short,
        R.string.widget_habit_title to R.string.widget_habit_title_short,
    )

    /** 族的画廊短名；没登记兜底回长名（新族忘了登记也不会显示空白）。 */
    fun titleShortOf(titleRes: Int): Int = TITLE_SHORT_OF[titleRes] ?: titleRes

    /**
     * 面板分页顺序（设计稿 §5：4×2 → 2×2 → 4×1 → 4×4）。
     * 不能直接用登记顺序：净资产 / 接下来 / 快捷入口的**基类**是 2×2 或 4×1，
     * 首档会排错（2026-10-24 收敛后遗留）。catalog 的 entries 首位 = 面板默认选中档。
     */
    private fun tierRankOf(entry: Entry): Int = when (cellsOf(entry)) {
        "4×2" -> 0
        "2×2" -> 1
        "4×1" -> 2
        "4×4" -> 3
        else -> Int.MAX_VALUE
    }

    fun catalog(): List<CatalogGroup> = WidgetGroup.entries.map { group ->
        val components = entries
            .filter { groupOf(it) == group }
            .groupBy { it.titleRes }
            .map { (title, family) ->
                CatalogComponent(
                    familyKey = title,
                    titleRes = title,
                    titleShortRes = titleShortOf(title),
                    descRes = family.first().descRes,
                    entries = family.sortedBy { tierRankOf(it) },
                    configureActivity = family.first().configureActivity,
                    accentEditable = family.first().accentEditable,
                )
            }
        CatalogGroup(group, components)
    }.filter { it.components.isNotEmpty() }

    /**
     * 各组件的**设计尺寸**（dp，宽 to 高），取自 `docs/widgets/spec.html`
     * 的 `.s11/.s21/.s22/.s32/.s41/.s43` —— 就是它们在桌面上的真实占格大小。
     * 目录里的预览卡按这套尺寸**同比缩放**（不是各自拉满），比例才和真机一致：
     * 1×1 是小方块、2×1 是矮条、4×3 是大块。
     */
    private val DESIGN_SIZE_DP: Map<Class<*>, Pair<Int, Int>> = mapOf(
        BillWidgetProvider::class.java to (340 to 148),
        BillSquare::class.java to (160 to 160),
        BillBar::class.java to (340 to 74),
        NetWorthWidgetProvider::class.java to (160 to 160),
        NetWorthWide::class.java to (340 to 148),
        NetWorthBar::class.java to (340 to 74),
        TodoWidgetProvider::class.java to (340 to 148),
        TodoWide::class.java to (340 to 74),
        TodoSquare::class.java to (160 to 160),
        HabitWidgetProvider::class.java to (340 to 148),
        HabitWide::class.java to (340 to 74),
        HabitSquare::class.java to (160 to 160),
        LifeCounterWidgetProvider::class.java to (340 to 148),
        CounterSquare::class.java to (160 to 160),
        CounterBar::class.java to (340 to 74),
        NextUpWidgetProvider::class.java to (160 to 160),
        NextUpWide::class.java to (340 to 148),
        NextUpBar::class.java to (340 to 74),
        SubscriptionWidgetProvider::class.java to (340 to 148),
        SubscriptionSquare::class.java to (160 to 160),
        SubscriptionBar::class.java to (340 to 74),
        DashboardWidgetProvider::class.java to (340 to 148),
        DashboardSquare::class.java to (160 to 160),
        DashboardBar::class.java to (340 to 74),
        AssetWidgetProvider::class.java to (340 to 148),
        AssetSquare::class.java to (160 to 160),
        AssetBar::class.java to (340 to 74),
        ShortcutsWidgetProvider::class.java to (160 to 160),
        ShortcutsFull::class.java to (340 to 148),
        ShortcutsWide::class.java to (340 to 74),
        DashboardTall::class.java to (340 to 340),
        BillTall::class.java to (340 to 340),
        TodoTall::class.java to (340 to 340),
        HabitTall::class.java to (340 to 340),
        CounterTall::class.java to (340 to 340),
        NextUpTall::class.java to (340 to 340),
        NetWorthTall::class.java to (340 to 340),
        SubscriptionTall::class.java to (340 to 340),
        AssetTall::class.java to (340 to 340),
        ShortcutsTall::class.java to (340 to 340),
    )

    /** 组件的设计尺寸（dp，宽 to 高）；没登记返回 null，预览走兜底比例。 */
    fun designSizeOf(entry: Entry): Pair<Int, Int>? = DESIGN_SIZE_DP[entry.providerClass]

    /** 入口封面形态的最小宽度（dp）：比 1×1 的 82dp 宽，才撑得住一张卡。 */
    private const val SHOWCASE_MIN_WIDTH_DP = 160

    /** 入口封面形态的最大宽高比：4×1（≈4.6）这种细条不适合当封面。 */
    private const val SHOWCASE_MAX_RATIO = 2.6f

    /**
     * 入口卡展示哪个尺寸形态 —— **不跟「上次选的尺寸」走**，固定挑该组件最饱满的一版：
     * 宽度够看（≥ [SHOWCASE_MIN_WIDTH_DP]）且不过分扁（宽高比 ≤ [SHOWCASE_MAX_RATIO]）的形态里
     * 取面积最大者；一个都不合格（如只剩 1×1 的缩档形态）时退化为面积最大者，宁可用小方块也不用细条。
     *
     * 2026-10-21 用户反馈：入口该像系统小部件页那样「都显示 2×2、4×2 这种大卡」，
     * 具体尺寸留到点进面板再选 —— 之前入口跟着上次选的尺寸走，选过 2×1 后整排卡都缩成矮条。
     */
    fun showcaseEntryOf(component: CatalogComponent): Entry {
        val sized = component.entries.mapNotNull { entry ->
            designSizeOf(entry)?.let { entry to it }
        }
        val qualified = sized.filter { (_, size) ->
            size.first >= SHOWCASE_MIN_WIDTH_DP &&
                size.first.toFloat() / size.second <= SHOWCASE_MAX_RATIO
        }
        val pool = qualified.ifEmpty { sized }
        return pool.maxByOrNull { (_, size) -> size.first * size.second }?.first
            ?: component.entries.first()
    }

    /**
     * 入口卡在画廊里占**几列**：只有两种画布（2026-10-21 用户定「入口显示的大小要是 4×2 或 2×2」）。
     * 4×2 的卡占满一整行，2×2 的卡占一列。
     *
     * 用显式列表而不是按宽高比推算：2×1（比例 2.16）和 4×2（2.30）光看比例分不开，但 2×1 那一版
     * 是「三行拉开的数字」，塞进 4×2 会中间大片空白（2026-10-21 目检）。
     */
    internal fun entryCellOf(component: CatalogComponent): WidgetSizeClass =
        if (component.entries.any { it.providerClass in SQUARE_ENTRY }) {
            WidgetSizeClass.SQUARE
        } else {
            WidgetSizeClass.WIDE
        }

    /** 方阵型组件：入口摆 2×2（2026-10-23 定案「净资产|生活计数、接下来|快捷入口」两两并排）。 */
    private val SQUARE_ENTRY: Set<Class<*>> = setOf(
        NetWorthWidgetProvider::class.java,
        LifeCounterWidgetProvider::class.java,
        NextUpWidgetProvider::class.java,
        ShortcutsWidgetProvider::class.java
    )

    /**
     * 入口卡的画布尺寸（dp）：4×2 = 340×148、2×2 = 160×160。
     * 入口预览图必须按这个画布出，否则图与卡位比例不一致，铺进卡里会留白或裁边。
     */
    internal fun entrySizeOf(cell: WidgetSizeClass): Pair<Int, Int> =
        cell.designWidthDp to cell.designHeightDp

    /**
     * 入口卡用的预览图：统一按 4×2 / 2×2 画布重出的 `widget_entry_*`。
     *
     * **不复用 `Entry.previewImageRes`** —— 那是各形态自己的画布（3×2 / 4×3 / 2×1 …），
     * 塞进 4×2 或 2×2 的卡位会留白或裁边（2026-10-21 目检：4×3 的概览缩进 2×2 只剩图标，
     * 2×1 的总资产拉进 4×2 中间一大片空）。
     */
    private val ENTRY_IMAGE: Map<Class<*>, Int> = mapOf(
        BillWidgetProvider::class.java to R.drawable.widget_entry_bill,
        NetWorthWidgetProvider::class.java to R.drawable.widget_entry_net_worth,
        TodoWidgetProvider::class.java to R.drawable.widget_entry_todo,
        HabitWidgetProvider::class.java to R.drawable.widget_entry_habit,
        LifeCounterWidgetProvider::class.java to R.drawable.widget_entry_counter,
        NextUpWidgetProvider::class.java to R.drawable.widget_entry_next_up,
        SubscriptionWidgetProvider::class.java to R.drawable.widget_entry_subscription,
        // 概览：4×2（左 hero + 右三胶囊，v22 构图）
        DashboardWidgetProvider::class.java to R.drawable.widget_entry_dashboard,
        AssetWidgetProvider::class.java to R.drawable.widget_entry_asset,
        ShortcutsWidgetProvider::class.java to R.drawable.widget_entry_shortcuts
    )

    internal fun entryImageOf(component: CatalogComponent): Int =
        component.entries.firstNotNullOfOrNull { ENTRY_IMAGE[it.providerClass] }
            ?: showcaseEntryOf(component).previewImageRes

    /**
     * 面板默认选中的样式 = 画廊卡位对应的那一档：2×2 族取 2×2，其余取 4×2。
     * 打开面板第一眼看到的预览形状必须与刚才那张画廊卡一致（页序按 4×2 开头后，
     * entries[0] 不再总是画廊卡位那一档）。
     */
    internal fun defaultStyleEntry(component: CatalogComponent): Entry {
        val wanted = if (entryCellOf(component) == WidgetSizeClass.SQUARE) "2×2" else "4×2"
        return component.entries.firstOrNull { cellsOf(it) == wanted }
            ?: component.entries.first()
    }

    /** 一屏要展示的全部状态：通道是否可用 + 各形态桌面实例 id + 是否成功落桌过。 */
    data class Snapshot(
        val pinSupported: Boolean,
        /** 每个尺寸形态在桌面上的实例 id（升序；键是本形态的 provider class）。 */
        val bound: Map<Class<*>, List<Int>>,
        val pinOk: Boolean
    ) {
        fun boundCount(entry: Entry): Int = bound[entry.providerClass].orEmpty().size

        /** 组件（含各尺寸形态）在桌面上的实例总数。 */
        fun boundCountOf(component: CatalogComponent): Int =
            component.entries.sumOf { boundCount(it) }

        /** 组件在桌面上的全部实例：按目录的尺寸顺序，同尺寸按 id 升序（编辑面板据此列实例）。 */
        fun instancesOf(component: CatalogComponent): List<PlacedInstance> =
            component.entries.flatMap { entry ->
                bound[entry.providerClass].orEmpty().map { PlacedInstance(it, entry) }
            }
    }

    /** 桌面上已放置的一个实例：编辑面板按 id 找到它，尺寸由 [entry] 定（不再翻页换尺寸）。 */
    data class PlacedInstance(val appWidgetId: Int, val entry: Entry)

    fun snapshot(context: Context): Snapshot = Snapshot(
        pinSupported = pinSupported(context),
        bound = entries.associate {
            it.providerClass to boundIds(context, it.providerClass).sorted()
        },
        pinOk = pinSucceededBefore(context)
    )

    /**
     * 权限条的出现条件：启动器支持应用内添加 + 当前桌面 pin 前会校验「桌面快捷方式」权限 +
     * **还没成功落桌过**。该权限没有公开查询法，但成功落桌过就是权限已开的实证——之后再常驻
     * 提示只会制造焦虑；后来被关掉权限时，添加失败的 toast 里已有引导文案兜底。
     */
    internal fun showHomePermStrip(pinSupported: Boolean, homeNeedsPerm: Boolean, pinOk: Boolean): Boolean =
        pinSupported && homeNeedsPerm && !pinOk

    /** 成功落桌过一次的记录（本地存，只增不删）：用于收起权限条。 */
    fun pinSucceededBefore(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PIN_OK, false)

    /** [awaitPinned] 数到实例变多时记一笔；测试可直接调它铺路。 */
    internal fun recordPinSuccess(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PIN_OK, true).apply()
    }

    /**
     * 成功落桌后的权限入口被用户**手动关掉**过：关了就不再显示（2026-10-25 用户定：
     * 「开启权限后的提示可以加上关闭叉」）。只影响单行入口，警示条（从未成功）不受它管。
     */
    fun permEntryDismissed(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERM_ENTRY_DISMISSED, false)

    /** 权限入口的用户关闭动作：持久记住，之后进页面不再出现。 */
    fun dismissPermEntry(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PERM_ENTRY_DISMISSED, true).apply()
    }

    fun pinSupported(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return manager.isRequestPinAppWidgetSupported
    }

    fun boundCount(context: Context, providerClass: Class<*>): Int = boundIds(context, providerClass).size

    /**
     * 桌面上的实例 id 集合。
     * 核对该用 id 而不是数量：落桌后还要按 id 写配置（面板里选的不透明度要在这一刻落到新实例上）。
     */
    fun boundIds(context: Context, providerClass: Class<*>): IntArray {
        val manager = AppWidgetManager.getInstance(context) ?: return IntArray(0)
        return manager.getAppWidgetIds(ComponentName(context, providerClass)) ?: IntArray(0)
    }

    /**
     * 发起请求后核对到底有没有落桌，返回**新落桌的那个实例 id**（一直没数到就返回 null）。
     *
     * `requestPinAppWidget` 的返回值只说明「这个桌面支持应用内添加」，**不代表添加成功** ——
     * 平台文档写明「返回时不等用户响应，因此返回 true 不等于已固定」。桌面在缺少相关权限时
     * 会静默吞掉请求（确认页一闪而过、桌面什么都没有），只看返回值就会报出假成功。
     * 所以这里不猜：等一会儿重新数一遍桌面上的实例，比出多出来的那个才算成了，
     * 顺带把 id 带回去（面板里选的不透明度要写到这个新实例上）。
     *
     * 成功会在数到的那一刻立即返回；一直没数到才等到最后一轮结束，此时返回 null（未确认）。
     */
    suspend fun awaitPinned(context: Context, entry: Entry, before: IntArray): Int? {
        val newId = awaitNewId(before) { boundIds(context, entry.providerClass) }
        if (newId != null) recordPinSuccess(context)
        return newId
    }

    /** [awaitPinned] 的核对逻辑本体：每轮重新取一次实例集合，比 [before] 多出来的那个就是新实例。 */
    internal suspend fun awaitNewId(before: IntArray, ids: () -> IntArray): Int? {
        repeat(PIN_VERIFY_ATTEMPTS) {
            delay(PIN_VERIFY_INTERVAL_MS)
            ids().firstOrNull { it !in before }?.let { return it }
        }
        return null
    }

    /**
     * 发起固定请求。附带预览布局（EXTRA_APPWIDGET_PREVIEW，API 31+ 生效），
     * 旧启动器会忽略不认识的 extra，不影响请求本身。返回 false = 通道不可用。
     *
     * 走这条通道时**启动器不会拉起组件的配置页**（AppWidgetManager#requestPinAppWidget 的
     * 既有行为），pin 完就是默认配置。所以带配置页的组件要自己补一步：挂一个成功回调，
     * 系统落桌后会把 appWidgetId 回传到回调里，我们据此把配置页打开——
     * 账本选择只有拿到这个 id 才存得下（键就是 appWidgetId）。
     */
    /**
     * pin 确认页用的静态预览：一张 `_card` 整卡图（逐形态由 WidgetPreviewPngGenerator 生成）。
     * 取不到 `_card` 时回退内容层图 —— 宁可少一圈卡底，也不显示空壳。
     */
    private fun pinPreview(context: Context, entry: Entry): RemoteViews {
        val res = context.resources
        val card = res.getIdentifier(
            res.getResourceEntryName(entry.previewImageRes) + "_card",
            "drawable",
            res.getResourcePackageName(entry.previewImageRes)
        ).takeIf { it != 0 } ?: entry.previewImageRes
        return RemoteViews(context.packageName, R.layout.widget_pin_card_image).apply {
            setImageViewResource(R.id.widget_pin_card_image, card)
        }
    }

    fun requestPin(context: Context, entry: Entry): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        val extras = Bundle().apply {
            // 系统 pin 预览是一次性的，但 launcher 端只 inflate 布局、**不跑应用代码**：
            // 指向运行时布局就是空壳或 "0" 占位（40 档里 27 档曾如此）。改用已出好的整卡图。
            putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, pinPreview(context, entry))
        }
        return manager.requestPinAppWidget(
            ComponentName(context, entry.providerClass),
            extras,
            configureCallback(context, entry)
        )
    }

    /** 无配置页的组件不需要回调；返回 null 表示 pin 完就结束。 */
    private fun configureCallback(context: Context, entry: Entry): android.app.PendingIntent? {
        val target = entry.configureActivity ?: return null
        val intent = Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return android.app.PendingIntent.getActivity(
            context,
            CONFIGURE_REQUEST_CODE,
            intent,
            // 必须 MUTABLE：appWidgetId 是系统在 send() 时**填进**来的（fill-in extras），
            // IMMUTABLE 会忽略 fill-in，配置页就只能拿到无效 id 然后立刻退出。
            // 目标是本应用自己的 Activity（显式 Intent），不存在被第三方改写的风险。
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * 当前桌面是否属于「pin 前要先开桌面快捷方式权限」的那一类：这类桌面的 pin 确认页（AddItemActivity）
     * 在真正落桌前会校验调用方有没有这条权限（logcat: "add widget failed, <pkg> has no permission"），
     * 未授权时确认页静默退出、用户看不到任何界面——所以要提前摆权限引导条。
     *
     * 判据是桌面应用的包名（下面这串就是真实包名）：不在名单里的桌面不校验这条权限，返回 false、
     * 不摆引导条；将来遇到同类桌面只在这里补一串包名即可。
     */
    fun homeNeedsPerm(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("com.miui.home", 0) != null
    } catch (_: Exception) {
        false
    }

    /** pin 成功回调的 requestCode；组件级共用，回调内容由系统按落桌实例补全。 */
    private const val CONFIGURE_REQUEST_CODE = 424_243

    /** 「成功落桌过」的本地记录（见 [pinSucceededBefore]）。 */
    private const val PREFS_NAME = "widget_pin"
    private const val KEY_PIN_OK = "pin_ok"
    private const val KEY_PERM_ENTRY_DISMISSED = "perm_entry_dismissed"

    /** 核对落桌结果：最多数 [PIN_VERIFY_ATTEMPTS] 次，每次间隔 [PIN_VERIFY_INTERVAL_MS]。 */
    private const val PIN_VERIFY_ATTEMPTS = 5
    private const val PIN_VERIFY_INTERVAL_MS = 1_500L
}
