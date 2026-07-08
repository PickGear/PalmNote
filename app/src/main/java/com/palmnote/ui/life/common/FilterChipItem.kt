package com.palmnote.ui.life.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FilterChipItem(
    label: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .background(if (selected) color.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, fontSize = 12.sp, color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}