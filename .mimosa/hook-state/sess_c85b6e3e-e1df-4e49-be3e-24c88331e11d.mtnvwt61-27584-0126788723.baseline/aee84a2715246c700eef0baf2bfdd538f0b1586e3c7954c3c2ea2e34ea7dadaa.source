package com.palmnote.ui.settings
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.dao.AssetRecycleBinDao
import com.palmnote.data.db.dao.BillRecycleBinDao
import com.palmnote.data.db.entity.AssetRecycleBin
import com.palmnote.data.db.entity.BillRecycleBin
import com.palmnote.domain.repository.AssetRepository
import com.palmnote.domain.repository.BillRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


@Stable
data class RecycleBinState(
    val deletedAssets: List<AssetRecycleBin> = emptyList(),
    val deletedBills: List<BillRecycleBin> = emptyList()
)

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val assetRecycleBinDao: AssetRecycleBinDao,
    private val billRecycleBinDao: BillRecycleBinDao,
    private val assetRepository: AssetRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RecycleBinState())
    val state: StateFlow<RecycleBinState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                assetRecycleBinDao.getAll(),
                billRecycleBinDao.getAll()
            ) { assets, bills ->
                RecycleBinState(deletedAssets = assets, deletedBills = bills)
            }.collect { _state.value = it }
        }
    }

    fun restoreAsset(id: Long) { viewModelScope.launch { assetRepository.restoreAsset(id) } }
    fun restoreBill(id: Long) { viewModelScope.launch { billRepository.restoreBill(id) } }

    fun hardDeleteAsset(id: Long) { viewModelScope.launch { assetRepository.hardDeleteAsset(id) } }
    fun hardDeleteBill(id: Long) { viewModelScope.launch { billRepository.hardDeleteBill(id) } }

    fun clearAll() {
        viewModelScope.launch {
            val current = state.value
            current.deletedAssets.forEach { assetRepository.hardDeleteAsset(it.id) }
            current.deletedBills.forEach { billRepository.hardDeleteBill(it.id) }
        }
    }
}
