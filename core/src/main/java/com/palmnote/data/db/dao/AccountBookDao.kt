package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.AccountBook
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountBookDao {
    @Query("SELECT * FROM account_books ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllBooksIncludingHidden(): Flow<List<AccountBook>>

    @Query("SELECT * FROM account_books WHERE isHidden = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllBooks(): Flow<List<AccountBook>>

    @Query("SELECT * FROM account_books WHERE isHidden = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun getHiddenBooks(): Flow<List<AccountBook>>

    @Query("SELECT * FROM account_books WHERE id = :id")
    suspend fun getBookById(id: Long): AccountBook?

    /**
     * 按类型取账本（一次性）。目前只服务于「预设账本出厂色刷新」：
     * 那种场景拿不到 Flow 也无所谓，要的就是当下这一份快照。
     */
    @Query("SELECT * FROM account_books WHERE bookType = :bookType")
    suspend fun getBooksByType(bookType: String): List<AccountBook>

    @Query("SELECT * FROM account_books WHERE isDefault = 1 AND isHidden = 0 LIMIT 1")
    suspend fun getDefaultBook(): AccountBook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: AccountBook): Long

    @Update
    suspend fun updateBook(book: AccountBook)

    @Query("UPDATE account_books SET isDefault = 1, updatedAt = :now WHERE id = :id")
    suspend fun setDefault(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE account_books SET isDefault = 0, updatedAt = :now WHERE isDefault = 1")
    suspend fun clearAllDefaults(now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun setAsDefault(id: Long, now: Long = System.currentTimeMillis()) {
        clearAllDefaults(now)
        setDefault(id, now)
    }

    @Query("UPDATE account_books SET isHidden = :hidden, updatedAt = :now WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean, now: Long = System.currentTimeMillis())


    @Query("DELETE FROM account_books WHERE id = :id")
    suspend fun deleteBook(id: Long)

    /** 演示数据清理（v14 isDemo 列）：演示模式关闭时物理删除示例行。 */
    @Query("DELETE FROM account_books WHERE isDemo = 1")
    suspend fun clearDemoBooks()

    /** 当前示例行数（关演示反馈 / 幂等判断用）。 */
    @Query("SELECT COUNT(*) FROM account_books WHERE isDemo = 1")
    suspend fun countDemoBooks(): Int

    /** 演示期用户自建账单的毕业迁移目标：优先默认账本，其次最早的普通账本。 */
    @Query("SELECT id FROM account_books WHERE isDemo = 0 ORDER BY isDefault DESC, sortOrder ASC LIMIT 1")
    suspend fun firstRealBookId(): Long?
}
