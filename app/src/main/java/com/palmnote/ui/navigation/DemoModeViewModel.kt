package com.palmnote.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 主界面壳层的演示模式状态：「示例模式」横幅挂在所有主 tab 之上。 */
@HiltViewModel
class DemoModeViewModel @Inject constructor(
    preferencesManager: PreferencesManager
) : ViewModel() {
    val demoModeOn: StateFlow<Boolean> = preferencesManager.lifeDemoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
