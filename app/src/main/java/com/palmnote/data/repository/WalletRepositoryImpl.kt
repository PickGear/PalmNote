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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.WalletRepository

/** 演示模式下钱包同样分流：只暴露示例钱包，余额也只算示例（见 AssetRepositoryImpl 说明）。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WalletRepositoryImpl @Inject constructor(
    private val walletDao: WalletDao,
    private val billRepository: BillRepository,
    private val appDatabase: AppDatabase,
    @ApplicationContext private val context: Context,
    private val eventBus: com.palmnote.domain.event.EventBus,
    private val preferencesManager: com.palmnote.data.datastore.PreferencesManager
) : WalletRepository {

    private fun scopedList(source: kotlinx.coroutines.flow.Flow<List<com.palmnote.data.db.entity.Wallet>>) =
        preferencesManager.lifeDemoMode.flatMapLatest { demo ->
            source.map { list -> list.filter { it.isDemo == demo } }
        }

    private fun <T> scoped(block: (Boolean) -> kotlinx.coroutines.flow.Flow<T>) =
        preferencesManager.lifeDemoMode.flatMapLatest { demo -> block(demo) }
    override fun getEnabledWallets(): Flow<List<Wallet>> = scopedList(walletDao.getEnabledWallets())

    override fun getAllWallets(): Flow<List<Wallet>> = scopedList(walletDao.getAllWallets())

    override suspend fun getWalletById(id: Long): Wallet? = walletDao.getWalletById(id)

    override fun getWalletByIdFlow(id: Long): Flow<Wallet?> = walletDao.getWalletByIdFlow(id)

    override fun getWalletsByType(type: String): Flow<List<Wallet>> = walletDao.getWalletsByType(type)

    override suspend fun getDefaultWallet(): Wallet? = walletDao.getDefaultWallet()

    override fun getDefaultWalletFlow(): Flow<Wallet?> = walletDao.getDefaultWalletFlow()

    override fun getTotalBalance(): Flow<Long?> = scoped { walletDao.getTotalBalance(it) }

    override fun getTotalCreditCardBalance(): Flow<Long?> =
        scoped { walletDao.getTotalCreditCardBalance(it) }

    override fun getEnabledWalletCount(): Flow<Int> = scoped { walletDao.getEnabledWalletCount(it) }

    /** 演示模式下新建的钱包标 `USER`（同账单口径）。 */
    override suspend fun insert(wallet: Wallet): Long {
        val demo = preferencesManager.lifeDemoMode.first()
        return walletDao.insert(
            if (demo) wallet.copy(isDemo = true, demoOrigin = com.palmnote.domain.model.DemoOrigin.USER) else wallet
        )
    }

    /**
     * 编辑口径与插入一致：演示模式下编辑示例钱包必须**原地保持示例身份**——
     * 表单重建的实体默认真实行，原样写库会让它静默「毕业」（演示列表丢行、
     * 真实数据漏行），与物品 / 账单侧同一缺陷（2026-11-02）。
     */
    override suspend fun update(wallet: Wallet) {
        val aligned = if (preferencesManager.lifeDemoMode.first()) {
            val existing = walletDao.getWalletById(wallet.id)
            if (existing?.isDemo == true) {
                wallet.copy(isDemo = true, demoOrigin = existing.demoOrigin)
            } else wallet
        } else wallet
        walletDao.update(aligned)
    }

    override suspend fun updateBalance(id: Long, balance: Long) {
        walletDao.updateBalance(id, balance)
        runCatching { eventBus.publish(DomainEvent.WalletBalanceChanged(id, balance)) }
    }

    override suspend fun adjustBalance(id: Long, amount: Long) {
        walletDao.adjustBalance(id, amount)
        // 记账/回滚路径不知道新余额，回读后再发（读不到就不发，别发错数）；
        // 整个事件路径 runCatching：桌面刷新失败绝不影响余额更新的主流程
        runCatching {
            val wallet = walletDao.getWalletById(id) ?: return
            eventBus.publish(DomainEvent.WalletBalanceChanged(id, wallet.currentBalance))
        }
    }

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
