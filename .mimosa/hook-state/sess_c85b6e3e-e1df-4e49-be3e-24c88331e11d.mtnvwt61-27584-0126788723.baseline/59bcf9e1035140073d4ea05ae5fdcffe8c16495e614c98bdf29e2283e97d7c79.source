package com.palmnote.ui.life.common

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteConfirmSheet(
    title: String = stringResource(R.string.life_delete_confirm_default),
    subtitle: String = "",
    itemSummary: String = "",
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var deleting by remember { mutableStateOf(false) }

    if (deleting) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(200)
            onDelete()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!deleting) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            // Drag handle
            Box(modifier = Modifier.width(36.dp).height(4.dp).align(Alignment.CenterHorizontally).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.height(20.dp))

            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)

            if (subtitle.isNotBlank() || itemSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(subtitle.ifBlank { itemSummary }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { if (!deleting) onDismiss() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !deleting
                ) { Text(stringResource(R.string.life_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) }

                Button(
                    onClick = { deleting = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !deleting
                ) {
                    if (deleting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text(stringResource(R.string.delete), color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
