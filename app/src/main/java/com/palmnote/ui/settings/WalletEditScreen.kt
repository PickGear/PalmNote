package com.palmnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.domain.model.Money
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.model.toYuanString
import com.palmnote.domain.util.CurrencyUtils
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.data.db.entity.Wallet
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WalletEditScreen(
    walletId: Long? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: WalletViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isEditing = walletId != null && walletId > 0

    // Load wallet for editing
    val existingWallet by remember(walletId) {
        if (isEditing) viewModel.getWalletByIdFlow(walletId!!)
        else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("CASH") }
    var icon by rememberSaveable { mutableStateOf(com.palmnote.ui.theme.AppIcon.Payments) }
    var color by rememberSaveable { mutableStateOf("#4CAF50") }
    var bankName by rememberSaveable { mutableStateOf("") }
    var cardNumber by rememberSaveable { mutableStateOf("") }
    var initialBalance by rememberSaveable { mutableStateOf("0") }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var balanceError by rememberSaveable { mutableStateOf<String?>(null) }
    var initialized by rememberSaveable { mutableStateOf(false) }

    // Initialize form when wallet is loaded
    LaunchedEffect(existingWallet) {
        if (isEditing && existingWallet != null && !initialized) {
            val w = existingWallet!!
            name = w.name
            type = w.type
            icon = w.icon
            color = w.color
            bankName = w.bankName
            cardNumber = w.cardNumber
            initialBalance = w.initialBalance.toYuanString()
            initialized = true
        }
    }

    val balanceInvalidMsg = stringResource(R.string.wallet_balance_invalid)
    val balanceNegativeMsg = stringResource(R.string.wallet_balance_negative)
    val walletNameRequired = stringResource(R.string.wallet_name_required)

    val typeOptions = listOf(
        "CASH" to stringResource(R.string.wallet_type_cash),
        "E_WALLET" to stringResource(R.string.wallet_type_e_wallet),
        "BANK_CARD" to stringResource(R.string.wallet_type_bank_card),
        "CREDIT_CARD" to stringResource(R.string.wallet_type_credit_card),
        "INVESTMENT" to stringResource(R.string.wallet_type_investment),
        "TOP_UP" to stringResource(R.string.wallet_type_top_up),
        "OTHER" to stringResource(R.string.wallet_type_other)
    )

    fun save() {
        if (name.isBlank()) { nameError = walletNameRequired; return }
        val balance = Money.parse(initialBalance)?.cents
        if (balance == null) { balanceError = balanceInvalidMsg; return }
        if (balance < 0) { balanceError = balanceNegativeMsg; return }
        val wallet = Wallet(
            id = existingWallet?.id ?: 0L,
            name = name.trim(),
            type = type,
            icon = icon,
            color = color,
            bankName = bankName.trim(),
            cardNumber = cardNumber.trim(),
            initialBalance = balance,
            currentBalance = existingWallet?.let { it.currentBalance + (balance - it.initialBalance) } ?: balance,            isDefault = existingWallet?.isDefault ?: false,
            isEnabled = existingWallet?.isEnabled ?: true,
            sortOrder = existingWallet?.sortOrder ?: 0
        )
        if (isEditing) viewModel.updateWallet(wallet) else viewModel.addWallet(wallet)
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = if (isEditing) stringResource(R.string.wallet_edit) else stringResource(R.string.wallet_add),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = name.isNotBlank()) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ══════════════════════════════════════�?
            // 基本信息
            // ══════════════════════════════════════�?
            ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                SectionHeader(Icons.Outlined.Info, stringResource(R.string.wallet_basic_info))
                Spacer(modifier = Modifier.height(12.dp))

                // 名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text(stringResource(R.string.wallet_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 类型选择
                Text(stringResource(R.string.wallet_type), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    typeOptions.forEach { (t, label) ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentOrange.copy(alpha = 0.15f),
                                selectedLabelColor = AccentOrange
                            )
                        )
                    }
                }

                // 银行信息（条件显示）
                if (type == "BANK_CARD" || type == "CREDIT_CARD") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bankName,
                            onValueChange = { bankName = it },
                            label = { Text(stringResource(R.string.wallet_bank)) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = cardNumber,
                            onValueChange = { cardNumber = it },
                            label = { Text(stringResource(R.string.wallet_card_last_four)) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true
                        )
                    }
                }
            }

            // ══════════════════════════════════════�?
            // 余额
            // ══════════════════════════════════════�?
            ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                SectionHeader(Icons.Outlined.AccountBalance, stringResource(R.string.wallet_balance))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = { initialBalance = it; balanceError = null },
                    label = { Text(stringResource(R.string.wallet_initial_balance)) },
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text(stringResource(R.string.currency_symbol)) },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    isError = balanceError != null,
                    supportingText = balanceError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (isEditing && existingWallet != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.wallet_current_balance) + ": " + com.palmnote.domain.util.CurrencyUtils.formatCurrency(existingWallet!!.currentBalance.toMoney()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ══════════════════════════════════════�?
            // 外观（图�?+ 颜色�?
            // ══════════════════════════════════════�?
            ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                SectionHeader(Icons.Outlined.Palette, stringResource(R.string.wallet_appearance))
                Spacer(modifier = Modifier.height(12.dp))

                // 预览�?
                val walletColor = try { color.toComposeColor() } catch (_: Exception) { AccentOrange }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large).background(walletColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon.imageVector, null, modifier = Modifier.size(28.dp), tint = walletColor)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 图标选择
                Text(stringResource(R.string.wallet_icon), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                IconPickerGrid(selectedIcon = icon, onSelected = { icon = it })

                Spacer(modifier = Modifier.height(8.dp))

                // 颜色选择
                Text(stringResource(R.string.wallet_color), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                ColorPicker(selectedColor = color, onColorSelected = { color = it })
            }

            // ══════════════════════════════════════�?
            // 保存按钮
            // ══════════════════════════════════════�?
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = name.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isEditing) stringResource(R.string.save_changes) else stringResource(R.string.wallet_add),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
