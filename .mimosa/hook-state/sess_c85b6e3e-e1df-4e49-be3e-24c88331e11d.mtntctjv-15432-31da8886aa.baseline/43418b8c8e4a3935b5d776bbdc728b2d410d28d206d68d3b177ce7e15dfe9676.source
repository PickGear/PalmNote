package com.palmnote.ui.components.field

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.ui.theme.Spacing

@Composable
fun FieldDisplayComponent(
    config: FieldConfig,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(config.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = formatFieldValue(config, value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatFieldValue(config: FieldConfig, value: String): String {
    if (value.isEmpty()) return "-"
    return when (config.type) {
        FieldType.NUMBER -> if (config.unit.isNotEmpty()) "$value ${config.unit}" else value
        FieldType.PERCENT -> "$value%"
        FieldType.DATE -> {
            val ts = value.toLongOrNull() ?: return value
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(ts))
        }
        FieldType.RATING -> {
            val r = value.toIntOrNull() ?: return value
            "★".repeat(r / 2) + if (r % 2 == 1) "½" else ""
        }
        else -> value
    }
}
