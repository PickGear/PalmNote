package com.palmnote.ui.search
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.Anniversary
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.Moment
import com.palmnote.domain.repository.AnniversaryRepository
import com.palmnote.domain.repository.AssetRepository
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.MomentRepository
import com.palmnote.domain.util.LifeTemplateKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


@Stable
data class SearchState(
    val query: String = "",
    val assets: List<Asset> = emptyList(),
    val bills: List<Bill> = emptyList(),
    /** 「目标」节：目标模板下的生活条目（目标已并入模板体系，v1.4.2）。 */
    val goals: List<LifeItem> = emptyList(),
    val anniversaries: List<Anniversary> = emptyList(),
    val moments: List<Moment> = emptyList(),
    val isSearching: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val assetRepository: AssetRepository,
    private val billRepository: BillRepository,
    /** 目标已并入生活模板体系：这里按语义类型搜目标条目，不再是独立的 goals 表。 */
    private val lifeItemDao: LifeItemDao,
    private val anniversaryRepository: AnniversaryRepository,
    private val momentRepository: MomentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = SearchState(query = query)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(isSearching = true) }
            try {
                val assetsDeferred = async { assetRepository.search(query) }
                val billsDeferred = async { billRepository.search(query) }
                val goalsDeferred = async {
                    lifeItemDao.searchByTemplateKind(LifeTemplateKind.GOAL.name, query)
                }
                val anniversariesDeferred = async { anniversaryRepository.search(query) }
                val momentsDeferred = async { momentRepository.search(query) }
                val assets = assetsDeferred.await()
                val bills = billsDeferred.await()
                val goals = goalsDeferred.await()
                val anniversaries = anniversariesDeferred.await()
                val moments = momentsDeferred.await()
                _state.update {
                    it.copy(
                        assets = assets, bills = bills, goals = goals,
                        anniversaries = anniversaries, moments = moments, isSearching = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSearching = false) }
            }
        }
    }
}
