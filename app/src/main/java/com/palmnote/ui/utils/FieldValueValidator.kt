package com.palmnote.ui.utils

import android.content.Context
import com.palmnote.R
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType

object FieldValueValidator {
    data class ValidationResult(val isValid: Boolean, val errorMessage: String = "")

    fun validate(context: Context, config: FieldConfig, value: String): ValidationResult {
        if (config.required && value.isBlank()) {
            return ValidationResult(false, context.getString(R.string.field_error_required, config.label))
        }
        if (value.isBlank()) return ValidationResult(true)

        return when (config.type) {
            FieldType.NUMBER -> validateNumber(context, config, value)
            FieldType.PERCENT -> validatePercent(context, value)
            FieldType.RATING -> validateRating(context, value)
            else -> ValidationResult(true)
        }
    }

    private fun validateNumber(context: Context, config: FieldConfig, value: String): ValidationResult {
        val num = value.toDoubleOrNull() ?: return ValidationResult(false, context.getString(R.string.field_error_invalid_number))
        config.min?.let { if (num < it) return ValidationResult(false, context.getString(R.string.field_error_min_value, it.toInt())) }
        config.max?.let { if (num > it) return ValidationResult(false, context.getString(R.string.field_error_max_value, it.toInt())) }
        return ValidationResult(true)
    }

    private fun validatePercent(context: Context, value: String): ValidationResult {
        val num = value.toIntOrNull() ?: return ValidationResult(false, context.getString(R.string.field_error_invalid_percent))
        if (num < 0 || num > 100) return ValidationResult(false, context.getString(R.string.field_error_percent_range))
        return ValidationResult(true)
    }

    private fun validateRating(context: Context, value: String): ValidationResult {
        val num = value.toIntOrNull() ?: return ValidationResult(false, context.getString(R.string.field_error_invalid_rating))
        if (num < 0 || num > 10) return ValidationResult(false, context.getString(R.string.field_error_rating_range))
        return ValidationResult(true)
    }
}
