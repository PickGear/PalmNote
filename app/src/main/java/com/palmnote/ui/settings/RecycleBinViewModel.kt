package com.palmnote.ui.settings

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.Anniversary
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Goal
import com.palmnote.data.db.entity.Moment
import com.palmnote.domain.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


@Stable
data class RecycleBinState(
    val deletedAssets: List<Asset> = emptyList(),
    val deletedBills: List<Bill> = emptyList(),
    val deletedGoals: List<Goal> = emptyList(),
    val deletedAnniversaries: List<Anniversary> = emptyList(),
    val deletedMoments: List<Moment> = emptyList()
)

class RecycleBinViewModel(
    private val assetRepository: AssetRepository,
    private val billRepository: BillRepository,
    private val goalRepository: GoalRepository,
    private val anniversaryRepository: AnniversaryRepository,
    private val momentRepository: MomentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RecycleBinState())
    val state: StateFlow<RecycleBinState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                assetRepository.getDeletedAssets(),
                billRepository.getDeletedBills(),
                goalRepository.getDeletedGoals(),
                anniversaryRepository.getDeletedAnniversaries(),
                momentRepository.getDeletedMoments()
            ) { assets, bills, goals, anniversaries, moments ->
                RecycleBinState(
                    deletedAssets = assets,
                    deletedBills = bills,
                    deletedGoals = goals,
                    deletedAnniversaries = anniversaries,
                    deletedMoments = moments
                )
            }.collect { _state.value = it }
        }
    }

    fun restoreAsset(id: Long) { viewModelScope.launch { assetRepository.restoreAsset(id) } }
    fun restoreBill(id: Long) { viewModelScope.launch { billRepository.restoreBill(id) } }
    fun restoreGoal(id: Long) { viewModelScope.launch { goalRepository.restoreGoal(id) } }
    fun restoreAnniversary(id: Long) { viewModelScope.launch { anniversaryRepository.restoreAnniversary(id) } }
    fun restoreMoment(id: Long) { viewModelScope.launch { momentRepository.restoreMoment(id) } }

    fun hardDeleteAsset(id: Long) { viewModelScope.launch { assetRepository.hardDeleteAsset(id) } }
    fun hardDeleteBill(id: Long) { viewModelScope.launch { billRepository.hardDeleteBill(id) } }
    fun hardDeleteGoal(id: Long) { viewModelScope.launch { goalRepository.hardDeleteGoal(id) } }
    fun hardDeleteAnniversary(id: Long) { viewModelScope.launch { anniversaryRepository.hardDeleteAnniversary(id) } }
    fun hardDeleteMoment(id: Long) { viewModelScope.launch { momentRepository.hardDeleteMoment(id) } }

    fun clearAll() {
        viewModelScope.launch {
            val current = state.value
            current.deletedAssets.forEach { assetRepository.hardDeleteAsset(it.id) }
            current.deletedBills.forEach { billRepository.hardDeleteBill(it.id) }
            current.deletedGoals.forEach { goalRepository.hardDeleteGoal(it.id) }
            current.deletedAnniversaries.forEach { anniversaryRepository.hardDeleteAnniversary(it.id) }
            current.deletedMoments.forEach { momentRepository.hardDeleteMoment(it.id) }
        }
    }
}
