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
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.theme.AppIcon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.palmnote.domain.repository.AccountBookRepository
import com.palmnote.domain.repository.BillRepository
@OptIn(ExperimentalCoroutinesApi::class)
class AccountBookRepositoryImpl @Inject constructor(
    private val accountBookDao: AccountBookDao,
    private val billRepository: BillRepository,
    private val appDatabase: AppDatabase,
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) : AccountBookRepository {
    /**
     * 演示模式下的账本列表**只暴露示例账本**。
     *
     * 这是「全 App 口径唯一」的支点：账单页、报表、账单小组件全部按账本取数，
     * 所以只要可见账本只剩示例账本，这三处的示例隔离就自动成立，不必去改几十个聚合查询。
     * 关掉演示即回到真实账本（示例账本连同示例行一起被物理删除）。
     */
    private fun demoScopedBooks(source: Flow<List<AccountBook>>): Flow<List<AccountBook>> =
        preferencesManager.lifeDemoMode.flatMapLatest { demo ->
            // 与物品 / 钱包同一写法（`isDemo == demo`），不写成 `if (demo) 过滤 else 原样`：
            // 关演示后若残留示例账本（清理失败、或从旧备份恢复回来），也要和残留的示例行一样不显示。
            source.map { books -> books.filter { it.isDemo == demo } }
        }

    override fun getAllBooks(): Flow<List<AccountBook>> = demoScopedBooks(accountBookDao.getAllBooks())

    override fun getAllBooksIncludingHidden(): Flow<List<AccountBook>> =
        demoScopedBooks(accountBookDao.getAllBooksIncludingHidden())

    override fun getHiddenBooks(): Flow<List<AccountBook>> = demoScopedBooks(accountBookDao.getHiddenBooks())

    override suspend fun getBookById(id: Long): AccountBook? = accountBookDao.getBookById(id)

    override suspend fun getDefaultBook(): AccountBook? = accountBookDao.getDefaultBook()

    override suspend fun demoBookId(): Long? = accountBookDao.demoBookId()

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
    /**
     * 示例账本不允许删除：演示模式下列表只有它，删掉会被播种守卫重播，
     * 用户看到的是"数据自己复活"。这里兜底，界面也据此不渲染删除入口。
     */
    override suspend fun deleteAccountBookWithData(bookId: Long) {
        // 示例账本禁删：删掉会被播种守卫重播，表现成"数据自己复活"
        if (accountBookDao.getBookById(bookId)?.isDemo == true) return
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
