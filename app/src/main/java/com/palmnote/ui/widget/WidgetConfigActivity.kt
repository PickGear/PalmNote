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
 * 组件配置页（`android:configure`，**除账单外的组件都用这个**）：只调这个实例的透明度。
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

        setContent {
            PalmNoteTheme {
                WidgetConfigScreen(
                    showBooks = false,
                    booksFlow = flowOf(emptyList()),
                    selectedBookFlow = flowOf(null),
                    opacityOverrideFlow = preferencesManager.widgetOpacityOverride(widgetId),
                    defaultOpacityFlow = preferencesManager.widgetOpacity,
                    onConfirm = ::confirm,
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun confirm(result: WidgetConfigResult) {
        lifecycleScope.launch {
            preferencesManager.setWidgetOpacityFor(widgetId, result.opacityOverride)
            // 这类组件只改了外观：整轮刷新一次最省事，不必按 provider 细分
            WidgetUpdateHelper.refreshAllWidgets()
            setResult(RESULT_OK, WidgetConfigContract.okResult(widgetId))
            finish()
        }
    }
}
