package com.palmnote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.WalletRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


class WalletViewModel(
    private val walletRepository: WalletRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    val wallets: StateFlow<List<Wallet>> = walletRepository.getAllWallets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBalance: StateFlow<Double?> = walletRepository.getTotalBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun addWallet(wallet: Wallet) {
        viewModelScope.launch {
            walletRepository.insert(wallet)
        }
    }

    fun updateWallet(wallet: Wallet) {
        viewModelScope.launch {
            walletRepository.update(wallet.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteWallet(id: Long) {
        viewModelScope.launch {
            walletRepository.softDelete(id)
        }
    }

    fun deleteWalletWithData(walletId: Long) {
        viewModelScope.launch {
            billRepository.softDeleteByWallet(walletId)
            walletRepository.softDelete(walletId)
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
