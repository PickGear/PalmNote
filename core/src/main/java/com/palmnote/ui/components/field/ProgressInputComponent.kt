package com.palmnote.ui.components.field

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ProgressInputComponent(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sliderValue = value.toFloat().coerceIn(0f, 100f)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$value%", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = sliderValue,
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 99
        )
    }
}
