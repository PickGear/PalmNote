package com.palmnote.ui.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 生活页外壳 ViewModel（仅承载演示模式的**一次性说明**状态）。
 *
 * 首页数据（分类卡 / 今日看板 / 待办卡 / 周历）都在 [LifeCalendarViewModel]；
 * 本类只负责「演示开启 且 还没看过说明」时弹一次说明弹窗。
 */
@HiltViewModel
class LifeHomeViewModel @Inject constructor(
    private val prefs: PreferencesManager
) : ViewModel() {

    /**
     * 演示模式的**一次性说明**是否需要弹出（用户定案：默认开启，但要提醒用户）。
     * 只有「演示模式开启 且 还没看过说明」时为 true；用户确认后写入偏好，不再打扰。
     */
    val demoHintVisible: StateFlow<Boolean> =
        combine(prefs.lifeDemoMode, prefs.lifeDemoHintShown) { enabled, shown -> enabled && !shown }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun dismissDemoHint() {
        viewModelScope.launch { prefs.setLifeDemoHintShown(true) }
    }
}
