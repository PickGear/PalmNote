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
    fun getTotalBalance(): Flow<Double?>
    fun getTotalCreditCardBalance(): Flow<Double?>
    fun getEnabledWalletCount(): Flow<Int>
    suspend fun insert(wallet: Wallet): Long
    suspend fun update(wallet: Wallet)
    suspend fun updateBalance(id: Long, balance: Double)
    suspend fun adjustBalance(id: Long, amount: Double)
    suspend fun setDefault(id: Long)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun softDelete(id: Long)
    suspend fun hardDelete(id: Long)
    suspend fun initDefaultWallets()
}
