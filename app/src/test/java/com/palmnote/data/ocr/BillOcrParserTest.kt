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
    fun `note never picks standalone time lines`() {
        // 截图角落/状态栏时间行曾被选为备注（不含金额符号、不匹配日期模式、无"时间"字样）
        val text = """
            微信支付
            22:47
            商户：瑞幸咖啡
            金额：¥9.90
            支付成功
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(990L, result.amount)
        // 全部候选行都是装饰词/标签行/时间行，备注应为空而不是误取 "22:47"
        assertEquals("", result.note)
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
            订单号：260827-000000000000001
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
            订单信息 共7项 0000000000000000001
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

    @Test
    fun `wechat bill list page splits rows by bare amount lines`() {
        // 微信账单列表页：金额无¥前缀独立成行，行序为 商户→日期时间→金额
        val text = """
            全部账单
            2026年9月
            支出¥156.45 收入¥2.40
            京东
            9月8日 17:23
            -10.14
            京东
            9月7日 11:03
            -14.29
            朴朴超市-退款
            9月6日 11:31
            +0.23
            商家转账-来自拼多多
            9月6日 11:11
            +0.88
            朴朴
            9月6日 10:53
            -32.22
            已退款(¥0.23)
            京东
            9月5日 20:00
            -9.40
            扫二维码付款-给张枳生
            9月5日 12:48
            -3.00
            从3*****04的QQ钱包转到...
            9月5日 12:47
            11.64
        """.trimIndent()

        val results = parser.parseMultiple(text)

        assertEquals(8, results.size)
        assertEquals(1014L, results[0].amount)
        assertEquals("京东", results[0].merchant)
        assertEquals(BillType.EXPENSE, results[0].type)
        assertSameLocalDate(2026, 9, 8, results[0].date ?: error("missing date"))
        assertEquals(1429L, results[1].amount)
        assertEquals(BillType.EXPENSE, results[1].type)
        assertSameLocalDate(2026, 9, 7, results[1].date ?: error("missing date"))
        // 退款 +0.23：行首 + 符号判收入
        assertEquals(23L, results[2].amount)
        assertEquals(BillType.INCOME, results[2].type)
        assertTrue(results[2].merchant.contains("朴朴"))
        // 朴朴 -32.22：金额取 32.22、类型为支出（行首符号优先于"已退款"关键词）
        assertEquals(88L, results[3].amount)
        assertEquals(BillType.INCOME, results[3].type)
        assertEquals(3222L, results[4].amount)
        assertEquals(BillType.EXPENSE, results[4].type)
        assertEquals(940L, results[5].amount)
        assertEquals("京东", results[5].merchant)
        assertEquals(300L, results[6].amount)
        // QQ 钱包转入 11.64：无符号无关键词，类型回退默认由用户确认
        assertEquals(1164L, results[7].amount)
    }

    @Test
    fun `alipay bookkeeping day groups give dates to entries`() {
        // 支付宝"记账本"小程序：按日分组，组头带"支x 收x"汇总，条目行只有时间无日期
        val text = """
            记账本
            总账本
            9月总支出 ¥53.74 总收入 ¥0.37
            9月9日 星期三 支0.00 收0.14
            投资理财
            11:24  余额宝-自动转入
            0.12
            转账
            +0.12
            11:24  淘宝签到提现-淘宝（中国）软件有...
            投资理财
            +0.02
            02:10  余额宝-收益发放
            9月8日 星期二 支0.00 收0.02
            投资理财
            +0.02
            02:43  余额宝-收益发放
        """.trimIndent()

        val results = parser.parseMultiple(text)

        // 汇总行不产生记录；9月9日两条 + 9月8日一条（02:10 的收益发放并入下一分组头前的块）
        assertTrue(results.size >= 3)
        assertEquals(12L, results[0].amount)
        assertSameLocalDate(2026, 9, 9, results[0].date ?: error("missing date"))
        // 行首 + 符号判收入
        assertEquals(BillType.INCOME, results[1].type)
        assertEquals(12L, results[1].amount)
        assertSameLocalDate(2026, 9, 9, results[1].date ?: error("missing date"))
        // 9月8日组内条目从组头拿到日期
        val dayOfMonth8 = results.any {
            it.amount == 2L && it.date != null &&
                java.time.Instant.ofEpochMilli(it.date).atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().dayOfMonth == 8
        }
        assertTrue(dayOfMonth8)
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
