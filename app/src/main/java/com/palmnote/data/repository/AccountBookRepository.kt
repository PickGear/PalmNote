package com.palmnote.data.repository

import android.content.Context
import com.palmnote.R
import com.palmnote.data.db.dao.AccountBookDao
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.ui.theme.AppIcon
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.AccountBookRepository
class AccountBookRepository(
    private val accountBookDao: AccountBookDao,
    private val context: Context
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

    override suspend fun softDeleteBook(id: Long) = accountBookDao.softDeleteBook(id)

    override suspend fun initDefaultBooks() {
        val existing = getDefaultBook()
        val allBooks = accountBookDao.getBookById(AccountBook.ALL_BOOKS_ID)
        if (existing != null) {
            if (allBooks == null) {
                insertAllBooks()
            }
            return
        }
        if (allBooks == null) {
            insertAllBooks()
        }
        accountBookDao.insertBook(
            AccountBook(
                name = context.getString(R.string.account_book_daily_name),
                icon = AppIcon.MenuBook,
                color = "#2D4A3E",
                description = context.getString(R.string.account_book_daily_desc),
                bookType = "DAILY",
                isDefault = true
            )
        )
    }

    private suspend fun insertAllBooks() {
        accountBookDao.insertBook(
            AccountBook(
                id = AccountBook.ALL_BOOKS_ID,
                name = context.getString(R.string.account_book_all_name),
                icon = AppIcon.Inventory2,
                color = "#607D8B",
                description = context.getString(R.string.account_book_all_desc),
                bookType = "ALL",
                isAllBooks = true,
                sortOrder = -1
            )
        )
    }
}
