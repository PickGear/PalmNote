package com.palmnote.ui.bills
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.repository.BillRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


@Stable
data class BillDetailState(val bill: Bill? = null)

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val cachedWallets: @JvmSuppressWildcards StateFlow<List<Wallet>>,
    private val cachedCategoryConfigs: @JvmSuppressWildcards StateFlow<List<CategoryConfig>>,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    private val _state = MutableStateFlow(BillDetailState())
    val state: StateFlow<BillDetailState> = _state.asStateFlow()

    val presetCategoryOverrides: StateFlow<Map<String, String>> =
        preferencesManager.presetCategoryOverrides
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val categoryConfigs: StateFlow<List<CategoryConfig>> = cachedCategoryConfigs

    val walletNames: StateFlow<Map<Long, String>> = cachedWallets
        .map { wallets -> wallets.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun loadBill(billId: Long) {
        viewModelScope.launch {
            val bill = billRepository.getBillById(billId)
            _state.value = BillDetailState(bill = bill)
        }
    }

    fun deleteBill(billId: Long) {
        viewModelScope.launch { billRepository.deleteBill(billId) }
    }
}
