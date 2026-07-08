package com.palmnote.ui.utils

import com.palmnote.domain.model.FieldType

object FieldValueFormatter {
    fun formatForDisplay(type: FieldType, value: String, unit: String = ""): String {
        if (value.isEmpty()) return "-"
        return when (type) {
            FieldType.NUMBER -> if (unit.isNotEmpty()) "$value $unit" else value
            FieldType.PERCENT -> "$value%"
            FieldType.RATING -> {
                val r = value.toIntOrNull() ?: return value
                val full = r / 2
                val half = r % 2
                "★".repeat(full) + if (half == 1) "½" else ""
            }
            FieldType.DATE -> value.toLongOrNull()?.let {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(it))
            } ?: value
            else -> value
        }
    }

    fun formatForStorage(type: FieldType, displayValue: String): String {
        return when (type) {
            FieldType.DATE -> {
                // Try to parse date string to timestamp
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    sdf.parse(displayValue)?.time?.toString() ?: displayValue
                } catch (_: Exception) { displayValue }
            }
            else -> displayValue
        }
    }
}
