package com.palmnote.ui.life.record.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.palmnote.R
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.Achievement
import com.palmnote.domain.repository.AchievementRepository
import com.palmnote.ui.theme.iconFromName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class AchievementUiState(
    val achievements: List<Achievement> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val achievementRepo: AchievementRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AchievementUiState())
    val uiState: StateFlow<AchievementUiState> = _uiState.asStateFlow()

    init {
        achievementRepo.getAllAchievements().onEach { achievements ->
            _uiState.update { it.copy(achievements = achievements, isLoading = false) }
        }.launchIn(viewModelScope)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementScreen(onBack: () -> Unit, viewModel: AchievementViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var selectedAchievement by remember { mutableStateOf<Achievement?>(null) }

    val unlocked = state.achievements.filter { it.unlockedAt != null }
    val locked = state.achievements.filter { it.unlockedAt == null }

    if (selectedAchievement != null) {
        val ach = selectedAchievement!!
        AppDialog(
            onDismissRequest = { selectedAchievement = null },
            title = { Text(ach.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(iconFromName(ach.icon), null, tint = if (ach.unlockedAt != null) Color(0xFF66D98D) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(ach.description, fontSize = 14.sp, textAlign = TextAlign.Center)
                    if (ach.unlockedAt != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.life_achievement_unlocked_on, dateFmt.format(Date(ach.unlockedAt))), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedAchievement = null }) { Text(stringResource(R.string.life_achievement_close)) } }
        )
    }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = { Text(stringResource(R.string.life_achievement_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.life_back)) } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.life_achievement_unlocked), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 300.dp)) {
                    items(unlocked, key = { it.id }) { ach ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selectedAchievement = ach }.padding(4.dp)) {
                            Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(iconFromName(ach.icon), null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(ach.name, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            if (ach.unlockedAt != null) {
                                Text(dateFmt.format(Date(ach.unlockedAt)), fontSize = 9.sp, color = MaterialTheme.colorScheme.outline, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.life_achievement_locked), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(locked, key = { it.id }) { ach ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selectedAchievement = ach }.padding(4.dp).alpha(0.4f)) {
                            Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(iconFromName(ach.icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(ach.name, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
