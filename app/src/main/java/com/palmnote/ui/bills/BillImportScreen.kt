package com.palmnote.ui.bills

import android.net.Uri
import com.palmnote.domain.model.BillType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.palmnote.app.R
import com.palmnote.data.export.BillCsvImporter
import com.palmnote.data.export.ParsedBill
import com.palmnote.data.ocr.OcrBillResult
import com.palmnote.domain.model.Money
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillImportScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BillImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val unknownFileLabel = stringResource(R.string.bill_import_unknown_file)
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: unknownFileLabel
            viewModel.parseFile(context, uri, fileName)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.processOcrImage(context, uri)
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.bill_import_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.stage == ImportStage.IDLE) {
                ModeSelection(
                    onSelectFile = { viewModel.setMode(ImportMode.FILE); filePickerLauncher.launch(arrayOf("text/*", "*/*")) },
                    onSelectOcr = { viewModel.setMode(ImportMode.OCR); imagePickerLauncher.launch("image/*") }
                )
            } else {
                TabRow(
                    selectedTabIndex = if (state.mode == ImportMode.FILE) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(selected = state.mode == ImportMode.FILE, onClick = {
                        viewModel.setMode(ImportMode.FILE)
                        filePickerLauncher.launch(arrayOf("text/*", "*/*"))
                    }) { Text(stringResource(R.string.bill_import_file), modifier = Modifier.padding(12.dp)) }
                    Tab(selected = state.mode == ImportMode.OCR, onClick = {
                        viewModel.setMode(ImportMode.OCR)
                        imagePickerLauncher.launch("image/*")
                    }) { Text(stringResource(R.string.bill_import_image), modifier = Modifier.padding(12.dp)) }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.stage) {
                        ImportStage.IDLE -> {}
                        ImportStage.PARSING -> LoadingContent(stringResource(R.string.bill_import_parsing))
                        ImportStage.IMPORTING -> LoadingContent(stringResource(R.string.bill_import_importing))
                        ImportStage.DONE -> DoneContent(
                            count = state.importCount,
                            onBack = onNavigateBack,
                            onContinue = { viewModel.reset() }
                        )
                        ImportStage.ERROR -> ErrorContent(
                            error = state.error ?: stringResource(R.string.bill_import_failed),
                            diagnostic = state.diagnostic,
                            onRetry = {
                                viewModel.reset()
                                if (state.mode == ImportMode.FILE) filePickerLauncher.launch(arrayOf("text/*", "*/*"))
                                else imagePickerLauncher.launch("image/*")
                            },
                            onBack = onNavigateBack
                        )
                        ImportStage.PREVIEW -> {
                            if (state.mode == ImportMode.FILE) {
                                FilePreviewContent(state, viewModel, onPickAgain = { filePickerLauncher.launch(arrayOf("text/*", "*/*")) })
                            } else {
                                OcrPreviewContent(state, viewModel, context, onPickAnother = { imagePickerLauncher.launch("image/*") })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelection(onSelectFile: () -> Unit, onSelectOcr: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.bill_import_select_method), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onSelectFile),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FileUpload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.bill_import_file), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.bill_import_file_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onSelectOcr),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CameraAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.bill_import_image), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.bill_import_image_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text)
        }
    }
}

@Composable
private fun DoneContent(count: Int, onBack: () -> Unit, onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.bill_import_complete), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.bill_import_success_count, count), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack) { Text(stringResource(R.string.back)) }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onContinue) { Text(stringResource(R.string.bill_import_continue)) }
        }
    }
}

@Composable
private fun ErrorContent(error: String, diagnostic: String = "", onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState())) {
            Icon(Icons.Outlined.ErrorOutline, null, modifier = Modifier.size(64.dp), tint = ExpenseRed)
            Spacer(modifier = Modifier.height(16.dp))
            Text(error, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            if (diagnostic.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text(diagnostic, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.bill_import_reselect)) }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
    }
}

@Composable
private fun FilePreviewContent(state: BillImportState, viewModel: BillImportViewModel, onPickAgain: () -> Unit) {
    val context = LocalContext.current
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(stringResource(R.string.bill_import_total_records, state.parsed.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.bill_import_selected_records, state.selectedIndices.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.selectAllFiles() }) { Text(stringResource(R.string.bill_import_select_all)) }
                TextButton(onClick = { viewModel.deselectAllFiles() }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = onPickAgain) { Text(stringResource(R.string.bill_import_reselect)) }
            }
        }
        if (state.format != BillCsvImporter.CsvFormat.UNKNOWN) {
            val label = when (state.format) { BillCsvImporter.CsvFormat.WECHAT -> stringResource(R.string.bill_import_wechat_bill); BillCsvImporter.CsvFormat.ALIPAY -> stringResource(R.string.bill_import_alipay_bill); else -> "" }
            val color = when (state.format) { BillCsvImporter.CsvFormat.WECHAT -> IncomeGreen; BillCsvImporter.CsvFormat.ALIPAY -> InfoBlue; else -> MaterialTheme.colorScheme.onSurfaceVariant }
            if (label.isNotEmpty()) {
                Surface(color = color.copy(alpha = 0.1f), shape = MaterialTheme.shapes.small, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }
        // 记到哪个账本、从哪个钱包出，均由用户选择（文件与 OCR 导入共用同一选择）
        BookChipRow(state, viewModel, context, modifier = Modifier.padding(horizontal = 16.dp))
        WalletChipRow(state, viewModel, context, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.parsed, key = { index, _ -> index }) { index, bill ->
                FileBillRow(
                    bill,
                    index in state.selectedIndices,
                    onToggle = { viewModel.toggleFileSelection(index) },
                    onEdit = { editingIndex = index }
                )
            }
        }
        Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
            Button(onClick = { viewModel.importSelected() }, modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = state.selectedIndices.isNotEmpty(), shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Outlined.FileUpload, null); Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.bill_import_selected_count, state.selectedIndices.size))
            }
        }
    }

    // 逐笔编辑文件导入记录
    editingIndex?.let { idx ->
        state.parsed.getOrNull(idx)?.let { bill ->
            FileEditDialog(
                bill = bill,
                onDismiss = { editingIndex = null },
                onSave = { updated ->
                    viewModel.updateParsedBill(idx, updated)
                    editingIndex = null
                }
            )
        }
    }
}

@Composable
private fun FileBillRow(bill: ParsedBill, selected: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface), onClick = onToggle) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(bill.merchant.ifEmpty { bill.category }, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        CurrencyUtils.formatCurrency(context, bill.amount.toMoney()),
                        fontWeight = FontWeight.Bold,
                        color = if (bill.type == BillType.EXPENSE.value) ExpenseRed else IncomeGreen
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(DateUtils.formatDisplayDate(context, bill.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(bill.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (bill.note.isNotEmpty()) Text(bill.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.bill_import_edit_bill),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 文件导入预览的逐笔编辑对话框：金额/类型/商户/分类/日期/备注（表单与 OCR 编辑共用） */
@Composable
private fun FileEditDialog(bill: ParsedBill, onDismiss: () -> Unit, onSave: (ParsedBill) -> Unit) {
    var form by remember {
        mutableStateOf(
            OcrEditForm(
                amount = String.format(Locale.US, "%.2f", bill.amount / 100.0),
                isIncome = bill.type == BillType.INCOME.value,
                merchant = bill.merchant,
                category = bill.category,
                note = bill.note,
                dateStr = DateUtils.formatDate(bill.date)
            )
        )
    }
    var dateError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bill_import_edit_bill), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OcrEditFields(
                    form = form,
                    onForm = { form = it },
                    dateError = dateError,
                    onDate = { dateError = false }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = Money.parse(form.amount)?.cents
                if (cents == null || cents <= 0) return@TextButton
                val dateMillis = if (form.dateStr.isBlank()) bill.date else runCatching {
                    LocalDate.parse(form.dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrElse {
                    dateError = true
                    return@TextButton
                }
                onSave(
                    bill.copy(
                        amount = cents,
                        type = if (form.isIncome) BillType.INCOME.value else BillType.EXPENSE.value,
                        merchant = form.merchant.trim(),
                        category = form.category.ifBlank { "其他" },
                        note = form.note.trim(),
                        date = dateMillis
                    )
                )
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun OcrPreviewContent(state: BillImportState, viewModel: BillImportViewModel, context: android.content.Context, onPickAnother: () -> Unit) {
    val isMulti = state.ocrResults.size > 1
    var showZoom by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.ocrImageUri != null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                AsyncImage(
                    model = state.ocrImageUri,
                    contentDescription = stringResource(R.string.bill_import_screenshot),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { showZoom = true },
                    contentScale = ContentScale.Fit
                )
                Text(
                    stringResource(R.string.bill_import_ocr_tap_zoom),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (isMulti) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bill_import_ocr_recognized, state.ocrResults.size, state.ocrSelectedIndices.size), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row { TextButton(onClick = { viewModel.selectAllOcr() }) { Text(stringResource(R.string.bill_import_select_all)) }; TextButton(onClick = { viewModel.deselectAllOcr() }) { Text(stringResource(R.string.bill_import_select_none)) } }
            }
            // 整批记到哪个账本、从哪个钱包出，由用户选择
            BookChipRow(state, viewModel, context, modifier = Modifier.padding(horizontal = 16.dp))
            WalletChipRow(state, viewModel, context, modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                itemsIndexed(state.ocrResults, key = { index, _ -> index }) { index, result ->
                    OcrItem(
                        result = result,
                        selected = index in state.ocrSelectedIndices,
                        onClick = { viewModel.toggleOcrSelection(index) },
                        onEdit = { editingIndex = index }
                    )
                }
            }
        } else {
            OcrSingleEditor(state, viewModel, context)
        }
        var showRaw by remember { mutableStateOf(false) }
        TextButton(onClick = { showRaw = !showRaw }, modifier = Modifier.padding(horizontal = 16.dp)) { Text(if (showRaw) stringResource(R.string.bill_import_hide_raw) else stringResource(R.string.bill_import_show_raw)) }
        if (showRaw && state.ocrRawText.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(state.ocrRawText, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onPickAnother, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.bill_import_reselect)) }
            Button(onClick = { viewModel.saveOcrSelected() }, modifier = Modifier.weight(1f), enabled = state.ocrSelectedIndices.isNotEmpty()) {
                Text(if (isMulti) stringResource(R.string.bill_import_batch_save, state.ocrSelectedIndices.size) else stringResource(R.string.bill_import_save))
            }
        }
    }
    if (showZoom && state.ocrImageUri != null) {
        ZoomableImageDialog(uri = state.ocrImageUri, onDismiss = { showZoom = false })
    }
    editingIndex?.let { idx ->
        state.ocrResults.getOrNull(idx)?.let { result ->
            OcrEditDialog(
                result = result,
                onDismiss = { editingIndex = null },
                onSave = { updated ->
                    viewModel.updateOcrResult(idx, updated)
                    editingIndex = null
                }
            )
        }
    }
}

/** 单笔识别结果的可编辑表单（收支/钱包/金额/商户/日期/分类/备注） */
@Composable
private fun OcrSingleEditor(state: BillImportState, viewModel: BillImportViewModel, context: android.content.Context, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.bill_import_ocr_result), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(BillType.EXPENSE to R.string.bill_expense, BillType.INCOME to R.string.bill_income).forEach { (t, labelRes) ->
                FilterChip(
                    selected = state.ocrType == t,
                    onClick = { viewModel.updateOcrType(t) },
                    label = { Text(stringResource(labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = (if (t == BillType.EXPENSE) ExpenseRed else StatusActive).copy(alpha = 0.15f),
                        selectedLabelColor = if (t == BillType.EXPENSE) ExpenseRed else StatusActive
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        BookChipRow(state, viewModel, context)
        Spacer(modifier = Modifier.height(4.dp))
        WalletChipRow(state, viewModel, context)
        Spacer(modifier = Modifier.height(12.dp))
        EditField(stringResource(R.string.bill_import_amount), state.ocrAmount, viewModel::updateOcrAmount, prefix = "¥ ")
        EditField(stringResource(R.string.bill_import_merchant), state.ocrMerchant, viewModel::updateOcrMerchant)
        DateField(stringResource(R.string.bill_import_date), state.ocrDate, viewModel::updateOcrDate)
        EditField(stringResource(R.string.bill_import_category), state.ocrCategory, viewModel::updateOcrCategory)
        EditField(stringResource(R.string.bill_import_note), state.ocrNote, viewModel::updateOcrNote)
        if (state.error != null) { Spacer(modifier = Modifier.height(8.dp)); Text(state.error, color = ExpenseRed, style = MaterialTheme.typography.bodySmall) }
    }
}

/** 账本选择 chips 行：这批账单记进哪个账本（与首页"当前账本"相互独立） */
@Composable
private fun BookChipRow(state: BillImportState, viewModel: BillImportViewModel, context: android.content.Context, modifier: Modifier = Modifier) {
    if (state.accountBooks.isEmpty()) return
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.bill_book),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.accountBooks, key = { it.id }) { book ->
                FilterChip(
                    selected = state.importBookId == book.id,
                    onClick = { viewModel.updateImportBook(book.id) },
                    label = { Text(book.getDisplayName(context), fontSize = 11.sp) }
                )
            }
        }
    }
}

/** 钱包选择 chips 行：这笔钱从哪个账户出（微信/支付宝/现金…，默认按账单渠道推断） */
@Composable
private fun WalletChipRow(state: BillImportState, viewModel: BillImportViewModel, context: android.content.Context, modifier: Modifier = Modifier) {
    if (state.wallets.isEmpty()) return
    Column(modifier = modifier) {
        Text(stringResource(R.string.bill_wallet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.wallets, key = { it.id }) { wallet ->
                FilterChip(
                    selected = state.importWalletId == wallet.id,
                    onClick = { viewModel.updateImportWallet(wallet.id) },
                    label = { Text(com.palmnote.ui.components.getLocalizedWalletDisplayName(wallet, context), fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
private fun OcrItem(result: OcrBillResult, selected: Boolean, onClick: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.medium).background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = selected, onCheckedChange = null)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            OcrItemInfo(result)
        }
        if (result.amount != null) {
            // 按笔类型显示符号与颜色（收+/绿，支-/红），与保存时的类型判定一致
            val isIncome = result.type == BillType.INCOME
            Text(
                (if (isIncome) "+" else "-") + CurrencyUtils.formatCurrency(context, result.amount.toMoney()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) StatusActive else ExpenseRed
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.bill_import_ocr_edit),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 识别结果条目的信息列（商户/日期/分类/备注） */
@Composable
private fun OcrItemInfo(result: OcrBillResult) {
    if (result.merchant.isNotBlank()) {
        Text(
            result.merchant, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, maxLines = 1
        )
    }
    Row {
        if (result.date != null) {
            Text(
                DateUtils.formatDate(result.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            // 电商列表页无日期，提示将按今天记录，可点编辑修改
            Text(
                stringResource(R.string.bill_import_ocr_no_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (result.category != "其他") {
            val impResId = getLocalizedCategoryName(result.category)
            Text(
                if (impResId != null) stringResource(impResId) else result.category,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    if (result.note.isNotBlank()) {
        Text(
            result.note, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
        )
    }
}

@Composable
private fun EditField(label: String, value: String, onValueChange: (String) -> Unit, prefix: String? = null, placeholder: String? = null) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        prefix = prefix?.let { { Text(it) } }, placeholder = placeholder?.let { { Text(it) } })
}

/** 日期字段：可手输 yyyy-MM-dd，也可点日历图标弹出日期选择器 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String, onValueChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        placeholder = { Text("yyyy-MM-dd") },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.bill_import_date))
            }
        }
    )
    if (showPicker) {
        val initialMillis = runCatching {
            LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }.getOrElse { LocalDate.now() }
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onValueChange(
                            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        )
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }
}

/** 逐笔编辑对话框的表单值 */
private data class OcrEditForm(
    val amount: String,
    val isIncome: Boolean,
    val merchant: String,
    val category: String,
    val note: String,
    val dateStr: String
)

/** 多笔识别结果的逐笔编辑对话框：金额/类型/商户/分类/日期/备注 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrEditDialog(result: OcrBillResult, onDismiss: () -> Unit, onSave: (OcrBillResult) -> Unit) {
    var form by remember {
        mutableStateOf(
            OcrEditForm(
                amount = result.amount?.let { String.format(Locale.US, "%.2f", it / 100.0) } ?: "",
                isIncome = result.type == BillType.INCOME,
                merchant = result.merchant,
                category = result.category,
                note = result.note,
                dateStr = result.date?.let { DateUtils.formatDate(it) } ?: ""
            )
        )
    }
    var dateError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bill_import_ocr_edit_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OcrEditFields(
                    form = form,
                    onForm = { form = it },
                    dateError = dateError,
                    onDate = { dateError = false }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = Money.parse(form.amount)?.cents
                if (cents == null || cents <= 0) return@TextButton
                val dateMillis = if (form.dateStr.isBlank()) null else runCatching {
                    LocalDate.parse(form.dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrElse {
                    dateError = true
                    return@TextButton
                }
                onSave(
                    result.copy(
                        amount = cents,
                        type = if (form.isIncome) BillType.INCOME else BillType.EXPENSE,
                        merchant = form.merchant.trim(),
                        category = form.category.ifBlank { "其他" },
                        note = form.note.trim(),
                        date = dateMillis
                    )
                )
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** 逐笔编辑对话框的表单区（收支 chips + 各字段） */
@Composable
private fun OcrEditFields(
    form: OcrEditForm,
    onForm: (OcrEditForm) -> Unit,
    dateError: Boolean,
    onDate: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(BillType.EXPENSE to R.string.bill_expense, BillType.INCOME to R.string.bill_income).forEach { (t, labelRes) ->
            FilterChip(
                selected = if (t == BillType.INCOME) form.isIncome else !form.isIncome,
                onClick = { onForm(form.copy(isIncome = t == BillType.INCOME)) },
                label = { Text(stringResource(labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = (if (t == BillType.EXPENSE) ExpenseRed else StatusActive).copy(alpha = 0.15f),
                    selectedLabelColor = if (t == BillType.EXPENSE) ExpenseRed else StatusActive
                )
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    EditField(stringResource(R.string.bill_import_amount), form.amount, { onForm(form.copy(amount = it)) }, prefix = "¥ ")
    EditField(stringResource(R.string.bill_import_merchant), form.merchant, { onForm(form.copy(merchant = it)) })
    DateField(stringResource(R.string.bill_import_date), form.dateStr, { onForm(form.copy(dateStr = it)); onDate() })
    if (dateError) Text("yyyy-MM-dd", color = ExpenseRed, style = MaterialTheme.typography.labelSmall)
    EditField(stringResource(R.string.bill_import_category), form.category, { onForm(form.copy(category = it)) })
    EditField(stringResource(R.string.bill_import_note), form.note, { onForm(form.copy(note = it)) })
}

/** 全屏可缩放查看识别原图：双指缩放 + 拖动，点背景关闭 */
@Composable
private fun ZoomableImageDialog(uri: Uri, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.bill_import_screenshot),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .clickable(enabled = false) { }
            )
            Text(
                "×%.1f".format(scale),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
            )
        }
    }
}
