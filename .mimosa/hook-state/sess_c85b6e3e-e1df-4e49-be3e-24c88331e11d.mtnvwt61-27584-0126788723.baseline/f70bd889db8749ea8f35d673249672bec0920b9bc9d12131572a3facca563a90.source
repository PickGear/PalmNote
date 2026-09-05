package com.palmnote.ui.life.time.anniversary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun AnniversaryListScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: AnniversaryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { viewModel.load(templateId) }
    
    val config = TimeListConfig(
        title = stringResource(R.string.life_anniversary_title),
        accentColor = LifeAnniversary,
        emptyIcon = Icons.Default.Favorite,
        emptyTitle = stringResource(R.string.life_empty_anniversary),
        emptySubtitle = stringResource(R.string.life_empty_anniversary_subtitle),
        emptyActionText = stringResource(R.string.life_empty_anniversary_action),
        deleteConfirmText = stringResource(R.string.life_confirm_delete_anniversary)
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
        val dateMillis = try {
            val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
            (obj["date"] as? JsonPrimitive)?.content?.toLongOrNull()
                ?: (obj["target_date"] as? JsonPrimitive)?.content?.toLongOrNull()
        } catch (_: Exception) { null }
        val days = if (dateMillis != null) {
            ChronoUnit.DAYS.between(DateUtils.millisToLocalDate(dateMillis), LocalDate.now())
        } else 0L

        Card(
            modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.width(3.dp).height(60.dp).align(Alignment.CenterStart).background(LifeAnniversary, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, tint = LifeAnniversary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.life_anniversary_days_ago, days.toInt()), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("$days", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = LifeAnniversary)
                }
            }
        }
    }
}