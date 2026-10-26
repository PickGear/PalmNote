package com.palmnote.ui.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.theme.PalmNoteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * 组件配置页（`android:configure`，只有**概览 / 接下来**在 provider XML 里声明它）：
 * 只改这一个实例的**强调色覆盖**（这两类组件渲染吃强调色）。
 *
 * 不透明度与背景色不在这里 —— 它们在添加/编辑面板（`WidgetPinScreen`）里改，
 * 与实例 id 一起直接落到 DataStore，不必等落桌确认。账单走它自己的
 * `BillWidgetConfigActivity`（账本 + 配色 + 预算口径）。
 *
 * 桌面在摆放时拉起本页，用户确认后回传 RESULT_OK 才会真正落桌；
 * 桌面长按已放置的组件也能再次进这里改设置。
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private var widgetId = WidgetConfigContract.INVALID_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 与 MainActivity 一致地走 edge-to-edge：targetSdk 升到 35+ 时系统会强制铺满，
        // 那时没有这一步、也没有内边距处理的话，标题与返回键会被状态栏压住。
        enableEdgeToEdge()
        // 兜底：没走到 confirm 就退出（返回键/进程被杀）＝用户放弃，组件不落桌
        setResult(RESULT_CANCELED)
        widgetId = WidgetConfigContract.widgetIdOf(intent)
        if (!WidgetConfigContract.canConfigure(widgetId)) {
            finish()
            return
        }

        // 「配色」只对渲染吃强调色的组件显示：账单/概览/接下来，其余组件配了也不生效
        val providerClass = android.appwidget.AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(widgetId)?.provider?.className
        val showAccent = providerClass in setOf(
            BillWidgetProvider::class.java.name,
            DashboardWidgetProvider::class.java.name,
            NextUpWidgetProvider::class.java.name
        )
        setContent {
            PalmNoteTheme {
                WidgetConfigScreen(
                    showBooks = false,
                    showAccent = showAccent,
                    booksFlow = flowOf(emptyList()),
                    selectedBookFlow = flowOf(null),
                    accentThemeOverrideFlow = preferencesManager.widgetAccentThemeFor(widgetId),
                    onConfirm = ::confirm,
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun confirm(result: WidgetConfigResult) {
        lifecycleScope.launch {
            preferencesManager.setWidgetAccentThemeFor(widgetId, result.accentThemeId)
            // 这类组件只改了外观：整轮刷新一次最省事，不必按 provider 细分
            WidgetUpdateHelper.refreshAllWidgets()
            setResult(RESULT_OK, WidgetConfigContract.okResult(widgetId))
            finish()
        }
    }
}
