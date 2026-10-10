package com.palmnote.ui.widget

import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import java.time.LocalDate
import java.time.ZoneId
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
 * 桌面打卡核心动作（[HabitCheckInReceiver.toggle]）跑在 in-memory Room 上。
 *
 * 守住四条口径（与生活页详情的 `toggleCheckIn` 逐条对齐）：
 * 首次打卡插一条 LifeItem 行；再点一次是**软撤销**（归档、字段数据保留）；
 * 第三次是**复活**归档行而不是插新行；演示感知按 meta 互斥。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class HabitCheckInReceiverTest {

    private lateinit var db: AppDatabase
    private var templateId: Long = 0L
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        templateId = runBlocking {
            db.lifeTemplateDao().insertTemplate(
                LifeTemplate(
                    name = "跑步",
                    category = "habit",
                    icon = "calendar_month",
                    color = "#0891B2",
                    fieldsConfig = "{}",
                    layoutType = "list",
                    availableLayouts = "[]",
                    statusFlowConfig = "[]",
                    linkConfig = "{}"
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun toggle(includeDemo: Boolean = false) = runBlocking {
        HabitCheckInReceiver.toggle(
            dao = db.lifeItemDao(),
            templateId = templateId,
            title = "跑步",
            includeDemo = includeDemo,
            demoMeta = LIFE_DEMO_META,
            today = today
        )
    }

    /** 今天该模板的全部行（含归档），按状态无关地取。 */
    private fun todayRows(): List<LifeItem> = runBlocking {
        db.lifeItemDao().getDayItemsOfTemplate(templateId, today.toString(), false, LIFE_DEMO_META)
            .mapNotNull { db.lifeItemDao().getItemById(it.itemId) }
    }

    @Test
    fun `首次打卡插一条今天的行`() {
        assertTrue("打卡后应处于已打卡态", toggle())

        val rows = todayRows()
        assertEquals(1, rows.size)
        assertEquals("ACTIVE", rows.first().status)
        // 日期落在今天 0 点，与生活页/月历口径一致
        val dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(dayStart, rows.first().dueDate)
    }

    @Test
    fun `再点一次是软撤销而不是删行`() {
        toggle()
        val rowId = todayRows().first().id

        assertFalse("撤销后应处于未打卡态", toggle())

        val rows = todayRows()
        assertEquals("撤销不应删行（字段数据要保留）", 1, rows.size)
        assertEquals(rowId, rows.first().id)
        assertEquals("ARCHIVED", rows.first().status)
    }

    @Test
    fun `第三次是复活归档行不重复插行`() {
        toggle()
        toggle()
        val rowId = todayRows().first().id

        assertTrue(toggle())

        val rows = todayRows()
        assertEquals("复活不应新增行", 1, rows.size)
        assertEquals(rowId, rows.first().id)
        assertEquals("ACTIVE", rows.first().status)
    }

    @Test
    fun `演示模式打卡落在示例行上，与真实行互斥`() {
        // 先有一条「自己的」行
        toggle(includeDemo = false)

        // 演示态下打卡只看得见示例行 → 应新插一条带 demo meta 的行
        assertTrue(toggle(includeDemo = true))

        val demoRows = runBlocking {
            db.lifeItemDao().getDayItemsOfTemplate(templateId, today.toString(), true, LIFE_DEMO_META)
                .mapNotNull { db.lifeItemDao().getItemById(it.itemId) }
        }
        assertEquals(1, demoRows.size)
        assertEquals(LIFE_DEMO_META, demoRows.first().meta)
    }
}
