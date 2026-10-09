package com.palmnote.ui.widget

import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.LifeItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 桌面待办勾选核心动作（[TodoToggleReceiver.toggle]）跑在 in-memory Room 上。
 *
 * 守住三条写库口径：ACTIVE↔COMPLETED 双向切换、子项（parentId != null）不动、
 * 不存在的 id 返回 false。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class TodoToggleReceiverTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun item(parentId: Long? = null, status: String = "ACTIVE") =
        LifeItem(templateId = 1, title = "买牛奶", status = status, parentId = parentId)

    @Test
    fun `ACTIVE 切换为 COMPLETED`() = runBlocking {
        val id = db.lifeItemDao().insertItem(item())

        assertTrue(TodoToggleReceiver.toggle(db.lifeItemDao(), id))

        assertEquals("COMPLETED", db.lifeItemDao().getItemById(id)!!.status)
    }

    @Test
    fun `COMPLETED 切回 ACTIVE`() = runBlocking {
        val id = db.lifeItemDao().insertItem(item(status = "COMPLETED"))

        assertTrue(TodoToggleReceiver.toggle(db.lifeItemDao(), id))

        assertEquals("ACTIVE", db.lifeItemDao().getItemById(id)!!.status)
    }

    @Test
    fun `子项不动`() = runBlocking {
        val parentId = db.lifeItemDao().insertItem(item())
        val childId = db.lifeItemDao().insertItem(item(parentId = parentId))

        assertFalse("子项不应被桌面勾选切换", TodoToggleReceiver.toggle(db.lifeItemDao(), childId))

        assertEquals("ACTIVE", db.lifeItemDao().getItemById(childId)!!.status)
    }

    @Test
    fun `不存在的 id 返回 false`() = runBlocking {
        assertFalse(TodoToggleReceiver.toggle(db.lifeItemDao(), 9999L))
    }
}
