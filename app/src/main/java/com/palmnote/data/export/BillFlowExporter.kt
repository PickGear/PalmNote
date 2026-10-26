package com.palmnote.data.export

import android.content.Context
import com.palmnote.app.R
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.BillType
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 账单流水导出器：全部账单按时间升序写成 **13 列 CSV（UTF-8 带 BOM）**。
 *
 * 与「设置 → 数据交换」的交换包（[CsvDataExporter]）是两条不同的口径：
 * - 交换包是**可回导契约**（ZIP 内按行混排、靠「实体类型」列解析），代码注释明确「不要改」；
 * - 这里是自有干净格式，**有意不可回导**（回导走交换包；也避免导出→再导入造成重复记账）。
 *
 * 列口径（v1 定稿）：交易时间(yyyy-MM-dd HH:mm) / 收支(支出|收入|转账) / 金额(分→元恒正) /
 * 账本 / 钱包 / 转入钱包(仅转账行) / 分类 / 子分类 / 商户 / 备注 / 支付方式 / 交易单号 / 报销
 * (否|待报销|已报销|部分报销 —— [Bill.reimbursedAmount] 支持部分报销)。
 * 不含地点/标签/图片/经纬度/内部 ID（transactionId 除外——它是与原始支付宝/微信文件
 * 对账 VLOOKUP 的连接键）。演示账单排除（示例不进「我的数据」，与 CSV 导出同口径）。
 *
 * 收支与支付方式两列走 strings（表头也走 strings）：表头按 locale 翻译而值写死中文的话，
 * 英文界面会导出「英文表头 + 中文值」。[CsvDataExporter] 那套写死中文的 CODE_TO_CN 是
 * 可回导契约，此处不复用。
 */
class BillFlowExporter(private val context: Context) {

    /** 时间列格式：精确到分钟（账单不存秒级语义）。 */
    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    /**
     * 支付方式 → 本地化标签。**必须走 strings**：表头已经按 locale 翻译，
     * 值若写死中文，英文界面导出的就是「英文表头 + 中文值」。
     * （交换包 [CsvDataExporter] 的写死中文映射属可回导契约，此处不复用。）
     */
    private val paymentLabelRes = mapOf(
        "CASH" to R.string.payment_cash, "WECHAT" to R.string.payment_wechat,
        "ALIPAY" to R.string.payment_alipay, "CARD" to R.string.payment_card,
        "BANK_TRANSFER" to R.string.payment_bank_transfer, "OTHER" to R.string.payment_other
    )

    /** 收支类型 → 本地化标签（转账必须有自己的位置）。 */
    private val typeLabelRes = mapOf(
        BillType.EXPENSE to R.string.bill_filter_type_expense,
        BillType.INCOME to R.string.bill_filter_type_income,
        BillType.TRANSFER to R.string.bill_transfer
    )

    /**
     * 写出全部账单（升序、排除演示数据）到 [output]，返回写出的行数。
     * [wallets] / [books] 用于把 ID 解析成名字（查不到时留空，不写裸 ID）。
     *
     * **不关闭 [output]**：流的所有权留给调用方（VM 用 `use {}` 包住），
     * 避免「导出器偷偷关流」导致调用方后续写操作失败。
     */
    fun export(
        bills: List<Bill>,
        output: OutputStream,
        wallets: List<Wallet>,
        books: List<AccountBook>
    ): Int {
        val walletNames = wallets.associate { it.id to it.name }
        val bookNames = books.associate { it.id to it.name }
        val header = context.getString(R.string.bill_flow_header)
        val rows = bills
            .asSequence()
            .filterNot { it.isDemo }
            .sortedBy { it.date }
            .map { bill -> rowOf(bill, walletNames, bookNames) }
            .toList()
        val all = buildString {
            append(header).append('\n')
            rows.forEach { append(it).append('\n') }
        }

        output.write("\uFEFF".toByteArray(Charsets.UTF_8))
        output.write(all.toByteArray(Charsets.UTF_8))
        output.flush()
        return rows.size
    }

    private fun rowOf(bill: Bill, walletNames: Map<Long, String>, bookNames: Map<Long, String>): String {
        val cells = listOf(
            timeFormat.format(Instant.ofEpochMilli(bill.date)),
            typeLabelRes[bill.type]?.let(context::getString).orEmpty(),
            moneyText(bill.amount),
            bookNames[bill.accountBookId].orEmpty(),
            bill.walletId?.let { walletNames[it] }.orEmpty(),
            bill.toWalletId?.let { walletNames[it] }.orEmpty(),
            bill.category,
            bill.subCategory,
            bill.merchant,
            bill.note,
            paymentLabelRes[bill.paymentMethod.value]?.let(context::getString).orEmpty(),
            bill.transactionId,
            reimbursementLabel(bill)
        )
        return cells.joinToString(",") { escape(it) }
    }

    /** 分 → 元恒正文本（两位小数）：导出列不带正负号，收支由「收支」列表达。 */
    private fun moneyText(cents: Long): String {
        val safe = if (cents < 0) -cents else cents
        return "${safe / 100}.${(safe % 100).toString().padStart(2, '0')}"
    }

    /** 报销列：不可报销=否；可报销未报=待报销；报满=已报销；报了一部分=部分报销。 */
    private fun reimbursementLabel(bill: Bill): String {
        if (!bill.isReimbursable) return context.getString(R.string.bill_flow_reimb_none)
        val reimbursed = bill.reimbursedAmount
        return when {
            reimbursed <= 0 -> context.getString(R.string.bill_flow_reimb_pending)
            reimbursed >= bill.amount -> context.getString(R.string.bill_flow_reimb_done)
            else -> context.getString(R.string.bill_flow_reimb_partial)
        }
    }

    /**
     * CSV 字段转义：换行压成空格；以 `= + - @` 或制表符开头的单元格前置 `'`，避免 Excel/WPS
     * 把导入来源（微信/支付宝文件）里的备注、商户当公式执行（CSV 注入）；再做逗号/引号包裹。
     */
    private fun escape(value: String): String {
        val clean = value.replace('\n', ' ').replace('\r', ' ')
        val neutralized = if (clean.isNotEmpty() && clean.first() in FORMULA_PREFIXES) "'$clean" else clean
        return if (neutralized.contains(',') || neutralized.contains('"')) {
            "\"" + neutralized.replace("\"", "\"\"") + "\""
        } else {
            neutralized
        }
    }

    private companion object {
        /** Excel / WPS 会把这些首字符当作公式起始 */
        val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@', '\t')
    }
}
