package com.palmnote.ui.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 生活页外壳 ViewModel（Phase 1 仅承载"默认视图"持久化）。
 *
 * 三视图（今天 / 日历 / 全部）不再用分段切换器，改由左上角标题文字循环切换；
 * 长按标题把当前视图写为默认页，下次进入生活页直接落在这个视图。
 * 存储键沿用既有的 [PreferencesManager.LIFE_HOME_VIEW]。
 */
@HiltViewModel
class LifeHomeViewModel @Inject constructor(
    private val prefs: PreferencesManager
) : ViewModel() {

    /** 默认视图 key：today / calendar / all，默认 today。 */
    val defaultView: StateFlow<String> = prefs.lifeHomeView
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LifeViewKey.TODAY)

    fun setDefaultView(key: String) {
        viewModelScope.launch { prefs.setLifeHomeView(key) }
    }
}
