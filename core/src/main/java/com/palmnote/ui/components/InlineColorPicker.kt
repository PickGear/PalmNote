package com.palmnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.R

@Composable
fun InlineColorPicker(
    presetColors: List<Pair<String, Color>>,
    selectedColor: String?,
    onColorSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.wallpaper_preset),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        PresetColorGrid(presetColors, selectedColor, onColorSelected)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.wallpaper_custom_color),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        CustomHexField(onColorSelected)
    }
}

@Composable
private fun PresetColorGrid(
    presetColors: List<Pair<String, Color>>,
    selectedColor: String?,
    onColorSelected: (String?) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        presetColors.chunked(4).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { (name, color) ->
                    val isSelected = selectedColor == name
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                            )
                            .clickable { onColorSelected(name) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomHexField(onColorSelected: (String?) -> Unit) {
    var customHex by remember { mutableStateOf("") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = customHex,
            onValueChange = { v -> customHex = v.filter { it.isLetterOrDigit() }.take(6) },
            label = { Text("HEX") },
            prefix = { Text("#", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.small
        )
        val previewColor = "#$customHex".toComposeColor(Color.Gray)
        val isValid = customHex.length == 6
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(previewColor)
                .then(
                    if (isValid) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
                .clickable(enabled = isValid) { onColorSelected("#$customHex") },
            contentAlignment = Alignment.Center
        ) {
            if (isValid) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
