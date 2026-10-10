package com.palmnote.ui.dashboard
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.dao.CategoryCount
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.Anniversary
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.Goal
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.domain.model.SubscriptionDueItem
import com.palmnote.domain.repository.*
import com.palmnote.domain.util.DateUtils
import com.palmnote.domain.util.HabitCheckIn
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.getKind
import com.palmnote.feature.vault.VaultRepository
import com.palmnote.ui.life.identityColor
import com.palmnote.ui.theme.AppIcon
import com.palmnote.ui.widget.WidgetUpdateHelper
import java.time.LocalDate
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
    val templateId: Long,
    val title: String,
    /** 模板 iconKey（与生活页同一个映射表 [com.palmnote.ui.life.iconFor]）。 */
    val iconKey: String,
    /** 模板色，用作图标底与图标着色。 */
    val tint: androidx.compose.ui.graphics.Color,
    val isCheckedToday: Boolean
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("LongParameterList")
class DashboardViewModel @Inject constructor(
    private val assetRepository: AssetRepository,
    private val billRepository: BillRepository,
    private val budgetRepository: BudgetRepository,
    private val anniversaryRepository: AnniversaryRepository,
    private val preferencesManager: PreferencesManager,
    private val vaultRepository: VaultRepository,
    private val walletRepository: WalletRepository,
    private val lifeItemRepository: LifeItemRepository,
    private val cachedCategoryConfigs: @JvmSuppressWildcards StateFlow<List<CategoryConfig>>,
    private val templateRepository: LifeTemplateRepository,
    // 打卡卡与生活页/桌面组件同源：直接走生活那两张表的 DAO（生活详情页也是这么取的）
    private val lifeTemplateDao: LifeTemplateDao,
    private val lifeItemDao: LifeItemDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _itemTemplateIcons = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _cardConfigs = MutableStateFlow(DashboardCardConfig.defaults)
    val cardConfigs: StateFlow<List<DashboardCardConfig>> = _cardConfigs.asStateFlow()

    val visibleConfigs: StateFlow<List<DashboardCardConfig>> = _cardConfigs
        .map { it.filter { c -> c.visible } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardCardConfig.defaults.filter { it.visible })

    /** 演示模式开启中：净资产等「含示例」的数字需要标注，否则用户会把示例余额当成自己的。 */
    val demoModeOn: StateFlow<Boolean> = preferencesManager.lifeDemoMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    fun checkInHabit(templateId: Long) {
        viewModelScope.launch {
            try {
                val template = lifeTemplateDao.getTemplateById(templateId) ?: return@launch
                val includeDemo = preferencesManager.lifeDemoMode.first()
                HabitCheckIn.toggle(
                    dao = lifeItemDao,
                    templateId = templateId,
                    title = template.getDisplayName(context),
                    includeDemo = includeDemo,
                    demoMeta = LIFE_DEMO_META
                )
                // 桌面组件读的是同一份数据，打卡后立刻刷新，不用等下一轮轮询
                WidgetUpdateHelper.refreshHabitWidgets()
            } catch (e: Exception) {
                AppLogger.e("DashboardVM", "checkInHabit failed", e)
            }
        }
    }

    /** 今天该模板是否已打卡：口径与组件、详情页一致（今天有非 ARCHIVED 的行）。 */
    private suspend fun habitCheckedToday(templateId: Long, includeDemo: Boolean): Boolean =
        lifeItemDao.getDistinctCheckInDays(templateId, includeDemo, LIFE_DEMO_META)
            .first()
            .contains(LocalDate.now().toString())

    /**
     * 打卡卡的数据：与生活页、桌面组件同源 —— kind = HABIT 的生活模板 + 今天该模板的打卡行。
     * 每行状态要按模板取一次「打卡天集合」（与组件同法），模板数量在十位以内。
     */
    private fun buildHabitFlow(): Flow<HabitData> = combine(
        lifeTemplateDao.getAllVisibleTemplates(),
        preferencesManager.lifeDemoMode
    ) { templates, includeDemo ->
        val habits = templates.filter { it.getKind() == LifeTemplateKind.HABIT }
        val checked = habits.filter { habitCheckedToday(it.id, includeDemo) }.map { it.id }.toSet()
        HabitData(
            total = habits.size,
            checked = checked.size,
            rows = habits.map { template ->
                HabitTodayRow(
                    templateId = template.id,
                    title = template.getDisplayName(context),
                    iconKey = template.icon,
                    tint = identityColor(template.color),
                    isCheckedToday = template.id in checked
                )
            }
        )
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
        // 演示感知（**互斥**）：演示开启 = **只看示例**（用户自己的条目与旧版导入的纪念日都不参与）；
        // 关闭 = 只看用户自己的（示例行已在关闭时物理删除）。
        val gaFlow = preferencesManager.lifeDemoMode
            .flatMapLatest { includeDemo ->
                lifeItemRepository.getAnniversaryLikeItems(includeDemo, LIFE_DEMO_META)
                    .combine(anniversaryRepository.getAllAnniversaries()) { items, legacy ->
                        val mapped = items.mapNotNull { item ->
                            val tplIcon = _itemTemplateIcons.value[item.templateId]
                            // dueDate（执行列）可能为空：它是 v8 才补上的查询索引，演示种子与存量行未必回填。
                            // 此时回落到 fieldsData 这个「展示唯一信源」再取一次；仍然取不到就**整条不发出去**。
                            // 绝不能写成 `item.dueDate ?: 0L` —— 0 会被当成 1970-01-01，
                            // 于是卡片上出现「01月01日 / 已过 20716 天」（总纲 §13 A 档同一缺陷类）。
                            val date = item.dueDate ?: DateUtils.dateFromFieldsDataOrNull(item.fieldsData)
                                ?: return@mapNotNull null
                            Anniversary(
                                id = -item.id - 1_000_000L,
                                title = item.title,
                                solarDate = date,
                                // 生日 / 纪念日都是**按年复现**的日子，用户要看的是「还有几天」。
                                // 用 COUNT_UP 会拿一个未来日期去算天数差，得负数 —— 生活页样板卡也是「还有 N 天」。
                                displayMode = "COUNT_DOWN",
                                type = if (tplIcon == "cake") "BIRTHDAY" else "CUSTOM"
                            )
                        }
                        if (includeDemo) mapped else legacy + mapped
                    }
            }
            .map { all -> GoalAnnivData(all.size, all) }
        val habitFlow = buildHabitFlow()
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
