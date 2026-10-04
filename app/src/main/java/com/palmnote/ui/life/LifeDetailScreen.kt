package com.palmnote.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.getKind
import com.palmnote.ui.components.AppDialog
import com.palmnote.ui.components.SecondaryTopAppBar
import com.palmnote.ui.theme.BigCardShape
import com.palmnote.ui.theme.Spacing

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
    onEditTemplate: (Long) -> Unit,
    onEditRecord: (Long) -> Unit = {},
    viewModel: LifeDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var milestone by remember { mutableStateOf<Int?>(null) }
    var shareOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.milestoneEvents.collect { milestone = it }
    }
    LaunchedEffect(milestone) {
        if (milestone != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(2400)
            milestone = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        val ready = state as? DetailUiState.Ready
        DetailHeader(
            title = ready?.ui?.ctx?.item?.title.orEmpty(),
            accent = ready?.ui?.ctx?.accent,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onBack = onBack,
            onShare = ready?.let { { shareOpen = true } },
            onEditTemplate = ready?.ui?.ctx?.template?.id?.let { id -> { onEditTemplate(id) } },
            onEditRecord = ready?.let { { onEditRecord(it.ui.ctx.item.id) } },
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
            // 数据流失败：此前没有这个分支，上游一抛异常页面就永远停在转圈上。
            DetailUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.life_data_load_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is DetailUiState.Ready -> DetailBody(
                ui = s.ui,
                actions = DetailRowActions(
                    onToggleChecklist = { key, index -> viewModel.toggleChecklist(key, index) },
                    onSetProgress = { key, value -> viewModel.setProgress(key, value) },
                    onNudgeProgress = { key, delta, min, max -> viewModel.nudgeProgress(key, delta, min, max) },
                    onToggleCell = { key, row, col -> viewModel.toggleTableCell(key, row, col) },
                    onEditField = { key, raw -> viewModel.setFieldValue(key, raw) }
                ),
                onToggleCheckIn = { viewModel.toggleCheckIn() },
                onFocusSessionSaved = { viewModel.saveFocusSession(it) },
                focusTimer = viewModel.focusTimerState.collectAsStateWithLifecycle().value,
                onFocusStart = viewModel::startFocusTimer,
                onFocusResume = viewModel::resumeFocusTimer,
                onFocusPause = viewModel::pauseFocusTimer
            )
        }

        milestone?.let { streak -> MilestoneCelebration(streak = streak) }
    }
    }

    // 分享图卡：弹层里那张卡片就是截图对象（所见即所分享）
    if (shareOpen) {
        (state as? DetailUiState.Ready)?.let { readyState ->
            LifeShareSheet(ui = readyState.ui, onDismiss = { shareOpen = false })
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

/**
 * 顶栏左侧的次要动作：身份色圆点（识别，不承担判断语义，§14.12(7) 第 4 条）+ 分享。
 *
 * 抽出来是因为 `DetailHeader` 已经顶到 detekt 的 `LongMethod` 阈值（60 行）——
 * 拆的是"次要动作"与"页面骨架"两件事，不是为凑行数。
 */
@Composable
private fun DetailHeaderLeadingActions(accent: Color?, onShare: (() -> Unit)?) {
    if (accent != null) {
        Box(
            modifier = Modifier
                .padding(end = Spacing.xxs)
                .size(9.dp)
                .clip(CircleShape)
                .background(accent)
        )
    }
    if (onShare != null) {
        IconButton(onClick = onShare) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.life_share_action),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DetailHeader(
    title: String,
    accent: Color?,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    onEditTemplate: (() -> Unit)?,
    onEditRecord: (() -> Unit)?,
    onDelete: () -> Unit
) {
    // 与完整列表/分类页同一套二级页头（排版统一）
    SecondaryTopAppBar(
        title = title,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_navigate_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            // 身份色圆点 + 分享（「⋮」菜单留在本函数里：它要读写 menuOpen）
            DetailHeaderLeadingActions(accent = accent, onShare = onShare)
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = stringResource(R.string.life_detail_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                    if (onEditRecord != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.life_record_edit_title)) },
                            onClick = {
                                onMenuOpenChange(false)
                                onEditRecord()
                            }
                        )
                    }
                    if (onEditTemplate != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.life_template_edit)) },
                            onClick = {
                                onMenuOpenChange(false)
                                onEditTemplate()
                            }
                        )
                    }
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
    )
}

@Composable
private fun DetailBody(
    ui: DetailUi,
    actions: DetailRowActions,
    onToggleCheckIn: () -> Unit,
    onFocusSessionSaved: (Long) -> Unit,
    focusTimer: LifeDetailViewModel.FocusTimerState,
    onFocusStart: () -> Unit,
    onFocusResume: () -> Unit,
    onFocusPause: () -> Unit
) {
    val ctx = ui.ctx
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(Spacing.xs))
        // ① 英雄区（卡圆角 16，§14.12(2)）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(3.dp, BigCardShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.10f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), BigCardShape),
            shape = BigCardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            LifeHero(
                ctx = ctx,
                onToggleChecklist = actions.onToggleChecklist ?: { _, _ -> },
                onSetProgress = actions.onSetProgress,
                onNudgeProgress = actions.onNudgeProgress,
                onSaveFocus = onFocusSessionSaved,
                focusTimer = focusTimer,
                onFocusStart = onFocusStart,
                onFocusResume = onFocusResume,
                onFocusPause = onFocusPause
            )
        }

        // ①→② 间距 12；② 只在英雄区不是指标型时出现
        if (ui.metrics.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            MetricRow(ui.metrics)
        }

        // 打卡模板：③ 结构区之前放「今日打卡」按钮（dtl_11 §14.12(5)）
        if (ctx.template.getKind() == LifeTemplateKind.HABIT) {
            Spacer(Modifier.height(Spacing.sm))
            CheckInTodayButton(done = ui.checkInTodayDone, onClick = onToggleCheckIn, accent = ctx.accent)
        }

        // ③ 结构区：每组一张卡，组间 14；清单组传就地勾选（展开后可用）
        ui.groups.forEach { group ->
            Spacer(Modifier.height(14.dp))
            // 就地编辑要的是**原始表单值**（行里的 value 是格式化后的显示串）
            val cfg = ctx.configs.firstOrNull { it.key == group.fieldKey }
            StructureGroupBlock(
                group = group,
                accent = ctx.accent,
                actions = actions,
                config = cfg,
                rawValue = cfg?.let { decodeFieldValue(it, ctx.obj[it.key]) },
                // 只有一组时不显示标题：泛化的手名（「数值」）在单组时是纯噪音
                showTitle = ui.groups.size > 1
            )
        }

        // ④ 时间与关联
        Spacer(Modifier.height(20.dp))
        TimeFooter(
            createdAt = ctx.item.createdAt,
            updatedAt = ctx.item.updatedAt,
            relationCounts = ui.relationCounts
        )
        Spacer(Modifier.height(Spacing.lg))
    }
}

/** 打卡「今日打卡」按钮（dtl_11）：今天已打 → 静态「今天已打卡 ✓」，不可再点。 */
@Composable
private fun CheckInTodayButton(done: Boolean, onClick: () -> Unit, accent: Color) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            if (!done) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
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

/**
 * 打卡目标达成微庆祝：柔色 wash 居中卡 + 缩放入场 + 一次触觉，2.4s 自动散场。
 * 克制版仪式感——纯展示、不拦操作（散场由外层 LaunchedEffect 计时承担）。
 */
@Composable
private fun MilestoneCelebration(streak: Int) {
    val scale = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }
    val wash = lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surface, 0.88f)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .graphicsLayer(scaleX = scale.value, scaleY = scale.value)
                .clip(RoundedCornerShape(24.dp))
                .background(wash)
                .padding(horizontal = 32.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉", fontSize = 46.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.life_milestone_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.life_milestone_days, streak),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
