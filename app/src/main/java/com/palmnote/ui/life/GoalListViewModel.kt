package com.palmnote.ui.life

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.Goal
import com.palmnote.domain.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 目标列表（总纲 P5d）：goals 表早于本页存在（仪表盘/CSV 早已读写），这里第一次给它 UI。
 *
 * 只接**累计型（CUMULATIVE）与目标值型（TARGET）**；习惯打卡不在这里——习惯语义由
 * 生活模板体系承担，HABIT 型目标行（含演示播种的历史行）不进列表。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GoalListViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val preferences: PreferencesManager
) : ViewModel() {

    /** 目标行：演示感知（互斥）与生活条目同一口径——演示开只看示例、关只看自己的。 */
    val goals: StateFlow<List<Goal>> = preferences.lifeDemoMode
        .flatMapLatest { demo ->
            goalRepository.getAllGoals().map { list ->
                list.filter { it.goalType != TYPE_HABIT && it.isDemo == demo }
            }
        }
        .catchLife("goal.list", emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 快选 ±：[towardGoal] = true 表示朝目标推进一步。
     * 目标值型递减（如戒烟）的「推进」是把当前值往下减，方向在这里翻成同一种语义。
     */
    fun nudge(goal: Goal, towardGoal: Boolean) {
        viewModelScope.launch {
            val current = goalRepository.getGoalById(goal.id) ?: return@launch
            val delta = if (towardGoal) 1 else -1
            val next = if (current.direction == DIRECTION_DECREASE) current.currentCount - delta
            else current.currentCount + delta
            // 递减型的合法区间是 [目标值, 当前起点]（比如戒烟：目标 0、起点 20），
            // 不能按累计型钳到 [0, 目标值]——那样第一步就会被钳回目标值。
            val clamped = if (current.direction == DIRECTION_DECREASE) {
                val low = minOf(current.totalCount, current.initialValue.toInt())
                val high = maxOf(current.totalCount, current.initialValue.toInt())
                next.coerceIn(low, high)
            } else {
                next.coerceIn(0, current.totalCount)
            }
            goalRepository.setGoalProgress(current.id, clamped)
        }
    }

    fun delete(goalId: Long) {
        viewModelScope.launch { goalRepository.deleteGoal(goalId) }
    }

    companion object {
        const val TYPE_HABIT = "HABIT"
        const val DIRECTION_DECREASE = "DECREASE"
    }
}

/**
 * 目标进度比例（0..1）：目标值型递减按「初始 → 目标」的跨度算，
 * 其余按「当前 / 目标」算；跨度或目标为 0 一律 0，不除零。
 */
internal fun goalProgressFraction(goal: Goal): Float = when {
    goal.direction == GoalListViewModel.DIRECTION_DECREASE && goal.initialValue > goal.totalCount ->
        ((goal.initialValue - goal.currentCount).toFloat() / (goal.initialValue - goal.totalCount))
            .coerceIn(0f, 1f)
    goal.totalCount > 0 -> (goal.currentCount.toFloat() / goal.totalCount).coerceIn(0f, 1f)
    else -> 0f
}

/**
 * 目标编辑/新建：[goalId] = 0 新建。保存时演示标记跟随当前演示模式——
 * 演示期建的目标带示例标记，关演示时一并清理（与生活条目同一口径）。
 */
@HiltViewModel
class GoalEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val goalRepository: GoalRepository,
    private val preferences: PreferencesManager
) : ViewModel() {

    private val goalId: Long = when (val raw = savedStateHandle.get<Any>("goalId")) {
        is Long -> raw
        is Int -> raw.toLong()
        is String -> raw.toLongOrNull() ?: 0L
        else -> 0L
    }

    data class UiState(
        val title: String = "",
        val cumulative: Boolean = true,
        val target: Int = 100,
        val unit: String = "次",
        val decrease: Boolean = false,
        val initialValue: Long = 0L,
        /** 编辑已有目标（goalId > 0）：顶栏标题与空表单初始值都按此区分。 */
        val isEdit: Boolean = false,
        val loaded: Boolean = false,
        val saved: Boolean = false
    )

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(UiState())
    val state: kotlinx.coroutines.flow.StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            if (goalId > 0L) {
                val goal = goalRepository.getGoalById(goalId) ?: return@launch
                _state.value = UiState(
                    title = goal.title,
                    cumulative = goal.goalType == "CUMULATIVE",
                    target = goal.totalCount,
                    unit = goal.unit,
                    decrease = goal.direction == GoalListViewModel.DIRECTION_DECREASE,
                    initialValue = if (goal.initialValue > 0) goal.initialValue else goal.currentCount.toLong(),
                    isEdit = true,
                    loaded = true
                )
            } else {
                _state.value = _state.value.copy(loaded = true)
            }
        }
    }

    fun onEdit(transform: (UiState) -> UiState) {
        _state.value = transform(_state.value)
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.title.isBlank() || s.target <= 0) return
        // 递减型的当前值必须高于目标值，否则区间退化、± 都无效
        if (!s.cumulative && s.decrease && s.initialValue <= s.target) return
        viewModelScope.launch {
            val demo = preferences.lifeDemoMode.first()
            val direction = if (!s.cumulative && s.decrease) GoalListViewModel.DIRECTION_DECREASE else "INCREASE"
            val existing = if (goalId > 0L) goalRepository.getGoalById(goalId) else null
            val goal = existing?.let { buildEditedGoal(it, s, direction) } ?: buildNewGoal(s, direction, demo)
            if (goalId > 0L) goalRepository.updateGoal(goal) else goalRepository.insertGoal(goal)
            _state.value = s.copy(saved = true)
            onSaved()
        }
    }

    /** 编辑：在存量行上 copy，没在表单里的 20+ 列（截止日/打卡统计/关联资产…）原样保留。 */
    private fun buildEditedGoal(existing: Goal, s: UiState, direction: String): Goal = existing.copy(
        title = s.title.trim(),
        goalType = if (s.cumulative) "CUMULATIVE" else "TARGET",
        totalCount = s.target,
        unit = s.unit.ifBlank { "次" },
        direction = direction,
        initialValue = if (direction == GoalListViewModel.DIRECTION_DECREASE) s.initialValue else 0L,
        // 切成递减型且还没有进度时，当前值从起点起算
        currentCount = if (direction == GoalListViewModel.DIRECTION_DECREASE && existing.currentCount == 0) {
            s.initialValue.toInt()
        } else {
            existing.currentCount
        },
        updatedAt = System.currentTimeMillis()
    )

    private fun buildNewGoal(s: UiState, direction: String, demo: Boolean): Goal = Goal(
        title = s.title.trim(),
        goalType = if (s.cumulative) "CUMULATIVE" else "TARGET",
        totalCount = s.target,
        currentCount = if (direction == GoalListViewModel.DIRECTION_DECREASE) s.initialValue.toInt() else 0,
        unit = s.unit.ifBlank { "次" },
        direction = direction,
        initialValue = if (direction == GoalListViewModel.DIRECTION_DECREASE) s.initialValue else 0L,
        icon = com.palmnote.ui.theme.AppIcon.Flag,
        isDemo = demo,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
