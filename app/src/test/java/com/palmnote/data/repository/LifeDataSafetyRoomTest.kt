package com.palmnote.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.event.EventBusImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 生活页写路径安全验收：真实 [LifeItemRepositoryImpl] / [LifeTemplateRepositoryImpl]
 * 跑在 in-memory Room 上。
 *
 * 钉住的都是「主表与统计副本（field_values）不许分叉」的口径：
 * - 插入即双写（后续 VM 收口依赖这个前提）；
 * - 删模板级联必须同事务清掉统计副本，不留孤儿行；
 * - 级联删除按既有口径放过示例条目（示例行本就没有统计副本）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LifeDataSafetyRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var itemRepo: LifeItemRepositoryImpl
    private lateinit var templateRepo: LifeTemplateRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        templateRepo = LifeTemplateRepositoryImpl(
            db.lifeTemplateDao(), db.lifeItemDao(), db.fieldValueDao(), db
        )
        itemRepo = LifeItemRepositoryImpl(
            db.lifeItemDao(), db.fieldValueDao(), db, templateRepo,
            PreferencesManager(context, CoroutineScope(Dispatchers.IO)), EventBusImpl()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun template(config: String) = LifeTemplate(
        name = "测试模板",
        category = "record",
        icon = "fitness_center",
        color = "#0891B2",
        fieldsConfig = config,
        layoutType = "card",
        availableLayouts = """["card","list"]""",
        statusFlowConfig = "{}",
        linkConfig = "{}"
    )

    // ── 双写前提 ──

    @Test
    fun `insertItem writes the statistics copy in the same transaction`() = runBlocking {
        val templateId = templateRepo.insertTemplate(
            template("""[{"key":"duration","label":"时长","type":"DURATION"}]""")
        )
        itemRepo.insertItem(
            LifeItem(templateId = templateId, title = "专注", fieldsData = """{"duration":1500}""")
        )
        assertEquals(1, db.fieldValueDao().countBy("duration").first())
    }

    // ── 删模板级联（S1）──

    @Test
    fun `deleteTemplateCascade removes the statistics copy with the items`() = runBlocking {
        val templateId = templateRepo.insertTemplate(
            template("""[{"key":"amount","label":"金额","type":"NUMBER"}]""")
        )
        itemRepo.insertItem(
            LifeItem(templateId = templateId, title = "记录", fieldsData = """{"amount":42}""")
        )
        assertEquals(1, db.fieldValueDao().countBy("amount").first())

        templateRepo.deleteTemplateCascade(templateId)

        assertEquals(0, db.fieldValueDao().countBy("amount").first())
        assertTrue(
            db.lifeItemDao().getAllItemsIncludingHidden().first().none { it.templateId == templateId }
        )
        assertNull(db.lifeTemplateDao().getTemplateById(templateId))
    }

    @Test
    fun `deleteTemplateCascade keeps demo items and their (empty) copy untouched`() = runBlocking {
        val templateId = templateRepo.insertTemplate(
            template("""[{"key":"amount","label":"金额","type":"NUMBER"}]""")
        )
        // 示例行按既有口径由播种器直插、不写统计副本；级联删除不能误伤它。
        db.lifeItemDao().insertItem(
            LifeItem(
                templateId = templateId, title = "示例", fieldsData = """{"amount":1}""",
                meta = LIFE_DEMO_META, isSeedSample = true
            )
        )
        itemRepo.insertItem(
            LifeItem(templateId = templateId, title = "真实", fieldsData = """{"amount":42}""")
        )

        templateRepo.deleteTemplateCascade(templateId)

        val remaining = db.lifeItemDao().getAllItemsIncludingHidden().first()
            .filter { it.templateId == templateId }
        assertEquals(1, remaining.size)
        assertEquals(LIFE_DEMO_META, remaining.single().meta)
        // 被删的是真实条目的副本；示例行本来就没有副本。
        assertEquals(0, db.fieldValueDao().countBy("amount").first())
        assertNull(db.lifeTemplateDao().getTemplateById(templateId))
        assertNotNull(remaining.single())
    }
}
