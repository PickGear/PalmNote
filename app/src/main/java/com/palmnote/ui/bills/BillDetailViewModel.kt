package com.palmnote.ui.bills

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.repository.BillRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


@Stable
data class BillDetailState(val bill: Bill? = null)

class BillDetailViewModel(
    private val billRepository: BillRepository
) : ViewModel() {
    private val _state = MutableStateFlow(BillDetailState())
    val state: StateFlow<BillDetailState> = _state.asStateFlow()

    fun loadBill(billId: Long) {
        viewModelScope.launch {
            val bill = billRepository.getBillById(billId)
            _state.value = BillDetailState(bill = bill)
        }
    }

    fun deleteBill(billId: Long) {
        viewModelScope.launch { billRepository.softDeleteBill(billId) }
    }
}
