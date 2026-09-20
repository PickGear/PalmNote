package com.palmnote.ui.bills

/** 预览页筛选档位：全部 / 只看待复核 / 已跳过（未勾选、本次不导入的行；仅文件页提供） */
enum class ReviewFilter { ALL, REVIEW, SKIPPED }

/** 单条记录需要人工复核的原因；[severe] = true 用警示红，false 用琥珀色 */
enum class ReviewReason(val severe: Boolean) {
    MISSING_AMOUNT(true),
    MISSING_DATE(true),
    BLANK_MERCHANT(false),
    UNKNOWN_CATEGORY(false)
}

/**
 * 从已有的预览字段推断单条记录是否需要人工复核，按严重度取最高优先级的一条：
 * 缺金额 > 缺日期 > 商户待确认 > 分类未识别；字段齐全时返回 null。
 *
 * 注意：文件导入的 [com.palmnote.data.export.ParsedBill] 金额/日期均非空（非空即不进入预览），
 * 因此文件预览只会命中「商户待确认 / 分类未识别」两条；「缺金额 / 缺日期」仅 OCR 结果可能命中。
 *
 * 分类未识别（[ReviewReason.UNKNOWN_CATEGORY]）由 [categoryResolved] 决定，**不再**用
 * `category == "其他"` 判断：源文件显式写「其他」、或词表有意映射到「其他」（转账/生活服务等）
 * 都算「已明确」，不再误报；只有真正未能识别、被迫回退「其他」才报。
 * OCR 侧（[com.palmnote.data.ocr.OcrBillResult] 无该字段）沿用默认 true，行为不变。
 */
@Suppress("UnusedParameter") // category 仅为调用点签名兼容保留；判定已改由 categoryResolved 决定
fun reviewReasonOf(
    amountCents: Long?,
    dateMillis: Long?,
    merchant: String,
    category: String,
    categoryResolved: Boolean = true
): ReviewReason? = when {
    amountCents == null || amountCents <= 0 -> ReviewReason.MISSING_AMOUNT
    dateMillis == null -> ReviewReason.MISSING_DATE
    merchant.isBlank() -> ReviewReason.BLANK_MERCHANT
    !categoryResolved -> ReviewReason.UNKNOWN_CATEGORY
    else -> null
}
