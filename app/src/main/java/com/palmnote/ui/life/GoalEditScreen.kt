package com.palmnote.ui.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.ModuleLife

/**
 * 目标新建 / 编辑表单（总纲 P5d）：累计型 / 目标值型。
 * 习惯打卡不在这里——习惯语义由生活模板承担。
 */
@Composable
fun GoalEditScreen(
    onBack: () -> Unit,
    viewModel: GoalEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SecondaryTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEdit) R.string.life_goal_edit else R.string.life_goal_new
                        ),
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GoalTitleField(viewModel, state)
            GoalTypeSection(viewModel, state)
            GoalValueFields(viewModel, state)
            if (!state.cumulative) {
                GoalDirectionSection(viewModel, state)
            }
            Spacer(Modifier.height(8.dp))
            GoalSaveButton(viewModel, state, onBack)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GoalTitleField(viewModel: GoalEditViewModel, state: GoalEditViewModel.UiState) {
    OutlinedTextField(
        value = state.title,
        onValueChange = { v -> viewModel.onEdit { it.copy(title = v) } },
        label = { Text(stringResource(R.string.life_goal_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun GoalTypeSection(viewModel: GoalEditViewModel, state: GoalEditViewModel.UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.life_goal_type),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.cumulative,
                onClick = { viewModel.onEdit { it.copy(cumulative = true) } },
                label = { Text(stringResource(R.string.life_goal_type_cumulative)) }
            )
            FilterChip(
                selected = !state.cumulative,
                onClick = { viewModel.onEdit { it.copy(cumulative = false) } },
                label = { Text(stringResource(R.string.life_goal_type_target)) }
            )
        }
        Text(
            stringResource(
                if (state.cumulative) R.string.life_goal_type_cumulative_hint
                else R.string.life_goal_type_target_hint
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GoalValueFields(viewModel: GoalEditViewModel, state: GoalEditViewModel.UiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = if (state.target == 0) "" else state.target.toString(),
            onValueChange = { v ->
                viewModel.onEdit { it.copy(target = v.filter(Char::isDigit).take(9).toIntOrNull() ?: 0) }
            },
            label = { Text(stringResource(R.string.life_goal_target)) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = state.unit,
            onValueChange = { v -> viewModel.onEdit { it.copy(unit = v.take(6)) } },
            label = { Text(stringResource(R.string.life_goal_unit)) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GoalDirectionSection(viewModel: GoalEditViewModel, state: GoalEditViewModel.UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.life_goal_direction),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !state.decrease,
                onClick = { viewModel.onEdit { it.copy(decrease = false) } },
                label = { Text(stringResource(R.string.life_goal_direction_increase)) }
            )
            FilterChip(
                selected = state.decrease,
                onClick = { viewModel.onEdit { it.copy(decrease = true) } },
                label = { Text(stringResource(R.string.life_goal_direction_decrease)) }
            )
        }
        if (state.decrease) {
            OutlinedTextField(
                value = if (state.initialValue == 0L) "" else state.initialValue.toString(),
                onValueChange = { v ->
                    viewModel.onEdit { it.copy(initialValue = v.filter(Char::isDigit).take(9).toLongOrNull() ?: 0L) }
                },
                label = { Text(stringResource(R.string.life_goal_initial_value)) },
                supportingText = { Text(stringResource(R.string.life_goal_initial_value_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GoalSaveButton(viewModel: GoalEditViewModel, state: GoalEditViewModel.UiState, onBack: () -> Unit) {
    Button(
        onClick = { viewModel.save(onBack) },
        enabled = state.title.isNotBlank() && state.target > 0 &&
            // 递减型要求当前值高于目标值（区间不退化）
            (state.cumulative || !state.decrease || state.initialValue > state.target),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
    }
}
