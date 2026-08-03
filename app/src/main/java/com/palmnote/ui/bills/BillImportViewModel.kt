package com.palmnote.ui.bills
import kotlin.jvm.JvmSuppressWildcards
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.PaymentMethod
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.R
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.export.BillCsvImporter
import com.palmnote.data.export.BillXlsxImporter
import com.palmnote.data.export.ParsedBill
import com.palmnote.data.ocr.BillOcrParser
import com.palmnote.data.ocr.OcrBillResult
import com.palmnote.data.ocr.OcrEngine
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.Money
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.util.DateUtils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset


enum class ImportMode { FILE, OCR }
enum class ImportStage { IDLE, PARSING, PREVIEW, IMPORTING, DONE, ERROR }

@Stable
data class BillImportState(
    val mode: ImportMode = ImportMode.FILE,
    val stage: ImportStage = ImportStage.IDLE,
    val parsed: List<ParsedBill> = emptyList(),
    val selectedIndices: Set<Int> = emptySet(),
    val importCount: Int = 0,
    val error: String? = null,
    val fileName: String = "",
    val format: BillCsvImporter.CsvFormat = BillCsvImporter.CsvFormat.UNKNOWN,
    val diagnostic: String = "",
    val ocrResults: List<OcrBillResult> = emptyList(),
    val ocrSelectedIndices: Set<Int> = emptySet(),
    val ocrImageUri: Uri? = null,
    val ocrRawText: String = "",
    val ocrAmount: String = "",
    val ocrMerchant: String = "",
    val ocrDate: String = "",
    val ocrCategory: String = "其他",
    val ocrNote: String = "",
    val ocrType: BillType = BillType.EXPENSE,
    val ocrWalletId: Long? = null,
    val wallets: List<com.palmnote.data.db.entity.Wallet> = emptyList()
)

@HiltViewModel
class BillImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billRepository: BillRepository,
    private val cachedWallets: @JvmSuppressWildcards StateFlow<List<Wallet>>,
    private val ocrEngine: OcrEngine
) : ViewModel() {

    private val _state = MutableStateFlow(BillImportState())
    val state: StateFlow<BillImportState> = _state.asStateFlow()
    private val ocrParser = BillOcrParser()

    init {
        viewModelScope.launch {
            cachedWallets.first().let { wallets ->
                _state.update { it.copy(wallets = wallets, ocrWalletId = wallets.firstOrNull()?.id) }
            }
        }
    }

    override fun onCleared() {
        CoroutineScope(Dispatchers.IO).launch { ocrEngine.release() }
        super.onCleared()
    }

    fun setMode(mode: ImportMode) {
        _state.value = BillImportState(mode = mode)
    }

    fun parseFile(@ApplicationContext context: Context, uri: Uri, fileName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(stage = ImportStage.PARSING, fileName = fileName, error = null, diagnostic = "")
            try {
                val diag = StringBuilder()
                // 先查文件大小，超限直接提示，避免 readBytes 整读大文件 OOM
                val fileSize = withContext(Dispatchers.IO) {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                }
                if (fileSize > MAX_IMPORT_SIZE) {
                    _state.value = _state.value.copy(
                        stage = ImportStage.ERROR,
                        error = context.getString(R.string.bill_import_error_too_large)
                    )
                    return@launch
                }
                val rawBytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (rawBytes == null || rawBytes.isEmpty()) {
                    _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_read_file))
                    return@launch
                }
                diag.append(context.getString(R.string.bill_import_diag_file_size, rawBytes.size) + "\n")

                val isZip = rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte() && rawBytes[2] == 0x03.toByte() && rawBytes[3] == 0x04.toByte()
                diag.append(context.getString(R.string.bill_import_diag_format, if (isZip) "XLSX(ZIP)" else "CSV/Text") + "\n")

                if (isZip) {
                    val parsed = withContext(Dispatchers.IO) { BillXlsxImporter().parseBytes(rawBytes, diag) }
                    diag.append(context.getString(R.string.bill_import_diag_records, parsed.size) + "\n")
                    if (parsed.isEmpty()) {
                        _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_parse_invalid), diagnostic = diag.toString())
                        return@launch
                    }
                    _state.value = _state.value.copy(stage = ImportStage.PREVIEW, parsed = parsed, selectedIndices = parsed.indices.toSet(), format = BillCsvImporter.CsvFormat.WECHAT, diagnostic = diag.toString())
                } else {
                    val lines = withContext(Dispatchers.IO) { decodeText(rawBytes, diag) }
                    diag.append(context.getString(R.string.bill_import_diag_lines, lines.size) + "\n")
                    if (lines.isNotEmpty()) diag.append(context.getString(R.string.bill_import_diag_first_line, lines.first().take(80)) + "\n")
                    val importer = BillCsvImporter()
                    val format = importer.detectFormat(lines)
                    diag.append(context.getString(R.string.bill_import_diag_detected_format, format) + "\n")
                    if (format == BillCsvImporter.CsvFormat.UNKNOWN) {
                        _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_format_unknown), diagnostic = diag.toString())
                        return@launch
                    }
                    val parsed = importer.parseFromLines(lines, format, diag)
                    diag.append(context.getString(R.string.bill_import_diag_records, parsed.size) + "\n")
                    if (parsed.isEmpty()) {
                        _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_parse_invalid), diagnostic = diag.toString())
                        return@launch
                    }
                    _state.value = _state.value.copy(stage = ImportStage.PREVIEW, parsed = parsed, selectedIndices = parsed.indices.toSet(), format = format, diagnostic = diag.toString())
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_parse_failed, e.message))
            }
        }
    }

    private fun decodeText(rawBytes: ByteArray, diag: StringBuilder): List<String> {
        val encoding = detectEncoding(rawBytes)
        diag.append(context.getString(R.string.bill_import_diag_encoding, encoding) + "\n")
        val content = try { String(rawBytes, Charset.forName(encoding)) } catch (_: Exception) { String(rawBytes, Charset.forName("UTF-8")) }
        return content.replace("\u0000", "").lines().map { it.trimStart('\uFEFF').trim() }.filter { it.isNotBlank() }
    }

    fun processOcrImage(@ApplicationContext context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(stage = ImportStage.PARSING, ocrImageUri = uri, error = null)
            try {
                val text = withContext(Dispatchers.IO) {
                    // 先采样解码获取尺寸，再按需降采样，避免大图 OOM
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    val sample = BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 2048)
                    }
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, sample) }
                        ?: throw Exception(context.getString(R.string.bill_import_error_read_image))
                    val rotated = rotateBitmapIfNeeded(context, uri, bitmap)
                    ocrEngine.recognize(rotated)
                }
                val results = ocrParser.parseMultiple(text)
                if (results.isEmpty()) {
                    _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_ocr_invalid), ocrRawText = text)
                    return@launch
                }
                if (results.size == 1) {
                    val r = results[0]
                    val amountStr = r.amount?.let { String.format(java.util.Locale.US, "%.2f", it / 100.0) } ?: ""
                    val dateStr = r.date?.let { DateUtils.formatDate(it) } ?: ""
                    _state.value = _state.value.copy(
                        stage = ImportStage.PREVIEW, ocrResults = results, ocrSelectedIndices = setOf(0),
                        ocrImageUri = uri, ocrRawText = text,
                        ocrAmount = amountStr, ocrMerchant = r.merchant, ocrDate = dateStr,
                        ocrCategory = r.category, ocrNote = r.note
                    )
                } else {
                    _state.value = _state.value.copy(
                        stage = ImportStage.PREVIEW, ocrResults = results,
                        ocrSelectedIndices = results.indices.toSet(),
                        ocrImageUri = uri, ocrRawText = text
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_ocr_failed, e.message))
            }
        }
    }

    fun toggleFileSelection(index: Int) {
        val s = _state.value
        _state.value = s.copy(selectedIndices = if (index in s.selectedIndices) s.selectedIndices - index else s.selectedIndices + index)
    }
    fun selectAllFiles() { _state.value = _state.value.copy(selectedIndices = _state.value.parsed.indices.toSet()) }
    fun deselectAllFiles() { _state.value = _state.value.copy(selectedIndices = emptySet()) }

    fun toggleOcrSelection(index: Int) {
        val s = _state.value
        _state.value = s.copy(ocrSelectedIndices = if (index in s.ocrSelectedIndices) s.ocrSelectedIndices - index else s.ocrSelectedIndices + index)
    }
    fun selectAllOcr() { _state.value = _state.value.copy(ocrSelectedIndices = _state.value.ocrResults.indices.toSet()) }
    fun deselectAllOcr() { _state.value = _state.value.copy(ocrSelectedIndices = emptySet()) }

    fun updateOcrAmount(v: String) { _state.value = _state.value.copy(ocrAmount = v) }
    fun updateOcrMerchant(v: String) { _state.value = _state.value.copy(ocrMerchant = v) }
    fun updateOcrDate(v: String) { _state.value = _state.value.copy(ocrDate = v) }
    fun updateOcrCategory(v: String) { _state.value = _state.value.copy(ocrCategory = v) }
    fun updateOcrNote(v: String) { _state.value = _state.value.copy(ocrNote = v) }
    fun updateOcrType(t: BillType) { _state.value = _state.value.copy(ocrType = t) }
    fun updateOcrWallet(id: Long?) { _state.value = _state.value.copy(ocrWalletId = id) }

    fun importSelected() {
        val s = _state.value
        val selected = s.parsed.filterIndexed { i, _ -> i in s.selectedIndices }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _state.value = s.copy(stage = ImportStage.IMPORTING)
            val count = saveBills(selected)
            _state.value = _state.value.copy(stage = ImportStage.DONE, importCount = count)
        }
    }

    fun saveOcrSelected() {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(stage = ImportStage.IMPORTING)
            val existing = billRepository.getAllBills().first()
            val walletId = s.ocrWalletId ?: getWalletId()

            val toSave = if (s.ocrResults.isNotEmpty() && s.ocrSelectedIndices.isNotEmpty()) {
                s.ocrResults.filterIndexed { i, _ -> i in s.ocrSelectedIndices }.mapNotNull { r ->
                    val amount = r.amount ?: return@mapNotNull null
                    val date = r.date ?: System.currentTimeMillis()
                    Bill(amount = amount, type = s.ocrType, category = r.category, note = r.note,
                        date = date, yearMonth = DateUtils.formatYearMonth(date), walletId = walletId,
                        merchant = r.merchant, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
                }
            } else {
                val amount = Money.parse(s.ocrAmount)?.cents
                if (amount == null) { _state.value = _state.value.copy(stage = ImportStage.PREVIEW, error = context.getString(R.string.bill_import_error_invalid_amount)); return@launch }
                val billDate = try { java.time.LocalDate.parse(s.ocrDate, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
                catch (_: Exception) { System.currentTimeMillis() }
                listOf(Bill(amount = amount, type = s.ocrType, category = s.ocrCategory, note = s.ocrNote,
                    date = billDate, yearMonth = DateUtils.formatYearMonth(billDate), walletId = walletId,
                    merchant = s.ocrMerchant, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            }

            val count = insertBillsIfNew(toSave, existing)
            _state.value = _state.value.copy(stage = ImportStage.DONE, importCount = count)
        }
    }

    private suspend fun saveBills(parsed: List<ParsedBill>): Int {
        val walletId = getWalletId()
        val existing = billRepository.getAllBills().first()
        val bills = parsed.map { pb ->
            Bill(
                amount = pb.amount, type = BillType.from(pb.type), category = pb.category, note = pb.note,
                date = pb.date, yearMonth = DateUtils.formatYearMonth(pb.date), walletId = walletId,
                paymentMethod = PaymentMethod.from(pb.paymentMethod), merchant = pb.merchant,
                transactionId = pb.transactionId,
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
        }
        return insertBillsIfNew(bills, existing)
    }

    private suspend fun getWalletId(): Long? = try {
        cachedWallets.first().firstOrNull()?.id
    } catch (_: Exception) { null }

    private suspend fun insertBillsIfNew(bills: List<Bill>, existing: List<Bill>): Int {
        var count = 0
        // transactionId 是最可靠唯一键（微信/支付宝交易单号）
        val existingTxIds = existing.mapNotNull { it.transactionId.takeIf { t -> t.isNotBlank() } }.toSet()
        // 批内去重，避免同一文件内重复记录被重复插入
        val seenTxIds = mutableSetOf<String>()
        val seenByAttrs = mutableSetOf<String>()
        for (bill in bills) {
            val txId = bill.transactionId.takeIf { it.isNotBlank() }
            if (txId != null) {
                if (existingTxIds.contains(txId) || !seenTxIds.add(txId)) continue
            } else {
                val attrKey = "${bill.date}_${bill.amount}_${bill.merchant}_${bill.type}"
                if (existing.any { it.date == bill.date && it.amount == bill.amount && it.merchant == bill.merchant && it.type == bill.type } || !seenByAttrs.add(attrKey)) continue
            }
            billRepository.insertBill(bill)
            count++
        }
        return count
    }

    fun reset() { _state.value = BillImportState(mode = _state.value.mode) }

    private fun rotateBitmapIfNeeded(@ApplicationContext context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> null
                }
            }
        } catch (_: Exception) { null }
        return if (degrees != null && degrees != 0) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
            bitmap.recycle()
            rotated
        } else bitmap
    }

    private fun detectEncoding(rawBytes: ByteArray): String {
        if (rawBytes.size < 2) return "UTF-8"
        val b0 = rawBytes[0].toInt() and 0xFF
        val b1 = rawBytes[1].toInt() and 0xFF
        if (b0 == 0xFE && b1 == 0xFF) return "UTF-16BE"
        if (b0 == 0xFF && b1 == 0xFE) return "UTF-16LE"
        if (b0 == 0xEF && b1 == 0xBB && rawBytes.size > 2 && (rawBytes[2].toInt() and 0xFF) == 0xBF) return "UTF-8"
        for (enc in listOf("UTF-8", "GBK", "GB2312", "GB18030", "UTF-16LE", "UTF-16BE")) {
            try {
                val test = String(rawBytes, Charset.forName(enc))
                if (test.contains("交易") || test.contains("金额") || test.contains("收/支")) return enc
            } catch (_: Exception) { }
        }
        return "UTF-8"
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / (sample * 2) >= maxSize || h / (sample * 2) >= maxSize) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        /** 导入文件大小上限（30MB），避免整读大文件导致 OOM */
        const val MAX_IMPORT_SIZE = 30L * 1024 * 1024
    }
}
