package com.palmnote.ui.life.common
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.palmnote.app.R
import com.palmnote.ui.components.SecondaryTopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.ui.life.common.displayName
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow


private val emptyStateConfigs: Map<String, Triple<ImageVector, Int, Int>> = mapOf(
    "savings" to Triple(Icons.Default.Star, R.string.life_empty_saving_title, R.string.life_empty_saving_subtitle),
    "shopping_cart" to Triple(Icons.Default.ShoppingCart, R.string.life_empty_shopping_title, R.string.life_empty_shopping_subtitle),
    "flight" to Triple(Icons.Default.Flight, R.string.life_empty_travel_title, R.string.life_empty_travel_subtitle),
    "menu_book" to Triple(Icons.AutoMirrored.Filled.MenuBook, R.string.life_empty_reading_title, R.string.life_empty_reading_subtitle),
    "school" to Triple(Icons.Default.School, R.string.life_empty_study_title, R.string.life_empty_study_subtitle),
    "checklist" to Triple(Icons.Default.EditNote, R.string.life_empty_todo_title, R.string.life_empty_todo_subtitle),
    "trending_up" to Triple(Icons.AutoMirrored.Filled.TrendingUp, R.string.life_empty_countup_title, R.string.life_empty_countup_subtitle),
    "timer_off" to Triple(Icons.Default.HourglassTop, R.string.life_empty_countdown_title, R.string.life_empty_countdown_subtitle),
    "cake" to Triple(Icons.Default.Cake, R.string.life_empty_birthday_title, R.string.life_empty_birthday_subtitle),
    "celebration" to Triple(Icons.Default.Favorite, R.string.life_empty_anniversary_title, R.string.life_empty_anniversary_subtitle),
    "subscriptions" to Triple(Icons.Default.Subscriptions, R.string.life_empty_subscription_title, R.string.life_empty_subscription_subtitle),
    "calendar_month" to Triple(Icons.Default.CheckCircle, R.string.life_empty_habit_title, R.string.life_empty_habit_subtitle),
    "mood" to Triple(Icons.Default.Favorite, R.string.life_empty_mood_title, R.string.life_empty_mood_subtitle),
    "book" to Triple(Icons.Default.AutoStories, R.string.life_empty_journal_title, R.string.life_empty_journal_subtitle),
    "BarChart" to Triple(Icons.Default.BarChart, R.string.life_empty_report_title, R.string.life_empty_report_subtitle)
)

@HiltViewModel
class GenericListViewModel @Inject constructor(
    private val itemRepo: LifeItemRepository
) : ViewModel() {
    fun loadPaged(templateId: Long): Flow<PagingData<LifeItem>> {
        return itemRepo.getPagedItemsByTemplate(templateId).cachedIn(viewModelScope)
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            try {
                itemRepo.delete(id)
            } catch (e: Exception) {
                android.util.Log.w("GenericListVM", "delete failed", e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericTemplateListScreen(
    template: LifeTemplate,
    templateId: Long,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onCreateClick: () -> Unit,
    viewModel: GenericListViewModel = hiltViewModel()
) {
    val pagingItems = viewModel.loadPaged(templateId).collectAsLazyPagingItems()
    val emptyConfig = remember(template.icon) { emptyStateConfigs[template.icon] ?: Triple(Icons.Default.Inbox, R.string.life_empty_default_title, R.string.life_empty_default_subtitle) }
    val tplColor = MaterialTheme.colorScheme.secondary

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(template.displayName(), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick, containerColor = tplColor) { Icon(Icons.Default.Add, stringResource(R.string.create_new), tint = Color.White) }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (pagingItems.itemCount == 0) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(emptyConfig.first, null, tint = tplColor.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(emptyConfig.second), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(emptyConfig.third), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
                items(pagingItems.itemCount, key = { pagingItems[it]?.id ?: it }) { idx ->
                    val item = pagingItems[idx] ?: return@items
                    SwipeableItem(onDelete = { viewModel.deleteItem(item.id) }, modifier = Modifier.padding(vertical = 2.dp)) {
                        RichCard(
                            tpl = template,
                            item = item,
                            iconColor = tplColor,
                            variant = "AUTO",
                            onClick = { onItemClick(item.id) }
                        )
                    }
                }
            }
        }
    }
}
