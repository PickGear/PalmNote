package com.palmnote.ui.components.field

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.palmnote.R

@Composable
fun CounterInputComponent(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = { if (value > min) onValueChange(value - 1) },
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Remove, stringResource(R.string.field_decrease), modifier = Modifier.size(18.dp))
            }

            Text(
                text = value.toString(),
                modifier = Modifier.width(48.dp).padding(horizontal = 8.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )

            FilledIconButton(
                onClick = { if (value < max) onValueChange(value + 1) },
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.field_increase), modifier = Modifier.size(18.dp))
            }
        }
    }
}
