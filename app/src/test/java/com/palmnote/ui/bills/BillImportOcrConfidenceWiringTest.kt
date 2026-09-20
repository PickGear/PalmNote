package com.palmnote.ui.bills

import com.palmnote.data.ocr.FieldConfidence
import com.palmnote.data.ocr.OcrBillResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * OCR 置信度 → 复核标记的接线契约：
 * - 分类置信度 INFERRED（规则推断）= 已识别；MISSING = 未识别，需人工复核；
 * - 金额/商户/日期缺失（对应置信度 MISSING）直接命中 [ImportReviewFlags] 的复核原因。
 */
class BillImportOcrConfidenceWiringTest {

    /** 与 UI 侧一致的约定：分类只有规则推断（INFERRED）算已识别。 */
    private fun categoryResolvedOf(result: OcrBillResult): Boolean =
        result.categoryConfidence == FieldConfidence.INFERRED

    @Test
    fun `inferred category is resolved and needs no review`() {
        val r = OcrBillResult(
            amount = 2500L, merchant = "便利店", date = 1L,
            category = "餐饮", categoryConfidence = FieldConfidence.INFERRED
        )
        assertNull(reviewReasonOf(r.amount, r.date, r.merchant, r.category, categoryResolvedOf(r)))
    }

    @Test
    fun `missing category confidence flags unknown category`() {
        val r = OcrBillResult(
            amount = 2500L, merchant = "便利店", date = 1L,
            category = "其他", categoryConfidence = FieldConfidence.MISSING
        )
        assertEquals(
            ReviewReason.UNKNOWN_CATEGORY,
            reviewReasonOf(r.amount, r.date, r.merchant, r.category, categoryResolvedOf(r))
        )
    }

    @Test
    fun `missing amount confidence with null amount flags missing amount`() {
        val r = OcrBillResult(
            amount = null, merchant = "便利店", date = 1L,
            amountConfidence = FieldConfidence.MISSING
        )
        assertEquals(
            ReviewReason.MISSING_AMOUNT,
            reviewReasonOf(r.amount, r.date, r.merchant, r.category)
        )
    }

    @Test
    fun `missing merchant confidence with blank merchant flags merchant`() {
        val r = OcrBillResult(
            amount = 2500L, merchant = "", date = 1L,
            merchantConfidence = FieldConfidence.MISSING
        )
        assertEquals(
            ReviewReason.BLANK_MERCHANT,
            reviewReasonOf(r.amount, r.date, r.merchant, r.category)
        )
    }
}
