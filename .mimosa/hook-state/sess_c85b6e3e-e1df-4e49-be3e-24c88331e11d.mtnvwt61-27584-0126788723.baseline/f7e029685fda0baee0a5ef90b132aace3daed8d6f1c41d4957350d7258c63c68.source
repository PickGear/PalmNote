package com.palmnote.feature.bills.usecase

import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.BillRecycleBinDao
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.toRecycleBin
import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.event.EventBus
import com.palmnote.domain.model.BillType
import javax.inject.Inject

/**
 * 删除账单：移到回收站 + 反向调整余额 + 发布事件。
 */
class DeleteBillUseCase @Inject constructor(
    private val billDao: BillDao,
    private val recycleBinDao: BillRecycleBinDao,
    private val walletDao: WalletDao,
    private val appDatabase: AppDatabase,
    private val eventBus: EventBus
) {
    suspend operator fun invoke(billId: Long): Unit = appDatabase.withTransaction {
        val bill = billDao.getBillById(billId) ?: return@withTransaction
        recycleBinDao.insert(bill.toRecycleBin())
        billDao.deleteById(billId)
        // 反向调整余额
        when (bill.type) {
            BillType.EXPENSE -> bill.walletId?.let { walletDao.adjustBalance(it, bill.amount) }
            BillType.INCOME -> bill.walletId?.let { walletDao.adjustBalance(it, -bill.amount) }
            BillType.TRANSFER -> {
                bill.walletId?.let { walletDao.adjustBalance(it, bill.amount) }
                bill.toWalletId?.let { walletDao.adjustBalance(it, -bill.amount) }
            }
        }
        eventBus.publish(DomainEvent.BillDeleted(billId))
    }
}
