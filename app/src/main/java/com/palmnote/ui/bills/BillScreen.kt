package com.palmnote.ui.bills

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import java.util.Calendar
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.data.db.entity.getDisplayDescription
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(
    onNavigateToAdd: () -> Unit = {},
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    onNavigateToReport: (Long, String) -> Unit = { _, _ -> },
    onNavigateToImportCsv: () -> Unit = {},
    onNavigateToAccountBook: () -> Unit = {},
    viewModel: BillViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("ALL") }
    val todayDay = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    val isCurrentMonth = remember(state.currentYearMonth) {
        state.currentYearMonth == DateUtils.getCurrentYearMonth()
    }
    var selectedDay by remember(state.currentYearMonth) {
        mutableStateOf(if (isCurrentMonth) todayDay else null)
    }
    var calendarExpanded by remember { mutableStateOf(false) }
    val filteredBills by remember(state.bills, selectedFilter, selectedDay) {
        derivedStateOf {
            val byType = when (selectedFilter) {
                "EXPENSE" -> state.bills.filter { it.type == "EXPENSE" }
                "INCOME" -> state.bills.filter { it.type == "INCOME" }
                else -> state.bills
            }
            if (selectedDay != null) byType.filter { DateUtils.getDayOfMonth(it.date) == selectedDay }
            else byType
        }
    }

    var showBookMenu by remember { mutableStateOf(false) }
    val currentBook = state.accountBooks.find { it.id == state.selectedBookId }
        ?: state.allAccountBooks.find { it.id == state.selectedBookId }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showBookMenu = true }
                                .padding(end = 4.dp)
                        ) {
                            Text(
                                text = currentBook?.getDisplayName(context) ?: stringResource(R.string.bill_title),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                    color = ModuleBill
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (showBookMenu) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                contentDescription = stringResource(R.string.bill_switch_book),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (showBookMenu) {
                            Popup(
                                alignment = Alignment.TopStart,
                                offset = with(LocalDensity.current) { IntOffset(0, 56.dp.roundToPx()) },
                                onDismissRequest = { showBookMenu = false },
                                properties = PopupProperties(focusable = true)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.background,
                                    shadowElevation = 3.dp
                                ) {
                                    Column(modifier = Modifier.width(260.dp).padding(vertical = 4.dp)) {
                                        Text(stringResource(R.string.bill_book_list), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                                        state.accountBooks.forEach { book ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        viewModel.selectAccountBook(book.id)
                                                        showBookMenu = false
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                            ) {
                                                Surface(shape = CircleShape, color = try { Color(android.graphics.Color.parseColor(book.color)) } catch (_: Exception) { Color.Gray }, modifier = Modifier.size(32.dp)) {
                                                    Box(contentAlignment = Alignment.Center) { Icon(book.icon.imageVector, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White) }
                                                }
                                                Spacer(Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(book.getDisplayName(context), style = MaterialTheme.typography.bodyMedium)
                                                        if (book.isDefault) {
                                                            Spacer(Modifier.width(6.dp))
                                                            Surface(
                                                                shape = MaterialTheme.shapes.extraSmall,
                                                                color = AccentOrange.copy(alpha = 0.1f)
                                                            ) {
                                                                Text(stringResource(R.string.bill_default), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                    style = MaterialTheme.typography.labelSmall, color = AccentOrange)
                                                            }
                                                        }
                                                    }
                                                    if (book.getDisplayDescription(context).isNotEmpty()) {
                                                        Text(book.getDisplayDescription(context), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                    }
                                                }
                                                if (book.id == state.selectedBookId) {
                                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showBookMenu = false
                                                    onNavigateToAccountBook()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, null, modifier = Modifier.padding(end = 8.dp))
                                            Text(stringResource(R.string.settings_bill_manage))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToImportCsv) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = stringResource(R.string.bill_import), tint = AccentOrange)
                    }
                    val allBooksLabel = stringResource(R.string.bill_all_books)
                    IconButton(onClick = { onNavigateToReport(state.selectedBookId, currentBook?.getDisplayName(context) ?: allBooksLabel) }) {
                        Icon(Icons.Outlined.Assessment, contentDescription = stringResource(R.string.bill_report), tint = AccentOrange)
                    }
                    IconButton(onClick = onNavigateToBudget) {
                        Icon(Icons.Outlined.AccountBalance, contentDescription = stringResource(R.string.bill_budget_tab), tint = AccentOrange)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = AccentOrange,
                contentColor = Color.White,
                shape = MaterialTheme.shapes.large,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.bill_add), fontWeight = FontWeight.Medium) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Summary Card
            item {
                ModuleCard(tint = billTint(), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    // Main numbers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.bill_monthly_expense),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyUtils.formatCurrency(state.monthlyExpense),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.bill_monthly_income),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyUtils.formatCurrency(state.monthlyIncome),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusActive
                            )
                        }
                    }

                    // Net
                    val net = state.monthlyIncome - state.monthlyExpense
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (net >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = if (net >= 0) StatusActive else ErrorLight
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.bill_balance_amount, "${if (net >= 0) "+" else ""}${CurrencyUtils.formatCurrency(net)}"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (net >= 0) StatusActive else ErrorLight
                        )
                    }

                    // Budget progress
                    if (state.budget != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        val budget = state.budget
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.bill_budget_usage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${CurrencyUtils.formatCurrency(state.monthlyExpense)} / ${CurrencyUtils.formatCurrency(budget?.totalBudget ?: 0.0)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (state.budgetUsagePercent > 1f) ErrorLight
                                        else if (state.budgetUsagePercent > 0.8f) AccentOrange
                                        else StatusActive
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        ProgressBar(
                            progress = state.budgetUsagePercent,
                            color = when {
                                state.budgetUsagePercent > 1f -> ErrorLight
                                state.budgetUsagePercent > 0.8f -> AccentOrange
                                else -> StatusActive
                            },
                            height = 8.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${(state.budgetUsagePercent * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val remaining = (budget?.totalBudget ?: 0.0) - state.monthlyExpense
                            Text(
                                if (remaining >= 0) stringResource(R.string.bill_remaining_amount, CurrencyUtils.formatCurrency(remaining))
                                else stringResource(R.string.bill_over_budget_amount, CurrencyUtils.formatCurrency(-remaining)),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (remaining >= 0) StatusActive else ErrorLight
                            )
                        }
                    }
                }
            }

            // Calendar View
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.bill_calendar), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { calendarExpanded = !calendarExpanded }) {
                            Text(if (calendarExpanded) stringResource(R.string.bill_collapse) else stringResource(R.string.bill_expand), style = MaterialTheme.typography.labelMedium, color = AccentOrange)
                        }
                    }

                    CalendarView(
                        yearMonth = state.currentYearMonth,
                        dailyData = state.dailySummary,
                        selectedDay = selectedDay,
                        onDaySelected = { day ->
                            selectedDay = if (selectedDay == day) null else day
                        },
                        collapsed = !calendarExpanded,
                        onMonthChanged = { newMonth -> viewModel.setMonth(newMonth) }
                    )
                }
            }

            // Filter chips (no ripple)
            item {
                Row(modifier = Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val interactionSource = remember { MutableInteractionSource() }
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (selectedFilter == "ALL") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier.clickable(interactionSource = interactionSource, indication = null) { selectedFilter = "ALL" }
                    ) {
                        Text(
                            text = stringResource(R.string.bill_all),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (selectedFilter == "ALL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    val interactionSource2 = remember { MutableInteractionSource() }
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (selectedFilter == "EXPENSE") ExpenseRed.copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier.clickable(interactionSource = interactionSource2, indication = null) { selectedFilter = "EXPENSE" }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.TrendingDown, null, Modifier.size(16.dp), tint = if (selectedFilter == "EXPENSE") ExpenseRed else MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.bill_expense),
                                color = if (selectedFilter == "EXPENSE") ExpenseRed else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    val interactionSource3 = remember { MutableInteractionSource() }
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (selectedFilter == "INCOME") StatusActive.copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier.clickable(interactionSource = interactionSource3, indication = null) { selectedFilter = "INCOME" }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.TrendingUp, null, Modifier.size(16.dp), tint = if (selectedFilter == "INCOME") StatusActive else MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.bill_income),
                                color = if (selectedFilter == "INCOME") StatusActive else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Expense by Category (pie chart)
            if (state.expenseByCategory.isNotEmpty()) {
                item {
                    ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(
                            text = stringResource(R.string.bill_expense_category),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mini pie chart
                            val total = state.expenseByCategory.sumOf { it.total }.takeIf { it > 0 } ?: 1.0
                            Canvas(modifier = Modifier.size(80.dp)) {
                                val strokeWidth = 10.dp.toPx()
                                val radius = (size.minDimension - strokeWidth) / 2
                                val center = Offset(size.width / 2, size.height / 2)
                                var startAngle = -90f

                                state.expenseByCategory.forEachIndexed { index, item ->
                                    val sweep = (item.total / total * 360f).toFloat()
                                    drawArc(
                                        color = ChartColors[index % ChartColors.size],
                                        startAngle = startAngle,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        topLeft = Offset(center.x - radius, center.y - radius),
                                        size = Size(radius * 2, radius * 2),
                                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                    )
                                    startAngle += sweep
                                }
                            }

                            // Legend
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.expenseByCategory.take(5).forEachIndexed { index, item ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(ChartColors[index % ChartColors.size])
                                        )
                                        Text(
                                            text = stringResource(getLocalizedCategoryName(item.category)),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = CurrencyUtils.formatCurrency(item.total),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${(item.total / total * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bill List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.bill_detail),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.bill_count, filteredBills.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (filteredBills.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Receipt,
                        title = stringResource(R.string.bill_no_records),
                        subtitle = stringResource(R.string.bill_start_recording)
                    )
                }
            } else {
                // Group by date
                val grouped = filteredBills.groupBy { DateUtils.formatDate(it.date) }
                grouped.forEach { (_, bills) ->
                    item {
                        Text(
                            text = DateUtils.formatDisplayDateWithWeekday(context, bills.first().date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(bills, key = { it.id }) { bill ->
                        BillListItem(
                            bill = bill,
                            wallets = state.wallets,
                            onDetail = { onNavigateToDetail(bill.id) },
                            onDelete = { viewModel.deleteBill(bill.id) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun BillListItem(bill: Bill, wallets: Map<Long, String> = emptyMap(), onDetail: () -> Unit, onDelete: () -> Unit) {
    val isExpense = bill.type == "EXPENSE"
    val categoryItem = remember(bill.category, bill.type) {
        val list = if (isExpense) expenseCategoryItems else incomeCategoryItems
        list.find { it.name == bill.category }
    }
    val catColor = categoryItem?.color ?: StatusRetired

    val density = LocalDensity.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    val deleteWidthPx = with(density) { 80.dp.toPx() }
    val thresholdPx = with(density) { 60.dp.toPx() }

    Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier.fillMaxHeight().width(80.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.errorContainer).clickable { onDelete() }.align(Alignment.CenterEnd),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.delete), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(bill.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = { offsetX = if (offsetX < -thresholdPx) -deleteWidthPx else 0f },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount -> offsetX = (offsetX + dragAmount).coerceIn(-deleteWidthPx, 0f) }
                    )
                }
                .clickable { if (offsetX == 0f) onDetail() else offsetX = 0f }
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).padding(end = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(modifier = Modifier.weight(1f, false), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(catColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        if (categoryItem != null) {
                            Icon(categoryItem.icon, null, tint = catColor, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(getLocalizedCategoryName(bill.category)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (bill.subCategory.isNotEmpty()) Text(" · ${bill.subCategory}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (bill.merchant.isNotEmpty()) {
                            Text(bill.merchant, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (bill.note.isNotEmpty()) {
                            Text(bill.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "${if (isExpense) "-" else "+"}${CurrencyUtils.formatCurrency(bill.amount)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (isExpense) ExpenseRed else StatusActive)
                    bill.walletId?.let { walletId ->
                        wallets[walletId]?.let { walletName ->
                            Text(walletName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    if (bill.isReimbursable && !bill.isReimbursed) StatusChip(stringResource(R.string.bill_reimbursable), AccentOrange)
                }
            }
        }
    }
}
