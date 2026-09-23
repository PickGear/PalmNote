package com.palmnote.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.export.CsvDataExporter
import com.palmnote.data.export.ImportReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 统一导入入口的「全量数据导入」部分。
 *
 * 账单导入（微信/支付宝/CSV/Excel/OCR）走 BillImport 流程，有自己的 ViewModel；
 * 这里只负责 PalmNote 全量数据包（CsvDataExporter 导出的 ZIP）的导入。
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val csvDataExporter: CsvDataExporter
) : ViewModel() {

    data class FullImportUiState(
        val importing: Boolean = false,
        val report: ImportReport? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(FullImportUiState())
    val state: StateFlow<FullImportUiState> = _state.asStateFlow()

    /**
     * 全量导入：事务内按业务键判重（重复记录自动跳过，不覆盖任何已有数据），
     * 与账单导入的撤销不同——全量导入是合并语义，无需撤销入口。
     */
    fun importFullData(uri: Uri) {
        if (_state.value.importing) return
        _state.value = FullImportUiState(importing = true)
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { csvDataExporter.importFromUri(uri) }
            _state.value = outcome.fold(
                onSuccess = { FullImportUiState(report = it) },
                onFailure = { e ->
                    FullImportUiState(error = e.message ?: e.javaClass.simpleName)
                }
            )
        }
    }

    fun consumeResult() {
        if (!_state.value.importing) _state.value = FullImportUiState()
    }
}
