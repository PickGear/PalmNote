package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Wallet
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets WHERE isEnabled = 1 ORDER BY isDefault DESC, sortOrder ASC")
    fun getEnabledWallets(): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets ORDER BY isDefault DESC, sortOrder ASC")
    fun getAllWallets(): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getWalletById(id: Long): Wallet?

    @Query("SELECT * FROM wallets WHERE id = :id")
    fun getWalletByIdFlow(id: Long): Flow<Wallet?>

    @Query("SELECT * FROM wallets WHERE type = :type")
    fun getWalletsByType(type: String): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultWallet(): Wallet?

    @Query("SELECT * FROM wallets WHERE isDefault = 1 LIMIT 1")
    fun getDefaultWalletFlow(): Flow<Wallet?>

    @Query("SELECT SUM(currentBalance) FROM wallets WHERE isEnabled = 1 AND type != 'CREDIT_CARD'")
    fun getTotalBalance(): Flow<Long?>

    @Query("SELECT SUM(currentBalance) FROM wallets WHERE isEnabled = 1 AND type = 'CREDIT_CARD'")
    fun getTotalCreditCardBalance(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM wallets WHERE isEnabled = 1")
    fun getEnabledWalletCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallet: Wallet): Long

    @Update
    suspend fun update(wallet: Wallet)

    @Query("UPDATE wallets SET currentBalance = :balance, updatedAt = :now WHERE id = :id")
    suspend fun updateBalance(id: Long, balance: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE wallets SET currentBalance = currentBalance + :amount, updatedAt = :now WHERE id = :id")
    suspend fun adjustBalance(id: Long, amount: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE wallets SET isDefault = 1, updatedAt = :now WHERE id = :id")
    suspend fun setDefault(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE wallets SET isDefault = 0, updatedAt = :now WHERE isDefault = 1")
    suspend fun clearAllDefaults(now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun setAsDefault(id: Long, now: Long = System.currentTimeMillis()) {
        clearAllDefaults(now)
        setDefault(id, now)
    }

    @Query("UPDATE wallets SET isEnabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long = System.currentTimeMillis())



    @Query("DELETE FROM wallets WHERE id = :id")
    suspend fun deleteWallet(id: Long)

    @Query("DELETE FROM wallets")
    suspend fun deleteAll()
}
