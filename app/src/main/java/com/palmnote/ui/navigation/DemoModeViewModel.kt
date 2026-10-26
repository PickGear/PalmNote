package com.palmnote.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 「示例模式」横幅的共享状态：横幅挂在主 NavHost 壳层顶部（[PalmNoteNavHost]），
 * 主界面四个 tab 与全部二级页（设置 / 详情 / 搜索……）都可见。
 *
 * 横幅关闭只记**本次会话**（进程内存态，不落盘）：旋转屏幕不复现，
 * 下次冷启动 app 再次出现（示例模式还开着就再提醒一次）。
 *
 * [PalmNoteNavHost] 在根壳层用 [androidx.hilt.navigation.compose.hiltViewModel]
 * 获取本 VM（activity 作用域，全局单实例）；[MainTabs] 拿到同一实例后，
 * 把「当前是否停在生活 tab」上报到 [lifeTabSelected]，横幅文案据此在
 * 通用口径与生活页「互斥」口径之间切换。
 */
@HiltViewModel
class DemoModeViewModel @Inject constructor(
    preferencesManager: PreferencesManager
) : ViewModel() {
    val demoModeOn: StateFlow<Boolean> = preferencesManager.lifeDemoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 用户已在本次会话关掉示例模式横幅（内存态；进程结束即复现）。 */
    var bannerDismissed by mutableStateOf(false)
        private set

    /** 当前停在生活 tab（[MainTabs] 上报）：横幅文案切生活页「互斥」口径。 */
    var lifeTabSelected by mutableStateOf(false)
        private set

    fun onBannerDismissed() {
        bannerDismissed = true
    }

    /** [MainTabs] 在 tab 切换 / 离开组合时上报；离开主界面（进二级页）报 false。 */
    fun onLifeTabSelected(selected: Boolean) {
        lifeTabSelected = selected
    }
}
