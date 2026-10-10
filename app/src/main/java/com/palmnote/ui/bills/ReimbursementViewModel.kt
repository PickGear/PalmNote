package com.palmnote.ui.bills

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.model.BillType
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.ReimbursementAllocation
import com.palmnote.domain.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
data class ReimbursementState(
    /** 待报销（含只报了一部分的）。 */
    val pending: List<Bill> = emptyList(),
    /** 已报完的。 */
    val done: List<Bill> = emptyList(),
    /** 待报销剩余总额（分）。 */
    val pendingTotal: Long = 0,
    /** 待报销笔数。 */
    val pendingCount: Int = 0,
    /** 累计已报回金额（分）。 */
    val receivedTotal: Long = 0
)

/** 报销落库失败时打日志用；不引入日志框架，沿用 app 模块的 android.util.Log。 */
private const val TAG = "ReimbursementViewModel"

@HiltViewModel
class ReimbursementViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val preferencesManager: PreferencesManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val presetCategoryOverrides: StateFlow<Map<String, String>> =
        preferencesManager.presetCategoryOverrides
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val state: StateFlow<ReimbursementState> = combine(
        billRepository.getUnreimbursedBills(),
        billRepository.getReimbursedBills(),
        billRepository.getPendingReimbursementSummary(),
        billRepository.getTotalReimbursedAmount()
    ) { pending, done, summary, received ->
        ReimbursementState(
            pending = pending,
            done = done,
            // 查不到（区间内没有待报销项）时 SUM 为 null，收敛成 0，不要当成业务值
            pendingTotal = summary?.remaining ?: 0L,
            pendingCount = summary?.count ?: 0,
            receivedTotal = received ?: 0L
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReimbursementState())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /**
     * 一次性的失败提示（已取好文案，界面弹完调 [consumeErrorMessage] 清空）。
     *
     * 此前这里 `catch (e: Exception)` 只把 isSaving 放掉、什么也不说 —— 报销失败时
     * 弹窗停在原地、列表不动，用户完全看不出发生了什么（detekt 的 SwallowedException）。
     */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun consumeErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * 标记一笔支出已报销 [amount]（分，本次金额）。
     *
     * [createIncome] 为 true 时同时补记一笔「报销」分类的收入并与之关联 —— 这是默认路径，
     * 因为只改标记而不记收入，会让当天收支看起来无缘无故多花了钱。
     * 收入跟随支出所在账户，用户想换账户可以去编辑那条收入。
     */
    fun markReimbursed(
        expense: Bill,
        amount: Long,
        date: Long,
        createIncome: Boolean,
        onDone: () -> Unit = {}
    ) {
        if (amount <= 0) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                if (createIncome) {
                    val now = System.currentTimeMillis()
                    val income = Bill(
                        amount = amount,
                        type = BillType.INCOME,
                        category = REIMBURSEMENT_CATEGORY,
                        note = expense.note,
                        date = date,
                        yearMonth = DateUtils.formatYearMonth(date),
                        accountBookId = expense.accountBookId,
                        walletId = expense.walletId,
                        createdAt = now,
                        updatedAt = now
                    )
                    billRepository.createReimbursementIncome(
                        income = income,
                        allocations = listOf(ReimbursementAllocation(expense.id, amount))
                    )
                } else {
                    billRepository.applyReimbursement(
                        expenseId = expense.id,
                        paid = expense.reimbursedAmount + amount,
                        date = date,
                        byBillId = null
                    )
                }
                _isSaving.value = false
                onDone()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "markReimbursed failed (expenseId=${expense.id})", e)
                _errorMessage.value = appContext.getString(R.string.reimbursement_error_failed)
                _isSaving.value = false
            }
        }
    }

    /** 撤销整笔报销，退回待报销；系统代记的那笔报销收入会一并删除（进回收站，可恢复）。 */
    fun revoke(expenseId: Long) {
        viewModelScope.launch {
            try {
                billRepository.clearReimbursement(expenseId)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "revoke failed (expenseId=$expenseId)", e)
                _errorMessage.value = appContext.getString(R.string.reimbursement_error_failed)
            }
        }
    }
}
