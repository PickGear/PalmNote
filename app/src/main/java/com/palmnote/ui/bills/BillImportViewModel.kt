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
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.components.CategoryItem
import com.palmnote.ui.components.toComposeColor
import com.palmnote.data.export.BillCsvImporter
import com.palmnote.data.export.BillXlsxImporter
import com.palmnote.data.export.FailedRowsCsvWriter
import com.palmnote.data.export.ImportFailure
import com.palmnote.data.export.ImportFailureReason
import com.palmnote.data.export.ParsedBill
import com.palmnote.data.export.WrongPasswordException
import com.palmnote.data.export.ZipArchiveReader
import com.palmnote.data.ocr.BillOcrParser
import com.palmnote.data.ocr.FieldConfidence
import com.palmnote.data.ocr.OcrBillResult
import com.palmnote.data.ocr.OcrEngine
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.Money
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.util.AppLogger
import com.palmnote.domain.util.DateUtils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset


enum class ImportMode { FILE, OCR }
enum class ImportStage { IDLE, PARSING, PASSWORD, PREVIEW, IMPORTING, DONE, ERROR }

/** 一次批量写入的结果：成功写入的行 id 列表 + 因重复而被跳过的条数 */
private data class InsertOutcome(val insertedIds: List<Long>, val skipped: Int)

@Stable
data class BillImportState(
    val mode: ImportMode = ImportMode.FILE,
    val stage: ImportStage = ImportStage.IDLE,
    val parsed: List<ParsedBill> = emptyList(),
    val selectedIndices: Set<Int> = emptySet(),
    val importCount: Int = 0,
    /** 去重（txId/属性重复）被跳过、未写入的条数 */
    val skippedCount: Int = 0,
    /** 解析阶段被丢弃的记录（原始文本 + 原因），非空时结果页提供「导出失败记录」 */
    val failures: List<ImportFailure> = emptyList(),
    /** 本次导入成功写入的账单行 id，供结果页「撤销本次导入」按 id 删除（仅内存，不做持久化） */
    val importedBillIds: List<Long> = emptyList(),
    /** 结果页一次性提示（导出成功/撤销完成等） */
    val actionMessage: String? = null,
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
    /** 单笔校对页各字段置信度（分级圆点）；用户手动编辑后对应字段置为 HIGH */
    val ocrAmountConfidence: FieldConfidence = FieldConfidence.MISSING,
    val ocrMerchantConfidence: FieldConfidence = FieldConfidence.MISSING,
    val ocrDateConfidence: FieldConfidence = FieldConfidence.MISSING,
    val ocrCategoryConfidence: FieldConfidence = FieldConfidence.MISSING,
    /** 本次导入（文件/OCR 两条路径共用）记到哪个钱包，默认第一个 */
    val importWalletId: Long? = null,
    val wallets: List<Wallet> = emptyList(),
    /** 本次导入记到哪个账本；与手动记账一样可选，默认账本 */
    val importBookId: Long? = null,
    val accountBooks: List<AccountBook> = emptyList(),
    /** 文件为 ZipCrypto 加密、正等待用户输入解压密码 */
    val zipPasswordRequired: Boolean = false,
    /** 上一次提交的解压密码错误，用于弹窗内联报错 */
    val zipPasswordWrong: Boolean = false
)

@HiltViewModel
class BillImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billRepository: BillRepository,
    private val cachedWallets: @JvmSuppressWildcards StateFlow<List<Wallet>>,
    private val cachedCategoryConfigs: @JvmSuppressWildcards StateFlow<List<CategoryConfig>>,
    private val cachedAccountBooks: @JvmSuppressWildcards StateFlow<List<AccountBook>>,
    private val preferencesManager: PreferencesManager,
    private val ocrEngine: OcrEngine
) : ViewModel() {

    private val _state = MutableStateFlow(BillImportState())
    val state: StateFlow<BillImportState> = _state.asStateFlow()
    private val ocrParser = BillOcrParser()

    /** 当前解析/导入任务，供「取消」中断（OCR 识别大图可能耗时数秒） */
    private var workJob: Job? = null

    /** 加密 zip 的原始字节（等待密码期间暂存，避免让用户重新选文件） */
    private var pendingEncryptedBytes: ByteArray? = null

    /** 用户提交的解压密码（CharArray 便于就地清零；成功/失败后即弃用） */
    private var zipPassword: CharArray? = null

    /** 清空密码与暂存的加密字节（切模式/重置/取消/新文件时调用，缩短敏感数据驻留窗口） */
    private fun clearZipSecrets() {
        zipPassword?.fill('\u0000')
        zipPassword = null
        pendingEncryptedBytes = null
    }

    /** 取消进行中的解析/导入，回到选择方式页 */
    fun cancelWork() {
        workJob?.cancel()
        workJob = null
        clearZipSecrets()
        _state.value = _state.value.copy(
            stage = ImportStage.IDLE, error = null, zipPasswordRequired = false, zipPasswordWrong = false
        )
    }

    // ============ 分类数据源（与 BillViewModel/记账页完全一致） ============

    /** 预设分类的名称/开关覆盖（用户在分类管理里改过的） */
    val presetCategoryOverrides: StateFlow<Map<String, String>> =
        preferencesManager.presetCategoryOverrides
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val customExpenseCategories: StateFlow<List<CategoryItem>> = cachedCategoryConfigs
        .map { configs -> configs.filter { it.type == "BILL_EXPENSE" && it.isEnabled }
            .map { CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customIncomeCategories: StateFlow<List<CategoryItem>> = cachedCategoryConfigs
        .map { configs -> configs.filter { it.type == "BILL_INCOME" && it.isEnabled }
            .map { CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 分类使用频次（支出+收入合并），用于把常用分类排前面 */
    val categoryUsageCounts: StateFlow<Map<String, Int>> = combine(
        billRepository.getCategoryUsageCounts("EXPENSE"),
        billRepository.getCategoryUsageCounts("INCOME")
    ) { expense, income ->
        (expense + income).groupBy({ it.category }, { it.count }).mapValues { it.value.sum() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
        clearZipSecrets()
        CoroutineScope(Dispatchers.IO).launch { ocrEngine.release() }
        super.onCleared()
    }

    fun setMode(mode: ImportMode) {
        // 切换模式只清解析结果，保留账本/钱包列表与选择（此前整表重置导致切换后 chips 消失）
        clearZipSecrets()
        _state.value = BillImportState(
            mode = mode,
            wallets = _state.value.wallets,
            importWalletId = _state.value.importWalletId,
            accountBooks = _state.value.accountBooks,
            importBookId = _state.value.importBookId
        )
    }

    fun parseFile(@ApplicationContext context: Context, uri: Uri, fileName: String) {
        workJob = viewModelScope.launch {
            clearZipSecrets()
            _state.value = _state.value.copy(
                stage = ImportStage.PARSING, fileName = fileName, error = null, diagnostic = "",
                zipPasswordRequired = false, zipPasswordWrong = false
            )
            try {
                val diag = StringBuilder()
                val failures = mutableListOf<ImportFailure>()
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
                routeFileBytes(context, rawBytes, diag, failures) ?: return@launch
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_parse_failed, e.message))
            }
        }
    }

    /** 文件字节分流：非 zip → 直接按文本解析；zip 则解出待处理载荷后再统一分流 */
    private suspend fun routeFileBytes(
        context: Context,
        bytes: ByteArray,
        diag: StringBuilder,
        failures: MutableList<ImportFailure>
    ): Unit? {
        val reader = ZipArchiveReader()
        if (!reader.isZip(bytes)) {
            diag.append(context.getString(R.string.bill_import_diag_format, "CSV/Text") + "\n")
            return parsePayload(context, bytes, diag, failures)
        }
        val entries = withContext(Dispatchers.IO) { reader.readEntries(bytes) }
        val payload = resolveZipPayload(context, reader, bytes, entries, diag) ?: return null
        return parsePayload(context, payload, diag, failures)
    }

    /**
     * 从 zip 得到待处理载荷：
     * ① 含 ZipCrypto 加密条目 → 暂存字节、转「输入密码」态，返回 null（分支已消费）；
     * ② 未加密 xlsx（本质是含 xl/ 的 zip）→ 原字节即 xlsx；
     * ③ 未加密普通 zip → 取第一个 csv/xlsx/txt 条目解出。
     */
    private suspend fun resolveZipPayload(
        context: Context,
        reader: ZipArchiveReader,
        bytes: ByteArray,
        entries: List<ZipArchiveReader.Entry>,
        diag: StringBuilder
    ): ByteArray? {
        if (entries.any { it.encrypted }) {
            diag.append(context.getString(R.string.bill_import_diag_format, "XLSX/ZIP(encrypted)") + "\n")
            pendingEncryptedBytes = bytes
            _state.value = _state.value.copy(
                stage = ImportStage.PASSWORD,
                zipPasswordRequired = true,
                zipPasswordWrong = false,
                diagnostic = diag.toString()
            )
            return null
        }
        if (isXlsxArchive(entries)) {
            diag.append(context.getString(R.string.bill_import_diag_format, "XLSX(ZIP)") + "\n")
            return bytes
        }
        return extractFirstDataEntry(context, reader, bytes, entries, diag)
    }

    /** 解出普通 zip 里第一个数据条目；无可用条目或解压失败时置错误态并返回 null */
    private suspend fun extractFirstDataEntry(
        context: Context,
        reader: ZipArchiveReader,
        bytes: ByteArray,
        entries: List<ZipArchiveReader.Entry>,
        diag: StringBuilder
    ): ByteArray? {
        diag.append(context.getString(R.string.bill_import_diag_format, "ZIP(alt)") + "\n")
        val fileEntry = entries.firstOrNull { !it.name.endsWith("/") && isDataEntry(it.name) }
        if (fileEntry == null) {
            _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_format_unknown), diagnostic = diag.toString())
            return null
        }
        return try {
            withContext(Dispatchers.IO) { reader.extract(bytes, fileEntry, null) }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                stage = ImportStage.ERROR,
                error = context.getString(R.string.bill_import_error_parse_failed, e.message),
                diagnostic = diag.toString()
            )
            null
        }
    }

    /**
     * 提交解压密码：解出加密 zip 中的首个数据文件并继续解析。密码错则回到「输入密码」态并内联报错，
     * 让用户重试；其他异常落到错误态。
     */
    fun submitZipPassword(password: String) {
        val bytes = pendingEncryptedBytes ?: return
        val pwd = password.toCharArray()
        zipPassword = pwd
        workJob = viewModelScope.launch {
            _state.value = _state.value.copy(stage = ImportStage.PARSING, zipPasswordWrong = false)
            try {
                val diag = StringBuilder()
                val failures = mutableListOf<ImportFailure>()
                val reader = ZipArchiveReader()
                val entries = withContext(Dispatchers.IO) { reader.readEntries(bytes) }
                val fileEntry = entries.firstOrNull { !it.name.endsWith("/") && isDataEntry(it.name) }
                    ?: entries.firstOrNull { !it.name.endsWith("/") }
                if (fileEntry == null) {
                    _state.value = _state.value.copy(
                        stage = ImportStage.ERROR,
                        error = context.getString(R.string.bill_import_error_format_unknown)
                    )
                    return@launch
                }
                // extract 会就地清零 pwd（= zipPassword），解出后即可丢弃
                val data = withContext(Dispatchers.IO) { reader.extract(bytes, fileEntry, pwd) }
                zipPassword = null
                parsePayload(context, data, diag, failures) ?: return@launch
            } catch (_: WrongPasswordException) {
                // 密码错是**预期内**的控制流（用户可能输错），不做异常上报，只回弹窗内联报错
                _state.value = _state.value.copy(stage = ImportStage.PASSWORD, zipPasswordRequired = true, zipPasswordWrong = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_parse_failed, e.message))
            }
        }
    }

    /** xlsx 本质是 zip：含 [Content_Types].xml 或 xl/ 目录即判定为 xlsx，否则当作普通压缩包 */
    private fun isXlsxArchive(entries: List<ZipArchiveReader.Entry>): Boolean =
        entries.any { it.name == "[Content_Types].xml" || it.name.startsWith("xl/") }

    private fun isDataEntry(name: String): Boolean =
        name.endsWith(".csv", true) || name.endsWith(".txt", true) || name.endsWith(".xlsx", true)

    /** 把「可能是 xlsx、也可能是 CSV 文本」的字节解析进预览；空结果置错误态并返回 null */
    private suspend fun parsePayload(
        context: Context,
        payload: ByteArray,
        diag: StringBuilder,
        failures: MutableList<ImportFailure>
    ): Unit? {
        if (ZipArchiveReader().isZip(payload)) {
            val (xlsxFormat, parsed) = withContext(Dispatchers.IO) {
                BillXlsxImporter().parseBytesWithFormat(payload, diag, failures)
            }
            return showParsed(context, parsed, xlsxFormat, diag, failures)
        }
        val lines = withContext(Dispatchers.IO) { decodeText(payload, diag) }
        diag.append(context.getString(R.string.bill_import_diag_lines, lines.size) + "\n")
        if (lines.isNotEmpty()) diag.append(context.getString(R.string.bill_import_diag_first_line, lines.first().take(80)) + "\n")
        val importer = BillCsvImporter()
        val format = importer.detectFormat(lines)
        diag.append(context.getString(R.string.bill_import_diag_detected_format, format) + "\n")
        if (format == BillCsvImporter.CsvFormat.UNKNOWN) {
            _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_format_unknown), diagnostic = diag.toString())
            return null
        }
        val parsed = withContext(Dispatchers.IO) { importer.parseWithFailures(lines, format, diag, failures) }
        return showParsed(context, parsed, format, diag, failures)
    }

    /** 解析结果进入预览；空结果则置错误态并返回 null */
    private fun showParsed(
        context: Context,
        parsed: List<ParsedBill>,
        format: BillCsvImporter.CsvFormat,
        diag: StringBuilder,
        failures: List<ImportFailure>
    ): Unit? {
        diag.append(context.getString(R.string.bill_import_diag_records, parsed.size) + "\n")
        return if (parsed.isEmpty()) {
            _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_parse_invalid), diagnostic = diag.toString())
            null
        } else {
            // 微信账单默认记到"微信"钱包、支付宝账单默认记到"支付宝"；找不到同名钱包时保持原选择
            val walletId = pickWalletFor(channelOf(format), _state.value.wallets) ?: _state.value.importWalletId
            _state.value = _state.value.copy(
                stage = ImportStage.PREVIEW, parsed = parsed,
                selectedIndices = parsed.indices.filter { parsed[it].defaultSelected }.toSet(), format = format,
                diagnostic = diag.toString(), importWalletId = walletId,
                failures = failures
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
        workJob = viewModelScope.launch {
            _state.value = _state.value.copy(stage = ImportStage.PARSING, ocrImageUri = uri, error = null)
            try {
                val (lines, text) = withContext(Dispatchers.IO) {
                    // 先采样解码获取尺寸，再按需降采样，避免大图 OOM
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    val sample = BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 2048)
                    }
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, sample) }
                        ?: throw Exception(context.getString(R.string.bill_import_error_read_image))
                    val rotated = rotateBitmapIfNeeded(context, uri, bitmap)
                    // 逐行识别并保留每行置信度（供字段级置信度分级）；同时拼回纯文本用于渠道判定与原文展示
                    val ocrLines = ocrEngine.recognizeDetailed(rotated)
                    ocrLines to ocrLines.joinToString("\n") { it.text }
                }
                val failures = mutableListOf<ImportFailure>()
                val results = ocrParser.parseMultiple(lines, failures)
                if (results.isEmpty()) {
                    _state.value = _state.value.copy(stage = ImportStage.ERROR, error = context.getString(R.string.bill_import_error_ocr_invalid), ocrRawText = text)
                    return@launch
                }
                // 微信/支付宝截图默认记到对应钱包（截图判不出渠道时保持原选择）
                val walletId = pickWalletFor(detectChannel(text), _state.value.wallets) ?: _state.value.importWalletId
                if (results.size == 1) {
                    val r = results[0]
                    val amountStr = r.amount?.let { String.format(java.util.Locale.US, "%.2f", it / 100.0) } ?: ""
                    // 截图无日期（如电商订单裁剪图）时预填今天：保存本就回退今天，界面上不该留空白让人疑惑
                    val dateStr = r.date?.let { DateUtils.formatDate(it) }
                        ?: DateUtils.formatDate(System.currentTimeMillis())
                    _state.value = _state.value.copy(
                        stage = ImportStage.PREVIEW, ocrResults = results, ocrSelectedIndices = setOf(0),
                        ocrImageUri = uri, ocrRawText = text, importWalletId = walletId,
                        ocrAmount = amountStr, ocrMerchant = r.merchant, ocrDate = dateStr,
                        ocrCategory = r.category, ocrNote = r.note,
                        ocrAmountConfidence = r.amountConfidence,
                        ocrMerchantConfidence = r.merchantConfidence,
                        ocrDateConfidence = r.dateConfidence,
                        ocrCategoryConfidence = r.categoryConfidence
                    )
                } else {
                    _state.value = _state.value.copy(
                        stage = ImportStage.PREVIEW, ocrResults = results,
                        ocrSelectedIndices = results.indices.toSet(),
                        ocrImageUri = uri, ocrRawText = text, importWalletId = walletId,
                        failures = failures
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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

    // 用户手输/改过的值即可信：对应字段置信度置为 HIGH
    fun updateOcrAmount(v: String) { _state.value = _state.value.copy(ocrAmount = v, ocrAmountConfidence = FieldConfidence.HIGH) }
    fun updateOcrMerchant(v: String) { _state.value = _state.value.copy(ocrMerchant = v, ocrMerchantConfidence = FieldConfidence.HIGH) }
    fun updateOcrDate(v: String) { _state.value = _state.value.copy(ocrDate = v, ocrDateConfidence = FieldConfidence.HIGH) }
    fun updateOcrCategory(v: String) { _state.value = _state.value.copy(ocrCategory = v, ocrCategoryConfidence = FieldConfidence.HIGH) }
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
        workJob = viewModelScope.launch {
            _state.value = s.copy(stage = ImportStage.IMPORTING)
            try {
                val outcome = withContext(Dispatchers.IO) {
                    saveBills(selected, s.importWalletId ?: getWalletId(), resolveBookId(s))
                }
                _state.value = _state.value.copy(
                    stage = ImportStage.DONE,
                    importCount = outcome.insertedIds.size,
                    skippedCount = outcome.skipped,
                    importedBillIds = outcome.insertedIds,
                    actionMessage = null
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BillImportViewModel", "import failed", e)
                _state.value = _state.value.copy(
                    stage = ImportStage.PREVIEW,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    fun saveOcrSelected() {
        val s = _state.value
        workJob = viewModelScope.launch {
            _state.value = s.copy(stage = ImportStage.IMPORTING)
            try {
                val (toSave, existing) = withContext(Dispatchers.IO) {
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
                        if (amount == null) return@withContext null to existing
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
                    toSave to existing
                }

                if (toSave == null) {
                    _state.value = _state.value.copy(
                        stage = ImportStage.PREVIEW,
                        error = context.getString(R.string.bill_import_error_invalid_amount)
                    )
                    return@launch
                }

                val outcome = insertBillsIfNew(toSave, existing)
                _state.value = _state.value.copy(
                    stage = ImportStage.DONE,
                    importCount = outcome.insertedIds.size,
                    skippedCount = outcome.skipped,
                    importedBillIds = outcome.insertedIds,
                    actionMessage = null
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BillImportViewModel", "import failed", e)
                _state.value = _state.value.copy(
                    stage = ImportStage.PREVIEW,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    private suspend fun saveBills(parsed: List<ParsedBill>, walletId: Long?, bookId: Long): InsertOutcome {
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

    private suspend fun insertBillsIfNew(bills: List<Bill>, existing: List<Bill>): InsertOutcome {
        // transactionId 是最可靠唯一键（微信/支付宝交易单号）
        val existingTxIds = existing.mapNotNull { it.transactionId.takeIf { t -> t.isNotBlank() } }.toSet()
        // 无交易单号的记录退化为属性指纹去重：预建索引，避免逐条对全量账单做 O(n×m) 线性扫描
        val existingAttrKeys = existing.mapTo(HashSet(existing.size)) {
            "${it.date}_${it.amount}_${it.merchant}_${it.type}"
        }
        // 批内去重，避免同一文件内重复记录被重复插入
        val seenTxIds = mutableSetOf<String>()
        val seenByAttrs = mutableSetOf<String>()
        val pending = mutableListOf<Bill>()
        for (bill in bills) {
            val txId = bill.transactionId.takeIf { it.isNotBlank() }
            if (txId != null) {
                if (existingTxIds.contains(txId) || !seenTxIds.add(txId)) continue
            } else {
                val attrKey = "${bill.date}_${bill.amount}_${bill.merchant}_${bill.type}"
                if (attrKey in existingAttrKeys || !seenByAttrs.add(attrKey)) continue
            }
            pending.add(bill)
        }
        // 与手动记账一致：写账单 + 调整钱包余额（单事务批量），并保留行 id 供结果页「撤销本次导入」
        // 此前直接 insertBill 是裸插入，导致导入账单从不改变钱包余额
        val insertedIds = billRepository.createBillsWithWalletAdjustment(pending)
        // 每条记录要么写入、要么因重复跳过，故跳过数 = 总数 − 成功写入数
        return InsertOutcome(insertedIds, bills.size - insertedIds.size)
    }

    fun reset() {
        clearZipSecrets()
        _state.value = BillImportState(
            mode = _state.value.mode,
            wallets = _state.value.wallets,
            importWalletId = _state.value.importWalletId,
            accountBooks = _state.value.accountBooks,
            importBookId = _state.value.importBookId
        )
    }

    /**
     * 把解析失败的记录导出到用户选择的 Uri（SAF）。导出的 CSV 采用 BillCsvImporter 的 GENERIC
     * 格式表头，可被直接读回，用户补齐缺失的金额/日期后即可重新导入。
     */
    fun exportFailures(uri: Uri) {
        val failures = _state.value.failures
        if (failures.isEmpty()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val csv = FailedRowsCsvWriter.build(failures, ::reasonLabel)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        os.write(csv.toByteArray(Charsets.UTF_8))
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }
            _state.update {
                it.copy(
                    actionMessage = context.getString(
                        if (ok) R.string.bill_import_failed_export_ok else R.string.bill_import_failed_export_error
                    )
                )
            }
        }
    }

    /**
     * 撤销本次导入：按行 id 走与手动删除相同的删除路径（[BillRepository.deleteBill]，回收站留档 +
     * 回滚钱包余额）——因为导入已按手动记账一致地调整过余额。仅内存操作，离开结果页即失效。
     */
    fun undoImport() {
        val ids = _state.value.importedBillIds
        if (ids.isEmpty()) return
        workJob = viewModelScope.launch {
            _state.value = _state.value.copy(stage = ImportStage.IMPORTING)
            try {
                ids.forEach { billRepository.deleteBill(it) }
                _state.value = _state.value.copy(
                    stage = ImportStage.DONE,
                    importCount = 0,
                    importedBillIds = emptyList(),
                    actionMessage = context.getString(R.string.bill_import_undo_done)
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BillImportViewModel", "undo import failed", e)
                _state.value = _state.value.copy(
                    stage = ImportStage.DONE,
                    actionMessage = context.getString(R.string.bill_import_undo_error)
                )
            }
        }
    }

    /** 失败原因码 → 本地化文案（导出 CSV 的「失败原因」列） */
    private fun reasonLabel(reason: ImportFailureReason): String = context.getString(
        when (reason) {
            ImportFailureReason.MISSING_AMOUNT -> R.string.bill_import_reason_missing_amount
            ImportFailureReason.MISSING_DATE -> R.string.bill_import_reason_missing_date
            ImportFailureReason.UNPARSEABLE -> R.string.bill_import_reason_unparseable
        }
    )

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
