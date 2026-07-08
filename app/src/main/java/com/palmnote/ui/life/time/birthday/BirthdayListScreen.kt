package com.palmnote.ui.life.time.birthday

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.palmnote.ui.life.common.LifeLazyList
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.R
import com.palmnote.ui.life.common.EmptyState
import com.palmnote.ui.life.common.SwipeableItem
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.theme.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayListScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: BirthdayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { viewModel.load(templateId) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_delete_birthday)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteItem(deleteTarget!!); deleteTarget = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.life_birthday_title), fontWeight = FontWeight.Bold, color = LifeBirthday) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = { IconButton(onClick = onCreateClick) { Icon(Icons.Default.Add, stringResource(R.string.life_new_create)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LifeBirthday) }
            return@Scaffold
        }
        if (state.items.isEmpty()) {
            EmptyState(
                icon = Icons.Default.CardGiftcard,
                title = stringResource(R.string.life_empty_birthday),
                subtitle = stringResource(R.string.life_empty_birthday_subtitle),
                actionLabel = stringResource(R.string.life_empty_birthday_action),
                onAction = onCreateClick
            )
        } else {
        LifeLazyList(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.items, key = { it.id }) { item ->
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
                    val bd = LocalDate.ofEpochDay(birthDate / 86400000L)
                    val nextBd = bd.withYear(LocalDate.now().year)
                    val target = if (nextBd.isBefore(LocalDate.now())) nextBd.plusYears(1) else nextBd
                    ChronoUnit.DAYS.between(LocalDate.now(), target)
                } else null

                val dateText = if (birthDate != null) {
                    val bd = LocalDate.ofEpochDay(birthDate / 86400000L)
                    "${fields.second}\u00B7${bd.format(DateTimeFormatter.ofPattern("M\u6708d\u65E5"))}"
                } else fields.second

                SwipeableItem(onDelete = { deleteTarget = item.id }) {
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
        }
        }
    }
}
