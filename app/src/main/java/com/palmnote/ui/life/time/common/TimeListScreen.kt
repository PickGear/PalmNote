package com.palmnote.ui.life.time.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.life.common.LifeLazyList
import com.palmnote.ui.life.common.SwipeableItem

data class TimeListConfig(
    val title: String,
    val accentColor: Color,
    val emptyIcon: ImageVector,
    val emptyTitle: String,
    val emptySubtitle: String,
    val emptyActionText: String,
    val deleteConfirmText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeListScreen(
    config: TimeListConfig,
    items: List<LifeItem>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onCreateClick: () -> Unit,
    onDeleteItem: (Long) -> Unit,
    extraActions: @Composable RowScope.() -> Unit = {},
    itemContent: @Composable (LifeItem) -> Unit
) {
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    
    if (deleteTarget != null) {
        AppDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold) },
            text = { Text(config.deleteConfirmText) },
            confirmButton = { 
                TextButton(onClick = { 
                    deleteTarget?.let { onDeleteItem(it) }; 
                    deleteTarget = null 
                }) { 
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) 
                } 
            },
            dismissButton = { 
                TextButton(onClick = { deleteTarget = null }) { 
                    Text(stringResource(R.string.cancel)) 
                } 
            }
        )
    }
    
    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(config.title, fontWeight = FontWeight.Bold, color = config.accentColor) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } },
                actions = {
                    IconButton(onClick = onCreateClick) { Icon(Icons.Default.Add, stringResource(R.string.life_new_create)) }
                    extraActions()
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { 
                CircularProgressIndicator(color = config.accentColor) 
            }
            return@Scaffold
        }
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = config.emptyIcon,
                    title = config.emptyTitle,
                    subtitle = config.emptySubtitle,
                    actionText = config.emptyActionText,
                    onActionClick = onCreateClick
                )
            }
        } else {
            LifeLazyList(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items.size) { index ->
                    val item = items[index]
                    SwipeableItem(onDelete = { deleteTarget = item.id }) {
                        itemContent(item)
                    }
                }
            }
        }
    }
}