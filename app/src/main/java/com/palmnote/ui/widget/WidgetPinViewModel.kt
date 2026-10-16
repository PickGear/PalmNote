package com.palmnote.ui.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 添加页的落桌收尾：把面板里挑好的**透明度**写到刚落桌的那个实例上。
 *
 * 为什么只能等落桌之后再写：我们的配置一律按 `appWidgetId` 存（键就是实例 id），
 * 而 id 要等桌面确认落桌才存在 —— 所以面板里选的档位要等到核对到新 id 才落得下去。
 * 带配置页的组件（账单）不走这条路：系统落桌后会拉起它的配置页，那里同时管账本与透明度。
 */
@HiltViewModel
class WidgetPinViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    /**
     * 覆盖某个实例的透明度；[opacity] 传 null = 恢复跟随全局默认。
     * 写完立刻整轮刷新一次（只改外观，按 provider 细分不值得），否则要等下一次刷新才看得到。
     */
    fun applyOpacity(appWidgetId: Int, opacity: Float?) {
        viewModelScope.launch {
            preferencesManager.setWidgetOpacityFor(appWidgetId, opacity)
            WidgetUpdateHelper.refreshAllWidgets()
        }
    }
}
