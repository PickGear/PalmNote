package com.palmnote.data.ocr

import com.palmnote.domain.model.BillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillOcrParserTest {

    private val parser = BillOcrParser()

    @Test
    fun `synthetic wechat receipt extracts amount merchant date note category`() {
        val text = """
            微信支付凭证
            交易时间：2025-08-03 12:34:56
            商户：老王面馆
            商品：牛肉面
            支付方式：零钱
            金额：¥23.50
            支付成功
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(2350L, result.amount)
        assertEquals("老王面馆", result.merchant)
        assertEquals("牛肉面", result.note)
        assertEquals("餐饮", result.category)
        assertNotNull(result.date)
        assertSameLocalDate(2025, 8, 3, result.date ?: error("missing date"))
    }

    @Test
    fun `alipay style receipt with 年月日 formats`() {
        val text = """
            支付宝
            2026年1月15日 09:08:00
            收款方：早餐店
            金额：¥52.00
            交易完成
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(5200L, result.amount)
        assertEquals("早餐店", result.merchant)
        assertSameLocalDate(2026, 1, 15, result.date ?: error("missing date"))
    }

    @Test
    fun `no amount returns null amount with defaults`() {
        val result = parser.parse("无金额样张")
        assertNull(result.amount)
        assertEquals("其他", result.category)
    }

    @Test
    fun `parseMultiple splits receipts at separator`() {
        val text = """
            商户：店A
            金额：¥10.00
            ---
            商户：店B
            金额：¥20.00
        """.trimIndent()

        val results = parser.parseMultiple(text)

        assertEquals(2, results.size)
        assertEquals(1000L, results[0].amount)
        assertEquals(2000L, results[1].amount)
        assertEquals("店A", results[0].merchant)
        assertEquals("店B", results[1].merchant)
    }

    @Test
    fun `parseMultiple returns single result when only one receipt`() {
        val text = """
            商户：便利店
            金额：¥8.50
        """.trimIndent()

        val results = parser.parseMultiple(text)
        assertEquals(1, results.size)
        assertEquals(850L, results[0].amount)
    }

    @Test
    fun `empty text returns empty default result`() {
        val result = parser.parse("")
        assertNull(result.amount)
        assertEquals("", result.merchant)
    }

    @Test
    fun `takes largest amount when a total line exists`() {
        val text = """
            商户：超市
            小计 ¥18.00
            实付 ¥18.00
        """.trimIndent()
        assertEquals(1800L, parser.parse(text).amount)
    }

    // ── 电商订单页实测样张（抖音/拼多多/淘宝） ──────────────────────────

    @Test
    fun `pdd detail takes paid amount not product original price`() {
        // 拼多多详情页：商品原价 ¥6.25，实付 ¥4.16，页面下方还有推荐商品价格
        val text = """
            百亿补贴·超霸 品牌
            GP超霸 CR2450 纽扣电池汽车钥匙遥控器电池
            ¥6.25
            CR2450*2粒,+1个收纳盒
            7天保价 极速发货
            补贴后共优惠¥2.09 实付款:¥4.16(免运费)
            订单号：260827-604189509162912
            消费提醒
            券后¥3.94
            ¥2.87 已拼13.6万+件
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(416L, result.amount)
        assertEquals(BillType.EXPENSE, result.type) // 付款金额优先于页面装饰词，判为支出
    }

    @Test
    fun `douyin order list splits three orders by paid line`() {
        // 抖音订单列表：无日期、无分隔线，仅靠“实付款”行切分
        val text = """
            黄庄酥皮月饼河北总店 直播
            【正宗黄庄酥皮月饼10个】蛋黄酥
            ¥9.80
            含运费险服务 实付款 ¥2.80
            商家 更多 申请售后
            良品壹面官方旗舰店 抖音旗舰
            【多拼组合】泡面先生定制14包
            ¥19.90
            含运费险服务 实付款 ¥19.90
            心相印洗护清洁旗舰店 抖音旗舰
            【3件划算】心相印茶语丝抽纸
            ¥3.00
            含运费险服务 实付款 ¥0.01
        """.trimIndent()

        val results = parser.parseMultiple(text)

        assertEquals(3, results.size)
        assertEquals(280L, results[0].amount)
        assertEquals(1990L, results[1].amount)
        assertEquals(1L, results[2].amount)
        assertTrue(results[0].merchant.contains("黄庄"))
        assertTrue(results[0].type == BillType.EXPENSE)
    }

    @Test
    fun `taobao detail picks paid over discount amount`() {
        // 淘宝详情页：“实付款 共减¥3”的 ¥3 是优惠，真实付款 ¥5.9 在下一行
        val text = """
            趣评测商城
            【清仓特价】趣评测/适用于小米手机壳
            ¥8.9
            实付价 ¥5.9 价格明细
            闲鱼转卖 加入购物车
            实付款 共减¥3
            ¥5.9
            订单信息 共7项 5127392990007007926
        """.trimIndent()

        val results = parser.parseMultiple(text)

        assertTrue(results.isNotEmpty())
        assertEquals(590L, results[0].amount)
    }

    @Test
    fun `taobao order list extracts dates from MMdd format`() {
        // 淘宝订单列表：“09.01 实付款 ¥11.84”，无年份日期应补当前年
        val text = """
            淘宝 彼岸有品
            膜栗 适用小米17Promax钢化膜
            ¥11.84
            09.01 实付款 ¥11.84
            淘宝 煤油之家
            煤油之家适用于小米13/14/15/17钢化膜
            ¥14.1
            09.01 应付款 ¥14.1
        """.trimIndent()

        val results = parser.parseMultiple(text)

        assertTrue(results.any { it.amount == 1184L })
        val dated = results.first { it.amount == 1184L }
        assertSameLocalDate(
            java.time.LocalDate.now().year, 9, 1,
            dated.date ?: error("missing date")
        )
    }

    @Test
    fun `pdd order list splits orders and keeps paid amounts`() {
        val text = """
            裕津厨电官方旗舰店
            不锈钢淋浴花洒套装软管防爆淋浴
            ¥6.49
            已签收 极免速递
            百亿补贴·超霸 品牌
            GP超霸 CR2450 纽扣电池
            ¥6.25
            实付款:¥4.16(免运费)
            百亿补贴·海氏海诺 品牌
            海氏海诺婴童棉签双头
            ¥4.36
            实付款:¥3.93(免运费)
        """.trimIndent()

        val results = parser.parseMultiple(text)

        assertTrue(results.any { it.amount == 416L })
        assertTrue(results.any { it.amount == 393L })
        assertTrue(results.none { it.amount == 625L })
    }

    @Test
    fun `month day without year fills current year`() {
        val text = """
            订单保障
            凭据：8月30日下单交易快照
            实付款 ¥5.9
        """.trimIndent()

        val result = parser.parse(text)
        assertSameLocalDate(
            java.time.LocalDate.now().year, 8, 30,
            result.date ?: error("missing date")
        )
    }

    @Test
    fun `merchant keyword ignores order number lines`() {
        val text = """
            商户单号 4200012345678
            实付款 ¥10.00
        """.trimIndent()

        val result = parser.parse(text)
        assertFalse(result.merchant.contains("单号"))
    }

    private fun assertSameLocalDate(year: Int, month: Int, day: Int, epochMillis: Long) {
        val expected = java.time.LocalDate.of(year, month, day).toEpochDay()
        val actual = java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        assertEquals(expected, actual)
    }
}
