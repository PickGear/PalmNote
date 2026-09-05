package com.palmnote.ui.life.time.birthday

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Person
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
fun BirthdayListScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: BirthdayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { viewModel.load(templateId) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val config = TimeListConfig(
        title = stringResource(R.string.life_birthday_title),
        accentColor = LifeBirthday,
        emptyIcon = Icons.Default.CardGiftcard,
        emptyTitle = stringResource(R.string.life_empty_birthday),
        emptySubtitle = stringResource(R.string.life_empty_birthday_subtitle),
        emptyActionText = stringResource(R.string.life_empty_birthday_action),
        deleteConfirmText = stringResource(R.string.life_confirm_delete_birthday)
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
        val fields = try {
            val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
            val birthDate = (obj["date"] as? JsonPrimitive)?.content?.toLongOrNull()
                ?: (obj["birthday_date"] as? JsonPrimitive)?.content?.toLongOrNull()
            val relation = (obj["relationship"] as? JsonPrimitive)?.content ?: ""
            val gift = (obj["last_gift"] as? JsonPrimitive)?.content ?: ""
            Triple(birthDate, relation, gift)
        } catch (_: Exception) { Triple(null, "", "") }

        val birthDate = fields.first
        val days = if (birthDate != null) {
            val bd = DateUtils.millisToLocalDate(birthDate)
            val md = java.time.MonthDay.of(bd.month, bd.dayOfMonth)
            val today = LocalDate.now()
            val target = try {
                val nextBd = md.atYear(today.year)
                if (nextBd.isBefore(today)) md.atYear(today.year + 1) else nextBd
            } catch (_: Exception) {
                md.atYear(today.year + 1)
            }
            ChronoUnit.DAYS.between(today, target)
        } else null

        val dateText = if (birthDate != null) {
            val bd = DateUtils.millisToLocalDate(birthDate)
            "${fields.second}\u00B7${bd.format(DateTimeFormatter.ofPattern(context.getString(R.string.date_format_display)))}"
        } else fields.second

        Card(
            modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.width(3.dp).height(80.dp).align(Alignment.CenterStart).background(LifeBirthday, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(36.dp).background(LifeBirthday.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = LifeBirthday, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(dateText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${days ?: "--"}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = LifeBirthday)
                            Text(stringResource(R.string.life_birthday_days_after), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (fields.third.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CardGiftcard, null, tint = Color(0xFFE8A848), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.life_birthday_last_gift, fields.third), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}