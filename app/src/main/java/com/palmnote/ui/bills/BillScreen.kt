package com.palmnote.ui.bills

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.palmnote.PalmNoteApp
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
    onNavigateToAdd: (Long) -> Unit = {},
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigateToBudget: () -> Unit = {},
    onNavigateToReport: (Long, String) -> Unit = { _, _ -> },
    onNavigateToImportCsv: () -> Unit = {},
    onNavigateToAccountBook: () -> Unit = {},
    viewModel: BillViewModel = simpleViewModel { PalmNoteApp.container.billViewModel() }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val customExpenseCategories by viewModel.customExpenseCategories.collectAsStateWithLifecycle()
    val customIncomeCategories by viewModel.customIncomeCategories.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadBillData()
        }
    }
    var calendarExpanded by remember { mutableStateOf(false) }
    val selectedFilter = state.currentFilter.type ?: "ALL"
    val filteredBills by remember(state.bills, state.filteredBills, state.currentFilter, selectedFilter, state.selectedDay) {
        derivedStateOf {
            val base = if (state.currentFilter.isActive || state.searchQuery.isNotBlank()) state.filteredBills else state.bills
            val byType = when (selectedFilter) {
                "EXPENSE" -> base.filter { it.type == "EXPENSE" }
                "INCOME" -> base.filter { it.type == "INCOME" }
                else -> base
            }
            val sd = state.selectedDay
            if (sd != null) byType.filter { DateUtils.getDayOfMonth(it.date) == sd }
            else byType
        }
    }

    val groupedBills = remember(filteredBills) { filteredBills.groupBy { DateUtils.formatDate(it.date) } }

    var showBookMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val currentBook = state.accountBooks.find { it.id == state.selectedBookId }
        ?: state.allAccountBooks.find { it.id == state.selectedBookId }
    
    var billToDelete by remember { mutableStateOf<Bill?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.filterFeedbackSignal) {
        if (state.filterFeedbackSignal > 0) {
            snackbarHostState.showSnackbar(
                message = if (state.currentFilter.isActive) context.getString(R.string.bill_filter_applied) else context.getString(R.string.bill_filter_cleared),
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .exclude(WindowInsets.navigationBars),
        topBar = {
            CompactTopAppBar(
                title = {
                    if (showSearch) {
                        ModuleSearchBar(
                            query = state.searchQuery,
                            onQueryChange = { viewModel.onSearchQueryChanged(it) },
                            onClear = { viewModel.clearSearch() },
                            placeholder = stringResource(R.string.search),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
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
                                                    Surface(shape = CircleShape, color = book.color.toComposeColor(Color.Gray), modifier = Modifier.size(32.dp)) {
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
                    }
                },
                actions = {
                    if (showSearch) {
                        TextButton(onClick = { showSearch = false; viewModel.clearSearch() }, modifier = Modifier.padding(end = 4.dp)) {
                            Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onNavigateToImportCsv) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = stringResource(R.string.bill_import), tint = MaterialTheme.colorScheme.primary)
                        }
                        val allBooksLabel = stringResource(R.string.bill_all_books)
                        IconButton(onClick = { onNavigateToReport(state.selectedBookId, currentBook?.getDisplayName(context) ?: allBooksLabel) }) {
                            Icon(Icons.Outlined.Assessment, contentDescription = stringResource(R.string.bill_report), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onNavigateToBudget) {
                            Icon(Icons.Outlined.AccountBalance, contentDescription = stringResource(R.string.bill_budget_tab), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val sd = state.selectedDay
                    val date = if (sd != null) {
                        DateUtils.toMillis(state.currentYearMonth, sd)
                    } else {
                        System.currentTimeMillis()
                    }
                    onNavigateToAdd(date)
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shape = MaterialTheme.shapes.large,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.bill_add), fontWeight = FontWeight.Medium) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Calendar (fixed)
                AnimatedVisibility(
                    visible = !showSearch,
                    enter = fadeIn(tween(120)) + expandVertically(tween(120)),
                    exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
                ) {
                    ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.bill_calendar), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.selectedDay != DateUtils.getDayOfMonth(System.currentTimeMillis()) || state.currentYearMonth != DateUtils.getCurrentYearMonth()) {
                                    TextButton(onClick = { viewModel.setMonth(DateUtils.getCurrentYearMonth()) }, modifier = Modifier.height(32.dp)) {
                                        Text(stringResource(R.string.common_today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                TextButton(onClick = { calendarExpanded = !calendarExpanded }) {
                                    Text(if (calendarExpanded) stringResource(R.string.bill_collapse) else stringResource(R.string.bill_expand), style = MaterialTheme.typography.labelMedium, color = AccentOrange)
                                }
                            }
                        }
                        CalendarView(
                            yearMonth = state.currentYearMonth,
                            dailyData = state.dailySummary,
                            selectedDay = state.selectedDay,
                            onDaySelected = { day ->
                                viewModel.setSelectedDay(if (state.selectedDay == day) null else day)
                            },
                            collapsed = !calendarExpanded,
                            onMonthChanged = { newMonth -> viewModel.setMonth(newMonth) }
                        )
                    }
                }

                // Filter chips (fixed)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val interactionSource = remember { MutableInteractionSource() }
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (selectedFilter == "ALL") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier.clickable(interactionSource = interactionSource, indication = null) { viewModel.setFilterType("ALL") }
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
                            modifier = Modifier.clickable(interactionSource = interactionSource2, indication = null) { viewModel.setFilterType("EXPENSE") }
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
                            modifier = Modifier.clickable(interactionSource = interactionSource3, indication = null) { viewModel.setFilterType("INCOME") }
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
                    IconButton(onClick = { viewModel.toggleFilterSheet() }) {
                        Icon(
                            Icons.Outlined.FilterList,
                            contentDescription = stringResource(R.string.bill_filter),
                            tint = if (state.currentFilter.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Bill list (scrollable)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
                        subtitle = stringResource(R.string.bill_start_recording),
                        tint = AccentOrange
                    )
                }
            } else {
                groupedBills.forEach { (_, bills) ->
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
                            onDelete = { billToDelete = bill }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
        }
    }
    
    // 删除确认弹窗
    billToDelete?.let { bill ->
        AppDialog(
            onDismissRequest = { billToDelete = null },
            title = { Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBill(bill.id)
                    billToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { billToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 高级筛选Sheet
    if (state.showFilterSheet) {
        BillFilterSheet(
            onDismiss = { viewModel.toggleFilterSheet() },
            onApply = { filter -> viewModel.applyFilter(filter) },
            currentFilter = state.currentFilter,
            expenseCategories = expenseCategoryItems + customExpenseCategories,
            incomeCategories = incomeCategoryItems + customIncomeCategories
        )
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
                            val catResId = getLocalizedCategoryName(bill.category)
                            Text(if (catResId != null) stringResource(catResId) else bill.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (bill.subCategory.isNotEmpty()) Text(" · ${bill.subCategory}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val merchantText = if (bill.merchant.isNotEmpty() && bill.location.isNotEmpty()) {
                            "${bill.merchant} · ${bill.location}"
                        } else if (bill.merchant.isNotEmpty()) {
                            bill.merchant
                        } else if (bill.location.isNotEmpty()) {
                            bill.location
                        } else null
                        if (merchantText != null) {
                            Text(merchantText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
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
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
