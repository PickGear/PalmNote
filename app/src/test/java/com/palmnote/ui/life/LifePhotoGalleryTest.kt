package com.palmnote.ui.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 相册载荷（§3.7(2) 零迁移升级）的三形态兼容与往返：
 * 旧单图裸路径、字符串数组、新对象数组都要能读；序列化统一成对象数组。
 */
class LifePhotoGalleryTest {

    @Test
    fun `parses legacy single path and legacy string array`() {
        assertEquals(
            listOf(LifePhoto("/data/x/a.jpg")),
            parsePhotos("/data/x/a.jpg")
        )
        assertEquals(
            listOf(LifePhoto("content://gal/1"), LifePhoto("content://gal/2")),
            parsePhotos("""["content://gal/1","content://gal/2"]""")
        )
    }

    @Test
    fun `parses object payload with titles`() {
        val photos = parsePhotos("""[{"p":"content://gal/1","t":"出发"},{"p":"content://gal/2"}]""")
        assertEquals(listOf(LifePhoto("content://gal/1", "出发"), LifePhoto("content://gal/2")), photos)
    }

    @Test
    fun `unparsable raw falls back to a single photo instead of dropping it`() {
        assertEquals(listOf(LifePhoto("content://gal/9")), parsePhotos("content://gal/9"))
        assertTrue(parsePhotos(null).isEmpty())
        assertTrue(parsePhotos("").isEmpty())
    }

    @Test
    fun `serialize then parse round-trips titles and paths`() {
        val photos = listOf(LifePhoto("content://gal/1", "山顶"), LifePhoto("content://gal/2"))
        assertEquals(photos, parsePhotos(serializePhotos(photos)))
    }
}
