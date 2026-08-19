package com.palmnote.feature.bills.usecase

import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.event.DomainEvent
import com.palmnote.domain.event.EventBus
import com.palmnote.domain.model.BillType
import javax.inject.Inject

/**
 * 创建账单：插入账单 + 调整钱包余额 + 发布事件，一个事务内完成。
 */
class CreateBillUseCase @Inject constructor(
    private val billDao: BillDao,
    private val walletDao: WalletDao,
    private val appDatabase: AppDatabase,
    private val eventBus: EventBus
) {
    suspend operator fun invoke(bill: Bill): Long = appDatabase.withTransaction {
        val id = billDao.insertBill(bill)
        when (bill.type) {
            BillType.EXPENSE -> bill.walletId?.let { walletDao.adjustBalance(it, -bill.amount) }
            BillType.INCOME -> bill.walletId?.let { walletDao.adjustBalance(it, bill.amount) }
            BillType.TRANSFER -> {
                bill.walletId?.let { walletDao.adjustBalance(it, -bill.amount) }
                bill.toWalletId?.let { walletDao.adjustBalance(it, bill.amount) }
            }
        }
        eventBus.publish(DomainEvent.BillCreated(id, bill.type, bill.amount))
        id
    }
}
