package com.palmnote.ui.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.AccountBookDao
import com.palmnote.ui.theme.PalmNoteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 账单组件的配置页（`android:configure`）：选这个实例只看哪个账本，外加它自己的透明度。
 *
 * 单独一个 Activity 是因为**账本这一段只有账单组件有**——配置页由各组件在
 * `*_info.xml` 里各自声明，不必在运行时去猜「正在配置哪个组件」。
 */
@AndroidEntryPoint
class BillWidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var accountBookDao: AccountBookDao

    private var widgetId = WidgetConfigContract.INVALID_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 与 MainActivity 一致地走 edge-to-edge（同 WidgetConfigActivity 的说明）
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)
        widgetId = WidgetConfigContract.widgetIdOf(intent)
        if (!WidgetConfigContract.canConfigure(widgetId)) {
            finish()
            return
        }

        setContent {
            PalmNoteTheme {
                WidgetConfigScreen(
                    showBooks = true,
                    booksFlow = accountBookDao.getAllBooks(),
                    selectedBookFlow = preferencesManager.widgetBook(widgetId),
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
            preferencesManager.setWidgetBook(widgetId, result.bookId)
            preferencesManager.setWidgetOpacityFor(widgetId, result.opacityOverride)
            // 立刻按新设置重出内容：桌面拿到 RESULT_OK 后也会刷新一次，这里先动避免看到旧数据
            WidgetUpdateHelper.refreshBillWidgets()
            setResult(RESULT_OK, WidgetConfigContract.okResult(widgetId))
            finish()
        }
    }
}
