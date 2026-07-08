package com.palmnote.domain.repository

import com.palmnote.data.db.entity.AccountBook
import kotlinx.coroutines.flow.Flow

interface AccountBookRepository {
    fun getAllBooks(): Flow<List<AccountBook>>
    fun getAllBooksIncludingHidden(): Flow<List<AccountBook>>
    fun getHiddenBooks(): Flow<List<AccountBook>>
    suspend fun getBookById(id: Long): AccountBook?
    suspend fun getDefaultBook(): AccountBook?
    suspend fun insertBook(book: AccountBook): Long
    suspend fun updateBook(book: AccountBook)
    suspend fun setDefault(id: Long)
    suspend fun setHidden(id: Long, hidden: Boolean)
    suspend fun softDeleteBook(id: Long)
    suspend fun initDefaultBooks()
}
