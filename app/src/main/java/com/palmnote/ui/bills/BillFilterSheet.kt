package com.palmnote.ui.bills

import androidx.compose.foundation.layout.*
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.PaymentMethod
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
import com.palmnote.domain.model.toYuanString
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
    onManageCategories: ((String) -> Unit)? = null,
    presetOverrides: Map<String, String> = emptyMap()
) {
    var selectedCategory by remember { mutableStateOf(currentFilter.category) }
    var amountMin by remember { mutableStateOf(currentFilter.amountMin?.toYuanString() ?: "") }
    var amountMax by remember { mutableStateOf(currentFilter.amountMax?.toYuanString() ?: "") }
    var selectedType by remember { mutableStateOf(currentFilter.type) }
    val context = LocalContext.current

    fun filterDisplayName(key: String, type: String?): String {
        val prefix = if (type == "EXPENSE") "EXPENSE_" else if (type == "INCOME") "INCOME_" else ""
        val overrideKey = "preset_$prefix$key"
        val json = presetOverrides[overrideKey]
        if (json != null) {
            try {
                val obj = org.json.JSONObject(json)
                if (obj.has("name")) return obj.getString("name")
            } catch (_: Exception) {}
        }
        return getLocalizedCategoryName(key)?.let { context.getString(it) } ?: key
    }

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
                    selected = selectedType == BillType.EXPENSE,
                    onClick = { selectedType = if (selectedType == BillType.EXPENSE) null else BillType.EXPENSE },
                    label = { Text(stringResource(R.string.bill_filter_type_expense)) }
                )
                FilterChip(
                    selected = selectedType == BillType.INCOME,
                    onClick = { selectedType = if (selectedType == BillType.INCOME) null else BillType.INCOME },
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
            val categories = if (selectedType == BillType.INCOME) incomeCategories else expenseCategories
            val categoryType = if (selectedType == BillType.INCOME) "BILL_INCOME" else "BILL_EXPENSE"
            CategoryPicker(
                selected = selectedCategory ?: "",
                onSelected = { catName ->
                    selectedCategory = if (catName == selectedCategory) null else catName
                },
                categories = categories,
                rows = 3,
                columns = 5,
                onManageCategories = onManageCategories?.let { { it(categoryType) } },
                getDisplayName = { filterDisplayName(it, (selectedType ?: BillType.EXPENSE).value) }
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
                                    amountMin = com.palmnote.domain.model.Money.parse(amountMin)?.cents,
                                    amountMax = com.palmnote.domain.model.Money.parse(amountMax)?.cents
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
                                amountMin = com.palmnote.domain.model.Money.parse(amountMin)?.cents,
                                amountMax = com.palmnote.domain.model.Money.parse(amountMax)?.cents
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
    val type: BillType? = null,
    val category: String? = null,
    val paymentMethod: PaymentMethod? = null,
    val amountMin: Long? = null, // 金额（分）
    val amountMax: Long? = null // 金额（分）
) {
    val isActive: Boolean get() = type != null || category != null || paymentMethod != null || amountMin != null || amountMax != null
}
