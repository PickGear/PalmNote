package com.palmnote.data.repository
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.Wallet
import com.palmnote.ui.bills.walletPresets
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.WalletRepository

class WalletRepositoryImpl @Inject constructor(
    private val walletDao: WalletDao,
    private val billDao: BillDao,
    private val appDatabase: AppDatabase,
    @ApplicationContext private val context: Context
) : WalletRepository {
    override fun getEnabledWallets(): Flow<List<Wallet>> = walletDao.getEnabledWallets()

    override fun getAllWallets(): Flow<List<Wallet>> = walletDao.getAllWallets()

    override suspend fun getWalletById(id: Long): Wallet? = walletDao.getWalletById(id)

    override fun getWalletByIdFlow(id: Long): Flow<Wallet?> = walletDao.getWalletByIdFlow(id)

    override fun getWalletsByType(type: String): Flow<List<Wallet>> = walletDao.getWalletsByType(type)

    override suspend fun getDefaultWallet(): Wallet? = walletDao.getDefaultWallet()

    override fun getDefaultWalletFlow(): Flow<Wallet?> = walletDao.getDefaultWalletFlow()

    override fun getTotalBalance(): Flow<Long?> = walletDao.getTotalBalance()

    override fun getTotalCreditCardBalance(): Flow<Long?> = walletDao.getTotalCreditCardBalance()

    override fun getEnabledWalletCount(): Flow<Int> = walletDao.getEnabledWalletCount()

    override suspend fun insert(wallet: Wallet): Long = walletDao.insert(wallet)

    override suspend fun update(wallet: Wallet) = walletDao.update(wallet)

    override suspend fun updateBalance(id: Long, balance: Long) = walletDao.updateBalance(id, balance)

    override suspend fun adjustBalance(id: Long, amount: Long) = walletDao.adjustBalance(id, amount)

    override suspend fun setDefault(id: Long) = walletDao.setAsDefault(id)

    override suspend fun setEnabled(id: Long, enabled: Boolean) = walletDao.setEnabled(id, enabled)

    override suspend fun softDelete(id: Long) = walletDao.softDelete(id)

    override suspend fun hardDelete(id: Long) = walletDao.hardDelete(id)

    override suspend fun deleteWalletWithData(walletId: Long) = appDatabase.withTransaction {
        billDao.softDeleteByWallet(walletId)
        walletDao.softDelete(walletId)
    }

    override suspend fun initDefaultWallets() = appDatabase.withTransaction {
        val existing = getDefaultWallet()
        if (existing != null) return@withTransaction

        walletPresets.forEach { preset ->
            walletDao.insert(
                Wallet(
                    name = context.getString(preset.nameRes),
                    type = preset.type,
                    icon = preset.icon,
                    color = "#%02X%02X%02X".format((preset.color.red * 255).toInt(), (preset.color.green * 255).toInt(), (preset.color.blue * 255).toInt()),
                    isDefault = preset.isDefault,
                    currentBalance = 0,
                    initialBalance = 0,
                    sortOrder = walletPresets.indexOf(preset)
                )
            )
        }
    }
}
