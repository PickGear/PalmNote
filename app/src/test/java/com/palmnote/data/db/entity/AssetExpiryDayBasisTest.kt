package com.palmnote.data.db.entity

import com.palmnote.domain.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 「还在有效期吗」与「剩余 N 天」必须是**同一个口径**。
 *
 * 原来的实现拿时间戳比大小（`expire > now`），而日期列存的是当天 00:00 —— 到期当天 00:00 起
 * 就已经"过期"了，可同一张卡右侧的 chip 按自然日算又显示绿色「剩余 0 天」。
 * 一张卡上一个红盾一个绿字，用户没法知道该信哪个。
 */
class AssetExpiryDayBasisTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun day(offset: Long): Long =
        LocalDate.now(zone).plusDays(offset).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `到期当天仍然有效——那天是最后一天`() {
        val asset = Asset(name = "相机", category = "数码", warrantyExpireDate = day(0))
        assertTrue("到期当天不该判成已过保", asset.isWarrantyValid)
    }

    @Test
    fun `昨天就过期了`() {
        assertFalse(Asset(name = "相机", category = "数码", warrantyExpireDate = day(-1)).isWarrantyValid)
    }

    @Test
    fun `明天还有效`() {
        assertTrue(Asset(name = "相机", category = "数码", warrantyExpireDate = day(1)).isWarrantyValid)
    }

    @Test
    fun `没填日期时不算有效`() {
        assertFalse(Asset(name = "相机", category = "数码").isWarrantyValid)
    }

    @Test
    fun `保险与质保同一口径`() {
        assertTrue(Asset(name = "相机", category = "数码", insuranceExpireDate = day(0)).isInsuranceValid)
        assertFalse(Asset(name = "相机", category = "数码", insuranceExpireDate = day(-1)).isInsuranceValid)
        assertFalse(Asset(name = "相机", category = "数码").isInsuranceValid)
    }

    @Test
    fun `判定与界面上算剩余天数的口径一致`() {
        for (offset in -3L..3L) {
            val asset = Asset(name = "相机", category = "数码", warrantyExpireDate = day(offset))
            val daysLeft = DateUtils.getDaysUntil(asset.warrantyExpireDate!!)
            assertEquals(
                "第 $offset 天：isWarrantyValid 与 getDaysUntil 的口径必须一致",
                daysLeft >= 0,
                asset.isWarrantyValid
            )
        }
    }
}
