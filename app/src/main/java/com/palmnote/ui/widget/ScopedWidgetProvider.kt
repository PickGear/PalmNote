package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.util.AppLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 走数据库查询的组件共用的协程样板：goAsync 生命周期绑定 + 缓存作用域，
// 子类只实现 onUpdateAsync（取数 + 构建 RemoteViews），异常统一按类名记日志。
// 十个组件（40 个尺寸形态）全部继承本类 —— 发布出口 [publish] 是色调/不透明度唯一的落地点，绕开它就会漏掉其中一项。
abstract class ScopedWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PreferencesEntryPoint {
        fun preferencesManager(): PreferencesManager
    }

    private var cachedScope: CoroutineScope? = null

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        cachedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cachedScope?.cancel()
        cachedScope = null
    }

    /**
     * 组件从桌面移除时清掉它的**按实例**配置（账本、不透明度、配色、背景、模糊度）。
     * 桌面会复用 appWidgetId，不清的话新摆的组件会继承上一个实例的设置。
     *
     * 图片背景的**持久 URI 授权**也要一起释放：否则删除/换背景后授权会一直累积，
     * 系统对每个应用能持久持有的 URI 数量有上限，攒满后新选图会静默失效。
     */
    final override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val preferences = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PreferencesEntryPoint::class.java
        ).preferencesManager()
        // 广播返回后进程可能被回收：清理协程要绑 goAsync 才可靠
        val pendingResult = goAsync()
        val ownedScope = cachedScope == null
        val scope = cachedScope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                appWidgetIds.forEach {
                    releaseImageBackground(context, preferences, it)
                    preferences.setWidgetBook(it, null)
                    preferences.setWidgetOpacityFor(it, null)
                    preferences.setWidgetAccentThemeFor(it, null)
                    preferences.setWidgetBackgroundFor(it, null)
                    preferences.setWidgetBlurFor(it, null)
                }
            } finally {
                if (ownedScope) scope.cancel()
                pendingResult.finish()
            }
        }
    }

    /** 该实例若用图片背景，释放它的持久读取授权（失败不影响清理）。 */
    private suspend fun releaseImageBackground(
        context: Context,
        preferences: PreferencesManager,
        appWidgetId: Int
    ) {
        val background = preferences.widgetBackgroundFor(appWidgetId).first() ?: return
        if (!background.startsWith("img:")) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(background.removePrefix("img:")),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    final override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        launchUpdate { onUpdateAsync(context, appWidgetManager, appWidgetIds) }
    }

    final override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        launchUpdate { onUpdateAsync(context, appWidgetManager, intArrayOf(appWidgetId)) }
    }

    protected abstract suspend fun onUpdateAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    )

    /**
     * 统一发布出口。每个实例各自定两件事：
     *
     * - **不透明度**（有逐实例覆盖用覆盖，否则跟随全局默认）：只作用在**背景层** ——
     *   卡片底色把 alpha 写进颜色、背景图用 `setImageAlpha`、会盖住整张卡的内层大色块
     *   （概览四块砖、快捷入口四格）也跟着变透（[WidgetOpacityFade]）。卡上的文字/图标保持实色，
     *   不跟着发灰（旧实现在 31+ 对整卡 `setAlpha`，正文对比度被一起压掉，也与面板预览对不上）。
     * - **内容色调** [WidgetStyle.tone]：实例接管了背景（自定义纯色/图片）时按背景色配 on-color
     *   （白底深字、其他颜色浅字，见 [WidgetTonePicker]）。
     *   做法是把色调塞进 Context（[WidgetToneContext]），组件里的 `context.getColor(...)`、
     *   `WidgetData.colorFamily(...)`、以及 [widgetViews] 建 RemoteViews 时读到的布局色，
     *   全都自动落到该明暗的令牌上 —— 十几个组件不必逐个传参。
     *   未接管背景时色调传 null，卡上的颜色由桌面按系统明暗解析（[widgetViews] 仍会按系统那一档
     *   重下一遍，用来复位上一轮自定义背景留在视图树上的旧色）。
     *
     * 顺序：背景解码在 bind **之前**（色调要先定），背景绘制在 bind **之后**（组件设的语义色排在前面，
     * 描边/逾期红/今日高亮不会被色调抹平）。偏好读取走 prefsFlow（内部已 catch 兜底），坏值不会让组件停更。
     */
    protected suspend fun publish(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        bind: suspend (context: Context, appWidgetId: Int, style: WidgetStyle) -> RemoteViews
    ) {
        val preferences = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PreferencesEntryPoint::class.java
        ).preferencesManager()
        appWidgetIds.forEach { appWidgetId ->
            val opacity = preferences.widgetOpacityFor(appWidgetId).first()
            val (widthDp, heightDp) = appWidgetManager.getAppWidgetOptions(appWidgetId).widgetSizeDp()
            val background = WidgetWallpaper.load(context, preferences, appWidgetId, widthDp, heightDp)
            // 接管了背景就按背景色配内容色调（白底深字，其他颜色浅字）；否则跟随系统
            val style = WidgetStyle(opacity, WidgetWallpaper.toneOf(context, background))
            // 色调与不透明度一起塞进 Context：色调决定取哪套令牌，alpha 只乘进底色（见 WidgetToneSweep）
            val views = bind(WidgetToneContext.of(context, style.tone, style.opacity), appWidgetId, style)
            WidgetWallpaper.apply(context, views, background, widthDp, heightDp, opacity)
            WidgetOpacityFade.apply(views, opacity)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun launchUpdate(block: suspend () -> Unit) {
        val pendingResult = goAsync()
        // onEnabled 未触发的路径（进程被杀后直接 onUpdate）没有缓存作用域：
        // 用临时作用域并在收尾取消，避免孤儿 Job 泄漏（审计 #16）
        val ownedScope = cachedScope == null
        val scope = cachedScope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                AppLogger.e(this@ScopedWidgetProvider::class.java.simpleName, "Widget update failed", e)
            } finally {
                pendingResult.finish()
                if (ownedScope) scope.cancel()
            }
        }
    }
}
