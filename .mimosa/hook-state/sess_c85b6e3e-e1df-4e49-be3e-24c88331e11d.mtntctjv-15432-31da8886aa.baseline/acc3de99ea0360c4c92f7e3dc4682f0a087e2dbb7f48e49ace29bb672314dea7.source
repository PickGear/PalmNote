package com.palmnote.ui.bills
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.domain.model.BillType

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.domain.model.toMoney
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
    viewModel: BillViewModel = hiltViewModel()
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
    val categoryUsageCounts by viewModel.categoryUsageCounts.collectAsStateWithLifecycle()
    val presetOverrides by viewModel.presetCategoryOverrides
        .collectAsStateWithLifecycle()
    val categories = remember(formState.type, customExpenseCategories, customIncomeCategories, categoryUsageCounts, presetOverrides) {
        val isExpense = formState.type == BillType.EXPENSE
        val rawPresets = if (isExpense) expenseCategoryItems else incomeCategoryItems
        val prefix = if (isExpense) "EXPENSE_" else "INCOME_"
        val filteredPresets = rawPresets.filter { item ->
            val key = "preset_$prefix${item.name}"
            val json = presetOverrides[key]
            if (json != null) {
                try {
                    org.json.JSONObject(json).optBoolean("enabled", true)
                } catch (_: Exception) { true }
            } else true
        }.map { item ->
            val resolved = com.palmnote.ui.theme.ColorResolver.resolve(item.name, item.color)
            if (resolved != item.color) item.copy(color = resolved) else item
        }
        val base = filteredPresets + if (isExpense) customExpenseCategories else customIncomeCategories
        base.sortedByDescending { categoryUsageCounts[it.name] ?: 0 }
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
                    val expenseSelected = formState.type == BillType.EXPENSE
                    val incomeSelected = formState.type == BillType.INCOME
                    val transferSelected = formState.type == BillType.TRANSFER
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (expenseSelected) ExpenseRed else Color.Transparent)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { viewModel.updateForm { copy(type = BillType.EXPENSE, category = "", toWalletId = null) } }
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
                            ) { viewModel.updateForm { copy(type = BillType.INCOME, category = "", toWalletId = null) } }
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (transferSelected) InfoBlue else Color.Transparent)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { viewModel.updateForm { copy(type = BillType.TRANSFER, category = "", toWalletId = null) } }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.bill_transfer),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (transferSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (transferSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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
            items(if (formState.type != BillType.TRANSFER) 1 else 0) {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = stringResource(R.string.bill_category),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (formState.categoryError != null) {
                        Text(formState.categoryError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = ErrorLight)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    CategoryPicker(
                        selected = formState.category,
                        onSelected = { viewModel.updateForm { copy(category = it, categoryError = null) } },
                        categories = categories,
                        onManageCategories = {
                            val categoryType = if (formState.type == BillType.EXPENSE) "BILL_EXPENSE" else "BILL_INCOME"
                            onNavigateToCategory(categoryType)
                        },
                        getDisplayName = { key ->
                            resolvePresetCategoryName(presetOverrides, key, formState.type.value, context)
                        }
                    )
                }
            }
            
            // Account/Wallet Selector
            item {
                val wallets by viewModel.wallets.collectAsStateWithLifecycle()

                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = if (formState.type == BillType.TRANSFER) stringResource(R.string.bill_transfer_from) else stringResource(R.string.bill_wallet),
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
                                    text = CurrencyUtils.formatCurrency(context, selectedWallet.currentBalance.toMoney()),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedWallet.currentBalance >= 0) StatusActive else ErrorLight
                                )
                            }
                        }
                    }
                    if (formState.type == BillType.TRANSFER) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            IconButton(onClick = {
                                val temp = formState.walletId
                                viewModel.updateForm { copy(walletId = formState.toWalletId, toWalletId = temp) }
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.SwapVert, contentDescription = stringResource(R.string.bill_transfer), tint = InfoBlue, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.bill_transfer_to),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(wallets, key = { it.id }) { wallet ->
                                val isSelected = formState.toWalletId == wallet.id
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
                                        ) { viewModel.updateForm { copy(toWalletId = wallet.id) } },
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
                        }
                        formState.toWalletId?.let { walletId ->
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
                                        text = CurrencyUtils.formatCurrency(context, selectedWallet.currentBalance.toMoney()),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selectedWallet.currentBalance >= 0) StatusActive else ErrorLight
                                    )
                                }
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
                    var showTimePicker by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedCard(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(DateUtils.formatDisplayDate(context, formState.date))
                                Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        OutlinedCard(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(DateUtils.formatTimeOnly(formState.date))
                                Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
                                        val newDateMidnight = utcDate - java.util.TimeZone.getDefault().getOffset(utcDate)
                                        val preservedDate = DateUtils.preserveTimeOfDay(formState.date, newDateMidnight)
                                        viewModel.updateForm { copy(date = preservedDate) }
                                    }
                                    showDatePicker = false
                                }) { Text(stringResource(R.string.confirm), color = AccentOrange, fontWeight = FontWeight.Bold) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) }
                            }
                        ) { DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background)) }
                    }

                    if (showTimePicker) {
                        val ldt = java.time.Instant.ofEpochMilli(formState.date)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                        val timeState = rememberTimePickerState(
                            initialHour = ldt.hour,
                            initialMinute = ldt.minute,
                            is24Hour = true
                        )
                        AlertDialog(
                            onDismissRequest = { showTimePicker = false },
                            containerColor = MaterialTheme.colorScheme.background,
                            confirmButton = {
                                TextButton(onClick = {
                                    val newLdt = ldt.withHour(timeState.hour).withMinute(timeState.minute)
                                    val newDate = newLdt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    viewModel.updateForm { copy(date = newDate) }
                                    showTimePicker = false
                                }) { Text(stringResource(R.string.confirm), color = AccentOrange, fontWeight = FontWeight.Bold) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTimePicker = false }) {
                                    Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
                                }
                            },
                            text = { TimePicker(state = timeState) }
                        )
                    }
                }
            }

            // Merchant + Location
            items(if (formState.type != BillType.TRANSFER) 1 else 0) {
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
                    Spacer(modifier = Modifier.height(8.dp))
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

            // Image Upload
            item {
                val accentColor = if (formState.type == BillType.TRANSFER) InfoBlue
                    else categories.find { it.name == formState.category }?.color ?: AccentOrange
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    ImageGridPicker(
                        title = stringResource(R.string.bill_image_section),
                        images = formState.images.toImageList(),
                        accentColor = accentColor,
                        hint = stringResource(R.string.bill_image_hint),
                        onAddImage = { viewModel.addImage(it) },
                        onRemoveImage = { viewModel.removeImage(it) },
                        onReorderImages = { from, to -> viewModel.reorderImages(from, to) }
                    )
                }
            }

            // Reimbursable Toggle
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.bill_reimbursable),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.bill_reimbursable_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        CapsuleSwitch(
                            checked = formState.isReimbursable,
                            onCheckedChange = { viewModel.updateForm { copy(isReimbursable = it) } }
                        )
                    }
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
                        containerColor = when (formState.type) {
                            BillType.EXPENSE -> ExpenseRed
                            BillType.TRANSFER -> InfoBlue
                            else -> StatusActive
                        }
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
