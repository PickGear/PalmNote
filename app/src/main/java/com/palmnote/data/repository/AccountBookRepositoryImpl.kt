package com.palmnote.data.repository
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context
import androidx.room.withTransaction
import com.palmnote.app.R
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.AccountBookDao
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.LEGACY_PRESET_BOOK_COLORS
import com.palmnote.data.db.entity.presetBookColor
import com.palmnote.ui.theme.AppIcon
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.AccountBookRepository
import com.palmnote.domain.repository.BillRepository
class AccountBookRepositoryImpl @Inject constructor(
    private val accountBookDao: AccountBookDao,
    private val billRepository: BillRepository,
    private val appDatabase: AppDatabase,
    @ApplicationContext private val context: Context
) : AccountBookRepository {
    override fun getAllBooks(): Flow<List<AccountBook>> = accountBookDao.getAllBooks()

    override fun getAllBooksIncludingHidden(): Flow<List<AccountBook>> = accountBookDao.getAllBooksIncludingHidden()

    override fun getHiddenBooks(): Flow<List<AccountBook>> = accountBookDao.getHiddenBooks()

    override suspend fun getBookById(id: Long): AccountBook? = accountBookDao.getBookById(id)

    override suspend fun getDefaultBook(): AccountBook? = accountBookDao.getDefaultBook()

    override suspend fun insertBook(book: AccountBook): Long = accountBookDao.insertBook(book)

    override suspend fun updateBook(book: AccountBook) = accountBookDao.updateBook(book)

    override suspend fun setDefault(id: Long) = accountBookDao.setAsDefault(id)

    override suspend fun setHidden(id: Long, hidden: Boolean) = accountBookDao.setHidden(id, hidden)

    override suspend fun deleteBook(id: Long) = accountBookDao.deleteBook(id)

    /**
     * 删除账本及其全部账单。账单部分**必须**走 [BillRepository.deleteByBook]：
     * 进回收站、回滚钱包余额、解绑报销关联、事务提交后清理图片文件——
     * 此前这里直调 `billDao.deleteByBook` 硬删，与钱包级联删除同病
     * （2026-10-11 回收站覆盖度审计发现）。
     *
     * 两步各自成事务：账单先进回收站（失败则原样不动），账本本体随后删。
     */
    override suspend fun deleteAccountBookWithData(bookId: Long) {
        billRepository.deleteByBook(bookId)
        appDatabase.withTransaction { accountBookDao.deleteBook(bookId) }
    }

    override suspend fun initDefaultBooks() = appDatabase.withTransaction {
        val existing = getDefaultBook()
        val allBooks = accountBookDao.getBookById(AccountBook.ALL_BOOKS_ID)
        if (existing == null) {
            if (allBooks == null) {
                insertAllBooks()
            }
            accountBookDao.insertBook(
                AccountBook(
                    name = context.getString(R.string.account_book_daily_name),
                    icon = AppIcon.MenuBook,
                    color = presetBookColor("DAILY") ?: AccountBook.DEFAULT_COLOR,
                    description = context.getString(R.string.account_book_daily_desc),
                    bookType = "DAILY",
                    isDefault = true
                )
            )
        } else if (allBooks == null) {
            insertAllBooks()
        }
        // 老版本装过的库：把仍停在历史出厂默认色的预设账本刷新成当前配色（用户改过的不动）
        refreshLegacyPresetColors()
    }

    /**
     * 把仍停在**历史出厂默认色**（[LEGACY_PRESET_BOOK_COLORS]）的预设账本刷新成当前默认色。
     *
     * 账本色存在 DB 里，只改种子代码只能影响干净安装；老用户覆盖升级后会一直背着旧色。
     * 这里只在颜色**逐字等于旧出厂值**时改写 —— 那等价于「用户从未碰过这个颜色」；
     * 用户只要在编辑页换过色（包括换成色板里任意一支），值就对不上，此后永远不再触碰。
     *
     * 幂等：刷新后颜色即当前默认色，下次启动对照失败、不产生写入。
     */
    private suspend fun refreshLegacyPresetColors() {
        LEGACY_PRESET_BOOK_COLORS.forEach { (bookType, legacyColor) ->
            val current = presetBookColor(bookType) ?: return@forEach
            accountBookDao.getBooksByType(bookType)
                .filter { it.color.equals(legacyColor, ignoreCase = true) }
                .forEach { book ->
                    accountBookDao.updateBook(
                        book.copy(color = current, updatedAt = System.currentTimeMillis())
                    )
                }
        }
    }

    private suspend fun insertAllBooks() {
        accountBookDao.insertBook(
            AccountBook(
                id = AccountBook.ALL_BOOKS_ID,
                name = context.getString(R.string.account_book_all_name),
                icon = AppIcon.Inventory2,
                color = AccountBook.ALL_BOOKS_COLOR,
                description = context.getString(R.string.account_book_all_desc),
                bookType = "ALL",
                isAllBooks = true,
                sortOrder = -1
            )
        )
    }
}
