package com.palmnote.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.theme.Spacing
import com.palmnote.ui.theme.BigCardShape

/**
 * 详情页（总纲 §14）：**四段骨架** —— ① 英雄区 / ② 主指标行 / ③ 结构区 / ④ 时间与关联。
 *
 * 取代旧实现的三处病（§14.1）：自绘进度环（第七套外观）、按 fixed key 猜 `cur`/`tot`、
 * 空值字段整条消失。现在：进度走形态族、取值全经 `FieldConfig`（契约）、空值照常占位。
 * 内容全部来自数据库（`LifeItem` + `LifeTemplate`），**没有写在界面上的假数据**。
 */
@Composable
fun LifeDetailScreen(
    onBack: () -> Unit,
    viewModel: LifeDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        val ready = state as? DetailUiState.Ready
        DetailHeader(
            title = ready?.ui?.ctx?.item?.title.orEmpty(),
            accent = ready?.ui?.ctx?.accent,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onBack = onBack,
            onDelete = { confirmDelete = true }
        )

        when (val s = state) {
            DetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            DetailUiState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.life_detail_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is DetailUiState.Ready -> DetailBody(
                ui = s.ui,
                onToggleChecklist = { key, index -> viewModel.toggleChecklist(key, index) },
                onToggleCheckIn = { viewModel.toggleCheckIn() },
                onFocusSessionSaved = { viewModel.saveFocusSession(it) }
            )
        }
    }

    if (confirmDelete) {
        AppDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.life_detail_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.life_detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) {
                    Text(
                        stringResource(R.string.life_detail_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}

@Composable
private fun DetailHeader(
    title: String,
    accent: Color?,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Spacing.xxl)
            .padding(horizontal = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_navigate_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (accent != null) {
            // 身份色圆点：识别（不承担判断语义，§14.12(7) 第 4 条）
            Box(
                modifier = Modifier
                    .padding(end = Spacing.xxs)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Box {
            IconButton(onClick = { onMenuOpenChange(true) }) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = stringResource(R.string.life_detail_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.life_detail_delete_confirm)) },
                    onClick = {
                        onMenuOpenChange(false)
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailBody(
    ui: DetailUi,
    onToggleChecklist: (String, Int) -> Unit,
    onToggleCheckIn: () -> Unit,
    onFocusSessionSaved: (Long) -> Unit
) {
    val ctx = ui.ctx
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        // ① 英雄区（卡圆角 16，§14.12(2)）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, BigCardShape),
            shape = BigCardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            LifeHero(ctx = ctx, onToggleChecklist = onToggleChecklist, onSaveFocus = onFocusSessionSaved)
        }

        // ①→② 间距 12；② 只在英雄区不是指标型时出现
        if (ui.metrics.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            MetricRow(ui.metrics)
        }

        // 打卡模板：③ 结构区之前放「今日打卡」按钮（dtl_11 §14.12(5)）
        if (ctx.template.icon == "calendar_month") {
            Spacer(Modifier.height(Spacing.sm))
            CheckInTodayButton(done = ui.checkInTodayDone, onClick = onToggleCheckIn, accent = ctx.accent)
        }

        // ③ 结构区：每组一张卡，组间 14
        ui.groups.forEach { group ->
            Spacer(Modifier.height(14.dp))
            StructureGroupBlock(group = group, accent = ctx.accent)
        }

        // ④ 时间与关联
        Spacer(Modifier.height(20.dp))
        TimeFooter(createdAt = ctx.item.createdAt, updatedAt = ctx.item.updatedAt)
        Spacer(Modifier.height(Spacing.lg))
    }
}

/** 打卡「今日打卡」按钮（dtl_11）：今天已打 → 静态「今天已打卡 ✓」，不可再点。 */
@Composable
private fun CheckInTodayButton(done: Boolean, onClick: () -> Unit, accent: Color) {
    Button(
        onClick = onClick,
        enabled = !done,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = BigCardShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant else accent,
            contentColor = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            if (done) stringResource(R.string.life_detail_checkin_done) else stringResource(R.string.life_detail_checkin_today),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
