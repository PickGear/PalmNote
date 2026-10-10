package com.palmnote.data.repository
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.Wallet
import com.palmnote.ui.bills.walletPresets
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.WalletRepository

class WalletRepositoryImpl @Inject constructor(
    private val walletDao: WalletDao,
    private val billRepository: BillRepository,
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

    override suspend fun delete(id: Long) = walletDao.deleteWallet(id)


    /**
     * 删除钱包及其全部账单。账单部分**必须**走 [BillRepository.deleteByWallet]：
     * 进回收站、回滚钱包余额、解绑报销关联、事务提交后清理图片文件——
     * 此前这里直调 `billDao.deleteByWallet` 硬删，用户删错钱包连反悔的机会都没有
     * （2026-10-11 回收站覆盖度审计发现；BillRepository 里现成的包装版一直没被调用）。
     *
     * 两步各自成事务：账单先进回收站（失败则原样不动），钱包本体随后删。
     * 账单回滚余额动作发生在即将被删的钱包行上，随后随行一起消失，无副作用。
     */
    override suspend fun deleteWalletWithData(walletId: Long) {
        billRepository.deleteByWallet(walletId)
        appDatabase.withTransaction { walletDao.deleteWallet(walletId) }
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
