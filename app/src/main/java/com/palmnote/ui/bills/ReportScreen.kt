package com.palmnote.ui.bills

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.PalmNoteApp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.R
import com.palmnote.data.db.dao.CategoryTotal
import com.palmnote.data.db.dao.DailySummary
import com.palmnote.data.db.dao.MonthTotal
import com.palmnote.domain.model.Money
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

private val BrandBlue = InfoBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToAddBill: () -> Unit = {},
    selectedBookId: Long = -1L,
    bookName: String? = null,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val resolvedBookName = bookName ?: stringResource(R.string.report_all_books)
    LaunchedEffect(selectedBookId) {
        viewModel.setSelectedBookId(selectedBookId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reportCustomCfg by PalmNoteApp.instance.cachedCategoryConfigs
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val reportCustomExpense = remember(reportCustomCfg) {
        reportCustomCfg.filter { it.type == "BILL_EXPENSE" && it.isEnabled }
            .map { com.palmnote.ui.components.CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
    }
    val reportCustomIncome = remember(reportCustomCfg) {
        reportCustomCfg.filter { it.type == "BILL_INCOME" && it.isEnabled }
            .map { com.palmnote.ui.components.CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.report_title),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = resolvedBookName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            item { PeriodSelector(state, viewModel) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { IncomeExpenseToggle(state, viewModel) }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { PeriodTabRow(state, viewModel) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            if (state.isLoading) {
                item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
            if (state.error != null) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SummarySection(state) }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item { DonutChartSection(state, onNavigateToAddBill, reportCustomExpense, reportCustomIncome) }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item { CategoryRankingSection(state, reportCustomExpense, reportCustomIncome) }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item { TrendChartSection(state) }
        }
    }
}

@Composable
private fun PeriodSelector(state: ReportState, viewModel: ReportViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            when (state.periodTab) {
                0 -> viewModel.previousWeek()
                1 -> viewModel.previousMonth()
                2 -> viewModel.previousYear()
            }
        }) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.report_previous))
        }
        Text(
            text = when (state.periodTab) {
                0 -> DateUtils.formatWeekRange(context, state.weekStart, state.weekEnd)
                1 -> DateUtils.formatDisplayMonth(context, state.currentYearMonth)
                2 -> stringResource(R.string.report_year_format, state.currentYear.toIntOrNull() ?: 0)
                else -> ""
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = {
            when (state.periodTab) {
                0 -> viewModel.nextWeek()
                1 -> viewModel.nextMonth()
                2 -> viewModel.nextYear()
            }
        }) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.report_next))
        }
    }
}

@Composable
private fun IncomeExpenseToggle(state: ReportState, viewModel: ReportViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(stringResource(R.string.bill_expense), stringResource(R.string.bill_income)).forEachIndexed { index, label ->
            val isSelected = state.incomeExpenseTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { viewModel.setIncomeExpenseTab(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) {
                        if (index == 0) ExpenseRed else IncomeGreen
                    } else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PeriodTabRow(state: ReportState, viewModel: ReportViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(stringResource(R.string.report_week), stringResource(R.string.report_month), stringResource(R.string.report_year)).forEachIndexed { index, label ->
            val isSelected = state.periodTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { viewModel.setPeriodTab(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummarySection(state: ReportState) {
    val isExpense = state.incomeExpenseTab == 0
    val total = if (isExpense) state.data.totalExpense else state.data.totalIncome
    val totalColor = if (isExpense) ExpenseRed else IncomeGreen
    val totalLabel = if (isExpense) stringResource(R.string.report_total_expense) else stringResource(R.string.report_total_income)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = totalLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${CurrencyUtils.formatCompact(total.toMoney())}",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = totalColor
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = stringResource(R.string.report_daily_avg, CurrencyUtils.formatCompact(Money(state.data.avgDaily.toLong()))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.data.billCount > 0) {
                Text(
                    text = stringResource(R.string.report_count_format, state.data.billCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DonutChartSection(state: ReportState, onNavigateToAddBill: () -> Unit,
    customExpense: List<CategoryItem> = emptyList(), customIncome: List<CategoryItem> = emptyList()) {
    val categories = state.data.categories
    val isExpense = state.incomeExpenseTab == 0
    val total = if (isExpense) state.data.totalExpense else state.data.totalIncome

    if (categories.isEmpty() || total <= 0) {
        EmptyChartState(onNavigateToAddBill)
        return
    }

    val sortedCategories = categories.sortedByDescending { it.total }
    val topCategories = sortedCategories.take(5)
    val otherTotal = sortedCategories.drop(5).sumOf { it.total }
    val displayCategories = if (otherTotal > 0) {
                topCategories + CategoryTotal(stringResource(R.string.report_other), otherTotal)
    } else {
        topCategories
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(140.dp)) {
                val strokeWidth = 20.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)
                val totalAngle = 360f
                val gapAngle = 2f
                val availableAngle = totalAngle - gapAngle * displayCategories.size

                var startAngle = -90f
                displayCategories.forEach { cat ->
                    val sweepAngle = if (total > 0) (cat.total.toDouble() / total * availableAngle).toFloat() else 0f
                    val isSelected = selectedCategory == cat.category
                    val offset = if (isSelected) 8f else 0f
                    val midAngle = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
                    val offsetX = (offset * Math.cos(midAngle)).toFloat()
                    val offsetY = (offset * Math.sin(midAngle)).toFloat()

                    drawArc(
                        color = getCatColor(cat.category, isExpense, if (isExpense) customExpense else customIncome),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(
                            center.x - radius + offsetX,
                            center.y - radius + offsetY
                        ),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweepAngle + gapAngle
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${CurrencyUtils.formatCompact(total.toMoney())}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (isExpense) stringResource(R.string.report_total_expense_label) else stringResource(R.string.report_total_income_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            displayCategories.forEach { cat ->
                val fraction = if (total > 0) (cat.total.toDouble() / total * 100).toInt() else 0
                val color = getCatColor(cat.category, isExpense, if (isExpense) customExpense else customIncome)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            selectedCategory = if (selectedCategory == cat.category) null else cat.category
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(color)
                    )
                    Spacer(Modifier.width(8.dp))
                    val rptResId1 = getLocalizedCategoryName(cat.category)
                    Text(
                        text = if (rptResId1 != null) stringResource(rptResId1) else cat.category,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$fraction%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChartState(onNavigateToAddBill: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.PieChart,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.report_no_records), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateToAddBill) {
            Text(stringResource(R.string.bill_add))
        }
    }
}

@Composable
private fun CategoryRankingSection(state: ReportState,
    customExpense: List<CategoryItem> = emptyList(), customIncome: List<CategoryItem> = emptyList()) {
    val categories = state.data.categories
    val isExpense = state.incomeExpenseTab == 0
    val total = if (isExpense) state.data.totalExpense else state.data.totalIncome

    if (categories.isEmpty() || total <= 0) return

    var expanded by remember { mutableStateOf(false) }
    val displayCategories = if (expanded) categories else categories.take(6)
    val hasMore = categories.size > 6

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.report_category_ranking),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (hasMore) {
                Text(
                    text = if (expanded) stringResource(R.string.report_collapse) else stringResource(R.string.report_expand_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { expanded = !expanded }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        displayCategories.forEach { cat ->
            CategoryRankingItem(cat, total, isExpense, if (isExpense) customExpense else customIncome)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CategoryRankingItem(cat: CategoryTotal, total: Long, isExpense: Boolean,
    customItems: List<CategoryItem> = emptyList()) {
    val fraction = if (total > 0) (cat.total.toFloat() / total).toFloat() else 0f
    val color = getCatColor(cat.category, isExpense, customItems)
    val categoryItem = (if (isExpense) expenseCategoryItems else incomeCategoryItems).find { it.name == cat.category }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            categoryItem?.icon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val rptResId2 = getLocalizedCategoryName(cat.category)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (rptResId2 != null) stringResource(rptResId2) else cat.category,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = " ${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${CurrencyUtils.formatCompact(cat.total.toMoney())}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun TrendChartSection(state: ReportState) {
    val periodTab = state.periodTab
    val isExpense = state.incomeExpenseTab == 0

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when (periodTab) {
                0 -> stringResource(R.string.report_weekly_trend)
                1 -> stringResource(R.string.report_monthly_trend)
                2 -> stringResource(R.string.report_yearly_trend)
                else -> ""
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        when (periodTab) {
            0 -> WeeklyBarChart(state.data.dailySummary, isExpense)
            1 -> MonthlyLineChart(state.data.dailySummary, isExpense)
            2 -> YearlyBarChart(state.data.monthlyTrend, isExpense)
        }
    }
}

@Composable
private fun WeeklyBarChart(dailySummary: List<DailySummary>, isExpense: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    if (dailySummary.isEmpty()) {
        GrayChartPlaceholder()
        return
    }

    val maxValue = dailySummary.maxOf { if (isExpense) it.expense else it.income }.toFloat().coerceAtLeast(1f)
    val weekdays = DateUtils.getWeekdayNames(context)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val leftPad = 40.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val chartW = size.width - leftPad
        val chartH = size.height - bottomPad
        val barWidth = 28.dp.toPx()
        val gap = (chartW - barWidth * 7) / 8

        for (i in 0..4) {
            val y = size.height - bottomPad - chartH * i / 4
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5f
            )
        }

        // Performance fix: hoist Paint object outside the loop
        val textPaint = android.graphics.Paint().apply {
            textSize = 10.sp.toPx()
            color = 0xFF9CA3AF.toInt()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val cal = java.util.Calendar.getInstance()

        // 按星期槽位放置柱子（0=周一 … 6=周日），无记录的天留空
        val slotValues = Array<Pair<Int, Long>?>(7) { null }
        dailySummary.takeLast(7).forEach { day ->
            cal.timeInMillis = day.date
            val weekday = cal.get(java.util.Calendar.DAY_OF_WEEK)
            val slot = when (weekday) {
                java.util.Calendar.MONDAY -> 0
                java.util.Calendar.TUESDAY -> 1
                java.util.Calendar.WEDNESDAY -> 2
                java.util.Calendar.THURSDAY -> 3
                java.util.Calendar.FRIDAY -> 4
                java.util.Calendar.SATURDAY -> 5
                java.util.Calendar.SUNDAY -> 6
                else -> -1
            }
            if (slot in 0..6) {
                slotValues[slot] = slot to (if (isExpense) day.expense else day.income)
            }
        }

        slotValues.forEachIndexed { slot, valueAndWeekday ->
            val value = valueAndWeekday?.second ?: return@forEachIndexed
            val barHeight = (value.toFloat() / maxValue) * chartH
            val x = leftPad + gap + slot * (barWidth + gap)
            val y = size.height - bottomPad - barHeight

            drawRoundRect(
                color = if (isExpense) ExpenseRed else IncomeGreen,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight.coerceAtLeast(1f)),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            if (slot < weekdays.size) {
                drawContext.canvas.nativeCanvas.drawText(
                    weekdays[slot],
                    x + barWidth / 2,
                    size.height - 4.dp.toPx(),
                    textPaint
                )
            }
        }
    }
}

@Composable
private fun MonthlyLineChart(dailySummary: List<DailySummary>, isExpense: Boolean) {
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    if (dailySummary.isEmpty()) {
        GrayChartPlaceholder()
        return
    }

    val monthRef = DateUtils.millisToLocalDate(dailySummary.first().date)
    val daysInMonth = monthRef.lengthOfMonth()
    val byDay = dailySummary.associateBy { DateUtils.millisToLocalDate(it.date).dayOfMonth }
    val maxValue = dailySummary.maxOf { if (isExpense) it.expense else it.income }.toFloat().coerceAtLeast(1f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val leftPad = 40.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val chartW = size.width - leftPad
        val chartH = size.height - bottomPad

        for (i in 0..4) {
            val y = size.height - bottomPad - chartH * i / 4
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5f
            )
        }

        // 按自然日铺满整月，无记录的天补 0，避免折线被压缩
        val points = (1..daysInMonth).map { day ->
            val value = if (isExpense) byDay[day]?.expense ?: 0 else byDay[day]?.income ?: 0
            val x = leftPad + chartW * (day - 1) / (daysInMonth - 1).coerceAtLeast(1)
            val y = size.height - bottomPad - (value.toFloat() / maxValue) * chartH
            Offset(x, y)
        }

        if (points.size >= 2) {
            val fillPath = Path().apply {
                moveTo(points.first().x, size.height - bottomPad)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, size.height - bottomPad)
                close()
            }
            drawPath(fillPath, BrandBlue.copy(alpha = 0.1f))

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            drawPath(linePath, BrandBlue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }

        points.forEach { point ->
            drawCircle(BrandBlue, 3.dp.toPx(), point)
            drawCircle(Color.White, 1.5.dp.toPx(), point)
        }
    }
}

@Composable
private fun YearlyBarChart(monthlyTrend: List<MonthTotal>, isExpense: Boolean) {
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    if (monthlyTrend.isEmpty()) {
        GrayChartPlaceholder()
        return
    }

    val maxValue = monthlyTrend.maxOf { it.total }.toFloat().coerceAtLeast(1f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val leftPad = 40.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val chartW = size.width - leftPad
        val chartH = size.height - bottomPad
        val barWidth = 20.dp.toPx()
        val gap = (chartW - barWidth * 12) / 13

        for (i in 0..4) {
            val y = size.height - bottomPad - chartH * i / 4
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5f
            )
        }

        // Performance fix: hoist Paint object outside the loop
        val monthPaint = android.graphics.Paint().apply {
            textSize = 9.sp.toPx()
            color = 0xFF9CA3AF.toInt()
            textAlign = android.graphics.Paint.Align.CENTER
        }

        monthlyTrend.forEachIndexed { index, month ->
            val barHeight = (month.total.toFloat() / maxValue) * chartH
            val x = leftPad + gap + index * (barWidth + gap)
            val y = size.height - bottomPad - barHeight

            drawRoundRect(
                color = if (isExpense) ExpenseRed else IncomeGreen,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight.coerceAtLeast(1f)),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            drawContext.canvas.nativeCanvas.drawText(
                "${index + 1}",
                x + barWidth / 2,
                size.height - 4.dp.toPx(),
                monthPaint
            )
        }
    }
}

@Composable
private fun GrayChartPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun getCatColor(name: String, isExpense: Boolean, customItems: List<CategoryItem>? = null): Color {
    val fromPreset = (if (isExpense) expenseCategoryItems else incomeCategoryItems).find { it.name == name }
    val fromCustom = customItems?.find { it.name == name }
    val fallback = fromCustom?.color ?: fromPreset?.color ?: Gray400
    return ColorResolver.resolve(name, fallback)
}
