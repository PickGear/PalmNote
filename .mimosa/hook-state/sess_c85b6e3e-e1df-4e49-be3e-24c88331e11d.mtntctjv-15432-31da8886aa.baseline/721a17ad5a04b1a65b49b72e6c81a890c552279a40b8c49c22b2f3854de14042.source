package com.palmnote.feature.bills.usecase

import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.BillRecycleBinDao
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.toBill
import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.event.EventBus
import com.palmnote.domain.model.BillType
import javax.inject.Inject

/**
 * 恢复账单：从回收站恢复 + 恢复余额 + 发布事件。
 */
class RestoreBillUseCase @Inject constructor(
    private val billDao: BillDao,
    private val recycleBinDao: BillRecycleBinDao,
    private val walletDao: WalletDao,
    private val appDatabase: AppDatabase,
    private val eventBus: EventBus
) {
    suspend operator fun invoke(recycleBinId: Long): Unit = appDatabase.withTransaction {
        val item = recycleBinDao.getById(recycleBinId) ?: return@withTransaction
        val bill = item.toBill()
        billDao.insertBill(bill)
        recycleBinDao.deleteById(recycleBinId)
        // 恢复余额
        when (bill.type) {
            BillType.EXPENSE -> bill.walletId?.let { walletDao.adjustBalance(it, -bill.amount) }
            BillType.INCOME -> bill.walletId?.let { walletDao.adjustBalance(it, bill.amount) }
            BillType.TRANSFER -> {
                bill.walletId?.let { walletDao.adjustBalance(it, -bill.amount) }
                bill.toWalletId?.let { walletDao.adjustBalance(it, bill.amount) }
            }
        }
        eventBus.publish(DomainEvent.BillRestored(bill.id))
    }
}
