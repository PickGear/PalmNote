package com.palmnote.ui.life

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 单日回读页数据源（v4 §五）：点月格某天后进入。
 *
 * 取该天全部条目（口径 = COALESCE(dueDate, createdAt)，与月历密度一致），
 * 演示感知——关闭演示模式时排除带标记的示例行。条目已含模板配色/图标/分类，供 UI 直接上色。
 *
 * 日期由路由参数 [ARG_DATE_KEY]（ISO `yyyy-MM-dd`）经 [SavedStateHandle] 传入。
 */
@HiltViewModel
class LifeDayReadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val preferences: PreferencesManager,
    private val lifeItemDao: LifeItemDao
) : ViewModel() {

    private val dateKey: String =
        savedStateHandle.get<String>(ARG_DATE_KEY).orEmpty().ifBlank { LocalDate.now().toString() }

    val items: StateFlow<List<LifeItemDao.LifeDayItemRow>> =
        preferences.lifeDemoMode
            .flatMapLatest { includeDemo ->
                val (start, end) = dayRange(dateKey)
                lifeItemDao.getItemsForDayDemoAware(start, end, includeDemo, LIFE_DEMO_META)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        const val ARG_DATE_KEY = "dateKey"
    }
}

/** ISO 日期 → 当天 [start, end) 的毫秒区间（按系统默认时区，与月历一致）。 */
private fun dayRange(key: String): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: LocalDate.now()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
}
