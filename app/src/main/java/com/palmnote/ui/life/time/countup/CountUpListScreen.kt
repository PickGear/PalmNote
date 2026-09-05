package com.palmnote.ui.life.time.countup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.domain.util.DateUtils
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.ui.theme.*
import com.palmnote.ui.life.time.common.TimeListConfig
import com.palmnote.ui.life.time.common.TimeListScreen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
@Suppress("LongMethod")
fun CountUpListScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: CountUpViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { viewModel.load(templateId) }
    
    val config = TimeListConfig(
        title = stringResource(R.string.life_countup_title),
        accentColor = LifeCountUp,
        emptyIcon = Icons.AutoMirrored.Filled.TrendingUp,
        emptyTitle = stringResource(R.string.life_empty_countup),
        emptySubtitle = stringResource(R.string.life_empty_countup_subtitle),
        emptyActionText = stringResource(R.string.life_empty_countup_action),
        deleteConfirmText = stringResource(R.string.life_confirm_delete_countup)
    )
    
    TimeListScreen(
        config = config,
        items = state.items,
        isLoading = state.isLoading,
        onBack = onBack,
        onItemClick = onItemClick,
        onCreateClick = onCreateClick,
        onDeleteItem = { viewModel.deleteItem(it) }
    ) { item ->
        val startDate = try {
            val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
            (obj["start_date"] as? JsonPrimitive)?.content?.toLongOrNull()
                ?: (obj["startDate"] as? JsonPrimitive)?.content?.toLongOrNull()
        } catch (_: Exception) { null }
        val days = if (startDate != null) {
            ChronoUnit.DAYS.between(DateUtils.millisToLocalDate(startDate), LocalDate.now())
        } else 0L
        val milestones = listOf(100L, 200L, 365L, 500L, 750L, 1000L)
        val nextMilestone = milestones.firstOrNull { it > days }

        Card(
            modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = LifeCountUp, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.life_countup_days, days), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = LifeCountUp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    milestones.forEach { target ->
                        val reached = days >= target
                        Box(
                            modifier = Modifier.size(28.dp).background(
                                if (reached) LifeCountUp else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(14.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$target", fontSize = 9.sp, color = if (reached) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (nextMilestone != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.life_countup_milestone, nextMilestone - days), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}