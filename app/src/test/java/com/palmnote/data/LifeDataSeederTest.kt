package com.palmnote.data

import com.palmnote.data.db.AppDatabase
import com.palmnote.domain.repository.LifeTemplateRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验内置生活模板清单完整性：
 * 防止误删/误改模板（如"存钱计划"曾被事务化重构意外丢失）导致新用户功能不可达。
 */
class LifeDataSeederTest {

    private fun seeder() = LifeDataSeeder(
        mockk<LifeTemplateRepository>(relaxed = true),
        mockk<AppDatabase>(relaxed = true)
    )

    @Test
    fun `lifeTemplateSeeds contains all 15 builtin templates`() {
        val seeds = seeder().lifeTemplateSeeds
        assertEquals(15, seeds.size)
    }

    @Test
    fun `savings template is present with correct key fields`() {
        val seeds = seeder().lifeTemplateSeeds
        val savings = seeds.firstOrNull { it.icon == "savings" }
        assertTrue("savings 模板缺失", savings != null)
        assertEquals("savings", savings?.icon)
        assertEquals("card", savings?.layoutType)
        assertTrue(savings!!.isBuiltin)
    }

    @Test
    fun `template names are unique`() {
        val seeds = seeder().lifeTemplateSeeds
        assertEquals(seeds.size, seeds.map { it.name }.distinct().size)
    }

    @Test
    fun `template icons are unique`() {
        val seeds = seeder().lifeTemplateSeeds
        assertEquals(seeds.size, seeds.map { it.icon }.distinct().size)
    }

    @Test
    fun `all templates have positive sort order`() {
        val seeds = seeder().lifeTemplateSeeds
        assertTrue(seeds.all { it.sortOrder > 0 })
    }
}
