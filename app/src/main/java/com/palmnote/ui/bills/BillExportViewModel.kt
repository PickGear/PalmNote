package com.palmnote.ui.bills

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.export.BillFlowExporter
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 导出时间范围预设。全部与自然月/自然年对齐（对账时不用再自己算边界）：
 * - [THIS_MONTH] 本月 1 日 ~ 本月最后一天
 * - [LAST_3_MONTHS] 含本月在内的 3 个自然月
 * - [CUSTOM] 用户自选起止日期（含首尾整天）
 */
enum class ExportRangePreset { ALL, THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, THIS_YEAR, CUSTOM }

/** [startMillis] / [endMillis] 为「含首尾整天」的闭区间（本地时区）。 */
data class DateBounds(val startMillis: Long, val endMillis: Long)

/**
 * 导出账单页面状态。
 *
 * [loading] 期间数据卡显示「正在统计」；[count] = 0 时按钮禁用并给出空态，
 * 而不是让用户选完保存位置才发现没东西可导。
 */
@androidx.compose.runtime.Stable
data class BillExportState(
    val loading: Boolean = true,
    val preset: ExportRangePreset = ExportRangePreset.ALL,
    /** 自定义范围（仅 [ExportRangePreset.CUSTOM] 时有意义），含首尾整天 */
    val customStart: Long? = null,
    val customEnd: Long? = null,
    val count: Int = 0,
    /** 所选范围内最早 / 最晚一条账单的时间戳（毫秒）；[count] 为 0 时两者均为 null */
    val firstDate: Long? = null,
    val lastDate: Long? = null,
    /** 这批账单涉及几个账本 / 几个钱包（按实际出现过的 id 去重计数） */
    val bookCount: Int = 0,
    val walletCount: Int = 0,
    val exporting: Boolean = false,
    /** 导出结果提示（成功条数 / 失败原因）；null = 还没导过 */
    val message: String? = null,
    val failed: Boolean = false
)

/**
 * 「导出账单」独立页的 ViewModel。
 *
 * 与 [BillImportViewModel] 分开是为了让导出只有自己的状态：进来先统计（多少条、时间范围、几个账本钱包），
 * 用户看清「要导出的是什么」再选保存位置，导出后停在原地看结果/重试。
 */
@HiltViewModel
class BillExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billRepository: BillRepository,
    private val cachedWallets: @JvmSuppressWildcards StateFlow<List<Wallet>>,
    private val cachedAccountBooks: @JvmSuppressWildcards StateFlow<List<AccountBook>>
) : ViewModel() {

    private val _state = MutableStateFlow(BillExportState())
    val state: StateFlow<BillExportState> = _state.asStateFlow()

    /** 上一次选定的保存位置：失败后「重试」直接复用，不必再走一遍系统面板。 */
    private var lastUri: Uri? = null

    /** 当前统计任务：切范围时先取消，避免旧结果覆盖新结果。 */
    private var summaryJob: Job? = null

    private val fileDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault())

    init {
        loadSummary()
    }

    // ── 时间范围 ──────────────────────────────────────────────────────────

    /** 切换预设；[ExportRangePreset.CUSTOM] 需要先选过自定义范围才有意义。 */
    fun selectPreset(preset: ExportRangePreset) {
        if (preset == ExportRangePreset.CUSTOM &&
            (_state.value.customStart == null || _state.value.customEnd == null)
        ) {
            return
        }
        _state.value = _state.value.copy(preset = preset, message = null, failed = false)
        loadSummary()
    }

    /** 选好自定义起止（含首尾整天）。 */
    fun setCustomRange(startMillis: Long, endMillis: Long) {
        val (s, e) = if (startMillis <= endMillis) startMillis to endMillis else endMillis to startMillis
        _state.value = _state.value.copy(
            preset = ExportRangePreset.CUSTOM,
            customStart = s,
            customEnd = e,
            message = null,
            failed = false
        )
        loadSummary()
    }

    /**
     * 当前预设对应的闭区间；[ExportRangePreset.ALL] 返回 null。
     * 边界都按本地时区对齐到整天（00:00:00.000 ~ 23:59:59.999）。
     */
    fun boundsOf(preset: ExportRangePreset = _state.value.preset): DateBounds? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return when (preset) {
            ExportRangePreset.ALL -> null
            ExportRangePreset.THIS_MONTH -> bounds(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()), zone)
            ExportRangePreset.LAST_MONTH -> {
                val last = today.minusMonths(1)
                bounds(last.withDayOfMonth(1), last.withDayOfMonth(last.lengthOfMonth()), zone)
            }
            ExportRangePreset.LAST_3_MONTHS -> {
                val first = today.withDayOfMonth(1).minusMonths(2)
                bounds(first, today.withDayOfMonth(today.lengthOfMonth()), zone)
            }
            ExportRangePreset.THIS_YEAR -> bounds(today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()), zone)
            ExportRangePreset.CUSTOM -> {
                val s = _state.value.customStart
                val e = _state.value.customEnd
                if (s == null || e == null) null else DateBounds(s, e)
            }
        }
    }

    private fun bounds(start: LocalDate, end: LocalDate, zone: ZoneId): DateBounds = DateBounds(
        start.atStartOfDay(zone).toInstant().toEpochMilli(),
        end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    )

    /** 导出文件名：范围写进名字，反复导出不同区间不会互相覆盖、也便于归档。 */
    fun fileName(): String {
        val b = boundsOf() ?: return context.getString(R.string.bill_export_file_name)
        return context.getString(
            R.string.bill_export_file_name_ranged,
            fileDate.format(Instant.ofEpochMilli(b.startMillis)),
            fileDate.format(Instant.ofEpochMilli(b.endMillis))
        )
    }

    // ── 统计 ──────────────────────────────────────────────────────────────

    /**
     * 按当前时间范围过滤（排除演示数据）。纯函数、不碰协程，便于直接单测。
     */
    internal fun billsInRange(all: List<Bill>): List<Bill> {
        val b = boundsOf()
        return all
            .filterNot { it.isDemo }
            .filter { b == null || it.date in b.startMillis..b.endMillis }
    }

    /**
     * 按当前时间范围统计（排除演示数据）：条数、实际首尾时间、涉及账本/钱包数。
     *
     * 连续切范围时取消上一次：否则慢的那次后返回，会用旧范围的结果覆盖新范围
     * （查询本身读的是当前 state，两次结果通常一致，但 IO 抖动时会出现数字与选中范围对不上）。
     */
    fun loadSummary() {
        summaryJob?.cancel()
        // 每次重新统计都回到 loading：切范围时旧条数不能再当「当前范围」显示，
        // 否则用户可能在数字还没刷新时点了导出（导出的数据是对的，但提示会像是导了 0 条）。
        _state.value = _state.value.copy(loading = true)
        summaryJob = viewModelScope.launch {
            val bills = withContext(Dispatchers.IO) {
                billsInRange(billRepository.getAllBills().first())
            }
            _state.value = _state.value.copy(
                loading = false,
                count = bills.size,
                firstDate = bills.minOfOrNull { it.date },
                lastDate = bills.maxOfOrNull { it.date },
                bookCount = bills.map { it.accountBookId }.distinct().size,
                walletCount = bills.mapNotNull { it.walletId }.distinct().size
            )
        }
    }

    // ── 导出 ──────────────────────────────────────────────────────────────

    /** 失败后重试：复用上次选定的位置，不重新弹系统面板。 */
    fun retry() {
        lastUri?.let { export(it) }
    }

    /**
     * 把所选时间范围内的账单流水写到用户选择的 Uri（SAF）。
     * 口径见 [BillFlowExporter]：13 列、时间升序、排除演示数据、UTF-8 带 BOM，有意不可回导。
     */
    fun export(uri: Uri) {
        if (_state.value.exporting || _state.value.count == 0) return
        lastUri = uri
        _state.value = _state.value.copy(exporting = true, message = null, failed = false)
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    val bills = billsInRange(billRepository.getAllBills().first())
                    val wallets = cachedWallets.first()
                    val books = cachedAccountBooks.first()
                    val written = context.contentResolver.openOutputStream(uri)?.use { out ->
                        BillFlowExporter(context).export(bills, out, wallets, books)
                    } ?: error("cannot open output stream")
                    context.getString(R.string.bill_export_done, written)
                }
                _state.value = _state.value.copy(exporting = false, message = message, failed = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BillExportViewModel", "export flow failed", e)
                _state.value = _state.value.copy(
                    exporting = false,
                    message = context.getString(R.string.bill_export_failed),
                    failed = true
                )
            }
        }
    }
}
