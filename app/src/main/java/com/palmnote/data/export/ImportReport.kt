package com.palmnote.data.export

/**
 * 一次 CSV-ZIP 导入的结果。
 *
 * ## 为什么「跳过」必须单独计数
 *
 * 旧实现只返回一个 `Int`（写入条数），界面把它渲染成「导入成功，共 N 条记录」。
 * 于是「重复导入自己的包」和「包是空的」在界面上长得一模一样——都是 N=0 / 一个小数字，
 * 用户无从判断到底发生了什么，更不知道有 M 条被悄悄丢掉了。
 *
 * 把三个数字拆开，才能把「导入」这件事说清楚：
 * - 真的新增了什么（[inserted]）
 * - 什么因为已存在而没动（[skipped]）——**这是"没有覆盖你的数据"的证据，不是失败**
 * - 为了保持账实相符顺手修正了什么（[walletsRecalculated]）
 */
data class ImportReport(
    /** 本次真正写入的记录数（物品的使用记录计入物品所属的那次写入，不单独累加）。 */
    val inserted: Int = 0,

    /** 因为业务键已存在而跳过的记录数。跳过 = 保留现有数据，绝不覆盖。 */
    val skipped: Int = 0,

    /** 余额与账单不一致、因而按账单重算了的钱包数。 */
    val walletsRecalculated: Int = 0
) {
    /** 包里没有任何可识别的记录（既没写入也没跳过）。 */
    val isEmpty: Boolean get() = inserted == 0 && skipped == 0
}
