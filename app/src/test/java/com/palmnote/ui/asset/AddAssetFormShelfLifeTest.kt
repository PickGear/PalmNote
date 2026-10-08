package com.palmnote.ui.asset

import com.palmnote.domain.model.ShelfLifeUnit
import com.palmnote.domain.util.shelfLifeExpiryFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 两种录法之间必须共享同一份到期日。
 *
 * 这条同步存在的原因是一个真实缺陷：填完「保质期限」再切回「到期日」，日期框会是空的，
 * 此刻保存会把保质期整个丢掉——因为保存时按当前录法取值，而日期那份从没被写过。
 */
class AddAssetFormShelfLifeTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millisOf(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `按期限录会把算出的到期日同步进表单`() {
        val produced = millisOf(2026, 5, 10)
        val form = AddAssetFormState(shelfLifeMode = SHELF_LIFE_MODE_PERIOD)
            .withShelfLifePeriod(produced, "12", ShelfLifeUnit.MONTH.value)

        assertEquals("到期日必须跟着期限一起写进去", millisOf(2027, 5, 10), form.shelfLifeExpireDate)
        assertEquals(produced, form.shelfLifeProducedDate)
        assertEquals("12", form.shelfLifeDurationValue)
    }

    @Test
    fun `期限填不全时到期日跟着清空，不留半截`() {
        val produced = millisOf(2026, 5, 10)
        val filled = AddAssetFormState().withShelfLifePeriod(produced, "12", ShelfLifeUnit.MONTH.value)

        assertNull("时长被清掉后，到期日不能还留着旧值", filled.withShelfLifePeriod(produced, "", ShelfLifeUnit.MONTH.value).shelfLifeExpireDate)
        assertNull("缺生产日期也算不出来", AddAssetFormState().withShelfLifePeriod(null, "12", ShelfLifeUnit.MONTH.value).shelfLifeExpireDate)
        assertNull("时长为 0 同样算不出来", AddAssetFormState().withShelfLifePeriod(produced, "0", ShelfLifeUnit.MONTH.value).shelfLifeExpireDate)
    }

    @Test
    fun `到期日录法只落到期日`() {
        val picked = millisOf(2027, 1, 1)
        val resolved = AddAssetFormState(shelfLifeExpireDate = picked).resolveShelfLife()

        assertEquals(picked, resolved.expireDate)
        assertNull(resolved.producedDate)
        assertNull(resolved.durationValue)
        assertNull(resolved.durationUnit)
    }

    @Test
    fun `按期限录填全了落到期日与那组输入`() {
        val produced = millisOf(2026, 5, 10)
        val resolved = AddAssetFormState(shelfLifeMode = SHELF_LIFE_MODE_PERIOD)
            .withShelfLifePeriod(produced, "12", ShelfLifeUnit.MONTH.value)
            .resolveShelfLife()

        assertEquals(millisOf(2027, 5, 10), resolved.expireDate)
        assertEquals(produced, resolved.producedDate)
        assertEquals(12, resolved.durationValue)
        assertEquals(ShelfLifeUnit.MONTH.value, resolved.durationUnit)
    }

    @Test
    fun `按期限录填不全时回退到已填的到期日，不丢`() {
        val picked = millisOf(2027, 1, 1)
        // 场景：用户在「到期日」里选过日期，之后切到「保质期限」但没填完就保存
        val resolved = AddAssetFormState(
            shelfLifeMode = SHELF_LIFE_MODE_PERIOD,
            shelfLifeExpireDate = picked,
            shelfLifeProducedDate = millisOf(2026, 5, 10)
        ).resolveShelfLife()

        assertEquals("期限没填全时，不能把用户填过的到期日丢掉", picked, resolved.expireDate)
        assertNull("半截的期限输入不许落库", resolved.producedDate)
        assertNull(resolved.durationValue)
        assertNull(resolved.durationUnit)
    }

    /**
     * 四列里「到期日」与「生产日期 + 时长」是同一个事实的两份表示，**不许各说各话**。
     * 这条不变量正是当初没把它们合并成一列的原因：既然两份都存，就得有测试钉住它们一致，
     * 否则哪天有人在别处直接构造 Asset（导入手改的 CSV 之类），界面按到期日说一套、
     * 详情卡按生产日期说另一套。
     */
    @Test
    fun `按期限录时，到期日恰好等于那组输入的推算结果`() {
        val produced = millisOf(2026, 5, 10)
        val resolved = AddAssetFormState(shelfLifeMode = SHELF_LIFE_MODE_PERIOD)
            .withShelfLifePeriod(produced, "18", ShelfLifeUnit.MONTH.value)
            .resolveShelfLife()

        val unit = requireNotNull(ShelfLifeUnit.fromOrNull(resolved.durationUnit)) { "按期限录必须把单位一起落下" }
        assertEquals(produced, resolved.producedDate)
        assertEquals(18, resolved.durationValue)
        assertEquals(
            "到期日必须恰好是那组输入的推算结果",
            shelfLifeExpiryFrom(produced, 18, unit),
            resolved.expireDate
        )
    }

    /**
     * 「直接选到期日」这条路径曾经用 `withShelfLifePeriod(null, "", …)` 来清期限输入，
     * 而那个函数会**重算**到期日 → 把刚选的日期当场抹成 null，表现就是"日期选不上"。
     * 下面两条钉住它：选完必须留得住，且保存时落的就是那个日期。
     */
    @Test
    fun `直接选到期日时日期留得住，只清掉那组期限输入`() {
        val picked = millisOf(2027, 1, 1)
        val form = AddAssetFormState(shelfLifeMode = SHELF_LIFE_MODE_PERIOD)
            .withShelfLifePeriod(millisOf(2026, 5, 10), "12", ShelfLifeUnit.MONTH.value)
            .withAbsoluteShelfLife(picked)

        assertEquals("刚选的日期不能被清掉", picked, form.shelfLifeExpireDate)
        assertNull("期限那组输入要清掉，免得切回去又把它顶回来", form.shelfLifeProducedDate)
        assertEquals("", form.shelfLifeDurationValue)
        assertEquals(SHELF_LIFE_DURATION_DEFAULT_UNIT, form.shelfLifeDurationUnit)
    }

    @Test
    fun `直接选到期日后保存，落库的就是那个日期`() {
        val picked = millisOf(2027, 1, 1)
        val resolved = AddAssetFormState(shelfLifeMode = SHELF_LIFE_MODE_DATE)
            .withAbsoluteShelfLife(picked)
            .resolveShelfLife()

        assertEquals(picked, resolved.expireDate)
        assertNull(resolved.producedDate)
        assertNull(resolved.durationValue)
        assertNull(resolved.durationUnit)
    }
}
