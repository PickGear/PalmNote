package com.palmnote.data.export

/**
 * 把解析失败的记录写成 CSV，供用户补齐后重新导入。
 *
 * 表头列名同时满足 [BillCsvImporter] 的两种识别路径：
 * - 微信官方表头（含「交易时间 + 收/支 + 商品」）——[BillCsvImporter.detectFormat] 的先命中分支；
 * - GENERIC 关键词（含「金额 + 时间」）——两者都能被 [BillCsvImporter] 读回。
 *
 * 刻意不用纯 GENERIC 表头（「日期,金额,…」）：[BillCsvImporter.detectFormat] 在 GENERIC 兜底之前
 * 会先扫描品牌关键词（微信支付/支付宝），而失败行的原始内容常含这些词，会把整个文件误判成
 * 微信/支付宝账单，导致找不到表头而 0 行导入。用微信官方列名可让品牌识别与读回都确定命中。
 *
 * 「原始内容 / 失败原因」两列仅作提示，解析器按关键词匹配列会忽略它们。带 UTF-8 BOM，
 * 与 [CsvDataExporter] 导出一致。
 */
object FailedRowsCsvWriter {

    private const val HEADER = "交易时间,交易类型,交易对方,商品,收/支,金额,备注,交易单号,原始内容,失败原因"

    /**
     * 生成失败记录的 CSV 文本。
     * @param failures 解析阶段收集到的失败记录
     * @param reasonOf 把原因码转成用户可读（本地化）文案
     */
    fun build(failures: List<ImportFailure>, reasonOf: (ImportFailureReason) -> String): String {
        val sb = StringBuilder("\uFEFF")
        sb.append(HEADER).append('\n')
        for (f in failures) {
            val cells = listOf("", "", "", "", "", "", "", "", f.rawText, reasonOf(f.reason))
            sb.append(cells.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    /** CSV 字段转义：换行压成空格，含逗号/引号时用双引号包裹并与同行内引号双写 */
    private fun escape(value: String): String {
        val clean = value.replace('\n', ' ').replace('\r', ' ')
        return if (clean.contains(',') || clean.contains('"')) {
            "\"" + clean.replace("\"", "\"\"") + "\""
        } else {
            clean
        }
    }
}
