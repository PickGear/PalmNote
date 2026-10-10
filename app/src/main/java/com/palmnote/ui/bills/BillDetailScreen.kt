package com.palmnote.ui.bills

import androidx.compose.foundation.background
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.PaymentMethod
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import com.palmnote.app.R
import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun BillDetailScreen(
    billId: Long,
    onNavigateBack: () -> Unit = {},
    onNavigateToEdit: (Long) -> Unit = {},
    onNavigateToBill: (Long) -> Unit = {},
    viewModel: BillDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val billPresetOverrides by viewModel.presetCategoryOverrides
        .collectAsStateWithLifecycle()
    val billCustomCfg by viewModel.categoryConfigs
        .collectAsStateWithLifecycle()
    val walletNames by viewModel.walletNames
        .collectAsStateWithLifecycle()
    val billCustomExpense = remember(billCustomCfg) {
        billCustomCfg.filter { it.type == "BILL_EXPENSE" }
            .map { com.palmnote.ui.components.CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
    }
    val billCustomIncome = remember(billCustomCfg) {
        billCustomCfg.filter { it.type == "BILL_INCOME" }
            .map { com.palmnote.ui.components.CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val linkedIncome by viewModel.linkedIncome.collectAsStateWithLifecycle()
    val isReimbursementSaving by viewModel.isReimbursementSaving.collectAsStateWithLifecycle()
    val coveredExpenses by viewModel.coveredExpenses.collectAsStateWithLifecycle()
    val reimbursementError by viewModel.errorMessage.collectAsStateWithLifecycle()
    LaunchedEffect(billId) { viewModel.loadBill(billId) }
    // 报销落库失败：早先既没 catch（直接崩）也没提示，现在统一弹一条 Snackbar
    LaunchedEffect(reimbursementError) {
        reimbursementError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeErrorMessage()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadBill(billId)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableIntStateOf(-1) }
    val bill = state.bill
    val imageList = remember(bill) { bill?.images?.toImageList().orEmpty() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.bill_detail_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete), tint = ExpenseRed)
                    }
                }
            )
        }
    ) { padding ->
        if (bill != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.clip(MaterialTheme.shapes.small).background(
                                    if (bill.type == BillType.EXPENSE) ExpenseRed.copy(alpha = 0.1f)
                                    else if (bill.type == BillType.TRANSFER) InfoBlue.copy(alpha = 0.1f)
                                    else IncomeGreen.copy(alpha = 0.1f)
                                ).padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${if (bill.type == BillType.EXPENSE) "-" else if (bill.type == BillType.TRANSFER) "" else "+"}${
                                        CurrencyUtils.formatCurrency(context, bill.amount.toMoney())
                                    }",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (bill.type == BillType.EXPENSE) ExpenseRed else if (bill.type == BillType.TRANSFER) InfoBlue else IncomeGreen
                                )
                            }
                    val catItem = remember(bill.category, bill.type, billCustomExpense, billCustomIncome) {
                        val item = (if (bill.type == BillType.EXPENSE) expenseCategoryItems else incomeCategoryItems).find { it.name == bill.category }
                        item?.let { it.copy(color = ColorResolver.resolve(it.name, it.color)) }
                            ?: (if (bill.type == BillType.EXPENSE) billCustomExpense else billCustomIncome).find { it.name == bill.category }
                            ?: com.palmnote.ui.components.CategoryItem(bill.category, Icons.Outlined.Cancel, ErrorLight)
                    }
                    fun getBillDisplayName(cat: String): String =
                        resolvePresetCategoryName(billPresetOverrides, cat, bill.type.value, context)
                    if (bill.type == BillType.TRANSFER) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(InfoBlue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.SwapVert, null, tint = InfoBlue, modifier = Modifier.size(20.dp))
                            }
                            Text(stringResource(R.string.bill_transfer), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = InfoBlue)
                        }
                    } else {
                        val displayName = getBillDisplayName(catItem.name)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(catItem.color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Icon(catItem.icon, null, tint = catItem.color, modifier = Modifier.size(20.dp))
                            }
                            Text(displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                        }
                        HorizontalDivider()
                        DetailRow(stringResource(R.string.bill_date), DateUtils.formatBillDate(context, bill.date))
                        if (bill.type == BillType.TRANSFER) {
                            DetailRow(stringResource(R.string.bill_wallet), walletNames[bill.walletId].orEmpty())
                            DetailRow(stringResource(R.string.bill_transfer_to), walletNames[bill.toWalletId].orEmpty())
                        } else {
                            bill.walletId?.let { DetailRow(stringResource(R.string.bill_wallet), walletNames[it].orEmpty()) }
                        }
                        if (bill.type != BillType.TRANSFER && bill.merchant.isNotEmpty()) DetailRow(stringResource(R.string.bill_merchant), bill.merchant)
                        if (bill.paymentMethod != PaymentMethod.OTHER) DetailRow(stringResource(R.string.bill_payment_method), stringResource(getLocalizedPaymentMethod(bill.paymentMethod.value)))
                        if (bill.note.isNotEmpty()) DetailRow(stringResource(R.string.bill_note), bill.note)
                        if (bill.subCategory.isNotEmpty()) DetailRow(stringResource(R.string.bill_sub_category), bill.subCategory)
                        if (bill.location.isNotEmpty()) DetailRow(stringResource(R.string.bill_location), bill.location)
                        if (bill.tags.isNotEmpty()) DetailRow(stringResource(R.string.bill_tags), bill.tags)
                        if (imageList.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                stringResource(R.string.bill_image_section),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                imageList.forEachIndexed { index, imagePath ->
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .clickable { previewIndex = index }
                                    ) {
                                        AsyncImage(
                                            model = File(imagePath),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.bill_created_at),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = DateUtils.formatDisplayDateTime(bill.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.bill_updated_at),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = DateUtils.formatDisplayDateTime(bill.updatedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 只有支出才有报销这回事：历史数据里可能残留「收入 + 可报销」的组合，一并排掉
                if (bill.isReimbursable && bill.type == BillType.EXPENSE) {
                    ReimbursementSection(
                        bill = bill,
                        linkedIncome = linkedIncome,
                        isSaving = isReimbursementSaving,
                        onMark = { amount, date, createIncome ->
                            viewModel.markReimbursed(amount, date, createIncome)
                        },
                        onRevoke = { viewModel.revokeReimbursement() },
                        onOpenIncome = onNavigateToBill
                    )
                } else if (coveredExpenses.isNotEmpty()) {
                    ReimbursementCoveredSection(
                        expenses = coveredExpenses,
                        onOpenExpense = onNavigateToBill
                    )
                }

                Button(
                    onClick = { onNavigateToEdit(billId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Outlined.Edit, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bill_edit_bill))
                }
            }
        }
    }
    
    // 删除确认弹窗
    if (showDeleteDialog) {
        AppDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBill(billId)
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    AppImagePreview(
        showPreview = previewIndex in imageList.indices,
        imageList = imageList,
        previewIndex = previewIndex,
        onClose = { previewIndex = -1 },
        onSaveImage = { com.palmnote.ui.components.saveImageToGallery(context, imageList[it]) }
    )
}

/**
 * 详情页的报销区块：状态 + 关联收入 + 标记/撤销。
 *
 * 只在账单开了「可报销」时由调用方渲染；没开报销的普通支出不会出现这一块。
 * 状态文案走金额而不是布尔：部分报销时「已报 500 / 1000」比「待报销」有用得多。
 */
@Composable
private fun ReimbursementSection(
    bill: Bill,
    linkedIncome: Bill?,
    isSaving: Boolean,
    onMark: (amount: Long, date: Long, createIncome: Boolean) -> Unit,
    onRevoke: () -> Unit,
    onOpenIncome: (Long) -> Unit
) {
    val context = LocalContext.current
    val remaining = (bill.amount - bill.reimbursedAmount).coerceAtLeast(0L)
    var showMarkDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }

    val statusText = when {
        bill.reimbursedAmount <= 0L -> stringResource(R.string.bill_pending_reimbursement)
        remaining > 0L -> stringResource(
            R.string.reimbursement_partial,
            CurrencyUtils.formatCurrency(context, bill.reimbursedAmount.toMoney()),
            CurrencyUtils.formatCurrency(context, bill.amount.toMoney())
        )
        else -> stringResource(
            R.string.reimbursement_done_amount,
            CurrencyUtils.formatCurrency(context, bill.reimbursedAmount.toMoney())
        )
    }

    ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Receipt, null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.bill_reimbursement_status),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            linkedIncome?.let { income ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onOpenIncome(income.id) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.reimbursement_linked_income),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+" + CurrencyUtils.formatCurrency(context, income.amount.toMoney()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = IncomeGreen
                    )
                }
            }

            if (remaining > 0L || bill.reimbursedAmount > 0L) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (remaining > 0L) {
                        Button(onClick = { showMarkDialog = true }, enabled = !isSaving) {
                            Text(stringResource(R.string.reimbursement_mark))
                        }
                    }
                    if (bill.reimbursedAmount > 0L) {
                        TextButton(onClick = { showRevokeDialog = true }, enabled = !isSaving) {
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

    if (showMarkDialog) {
        ReimbursementMarkDialog(
            expense = bill,
            isSaving = isSaving,
            onDismiss = { showMarkDialog = false },
            onConfirm = { amount, date, createIncome ->
                showMarkDialog = false
                onMark(amount, date, createIncome)
            }
        )
    }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text(stringResource(R.string.reimbursement_revoke_title)) },
            text = { Text(stringResource(R.string.reimbursement_revoke_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRevokeDialog = false
                    onRevoke()
                }) {
                    Text(stringResource(R.string.reimbursement_revoke))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 报销收入侧的区块：这笔收入分摊到了哪些支出上。
 *
 * 关联只存在支出一侧（`reimbursedByBillId`），这里靠反查补上另一半视角，
 * 否则从收入看不出钱是替谁垫的。点击某条支出可直接跳过去。
 */
@Composable
private fun ReimbursementCoveredSection(
    expenses: List<Bill>,
    onOpenExpense: (Long) -> Unit
) {
    val context = LocalContext.current
    ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Receipt, null, tint = IncomeGreen, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.reimbursement_covered_expenses, expenses.size),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        expenses.forEach { expense ->
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onOpenExpense(expense.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = expense.note.ifBlank { expense.category },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        Text(
                            text = DateUtils.formatDisplayDate(context, expense.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 该笔支出从这条收入拿到的金额（部分报销时小于支出原额）
                    Text(
                        text = "+" + CurrencyUtils.formatCurrency(
                            context,
                            expense.reimbursedAmount.coerceAtMost(expense.amount).toMoney()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = IncomeGreen
                    )
                }
            }
        }
    }
}
