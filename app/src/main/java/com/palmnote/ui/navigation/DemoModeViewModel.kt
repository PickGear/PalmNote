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
 * 主界面壳层的演示模式状态：「示例模式」横幅挂在所有主 tab 之上。
 *
 * 横幅关闭只记**本次会话**（进程内存态，不落盘）：旋转屏幕不复现，
 * 下次冷启动 app 再次出现（示例模式还开着就再提醒一次）。
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

    fun onBannerDismissed() {
        bannerDismissed = true
    }
}
