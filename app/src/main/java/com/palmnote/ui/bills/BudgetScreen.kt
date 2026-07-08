package com.palmnote.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.R
import com.palmnote.data.db.entity.Budget
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BillViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.budget_title),
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current Budget
            item {
                ModuleCard(tint = billTint(), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.budget_month_format, DateUtils.formatDisplayMonth(context, state.currentYearMonth)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    state.budget?.let { budget ->
                        val remaining = budget.totalBudget - state.monthlyExpense

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(stringResource(R.string.budget_total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    CurrencyUtils.formatCurrency(budget.totalBudget),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.budget_used), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    CurrencyUtils.formatCurrency(state.monthlyExpense),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.monthlyExpense > budget.totalBudget) ErrorLight else AccentOrange
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        ProgressBar(
                            progress = state.budgetUsagePercent,
                            color = when {
                                state.budgetUsagePercent > 1f -> ErrorLight
                                state.budgetUsagePercent > 0.8f -> AccentOrange
                                else -> StatusActive
                            },
                            height = 10.dp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${(state.budgetUsagePercent * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (remaining >= 0) "${stringResource(R.string.budget_remaining)} ${CurrencyUtils.formatCurrency(remaining)}"
                                else "${stringResource(R.string.budget_over)} ${CurrencyUtils.formatCurrency(-remaining)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (remaining >= 0) StatusActive else ErrorLight
                            )
                        }
                    } ?: run {
                        Text(
                            text = stringResource(R.string.budget_not_set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                    ) {
                        Icon(
                            if (state.budget != null) Icons.Outlined.Edit else Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.budget != null) stringResource(R.string.budget_modify) else stringResource(R.string.budget_set), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tips
            item {
                ModuleCard(tint = goalTint(), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.budget_tips),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.budget_tips_content),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Edit Budget Dialog
    if (showEditDialog) {
        var amount by remember { mutableStateOf(state.budget?.totalBudget?.toString() ?: "") }

        AppDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.budget_set)) },
            text = {
                Column {
                    Text(
                        text = DateUtils.formatDisplayMonth(context, state.currentYearMonth),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0.00") },
                        prefix = { Text("¥") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val budgetValue = amount.toDoubleOrNull() ?: 0.0
                        if (budgetValue > 0) {
                            val existing = state.budget
                            viewModel.saveBudget(
                                if (existing != null) {
                                    existing.copy(totalBudget = budgetValue)
                                } else {
                                    Budget(
                                        yearMonth = state.currentYearMonth,
                                        totalBudget = budgetValue
                                    )
                                }
                            )
                        }
                        showEditDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm), color = AccentOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
