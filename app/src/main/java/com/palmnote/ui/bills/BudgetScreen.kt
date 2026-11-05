// 周期条吸顶用的是 LazyColumn 的 stickyHeader（仍是 Experimental）。放在文件级而不加到
// `BudgetScreen` 的注解上：detekt 基线的 ID 里含注解文本，改注解会让整条 LongMethod
// 失配、被当成新问题报出来。
@file:OptIn(ExperimentalFoundationApi::class)

package com.palmnote.ui.bills

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.app.R
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Budget
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetProgress
import com.palmnote.domain.model.BudgetScope
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import java.io.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

/**
 * 预算管理：一条预算 = 周期（周/月/年）× 范围（全账本 / 账本 / 分类）。
 *
 * 预算是**长期模板**：设一次一直生效，不需要每月重设；周期切换只影响「看哪一期」和
 * 「给哪一期设预算」，同一个周期内的支出窗口由 [com.palmnote.domain.util.BudgetCalculator] 统一计算。
 *
 * 版式分三层，每层一张卡：
 * 1. **总预算卡**（米色 + 橙色日历图标 + 「修改预算」）——本期额度、已用比例、结余；
 * 2. **分类 / 账本卡**（白色 + 彩色图标方块 + 副标题 + 「添加」+ 右箭头）——按范围分组，
 *    空组时副标题就是空态，整卡即添加入口；有内容时条目列在卡头下面；
 * 3. **小贴士**（绿色，可折叠）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onNavigateBack: () -> Unit = {},
    /** 进来时停在哪个周期页签。首页预警卡会带上被预警那条的周期，默认月。 */
    initialPeriod: BudgetPeriod = BudgetPeriod.MONTH,
    viewModel: BillViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 与记账页同一口径（内置预设 + 自定义）。只取自定义分类会让没建过自定义分类的用户
    // 在「添加分类预算」时看到空列表。
    val categories by viewModel.budgetExpenseCategories.collectAsStateWithLifecycle()

    // 预算进度是真查库（周/月/年三个窗口各一次），首帧拿不到数据。ViewModel 的
    // StateFlow 初值是 null，null 就代表「还没算出来」——据此显示骨架，
    // 否则进页面会先闪一下「还没设置预算」，再突然长出卡片。
    val progressesOrNull by viewModel.budgetProgresses.collectAsStateWithLifecycle()
    val progresses = progressesOrNull.orEmpty()
    val loading = progressesOrNull == null

    var selectedPeriodName by rememberSaveable { mutableStateOf(initialPeriod.name) }
    val selectedPeriod = remember(selectedPeriodName) { BudgetPeriod.from(selectedPeriodName) }

    // 正在编辑的预算（null = 不在编辑）
    var editing by rememberSaveable { mutableStateOf<BudgetDraft?>(null) }
    // 选分类 / 选账本的中间弹窗
    var pickingScope by rememberSaveable { mutableStateOf<BudgetScope?>(null) }

    val periodProgresses = remember(progresses, selectedPeriod) {
        progresses.filter { it.budget.periodEnum == selectedPeriod }
    }
    val totalProgress = periodProgresses.firstOrNull { it.budget.scopeEnum == BudgetScope.TOTAL }
    val categoryProgresses = periodProgresses.filter { it.budget.scopeEnum == BudgetScope.CATEGORY }
    val bookProgresses = periodProgresses.filter { it.budget.scopeEnum == BudgetScope.BOOK }

    val bookNames = remember(state.allAccountBooks) {
        state.allAccountBooks.associate { it.id to it.name }
    }
    val realBooks = remember(state.allAccountBooks) {
        state.allAccountBooks.filter { it.id != AccountBook.ALL_BOOKS_ID && !it.isAllBooks }
    }
    val context = LocalContext.current
    // 账本已删时预算可能残留指向不存在的 id：这里先取好兜底文案，
    // `labelOf` 是普通 lambda，不能在里头调 stringResource。
    val missingBookLabel = stringResource(R.string.budget_label_book_missing)

    // 「添加」在分类 / 账本为空时原本什么都不发生，按钮等于死的。
    // 这里用 Snackbar 明确告诉用户去哪儿补，而不是让点击落空。
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val noCategoryHint = stringResource(R.string.budget_no_category_available)
    val noBookHint = stringResource(R.string.budget_no_book_available)

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.budget_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 28.dp),
            // 与账单页 / 资产页一致：卡片之间 8dp。分组标题自带 14dp 上边距，
            // 组与组之间仍是 22dp，层级不会因为收紧而消失。
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 周期条吸顶：分类/账本一多，切周期要先把列表滚回顶部，很别扭。
            // 吸顶后必须自己铺一层不透明底色，否则下面的卡片会从它后面透出来。
            // 条与下方卡片的间距只由 spacedBy(8dp) 提供——这里再加 bottom padding
            // 会叠成 16dp（2026-11-05 真机反馈）；滚动时卡片从条底滑入，48dp 外框自带缓冲。
            stickyHeader {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    PeriodSelector(
                        selected = selectedPeriod,
                        onSelect = { selectedPeriodName = it.name }
                    )
                }
            }

            if (loading) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                item { BudgetSkeleton(height = 148.dp) }
                item { BudgetSkeleton(height = 76.dp) }
                item { BudgetSkeleton(height = 76.dp) }
            } else {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                item {
                    TotalBudgetCard(
                        progress = totalProgress,
                        period = selectedPeriod,
                        onEdit = {
                            editing = BudgetDraft(
                                period = selectedPeriod,
                                scope = BudgetScope.TOTAL,
                                existing = totalProgress?.budget,
                                spent = totalProgress?.spent ?: 0L
                            )
                        }
                    )
                }

                item {
                    BudgetGroup(
                        title = stringResource(R.string.budget_section_category),
                        icon = Icons.Filled.GridView,
                        tint = InfoBlue,
                        progresses = categoryProgresses,
                        emptyText = stringResource(R.string.budget_empty_category),
                        labelOf = { budgetCategoryDisplayName(context, it.budget.category.orEmpty()) },
                        onAdd = {
                            if (categories.isEmpty()) {
                                uiScope.launch { snackbarHostState.showSnackbar(noCategoryHint) }
                            } else {
                                pickingScope = BudgetScope.CATEGORY
                            }
                        },
                        footerHint = categoryOverHint(categoryProgresses, totalProgress, context),
                        onEditItem = { progress ->
                            editing = BudgetDraft(
                                period = selectedPeriod,
                                scope = BudgetScope.CATEGORY,
                                category = progress.budget.category,
                                existing = progress.budget,
                                spent = progress.spent
                            )
                        }
                    )
                }

                item {
                    BudgetGroup(
                        title = stringResource(R.string.budget_section_book),
                        icon = Icons.Outlined.AccountBalanceWallet,
                        tint = Purple,
                        progresses = bookProgresses,
                        emptyText = stringResource(R.string.budget_empty_book),
                        labelOf = { bookNames[it.budget.bookId] ?: missingBookLabel },
                        onAdd = {
                            if (realBooks.isEmpty()) {
                                uiScope.launch { snackbarHostState.showSnackbar(noBookHint) }
                            } else {
                                pickingScope = BudgetScope.BOOK
                            }
                        },
                        onEditItem = { progress ->
                            editing = BudgetDraft(
                                period = selectedPeriod,
                                scope = BudgetScope.BOOK,
                                bookId = progress.budget.bookId,
                                bookName = bookNames[progress.budget.bookId],
                                existing = progress.budget,
                                spent = progress.spent
                            )
                        }
                    )
                }

                item { BudgetTipsCard() }
            }
        }
    }

    // ── 选分类 ──
    if (pickingScope == BudgetScope.CATEGORY && categories.isNotEmpty()) {
        ChoiceDialog(
            title = stringResource(R.string.budget_pick_category),
            options = categories,
            // 这里是「新建或进入某分类」，不是「从当前值里换一个」，传 null 才不亮一个假选中。
            selected = null,
            optionLabel = { it.name },
            optionIcon = { it.icon },
            optionTint = { it.color },
            onSelect = { item ->
                val existing = categoryProgresses.firstOrNull { it.budget.category == item.name }?.budget
                editing = BudgetDraft(
                    period = selectedPeriod,
                    scope = BudgetScope.CATEGORY,
                    category = item.name,
                    existing = existing
                )
            },
            onDismiss = { pickingScope = null }
        )
    }

    // ── 选账本 ──
    if (pickingScope == BudgetScope.BOOK && realBooks.isNotEmpty()) {
        // 账本紫 + 深色提亮，与账本组卡头同一套；不能用 billTint()——
        // 那是近白奶油，白底弹窗上 12% 圆底和图标会一起隐形（2026-11-05 真机反馈，
        // 与 PeriodSelector 选中态同类：模块 tint 只能当卡底，不能当前景色）
        val bookTint = adaptiveTint(Purple)
        ChoiceDialog(
            title = stringResource(R.string.budget_pick_book),
            options = realBooks,
            selected = null,
            optionLabel = { it.name },
            optionIcon = { Icons.Outlined.MenuBook },
            optionTint = { bookTint },
            onSelect = { book ->
                val existing = bookProgresses.firstOrNull { it.budget.bookId == book.id }?.budget
                editing = BudgetDraft(
                    period = selectedPeriod,
                    scope = BudgetScope.BOOK,
                    bookId = book.id,
                    bookName = book.name,
                    existing = existing
                )
            },
            onDismiss = { pickingScope = null }
        )
    }

    // ── 金额 / 阈值 / 通知 / 删除 ──
    editing?.let { draft ->
        BudgetEditDialog(
            draft = draft,
            onDismiss = { editing = null },
            onSave = { amount, threshold, notify ->
                viewModel.saveBudget(
                    draft.existing?.copy(
                        totalBudget = amount,
                        reminderThreshold = threshold,
                        reminderEnabled = notify
                    ) ?: Budget(
                        period = draft.period.name,
                        scope = draft.scope.name,
                        bookId = draft.bookId,
                        category = draft.category,
                        totalBudget = amount,
                        reminderThreshold = threshold,
                        reminderEnabled = notify
                    )
                )
                editing = null
            },
            onDelete = draft.existing?.let { existing ->
                {
                    viewModel.deleteBudget(existing.id)
                    editing = null
                }
            }
        )
    }
}

/**
 * 编辑中的预算草稿：维度 + 已有行（编辑时非空）+ 本期已用（弹窗里显示「已用多少」）。
 *
 * 独立成 `internal`（而非 private）：弹窗本体在 `BudgetEditDialog.kt`，同包跨文件共用。
 * 实现 [Serializable]：页面状态用它做 `rememberSaveable`，旋转不再丢弹窗。
 */
internal data class BudgetDraft(
    val period: BudgetPeriod,
    val scope: BudgetScope,
    val bookId: Long? = null,
    val category: String? = null,
    /** 账本名在创建草稿时一并带上，编辑弹窗不必再拿 DAO（也不必改函数签名）。 */
    val bookName: String? = null,
    val existing: Budget? = null,
    /**
     * 本期已用金额（分）。
     *
     * 只在编辑已有预算时有意义——弹窗要显示「已用 ¥820.00 · 82%」，让用户调额度时
     * 看得见现在的消耗。`existing` 是 `Budget` 实体本身不含已用，所以另开一字段；
     * 三个调用点手上都有 [BudgetProgress]，取 `.spent` 即可。
     */
    val spent: Long = 0L
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 预算状态色：超支红 / 达阈值橙 / 正常绿。
 *
 * 进度条与组内百分比必须同一个判据，否则会出现「数字变红了但进度条还是绿」。
 */
@Composable
private fun budgetStatusColor(progress: BudgetProgress): Color = when {
    progress.isOver -> ErrorLight
    progress.reachedThreshold -> AccentOrange
    else -> StatusActive
}

/** 预算窗口的日期格式（月.日，两位补零）。中文 / 英文都用数字，不必再拆两套文案。 */
private val BUDGET_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd")

/**
 * 提醒阈值滑杆的落值：**无极滑动、1% 粒度**，并夹在 1%~100%。
 *
 * 下限从 50% 放宽到 1%（2026-11-05 用户要求）：想「一有动静就提醒」的人可以拉到最左。
 * 滑杆本身是连续的，但存进库里的值必须和界面显示的百分比一致——
 * 直接存 0.8137 会出现「显示 81%、实际按 81.37% 判定」的错位。
 */
internal fun quantizeThreshold(raw: Float): Float =
    (round(raw * 100f) / 100f).coerceIn(0.01f, 1f)

/** 提示条文字色：比 AccentOrange 深一档，压在 10% 橙底上才够读（约 5:1）。 */
private val BudgetHintText = Color(0xFFB4581A)

/** 「11.02 – 11.08」：本期窗口的可读范围（含首尾整天）。 */
internal fun budgetRangeText(start: Long, end: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val from = Instant.ofEpochMilli(start).atZone(zone).toLocalDate().format(BUDGET_DAY_FORMAT)
    val to = Instant.ofEpochMilli(end).atZone(zone).toLocalDate().format(BUDGET_DAY_FORMAT)
    return "$from – $to"
}

/**
 * 分类预算额度合计超出总预算的差额；没设总预算或没超出时返回 null。
 *
 * 抽成纯函数是为了能单测——这条判断直接决定卡底那条提示出不出来。
 * 注意比的是**额度**（规划），不是已用金额（执行）。
 */
internal fun categoryOverTotal(categoryLimitTotal: Long, totalLimit: Long): Long? =
    (categoryLimitTotal - totalLimit).takeIf { totalLimit > 0L && it > 0L }

/**
 * 分类组的卡底提示文案：合计额度超过总预算时才有内容。
 *
 * 独立成一个 composable 而不是写在 `BudgetScreen` 里，是为了把那几个分支
 * （取总预算、判空、拼文案）挡在主函数外面，免得页面级函数的圈复杂度顶到阈值。
 */
@Composable
private fun categoryOverHint(
    categoryProgresses: List<BudgetProgress>,
    totalProgress: BudgetProgress?,
    context: Context
): String? {
    val totalLimit = totalProgress?.budget?.totalBudget ?: 0L
    val categoryTotal = categoryProgresses.sumOf { it.budget.totalBudget }
    val over = categoryOverTotal(categoryTotal, totalLimit) ?: return null
    return stringResource(
        R.string.budget_category_over_total,
        CurrencyUtils.formatCurrency(context, categoryTotal.toMoney()),
        CurrencyUtils.formatCurrency(context, over.toMoney())
    )
}

/**
 * 周期切换：三段等宽胶囊。
 *
 * 选中态必须是**实色底 + onPrimary 字**。早前用 `billTint()`（浅橙近白）当底再配
 * `onPrimary`，浅色主题下是白字压白底（≈1.03:1）、深色主题下是黑字压深棕，
 * 选中项几乎不可见——这是本页最严重的一处可读性缺陷。
 */
@Composable
private fun PeriodSelector(selected: BudgetPeriod, onSelect: (BudgetPeriod) -> Unit) {
    val context = LocalContext.current
    // 一个整体容器 + 内部滑块，而不是三个各自独立的胶囊：
    // 外层铺 surfaceVariant 当槽，选中段在里面铺橙色，未选中段透明，
    // 视觉上是「同一组里的切换」而不是「三个并列按钮」。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        BudgetPeriod.entries.forEach { period ->
            val active = period == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (active) AccentOrange else Color.Transparent)
                    // selectable 排在 background 之后：涟漪才画在色块上方、并被胶囊形状裁掉
                    .selectable(selected = active, role = Role.Tab) { onSelect(period) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = budgetPeriodLabel(context, period),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

// ── 总预算卡 ──

@Composable
private fun TotalBudgetCard(
    progress: BudgetProgress?,
    period: BudgetPeriod,
    onEdit: () -> Unit
) {
    ModuleCard(
        tint = billTint(),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (progress == null) {
            TotalBudgetEmpty(period = period, onEdit = onEdit)
        } else {
            TotalBudgetFilled(progress = progress, period = period, onEdit = onEdit)
        }
    }
}

/**
 * 已设置：图标 + 周期标题 + 「修改预算」/ 本期额度大字 + 已用比例 / 进度条 / 结余。
 *
 * 大字是**本期额度**（不是已用）：「已用 ¥0.00」当主数字会让没花钱的那天看起来
 * 像没设预算。已用金额进脚注、已用比例跟在数字右边，两者都不抢主位。
 */
@Composable
private fun TotalBudgetFilled(
    progress: BudgetProgress,
    period: BudgetPeriod,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val statusColor = budgetStatusColor(progress)
    val over = progress.isOver

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 存钱罐猪用项目既有的 AppIcon.Savings 那一支（账本模板「投资」、分类「理财」同款），
            // 保持图标体系统一，不再维护一份自绘 vector。
            IconBadge(icon = Icons.Outlined.Savings, tint = AccentOrange, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.budget_label_total, budgetPeriodLabel(context, period)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                // 日期挂在标题下，利用图标 40dp 的高度余量，卡片高度不变。
                // 「本周 / 本月」到底从哪天算起，跨月、跨年时尤其需要看见。
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = budgetRangeText(progress.windowStart, progress.windowEnd),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        EditPill(onClick = onEdit)
    }

    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = CurrencyUtils.formatCurrency(context, progress.limit.toMoney()),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (over) ErrorLight else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.budget_used_percent, progress.usedPercent),
            style = MaterialTheme.typography.bodyLarge,
            color = if (over) ErrorLight else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    // 只取一次时间：脚注上的「已过 13%」用这个值。条上原来那道时间刻度已按真机反馈移除
    // （细竖线被当成屏幕坏点），所以这里只给文字用。
    val elapsed = progress.elapsedRatio()
    BudgetBar(progress = progress, color = statusColor, height = 8.dp)
    Spacer(modifier = Modifier.height(10.dp))
    TotalBudgetMeta(progress = progress, elapsed = elapsed)
}

/**
 * 预算进度条（本页专用，不走 core 的通用 [com.palmnote.ui.components.ProgressBar]）。
 *
 * 与通用版的差异都是针对预算场景的：
 * 1. **纯色**而不是水平渐变——条宽几百 dp、填充多在 30%~100%，
 *   左端 80% 透明度到右端全色的过渡肉眼看不出来，短条反而显得脏；
 * 2. 动画 500ms（通用版 800ms），切周/月/年时数字和条要一起到位；
 * 3. 轨道用状态色的 16% 淡版：米色卡上 `surfaceVariant` 与卡底几乎同值，
 *   0% 时整条会消失。
 *
 * 曾经在条上画过一道「本期已过多少」的刻度竖线，真机上被当成屏幕坏点（2026-11-05 反馈），
 * 已移除；时间进度只保留脚注里的「已过 13%」文字。
 */
@Composable
private fun BudgetBar(progress: BudgetProgress, color: Color, height: Dp) {
    val animated by animateFloatAsState(
        targetValue = progress.ratio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "budgetProgress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            // 自绘的条没有语义节点，读屏只会念旁边那行「已用 82%」，进度本身读不出来。
            // progressSemantics 契约是 0..1，超支时裸 ratio（如 1.05）会让读屏异常，封顶同动画口径
            .progressSemantics(progress.ratio.coerceIn(0f, 1f))
            .clip(RoundedCornerShape(height / 2))
            .background(color.copy(alpha = 0.16f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(height / 2))
                .background(color)
        )
    }
}

/** 总卡脚注：左边「总额」给上方无标签的大数字定性，中间是本期时间进度，右侧是结余胶囊。 */
@Composable
private fun TotalBudgetMeta(progress: BudgetProgress, elapsed: Float) {
    val context = LocalContext.current
    val over = progress.isOver
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                R.string.budget_total_of,
                CurrencyUtils.formatCurrency(context, progress.limit.toMoney())
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 与条上那道刻度对应：没有这行字，刻度是「不知道是什么的一条缝」
        Text(
            text = stringResource(
                R.string.budget_elapsed_percent,
                (elapsed * 100).toInt()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(8.dp))
        ResultPill(
            text = if (over) {
                stringResource(
                    R.string.budget_over_amount,
                    CurrencyUtils.formatCurrency(context, (-progress.remaining).toMoney())
                )
            } else {
                stringResource(
                    R.string.budget_remaining_amount,
                    CurrencyUtils.formatCurrency(context, progress.remaining.toMoney())
                )
            },
            color = if (over) ErrorLight else StatusActive,
            icon = if (over) Icons.Filled.PriorityHigh else Icons.Filled.Check
        )
    }
}

/**
 * 结余 / 超支胶囊：浅色底 + 同色勾选（或感叹号）+ 金额。
 *
 * 用胶囊而不是纯文字，是因为这一颗是整张总卡唯一的「结论」——「还剩多少」。
 * 纯文字会和左边的「总额」同权重，胶囊把它拎出来，扫一眼就知道结果。
 */
@Composable
private fun ResultPill(text: String, color: Color, icon: ImageVector) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
    }
}

/** 未设置：同一套图标 + 标题，正文换成引导，底部给主按钮。 */
@Composable
private fun TotalBudgetEmpty(period: BudgetPeriod, onEdit: () -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = Icons.Outlined.Savings, tint = AccentOrange, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.budget_label_total, budgetPeriodLabel(context, period)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.budget_not_set_period, budgetPeriodLabel(context, period)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
    Button(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.budget_set), fontWeight = FontWeight.Bold)
    }
}

/**
 * 深色主题下把原色往白里提一档再画。
 *
 * 紫 `#9C27B0` 压在 `#1E1E1E` 上只有 2.64:1，低于图标 3:1 的可读线；
 * 提亮后是 4.69:1。浅色主题保持原色不动，与分类色板同一口径。
 */
@Composable
private fun adaptiveTint(base: Color): Color =
    if (LocalIsDarkTheme.current) lerp(base, Color.White, 0.3f) else base

/** 图标方块：40dp 圆角底 + 居中图标，三张卡的头部共用。 */
@Composable
private fun IconBadge(icon: ImageVector, tint: Color, size: Dp) {
    val iconTint = adaptiveTint(tint)
    Box(
        modifier = Modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(iconTint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * 「修改预算」：无底色的铅笔 + 文字，是总卡上唯一的可点区域。
 *
 * 不做成实心胶囊——它只是这张卡的次要操作，纯文字才不跟下方主数字抢重量。
 * 颜色仍是预算族色橙（`AccentOrange`），与周期胶囊、设置按钮同一支。
 */
@Composable
private fun EditPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            // 视觉高度由文字撑开（约 20dp），触控补到 40dp，否则这颗小链接很难点中
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Edit,
            contentDescription = null,
            tint = AccentOrange,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.budget_modify),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = AccentOrange
        )
    }
}

// ── 分类 / 账本分组卡 ──

/**
 * 一组预算：彩色图标 + 组名 + 副标题 + 「添加」+ 右箭头，整卡头就是添加入口。
 *
 * 空组时副标题直接写「还没有分类预算」，不再额外画一条空态行——卡片本身就是入口，
 * 空态和满态共用同一块版位，页面不会因为没数据而长出一排假控件。
 */
@Composable
private fun BudgetGroup(
    title: String,
    icon: ImageVector,
    tint: Color,
    progresses: List<BudgetProgress>,
    emptyText: String,
    labelOf: (BudgetProgress) -> String,
    onAdd: () -> Unit,
    onEditItem: (BudgetProgress) -> Unit,
    /** 卡底提示（目前只有「分类合计超总预算」用），null 时整条不渲染、不占位。 */
    footerHint: String? = null
) {
    ModuleCard(
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        BudgetGroupHeader(
            title = title,
            icon = icon,
            tint = tint,
            subtitle = if (progresses.isEmpty()) {
                emptyText
            } else {
                // 走 plurals：英文 1 条要说 "1 item"，不能拼成 "1 items"。
                pluralStringResource(R.plurals.budget_group_items, progresses.size, progresses.size)
            },
            onAdd = onAdd
        )
        progresses.forEach { progress ->
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                // outlineVariant(#F2EFE9) 在白卡上只有 1.06:1，条目之间等于没有分界；
                // 用 outline 压到 50% 才看得出「这是一条一条」。
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            BudgetRow(
                title = labelOf(progress),
                progress = progress,
                onClick = { onEditItem(progress) }
            )
        }
        if (footerHint != null) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            BudgetHintRow(footerHint)
        }
    }
}

/** 卡底提示条：浅橙底 + 警示图标，只用于「分类合计超总预算」这类规划冲突。 */
@Composable
private fun BudgetHintRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentOrange.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = BudgetHintText,
            modifier = Modifier.padding(top = 1.dp).size(15.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = BudgetHintText
        )
    }
}

@Composable
private fun BudgetGroupHeader(
    title: String,
    icon: ImageVector,
    tint: Color,
    subtitle: String,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon = icon, tint = tint, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // 胶囊用本组图标的同一种色：分类蓝、账本紫，一眼能对上是哪一组。
        // 它自己处理点击：否则点这颗胶囊时，涟漪会扩展到整行卡头。
        AddPill(text = stringResource(R.string.budget_add), tint = tint, onClick = onAdd)
    }
}

/**
 * 「+ 添加」胶囊。
 *
 * 胶囊**自己**挂 `clickable`：整行卡头也是可点的，如果只有外层挂点击，
 * 点这颗胶囊时涟漪会铺满整个卡头（看起来像"点按钮亮整张卡"）。
 * 内层可点元素会消费事件，外层的卡头点击不会被触发，涟漪也就收在胶囊里。
 */
@Composable
private fun AddPill(text: String, tint: Color, onClick: () -> Unit) {
    val pillTint = adaptiveTint(tint)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(pillTint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = pillTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = pillTint,
            maxLines = 1
        )
    }
}

/** 一条预算：名称 + 百分比 / 进度条 / 已用与结余。 */
@Composable
private fun BudgetRow(
    title: String,
    progress: BudgetProgress,
    onClick: () -> Unit
) {
    val statusColor = budgetStatusColor(progress)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${progress.usedPercent}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        BudgetBar(progress = progress, color = statusColor, height = 6.dp)
        Spacer(modifier = Modifier.height(6.dp))
        BudgetRowMeta(progress = progress, statusColor = statusColor)
    }
}

/** 组内一条的脚注：左边「已用 x / y」，右边结余或超支额。 */
@Composable
private fun BudgetRowMeta(progress: BudgetProgress, statusColor: Color) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                R.string.budget_used_of,
                CurrencyUtils.formatCurrency(context, progress.spent.toMoney()),
                CurrencyUtils.formatCurrency(context, progress.limit.toMoney())
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (progress.isOver) {
                stringResource(
                    R.string.budget_over_amount,
                    CurrencyUtils.formatCurrency(context, (-progress.remaining).toMoney())
                )
            } else {
                stringResource(
                    R.string.budget_remaining_amount,
                    CurrencyUtils.formatCurrency(context, progress.remaining.toMoney())
                )
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            maxLines = 1
        )
    }
}

/** 首帧骨架：高度对齐真实卡片（总卡 148dp / 组卡 76dp），数据到达后不跳版。 */
@Composable
private fun BudgetSkeleton(height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

/**
 * 使用建议：默认展开三条，点标题行可收起。
 *
 * 这一版按设计稿给展开态：页面底部本来就没有别的内容，收起反而让首屏显得没写完；
 * 用户看过一次后仍然可以点掉。
 */
@Composable
private fun BudgetTipsCard() {
    var expanded by rememberSaveable { mutableStateOf(true) }
    ModuleCard(
        tint = goalTint(),
        modifier = Modifier.fillMaxWidth(),
        // 内容内边距交给下面那行自己，卡片不再垫 16dp：涟漪要能从卡边开始画，
        // 垫过内边距的话可点区域会内缩一圈，看起来是浮在卡里的小方块而不是「整卡可点」。
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 与卡片同为 16dp 圆角，涟漪四角才和卡片边界重合
                .clip(MaterialTheme.shapes.large)
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.budget_tips),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = stringResource(R.string.budget_tips_content),
                // 文案里用 \n 分三条，bodySmall 默认行高 16sp 会让三条挤成一坨；
                // 抬到 20sp，条目之间才有呼吸。
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // 左右下内边距从卡片的 contentPadding 搬到标题行之后（那边现在是 0）
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        }
    }
}
