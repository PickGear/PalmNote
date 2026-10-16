package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
object WidgetPin {


    /** 目录里的一行：桌面上的哪个组件、用哪张缩略图/哪个确认框预览布局与文案。 */
    data class Entry(
        val providerClass: Class<*>,
        val previewImageRes: Int,
        val previewLayoutRes: Int,
        val titleRes: Int,
        val descRes: Int,
        /** 组件有配置页时填这里：pin 成功后要自己把它打开（见 requestPin 注释）。 */
        val configureActivity: Class<*>? = null
    )

    /** 目录分组：22 个尺寸形态按用途归到 5 组，组内再按组件折叠（见 [catalog]）。 */
    enum class WidgetGroup(val titleRes: Int) {
        BILLING(R.string.widget_group_billing),
        LIFE(R.string.widget_group_life),
        EFFICIENCY(R.string.widget_group_efficiency),
        ASSET(R.string.widget_group_asset),
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
        val descRes: Int,
        val entries: List<Entry>,
        val configureActivity: Class<*>?,
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
            R.layout.widget_bill_preview,
            R.string.widget_bill_title,
            R.string.widget_bill_desc,
            configureActivity = BillWidgetConfigActivity::class.java
        ),
        Entry(
            NetWorthWidgetProvider::class.java,
            R.drawable.widget_preview_net_worth,
            R.layout.widget_net_worth_preview,
            R.string.wallet_total_assets,
            R.string.widget_net_worth_desc,
        ),
        Entry(
            TodoWidgetProvider::class.java,
            R.drawable.widget_preview_todo,
            R.layout.widget_todo_preview,
            R.string.widget_todo_title,
            R.string.widget_todo_desc,
        ),
        // 11 个尺寸形态：与基类同 title/desc（不加新字符串），靠尺寸标 + 缩略图区分
        Entry(
            TodoMini::class.java,
            R.drawable.widget_preview_todo_mini,
            R.layout.widget_todo_mini_preview,
            R.string.widget_todo_title,
            R.string.widget_todo_desc,
        ),
        Entry(
            TodoWide::class.java,
            R.drawable.widget_preview_todo_wide,
            R.layout.widget_todo_wide_preview,
            R.string.widget_todo_title,
            R.string.widget_todo_desc,
        ),
        Entry(
            HabitWidgetProvider::class.java,
            R.drawable.widget_preview_habit,
            R.layout.widget_habit_preview,
            R.string.widget_habit_title,
            R.string.widget_habit_desc,
        ),
        Entry(
            HabitMini::class.java,
            R.drawable.widget_preview_habit_mini,
            R.layout.widget_habit_mini_preview,
            R.string.widget_habit_title,
            R.string.widget_habit_desc,
        ),
        Entry(
            HabitWide::class.java,
            R.drawable.widget_preview_habit_wide,
            R.layout.widget_habit_wide_preview,
            R.string.widget_habit_title,
            R.string.widget_habit_desc,
        ),
        Entry(
            HabitNarrow::class.java,
            R.drawable.widget_preview_habit_narrow,
            R.layout.widget_habit_narrow_preview,
            R.string.widget_habit_title,
            R.string.widget_habit_desc,
        ),
        Entry(
            LifeCounterWidgetProvider::class.java,
            R.drawable.widget_preview_counter,
            R.layout.widget_counter_preview,
            R.string.widget_counter_title,
            R.string.widget_counter_desc,
        ),
        Entry(
            CounterNarrow::class.java,
            R.drawable.widget_preview_counter_narrow,
            R.layout.widget_counter_narrow_preview,
            R.string.widget_counter_title,
            R.string.widget_counter_desc,
        ),
        Entry(
            CounterTall::class.java,
            R.drawable.widget_preview_counter_tall,
            R.layout.widget_counter_tall_preview,
            R.string.widget_counter_title,
            R.string.widget_counter_desc,
        ),
        Entry(
            NextUpWidgetProvider::class.java,
            R.drawable.widget_preview_next_up,
            R.layout.widget_next_up_preview,
            R.string.widget_next_up_title,
            R.string.widget_next_up_desc,
        ),
        Entry(
            SubscriptionWidgetProvider::class.java,
            R.drawable.widget_preview_subscription,
            R.layout.widget_subscription_preview,
            R.string.widget_subscription_title,
            R.string.widget_subscription_desc,
        ),
        Entry(
            SubscriptionMini::class.java,
            R.drawable.widget_preview_subscription_mini,
            R.layout.widget_subscription_mini_preview,
            R.string.widget_subscription_title,
            R.string.widget_subscription_desc,
        ),
        Entry(
            SubscriptionNarrow::class.java,
            R.drawable.widget_preview_subscription_narrow,
            R.layout.widget_subscription_narrow_preview,
            R.string.widget_subscription_title,
            R.string.widget_subscription_desc,
        ),
        Entry(
            DashboardWidgetProvider::class.java,
            R.drawable.widget_preview_dashboard,
            R.layout.widget_dashboard_preview,
            R.string.widget_dashboard_title,
            R.string.widget_dashboard_desc,
        ),
        Entry(
            AssetWidgetProvider::class.java,
            R.drawable.widget_preview_asset,
            R.layout.widget_asset_preview,
            R.string.widget_asset_title,
            R.string.widget_asset_desc,
        ),
        Entry(
            VaultWidgetProvider::class.java,
            R.drawable.widget_preview_vault,
            R.layout.widget_vault_preview,
            R.string.widget_vault_title,
            R.string.widget_vault_desc,
        ),
        Entry(
            ShortcutsWidgetProvider::class.java,
            R.drawable.widget_preview_shortcuts,
            R.layout.widget_shortcuts_preview,
            R.string.widget_shortcuts_title,
            R.string.widget_shortcuts_desc,
        ),
        Entry(
            ShortcutsWide::class.java,
            R.drawable.widget_preview_shortcuts_wide,
            R.layout.widget_shortcuts_wide_preview,
            R.string.widget_shortcuts_title,
            R.string.widget_shortcuts_desc,
        ),
        Entry(
            ShortcutsSmall::class.java,
            R.drawable.widget_preview_shortcuts_small,
            R.layout.widget_shortcuts_small_preview,
            R.string.widget_shortcuts_title,
            R.string.widget_shortcuts_desc,
        )
    )


    /**
     * 组件占几格（与各自 `*_info.xml` 的 targetCell* 一致），目录里当尺寸标签用。
     * 横滑行里卡片高度是统一的，尺寸只能靠标签说，不然 2×1 和 4×3 看起来一样大。
     */
    private val CELLS: Map<Class<*>, String> = mapOf(
        BillWidgetProvider::class.java to "3×2",
        NetWorthWidgetProvider::class.java to "2×1",
        TodoWidgetProvider::class.java to "4×3",
        TodoMini::class.java to "2×1",
        TodoWide::class.java to "4×1",
        HabitWidgetProvider::class.java to "4×3",
        HabitMini::class.java to "2×1",
        HabitWide::class.java to "4×1",
        HabitNarrow::class.java to "1×3",
        LifeCounterWidgetProvider::class.java to "3×2",
        CounterNarrow::class.java to "1×2",
        CounterTall::class.java to "1×3",
        NextUpWidgetProvider::class.java to "3×2",
        SubscriptionWidgetProvider::class.java to "3×2",
        SubscriptionMini::class.java to "2×1",
        SubscriptionNarrow::class.java to "1×2",
        DashboardWidgetProvider::class.java to "4×3",
        AssetWidgetProvider::class.java to "3×2",
        VaultWidgetProvider::class.java to "1×1",
        ShortcutsWidgetProvider::class.java to "2×2",
        ShortcutsWide::class.java to "4×1",
        ShortcutsSmall::class.java to "1×1"
    )

    /** 尺寸标签（如 `3×2`）；没登记就返回空串，标签自动隐藏。 */
    fun cellsOf(entry: Entry): String = CELLS[entry.providerClass].orEmpty()

    /** 每个形态属于哪个目录分组（与 [WidgetGroup] 对应）。 */
    private val GROUP_OF: Map<Class<*>, WidgetGroup> = mapOf(
        BillWidgetProvider::class.java to WidgetGroup.BILLING,
        NetWorthWidgetProvider::class.java to WidgetGroup.BILLING,
        TodoWidgetProvider::class.java to WidgetGroup.LIFE,
        TodoMini::class.java to WidgetGroup.LIFE,
        TodoWide::class.java to WidgetGroup.LIFE,
        HabitWidgetProvider::class.java to WidgetGroup.LIFE,
        HabitMini::class.java to WidgetGroup.LIFE,
        HabitWide::class.java to WidgetGroup.LIFE,
        HabitNarrow::class.java to WidgetGroup.LIFE,
        NextUpWidgetProvider::class.java to WidgetGroup.LIFE,
        LifeCounterWidgetProvider::class.java to WidgetGroup.EFFICIENCY,
        CounterNarrow::class.java to WidgetGroup.EFFICIENCY,
        CounterTall::class.java to WidgetGroup.EFFICIENCY,
        SubscriptionWidgetProvider::class.java to WidgetGroup.EFFICIENCY,
        SubscriptionMini::class.java to WidgetGroup.EFFICIENCY,
        SubscriptionNarrow::class.java to WidgetGroup.EFFICIENCY,
        DashboardWidgetProvider::class.java to WidgetGroup.OTHER,
        AssetWidgetProvider::class.java to WidgetGroup.ASSET,
        VaultWidgetProvider::class.java to WidgetGroup.ASSET,
        ShortcutsWidgetProvider::class.java to WidgetGroup.OTHER,
        ShortcutsWide::class.java to WidgetGroup.OTHER,
        ShortcutsSmall::class.java to WidgetGroup.OTHER,
    )

    /** 形态所属分组；没登记归 OTHER（与 [GROUP_OF] 一致，测试可据此锁住分组完整）。 */
    fun groupOf(entry: Entry): WidgetGroup = GROUP_OF[entry.providerClass] ?: WidgetGroup.OTHER

    /**
     * 把 22 个尺寸形态折叠成「分组 → 组件」两级目录：
     * 同族尺寸形态（共享 titleRes）合并成一个 [CatalogComponent]，基类形态在首位即默认尺寸。
     * 分组顺序 = [WidgetGroup] 声明顺序，组内组件顺序 = 目录里的先后。
     */
    fun catalog(): List<CatalogGroup> = WidgetGroup.entries.map { group ->
        val components = entries
            .filter { groupOf(it) == group }
            .groupBy { it.titleRes }
            .map { (title, family) ->
                CatalogComponent(
                    familyKey = title,
                    titleRes = title,
                    descRes = family.first().descRes,
                    entries = family,
                    configureActivity = family.first().configureActivity,
                )
            }
        CatalogGroup(group, components)
    }.filter { it.components.isNotEmpty() }

    /**
     * 各组件的**设计尺寸**（dp，宽 to 高），取自 `dist/widget-design-v2/design-v4.html`
     * 的 `.s11/.s21/.s22/.s32/.s41/.s43` —— 就是它们在桌面上的真实占格大小。
     * 目录里的预览卡按这套尺寸**同比缩放**（不是各自拉满），比例才和真机一致：
     * 1×1 是小方块、2×1 是矮条、4×3 是大块。
     */
    private val DESIGN_SIZE_DP: Map<Class<*>, Pair<Int, Int>> = mapOf(
        BillWidgetProvider::class.java to (245 to 148),
        NetWorthWidgetProvider::class.java to (160 to 74),
        TodoWidgetProvider::class.java to (340 to 268),
        TodoMini::class.java to (160 to 74),
        TodoWide::class.java to (340 to 74),
        HabitWidgetProvider::class.java to (340 to 268),
        HabitMini::class.java to (160 to 74),
        HabitWide::class.java to (340 to 74),
        HabitNarrow::class.java to (82 to 268),
        LifeCounterWidgetProvider::class.java to (245 to 148),
        CounterNarrow::class.java to (82 to 160),
        CounterTall::class.java to (82 to 268),
        NextUpWidgetProvider::class.java to (245 to 148),
        SubscriptionWidgetProvider::class.java to (245 to 148),
        SubscriptionMini::class.java to (160 to 74),
        SubscriptionNarrow::class.java to (82 to 160),
        DashboardWidgetProvider::class.java to (340 to 268),
        AssetWidgetProvider::class.java to (245 to 148),
        VaultWidgetProvider::class.java to (82 to 82),
        ShortcutsWidgetProvider::class.java to (160 to 160),
        ShortcutsWide::class.java to (340 to 74),
        ShortcutsSmall::class.java to (82 to 82)
    )

    /** 组件的设计尺寸（dp，宽 to 高）；没登记返回 null，预览走兜底比例。 */
    fun designSizeOf(entry: Entry): Pair<Int, Int>? = DESIGN_SIZE_DP[entry.providerClass]

    /** 一屏要展示的全部状态：通道是否可用 + 各组件桌面绑定数 + 是否成功落桌过。 */
    data class Snapshot(
        val pinSupported: Boolean,
        val bound: Map<Class<*>, Int>,
        val pinOk: Boolean
    ) {
        fun boundCount(entry: Entry): Int = bound[entry.providerClass] ?: 0

        /** 组件（含各尺寸形态）在桌面上的实例总数。 */
        fun boundCountOf(component: CatalogComponent): Int =
            component.entries.sumOf { bound[it.providerClass] ?: 0 }
    }

    fun snapshot(context: Context): Snapshot = Snapshot(
        pinSupported = pinSupported(context),
        bound = entries.associate { it.providerClass to boundCount(context, it.providerClass) },
        pinOk = pinSucceededBefore(context)
    )

    /**
     * 系统桌面权限条的出现条件：启动器支持应用内添加 + 是系统桌面 + **还没成功落桌过**。
     * 「桌面快捷方式」权限没有公开查询法，但成功落桌过就是权限已开的实证——之后再常驻
     * 提示只会制造焦虑；后来被关掉权限时，添加失败的 toast 里已有引导文案兜底。
     */
    internal fun showMiuiPermStrip(pinSupported: Boolean, isMiui: Boolean, pinOk: Boolean): Boolean =
        pinSupported && isMiui && !pinOk

    /** 成功落桌过一次的记录（本地存，只增不删）：用于收起小米权限条。 */
    fun pinSucceededBefore(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PIN_OK, false)

    /** [awaitPinned] 数到实例变多时记一笔；测试可直接调它铺路。 */
    internal fun recordPinSuccess(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PIN_OK, true).apply()
    }

    fun pinSupported(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return manager.isRequestPinAppWidgetSupported
    }

    fun boundCount(context: Context, providerClass: Class<*>): Int = boundIds(context, providerClass).size

    /**
     * 桌面上的实例 id 集合。
     * 核对该用 id 而不是数量：落桌后还要按 id 写配置（面板里选的透明度要在这一刻落到新实例上）。
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
     * 顺带把 id 带回去（面板里选的透明度要写到这个新实例上）。
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
    fun requestPin(context: Context, entry: Entry): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        val extras = Bundle().apply {
            putParcelable(
                AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,
                RemoteViews(context.packageName, entry.previewLayoutRes)
            )
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
     * 系统桌面：pin 确认页（AddItemActivity）在真正落桌前会校验
     * 调用方的「桌面快捷方式」权限（logcat: "add widget failed, <pkg> has no permission"），
     * 未授权时确认页静默退出、用户看不到任何界面——需要权限引导文案。
     */
    fun isMiuiHome(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("com.miui.home", 0) != null
    } catch (_: Exception) {
        false
    }

    /** pin 成功回调的 requestCode；组件级共用，回调内容由系统按落桌实例补全。 */
    private const val CONFIGURE_REQUEST_CODE = 424_243

    /** 「成功落桌过」的本地记录（见 [pinSucceededBefore]）。 */
    private const val PREFS_NAME = "widget_pin"
    private const val KEY_PIN_OK = "pin_ok"

    /** 核对落桌结果：最多数 [PIN_VERIFY_ATTEMPTS] 次，每次间隔 [PIN_VERIFY_INTERVAL_MS]。 */
    private const val PIN_VERIFY_ATTEMPTS = 5
    private const val PIN_VERIFY_INTERVAL_MS = 1_500L
}
