package com.palmnote.ui.components.field

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.palmnote.R
import com.palmnote.ui.theme.Spacing

@Composable
fun MoneyInputComponent(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newVal -> if (newVal.all { it.isDigit() || it == '.' }) onValueChange(newVal) },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        prefix = { Text(stringResource(R.string.currency_symbol)) },
        placeholder = { Text("0.00") }
    )
}
