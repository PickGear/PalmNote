package com.palmnote.data.export

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.PaymentMethod
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 流水导出（[BillFlowExporter]）口径守卫：13 列顺序、升序、排除演示账单、
 * 转账行与报销四态、金额恒正、CSV 转义、UTF-8 BOM。
 *
 * 这些是「导出即对账凭据」的硬要求，改列顺序或漏过滤都会让用户拿去 Excel 的表格失真。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class BillFlowExporterTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val exporter = BillFlowExporter(context)

    private val wallets = listOf(
        Wallet(id = 1, name = "微信"),
        Wallet(id = 2, name = "招商银行")
    )
    private val books = listOf(AccountBook(id = 1, name = "日常"))

    /** 测试夹具：每个参数对应一列，故意全开放，便于单测只覆盖关心的字段。 */
    @Suppress("LongParameterList")
    private fun bill(
        id: Long = 0,
        amount: Long = 4500,
        type: BillType = BillType.EXPENSE,
        date: Long = 1_762_000_000_000,
        category: String = "餐饮",
        subCategory: String = "午餐",
        merchant: String = "美团",
        note: String = "",
        walletId: Long? = 1,
        toWalletId: Long? = null,
        accountBookId: Long = 1,
        paymentMethod: PaymentMethod = PaymentMethod.WECHAT,
        transactionId: String = "TX-1",
        isReimbursable: Boolean = false,
        reimbursedAmount: Long = 0,
        isDemo: Boolean = false
    ) = Bill(
        id = id,
        amount = amount,
        type = type,
        category = category,
        subCategory = subCategory,
        note = note,
        date = date,
        yearMonth = "2025-10",
        accountBookId = accountBookId,
        walletId = walletId,
        toWalletId = toWalletId,
        paymentMethod = paymentMethod,
        merchant = merchant,
        transactionId = transactionId,
        isReimbursable = isReimbursable,
        reimbursedAmount = reimbursedAmount,
        isDemo = isDemo
    )

    private fun export(bills: List<Bill>): String {
        val out = ByteArrayOutputStream()
        exporter.export(bills, out, wallets, books)
        return out.toString("UTF-8")
    }

    private fun rows(csv: String): List<List<String>> =
        csv.removePrefix("\uFEFF")
            .trimEnd('\n')
            .lines()
            .map { splitCsv(it) }

    /** 与 exporter 的转义对称的最小解析：处理双引号包裹与引号双写。 */
    private fun splitCsv(line: String): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { cells += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        cells += sb.toString()
        return cells
    }

    @Test
    fun `writes BOM and 13 column header`() {
        val csv = export(emptyList())
        assertTrue("导出必须带 UTF-8 BOM，否则 Windows Excel 双击乱码", csv.startsWith("\uFEFF"))
        val header = rows(csv).single()
        assertEquals(13, header.size)
        assertEquals(
            listOf("交易时间", "收支", "金额", "账本", "钱包", "转入钱包", "分类", "子分类", "商户", "备注", "支付方式", "交易单号", "报销"),
            header
        )
    }

    @Test
    fun `rows are sorted ascending by date`() {
        val csv = export(
            listOf(
                bill(date = 1_762_000_300_000, transactionId = "C"),
                bill(date = 1_762_000_100_000, transactionId = "A"),
                bill(date = 1_762_000_200_000, transactionId = "B")
            )
        )
        assertEquals(listOf("A", "B", "C"), rows(csv).drop(1).map { it[11] })
    }

    @Test
    fun `demo bills are excluded`() {
        val csv = export(listOf(bill(transactionId = "REAL"), bill(transactionId = "DEMO", isDemo = true)))
        assertEquals(listOf("REAL"), rows(csv).drop(1).map { it[11] })
    }

    @Test
    fun `amount is always positive with two decimals`() {
        val csv = export(listOf(bill(amount = -4550, type = BillType.EXPENSE, transactionId = "NEG")))
        val row = rows(csv)[1]
        assertEquals("45.50", row[2])
        assertEquals("支出", row[1])
    }

    @Test
    fun `transfer row carries target wallet while expense leaves it blank`() {
        val csv = export(
            listOf(
                bill(type = BillType.TRANSFER, walletId = 1, toWalletId = 2, transactionId = "TR"),
                bill(type = BillType.EXPENSE, walletId = 2, toWalletId = null, transactionId = "EX")
            )
        )
        val (transfer, expense) = rows(csv).drop(1)
        assertEquals("转账", transfer[1])
        assertEquals("微信", transfer[4])
        assertEquals("招商银行", transfer[5])
        assertEquals("支出", expense[1])
        assertEquals("", expense[5])
    }

    @Test
    fun `wallet and book names fall back to empty when id is unknown`() {
        val csv = export(listOf(bill(walletId = 99, accountBookId = 99)))
        val row = rows(csv)[1]
        assertEquals("", row[3])
        assertEquals("", row[4])
    }

    @Test
    fun `reimbursement column covers all four states`() {
        val csv = export(
            listOf(
                bill(transactionId = "NONE"),
                bill(transactionId = "PENDING", isReimbursable = true),
                bill(transactionId = "PART", isReimbursable = true, reimbursedAmount = 2000),
                bill(transactionId = "DONE", isReimbursable = true, reimbursedAmount = 4500)
            )
        )
        val byTx = rows(csv).drop(1).associate { it[11] to it[12] }
        assertEquals("否", byTx["NONE"])
        assertEquals("待报销", byTx["PENDING"])
        assertEquals("部分报销", byTx["PART"])
        assertEquals("已报销", byTx["DONE"])
    }

    @Test
    fun `comma quote and newline in note are escaped`() {
        val csv = export(listOf(bill(note = "含,逗号 与\"引号\"\n换行")))
        val row = rows(csv)[1]
        assertEquals("含,逗号 与\"引号\" 换行", row[9])
        assertTrue("整行字段数仍须是 13", row.size == 13)
    }

    @Test
    fun `header and enum labels both follow the locale`() {
        // 表头按 locale 翻译，枚举值也必须跟着翻；否则英文界面导出「英文表头 + 中文值」。
        RuntimeEnvironment.setQualifiers("en")
        try {
            val rows = rows(export(listOf(bill(type = BillType.TRANSFER, walletId = 1, toWalletId = 2))))
            assertEquals("Time", rows.first()[0])
            val row = rows[1]
            assertEquals("Transfer", row[1])
            assertEquals("WeChat", row[10])
            assertEquals("No", row[12])
        } finally {
            RuntimeEnvironment.setQualifiers("zh")
        }
    }

    @Test
    fun `empty bill list still writes header only`() {
        val csv = export(emptyList())
        assertEquals(1, rows(csv).size)
    }

    @Test
    fun `cells starting with formula characters are neutralized`() {
        // 备注/商户来自导入的微信/支付宝文件，属外部内容；Excel/WPS 会把 = + - @ 开头当公式执行
        val csv = export(listOf(bill(category = "=SUM(A1)", merchant = "+cmd", note = "@note")))
        val row = rows(csv)[1]
        assertEquals("'=SUM(A1)", row[6])
        assertEquals("'+cmd", row[8])
        assertEquals("'@note", row[9])
    }

    @Test
    fun `exporter leaves the caller stream open`() {
        val out = object : ByteArrayOutputStream() {
            var closed = false
            override fun close() {
                closed = true
            }
        }
        exporter.export(emptyList(), out, wallets, books)
        assertFalse("导出器不应关闭调用方传入的流（关流归调用方）", out.closed)
    }
}
