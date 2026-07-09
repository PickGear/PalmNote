package com.palmnote.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Receipt
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.palmnote.R
import com.palmnote.data.db.entity.Bill
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
    viewModel: BillDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${if (bill.type == "EXPENSE") "-" else "+"}${CurrencyUtils.formatCurrency(bill.amount)}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (bill.type == "EXPENSE") ExpenseRed else IncomeGreen
                            )
                            val catItem = remember(bill.category, bill.type) {
                                (if (bill.type == "EXPENSE") expenseCategoryItems else incomeCategoryItems).find { it.name == bill.category }
                            }
                            if (catItem != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(catItem.color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                        Icon(catItem.icon, null, tint = catItem.color, modifier = Modifier.size(20.dp))
                                    }
                                    Text(stringResource(getLocalizedCategoryName(catItem.name)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            } else {
                                Text(stringResource(getLocalizedCategoryName(bill.category)), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        DetailRow(stringResource(R.string.bill_type), if (bill.type == "EXPENSE") stringResource(R.string.bill_expense) else stringResource(R.string.bill_income))
                        DetailRow(stringResource(R.string.bill_date), DateUtils.formatDisplayDate(context, bill.date))
                        if (bill.merchant.isNotEmpty()) DetailRow(stringResource(R.string.bill_merchant), bill.merchant)
                        if (bill.paymentMethod.isNotEmpty()) DetailRow(stringResource(R.string.bill_payment_method), stringResource(getLocalizedPaymentMethod(bill.paymentMethod)))
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
