package com.palmnote.ui.life.plan.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.palmnote.ui.life.common.LifeLazyList
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.palmnote.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.life.common.SwipeableItem
import com.palmnote.ui.theme.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun SubscriptionListScreen(templateId: Long, onBack: () -> Unit, onItemClick: (Long) -> Unit, onCreateClick: () -> Unit = {}, viewModel: SubscriptionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { viewModel.load(templateId) }
    val subColor = LifeSubscription
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.life_confirm), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_confirm_delete_subscription)) },
            confirmButton = { TextButton(onClick = { deleteTarget?.let { viewModel.deleteItem(it) }; deleteTarget = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    Scaffold(topBar = { 
        SecondaryTopAppBar(
            title = stringResource(R.string.life_subscription_title),
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
            actions = { IconButton(onClick = onCreateClick) { Icon(Icons.Default.Add, stringResource(R.string.life_new_create)) } },
            backgroundColor = MaterialTheme.colorScheme.background
        )
    }, containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        if (state.isLoading) { Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = subColor) }; return@Scaffold }
        if (state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.DateRange,
                    title = stringResource(R.string.life_empty_subscription),
                    subtitle = stringResource(R.string.life_empty_subscription_subtitle),
                    actionText = stringResource(R.string.life_empty_subscription_action),
                    onActionClick = onCreateClick
                )
            }
        } else {
        LifeLazyList(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.items, key = { it.id }) { item ->
                val fields = try {
                    val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
                    Triple(
                        (obj["price"] as? JsonPrimitive)?.content ?: "",
                        (obj["billingCycle"] as? JsonPrimitive)?.content ?: "",
                        (obj["nextBilling"] as? JsonPrimitive)?.content ?: ""
                    )
                } catch (_: Exception) { Triple("", "", "") }
                SwipeableItem(onDelete = { deleteTarget = item.id }) {
                    Card(modifier = Modifier.fillMaxWidth().clickable { onItemClick(item.id) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.width(3.dp).height(56.dp).align(Alignment.CenterStart).background(subColor, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
                            Row(modifier = Modifier.padding(start = 15.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("\u00A5${fields.first} / ${fields.second}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Text(when (item.status) { "ACTIVE" -> stringResource(R.string.life_subscription_active); "CANCELLED" -> stringResource(R.string.life_subscription_cancelled); else -> stringResource(R.string.life_subscription_archived) }, fontSize = 11.sp, color = when (item.status) { "ACTIVE" -> subColor; "CANCELLED" -> Color(0xFF85808a); else -> Color(0xFF504c58) })
                            }
                        }
                    }
                }
            }
        }
        }
    }
}