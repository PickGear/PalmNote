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

    // 缩略图与选择器 previewImage 同源（真实布局离屏渲染），
    // 确认框预览布局与选择器 previewLayout 同源：确认框里看到的就是添加后的样子
    // 顺序即目录里的顺序，按分组排（记账 → 生活 → 其他）
    val entries: List<Entry> = listOf(
        Entry(
            BillWidgetProvider::class.java,
            R.drawable.widget_preview_bill,
            R.layout.widget_bill_unified,
            R.string.widget_bill_title,
            R.string.widget_bill_desc,
            configureActivity = BillWidgetConfigActivity::class.java
        ),
        Entry(
            NetWorthWidgetProvider::class.java,
            R.drawable.widget_preview_net_worth,
            R.layout.widget_net_worth_unified,
            R.string.wallet_total_assets,
            R.string.widget_net_worth_desc,
        ),
        Entry(
            TodoWidgetProvider::class.java,
            R.drawable.widget_preview_todo,
            R.layout.widget_todo_unified,
            R.string.widget_todo_title,
            R.string.widget_todo_desc,
        ),
        Entry(
            HabitWidgetProvider::class.java,
            R.drawable.widget_preview_habit,
            R.layout.widget_habit_unified,
            R.string.widget_habit_title,
            R.string.widget_habit_desc,
        ),
        Entry(
            LifeCounterWidgetProvider::class.java,
            R.drawable.widget_preview_counter,
            R.layout.widget_counter_unified,
            R.string.widget_counter_title,
            R.string.widget_counter_desc,
        ),
        Entry(
            SubscriptionWidgetProvider::class.java,
            R.drawable.widget_preview_subscription,
            R.layout.widget_subscription_unified,
            R.string.widget_subscription_title,
            R.string.widget_subscription_desc,
        ),
        Entry(
            DashboardWidgetProvider::class.java,
            R.drawable.widget_preview_dashboard,
            R.layout.widget_dashboard_unified,
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
            R.layout.widget_vault_small,
            R.string.widget_vault_title,
            R.string.widget_vault_desc,
        ),
        Entry(
            ShortcutsWidgetProvider::class.java,
            R.drawable.widget_preview_shortcuts,
            R.layout.widget_shortcuts_unified,
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
        HabitWidgetProvider::class.java to "4×3",
        LifeCounterWidgetProvider::class.java to "4×3",
        SubscriptionWidgetProvider::class.java to "3×2",
        DashboardWidgetProvider::class.java to "4×3",
        AssetWidgetProvider::class.java to "3×2",
        VaultWidgetProvider::class.java to "1×1",
        ShortcutsWidgetProvider::class.java to "2×2"
    )

    /** 尺寸标签（如 `3×2`）；没登记就返回空串，标签自动隐藏。 */
    fun cellsOf(entry: Entry): String = CELLS[entry.providerClass].orEmpty()

    /** 一屏要展示的全部状态：通道是否可用 + 各组件桌面绑定数。 */
    data class Snapshot(
        val pinSupported: Boolean,
        val bound: Map<Class<*>, Int>
    ) {
        fun boundCount(entry: Entry): Int = bound[entry.providerClass] ?: 0
    }

    fun snapshot(context: Context): Snapshot = Snapshot(
        pinSupported = pinSupported(context),
        bound = entries.associate { it.providerClass to boundCount(context, it.providerClass) }
    )

    fun pinSupported(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return manager.isRequestPinAppWidgetSupported
    }

    fun boundCount(context: Context, providerClass: Class<*>): Int {
        val manager = AppWidgetManager.getInstance(context) ?: return 0
        return manager.getAppWidgetIds(ComponentName(context, providerClass))?.size ?: 0
    }

    /**
     * 发起请求后核对到底有没有落桌。
     *
     * `requestPinAppWidget` 的返回值只说明「这个桌面支持应用内添加」，**不代表添加成功** ——
     * 平台文档写明「返回时不等用户响应，因此返回 true 不等于已固定」。桌面在缺少相关权限时
     * 会静默吞掉请求（确认页一闪而过、桌面什么都没有），只看返回值就会报出假成功。
     * 所以这里不猜：等一会儿重新数一遍桌面上的实例，多了才算成了。
     *
     * 成功会在数到的那一刻立即返回；一直没数到才等到最后一轮结束，此时返回 false（未确认）。
     */
    suspend fun awaitPinned(context: Context, entry: Entry, before: Int): Boolean =
        awaitIncrease(before) { boundCount(context, entry.providerClass) }

    /** [awaitPinned] 的核对逻辑本体：每轮重新取一次数量，比 [before] 多就算落桌了。 */
    internal suspend fun awaitIncrease(before: Int, count: () -> Int): Boolean {
        repeat(PIN_VERIFY_ATTEMPTS) {
            delay(PIN_VERIFY_INTERVAL_MS)
            if (count() > before) return true
        }
        return false
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
     * MIUI/HyperOS 桌面：pin 确认页（AddItemActivity）在真正落桌前会校验
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

    /** 核对落桌结果：最多数 [PIN_VERIFY_ATTEMPTS] 次，每次间隔 [PIN_VERIFY_INTERVAL_MS]。 */
    private const val PIN_VERIFY_ATTEMPTS = 5
    private const val PIN_VERIFY_INTERVAL_MS = 1_500L
}
