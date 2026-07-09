package com.palmnote.ui.settings


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.ui.bills.walletColorOptions
import com.palmnote.ui.bills.walletTypeResIds
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: WalletViewModel = hiltViewModel()
) {
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val totalBalance by viewModel.totalBalance.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingWallet by remember { mutableStateOf<Wallet?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var walletToDelete by remember { mutableStateOf<Wallet?>(null) }
    var detailWallet by remember { mutableStateOf<Wallet?>(null) }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.wallet_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingWallet = null; showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.wallet_add))
            }
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(stringResource(R.string.wallet_total_assets), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                CurrencyUtils.formatCurrency(totalBalance ?: 0.0),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.wallet_count_format, wallets.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            items(wallets.size, key = { wallets[it].id }) { index ->
                val wallet = wallets[index]
                WalletItem(
                    wallet = wallet,
                    onClick = { detailWallet = wallet },
                    onToggleEnabled = { viewModel.setEnabled(wallet.id, !wallet.isEnabled) }
                )
            }

            if (wallets.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = stringResource(R.string.wallet_empty),
                        subtitle = stringResource(R.string.wallet_empty_hint)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // Detail Dialog
    val detailWalletSnapshot = detailWallet
    if (detailWalletSnapshot != null) {
        WalletDetailDialog(
            wallet = detailWalletSnapshot,
            onEdit = { editingWallet = detailWallet; showAddDialog = true; detailWallet = null },
            onDelete = { walletToDelete = detailWallet; showDeleteDialog = true; detailWallet = null },
            onSetDefault = { viewModel.setDefault(detailWalletSnapshot.id) },
            onDismiss = { detailWallet = null }
        )
    }

    // Add/Edit BottomSheet
    if (showAddDialog) {
        WalletEditBottomSheet(
            wallet = editingWallet,
            onSave = { wallet ->
                if (editingWallet != null) viewModel.updateWallet(wallet) else viewModel.addWallet(wallet)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // Delete Dialog
    val walletToDeleteSnapshot = walletToDelete
    if (showDeleteDialog && walletToDeleteSnapshot != null) {
        AppDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.wallet_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.wallet_delete_confirm, walletToDeleteSnapshot.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteWallet(walletToDeleteSnapshot.id); showDeleteDialog = false }) {
                    Text(stringResource(R.string.delete), color = ErrorLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) }
            }
        )
    }
}

@Composable
private fun WalletItem(
    wallet: Wallet,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val walletColor = try {
        wallet.color.toComposeColor()
    } catch (_: Exception) { AccentOrange }

    ModuleCard(
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClick() }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(walletColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    wallet.icon.imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (wallet.isEnabled) walletColor else walletColor.copy(alpha = 0.4f)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        com.palmnote.ui.components.getLocalizedWalletDisplayName(wallet, context),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (wallet.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (wallet.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                        Text(stringResource(R.string.bill_default), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text(stringResource(walletTypeResIds[wallet.type] ?: R.string.wallet_type_other),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                CurrencyUtils.formatCurrency(wallet.currentBalance),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (wallet.currentBalance >= 0) MaterialTheme.colorScheme.onSurface else ErrorLight
            )

            Spacer(Modifier.width(8.dp))

            XiaomiSwitch(
                checked = wallet.isEnabled,
                onCheckedChange = { onToggleEnabled() },
                checkedTrackColor = LocalSwitchColor.current
            )
        }
    }
}

@Composable
private fun WalletDetailDialog(
    wallet: Wallet,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val walletColor = try {
        wallet.color.toComposeColor()
    } catch (_: Exception) { AccentOrange }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.palmnote.ui.components.getLocalizedWalletDisplayName(wallet, context), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large).background(walletColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(wallet.icon.imageVector, null, modifier = Modifier.size(28.dp), tint = walletColor)
                    }
                    Column {
                        Text(com.palmnote.ui.components.getLocalizedWalletDisplayName(wallet, context), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(walletTypeResIds[wallet.type] ?: R.string.wallet_type_other), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                DetailRow(stringResource(R.string.wallet_balance), CurrencyUtils.formatCurrency(wallet.currentBalance))
                DetailRow(stringResource(R.string.wallet_type), stringResource(walletTypeResIds[wallet.type] ?: R.string.wallet_type_other))
                if (wallet.bankName.isNotEmpty()) DetailRow(stringResource(R.string.wallet_bank), wallet.bankName)
                if (wallet.cardNumber.isNotEmpty()) DetailRow(stringResource(R.string.wallet_card_number), "****${wallet.cardNumber}")
                DetailRow(stringResource(R.string.wallet_initial_balance), CurrencyUtils.formatCurrency(wallet.initialBalance))
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = ErrorLight) }
                if (!wallet.isDefault) {
                    TextButton(onClick = { onSetDefault(); onDismiss() }) { Text(stringResource(R.string.account_book_set_default), color = StatusActive) }
                }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit), color = AccentOrange) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), fontWeight = FontWeight.Bold) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WalletEditBottomSheet(
    wallet: Wallet?,
    onSave: (Wallet) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(wallet?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(wallet?.type ?: "CASH") }
    var icon by remember { mutableStateOf(wallet?.icon ?: AppIcon.Payments) }
    var color by rememberSaveable { mutableStateOf(wallet?.color ?: "#4CAF50") }
    var bankName by rememberSaveable { mutableStateOf(wallet?.bankName ?: "") }
    var cardNumber by rememberSaveable { mutableStateOf(wallet?.cardNumber ?: "") }
    var initialBalance by rememberSaveable { mutableStateOf(wallet?.initialBalance?.toString() ?: "0") }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }

    val balanceInvalidMsg = stringResource(R.string.wallet_balance_invalid)
    val balanceNegativeMsg = stringResource(R.string.wallet_balance_negative)
    val typeOptions = listOf(
        "CASH" to stringResource(R.string.wallet_type_cash), "E_WALLET" to stringResource(R.string.wallet_type_e_wallet), "BANK_CARD" to stringResource(R.string.wallet_type_bank_card),
        "CREDIT_CARD" to stringResource(R.string.wallet_type_credit_card), "INVESTMENT" to stringResource(R.string.wallet_type_investment), "TOP_UP" to stringResource(R.string.wallet_type_top_up), "OTHER" to stringResource(R.string.wallet_type_other)
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(
            if (wallet != null) stringResource(R.string.wallet_edit) else stringResource(R.string.wallet_add),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = name, onValueChange = { name = it; nameError = null },
            label = { Text(stringResource(R.string.wallet_name)) }, modifier = Modifier.fillMaxWidth(),
            isError = nameError != null, supportingText = nameError?.let { { Text(it) } },
            shape = MaterialTheme.shapes.medium, singleLine = true
        )

        Text(stringResource(R.string.wallet_type), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            typeOptions.forEach { (t, label) ->
                FilterChip(
                    selected = type == t, onClick = { type = t },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentOrange.copy(alpha = 0.15f),
                        selectedLabelColor = AccentOrange
                    )
                )
            }
        }

        if (type == "BANK_CARD" || type == "CREDIT_CARD") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text(stringResource(R.string.wallet_bank)) },
                    modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, singleLine = true)
                OutlinedTextField(value = cardNumber, onValueChange = { cardNumber = it }, label = { Text(stringResource(R.string.wallet_card_last_four)) },
                    modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, singleLine = true)
            }
        }

        OutlinedTextField(
            value = initialBalance, onValueChange = { initialBalance = it; balanceError = null },
            label = { Text(stringResource(R.string.wallet_balance)) }, modifier = Modifier.fillMaxWidth(),
            prefix = { Text("¥") }, shape = MaterialTheme.shapes.medium, singleLine = true,
            isError = balanceError != null,
            supportingText = balanceError?.let { { Text(it) } },
        )

        Text(stringResource(R.string.wallet_icon), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        IconPickerGrid(selectedIcon = icon, onSelected = { icon = it })

        Text(stringResource(R.string.wallet_color), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            walletColorOptions.forEach { c ->
                val hex = "#%02X%02X%02X".format((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(c)
                        .clickable { color = hex },
                    contentAlignment = Alignment.Center
                ) {
                    if (color == hex) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        val walletNameRequired = stringResource(R.string.wallet_name_required)
        AppSaveButton(
            onClick = {
                if (name.isBlank()) { nameError = walletNameRequired; return@AppSaveButton }
                val balance = initialBalance.toDoubleOrNull()
                if (balance == null) {
                    balanceError = balanceInvalidMsg
                    return@AppSaveButton
                }
                if (balance < 0) {
                    balanceError = balanceNegativeMsg
                    return@AppSaveButton
                }
                onSave(Wallet(
                    id = wallet?.id ?: 0L, name = name.trim(), type = type, icon = icon, color = color,
                    bankName = bankName.trim(), cardNumber = cardNumber.trim(),
                    initialBalance = balance, currentBalance = wallet?.currentBalance ?: balance,
                    isDefault = wallet?.isDefault ?: false, isEnabled = wallet?.isEnabled ?: true,
                    sortOrder = wallet?.sortOrder ?: 0
                ))
            },
            enabled = name.isNotBlank()
        )
    }
}
