package com.palmnote.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palmnote.app.R
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.ui.theme.ExpenseRed
import com.palmnote.ui.theme.StatusActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 汇总样式小票（第二样式，单据风）：店头 + 口号 → 日期/单号 → 分类汇总表（分类/笔数/金额，
 * 转账是内部划转、不进分类表）→ 记账笔数/支出/收入合计 → 结余 → 真条码（编号加 * 号框）。
 * 锯齿撕边 + 暖白纯色纸面 + 热敏纹理，与明细样式同一套纸感。宽度固定 300dp，保证截图尺寸稳定。
 */
@Composable
internal fun DailySummaryPaper(dayKey: String, entries: List<DailyReceiptEntry>) {
    // 条码数字 = 打印时间的年月日时分（12 位），单号与条码同源
    val printCode = remember { SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault()).format(Date()) }
    val digits13 = remember(printCode) { ean13Digits(printCode) }
    val dayExpense = entries.filter { it.bill.type == BillType.EXPENSE }.sumOf { it.bill.amount }
    val dayIncome = entries.filter { it.bill.type == BillType.INCOME }.sumOf { it.bill.amount }
    val balance = dayIncome - dayExpense
    Column(modifier = Modifier.width(300.dp)) {
        ScallopedEdge()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PaperColor)
                .thermalPaperTexture(seed = dayKey.hashCode())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            SummaryPaperHead(printCode = printCode)
            Spacer(Modifier.height(6.dp))
            SummaryTable(entries = entries)
            Spacer(Modifier.height(8.dp))
            SummaryPaperTotals(
                count = entries.size,
                dayExpense = dayExpense,
                dayIncome = dayIncome,
                balance = balance
            )
            Spacer(Modifier.height(12.dp))
            Barcode(digits13 = digits13, display = "*$printCode*")
        }
    }
}

/** 汇总纸的店头：店名 + 口号 + 日期/单号两行信息（单号即条码编码内容）。 */
@Composable
private fun SummaryPaperHead(printCode: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "PalmNote",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Ink
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.bill_receipt_thanks),
            style = MaterialTheme.typography.labelMedium,
            color = InkLight,
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(12.dp))
    DashedDivider()
    Spacer(Modifier.height(10.dp))
    SummaryInfoRow(
        stringResource(R.string.bill_receipt_paper_date),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    )
    Spacer(Modifier.height(4.dp))
    SummaryInfoRow(stringResource(R.string.bill_receipt_paper_no), "No.$printCode")
    Spacer(Modifier.height(10.dp))
    DashedDivider()
}

/** 汇总表的行：分类左（弹性）、笔数列固定宽居中、金额列固定最小宽右对齐——两列位置不随金额长短漂移，表头与数据行共用同一结构。 */
@Composable
private fun SummaryTableRow(category: String, count: String, amount: String, header: Boolean = false, amountColor: Color = Ink) {
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            category,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            count,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            fontFamily = FontFamily.Monospace,
            color = Ink,
            textAlign = TextAlign.Center,
            // 40dp：表头「笔 数」也放得下，不折行
            modifier = Modifier.width(40.dp)
        )
        Text(
            amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            fontFamily = FontFamily.Monospace,
            color = amountColor,
            textAlign = TextAlign.End,
            // 最小宽固定：常规金额恰好占满、列位置稳定；超大金额向左自然加宽（优雅降级）
            modifier = Modifier.widthIn(min = 104.dp)
        )
    }
}

/** 分类汇总表：只进支出/收入，转账是内部划转；金额带符号（支出 −、收入 ＋）。 */
@Composable
private fun SummaryTable(entries: List<DailyReceiptEntry>) {
    SummaryTableRow(
        category = stringResource(R.string.bill_receipt_col_category),
        count = stringResource(R.string.bill_receipt_col_count),
        amount = stringResource(R.string.bill_receipt_col_amount),
        header = true
    )
    entries.filter { it.bill.type != BillType.TRANSFER }
        .groupBy { it.categoryName }
        .forEach { (name, list) ->
            val amount = list.sumOf { e ->
                if (e.bill.type == BillType.EXPENSE) -e.bill.amount else e.bill.amount
            }
            SummaryTableRow(
                category = name,
                count = list.size.toString(),
                amount = (if (amount < 0) "-" else "+") +
                    CurrencyUtils.formatCurrency(
                        LocalContext.current,
                        (if (amount < 0) -amount else amount).toMoney()
                    ),
                amountColor = if (amount < 0) ExpenseRed else StatusActive
            )
        }
}

/** 汇总单据的信息行：标签左、等宽值右。 */
@Composable
private fun SummaryInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = Ink)
    }
}

/** 汇总纸的合计区：记账笔数 / 支出合计 / 收入合计 / 结余（加粗加大、语义色负红正绿）。 */
@Composable
private fun SummaryPaperTotals(count: Int, dayExpense: Long, dayIncome: Long, balance: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        DashedDivider()
        Spacer(Modifier.height(8.dp))
        SummaryInfoRow(stringResource(R.string.bill_receipt_summary_count), count.toString())
        Spacer(Modifier.height(4.dp))
        TotalRow(stringResource(R.string.bill_receipt_summary_expense), dayExpense, Ink)
        TotalRow(stringResource(R.string.bill_receipt_summary_income), dayIncome, Ink)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.bill_receipt_summary_balance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                (if (balance < 0) "-" else "") +
                    CurrencyUtils.formatCurrency(context, (if (balance < 0) -balance else balance).toMoney()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (balance >= 0) StatusActive else ExpenseRed
            )
        }
    }
}
