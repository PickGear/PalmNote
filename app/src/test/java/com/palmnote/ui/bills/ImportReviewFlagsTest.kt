package com.palmnote.ui.bills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [reviewReasonOf] 的判定与优先级测试。
 * 优先级：缺金额 > 缺日期 > 商户待确认 > 分类未识别；字段齐全返回 null。
 */
class ImportReviewFlagsTest {

    @Test
    fun `missing amount takes priority over everything`() {
        assertEquals(
            ReviewReason.MISSING_AMOUNT,
            reviewReasonOf(amountCents = null, dateMillis = null, merchant = "", category = "其他")
        )
    }

    @Test
    fun `non-positive amount counts as missing`() {
        assertEquals(
            ReviewReason.MISSING_AMOUNT,
            reviewReasonOf(amountCents = 0L, dateMillis = 1L, merchant = "商店", category = "餐饮")
        )
    }

    @Test
    fun `missing date is second priority`() {
        assertEquals(
            ReviewReason.MISSING_DATE,
            reviewReasonOf(amountCents = 100L, dateMillis = null, merchant = "商店", category = "餐饮")
        )
    }

    @Test
    fun `blank merchant is third priority`() {
        assertEquals(
            ReviewReason.BLANK_MERCHANT,
            reviewReasonOf(amountCents = 100L, dateMillis = 1L, merchant = "   ", category = "餐饮")
        )
    }

    @Test
    fun `unresolved category is lowest priority`() {
        assertEquals(
            ReviewReason.UNKNOWN_CATEGORY,
            reviewReasonOf(
                amountCents = 100L, dateMillis = 1L,
                merchant = "商店", category = "其他", categoryResolved = false
            )
        )
    }

    @Test
    fun `explicit other category is resolved`() {
        assertNull(
            reviewReasonOf(
                amountCents = 100L, dateMillis = 1L,
                merchant = "转账", category = "其他", categoryResolved = true
            )
        )
    }

    @Test
    fun `complete fields need no review`() {
        assertNull(
            reviewReasonOf(
                amountCents = 2500L, dateMillis = 1L,
                merchant = "便利店", category = "餐饮", categoryResolved = true
            )
        )
    }

    @Test
    fun `severity flags match contract`() {
        assertEquals(true, ReviewReason.MISSING_AMOUNT.severe)
        assertEquals(true, ReviewReason.MISSING_DATE.severe)
        assertEquals(false, ReviewReason.BLANK_MERCHANT.severe)
        assertEquals(false, ReviewReason.UNKNOWN_CATEGORY.severe)
    }
}
