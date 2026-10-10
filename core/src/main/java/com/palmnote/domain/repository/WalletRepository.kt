package com.palmnote.domain.repository

import com.palmnote.data.db.entity.Wallet
import kotlinx.coroutines.flow.Flow

interface WalletRepository {
    fun getEnabledWallets(): Flow<List<Wallet>>
    fun getAllWallets(): Flow<List<Wallet>>
    suspend fun getWalletById(id: Long): Wallet?
    fun getWalletByIdFlow(id: Long): Flow<Wallet?>
    fun getWalletsByType(type: String): Flow<List<Wallet>>
    suspend fun getDefaultWallet(): Wallet?
    fun getDefaultWalletFlow(): Flow<Wallet?>
    fun getTotalBalance(): Flow<Long?>
    fun getTotalCreditCardBalance(): Flow<Long?>
    fun getEnabledWalletCount(): Flow<Int>
    suspend fun insert(wallet: Wallet): Long
    suspend fun update(wallet: Wallet)
    suspend fun updateBalance(id: Long, balance: Long)
    suspend fun adjustBalance(id: Long, amount: Long)
    suspend fun setDefault(id: Long)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun delete(id: Long)

    /** 账单进回收站（回滚余额 + 解绑报销），随后删除钱包本体；钱包不可恢复。 */
    suspend fun deleteWalletWithData(walletId: Long)
    suspend fun initDefaultWallets()
}
