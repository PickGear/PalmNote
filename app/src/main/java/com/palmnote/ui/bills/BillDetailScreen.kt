package com.palmnote.ui.bills

import androidx.compose.foundation.background
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.PaymentMethod
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.PalmNoteApp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.palmnote.R
import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    billId: Long,
    onNavigateBack: () -> Unit = {},
    onNavigateToEdit: (Long) -> Unit = {},
    walletNames: Map<Long, String> = emptyMap(),
    viewModel: BillDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val billPresetOverrides by PalmNoteApp.instance.preferencesManager.presetCategoryOverrides
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val billCustomCfg by PalmNoteApp.instance.cachedCategoryConfigs
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val billCustomExpense = remember(billCustomCfg) {
        billCustomCfg.filter { it.type == "BILL_EXPENSE" }
            .map { com.palmnote.ui.components.CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
    }
    val billCustomIncome = remember(billCustomCfg) {
        billCustomCfg.filter { it.type == "BILL_INCOME" }
            .map { com.palmnote.ui.components.CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(billId) { viewModel.loadBill(billId) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadBill(billId)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
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
        val bill = state.bill
        if (bill != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${if (bill.type == BillType.EXPENSE) "-" else if (bill.type == BillType.TRANSFER) "" else "+"}${CurrencyUtils.formatCurrency(bill.amount.toMoney())}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (bill.type == BillType.EXPENSE) ExpenseRed else if (bill.type == BillType.TRANSFER) InfoBlue else IncomeGreen
                            )
                    val catItem = remember(bill.category, bill.type, billCustomExpense, billCustomIncome) {
                        val item = (if (bill.type == BillType.EXPENSE) expenseCategoryItems else incomeCategoryItems).find { it.name == bill.category }
                        item?.let { it.copy(color = ColorResolver.resolve(it.name, it.color)) }
                            ?: (if (bill.type == BillType.EXPENSE) billCustomExpense else billCustomIncome).find { it.name == bill.category }
                            ?: com.palmnote.ui.components.CategoryItem(bill.category, Icons.Outlined.Cancel, ErrorLight)
                    }
                    fun getBillDisplayName(cat: String): String {
                        val prefix = if (bill.type == BillType.EXPENSE) "EXPENSE_" else "INCOME_"
                        val overrideKey = "preset_$prefix$cat"
                        val json = billPresetOverrides[overrideKey]
                        if (json != null) {
                            try {
                                val obj = org.json.JSONObject(json)
                                if (obj.has("name")) return obj.getString("name")
                            } catch (_: Exception) {}
                        }
                        val resId = getLocalizedCategoryName(cat)
                        return if (resId != null) context.getString(resId) else cat
                    }
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
                        DetailRow(stringResource(R.string.bill_type), if (bill.type == BillType.TRANSFER) stringResource(R.string.bill_transfer) else if (bill.type == BillType.EXPENSE) stringResource(R.string.bill_expense) else stringResource(R.string.bill_income))
                        DetailRow(stringResource(R.string.bill_date), DateUtils.formatDisplayDate(context, bill.date))
                        if (bill.type == BillType.TRANSFER) {
                            DetailRow(stringResource(R.string.bill_wallet), walletNames[bill.walletId] ?: "")
                            DetailRow(stringResource(R.string.bill_transfer_to), walletNames[bill.toWalletId] ?: "")
                        } else {
                            bill.walletId?.let { DetailRow(stringResource(R.string.bill_wallet), walletNames[it] ?: "") }
                        }
                        if (bill.type != BillType.TRANSFER && bill.merchant.isNotEmpty()) DetailRow(stringResource(R.string.bill_merchant), bill.merchant)
                        if (bill.paymentMethod != PaymentMethod.OTHER) DetailRow(stringResource(R.string.bill_payment_method), stringResource(getLocalizedPaymentMethod(bill.paymentMethod.value)))
                        if (bill.note.isNotEmpty()) DetailRow(stringResource(R.string.bill_note), bill.note)
                        if (bill.subCategory.isNotEmpty()) DetailRow(stringResource(R.string.bill_sub_category), bill.subCategory)
                        if (bill.location.isNotEmpty()) DetailRow(stringResource(R.string.bill_location), bill.location)
                        if (bill.tags.isNotEmpty()) DetailRow(stringResource(R.string.bill_tags), bill.tags)
                        if (bill.images.isNotEmpty()) DetailRow(stringResource(R.string.bill_attachments), bill.images)
                    }
                }

                if (bill.isReimbursable) {
                    ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Receipt, null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.bill_reimbursement_status), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(if (bill.isReimbursed) stringResource(R.string.bill_reimbursed) else stringResource(R.string.bill_pending_reimbursement), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailRow(stringResource(R.string.bill_record_id), "${bill.id}")
                        DetailRow(stringResource(R.string.bill_created_at), DateUtils.formatDisplayDate(context, bill.createdAt))
                        DetailRow(stringResource(R.string.bill_updated_at), DateUtils.formatDisplayDate(context, bill.updatedAt))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

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
}
