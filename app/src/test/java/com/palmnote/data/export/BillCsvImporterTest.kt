package com.palmnote.data.export

import com.palmnote.data.export.BillCsvImporter.CsvFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class BillCsvImporterTest {

    private val importer = BillCsvImporter()

    // ── detectFormat ──

    @Test
    fun `detectFormat wechat header returns WECHAT`() {
        val lines = listOf("微信支付账单明细", "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态")
        assertEquals(CsvFormat.WECHAT, importer.detectFormat(lines))
    }

    @Test
    fun `detectFormat alipay header returns ALIPAY`() {
        val lines = listOf("收支明细", "交易时间,交易分类,收/支,商品说明,金额(元)")
        assertEquals(CsvFormat.ALIPAY, importer.detectFormat(lines))
    }

    @Test
    fun `detectFormat unknown returns UNKNOWN`() {
        assertEquals(CsvFormat.UNKNOWN, importer.detectFormat(listOf("a,b,c")))
    }

    @Test
    fun `detectFormat strips BOM before matching`() {
        val lines = listOf("\uFEFF交易时间,交易类型,交易对方,商品,收/支,金额(元)")
        assertEquals(CsvFormat.WECHAT, importer.detectFormat(lines))
    }

    // ── WeChat ──

    @Test
    fun `wechat expense row parses all fields`() {
        val header = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注"
        val row = "2026-07-20 12:30:00,商户消费,美团外卖,,支出,45.00,微信支付,已支付,420000123456,310000,午餐"
        val bills = importer.parseFromLines(listOf(header, row), CsvFormat.WECHAT)

        assertEquals(1, bills.size)
        val bill = bills[0]
        assertEquals("2026-07-20 12:30:00".ts(), bill.date)
        assertEquals("EXPENSE", bill.type)
        assertEquals(4500L, bill.amount)
        assertEquals("餐饮", bill.category)
        assertEquals("美团外卖", bill.merchant)
        assertEquals("午餐", bill.note)
        assertEquals("WECHAT", bill.paymentMethod)
        assertEquals("420000123456", bill.transactionId)
    }

    @Test
    fun `wechat income row parses as INCOME`() {
        val header = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态"
        val row = "2026-07-21 09:00:00,转账,老板,,收入,5000.00,零钱,已到账"
        val bills = importer.parseFromLines(listOf(header, row), CsvFormat.WECHAT)

        assertEquals(1, bills.size)
        assertEquals("INCOME", bills[0].type)
        assertEquals(500000L, bills[0].amount)
        assertEquals("CASH", bills[0].paymentMethod)
    }

    @Test
    fun `wechat skips non-paid statuses`() {
        val header = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态"
        val unpaid = "2026-07-22 10:00:00,商户消费,咖啡店,,支出,30.00,零钱,对方已退款"
        val bills = importer.parseFromLines(listOf(header, unpaid, "合计: 1笔"), CsvFormat.WECHAT)
        assertEquals(0, bills.size)
    }

    @Test
    fun `wechat skips 合计 and blank lines`() {
        val header = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态"
        val row = "2026-07-20 12:30:00,商户消费,便利店,日用品,支出,8.50,微信支付,已支付"
        val bills = importer.parseFromLines(
            listOf(header, "", "合计", "----", row, "合计: 5笔共8.50"),
            CsvFormat.WECHAT
        )
        assertEquals(1, bills.size)
    }

    @Test
    fun `wechat strips thousands separators and currency symbol`() {
        val header = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态"
        val row = "2026-07-23 18:00:00,商户消费,京东,数码产品,支出,\"¥1,234.56\",JD支付,已支付"
        val bills = importer.parseFromLines(listOf(header, row), CsvFormat.WECHAT)
        assertEquals(1, bills.size)
        assertEquals(123456L, bills[0].amount)
    }

    @Test
    fun `wechat handles negative amount sign as expense`() {
        val header = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态"
        val row = "2026-07-25 08:10:00,商户消费,滴滴,打车,支出,-18.0,微信支付,已支付"
        val bills = importer.parseFromLines(listOf(header, row), CsvFormat.WECHAT)
        assertEquals(1, bills.size)
        assertEquals(1800L, bills[0].amount)
        assertEquals("EXPENSE", bills[0].type)
    }

    @Test
    fun `wechat row with quoted comma in merchant is parsed`() {
        val header = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,备注"
        val row = "\"2026-07-01 08:00:00\",商户消费,\"超市,便利店\",,支出,12.30,微信支付,已支付,\"早餐,面包\""
        val bills = importer.parseFromLines(listOf(header, row), CsvFormat.WECHAT)
        assertEquals(1, bills.size)
        assertEquals("超市,便利店", bills[0].merchant)
        assertEquals("早餐,面包", bills[0].note)
    }

    @Test
    fun `wechat tab separated file is supported`() {
        val header = "交易时间\t交易类型\t交易对方\t收/支\t金额(元)\t当前状态"
        val row = "2026-07-26 11:00:00\t商户消费\t地铁\t支出\t5.00\t已支付"
        val bills = importer.parseFromLines(listOf(header, row), CsvFormat.WECHAT)
        assertEquals(1, bills.size)
        assertEquals(500L, bills[0].amount)
    }

    @Test
    fun `wechat semicolon separated file is supported`() {
        val header = "交易时间;交易类型;交易对方;收/支;金额(元);当前状态"
        val row = "2026-07-26 12:00:00;商户消费;测试;支出;8.00;已支付"
        val bills = importer.parseFromLines(listOf(header, row), CsvFormat.WECHAT)
        assertEquals(1, bills.size)
        assertEquals(800L, bills[0].amount)
    }

    // ── Alipay ──

    @Test
    fun `alipay row parses all fields`() {
        val header = "记录时间,交易分类,商品说明,收/支,金额,备注,账户"
        val row = "2026-07-21 20:15:00,餐饮,午餐,支出,25.00,工作餐,余额宝"
        val bills = importer.parseFromLines(listOf(header, row), CsvFormat.ALIPAY)

        assertEquals(1, bills.size)
        val bill = bills[0]
        assertEquals("2026-07-21 20:15:00".ts(), bill.date)
        assertEquals("EXPENSE", bill.type)
        assertEquals(2500L, bill.amount)
        assertEquals("餐饮", bill.category)
        assertEquals("午餐", bill.merchant)
        assertEquals("工作餐", bill.note)
        assertEquals("ALIPAY", bill.paymentMethod)
    }

    @Test
    fun `alipay income row parses as INCOME`() {
        val header = "记录时间,交易分类,商品说明,收/支,金额,账户"
        val row = "2026-07-22 10:30:00,工资,工资,收入,8000.00,招商银行储蓄卡(1234)"
        val bills = importer.parseFromLines(listOf(header, row), CsvFormat.ALIPAY)
        assertEquals(1, bills.size)
        assertEquals("INCOME", bills[0].type)
        assertEquals(800000L, bills[0].amount)
    }

    @Test
    fun `alipay unknown category falls back to guess category`() {
        val header = "记录时间,商品说明,收/支,金额"
        val row = "2026-07-24 13:40:00,美团外卖,支出,32.50"
        val bills = importer.parseFromLines(listOf(header, row), CsvFormat.ALIPAY)
        assertEquals(1, bills.size)
        assertEquals("餐饮", bills[0].category)
    }

    @Test
    fun `alipay amount with minus sign parses as positive cents`() {
        val header = "记录时间,商品说明,收/支,金额"
        val row = "2026-07-25 18:30:00,超市,支出,-128.50"
        val bills = importer.parseFromLines(listOf(header, row), CsvFormat.ALIPAY)
        assertEquals(1, bills.size)
        assertEquals(12850L, bills[0].amount)
    }

    // ── 通用行为 ──

    @Test
    fun `empty file returns empty list`() {
        assertEquals(0, importer.parseFromLines(emptyList(), CsvFormat.UNKNOWN).size)
    }

    @Test
    fun `unmatched header returns empty list`() {
        val bills = importer.parseFromLines(listOf("a,b,c", "1,2,3"), CsvFormat.WECHAT)
        assertEquals(0, bills.size)
    }
}

private fun String.ts(): Long {
    val p = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return p.parse(this)?.time ?: throw AssertionError("bad ts")
}
