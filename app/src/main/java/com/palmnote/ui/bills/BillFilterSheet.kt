package com.palmnote.ui.bills

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.R
import com.palmnote.ui.components.AppBottomSheet
import com.palmnote.ui.components.CategoryItem
import com.palmnote.ui.components.CategoryPicker
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillFilterSheet(
    onDismiss: () -> Unit,
    onApply: (BillFilter) -> Unit,
    currentFilter: BillFilter = BillFilter(),
    expenseCategories: List<CategoryItem> = emptyList(),
    incomeCategories: List<CategoryItem> = emptyList(),
    onManageCategories: ((String) -> Unit)? = null
) {
    var selectedCategory by remember { mutableStateOf(currentFilter.category) }
    var amountMin by remember { mutableStateOf(currentFilter.amountMin?.toString() ?: "") }
    var amountMax by remember { mutableStateOf(currentFilter.amountMax?.toString() ?: "") }
    var selectedType by remember { mutableStateOf(currentFilter.type) }
    val context = LocalContext.current

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bill_filter_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(stringResource(R.string.bill_filter_type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { selectedType = null },
                    label = { Text(stringResource(R.string.bill_filter_type_all)) }
                )
                FilterChip(
                    selected = selectedType == "EXPENSE",
                    onClick = { selectedType = "EXPENSE" },
                    label = { Text(stringResource(R.string.bill_filter_type_expense)) }
                )
                FilterChip(
                    selected = selectedType == "INCOME",
                    onClick = { selectedType = "INCOME" },
                    label = { Text(stringResource(R.string.bill_filter_type_income)) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(stringResource(R.string.bill_filter_amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountMin,
                    onValueChange = { amountMin = it },
                    label = { Text(stringResource(R.string.bill_filter_amount_min)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountMax,
                    onValueChange = { amountMax = it },
                    label = { Text(stringResource(R.string.bill_filter_amount_max)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(stringResource(R.string.bill_filter_category), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            val categories = if (selectedType == "INCOME") incomeCategories else if (selectedType == "EXPENSE") expenseCategories else expenseCategories + incomeCategories
            val categoryType = if (selectedType == "INCOME") "BILL_INCOME" else if (selectedType == "EXPENSE") "BILL_EXPENSE" else "BILL_EXPENSE"
            CategoryPicker(
                selected = selectedCategory ?: "",
                onSelected = { catName ->
                    selectedCategory = if (catName == selectedCategory) null else catName
                },
                categories = categories,
                rows = 3,
                columns = 5,
                onManageCategories = onManageCategories?.let { { it(categoryType) } },
                getDisplayName = { getLocalizedCategoryName(it)?.let { id -> context.getString(id) } ?: it }
            )
            Spacer(modifier = Modifier.height(16.dp))

            val hasActiveFilter = currentFilter.isActive
            if (hasActiveFilter) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onApply(BillFilter())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.bill_filter_clear), fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = {
                            onApply(
                                BillFilter(
                                    type = selectedType,
                                    category = selectedCategory,
                                    amountMin = amountMin.toDoubleOrNull(),
                                    amountMax = amountMax.toDoubleOrNull()
                                )
                            )
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.bill_filter_apply), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = {
                        onApply(
                            BillFilter(
                                type = selectedType,
                                category = selectedCategory,
                                amountMin = amountMin.toDoubleOrNull(),
                                amountMax = amountMax.toDoubleOrNull()
                            )
                        )
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.bill_filter_apply), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Stable
data class BillFilter(
    val type: String? = null,
    val category: String? = null,
    val paymentMethod: String? = null,
    val amountMin: Double? = null,
    val amountMax: Double? = null
) {
    val isActive: Boolean get() = type != null || category != null || paymentMethod != null || amountMin != null || amountMax != null
}
