package com.palmnote.ui.bills

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * 账单页日历网格（不含标题栏）。
 *
 * 头部右上角的「周 | 月」＝ `collapsed`：**两套 Pager 各自分页** ——
 * 月视图按「月」翻、周视图按「周」翻，左右滑动的手感（跟手 + 惯性）一致。
 * （早期折叠视图直接复用了按月分页的 Pager，于是周视图里左右滑动会一次跳一整月，
 * 用户 2026-10-10 反馈「周视图下左右滑动应该是切换上下周才对」——见 [WeekRow]。）
 *
 * 交互（与生活页 [com.palmnote.ui.life.LifeMonthCalendar] 对齐）：
 * - **翻月 / 翻周只靠左右滑动**（HorizontalPager），原 `< >` 导航按钮已移除；
 *   年月文案不再画在这里，改由卡片头部（`BillScreen`）与「日历」标题同一行显示；
 * - 点击月视图里上下月的灰号 → 滑到相邻月；周视图里点跨月的灰号 → 切到那一天所属月的
 *   上下文（标题 + 数据），本行不动（与月视图「点灰号换月」同款）；
 * - 月视图网格行数按月动态（4~6 行，见 [billCalMonthRows]），Pager 滑动时相邻两页同时可见，
 *   容器高度只能取其一，故高度锚定「**落定页**」并用动画平滑伸缩（不能直接用 currentPage——
 *   它拖动过半就翻转）；滑动进行中高度取相邻三页的最大行数，避免滑入的 6 行月被裁掉末行；
 *   周视图固定一行高，周/月切换由同一处动画平滑伸缩；
 * - **圆点只在「数据所属月」画**：`dailyData` 是按「日」索引的单月数据，滑动到相邻月/周时外部
 *   还没换数，此时先不画点，免得把本月的点错画到新月上（换月是滑动落定后才通知外部取数）；
 * - 周视图翻周时**只改「正在浏览的周」，不改选中日**（与生活页同口径）：只有翻到该月之外的周
 *   才上报换月，外部 `setMonth` 据此更新标题与数据。
 *
 * `todayJumpSignal` 是「回今天」的显式令牌（外部点卡片左上的「日历」时写入一个新时间戳，
 * `0` = 未触发）：周视图下已经在当月浏览别的周时，外部 `setMonth` 写进去的是同一个值、
 * StateFlow 不会重发，只靠 `yearMonth` 变化等不到这一跳。
 */
@Composable
fun CalendarView(
    yearMonth: String, // "2024-01"
    dailyData: Map<Int, Pair<Long, Long>>, // day -> (expense, income)
    selectedDay: Int?,
    onDaySelected: (Int) -> Unit,
    collapsed: Boolean = false,
    onMonthChanged: ((String) -> Unit)? = null,
    todayJumpSignal: Long = 0L,
    modifier: Modifier = Modifier
) {
    // 基准月只在进入时取一次，之后翻月全部由 Pager 驱动（外部 yearMonth 变化只用于「回跳」）。
    val baseMonth = remember {
        runCatching { YearMonth.parse(yearMonth, BILL_CAL_YM_FMT) }.getOrDefault(YearMonth.now())
    }
    // 外部数据所属月：用来判断某个页面该不该画圆点。
    val dataMonth = remember(yearMonth) {
        runCatching { YearMonth.parse(yearMonth, BILL_CAL_YM_FMT) }.getOrNull()
    }
    val pagerState = rememberPagerState(initialPage = BILL_CAL_CENTER_PAGE) { BILL_CAL_PAGE_COUNT }
    // 周视图（`collapsed`）另起一套「按周分页」的 Pager：基准周 + 居中页，page ↔ 周一一映射，
    // 与月 Pager 同构。基准周 = 选中日（无选中则当月 1 日）所在的那一周，周首与月历一致取周日。
    val baseWeek = remember {
        billCalWeekStart(baseMonth.atDay((selectedDay ?: 1).coerceIn(1, baseMonth.lengthOfMonth())))
    }
    val weekPagerState = rememberPagerState(initialPage = BILL_CAL_CENTER_PAGE) { BILL_CAL_PAGE_COUNT }
    val scope = rememberCoroutineScope()
    var settledPage by remember { mutableIntStateOf(pagerState.currentPage) }
    val latestYearMonth = rememberUpdatedState(yearMonth)

    /** 把周 Pager 滑到某一周：相邻走动画，跨多直接跳。 */
    fun scrollWeekTo(weekStart: LocalDate) {
        val page = (BILL_CAL_CENTER_PAGE + ChronoUnit.WEEKS.between(baseWeek, weekStart)).toInt()
            .coerceIn(0, BILL_CAL_PAGE_COUNT - 1)
        scope.launch {
            when {
                page == weekPagerState.currentPage -> Unit
                abs(page - weekPagerState.currentPage) <= 1 -> weekPagerState.animateScrollToPage(page)
                else -> weekPagerState.scrollToPage(page)
            }
        }
    }

    // 月视图滑动落定 → 通知外部换月（外部的 dailyData 会跟着换）。
    // 首次发射（进入组合时的 isScrollInProgress=false）只是初始状态、不是「用户滑完」，跳过不报。
    LaunchedEffect(pagerState, collapsed) {
        if (collapsed) return@LaunchedEffect
        var primed = false
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                settledPage = pagerState.currentPage
                if (primed) {
                    val ym = baseMonth.plusMonths((settledPage - BILL_CAL_CENTER_PAGE).toLong())
                    val str = ym.format(BILL_CAL_YM_FMT)
                    if (str != latestYearMonth.value) onMonthChanged?.invoke(str)
                } else {
                    primed = true
                }
            }
        }
    }

    // 周视图滑动落定 → 上报落定周的「锚定月」（标题与数据都跟着走）。
    // 注：`billCalWeekAnchorMonth` 对同一周是纯函数，所以这次上报引起的 yearMonth 回声会在下面
    // 那个「外部换月 → 对齐周」的 effect 里被判为「已在看该月的周」而跳过，不会来回追打。
    LaunchedEffect(weekPagerState, collapsed) {
        if (!collapsed) return@LaunchedEffect
        var primed = false
        snapshotFlow { weekPagerState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                if (primed) {
                    val week = baseWeek.plusWeeks(
                        (weekPagerState.currentPage - BILL_CAL_CENTER_PAGE).toLong()
                    )
                    val str = billCalWeekAnchorMonth(week, dataMonth).format(BILL_CAL_YM_FMT)
                    if (str != latestYearMonth.value) onMonthChanged?.invoke(str)
                } else {
                    primed = true
                }
            }
        }
    }

    // 月视图：外部指定月份（点「回今天」等）→ 把那一页滑进视野：相邻走动画，跨多直接跳。
    // 周视图下月 Pager 不参与显示，也不能去动它，否则切回月视图时它会把月份「拖回去」。
    LaunchedEffect(yearMonth, collapsed) {
        if (collapsed) return@LaunchedEffect
        val target = runCatching { YearMonth.parse(yearMonth, BILL_CAL_YM_FMT) }.getOrNull()
            ?: return@LaunchedEffect
        val page = (BILL_CAL_CENTER_PAGE + ChronoUnit.MONTHS.between(baseMonth, target)).toInt()
            .coerceIn(0, BILL_CAL_PAGE_COUNT - 1)
        when {
            page == pagerState.currentPage -> Unit
            abs(page - pagerState.currentPage) <= 1 -> pagerState.animateScrollToPage(page)
            else -> pagerState.scrollToPage(page)
        }
    }

    // 周视图：外部换月（回今天 / 点灰号换月 / 从月视图切进来）→ 把「该月的锚定周」带进视野。
    // 若当前这一周本就在该月范围内（`billCalWeekAnchorMonth(shown, target) == target`）就不动它：
    // 既吞掉上面那次上报的回声，也保证「点灰号换月」后视图停在原地、只看标题与数据切换。
    LaunchedEffect(yearMonth, collapsed) {
        if (!collapsed) return@LaunchedEffect
        val target = runCatching { YearMonth.parse(yearMonth, BILL_CAL_YM_FMT) }.getOrNull()
            ?: return@LaunchedEffect
        val shown = baseWeek.plusWeeks((weekPagerState.currentPage - BILL_CAL_CENTER_PAGE).toLong())
        if (billCalWeekAnchorMonth(shown, target) == target) return@LaunchedEffect
        scrollWeekTo(billCalWeekStart(target.atDay((selectedDay ?: 1).coerceIn(1, target.lengthOfMonth()))))
    }

    // 「回今天」：已经在当月浏览别的周时，`setMonth` 写进去的是同一个值，StateFlow 不会重发，
    // 只靠 yearMonth 变化永远等不到这一跳 —— 所以外部另给一个令牌。
    LaunchedEffect(todayJumpSignal) {
        if (todayJumpSignal <= 0L || !collapsed) return@LaunchedEffect
        scrollWeekTo(billCalWeekStart(LocalDate.now()))
    }

    val dayNames = remember {
        listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        ).map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
    val today = LocalDate.now()

    Column(modifier = modifier) {
        // 星期标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dayNames.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 日历网格 —— 横向分页：周视图一周一页、月视图一月一页，左右滑动各自切换
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 格子边长由可用宽度推出（7 列，格子自带 2dp 内边距即行间距）→ 行高即格宽。
            val cell = maxWidth / BILL_CAL_COLUMNS
            fun rowsOfPage(page: Int): Int = if (collapsed) {
                1
            } else {
                billCalMonthRows(baseMonth.plusMonths((page - BILL_CAL_CENTER_PAGE).toLong()))
            }
            val rows = if (pagerState.isScrollInProgress) {
                maxOf(rowsOfPage(settledPage - 1), rowsOfPage(settledPage), rowsOfPage(settledPage + 1))
            } else {
                rowsOfPage(settledPage)
            }
            // +1dp 裁剪余量：格边长是小数 dp，容器与格子各自取整可能差 1px，
            // 不留余量时选中/今天的圆底会被 clipToBounds 裁掉一截。
            val gridHeight by animateDpAsState(
                targetValue = cell * rows + BILL_CAL_CLIP_SLACK,
                label = "billMonthGridHeight"
            )
            Box(modifier = Modifier.fillMaxWidth().height(gridHeight).clipToBounds()) {
                if (collapsed) {
                    // 周视图：一周一页，左右滑动切换上/下周（不再复用按月分页的 Pager）。
                    HorizontalPager(state = weekPagerState, modifier = Modifier.fillMaxSize()) { page ->
                        WeekRow(
                            weekStart = baseWeek.plusWeeks((page - BILL_CAL_CENTER_PAGE).toLong()),
                            dataMonth = dataMonth,
                            dailyData = dailyData,
                            selectedDay = selectedDay,
                            today = today,
                            onDaySelected = onDaySelected,
                            onJumpMonth = { ym -> onMonthChanged?.invoke(ym) }
                        )
                    }
                } else {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        val pageMonth = baseMonth.plusMonths((page - BILL_CAL_CENTER_PAGE).toLong())
                        MonthPage(
                            displayMonth = pageMonth,
                            dailyData = if (dataMonth != null && pageMonth == dataMonth) dailyData else emptyMap(),
                            selectedDay = selectedDay,
                            today = today,
                            onDaySelected = onDaySelected,
                            onJumpMonth = { delta ->
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        (pagerState.currentPage + delta).coerceIn(0, BILL_CAL_PAGE_COUNT - 1)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // 汇总（选日期显示当天，否则显示当月）
        val summaryMonth = dataMonth ?: baseMonth
        val showDay = selectedDay != null && selectedDay in 1..summaryMonth.lengthOfMonth()
        val dayData = if (showDay) {
            dailyData[selectedDay]
        } else {
            Pair(dailyData.values.sumOf { it.first }, dailyData.values.sumOf { it.second })
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.bill_expense),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyUtils.formatCurrency(
                            androidx.compose.ui.platform.LocalContext.current,
                            (dayData?.first ?: 0L).toMoney()
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if ((dayData?.first ?: 0L) > 0) ExpenseRed
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.bill_income),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyUtils.formatCurrency(
                            androidx.compose.ui.platform.LocalContext.current,
                            (dayData?.second ?: 0L).toMoney()
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if ((dayData?.second ?: 0L) > 0) StatusActive
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.bill_balance),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val balance = (dayData?.second ?: 0L) - (dayData?.first ?: 0L)
                    Text(
                        text = CurrencyUtils.formatCurrency(
                            androidx.compose.ui.platform.LocalContext.current,
                            balance.toMoney()
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (balance >= 0) StatusActive else ErrorLight
                    )
                }
            }
        }
    }
}

/**
 * 日历一格的数据。
 * [jumpMonth] 区分格子的点击语义：`0` = 本月日（点选该日）；`-1` / `+1` = 相邻月的灰号
 * （点后翻到上 / 下一月，与旧版「点灰号换月」一致，不改选中日）。
 */
private data class BillCalCell(
    val day: Int,
    val inMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasExpense: Boolean,
    val hasIncome: Boolean,
    val jumpMonth: Int
)

/** 单月的一页网格：行数按月动态（4~6）。折叠成周视图时不再复用本页（改用 [WeekRow] 按周分页）。 */
@Composable
private fun MonthPage(
    displayMonth: YearMonth,
    dailyData: Map<Int, Pair<Long, Long>>,
    selectedDay: Int?,
    today: LocalDate,
    onDaySelected: (Int) -> Unit,
    onJumpMonth: (Int) -> Unit
) {
    val daysInMonth = displayMonth.lengthOfMonth()
    val firstDayOfWeek = displayMonth.atDay(1).dayOfWeek.value % 7 // 0 = 周日
    val rows = billCalMonthRows(displayMonth)
    val prevMonth = displayMonth.minusMonths(1)
    val nextMonth = displayMonth.plusMonths(1)

    Column {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0 until BILL_CAL_COLUMNS) {
                    val index = row * 7 + col
                    val day = index - firstDayOfWeek + 1
                    val cell = when {
                        day in 1..daysInMonth -> {
                            val d = dailyData[day]
                            BillCalCell(
                                day, true, today == displayMonth.atDay(day), day == selectedDay,
                                d != null && d.first > 0, d != null && d.second > 0, 0
                            )
                        }
                        index < firstDayOfWeek -> {
                            val pd = prevMonth.lengthOfMonth() - firstDayOfWeek + index + 1
                            BillCalCell(pd, false, today == prevMonth.atDay(pd), false, false, false, -1)
                        }
                        else -> {
                            val nd = day - daysInMonth
                            BillCalCell(nd, false, today == nextMonth.atDay(nd), false, false, false, 1)
                        }
                    }
                    DayCell(
                        day = cell.day,
                        isCurrentMonth = cell.inMonth,
                        isToday = cell.isToday,
                        isSelected = cell.isSelected,
                        hasExpense = cell.hasExpense,
                        hasIncome = cell.hasIncome,
                        onClick = { if (cell.jumpMonth == 0) onDaySelected(cell.day) else onJumpMonth(cell.jumpMonth) }
                    )
                }
            }
        }
    }
}

/**
 * 周视图的一行 7 格（周日打头，与本页表头同序，也与 [MonthPage] 的行分组一致）。
 *
 * 跨月的格子按「非本月」灰字处理（与月视图同规则、同样不画圆点）。点它的语义与月视图
 * 「点灰号换月」同款：切到那一天所属月的上下文（标题 + 数据），行的位置由外部的换月联动决定，
 * 所以不会凭空跳走；先报换月再报选中，顺序不能反（`setMonth` 会把选中日重置）。
 */
@Composable
private fun WeekRow(
    weekStart: LocalDate,
    dataMonth: YearMonth?,
    dailyData: Map<Int, Pair<Long, Long>>,
    selectedDay: Int?,
    today: LocalDate,
    onDaySelected: (Int) -> Unit,
    onJumpMonth: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (col in 0 until BILL_CAL_COLUMNS) {
            val date = weekStart.plusDays(col.toLong())
            val inMonth = dataMonth != null && YearMonth.from(date) == dataMonth
            val data = if (inMonth) dailyData[date.dayOfMonth] else null
            DayCell(
                day = date.dayOfMonth,
                isCurrentMonth = inMonth,
                isToday = date == today,
                isSelected = inMonth && date.dayOfMonth == selectedDay,
                hasExpense = data != null && data.first > 0,
                hasIncome = data != null && data.second > 0,
                onClick = {
                    if (inMonth) {
                        onDaySelected(date.dayOfMonth)
                    } else {
                        onJumpMonth(date.format(BILL_CAL_YM_FMT))
                        onDaySelected(date.dayOfMonth)
                    }
                }
            )
        }
    }
}

@Composable
private fun RowScope.DayCell(
    day: Int,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    hasExpense: Boolean,
    hasIncome: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    // 选中底 = AccentOrange 实色（2026-10-11 用户定稿：浅橙两轮校准仍被否，
                    // 「恢复到初始差不多的状态」）。不用 alpha：深色主题下叠暗卡底会变浑浊深棕。
                    isSelected -> AccentOrange
                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // 内容整体下移半个点槽（3dp）：「数字 + 6dp 点槽」按 Center 居中时，多出的槽位会把
        // 数字中心顶到格子中心上方 3dp —— 下移同样的量正好把数字送回格子正中，选中 / 今天的
        // 圆底（对准格子中心）就与数字**恒同心**，不用按「有无圆点」给圆分叉（用户 2026-10-10
        // 反馈「选中圆要视觉同心」；曾试过「无圆点时圆上移＋收小」的方案，圆会在两种状态间
        // 跳 3dp 且大小不一，不如这个统一）。数字与圆点跟着一起下移 3dp，所有日子一致，
        // 选中时圆点离圆边仍有约 6dp 余量。
        Column(
            modifier = Modifier.offset(y = BILL_CAL_DOT_SLOT / 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$day",
                // 与生活页月历日格同源（`LifeMonthCalendar` 的 MonthDayCell）：同一令牌、同样只给
                // fontSize、不挂 style。此前误用 `typography.bodySmall`（12sp），而本页格子更宽
                // （7 列无列间距 + 卡片内边距 12dp vs 生活页 20dp），「字号 ÷ 边长」只有生活页的
                // 82%，看着数字偏小（用户反馈）。统到 TypeScale.calDay 后两页一致。
                fontSize = TypeScale.calDay,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    isToday -> MaterialTheme.colorScheme.primary
                    isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )

            // 指示点槽位：**恒定占位** —— 当天没有记录时也留着这一行的高度。
            // 此前整行是 `if (有记录)` 才画，于是有记录的格子内容高出 6dp，被 Column 的
            // Center 重新居中后数字整体上移约 3dp（＝槽位高的一半），同一行里
            // 「有记录的数字」比「没记录的数字」高出一截（用户 2026-10-10 反馈）。
            // 占位后所有格子的内容高度相同，数字的纵向位置就完全一致了。
            //
            // 圆点**恒用红 / 绿语义色**，任何状态都不改色不描边 ——
            // 选中圆底定稿 = AccentOrange 实色（2026-10-11 用户拍板「恢复到初始差不多
            // 的状态」；期间试过浅橙底两轮校准、白描边、深色圆点变体，均被否）。
            // 红/绿对 AccentOrange 只有 1.66 / 1.30 : 1，用户接受初始观感。
            Row(
                modifier = Modifier
                    .height(BILL_CAL_DOT_SLOT)
                    .padding(top = BILL_CAL_DOT_GAP),
                horizontalArrangement = Arrangement.spacedBy(BILL_CAL_DOT_GAP)
            ) {
                if (hasExpense) {
                    Box(
                        modifier = Modifier
                            .size(BILL_CAL_DOT_SIZE)
                            .clip(CircleShape)
                            .background(ExpenseRed)
                    )
                }
                if (hasIncome) {
                    Box(
                        modifier = Modifier
                            .size(BILL_CAL_DOT_SIZE)
                            .clip(CircleShape)
                            .background(StatusActive)
                    )
                }
            }
        }
    }
}

/**
 * 某月需要几行格子（周日为每周首日）：`(前导空位数 + 当月天数)` 向上取整到整周。
 * 取值范围 4~6（如 2 月 28 天且周日开头 = 4 行；31 天且偏移 5/6 = 6 行）。
 */
private fun billCalMonthRows(month: YearMonth): Int {
    val firstDayOfWeek = month.atDay(1).dayOfWeek.value % 7
    return (firstDayOfWeek + month.lengthOfMonth() + 6) / 7
}

/** 某日所在周的周首（**周日**，与本页表头、[MonthPage] 的前导偏移同一口径）。 */
private fun billCalWeekStart(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value % 7).toLong())

/**
 * 周视图某一页该挂在哪个「月份上下文」上：这一周只要还落在 [month] 里就留在该月，
 * 否则按周首日所在月。对同一周是纯函数 —— 这正是「周 Pager 上报换月」与「外部换月对齐周」
 * 两条联动能互相收敛、不打转的关键（见 `CalendarView` 里两个 effect 的注释）。
 */
private fun billCalWeekAnchorMonth(weekStart: LocalDate, month: YearMonth?): YearMonth {
    val weekEnd = weekStart.plusDays(6)
    if (month != null && weekStart <= month.atEndOfMonth() && weekEnd >= month.atDay(1)) return month
    return YearMonth.from(weekStart)
}

/** 与 `DateUtils` 的 `yyyy-MM` 同构，用于与外部 `yearMonth` 字符串互转。 */
private val BILL_CAL_YM_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

/** 网格列数（周日至周六）。 */
private const val BILL_CAL_COLUMNS = 7

/** 滑动切月的总页数与居中页（约 100 年；居中页对应进入时所在月份）。 */
private const val BILL_CAL_PAGE_COUNT = 1200
private const val BILL_CAL_CENTER_PAGE = BILL_CAL_PAGE_COUNT / 2

/** 网格容器高度的裁剪余量（小数 dp 取整，1dp 视觉不可感知）。 */
private val BILL_CAL_CLIP_SLACK = 1.dp

/** 日格指示点的直径（与生活页月历的 4dp 同尺寸）。 */
private val BILL_CAL_DOT_SIZE = 4.dp

/** 指示点与数字之间、以及两个点之间的间距。 */
private val BILL_CAL_DOT_GAP = 2.dp

/**
 * 日格里「指示点那一行」的固定高度。**不论当天有没有圆点都按这个高度占位**，
 * 这样有记录 / 没记录的格子内容总高相同，数字的纵向位置才会一致
 * （否则多出这一行会把数字往上顶 3dp，见 [DayCell]）。
 */
private val BILL_CAL_DOT_SLOT = BILL_CAL_DOT_GAP + BILL_CAL_DOT_SIZE
