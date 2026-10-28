package com.palmnote.ui.bills

import com.palmnote.data.db.entity.Bill

/** 小票一行：账单 + 已解析的分类名（解析在调用方完成，与列表同口径）。 */
internal data class DailyReceiptEntry(val bill: Bill, val categoryName: String)
