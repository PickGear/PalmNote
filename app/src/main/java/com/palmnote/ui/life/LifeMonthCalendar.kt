package com.palmnote.ui.life

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.ui.theme.LifePlan
import com.palmnote.ui.theme.LifeRecord
import com.palmnote.ui.theme.LifeTime
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * A 版月历（设计稿 doubao_html「今日看板日历优化」落地，严格对齐）：
 * - 表头：标题（左，点击回今天）+ 年月展示 + 右上角「周 | 月」分段切换；
 * - 格子：正方形（aspect-ratio 1:1）、圆角 12dp、日期 13sp；
 * - 每天底部三色点编码出现的分类（计划蓝 / 时间粉 / 记录绿），钉在格底；
 * - **底色 / 圆点只随「有明确日期（`dueDate`）」的条目**，与下方看板列表同口径：
 *   没有日期的记录（打卡 / 心情 / 日记等模板无日期字段）不上日历，避免「格子有色、点进去却空」；
 * - 选中日（含今天）= 1dp 主色细环 + 粗字；今天未被选中时留一个同宽低透明度（0.28）弱化环做定位，
 *   避免屏幕上同时出现两个框、主次不分（设计稿的 3dp 粗环只给了今天，与其他选中日不一致，已弃用）；
 *
 * 比例换算：设计稿是 `max-w-md`(448px) 固定视口，页面 padding 16px、卡片 padding 24px，
 * 故卡片内容宽 = 448-32-48 = 368px；手机（逻辑宽约 382dp）首页列表同样的 16/24 padding，
 * 卡片内容宽 ≈ 302dp。因此卡内元素统一按 **302/368 ≈ 0.82** 换算，而不是 px→sp 直搬
 * （直搬会让文字相对卡片偏大约 1/5）：
 * 标题 20px→16sp、月份 18px→15sp、星期 12px→10sp、日期 16px→13sp、
 * 网格间距 8px→7dp、格子圆角 14px→12dp。
 *
 * 切月/切周：**只靠左右滑动**（HorizontalPager，跟手 + 惯性）；原 `< >` 导航按钮已移除（用户反馈：
 * 有滑动手势后按钮冗余）。
 * 网格行数**按月份动态**（4~6 行，见 [rowsForMonth]）—— 9 月只占 5 行就是 5 行，不补空行、不补灰号。
 * 代价：Pager 滑动时相邻两页同时可见，容器高度只能取其一，故高度锚定「**落定页**」的行数并用
 * 动画平滑伸缩（切月时卡片高度变化约 ±44dp）；滑动进行中高度改取「落定页 ± 1 页」的最大行数，
 * 保证滑入的 6 行月不会被裁掉末行（用户反馈过截断），落定后再收回实际行数。
 * 滑动只改「正在浏览的月份」，**不改选中日**（下方看板不动）；点某日才改选中日。
 *
 * 点击某日即选中并驱动下方看板。
 *
 * **头部职责**（用户反馈定稿）：左上角标题 = 点击**回到今天**；右上角 = 「周 | 月」分段切换 +
 * 当前年月（纯展示）。周视图只显示选中日所在周的一行格子（样式、圆点、选中环与月视图完全同源），
 * 跨月的周里非本月日灰字。
 */
@Composable
fun LifeMonthCalendar(
    title: String,
    selectedDate: LocalDate,
    weekMode: Boolean,
    onWeekModeChange: (Boolean) -> Unit,
    dayMap: Map<LocalDate, LifeCalendarViewModel.DayCalInfo>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    // 视图模式（周/月）由外部持有并持久化（DataStore）：右上角「周 | 月」分段切换；
    // 左上角标题点击回到今天。切月/切周只靠左右滑动。
    // 滑动切月：Pager 需要「基准月 + 居中页」，page ↔ YearMonth 一一映射（前后各约 50 年）。
    val baseMonth = remember { YearMonth.from(selectedDate) }
    val pagerState = rememberPagerState(initialPage = CENTER_PAGE) { MONTH_PAGE_COUNT }
    val displayMonth = baseMonth.plusMonths((pagerState.currentPage - CENTER_PAGE).toLong())
    // 周视图的「基准周 + 居中页」，与月 Pager 同构。头部年月取 [weekAnchorMonth]（锚定日所在月）：
    // 跨月周（如 9.28–10.4）若选中了 10.1，标签显示 10 月，而不是死板的周首日所在月 9 月。
    val baseWeek = remember { startOfWeek(selectedDate) }
    val weekPagerState = rememberPagerState(initialPage = CENTER_PAGE) { MONTH_PAGE_COUNT }
    val displayWeek = baseWeek.plusWeeks((weekPagerState.currentPage - CENTER_PAGE).toLong())
    val headerMonth = if (weekMode) weekAnchorMonth(displayWeek, selectedDate) else displayMonth

    // 高度锚点：只在「滑动落定」后才更新行数，避免拖动过程中高度反复伸缩。
    // （不能直接用 currentPage —— 它在拖动过半时就翻转，会让卡片在半途上蹿下跳。）
    var settledPage by remember { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collect { scrolling -> if (!scrolling) settledPage = pagerState.currentPage }
    }

    // 选中日变了（点日历格 / 点年月回今天）→ 把对应月份/所在周滑进视野；相邻走动画，跨多则直接跳。
    LaunchedEffect(selectedDate, weekMode) {
        if (weekMode) {
            val target = CENTER_PAGE + ChronoUnit.WEEKS.between(baseWeek, startOfWeek(selectedDate)).toInt()
            val page = target.coerceIn(0, MONTH_PAGE_COUNT - 1)
            when {
                page == weekPagerState.currentPage -> Unit
                abs(page - weekPagerState.currentPage) <= 1 -> weekPagerState.animateScrollToPage(page)
                else -> weekPagerState.scrollToPage(page)
            }
        } else {
            val target = CENTER_PAGE + ChronoUnit.MONTHS.between(baseMonth, YearMonth.from(selectedDate)).toInt()
            val page = target.coerceIn(0, MONTH_PAGE_COUNT - 1)
            when {
                page == pagerState.currentPage -> Unit
                abs(page - pagerState.currentPage) <= 1 -> pagerState.animateScrollToPage(page)
                else -> pagerState.scrollToPage(page)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        MonthHeader(
            title = title,
            weekMode = weekMode,
            onToggleMode = onWeekModeChange,
            displayMonth = headerMonth,
            onToday = { onSelectDate(today) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        WeekdayHeader()
        Spacer(modifier = Modifier.height(8.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 格子边长由可用宽度推出（7 列 + 6 个列间距）→ 行数由「落定月」决定的高度（格 + 行间距）。
            val cell = (maxWidth - GRID_GAP * (COLUMNS - 1)) / COLUMNS
            // 滑动中相邻两页同时可见：若落定月行数少于滑入月（如 9 月 5 行 → 8 月 6 行），
            // 固定按落定页取高会把滑入月的末行裁掉半截。故滑动中高度取落定页及左右相邻页的
            // 最大行数（宁可短暂多留白也不裁字），落定后收回到该月实际行数，animateDpAsState 平滑过渡。
            fun rowsOfPage(page: Int) = rowsForMonth(baseMonth.plusMonths((page - CENTER_PAGE).toLong()))
            val rows = if (pagerState.isScrollInProgress) {
                maxOf(rowsOfPage(settledPage - 1), rowsOfPage(settledPage), rowsOfPage(settledPage + 1))
            } else {
                rowsOfPage(settledPage)
            }
            // 周视图只占一行格子的高度；月/周切换时由 animateDpAsState 平滑伸缩。
            // +1dp 裁剪余量：格边长是小数 dp，容器与格子各自取整可能差 1px，
            // 不留余量时选中环底边会被 clipToBounds 裁掉一截（用户反馈过截断）。
            val targetHeight = (if (weekMode) cell else cell * rows + GRID_GAP * (rows - 1)) + CLIP_SLACK
            // 高度平滑伸缩：从 5 行月切到 6 行月时卡片「长高」，反向则「缩回」，不硬跳。
            val gridHeight by animateDpAsState(targetValue = targetHeight, label = "monthGridHeight")
            Box(modifier = Modifier.fillMaxWidth().height(gridHeight).clipToBounds()) {
                if (weekMode) {
                    HorizontalPager(
                        state = weekPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val weekStart = baseWeek.plusWeeks((page - CENTER_PAGE).toLong())
                        val weekMonth = weekAnchorMonth(weekStart, selectedDate)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
                        ) {
                            repeat(COLUMNS) { col ->
                                val date = weekStart.plusDays(col.toLong())
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    MonthDayCell(
                                        date = date,
                                        inMonth = YearMonth.from(date) == weekMonth,
                                        isToday = date == today,
                                        isSelected = date == selectedDate,
                                        info = dayMap[date],
                                        onClick = { onSelectDate(date) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        MonthGrid(
                            displayMonth = baseMonth.plusMonths((page - CENTER_PAGE).toLong()),
                            selectedDate = selectedDate,
                            dayMap = dayMap,
                            onSelectDate = onSelectDate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    title: String,
    weekMode: Boolean,
    onToggleMode: (Boolean) -> Unit,
    displayMonth: YearMonth,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左上角：标题点击回到今天（周/月视图通用），这是「回到今天」的唯一入口。
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onToday)
                .padding(vertical = 2.dp, horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.life_weekly_calendar_month_header, displayMonth.year, displayMonth.monthValue),
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 10.dp)
        )
        // 右上角：周/月分段切换（替代原 < > 按钮；切月/切周只靠左右滑动）。
        ModeToggle(weekMode = weekMode, onToggleMode = onToggleMode)
    }
}

/** 右上角的「周 | 月」分段切换：胶囊轨道 + 选中侧主色圆片。 */
@Composable
private fun ModeToggle(weekMode: Boolean, onToggleMode: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(TOGGLE_TRACK_BG)
            .padding(1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeToggleOption(
            label = stringResource(R.string.life_calendar_view_week),
            selected = weekMode,
            contentDescription = stringResource(R.string.life_calendar_toggle_week),
            onClick = { onToggleMode(true) }
        )
        ModeToggleOption(
            label = stringResource(R.string.life_calendar_view_month),
            selected = !weekMode,
            contentDescription = stringResource(R.string.life_calendar_toggle_month),
            onClick = { onToggleMode(false) }
        )
    }
}

@Composable
private fun ModeToggleOption(
    label: String,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick, onClickLabel = contentDescription)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else TOGGLE_TEXT
        )
    }
}

@Composable
private fun WeekdayHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
            Text(
                label,
                fontSize = 10.sp,
                color = WEEKDAY_TEXT,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MonthGrid(
    displayMonth: YearMonth,
    selectedDate: LocalDate,
    dayMap: Map<LocalDate, LifeCalendarViewModel.DayCalInfo>,
    onSelectDate: (LocalDate) -> Unit
) {
    // 行数按月动态：该月实际跨几周就画几行（4~6），不补空行、不补相邻月灰号。
    Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        repeat(rowsForMonth(displayMonth)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
            ) {
                repeat(7) { col ->
                    GridCell(
                        index = row * 7 + col,
                        displayMonth = displayMonth,
                        selectedDate = selectedDate,
                        dayMap = dayMap,
                        onSelectDate = onSelectDate
                    )
                }
            }
        }
    }
}

/**
 * 单格：非本月格（前导/尾部）**规则统一** —— 都显示相邻月的日期号（灰字 #C2C2C2、透明底、无圆点、可点，
 * 点后选中该日并自动切到对应月份）。月内格按真实数据渲染。
 *
 * 注：设计稿只在第一格填了上月「31」，尾部 4 格留空——那是静态稿的疏漏（两端不一致）。
 * 落地时按「同类格子同规则」修正为两端都填号；行数按月动态后，尾部格数量即该月最后一周的空位数。
 */
@Composable
private fun RowScope.GridCell(
    index: Int,
    displayMonth: YearMonth,
    selectedDate: LocalDate,
    dayMap: Map<LocalDate, LifeCalendarViewModel.DayCalInfo>,
    onSelectDate: (LocalDate) -> Unit
) {
    val firstDayOffset = (displayMonth.atDay(1).dayOfWeek.value + 6) % 7
    val daysInMonth = displayMonth.lengthOfMonth()
    val today = LocalDate.now()
    val prevYm = displayMonth.minusMonths(1)
    val prevDays = prevYm.lengthOfMonth()
    when {
        index < firstDayOffset -> {
            val date = prevYm.atDay(prevDays - (firstDayOffset - index - 1))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MonthDayCell(
                    date = date,
                    inMonth = false,
                    isToday = false,
                    isSelected = false,
                    info = null,
                    onClick = { onSelectDate(date) }
                )
            }
        }
        index >= firstDayOffset + daysInMonth -> {
            val date = displayMonth.plusMonths(1).atDay(index - firstDayOffset - daysInMonth + 1)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MonthDayCell(
                    date = date,
                    inMonth = false,
                    isToday = false,
                    isSelected = false,
                    info = null,
                    onClick = { onSelectDate(date) }
                )
            }
        }
        else -> {
            val date = displayMonth.atDay(index - firstDayOffset + 1)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MonthDayCell(
                    date = date,
                    inMonth = true,
                    isToday = date == today,
                    isSelected = date == selectedDate,
                    info = dayMap[date],
                    onClick = { onSelectDate(date) }
                )
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    info: LifeCalendarViewModel.DayCalInfo?,
    onClick: () -> Unit
) {
    // 底色 / 圆点一律随数据：当天没有记录 → 透明底、无圆点。
    // 「今天」只靠主色细环标记当前日，不再强制涂粉（避免「没记录却有底色」的歧义）。
    val level = heatLevel(info?.count ?: 0)
    val bg = heatBackground(level)
    val textColor = when {
        !inMonth -> OTHER_TEXT
        level >= 3 -> Color.White
        else -> DAY_TEXT
    }
    // 框的取舍（用户反馈：选中环必须统一粗细）：
    // - 选中（含今天）→ 统一 1dp 主色细环，今天不再加粗（设计稿 3dp 环与其他选中日不一致）；
    // - 今天未被选中 → 同样细，但透明度降到 0.28，只作「今天在哪」的定位提示，不与选中框争焦点。
    val border = when {
        isSelected -> BorderStroke(SELECT_RING_WIDTH, TODAY_RING)
        isToday -> BorderStroke(SELECT_RING_WIDTH, TODAY_RING.copy(alpha = TODAY_HINT_ALPHA))
        else -> null
    }
    val shape = RoundedCornerShape(DAY_CELL_RADIUS)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 13.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.align(Alignment.Center)
        )
        if (inMonth && info != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf(CAT_PLAN, CAT_TIME, CAT_RECORD).forEach { cat ->
                    if (cat in info.categories) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(categoryDotColor(cat))
                        )
                    }
                }
            }
        }
    }
}

/** 该日期所在周的第一天（周一），周视图以它为锚。 */
private fun startOfWeek(date: LocalDate): LocalDate =
    date.minusDays(((date.dayOfWeek.value + 6) % 7).toLong())

/**
 * 周视图某周的「锚定月」：优先选中日所在月（选了 10.1 就显示 10 月），
 * 其次今天所在月（浏览回本周时），否则周首日所在月。格子的「本月/非本月」灰字判定同此口径。
 */
private fun weekAnchorMonth(weekStart: LocalDate, selectedDate: LocalDate): YearMonth {
    val weekEnd = weekStart.plusDays(6)
    val today = LocalDate.now()
    return when {
        selectedDate >= weekStart && selectedDate <= weekEnd -> YearMonth.from(selectedDate)
        today >= weekStart && today <= weekEnd -> YearMonth.from(today)
        else -> YearMonth.from(weekStart)
    }
}

/**
 * 某月所需的网格行数：`(前导偏移 + 当月天数)` 向上取整到整周（周一起算）。
 * 取值范围 4~6 —— 例如 2 月 28 天且从周一开始 = 4 行；31 天且偏移 5/6 = 6 行。
 */
private fun rowsForMonth(month: YearMonth): Int {
    val firstDayOffset = (month.atDay(1).dayOfWeek.value + 6) % 7
    return (firstDayOffset + month.lengthOfMonth() + 6) / 7
}

/** 热力分档：完全按当日事件数；0 件 = 透明（无底色 / 无圆点）。「今天」不在此强制上色。 */
private fun heatLevel(count: Int): Int = when {
    count <= 0 -> 0
    count == 1 -> 1
    count <= 3 -> 2
    count <= 5 -> 3
    else -> 4
}

private fun heatBackground(level: Int): Color = when (level) {
    0 -> Color.Transparent
    1 -> PINK1
    2 -> PINK2
    3 -> PINK3
    else -> PINK4
}

private fun categoryDotColor(category: String): Color = when (category) {
    CAT_PLAN -> LifePlan
    CAT_TIME -> LifeTime
    CAT_RECORD -> LifeRecord
    else -> LifePlan
}

// ── A 版设计稿配色（doubao_html 今日看板日历优化）──
private val PINK1 = Color(0xFFFCE4EC)
private val PINK2 = Color(0xFFF8CDDB)
private val PINK3 = Color(0xFFF48FB1)
private val PINK4 = Color(0xFFEC407A)
private val TODAY_RING = Color(0xFFD81B60)
private val DAY_TEXT = Color(0xFF444444)
private val OTHER_TEXT = Color(0xFFC2C2C2)
private val WEEKDAY_TEXT = Color(0xFF9CA3AF)
private val TOGGLE_TRACK_BG = Color(0xFFF3F4F6)
private val TOGGLE_TEXT = Color(0xFF6B7280)

/** 网格列数（周一至周日）。 */
private const val COLUMNS = 7

/** 设计稿网格间距（calendar-grid gap: 8px）按 0.82 比例换算 → 7dp。 */
private val GRID_GAP = 7.dp

/** 滑动切月的总页数与居中页（约 100 年；居中页对应进入时所在月份）。 */
private const val MONTH_PAGE_COUNT = 1200
private const val CENTER_PAGE = MONTH_PAGE_COUNT / 2

/** 日期格圆角：设计稿 14px 按 0.82 换算 → 12dp。 */
private val DAY_CELL_RADIUS = 12.dp

/**
 * 网格容器高度的裁剪余量：格边长为小数 dp，容器与格子取整可能差 1px，
 * 不留余量时末行/单行格子的选中环底边会被裁。1dp 视觉不可感知。
 */
private val CLIP_SLACK = 1.dp

/** 选中框宽度：1dp，选中日（含今天）与「今天」的弱化定位环统一同宽，视觉上更干净。 */
private val SELECT_RING_WIDTH = 1.dp

/** 「今天」被别的日期选中时的环透明度：只作定位提示，不能与选中框争焦点。 */
private const val TODAY_HINT_ALPHA = 0.28f

private const val CAT_PLAN = "计划"
private const val CAT_TIME = "时间"
private const val CAT_RECORD = "记录"
