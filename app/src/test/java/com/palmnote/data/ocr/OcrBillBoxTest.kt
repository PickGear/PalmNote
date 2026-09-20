package com.palmnote.data.ocr

import android.app.Application
import android.graphics.PointF
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 批13 Part 1：OCR 坐标透传的数据层保真测试。
 *
 * 验证 [OcrLine.box] 的向后兼容默认值，以及 [BillOcrParser] 为每笔账单计算
 * [OcrBillResult.cropBox]（该笔所有识别行文本框的并集）的三条硬约束：
 * 1. 旧 `OcrLine(text, confidence)` 构造仍可用，`box` 默认为 null；
 * 2. 给定若干已知 box 时，并集外接矩形计算正确（像素坐标）；
 * 3. 任一行缺少 box 时 `cropBox == null`（UI 降级，不许崩）。
 *
 * 因涉 `android.graphics.PointF` / `RectF`，用 Robolectric 提供真实实现。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class OcrBillBoxTest {

    private val parser = BillOcrParser()

    /** 用左上/右下两点造一个 4 顶点文本框（与 ppocr OCRBox 一致的顺时针四角） */
    private fun box(left: Float, top: Float, right: Float, bottom: Float): List<PointF> =
        listOf(PointF(left, top), PointF(right, top), PointF(right, bottom), PointF(left, bottom))

    @Test
    fun `legacy two-arg OcrLine constructor still works and box defaults to null`() {
        val legacy = OcrLine("商户：店A", 0.9f)
        assertEquals("商户：店A", legacy.text)
        assertEquals(0.9f, legacy.confidence)
        assertNull(legacy.box)

        val legacyNullConfidence = OcrLine("支付成功", null)
        assertNull(legacyNullConfidence.confidence)
        assertNull(legacyNullConfidence.box)

        val withBox = OcrLine("金额", null, box(0f, 0f, 1f, 1f))
        assertEquals(4, withBox.box?.size)
    }

    @Test
    fun `union of three known boxes is computed correctly on single-block path`() {
        val lines = listOf(
            OcrLine("商户：店A", 0.9f, box(10f, 20f, 110f, 40f)),
            OcrLine("金额：¥10.00", 0.9f, box(0f, 0f, 50f, 15f)),
            OcrLine("支付成功", 0.9f, box(100f, 50f, 200f, 90f))
        )

        val result = parser.parseMultiple(lines).single()

        assertEquals(1000L, result.amount)
        // left=min(10,0,100)=0, top=min(20,0,50)=0, right=max(110,50,200)=200, bottom=max(40,15,90)=90
        assertEquals(RectF(0f, 0f, 200f, 90f), result.cropBox)
    }

    @Test
    fun `no box anywhere yields null cropBox without crashing`() {
        val lines = listOf(
            OcrLine("商户：店A", null),
            OcrLine("金额：¥10.00", null),
            OcrLine("支付成功", null)
        )

        val result = parser.parseMultiple(lines).single()

        assertEquals(1000L, result.amount)
        assertNull(result.cropBox)
    }

    @Test
    fun `any single line missing box degrades whole cropBox to null`() {
        val lines = listOf(
            OcrLine("商户：店A", 0.9f, box(10f, 20f, 110f, 40f)),
            OcrLine("金额：¥10.00", 0.9f), // 该行无 box
            OcrLine("支付成功", 0.9f, box(100f, 50f, 200f, 90f))
        )

        val result = parser.parseMultiple(lines).single()

        assertEquals(1000L, result.amount)
        assertNull(result.cropBox)
    }

    @Test
    fun `multi-block keeps per-bill cropBox and does not leak whole-image union`() {
        val lines = listOf(
            OcrLine("商户：店A", 0.9f, box(10f, 10f, 50f, 30f)),
            OcrLine("金额：¥10.00", 0.9f, box(10f, 40f, 80f, 60f)),
            OcrLine("---", 0.9f, box(0f, 70f, 30f, 80f)),
            OcrLine("商户：店B", 0.9f, box(100f, 10f, 140f, 30f)),
            OcrLine("金额：¥20.00", 0.9f, box(100f, 40f, 170f, 60f))
        )

        val results = parser.parseMultiple(lines)

        assertEquals(2, results.size)
        assertEquals(1000L, results[0].amount)
        assertEquals(2000L, results[1].amount)
        // 第一笔只用了 店A + 金额10 两行 → 并集 (10,10)-(80,60)
        assertEquals(RectF(10f, 10f, 80f, 60f), results[0].cropBox)
        // 第二笔用了 --- + 店B + 金额20 → 并集 (0,10)-(170,80)
        assertEquals(RectF(0f, 10f, 170f, 80f), results[1].cropBox)
        // 两笔各自的裁剪框独立，绝不能都退化成整图并集
        assertNotEquals(results[0].cropBox, results[1].cropBox)
    }

    @Test
    fun `string adapter path leaves cropBox null because no coordinates exist`() {
        val result = parser.parse("商户：便利店\n金额：¥8.50")

        assertEquals(850L, result.amount)
        assertNull(result.cropBox)
    }
}
