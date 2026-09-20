package com.palmnote.data.export

/**
 * 导入判重用的「业务键」。
 *
 * ## 为什么不能用主键 id 判重
 *
 * id 是**本机自增**的，跨设备毫无意义——两台设备各自都有 id=1 的账单，它们不是同一条。
 * 旧实现一边按 id 判重、一边用 `OnConflictStrategy.REPLACE` 落库，于是「另一台设备的第 1 条」
 * 会直接盖掉「本机的第 1 条」，而且全程不告诉用户（违反「不静默覆盖」）。
 *
 * 业务键要回答的是用户心里的那句话：「这是不是同一条记录」。
 *
 * ## 两条硬约束
 *
 * 1. **必须完全由包内字段推导。** 键里绝不能掺入 `System.currentTimeMillis()`、
 *    `UUID.randomUUID()` 这类生成值，否则同一个包导入两次会得到不同的键，去重直接失效。
 * 2. **确定性 + 区分度。** 判重方向是「宁可当成新记录，也不要误合并」——
 *    因为跳过是安全的，合并错是丢数据。
 *
 * ## 分隔符
 *
 * 字段之间用 U+0001（SOH，控制字符）而不是 `|` 或 `,`：
 * 用户备注里出现 `|` 是家常便饭，用可打印字符拼键会让 `"a|b"` 与 `"a"+"b"` 撞成同一个键。
 *
 * 本对象刻意不依赖任何 Android / Room 类型，走纯 Kotlin，便于单元测试。
 */
object ImportKeys {

    private const val SEP = '\u0001'

    /**
     * 账单键：**交易单号优先**。
     *
     * 有单号就直接用单号——微信/支付宝导入、以及任何带单号的记录都能被精确判重。
     * 没有单号（手记账单）才退化成字段指纹：同一钱包 · 同额 · 同一秒 · 同分类 ·
     * 同备注 · 同商户 · 同支付方式。这个组合已经细到「同一秒在同一商户买两笔同额同备注的东西」
     * 才算撞车，实际不可能；而它恰好能精确识别「同一个包里的同一条记录」。
     *
     * @param date 毫秒时间戳。导出侧按秒精度格式化，往返稳定。
     */
    fun bill(
        transactionId: String?,
        walletId: Long?,
        amount: Long,
        date: Long,
        category: String,
        subCategory: String,
        note: String,
        merchant: String,
        paymentMethod: String
    ): String {
        val tx = transactionId?.trim().orEmpty()
        return if (tx.isNotEmpty()) {
            "bill-tx" + SEP + tx
        } else {
            // 日期归一到秒：CSV 往返是秒精度，库里存的行带毫秒；
            // 不归一的话「重新导入自己的导出」指纹对不上，会把已有账单当新账单重复插入
            join("bill-fp", walletId, amount, date / 1000L * 1000L, category, subCategory, note, merchant, paymentMethod)
        }
    }

    /** 钱包键：名称 + 类型。用户在设置里看到的就是「这两个是不是同一个账户」。 */
    fun wallet(name: String, type: String): String = join("wallet", name, type)

    /** 物品键：名称 + 获取日期。同名同日期视为同一件。 */
    fun asset(name: String, acquisitionDate: Long?): String = join("asset", name, acquisitionDate)

    /** 目标键：标题 + 开始日期。 */
    fun goal(title: String, startDate: Long?): String = join("goal", title, startDate)

    /** 纪念日键：标题 + 公历日期。 */
    fun anniversary(title: String, solarDate: Long?): String = join("anniv", title, solarDate)

    /** 瞬间键：时间戳 + 正文。正文是瞬间的主键式内容。 */
    fun moment(timestamp: Long, content: String): String = join("moment", timestamp / 1000L * 1000L, content)

    /**
     * 打卡记录键：目标 + 日期。
     *
     * @param goalId 必须是**已解析到本机**的目标 id（若目标被改名合并，这里应传映射后的 id），
     *               否则跨设备合并时同一次打卡会被当成两条。
     */
    fun goalCheckIn(goalId: Long?, date: Long?): String = join("checkin", goalId, date)

    /** 周期模板键：名称 + 收支类型 + 频率。 */
    fun recurringTemplate(name: String, type: String, frequency: String): String =
        join("tmpl", name, type, frequency)

    /**
     * 预算键：**只用年月**。
     *
     * 本应用的语义是「一个月一条预算」，`budgets` 表没有唯一索引。
     * 若把金额也拼进键里，包内金额与本地不一致时会插出**第二条同月预算**，
     * 反而制造出「一个月两条预算」这种脏数据。
     */
    fun budget(yearMonth: String): String = join("budget", yearMonth)

    /** 分类键：类型 + 名称。「用户心里的同一个分类」是名字，不是 id。 */
    fun categoryConfig(type: String, name: String): String = join("cat", type, name)

    /** 标签键：名称。 */
    fun customTag(name: String): String = join("tag", name)

    /** 分类映射键：物品分类 + 账单分类。 */
    fun categoryMapping(assetCategory: String, billCategory: String): String =
        join("map", assetCategory, billCategory)

    /**
     * 使用记录键：使用时间 + 备注。
     *
     * 调用方按「同一个物品」分组比较，因此键里不含 assetId。
     * 旧实现每次都无脑 append，重复导入自己的包会让使用次数指数级膨胀。
     */
    fun usageRecord(usedAt: Long, note: String): String = join("use", usedAt, note)

    private fun join(vararg parts: Any?): String =
        parts.joinToString(SEP.toString()) { norm(it) }

    private fun norm(value: Any?): String = when (value) {
        null -> "~" // 用不可能出现在文本里的哨兵，避免 null 与空串撞键
        is String -> value.trim()
        else -> value.toString()
    }
}
