package com.palmnote.ui.bills

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.R
import com.palmnote.ui.components.AppBottomSheet
import com.palmnote.ui.components.CategoryItem
import com.palmnote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillFilterSheet(
    onDismiss: () -> Unit,
    onApply: (BillFilter) -> Unit,
    currentFilter: BillFilter = BillFilter(),
    expenseCategories: List<CategoryItem> = emptyList(),
    incomeCategories: List<CategoryItem> = emptyList(),
    @Suppress("UNUSED_PARAMETER") paymentMethods: List<String> = emptyList()
) {
    var selectedCategory by remember { mutableStateOf(currentFilter.category) }
    var amountMin by remember { mutableStateOf(currentFilter.amountMin?.toString() ?: "") }
    var amountMax by remember { mutableStateOf(currentFilter.amountMax?.toString() ?: "") }
    var selectedType by remember { mutableStateOf(currentFilter.type) }
    var showAllCategories by remember { mutableStateOf(false) }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.bill_filter_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 类型筛选
        Text(stringResource(R.string.bill_filter_type), style = MaterialTheme.typography.titleSmall)
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
        Spacer(modifier = Modifier.height(16.dp))

        // 金额范围
        Text(stringResource(R.string.bill_filter_amount), style = MaterialTheme.typography.titleSmall)
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
        Spacer(modifier = Modifier.height(16.dp))

        // 分类筛选（限制显示 + 展开收起）
        Text(stringResource(R.string.bill_filter_category), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        val categories = if (selectedType == "INCOME") incomeCategories else expenseCategories
        val maxVisible = 8
        val allItems = listOf(null) + categories.map { it.name }
        val visibleItems = if (showAllCategories || allItems.size <= maxVisible) allItems else allItems.take(maxVisible)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            visibleItems.forEach { name ->
                FilterChip(
                    selected = selectedCategory == name,
                    onClick = { selectedCategory = name },
                    label = { Text(name ?: stringResource(R.string.bill_filter_category_all)) }
                )
            }
        }
        if (allItems.size > maxVisible) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = { showAllCategories = !showAllCategories }) {
                Text(
                    if (showAllCategories) stringResource(R.string.bill_filter_show_less)
                    else stringResource(R.string.bill_filter_show_all),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // 应用按钮
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.bill_filter_apply))
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
    val isActive: Boolean
        get() = type != null || category != null || paymentMethod != null || amountMin != null || amountMax != null
}
