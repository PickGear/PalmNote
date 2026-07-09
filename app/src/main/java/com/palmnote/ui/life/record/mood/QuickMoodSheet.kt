package com.palmnote.ui.life.record.mood

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.R
import com.palmnote.ui.theme.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

private data class MoodOption(val emoji: String, val key: String, val label: String, val color: Color)

@Composable
private fun moodList(): List<MoodOption> = listOf(
    MoodOption("\uD83D\uDE04", "HAPPY", stringResource(R.string.life_mood_happy), LifeMoodHappy),
    MoodOption("\uD83D\uDE42", "GOOD", stringResource(R.string.life_mood_normal), LifeMoodNormal),
    MoodOption("\uD83D\uDE14", "NORMAL", stringResource(R.string.life_mood_upset), LifeMoodUpset),
    MoodOption("\uD83D\uDE22", "SAD", stringResource(R.string.life_mood_sad), LifeMoodSad),
    MoodOption("\uD83D\uDE21", "ANGRY", stringResource(R.string.life_mood_angry), LifeMoodAngry)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMoodSheet(
    onDismiss: () -> Unit,
    onSave: (mood: String, content: String, factors: String) -> Unit,
    editMood: String? = null,
    editContent: String? = null,
    editFactors: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val moods = moodList()
    
    // 根据编辑数据初始化状态
    val initialIndex = remember(editMood) {
        moods.indexOfFirst { it.key == editMood }.takeIf { it >= 0 } ?: 2
    }
    var selectedIndex by remember { mutableIntStateOf(initialIndex) }
    var content by remember { mutableStateOf(editContent ?: "") }
    
    val initialFactors = remember(editFactors) {
        if (editFactors.isNullOrBlank()) emptySet()
        else try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(editFactors) as? JsonArray
            arr?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet() ?: emptySet()
        } catch (_: Exception) { emptySet() }
    }
    var selectedFactors by remember { mutableStateOf(initialFactors) }
    
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val factors = listOf(stringResource(R.string.life_mood_factor_work) to "work", stringResource(R.string.life_mood_factor_health) to "favorite", stringResource(R.string.life_mood_factor_family) to "family_restroom", stringResource(R.string.life_mood_factor_finance) to "account_balance", stringResource(R.string.life_mood_factor_weather) to "cloud", stringResource(R.string.life_mood_factor_social) to "groups")
    val selectedMood = moods[selectedIndex].key
    
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) { Box(modifier = Modifier.width(36.dp).height(4.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))) }
            Text(
                if (editMood != null) stringResource(R.string.life_mood_edit) 
                else stringResource(R.string.life_mood_question), 
                fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { dragAccum = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragAccum += dragAmount
                                val threshold = 60f
                                if (dragAccum > threshold) {
                                    selectedIndex = if (selectedIndex == 0) 4 else selectedIndex - 1
                                    dragAccum = 0f
                                } else if (dragAccum < -threshold) {
                                    selectedIndex = if (selectedIndex == 4) 0 else selectedIndex + 1
                                    dragAccum = 0f
                                }
                            }
                        )
                    },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                moods.forEachIndexed { idx, mood ->
                    val isSelected = idx == selectedIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { selectedIndex = idx }
                            .graphicsLayer {
                                scaleX = if (isSelected) 1.15f else 0.85f
                                scaleY = if (isSelected) 1.15f else 0.85f
                            }
                            .alpha(if (isSelected) 1f else 0.5f)
                            .padding(4.dp)
                    ) {
                        Text(mood.emoji, fontSize = if (isSelected) 44.sp else 32.sp)
                        Text(mood.label, fontSize = 12.sp, color = if (isSelected) mood.color else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.life_mood_factors), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                factors.forEach { (label, icon) ->
                    val selected = selectedFactors.contains(icon)
                    Box(modifier = Modifier.padding(end = 4.dp, bottom = 4.dp).background(if (selected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).border(if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary) else BorderStroke(0.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp)).clickable { selectedFactors = if (selected) selectedFactors - icon else selectedFactors + icon }.padding(horizontal = 10.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 11.sp, color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = content, onValueChange = { content = it }, placeholder = { Text(stringResource(R.string.life_mood_content_hint), fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(60.dp), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LifeMoodColor))

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val factorsJson = JsonArray(selectedFactors.map { JsonPrimitive(it) }).toString()
                onSave(selectedMood, content, factorsJson)
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = LifeMoodColor)) { Text(stringResource(R.string.life_mood_save), fontWeight = FontWeight.SemiBold, color = Color.Black) }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}