package com.palmnote.ui.components.field

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.palmnote.R
import com.palmnote.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsInputComponent(
    label: String,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    suggestions: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(Spacing.xxs))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onTagsChange(tags - tag) },
                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xxs))

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                if (it.contains(",") || it.contains(" ")) {
                    val newTag = it.trim().trimEnd(',', ' ')
                    if (newTag.isNotEmpty() && newTag !in tags) {
                        onTagsChange(tags + newTag)
                    }
                    input = ""
                }
                showSuggestions = it.isNotEmpty()
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.field_input_hint)) }
        )

        if (showSuggestions && suggestions.isNotEmpty()) {
            suggestions.filter { it.contains(input, ignoreCase = true) && it !in tags }.take(5).forEach { s ->
                TextButton(onClick = { onTagsChange(tags + s); input = ""; showSuggestions = false }) {
                    Text(s, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
