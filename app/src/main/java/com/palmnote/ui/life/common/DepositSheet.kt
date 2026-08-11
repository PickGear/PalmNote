package com.palmnote.ui.life.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.domain.model.Money
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositSheet(
    savingItemName: String = "",
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val alpha by animateFloatAsState(
        targetValue = if (saving) 0.5f else 1f,
        animationSpec = tween(200),
        label = "deposit_alpha"
    )

    if (saving) {
        LaunchedEffect(Unit) {
            delay(300)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        dragHandle = { Spacer(Modifier.height(0.dp)) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle
            Box(modifier = Modifier.width(36.dp).height(4.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))

            Spacer(modifier = Modifier.height(20.dp))

            // Title row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.life_deposit_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                if (savingItemName.isNotBlank()) {
                    Text(savingItemName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Amount input
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amount = it },
                placeholder = { Text("0.00", fontSize = 36.sp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) },
                textStyle = LocalTextStyle.current.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text("\u00A5", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            Button(
                onClick = {
                    val amt = Money.parse(amount)?.cents
                    if (amt != null && amt > 0 && !saving) {
                        saving = true
                        onConfirm(amt)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = (Money.parse(amount)?.cents ?: 0L) > 0 && !saving,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                } else {
                    Text(stringResource(R.string.life_deposit_save), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}
