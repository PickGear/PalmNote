package com.palmnote.ui.bills

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.ui.components.CompactTopAppBar
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 独立的「导出账单」页。
 *
 * 为什么是独立页而不是弹窗 / 双 tab 里的一个页签：
 * - 导出前要让用户看清**自己导的是什么**（时间范围、条数、涉及几个账本钱包），弹窗塞不下；
 * - 失败要能停在原地看原因并重试，弹窗容易误关；
 * - 与「设置 → 数据交换」的交互一致；时间范围筛选与列清单也有地方长。
 *
 * 口径见 [com.palmnote.data.export.BillFlowExporter]：13 列、时间升序、排除演示数据、
 * UTF-8 带 BOM、有意不可回导（换机/迁移走设置里的交换包）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillExportScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BillExportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) viewModel.export(uri) }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.bill_import_export),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            ExportActionBar(
                state = state,
                onChooseLocation = { exportLauncher.launch(viewModel.fileName()) },
                onRetry = viewModel::retry
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExportSummaryHeader(state)
            ExportRangeCard(
                state = state,
                onSelectPreset = viewModel::selectPreset,
                onPickCustom = { showRangePicker = true }
            )
            ExportFormatSpec()
            state.message?.let { ExportResultText(message = it, failed = state.failed) }
        }
    }

    if (showRangePicker) {
        ExportRangePickerDialog(
            startMillis = state.customStart,
            endMillis = state.customEnd,
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showRangePicker = false
            }
        )
    }
}

/** 时间范围卡：预设胶囊（自动换行）+ 自定义区间显示。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportRangeCard(
    state: BillExportState,
    onSelectPreset: (ExportRangePreset) -> Unit,
    onPickCustom: () -> Unit
) {
    ImportCardSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.bill_export_range_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            // 换行而不是横向滚动：六个胶囊在 1080 宽下会溢出，
            // 一滚动就看不见「全部 / 本月」，用户会以为少了选项。
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExportRangePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = {
                            if (preset == ExportRangePreset.CUSTOM) onPickCustom() else onSelectPreset(preset)
                        },
                        label = { Text(presetLabel(preset)) }
                    )
                }
            }
            if (state.preset == ExportRangePreset.CUSTOM) {
                Text(
                    customRangeText(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun presetLabel(preset: ExportRangePreset): String = stringResource(
    when (preset) {
        ExportRangePreset.ALL -> R.string.bill_export_range_all
        ExportRangePreset.THIS_MONTH -> R.string.bill_export_range_this_month
        ExportRangePreset.LAST_MONTH -> R.string.bill_export_range_last_month
        ExportRangePreset.LAST_3_MONTHS -> R.string.bill_export_range_last_3_months
        ExportRangePreset.THIS_YEAR -> R.string.bill_export_range_this_year
        ExportRangePreset.CUSTOM -> R.string.bill_export_range_custom
    }
)

/** 自定义区间的可读文本（含首尾整天）。 */
@Composable
private fun customRangeText(state: BillExportState): String {
    val start = state.customStart
    val end = state.customEnd
    if (start == null || end == null) return stringResource(R.string.bill_export_range_custom)
    return remember(start, end) { formatDay(start) + " ～ " + formatDay(end) }
}

private fun formatDay(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()

/** 日期范围选择弹窗。确认时把「选中日」换算成含首尾整天的本地时间戳。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportRangePickerDialog(
    startMillis: Long?,
    endMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startMillis?.let(::toUtcPickerMillis),
        initialSelectedEndDateMillis = endMillis?.let(::toUtcPickerMillis)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val s = pickerState.selectedStartDateMillis
                    val e = pickerState.selectedEndDateMillis ?: s
                    if (s != null && e != null) {
                        val (start, end) = localDayBounds(s, e)
                        onConfirm(start, end)
                    }
                },
                enabled = pickerState.selectedStartDateMillis != null
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) {
        DateRangePicker(
            state = pickerState,
            title = {
                Text(
                    stringResource(R.string.bill_export_range_dialog_title),
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                )
            },
            showModeToggle = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 本地时间戳 → 日期选择器要的 UTC 零点。 */
private fun toUtcPickerMillis(localMillis: Long): Long =
    Instant.ofEpochMilli(localMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

/** 日期选择器给的 UTC 零点 → 本地「含首尾整天」区间。 */
private fun localDayBounds(startUtc: Long, endUtc: Long): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    fun localDate(utc: Long) = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
    val first = localDate(startUtc)
    val last = localDate(endUtc)
    val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return start to end
}

/**
 * 页面主信息：不套卡片，直接用字号分层——
 * 条数是用户最关心的，放大；覆盖范围与账本/钱包数作为次要信息。
 * 无数据时给「为什么空 + 下一步做什么」，而不是一行灰字占一张卡。
 */
@Composable
private fun ExportSummaryHeader(state: BillExportState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when {
            state.loading -> Text(
                stringResource(R.string.bill_export_summary_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.count == 0 -> {
                Text(
                    stringResource(
                        if (state.preset == ExportRangePreset.ALL) {
                            R.string.bill_export_empty
                        } else {
                            R.string.bill_export_range_empty
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.bill_export_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                Text(
                    stringResource(R.string.bill_export_summary_count, state.count),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                rangeTextOf(state)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(
                        R.string.bill_export_summary_scope,
                        state.bookCount,
                        state.walletCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 导出规格区：不套卡片，用「分隔线 + 一行摘要 + 两行列名」压成页脚式信息块。
 *
 * 这样页面只剩「时间范围」一张真控件卡，其它都是排版层级——避免整页一排描边卡片、
 * 又不会像纯文字那样显得信息不足。列名取自 [R.string.bill_flow_header]（导出器真正写的那行）。
 */
@Composable
private fun ExportFormatSpec() {
    val columns = stringResource(R.string.bill_flow_header).split(",")
    val half = (columns.size + 1) / 2
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(R.string.bill_export_format_summary, columns.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            columns.take(half).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
        )
        Text(
            columns.drop(half).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
        )
    }
}

/** 导出结果：成功用主色，失败用 error 色。 */
@Composable
private fun ExportResultText(message: String, failed: Boolean) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 底部固定操作栏。
 *
 * 主按钮一次做完「选位置 + 写文件」（系统保存面板本身就包含建文件，拆开会留下 0 字节空文件）；
 * 失败时主按钮切成「重试导出」复用上次位置，另有「换个位置」走系统面板。
 */
@Composable
private fun ExportActionBar(
    state: BillExportState,
    onChooseLocation: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = if (state.failed) onRetry else onChooseLocation,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading && state.count > 0 && !state.exporting
                ) {
                    Text(
                        stringResource(
                            if (state.failed) R.string.bill_export_retry else R.string.bill_export_action
                        )
                    )
                }
                if (state.failed) {
                    TextButton(onClick = onChooseLocation, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.bill_export_change_location))
                    }
                }
                Text(
                    stringResource(R.string.bill_export_footnote),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 「2026-08-11 ～ 2026-09-08」；没有数据返回 null。 */
@Composable
private fun rangeTextOf(state: BillExportState): String? {
    val first = state.firstDate
    val last = state.lastDate
    return remember(first, last) {
        if (first == null || last == null) null else formatDay(first) + " ～ " + formatDay(last)
    }
}
