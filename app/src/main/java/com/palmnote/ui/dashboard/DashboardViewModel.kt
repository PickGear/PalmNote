package com.palmnote.ui.dashboard
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.dao.CategoryCount
import com.palmnote.data.db.entity.Anniversary
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.Goal
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.domain.model.SubscriptionDueItem
import com.palmnote.domain.repository.*
import com.palmnote.domain.util.DateUtils
import com.palmnote.feature.vault.VaultRepository
import com.palmnote.ui.theme.AppIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.palmnote.domain.util.AppLogger


@Stable
data class DashboardState(
    val totalAssetValue: Long = 0,
    val activeAssetCount: Int = 0,
    val monthlyExpense: Long = 0,
    val monthlyIncome: Long = 0,
    val budget: Budget? = null,
    val budgetReminderEnabled: Boolean = true,
    val anniversaryCount: Int = 0,
    val upcomingAnniversaries: List<Anniversary> = emptyList(),
    val assetDistribution: List<CategoryCount> = emptyList(),
    val vaultCount: Int = 0,
    val habitTotal: Int = 0,
    val habitChecked: Int = 0,
    val habitRows: List<HabitTodayRow> = emptyList(),
    val upcomingSubscriptions: List<SubscriptionDueItem> = emptyList()
)

@Stable
data class HabitTodayRow(
    val goalId: Long,
    val title: String,
    val icon: AppIcon,
    val frequency: String,
    val isCheckedToday: Boolean
)

@HiltViewModel
@Suppress("LongParameterList")
class DashboardViewModel @Inject constructor(
    private val assetRepository: AssetRepository,
    private val billRepository: BillRepository,
    private val budgetRepository: BudgetRepository,
    private val goalRepository: GoalRepository,
    private val anniversaryRepository: AnniversaryRepository,
    private val preferencesManager: PreferencesManager,
    private val vaultRepository: VaultRepository,
    private val walletRepository: WalletRepository,
    private val lifeItemRepository: LifeItemRepository,
    private val cachedCategoryConfigs: @JvmSuppressWildcards StateFlow<List<CategoryConfig>>,
    private val templateRepository: LifeTemplateRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _itemTemplateIcons = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _cardConfigs = MutableStateFlow(DashboardCardConfig.defaults)
    val cardConfigs: StateFlow<List<DashboardCardConfig>> = _cardConfigs.asStateFlow()

    val visibleConfigs: StateFlow<List<DashboardCardConfig>> = _cardConfigs
        .map { it.filter { c -> c.visible } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardCardConfig.defaults.filter { it.visible })

    val presetCategoryOverrides: StateFlow<Map<String, String>> =
        preferencesManager.presetCategoryOverrides
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val categoryConfigs: StateFlow<List<CategoryConfig>> = cachedCategoryConfigs

    private val _dashboardMessageMode = MutableStateFlow(preferencesManager.getDashboardMessageModeSync())
    val dashboardMessageMode: StateFlow<Boolean> = _dashboardMessageMode.asStateFlow()

    val dashboardMessageLastDate: StateFlow<String> = preferencesManager.dashboardMessageLastDate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val profileNickname: StateFlow<String> = preferencesManager.profileNickname
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val profileAvatar: StateFlow<String> = preferencesManager.profileAvatar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Spa")

    val profileAvatarPath: StateFlow<String> = preferencesManager.profileAvatarPath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    init {
        loadDashboardData()
        loadBudgetReminder()
        viewModelScope.launch {
            templateRepository.getAllTemplates().collect { tpls ->
                _itemTemplateIcons.value = tpls.associate { it.id to it.icon }
            }
        }
        loadCardConfigs()
        loadVaultData()
        loadDashboardMessageMode()
    }

    private fun loadDashboardMessageMode() {
        viewModelScope.launch {
            preferencesManager.dashboardMessageMode.collect { mode ->
                _dashboardMessageMode.value = mode
            }
        }
    }

    private fun loadCardConfigs() {
        viewModelScope.launch {
            // collect 而非 first()：外部修改卡片配置时保持同步
            preferencesManager.dashboardCardConfigs.collect { configs ->
                _cardConfigs.value = configs
            }
        }
    }

    private var saveConfigsJob: Job? = null

    private fun saveConfigs() {
        saveConfigsJob?.cancel()
        saveConfigsJob = viewModelScope.launch {
            delay(300)
            _cardConfigs.value.let { preferencesManager.saveDashboardCardConfigs(it) }
        }
    }

    fun setCardCustomColor(type: CardType, color: String?) {
        _cardConfigs.update { configs ->
            configs.map { if (it.type == type) it.copy(customColor = color) else it }
        }
        viewModelScope.launch {
            preferencesManager.saveDashboardCardConfigs(_cardConfigs.value)
        }
    }

    fun moveCardDown(type: CardType) {
        _cardConfigs.update { configs ->
            val list = configs.toMutableList()
            val idx = list.indexOfFirst { it.type == type }
            if (idx < list.size - 1) {
                val item = list.removeAt(idx)
                list.add(idx + 1, item)
                list
            } else configs
        }
        saveConfigs()
    }

    fun moveCardUp(type: CardType) {
        _cardConfigs.update { configs ->
            val list = configs.toMutableList()
            val idx = list.indexOfFirst { it.type == type }
            if (idx > 0) {
                val item = list.removeAt(idx)
                list.add(idx - 1, item)
                list
            } else configs
        }
        saveConfigs()
    }

    fun toggleCard(type: CardType) {
        _cardConfigs.update { configs ->
            configs.map { if (it.type == type) it.copy(visible = !it.visible) else it }
        }
        saveConfigs()
    }

    fun toggleDashboardMessageMode() {
        viewModelScope.launch {
            val current = dashboardMessageMode.value
            preferencesManager.setDashboardMessageMode(!current)
        }
    }

    fun setDashboardMessageLastDate(date: String) {
        viewModelScope.launch {
            preferencesManager.setDashboardMessageLastDate(date)
        }
    }

    fun checkInHabit(goalId: Long) {
        viewModelScope.launch {
            try {
                val today = DateUtils.getTodayStart()
                val already = goalRepository.getTodayCheckedGoalIds(today, today + DateUtils.MILLIS_PER_DAY).first()
                if (goalId in already) return@launch  // 快速连点/重复回调会导致当日重复计数
                val checkInId = goalRepository.insertCheckIn(com.palmnote.data.db.entity.GoalCheckIn(goalId = goalId, date = today))
                if (checkInId > 0) goalRepository.incrementGoalProgress(goalId)
            } catch (e: Exception) {
                AppLogger.e("DashboardVM", "checkInHabit failed", e)
            }
        }
    }

    private fun loadBudgetReminder() {
        viewModelScope.launch {
            preferencesManager.budgetReminderEnabled.collect { enabled ->
                _state.update { it.copy(budgetReminderEnabled = enabled) }
            }
        }
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            buildDashboardFlow()
                .catch { e -> AppLogger.e("DashboardVM", "loadDashboardData failed", e) }
                .collect { (c, subs) ->
                    _state.update { s -> s.copy(
                        totalAssetValue = c.assetData.first,
                        activeAssetCount = c.assetData.second,
                        monthlyExpense = c.billData.first,
                        monthlyIncome = c.billData.second,
                        budget = c.budget,
                        anniversaryCount = c.gaData.anniversaryCount,
                        upcomingAnniversaries = c.gaData.anniversaries.sortedBy { it.daysUntil }.take(3),
                        assetDistribution = c.assetData.third,
                        habitTotal = c.habitData.total,
                        habitChecked = c.habitData.checked,
                        habitRows = c.habitData.rows,
                        upcomingSubscriptions = subs
                    )}
                }
        }
    }

    /** 跨天信号：每次进入新的一天发一枚，让月度/今日维度的数据在午夜后自动重算。 */
    private fun dayTickFlow(): Flow<Unit> = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            val now = java.util.Calendar.getInstance()
            val next = (now.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            delay((next.timeInMillis - System.currentTimeMillis()).coerceAtLeast(1000L))
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun buildDashboardFlow(): Flow<Pair<CoreData, List<SubscriptionDueItem>>> = dayTickFlow().flatMapLatest {
        // 月/今日的边界此前在 VM 创建时算死，跨天后预算/今日打卡一直是旧值
        val currentYearMonth = DateUtils.getCurrentYearMonth()
        val todayStart = DateUtils.getTodayStart()
        val tomorrowStart = todayStart + DateUtils.MILLIS_PER_DAY
        // NET_WORTH 主数值 = Wallet 账户余额(启用非信用卡钱包),不再用物品购买总价
        val assetFlow = combine(
            walletRepository.getTotalBalance(),
            assetRepository.getTotalAssetCount(),
            assetRepository.getCategoryDistribution()
        ) { balance, count, distribution ->
            Triple(balance ?: 0L, count, distribution)
        }
        val billFlow = combine(
            billRepository.getMonthlyExpense(currentYearMonth),
            billRepository.getMonthlyIncome(currentYearMonth)
        ) { expense, income ->
            Pair(expense ?: 0L, income ?: 0L)
        }
        // 纪念日数据源 = 生日/纪念日 LifeItem（生活页创建）∪ 旧版 anniversary 表（CSV 导入兼容）
        val gaFlow = lifeItemRepository.getAnniversaryLikeItems()
            .combine(anniversaryRepository.getAllAnniversaries()) { items, legacy ->
                val today = java.time.LocalDate.now()
                val mapped = items.map { item ->
                    val tplIcon = _itemTemplateIcons.value[item.templateId]
                    val date = item.dueDate ?: 0L
                    Anniversary(
                        id = -item.id - 1_000_000L,
                        title = item.title,
                        solarDate = date,
                        type = if (tplIcon == "cake") "BIRTHDAY" else "CUSTOM"
                    )
                }
                legacy + mapped
            }
            .map { all -> GoalAnnivData(all.size, all) }
        val habitFlow = combine(
            goalRepository.getHabitGoals(),
            goalRepository.getTodayCheckedGoalIds(todayStart, tomorrowStart)
        ) { habits, checkedIds ->
            val checked = checkedIds.toSet()
            HabitData(
                total = habits.size,
                checked = habits.count { it.id in checked },
                rows = habits.map {
                    HabitTodayRow(it.id, it.title, it.icon, it.frequency, it.id in checked)
                }
            )
        }
        val budgetFlow = budgetRepository.getBudgetByMonthFlow(currentYearMonth)
        val subFlow = lifeItemRepository.getSubscriptionsDueWithin(7)
        val core = combine(assetFlow, billFlow, gaFlow, budgetFlow, habitFlow) { assetData, billData, gaData, budget, habitData ->
            CoreData(assetData, billData, gaData, budget, habitData)
        }
        combine(core, subFlow) { c, subs -> c to subs }
    }

    private fun loadVaultData() {
        viewModelScope.launch {
            // 仅统计条数，不预载条目明文元数据（title/username）到内存，保护隐私
            vaultRepository.observeCount()
                .catch { e -> AppLogger.e("DashboardVM", "loadVaultData failed", e) }
                .collect { count ->
                    _state.update { it.copy(vaultCount = count) }
                }
        }
    }
}

private data class GoalAnnivData(
    val anniversaryCount: Int,
    val anniversaries: List<Anniversary>
)

private data class HabitData(
    val total: Int,
    val checked: Int,
    val rows: List<HabitTodayRow>
)

private data class CoreData(
    val assetData: Triple<Long, Int, List<CategoryCount>>,
    val billData: Pair<Long, Long>,
    val gaData: GoalAnnivData,
    val budget: Budget?,
    val habitData: HabitData
)
