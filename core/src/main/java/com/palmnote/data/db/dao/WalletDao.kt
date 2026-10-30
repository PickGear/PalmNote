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

    @Query("SELECT SUM(currentBalance) FROM wallets WHERE isEnabled = 1 AND type != 'CREDIT_CARD' AND isDemo = :includeDemo")
    fun getTotalBalance(includeDemo: Boolean): Flow<Long?>

    @Query("SELECT SUM(currentBalance) FROM wallets WHERE isEnabled = 1 AND type = 'CREDIT_CARD' AND isDemo = :includeDemo")
    fun getTotalCreditCardBalance(includeDemo: Boolean): Flow<Long?>

    @Query("SELECT COUNT(*) FROM wallets WHERE isEnabled = 1 AND isDemo = :includeDemo")
    fun getEnabledWalletCount(includeDemo: Boolean): Flow<Int>

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

    /** 演示数据清理：只删示例种子（v19 `demoOrigin = 'SEED'`）。 */
    @Query("DELETE FROM wallets WHERE demoOrigin = 'SEED'")
    suspend fun clearDemoWallets()

    /** 当前示例行数（关演示反馈 / 幂等判断用）。 */
    @Query("SELECT COUNT(*) FROM wallets WHERE isDemo = 1")
    suspend fun countDemoWallets(): Int

    /** 演示期自建钱包数（毕业询问计数用）。 */
    @Query("SELECT COUNT(*) FROM wallets WHERE demoOrigin = 'USER'")
    suspend fun countUserWallets(): Int

    /** 关演示·保留：演示期自建的钱包毕业为真实行（钱包没有账本维度，原地翻标记）。 */
    @Query("UPDATE wallets SET isDemo = 0, demoOrigin = 'NONE' WHERE demoOrigin = 'USER'")
    suspend fun graduateUserWallets()

    /** 关演示·一并删：演示期自建的钱包也清掉（用户明确不要这些内容）。 */
    @Query("DELETE FROM wallets WHERE demoOrigin = 'USER'")
    suspend fun clearUserWalletsFromDemo()
}
