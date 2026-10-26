package com.palmnote.ui.settings
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.repository.WalletRepository
import com.palmnote.ui.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository
) : ViewModel() {

    val wallets: StateFlow<List<Wallet>> = walletRepository.getAllWallets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBalance: StateFlow<Long?> = walletRepository.getTotalBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun getWalletByIdFlow(walletId: Long): Flow<Wallet?> =
        walletRepository.getWalletByIdFlow(walletId)

    fun addWallet(wallet: Wallet) {
        viewModelScope.launch {
            walletRepository.insert(wallet)
            WidgetUpdateHelper.refreshNetWorthWidgets()
            WidgetUpdateHelper.refreshDashboardWidgets()
        }
    }

    fun updateWallet(wallet: Wallet) {
        viewModelScope.launch {
            walletRepository.update(wallet.copy(updatedAt = System.currentTimeMillis()))
            // 编辑含改期初/当前余额：净值与概览 hero 都吃它
            WidgetUpdateHelper.refreshNetWorthWidgets()
            WidgetUpdateHelper.refreshDashboardWidgets()
        }
    }

    fun deleteWallet(id: Long) {
        viewModelScope.launch {
            walletRepository.delete(id)
        }
    }

    fun deleteWalletWithData(walletId: Long) {
        viewModelScope.launch {
            walletRepository.deleteWalletWithData(walletId)
            // 账单进回收站 + 钱包行消失：账单/净值/概览三路都要跟
            WidgetUpdateHelper.refreshBillWidgets()
            WidgetUpdateHelper.refreshNetWorthWidgets()
        }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch {
            walletRepository.setDefault(id)
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            walletRepository.setEnabled(id, enabled)
        }
    }
}
