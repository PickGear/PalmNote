package com.palmnote.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.db.entity.Goal
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.EmptyState
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.ModuleLife

/**
 * 目标列表（总纲 P5d）：goals 表第一次有 UI。
 * 观感基准与存钱计划一致 —— 数值 + 进度条 + 快选 ±；卡片点击进编辑。
 */
@Composable
fun GoalListScreen(
    onBack: () -> Unit,
    onEditGoal: (Long) -> Unit,
    onCreateGoal: () -> Unit,
    viewModel: GoalListViewModel = hiltViewModel()
) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Goal?>(null) }

    GoalDeleteDialog(pendingDelete, onDelete = { viewModel.delete(it) }, onDismiss = { pendingDelete = null })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SecondaryTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.life_goal_title),
                        fontWeight = FontWeight.Bold,
                        color = ModuleLife
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateGoal,
                containerColor = ModuleLife,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.life_goal_new))
            }
        }
    ) { padding ->
        if (goals.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = com.palmnote.ui.theme.AppIcon.Flag.imageVector,
                    title = stringResource(R.string.life_goal_empty),
                    subtitle = stringResource(R.string.life_goal_empty_hint)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(goals.size, key = { goals[it].id }) { index ->
                    GoalRow(
                        goal = goals[index],
                        onNudge = viewModel::nudge,
                        onOpen = { onEditGoal(goals[index].id) },
                        onDelete = { pendingDelete = goals[index] }
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalRow(
    goal: Goal,
    onNudge: (Goal, Boolean) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val fraction = remember(goal.id, goal.currentCount, goal.totalCount, goal.initialValue) {
        goalProgressFraction(goal)
    }
    com.palmnote.ui.components.ModuleCard(
        tint = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            GoalRowHeader(goal, onOpen, onDelete)
            GoalRowProgress(goal, onNudge)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = ModuleLife,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

/** 卡头：图标 + 标题（点名称进编辑）+ 删除（单独一颗图标，避免误触）。 */
@Composable
private fun GoalRowHeader(goal: Goal, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Icon(
            goal.icon.imageVector,
            contentDescription = null,
            tint = goal.icon.tint,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            goal.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.life_goal_edit),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 进度行：数值文案 + 快选 ±（+ 恒为「朝目标推进」，递减型 + 是往下减，语义统一为向目标走）。 */
@Composable
private fun GoalRowProgress(goal: Goal, onNudge: (Goal, Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(
                R.string.life_goal_progress,
                goal.currentCount, goal.totalCount, goal.unit
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onNudge(goal, false) }) {
            Icon(Icons.Filled.Remove, stringResource(R.string.life_goal_step_back))
        }
        IconButton(onClick = { onNudge(goal, true) }) {
            Icon(Icons.Filled.Add, stringResource(R.string.life_goal_step_forward))
        }
    }
}

/** 删除确认（目标与其打卡记录一并删除，不可恢复）。 */
@Composable
private fun GoalDeleteDialog(
    pending: Goal?,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    pending?.let { goal ->
        AppDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.life_goal_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_goal_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(goal.id)
                    onDismiss()
                }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}
