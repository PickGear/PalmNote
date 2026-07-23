package com.palmnote.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.PalmNoteApp
import com.palmnote.R
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBillScreen(
    billId: Long? = null,
    selectedDate: Long? = null,
    onNavigateBack: () -> Unit = {},
    onBillDateSaved: (Long) -> Unit = {},
    onNavigateToWallet: () -> Unit = {},
    onNavigateToCategory: (String) -> Unit = {},
    viewModel: BillViewModel = simpleViewModel { PalmNoteApp.container.billViewModel() }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val isEditing = billId != null

    LaunchedEffect(billId) {
        if (billId != null) viewModel.initFormForEdit(billId) else viewModel.resetForm(selectedDate)
    }

    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) {
            onBillDateSaved(formState.date)
            onNavigateBack()
        }
    }

    val customExpenseCategories by viewModel.customExpenseCategories.collectAsStateWithLifecycle()
    val customIncomeCategories by viewModel.customIncomeCategories.collectAsStateWithLifecycle()
    val categories = remember(formState.type, customExpenseCategories, customIncomeCategories) {
        if (formState.type == "EXPENSE") expenseCategoryItems + customExpenseCategories else incomeCategoryItems + customIncomeCategories
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = if (isEditing) stringResource(R.string.bill_edit_bill) else stringResource(R.string.bill_add),
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Type Selector
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val expenseSelected = formState.type == "EXPENSE"
                    val incomeSelected = formState.type == "INCOME"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (expenseSelected) ExpenseRed else Color.Transparent)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { viewModel.updateForm { copy(type = "EXPENSE", category = "") } }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.bill_expense),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (expenseSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (expenseSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (incomeSelected) StatusActive else Color.Transparent)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { viewModel.updateForm { copy(type = "INCOME", category = "") } }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.bill_income),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (incomeSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (incomeSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Amount
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = stringResource(R.string.bill_amount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = formState.amount,
                        onValueChange = { viewModel.updateForm { copy(amount = it, amountError = null) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0.00") },
                        prefix = { Text("¥", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = formState.amountError != null,
                        supportingText = formState.amountError?.let { { Text(it) } },
                        shape = MaterialTheme.shapes.medium,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        singleLine = true
                    )
                }
            }

            // Category
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = stringResource(R.string.bill_category),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (formState.categoryError != null) {
                        Text(formState.categoryError ?: "", style = MaterialTheme.typography.bodySmall, color = ErrorLight)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    CategoryPicker(
                        selected = formState.category,
                        onSelected = { viewModel.updateForm { copy(category = it, categoryError = null) } },
                        categories = categories,
                        onManageCategories = {
                            val categoryType = if (formState.type == "EXPENSE") "BILL_EXPENSE" else "BILL_INCOME"
                            onNavigateToCategory(categoryType)
                        },
                        getDisplayName = { getLocalizedCategoryName(it)?.let { id -> context.getString(id) } ?: it }
                    )
                }
            }

            // Account/Wallet Selector
            item {
                val wallets by viewModel.wallets.collectAsStateWithLifecycle()

                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = stringResource(R.string.bill_wallet),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(wallets, key = { it.id }) { wallet ->
                            val isSelected = formState.walletId == wallet.id
                            val walletColor = try {
                                wallet.color.toComposeColor()
                            } catch (_: Exception) { AccentOrange }

                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 56.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(if (isSelected) walletColor else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { viewModel.updateForm { copy(walletId = wallet.id) } },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        wallet.icon.imageVector,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isSelected) Color.White else walletColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        getLocalizedWalletDisplayName(wallet, context),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 56.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { onNavigateToWallet() },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(stringResource(R.string.more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Show selected wallet balance
                    formState.walletId?.let { walletId ->
                        val selectedWallet = wallets.find { it.id == walletId }
                        if (selectedWallet != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${getLocalizedWalletDisplayName(selectedWallet, context)} ${stringResource(R.string.balance)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CurrencyUtils.formatCurrency(selectedWallet.currentBalance),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedWallet.currentBalance >= 0) StatusActive else ErrorLight
                                )
                            }
                        }
                    }
                }
            }

            // Date
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = stringResource(R.string.bill_date),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var showDatePicker by remember { mutableStateOf(false) }
                    OutlinedCard(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(DateUtils.formatDisplayYearDate(context, formState.date))
                            Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (showDatePicker) {
                        val timeZoneOffset = java.util.TimeZone.getDefault().getOffset(formState.date)
                        val state = rememberDatePickerState(initialSelectedDateMillis = formState.date + timeZoneOffset)
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            tonalElevation = 0.dp,
                            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                            confirmButton = {
                                TextButton(onClick = {
                                    state.selectedDateMillis?.let { utcDate ->
                                        val localDate = utcDate - java.util.TimeZone.getDefault().getOffset(utcDate)
                                        viewModel.updateForm { copy(date = localDate) }
                                    }
                                    showDatePicker = false
                                }) { Text(stringResource(R.string.confirm), color = AccentOrange, fontWeight = FontWeight.Bold) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) }
                            }
                        ) { DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background)) }
                    }
                }
            }

            // Merchant
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = stringResource(R.string.merchant),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = formState.merchant,
                        onValueChange = { viewModel.updateForm { copy(merchant = it) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.merchant_hint)) },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true
                    )
                }
            }

            // Location
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = stringResource(R.string.bill_location),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = formState.location,
                        onValueChange = { viewModel.updateForm { copy(location = it) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.location_hint)) },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true
                    )
                }
            }

            // Note
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = stringResource(R.string.bill_note),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = formState.note,
                        onValueChange = { viewModel.updateForm { copy(note = it) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.note_hint)) },
                        shape = MaterialTheme.shapes.medium,
                        maxLines = 3
                    )
                }
            }

            // Save Button
            item {
                Button(
                    onClick = { viewModel.saveBill() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !formState.isSaving,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (formState.type == "EXPENSE") ExpenseRed else StatusActive
                    )
                ) {
                    if (formState.isSaving) {
                        CircularProgressIndicator(Modifier.size(24.dp), Color.White, 2.dp)
                    } else {
                        Text(if (isEditing) stringResource(R.string.save_changes) else stringResource(R.string.save), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
