package com.palmnote.data.export

/**
 * 一条在解析阶段被丢弃的导入记录。
 *
 * [rawText] 保存原始来源文本（CSV 行 / XLSX 行 / OCR 分块），[reason] 为机器可判定的失败原因码，
 * 供结果页统计、展示，以及导出「可修正后重新导入」的 CSV 使用。
 * 原因码在 UI/导出层本地化为用户可见文案。
 */
data class ImportFailure(
    val rawText: String,
    val reason: ImportFailureReason
)

/** 导入记录被丢弃的原因；文案在 UI 层（strings.xml）本地化 */
enum class ImportFailureReason {
    /** 无法从原始文本识别出金额 */
    MISSING_AMOUNT,

    /** 原始文本缺少可解析的日期 */
    MISSING_DATE,

    /** 原始文本结构异常，解析过程抛出异常 */
    UNPARSEABLE
}
