package com.palmnote.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.model.Money
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.model.toYuanString
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.CapsuleSwitch
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.DatePickerField
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.components.ModuleCard
import com.palmnote.ui.theme.AccentOrange
import com.palmnote.ui.theme.ExpenseRed
import com.palmnote.ui.theme.IncomeGreen
import com.palmnote.ui.theme.billTint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReimbursementScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (Long) -> Unit = {},
    viewModel: ReimbursementViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val presetOverrides by viewModel.presetCategoryOverrides.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var billToMark by remember { mutableStateOf<Bill?>(null) }
    var billToRevoke by remember { mutableStateOf<Bill?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val reimbursementError by viewModel.errorMessage.collectAsStateWithLifecycle()
    // 失败必须先关掉标记弹窗：AlertDialog 是独立窗口，会把 Scaffold 的 Snackbar 盖住
    LaunchedEffect(reimbursementError) {
        reimbursementError?.let {
            billToMark = null
            snackbarHostState.showSnackbar(it)
            viewModel.consumeErrorMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.reimbursement_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        val list = if (tab == 0) state.pending else state.done
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ReimbursementSummaryCard(
                    pendingTotal = state.pendingTotal,
                    pendingCount = state.pendingCount,
                    receivedTotal = state.receivedTotal
                )
            }
            item {
                ReimbursementTabs(selected = tab, onSelect = { tab = it })
            }

            if (list.isEmpty()) {
                item {
                    if (tab == 0) {
                        EmptyState(
                            icon = Icons.Outlined.Receipt,
                            title = stringResource(R.string.reimbursement_empty_pending),
                            subtitle = stringResource(R.string.reimbursement_empty_pending_hint),
                            tint = AccentOrange
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.Receipt,
                            title = stringResource(R.string.reimbursement_empty_done),
                            subtitle = stringResource(R.string.reimbursement_empty_done_hint),
                            tint = IncomeGreen
                        )
                    }
                }
            } else {
                items(list, key = { it.id }) { bill ->
                    ReimbursementListItem(
                        bill = bill,
                        categoryName = resolvePresetCategoryName(
                            presetOverrides, bill.category, bill.type.value, context
                        ),
                        onDetail = { onNavigateToDetail(bill.id) },
                        onMark = if (tab == 0) ({ billToMark = bill }) else null,
                        onRevoke = if (tab == 1) ({ billToRevoke = bill }) else null
                    )
                }
            }
        }
    }

    billToMark?.let { expense ->
        ReimbursementMarkDialog(
            expense = expense,
            isSaving = isSaving,
            onDismiss = { billToMark = null },
            onConfirm = { amount, date, createIncome ->
                viewModel.markReimbursed(
                    expense = expense,
                    amount = amount,
                    date = date,
                    createIncome = createIncome
                ) { billToMark = null }
            }
        )
    }

    billToRevoke?.let { expense ->
        AlertDialog(
            onDismissRequest = { billToRevoke = null },
            title = { Text(stringResource(R.string.reimbursement_revoke_title)) },
            text = { Text(stringResource(R.string.reimbursement_revoke_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revoke(expense.id)
                    billToRevoke = null
                }) {
                    Text(stringResource(R.string.reimbursement_revoke))
                }
            },
            dismissButton = {
                TextButton(onClick = { billToRevoke = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ReimbursementSummaryCard(
    pendingTotal: Long,
    pendingCount: Int,
    receivedTotal: Long
) {
    val context = LocalContext.current
    ModuleCard(tint = billTint(), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.reimbursement_pending_total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = CurrencyUtils.formatCurrency(context, pendingTotal.toMoney()),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.reimbursement_pending_count, pendingCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.reimbursement_received_total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = CurrencyUtils.formatCurrency(context, receivedTotal.toMoney()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReimbursementTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(
        stringResource(R.string.reimbursement_tab_pending),
        stringResource(R.string.reimbursement_tab_done)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selected == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReimbursementListItem(
    bill: Bill,
    categoryName: String,
    onDetail: () -> Unit,
    onMark: (() -> Unit)?,
    onRevoke: (() -> Unit)?
) {
    val context = LocalContext.current
    val remaining = (bill.amount - bill.reimbursedAmount).coerceAtLeast(0L)
    val isPartial = bill.reimbursedAmount > 0 && remaining > 0

    ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onDetail)
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(DateUtils.formatDisplayDate(context, bill.date))
                        if (bill.note.isNotBlank()) append(" · ").append(bill.note)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (isPartial) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.reimbursement_partial,
                            CurrencyUtils.formatCurrency(context, bill.reimbursedAmount.toMoney()),
                            CurrencyUtils.formatCurrency(context, bill.amount.toMoney())
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentOrange
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "-" + CurrencyUtils.formatCurrency(context, bill.amount.toMoney()),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseRed
                )
                if (onMark != null && remaining > 0) {
                    Text(
                        text = stringResource(
                            R.string.reimbursement_remaining_short,
                            CurrencyUtils.formatCurrency(context, remaining.toMoney())
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 用 ?.let 而非 `if (x != null)` 再直接读：reimbursedDate 声明在 :core 模块，
                    // 跨模块的公共 val 属性 Kotlin 不允许智能转换，直接读会编译失败
                    bill.reimbursedDate?.let { reimbursedDate ->
                        Text(
                            text = DateUtils.formatDisplayDate(context, reimbursedDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (onMark != null || onRevoke != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onMark != null) {
                    TextButton(onClick = onMark) {
                        Text(stringResource(R.string.reimbursement_mark))
                    }
                }
                if (onRevoke != null) {
                    TextButton(onClick = onRevoke) {
                        Text(
                            text = stringResource(R.string.reimbursement_revoke),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * 标记报销对话框（报销管理页与账单详情页共用）。
 *
 * 默认勾选「同时记一笔报销收入」—— 只改标记会让当天收支看起来凭空多花了钱，
 * 补记收入才能让「支出 - 报回」在账面上自洽。
 */
@Composable
fun ReimbursementMarkDialog(
    expense: Bill,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, date: Long, createIncome: Boolean) -> Unit
) {
    val context = LocalContext.current
    val remaining = (expense.amount - expense.reimbursedAmount).coerceAtLeast(0L)
    var amountText by remember(expense.id) { mutableStateOf(remaining.toYuanString()) }
    var date by remember(expense.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var createIncome by remember(expense.id) { mutableStateOf(true) }

    val parsed = Money.parse(amountText)?.cents
    val errorText: String? = when {
        amountText.isBlank() -> stringResource(R.string.reimbursement_error_amount)
        parsed == null -> stringResource(R.string.reimbursement_error_amount)
        parsed <= 0 -> stringResource(R.string.reimbursement_error_amount)
        parsed > remaining -> stringResource(
            R.string.reimbursement_error_exceed,
            CurrencyUtils.formatCurrency(context, remaining.toMoney())
        )
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reimbursement_mark_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.reimbursement_remaining,
                            CurrencyUtils.formatCurrency(context, remaining.toMoney())
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyUtils.formatCurrency(context, expense.amount.toMoney()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseRed
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.reimbursement_amount_label)) },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = if (errorText != null) {
                        { Text(errorText) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text(
                        text = stringResource(R.string.reimbursement_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    DatePickerField(selectedDate = date, onDateSelected = { date = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.reimbursement_record_income),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.reimbursement_record_income_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    CapsuleSwitch(checked = createIncome, onCheckedChange = { createIncome = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cents = Money.parse(amountText)?.cents ?: return@TextButton
                    onConfirm(cents, date, createIncome)
                },
                enabled = !isSaving && errorText == null
            ) {
                Text(stringResource(R.string.reimbursement_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
