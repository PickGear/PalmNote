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
import com.palmnote.app.R
import com.palmnote.data.db.entity.AccountBook
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
    /** 本次导入（文件/OCR 两条路径共用）记到哪个钱包，默认第一个 */
    val importWalletId: Long? = null,
    val wallets: List<Wallet> = emptyList(),
    /** 本次导入记到哪个账本；与手动记账一样可选，默认账本 */
    val importBookId: Long? = null,
    val accountBooks: List<AccountBook> = emptyList()
)

@HiltViewModel
class BillImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billRepository: BillRepository,
    private val cachedWallets: @JvmSuppressWildcards StateFlow<List<Wallet>>,
    private val cachedAccountBooks: @JvmSuppressWildcards StateFlow<List<AccountBook>>,
    private val ocrEngine: OcrEngine
) : ViewModel() {

    private val _state = MutableStateFlow(BillImportState())
    val state: StateFlow<BillImportState> = _state.asStateFlow()
    private val ocrParser = BillOcrParser()

    init {
        viewModelScope.launch {
            val wallets = cachedWallets.first()
            // "全部账本"（ALL_BOOKS_ID）只是首页的筛选视图，不能作为记账目标
            val books = cachedAccountBooks.first().filter { !it.isAllBooks && !it.isHidden }
            _state.update {
                it.copy(
                    wallets = wallets,
                    importWalletId = wallets.firstOrNull()?.id,
                    accountBooks = books,
                    importBookId = (books.find { book -> book.isDefault } ?: books.firstOrNull())?.id
                )
            }
        }
    }

    override fun onCleared() {
        CoroutineScope(Dispatchers.IO).launch { ocrEngine.release() }
        super.onCleared()
    }

    fun setMode(mode: ImportMode) {
        // 切换模式只清解析结果，保留账本/钱包列表与选择（此前整表重置导致切换后 chips 消失）
        _state.value = BillImportState(
            mode = mode,
            wallets = _state.value.wallets,
            importWalletId = _state.value.importWalletId,
            accountBooks = _state.value.accountBooks,
            importBookId = _state.value.importBookId
        )
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
                    val (xlsxFormat, parsed) = withContext(Dispatchers.IO) { BillXlsxImporter().parseBytesWithFormat(rawBytes, diag) }
                    showParsed(context, parsed, xlsxFormat, diag) ?: return@launch
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
                    val parsed = withContext(Dispatchers.IO) { importer.parseFromLines(lines, format, diag) }
                    showParsed(context, parsed, format, diag) ?: return@launch
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_parse_failed, e.message))
            }
        }
    }

    /** 解析结果进入预览；空结果则置错误态并返回 null */
    private fun showParsed(context: Context, parsed: List<ParsedBill>, format: BillCsvImporter.CsvFormat, diag: StringBuilder): Unit? {
        diag.append(context.getString(R.string.bill_import_diag_records, parsed.size) + "\n")
        return if (parsed.isEmpty()) {
            _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_parse_invalid), diagnostic = diag.toString())
            null
        } else {
            // 微信账单默认记到"微信"钱包、支付宝账单默认记到"支付宝"；找不到同名钱包时保持原选择
            val walletId = pickWalletFor(channelOf(format), _state.value.wallets) ?: _state.value.importWalletId
            _state.value = _state.value.copy(
                stage = ImportStage.PREVIEW, parsed = parsed,
                selectedIndices = parsed.indices.toSet(), format = format,
                diagnostic = diag.toString(), importWalletId = walletId
            )
        }
    }

    private fun decodeText(rawBytes: ByteArray, diag: StringBuilder): List<String> {
        val encoding = detectEncoding(rawBytes)
        diag.append(context.getString(R.string.bill_import_diag_encoding, encoding) + "\n")
        val content = try { String(rawBytes, Charset.forName(encoding)) } catch (_: Exception) { String(rawBytes, Charset.forName("UTF-8")) }
        return reflowQuotedLines(
            content.replace("\u0000", "").lines().map { it.trimStart('\uFEFF').trim() }.filter { it.isNotBlank() }
        )
    }

    // CSV 字段内含引号包裹的换行时，lines() 会把一条记录撕成多行（丢行/错位）——
    // 按引号配对把被撕开的行重新拼回
    private fun reflowQuotedLines(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        for (line in lines) {
            if (buf.isNotEmpty()) buf.append('\n')
            buf.append(line)
            if (buf.count { it == '"' } % 2 == 0) {
                out.add(buf.toString())
                buf.clear()
            }
        }
        if (buf.isNotEmpty()) out.add(buf.toString())
        return out
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
                // 微信/支付宝截图默认记到对应钱包（截图判不出渠道时保持原选择）
                val walletId = pickWalletFor(detectChannel(text), _state.value.wallets) ?: _state.value.importWalletId
                if (results.size == 1) {
                    val r = results[0]
                    val amountStr = r.amount?.let { String.format(java.util.Locale.US, "%.2f", it / 100.0) } ?: ""
                    val dateStr = r.date?.let { DateUtils.formatDate(it) } ?: ""
                    _state.value = _state.value.copy(
                        stage = ImportStage.PREVIEW, ocrResults = results, ocrSelectedIndices = setOf(0),
                        ocrImageUri = uri, ocrRawText = text, importWalletId = walletId,
                        ocrAmount = amountStr, ocrMerchant = r.merchant, ocrDate = dateStr,
                        ocrCategory = r.category, ocrNote = r.note
                    )
                } else {
                    _state.value = _state.value.copy(
                        stage = ImportStage.PREVIEW, ocrResults = results,
                        ocrSelectedIndices = results.indices.toSet(),
                        ocrImageUri = uri, ocrRawText = text, importWalletId = walletId
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

    /** 逐笔编辑文件导入预览中的记录（金额/类型/商户/分类/日期/备注） */
    fun updateParsedBill(index: Int, bill: ParsedBill) {
        val list = _state.value.parsed.toMutableList()
        if (index in list.indices) list[index] = bill
        _state.value = _state.value.copy(parsed = list)
    }

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
    fun updateImportWallet(id: Long?) { _state.value = _state.value.copy(importWalletId = id) }

    /** 切换本次导入记到哪个账本 */
    fun updateImportBook(id: Long?) { _state.value = _state.value.copy(importBookId = id) }

    /** 逐笔编辑多笔识别结果（金额/类型/商户/分类/日期/备注） */
    fun updateOcrResult(index: Int, result: OcrBillResult) {
        val list = _state.value.ocrResults.toMutableList()
        if (index in list.indices) list[index] = result
        _state.value = _state.value.copy(ocrResults = list)
    }

    fun importSelected() {
        val s = _state.value
        val selected = s.parsed.filterIndexed { i, _ -> i in s.selectedIndices }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _state.value = s.copy(stage = ImportStage.IMPORTING)
            val count = saveBills(selected, s.importWalletId ?: getWalletId(), resolveBookId(s))
            _state.value = _state.value.copy(stage = ImportStage.DONE, importCount = count)
        }
    }

    fun saveOcrSelected() {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(stage = ImportStage.IMPORTING)
            val existing = billRepository.getAllBills().first()
            val walletId = s.importWalletId ?: getWalletId()
            val bookId = resolveBookId(s)

            // 多笔：按勾选的解析结果逐笔保存；单笔/手动：以表单编辑值为准（表单留空回退解析值）——
            // 此前单笔编辑走解析值分支，用户在表单里改的金额/商户/日期/备注保存时被忽略
            val toSave = if (s.ocrResults.size > 1 && s.ocrSelectedIndices.isNotEmpty()) {
                s.ocrResults.filterIndexed { i, _ -> i in s.ocrSelectedIndices }.mapNotNull { r ->
                    val amount = r.amount ?: return@mapNotNull null
                    val date = r.date ?: System.currentTimeMillis()
                    Bill(amount = amount, type = r.type ?: s.ocrType, category = r.category, note = r.note,
                        date = date, yearMonth = DateUtils.formatYearMonth(date),
                        accountBookId = bookId, walletId = walletId,
                        merchant = r.merchant, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
                }
            } else {
                val parsed = s.ocrResults.firstOrNull()
                val amount = Money.parse(s.ocrAmount)?.cents ?: parsed?.amount
                if (amount == null) {
                    _state.value = _state.value.copy(
                        stage = ImportStage.PREVIEW,
                        error = context.getString(R.string.bill_import_error_invalid_amount)
                    )
                    return@launch
                }
                val billDate = try {
                    java.time.LocalDate.parse(s.ocrDate, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (_: Exception) { parsed?.date ?: System.currentTimeMillis() }
                listOf(
                    Bill(
                        amount = amount, type = parsed?.type ?: s.ocrType,
                        category = s.ocrCategory.ifBlank { parsed?.category ?: "其他" }, note = s.ocrNote,
                        date = billDate, yearMonth = DateUtils.formatYearMonth(billDate),
                        accountBookId = bookId, walletId = walletId,
                        merchant = s.ocrMerchant,
                        createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
                    )
                )
            }

            val count = insertBillsIfNew(toSave, existing)
            _state.value = _state.value.copy(stage = ImportStage.DONE, importCount = count)
        }
    }

    private suspend fun saveBills(parsed: List<ParsedBill>, walletId: Long?, bookId: Long): Int {
        val existing = billRepository.getAllBills().first()
        val bills = parsed.map { pb ->
            Bill(
                amount = pb.amount, type = BillType.from(pb.type), category = pb.category, note = pb.note,
                date = pb.date, yearMonth = DateUtils.formatYearMonth(pb.date),
                accountBookId = bookId, walletId = walletId,
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

    /** 导入目标账本：用户所选 → 默认账本 → 列表首个 → 兜底默认账本 */
    private fun resolveBookId(s: BillImportState): Long =
        s.importBookId ?: s.accountBooks.find { it.isDefault }?.id ?: s.accountBooks.firstOrNull()?.id ?: DEFAULT_BOOK_ID

    /** 账单渠道，用于挑默认钱包 */
    private enum class Channel { WECHAT, ALIPAY }

    private fun channelOf(format: BillCsvImporter.CsvFormat): Channel? = when (format) {
        BillCsvImporter.CsvFormat.WECHAT -> Channel.WECHAT
        BillCsvImporter.CsvFormat.ALIPAY -> Channel.ALIPAY
        else -> null
    }

    /** OCR 截图只能靠关键词判渠道；判不出返回 null，此时保持原有选择 */
    private fun detectChannel(text: String): Channel? = when {
        text.contains("微信") -> Channel.WECHAT
        text.contains("支付宝") -> Channel.ALIPAY
        else -> null
    }

    /**
     * 按渠道挑默认钱包：微信账单默认记到"微信"，支付宝账单默认记到"支付宝"。
     * 找不到同名钱包时返回 null，由调用方保持原选择——用户始终可以手动改成别的。
     */
    private fun pickWalletFor(channel: Channel?, wallets: List<Wallet>): Long? {
        val keywords = when (channel) {
            Channel.WECHAT -> listOf("微信", "wechat", "weixin")
            Channel.ALIPAY -> listOf("支付宝", "alipay", "zhifubao")
            null -> return null
        }
        return wallets.firstOrNull { wallet ->
            val name = wallet.name.lowercase()
            keywords.any { name.contains(it) }
        }?.id
    }

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

    fun reset() {
        _state.value = BillImportState(
            mode = _state.value.mode,
            wallets = _state.value.wallets,
            importWalletId = _state.value.importWalletId,
            accountBooks = _state.value.accountBooks,
            importBookId = _state.value.importBookId
        )
    }

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

        /** 兜底账本 ID，与 Bill.accountBookId 的默认值保持一致 */
        const val DEFAULT_BOOK_ID = 1L
    }
}
