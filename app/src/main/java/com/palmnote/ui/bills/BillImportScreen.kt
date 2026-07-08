package com.palmnote.ui.bills

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.palmnote.R
import com.palmnote.data.export.BillCsvImporter
import com.palmnote.data.export.ParsedBill
import com.palmnote.data.ocr.OcrBillResult
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

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
                                OcrPreviewContent(state, viewModel, onPickAnother = { imagePickerLauncher.launch("image/*") })
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
            Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(64.dp), tint = PrimaryGreenLight)
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
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.parsed, key = { index, _ -> index }) { index, bill ->
                FileBillRow(bill, index in state.selectedIndices) { viewModel.toggleFileSelection(index) }
            }
        }
        Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
            Button(onClick = { viewModel.importSelected() }, modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = state.selectedIndices.isNotEmpty(), shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Outlined.FileUpload, null); Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.bill_import_selected_count, state.selectedIndices.size))
            }
        }
    }
}

@Composable
private fun FileBillRow(bill: ParsedBill, selected: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface), onClick = onToggle) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(bill.merchant.ifEmpty { bill.category }, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                    Text(CurrencyUtils.formatCurrency(bill.amount), fontWeight = FontWeight.Bold, color = if (bill.type == "EXPENSE") ExpenseRed else IncomeGreen)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(DateUtils.formatDisplayDate(context, bill.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(bill.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (bill.note.isNotEmpty()) Text(bill.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun OcrPreviewContent(state: BillImportState, viewModel: BillImportViewModel, onPickAnother: () -> Unit) {
    val isMulti = state.ocrResults.size > 1
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.ocrImageUri != null) {
            AsyncImage(model = state.ocrImageUri, contentDescription = stringResource(R.string.bill_import_screenshot), modifier = Modifier.fillMaxWidth().height(160.dp).padding(16.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Fit)
        }
        if (isMulti) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bill_import_ocr_recognized, state.ocrResults.size, state.ocrSelectedIndices.size), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row { TextButton(onClick = { viewModel.selectAllOcr() }) { Text(stringResource(R.string.bill_import_select_all)) }; TextButton(onClick = { viewModel.deselectAllOcr() }) { Text(stringResource(R.string.bill_import_select_none)) } }
            }
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                itemsIndexed(state.ocrResults, key = { index, _ -> index }) { index, result ->
                    OcrItem(result, index in state.ocrSelectedIndices) { viewModel.toggleOcrSelection(index) }
                }
            }
        } else {
            Text(stringResource(R.string.bill_import_ocr_result), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                EditField(stringResource(R.string.bill_import_amount), state.ocrAmount, viewModel::updateOcrAmount, prefix = "¥ ")
                EditField(stringResource(R.string.bill_import_merchant), state.ocrMerchant, viewModel::updateOcrMerchant)
                EditField(stringResource(R.string.bill_import_date), state.ocrDate, viewModel::updateOcrDate, placeholder = "yyyy-MM-dd")
                EditField(stringResource(R.string.bill_import_category), state.ocrCategory, viewModel::updateOcrCategory)
                EditField(stringResource(R.string.bill_import_note), state.ocrNote, viewModel::updateOcrNote)
                if (state.error != null) { Spacer(modifier = Modifier.height(8.dp)); Text(state.error, color = ExpenseRed, style = MaterialTheme.typography.bodySmall) }
            }
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
}

@Composable
private fun OcrItem(result: OcrBillResult, selected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.medium).background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = selected, onCheckedChange = null)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (result.merchant.isNotBlank()) Text(result.merchant, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row {
                if (result.date != null) { Text(DateUtils.formatDate(result.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.width(8.dp)) }
                if (result.category != "其他") Text(stringResource(getLocalizedCategoryName(result.category)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (result.amount != null) Text("-${CurrencyUtils.formatCurrency(result.amount)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ExpenseRed)
    }
}

@Composable
private fun EditField(label: String, value: String, onValueChange: (String) -> Unit, prefix: String? = null, placeholder: String? = null) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        prefix = prefix?.let { { Text(it) } }, placeholder = placeholder?.let { { Text(it) } })
}
