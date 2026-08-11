package com.palmnote.ui.components.field

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.palmnote.R
import com.palmnote.ui.theme.Spacing

@Composable
fun DateRangeInputComponent(
    label: String,
    startValue: String,
    endValue: String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DateInputComponent(
                label = stringResource(R.string.field_start),
                value = startValue,
                onValueChange = onStartChange,
                modifier = Modifier.weight(1f)
            )
            DateInputComponent(
                label = stringResource(R.string.field_end),
                value = endValue,
                onValueChange = onEndChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
