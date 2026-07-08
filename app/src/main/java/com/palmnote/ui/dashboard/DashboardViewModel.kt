package com.palmnote.ui.dashboard

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.dao.CategoryCount
import com.palmnote.data.db.entity.Anniversary
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.Goal
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.repository.*
import com.palmnote.domain.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
data class DashboardState(
    val totalAssetValue: Double = 0.0,
    val activeAssetCount: Int = 0,
    val monthlyExpense: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val budget: Budget? = null,
    val budgetReminderEnabled: Boolean = true,
    val goalCount: Int = 0,
    val completedGoalCount: Int = 0,
    val anniversaryCount: Int = 0,
    val upcomingAnniversaries: List<Anniversary> = emptyList(),
    val recentGoals: List<Goal> = emptyList(),
    val assetDistribution: List<CategoryCount> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val assetRepository: AssetRepository,
    private val billRepository: BillRepository,
    private val budgetRepository: BudgetRepository,
    private val goalRepository: GoalRepository,
    private val anniversaryRepository: AnniversaryRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _cardConfigs = MutableStateFlow(DashboardCardConfig.defaults)
    val cardConfigs: StateFlow<List<DashboardCardConfig>> = _cardConfigs.asStateFlow()

    val visibleConfigs: StateFlow<List<DashboardCardConfig>> = _cardConfigs
        .map { it.filter { c -> c.visible } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardCardConfig.defaults.filter { it.visible })

    init {
        loadDashboardData()
        loadBudgetReminder()
        loadCardConfigs()
    }

    private fun loadCardConfigs() {
        viewModelScope.launch {
            preferencesManager.dashboardCardConfigs.collect { configs ->
                _cardConfigs.value = configs
            }
        }
    }

    private var saveConfigsJob: Job? = null

    private fun saveConfigs() {
        saveConfigsJob?.cancel()
        saveConfigsJob = viewModelScope.launch {
            delay(300) // debounce: 拖拽排序时避免高频写入
            preferencesManager.saveDashboardCardConfigs(_cardConfigs.value)
        }
    }

    fun moveCardDown(type: CardType) {
        val list = _cardConfigs.value.toMutableList()
        val idx = list.indexOfFirst { it.type == type }
        if (idx < list.size - 1) {
            val item = list.removeAt(idx)
            list.add(idx + 1, item)
            _cardConfigs.value = list
            saveConfigs()
        }
    }

    fun moveCardUp(type: CardType) {
        val list = _cardConfigs.value.toMutableList()
        val idx = list.indexOfFirst { it.type == type }
        if (idx > 0) {
            val item = list.removeAt(idx)
            list.add(idx - 1, item)
            _cardConfigs.value = list
            saveConfigs()
        }
    }

    fun toggleCard(type: CardType) {
        val list = _cardConfigs.value.toMutableList()
        val idx = list.indexOfFirst { it.type == type }
        if (idx >= 0) {
            list[idx] = list[idx].copy(visible = !list[idx].visible)
            _cardConfigs.value = list
            saveConfigs()
        }
    }

    private fun loadBudgetReminder() {
        viewModelScope.launch {
            preferencesManager.budgetReminderEnabled.collect { enabled ->
                _state.value = _state.value.copy(budgetReminderEnabled = enabled)
            }
        }
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            val currentYearMonth = DateUtils.getCurrentYearMonth()
            val assetFlow = combine(
                assetRepository.getTotalAssetValue(),
                assetRepository.getTotalAssetCount(),
                assetRepository.getCategoryDistribution()
            ) { total, count, distribution ->
                Triple(total ?: 0.0, count, distribution)
            }
            val billFlow = combine(
                billRepository.getMonthlyExpense(currentYearMonth),
                billRepository.getMonthlyIncome(currentYearMonth)
            ) { expense, income ->
                Pair(expense ?: 0.0, income ?: 0.0)
            }
            val gaFlow = combine(
                goalRepository.getGoalCount(),
                goalRepository.getCompletedGoalCount(),
                anniversaryRepository.getAnniversaryCount(),
                anniversaryRepository.getAllAnniversaries(),
                goalRepository.getAllGoals()
            ) { goalCount, completedCount, annivCount, anniversaries, goals ->
                GoalAnnivData(goalCount, completedCount, annivCount, anniversaries, goals)
            }
            val budgetFlow = budgetRepository.getBudgetByMonthFlow(currentYearMonth)
            combine(assetFlow, billFlow, gaFlow, budgetFlow) { assetData, billData, gaData, budget ->
                val s = _state.value
                DashboardState(
                    totalAssetValue = assetData.first,
                    activeAssetCount = assetData.second,
                    monthlyExpense = billData.first,
                    monthlyIncome = billData.second,
                    budget = budget,
                    budgetReminderEnabled = s.budgetReminderEnabled,
                    goalCount = gaData.goalCount,
                    completedGoalCount = gaData.completedGoalCount,
                    anniversaryCount = gaData.anniversaryCount,
                    upcomingAnniversaries = gaData.anniversaries.sortedBy { it.daysUntil }.take(3),
                    recentGoals = gaData.goals.take(3),
                    assetDistribution = assetData.third
                )
            }.collect { s -> _state.value = s }
        }
    }
}

private data class GoalAnnivData(
    val goalCount: Int,
    val completedGoalCount: Int,
    val anniversaryCount: Int,
    val anniversaries: List<Anniversary>,
    val goals: List<Goal>
)
