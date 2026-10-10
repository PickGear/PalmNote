package com.palmnote.ui.bills
import android.content.Context
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.BillType
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.ReimbursementAllocation
import com.palmnote.domain.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


@Stable
data class BillDetailState(val bill: Bill? = null)

/** 报销落库失败时打日志用；组件/VM 里不引入日志框架，沿用 app 模块的 android.util.Log。 */
private const val TAG = "BillDetailViewModel"

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val cachedWallets: @JvmSuppressWildcards StateFlow<List<Wallet>>,
    private val cachedCategoryConfigs: @JvmSuppressWildcards StateFlow<List<CategoryConfig>>,
    private val preferencesManager: PreferencesManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private val _state = MutableStateFlow(BillDetailState())
    val state: StateFlow<BillDetailState> = _state.asStateFlow()

    /** 这笔支出关联到的报销收入账单（没有则为 null）。 */
    private val _linkedIncome = MutableStateFlow<Bill?>(null)
    val linkedIncome: StateFlow<Bill?> = _linkedIncome.asStateFlow()

    /**
     * 反过来：这笔报销收入覆盖了哪些支出。
     * 关联方向是「支出存收入 id」，只能反查；普通账单恒为空。
     */
    private val _coveredExpenses = MutableStateFlow<List<Bill>>(emptyList())
    val coveredExpenses: StateFlow<List<Bill>> = _coveredExpenses.asStateFlow()

    /** 报销操作进行中：用于禁用按钮，避免连点补记出两条收入。 */
    private val _isReimbursementSaving = MutableStateFlow(false)
    val isReimbursementSaving: StateFlow<Boolean> = _isReimbursementSaving.asStateFlow()

    /**
     * 一次性的失败提示（已取好文案，界面弹完调 [consumeErrorMessage] 清空）。
     *
     * 报销要写库：失败若不吱声，用户看到的是「点了没反应」，比崩溃还难查。
     */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun consumeErrorMessage() {
        _errorMessage.value = null
    }

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
            _linkedIncome.value = bill?.reimbursedByBillId?.let { billRepository.getBillById(it) }
            // 只有报销收入才可能被别人关联；普通账单跳过这次查询
            _coveredExpenses.value = if (bill != null && bill.type == BillType.INCOME) {
                billRepository.getBillsReimbursedBy(bill.id)
            } else {
                emptyList()
            }
        }
    }

    fun deleteBill(billId: Long) {
        viewModelScope.launch { billRepository.deleteBill(billId) }
    }

    /**
     * 标记这笔支出已报销 [amount]（分，本次金额）。
     * [createIncome] 为 true 时同时补记一笔「报销」分类的收入并关联，收入跟随本笔支出的账户。
     */
    fun markReimbursed(amount: Long, date: Long, createIncome: Boolean) {
        val bill = _state.value.bill ?: return
        if (amount <= 0) return
        if (_isReimbursementSaving.value) return
        _isReimbursementSaving.value = true
        viewModelScope.launch {
            try {
                if (createIncome) {
                    val now = System.currentTimeMillis()
                    val income = Bill(
                        amount = amount,
                        type = BillType.INCOME,
                        category = REIMBURSEMENT_CATEGORY,
                        note = bill.note,
                        date = date,
                        yearMonth = DateUtils.formatYearMonth(date),
                        accountBookId = bill.accountBookId,
                        walletId = bill.walletId,
                        createdAt = now,
                        updatedAt = now
                    )
                    billRepository.createReimbursementIncome(
                        income = income,
                        allocations = listOf(ReimbursementAllocation(bill.id, amount))
                    )
                } else {
                    billRepository.applyReimbursement(
                        expenseId = bill.id,
                        paid = bill.reimbursedAmount + amount,
                        date = date,
                        byBillId = null
                    )
                }
                loadBill(bill.id)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "markReimbursed failed (billId=${bill.id})", e)
                _errorMessage.value = appContext.getString(R.string.reimbursement_error_failed)
            } finally {
                _isReimbursementSaving.value = false
            }
        }
    }

    /** 撤销整笔报销，退回待报销；系统代记的那笔报销收入会一并删除（进回收站，可恢复）。 */
    fun revokeReimbursement() {
        val bill = _state.value.bill ?: return
        if (_isReimbursementSaving.value) return
        _isReimbursementSaving.value = true
        viewModelScope.launch {
            try {
                billRepository.clearReimbursement(bill.id)
                loadBill(bill.id)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "revokeReimbursement failed (billId=${bill.id})", e)
                _errorMessage.value = appContext.getString(R.string.reimbursement_error_failed)
            } finally {
                _isReimbursementSaving.value = false
            }
        }
    }
}
