package com.palmnote.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.repository.LifeTemplateRepositoryImpl
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 内置生活模板播种的**端到端**验证（Robolectric + 真实 assets + 内存 Room，英文环境）。
 *
 * 双层设计的实证：种子写入时 fieldsConfig / statusFlowConfig 经 DemoTexts 落成**英文**
 * （字段标签是展示文案，库内即本地化值），而模板 `name` 列保持**中文快照**——
 * 名称由显示层 `getDisplayName` 按语义身份翻译（见 LifeTemplate.kt 的说明），
 * 两层各管各的，谁也不覆盖谁。
 *
 * 同步标记也在此链路上实证：marker 记录的是写入的本地化串，重复 seedIfEmpty 幂等。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "en")
class LifeDataSeederEndToEndTest {

    private lateinit var context: android.content.Context
    private lateinit var db: AppDatabase
    private lateinit var seeder: LifeDataSeeder
    private val syncPrefs by lazy {
        context.getSharedPreferences("builtin_template_sync_test", Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seeder = LifeDataSeeder(
            context,
            LifeTemplateRepositoryImpl(
                dao = db.lifeTemplateDao(),
                itemDao = db.lifeItemDao(),
                fieldValueDao = db.fieldValueDao(),
                recycleBinDao = db.lifeRecycleBinDao(),
                appDatabase = db
            ),
            db,
            syncPrefs
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `seeding stores english field labels and keeps name as zh snapshot`() = runBlocking {
        seeder.seedIfEmpty()

        val templates = db.lifeTemplateDao().getAllTemplates().first()
        assertEquals(seeder.lifeTemplateSeeds.size, templates.size)

        val savings = templates.first { it.icon == "savings" }
        // 名称是建库时的中文快照（显示层翻译），不随环境语言变化
        assertEquals("存钱计划", savings.name)
        // 字段标签 / 表格列契约 / 状态标签已随环境语言写入
        assertTrue("字段标签未本地化：${savings.fieldsConfig}", savings.fieldsConfig.contains("Target amount"))
        assertTrue("表格列契约未本地化：${savings.fieldsConfig}", savings.fieldsConfig.contains("name:Item:TEXT"))
        assertTrue("状态标签未本地化：${savings.statusFlowConfig}", savings.statusFlowConfig.contains("In progress"))
        assertTrue("英文字段里不应残留中文标签", !savings.fieldsConfig.contains("目标金额"))
        assertTrue("英文字段里不应残留中文状态", !savings.statusFlowConfig.contains("进行中"))

        // 幂等：重复 seedIfEmpty 不再改写（marker 与库内一致）
        seeder.seedIfEmpty()
        val after = db.lifeTemplateDao().getAllTemplates().first().first { it.icon == "savings" }
        assertEquals(savings.fieldsConfig, after.fieldsConfig)
    }
}
