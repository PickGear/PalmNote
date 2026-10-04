package com.palmnote.data.dao

import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LifeItemDaoSearchTest {

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

    @Test
    fun `search excludes items from hidden templates`() = runBlocking {
        val visibleTemplateId = insertTemplate("可见", isHidden = false)
        val hiddenTemplateId = insertTemplate("已关闭", isHidden = true)
        db.lifeItemDao().insertItem(LifeItem(templateId = visibleTemplateId, title = "匹配可见"))
        db.lifeItemDao().insertItem(LifeItem(templateId = hiddenTemplateId, title = "匹配隐藏"))

        val results = db.lifeItemDao().search("匹配")

        assertEquals(listOf("匹配可见"), results.map { it.title })
    }

    @Test
    fun `searchItems excludes items from hidden templates`() = runBlocking {
        val visibleTemplateId = insertTemplate("可见", isHidden = false)
        val hiddenTemplateId = insertTemplate("已关闭", isHidden = true)
        db.lifeItemDao().insertItem(LifeItem(templateId = visibleTemplateId, title = "保留"))
        db.lifeItemDao().insertItem(LifeItem(templateId = hiddenTemplateId, title = "隐藏"))
        db.lifeItemDao().insertItem(
            LifeItem(templateId = hiddenTemplateId, title = "另一条", fieldsData = """{"note":"字段匹配"}""")
        )

        val results = db.lifeItemDao().searchItems("匹配").first()

        assertEquals(emptyList<String>(), results.map { it.title })
    }

    @Test
    fun `anniversary items exclude hidden templates`() = runBlocking {
        val visibleTemplateId = insertTemplate("生日", isHidden = false, icon = "cake")
        val hiddenTemplateId = insertTemplate("纪念日", isHidden = true, icon = "celebration")
        db.lifeItemDao().insertItem(
            LifeItem(templateId = visibleTemplateId, title = "可见生日", dueDate = 1_700_000_000_000)
        )
        db.lifeItemDao().insertItem(
            LifeItem(templateId = hiddenTemplateId, title = "隐藏纪念日", dueDate = 1_800_000_000_000)
        )

        val results = db.lifeItemDao().getAnniversaryLikeItems(false, LIFE_DEMO_META).first()

        assertEquals(listOf("可见生日"), results.map { it.title })
    }

    @Test
    fun `calendar density excludes hidden templates`() = runBlocking {
        val visibleTemplateId = insertTemplate("可见", isHidden = false)
        val hiddenTemplateId = insertTemplate("已关闭", isHidden = true)
        db.lifeItemDao().insertItem(
            LifeItem(templateId = visibleTemplateId, title = "可见记录", dueDate = 1_000)
        )
        db.lifeItemDao().insertItem(
            LifeItem(templateId = hiddenTemplateId, title = "隐藏记录", dueDate = 1_000)
        )

        val counts = db.lifeItemDao().getDayCountsBetween(0, 10_000).first()

        assertEquals(1, counts.sumOf { it.cnt })
    }

    @Test
    fun `template record count excludes hidden templates`() = runBlocking {
        val hiddenTemplateId = insertTemplate("已关闭", isHidden = true)
        db.lifeItemDao().insertItem(LifeItem(templateId = hiddenTemplateId, title = "隐藏记录", createdAt = 1_000))

        val count = db.lifeItemDao()
            .getTemplateRecordCount(hiddenTemplateId, 0, 10_000, false, LIFE_DEMO_META)
            .first()

        assertEquals(0, count)
    }

    @Test
    fun `focus sessions exclude hidden templates`() = runBlocking {
        val visibleTemplateId = insertTemplate("专注可见", isHidden = false, icon = "timer")
        val hiddenTemplateId = insertTemplate("专注隐藏", isHidden = true, icon = "timer")
        db.lifeItemDao().insertItem(LifeItem(templateId = visibleTemplateId, title = "会话可见", createdAt = 1_000))
        db.lifeItemDao().insertItem(LifeItem(templateId = hiddenTemplateId, title = "会话隐藏", createdAt = 1_000))

        val visibleRows = db.lifeItemDao()
            .getFocusSessionsDemoAware(visibleTemplateId, 0, 10_000, false, LIFE_DEMO_META)
            .first()
        val hiddenRows = db.lifeItemDao()
            .getFocusSessionsDemoAware(hiddenTemplateId, 0, 10_000, false, LIFE_DEMO_META)
            .first()

        assertEquals(listOf("会话可见"), visibleRows.map { it.title })
        assertEquals(emptyList<String>(), hiddenRows.map { it.title })
    }

    @Test
    fun `unfinished count excludes hidden templates`() = runBlocking {
        val hiddenTemplateId = insertTemplate("已关闭", isHidden = true)
        db.lifeItemDao().insertItem(
            LifeItem(templateId = hiddenTemplateId, title = "未完成隐藏", status = "ACTIVE", createdAt = 1_000)
        )

        val count = db.lifeItemDao()
            .getUnfinishedCountByTemplate(hiddenTemplateId, 0, 10_000, false, LIFE_DEMO_META)
            .first()

        assertEquals(0, count)
    }

    /**
     * 订阅到期清单用的**批量**查询：一次取多个模板，且必须与其它出口同一套演示互斥口径。
     * 原实现走没有演示过滤的 `getActiveItemsByTemplate`，于是演示模式关掉后示例订阅仍会
     * 列进「即将扣费」、开着时反过来看不到真实订阅。顺带确认不带出隐藏模板与已归档记录。
     */
    @Test
    fun `batched active items are demo exclusive and drop hidden or archived`() = runBlocking {
        val subA = insertTemplate("订阅A", isHidden = false)
        val subB = insertTemplate("订阅B", isHidden = false)
        val hidden = insertTemplate("订阅隐藏", isHidden = true)
        db.lifeItemDao().insertItem(LifeItem(templateId = subA, title = "真实A"))
        db.lifeItemDao().insertItem(LifeItem(templateId = subB, title = "真实B"))
        db.lifeItemDao().insertItem(LifeItem(templateId = subA, title = "示例A", meta = LIFE_DEMO_META))
        db.lifeItemDao().insertItem(LifeItem(templateId = hidden, title = "隐藏模板"))
        db.lifeItemDao().insertItem(LifeItem(templateId = subA, title = "已归档", status = "ARCHIVED"))

        val ids = listOf(subA, subB)
        val realOnly = db.lifeItemDao().getActiveItemsByTemplateIds(ids, false, LIFE_DEMO_META)
        val demoOnly = db.lifeItemDao().getActiveItemsByTemplateIds(ids, true, LIFE_DEMO_META)

        assertEquals(listOf("真实A", "真实B"), realOnly.map { it.title }.sorted())
        assertEquals(listOf("示例A"), demoOnly.map { it.title })
    }

    private suspend fun insertTemplate(
        name: String,
        isHidden: Boolean,
        icon: String = name
    ): Long = db.lifeTemplateDao().insertTemplate(
        LifeTemplate(
            name = name,
            category = "计划",
            icon = icon,
            color = "#000000",
            fieldsConfig = "[]",
            layoutType = "card",
            availableLayouts = """["card"]""",
            statusFlowConfig = "{}",
            linkConfig = "{}",
            isHidden = isHidden
        )
    )
}
