package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import com.palmnote.app.R

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
        val descRes: Int
    )

    // 缩略图与选择器 previewImage 同源（真实布局离屏渲染），
    // 确认框预览布局与选择器 previewLayout 同源：确认框里看到的就是添加后的样子
    val entries: List<Entry> = listOf(
        Entry(
            DashboardWidgetProvider::class.java,
            R.drawable.widget_preview_dashboard,
            R.layout.widget_dashboard_unified,
            R.string.widget_dashboard_title,
            R.string.widget_dashboard_desc
        ),
        Entry(
            BillWidgetProvider::class.java,
            R.drawable.widget_preview_bill,
            R.layout.widget_bill_unified,
            R.string.widget_bill_title,
            R.string.widget_bill_desc
        ),
        Entry(
            TodoWidgetProvider::class.java,
            R.drawable.widget_preview_todo,
            R.layout.widget_todo_unified,
            R.string.widget_todo_title,
            R.string.widget_todo_desc
        ),
        Entry(
            HabitWidgetProvider::class.java,
            R.drawable.widget_preview_habit,
            R.layout.widget_habit_unified,
            R.string.widget_habit_title,
            R.string.widget_habit_desc
        ),
        Entry(
            LifeCounterWidgetProvider::class.java,
            R.drawable.widget_preview_counter,
            R.layout.widget_counter_unified,
            R.string.widget_counter_title,
            R.string.widget_counter_desc
        ),
        Entry(
            AssetWidgetProvider::class.java,
            R.drawable.widget_preview_asset,
            R.layout.widget_asset_unified,
            R.string.widget_asset_title,
            R.string.widget_asset_desc
        ),
        Entry(
            VaultWidgetProvider::class.java,
            R.drawable.widget_preview_vault,
            R.layout.widget_vault_small,
            R.string.widget_vault_title,
            R.string.widget_vault_desc
        ),
        Entry(
            ShortcutsWidgetProvider::class.java,
            R.drawable.widget_preview_shortcuts,
            R.layout.widget_shortcuts_unified,
            R.string.widget_shortcuts_title,
            R.string.widget_shortcuts_desc
        )
    )

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
     * 发起固定请求。附带预览布局（EXTRA_APPWIDGET_PREVIEW，API 31+ 生效），
     * 旧启动器会忽略不认识的 extra，不影响请求本身。返回 false = 通道不可用。
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
        return manager.requestPinAppWidget(ComponentName(context, entry.providerClass), extras, null)
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
}
