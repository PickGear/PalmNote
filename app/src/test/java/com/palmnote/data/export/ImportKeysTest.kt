package com.palmnote.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 业务键必须同时满足两条性质，缺一条判重就是错的：
 *
 * 1. **确定性** —— 同一个包导入两次要得到同一个键。若键里掺了 `System.currentTimeMillis()`
 *    这类生成值，第二次导入就会得到不同的键，去重形同虚设（宁漏不误的前提是键稳定）。
 * 2. **区分度** —— 不同记录不能撞键，否则会被误判成重复而**静默丢掉一条真数据**。
 *
 * 这组用例是纯 JVM 的（[ImportKeys] 不依赖 Android），因此不需要 Robolectric。
 */
class ImportKeysTest {

    @Test
    fun `bill key prefers transactionId over everything else`() {
        val a = ImportKeys.bill("TX-1", 1L, 1000L, 1_700_000_000_000L, "餐饮", "", "", "", "WECHAT")
        val b = ImportKeys.bill("TX-1", 2L, 2000L, 1_700_000_001_000L, "交通", "", "x", "y", "CASH")
        // 有单号时其余字段一律不参与：同一个单号就是同一条
        assertEquals(a, b)
    }

    @Test
    fun `blank transactionId is equivalent to no transactionId`() {
        val a = ImportKeys.bill(null, 1L, 1000L, 100L, "餐饮", "", "午饭", "食堂", "CASH")
        val b = ImportKeys.bill("   ", 1L, 1000L, 100L, "餐饮", "", "午饭", "食堂", "CASH")
        // 否则「导出成空串 / 解析成 null」会造成同一条记录被判成两条
        assertEquals(a, b)
    }

    @Test
    fun `bill fingerprint separates genuinely different rows`() {
        val base = ImportKeys.bill(null, 1L, 1000L, 100_000L, "餐饮", "", "午饭", "食堂", "CASH")
        assertNotEquals(base, ImportKeys.bill(null, 2L, 1000L, 100_000L, "餐饮", "", "午饭", "食堂", "CASH"))
        assertNotEquals(base, ImportKeys.bill(null, 1L, 1001L, 100_000L, "餐饮", "", "午饭", "食堂", "CASH"))
        assertNotEquals(base, ImportKeys.bill(null, 1L, 1000L, 101_000L, "餐饮", "", "午饭", "食堂", "CASH"))
        assertNotEquals(base, ImportKeys.bill(null, 1L, 1000L, 100_000L, "餐饮", "", "晚饭", "食堂", "CASH"))
        assertNotEquals(base, ImportKeys.bill(null, 1L, 1000L, 100_000L, "餐饮", "", "午饭", "超市", "CASH"))
        assertNotEquals(base, ImportKeys.bill(null, 1L, 1000L, 100_000L, "餐饮", "", "午饭", "食堂", "WECHAT"))
    }

    @Test
    fun `bill fingerprint truncates to seconds for round-trip stability`() {
        // CSV 往返只保留秒：库内毫秒级行与包内秒级行必须是同一个键，
        // 否则「重新导入自己的导出」会把已有账单当成新账单重复插入
        assertEquals(
            ImportKeys.bill(null, 1L, 1000L, 100_999L, "餐饮", "", "午饭", "食堂", "CASH"),
            ImportKeys.bill(null, 1L, 1000L, 100_000L, "餐饮", "", "午饭", "食堂", "CASH")
        )
    }

    @Test
    fun `field boundaries cannot be forged with printable characters`() {
        // 用 '|' 或 ',' 拼键的话，用户备注里一个竖线就能让「两个字段」冒充「一个字段」。
        // 分隔符取 U+0001 控制字符，并且字段数不同时键也不同。
        assertNotEquals(
            ImportKeys.categoryConfig("A", "B"),
            ImportKeys.categoryConfig("A\u0001B", "")
        )
        assertNotEquals(ImportKeys.customTag("a\u0001b"), ImportKeys.customTag("a"))
    }

    @Test
    fun `null is not conflated with empty or zero`() {
        assertNotEquals(ImportKeys.asset("杯子", null), ImportKeys.asset("杯子", 0L))
        assertNotEquals(ImportKeys.asset("杯子", null), ImportKeys.asset("", null))
    }

    @Test
    fun `surrounding whitespace is normalized`() {
        assertEquals(ImportKeys.wallet("现金", "CASH"), ImportKeys.wallet("  现金  ", "CASH"))
    }

    @Test
    fun `each entity kind keeps its own namespace`() {
        val keys = listOf(
            ImportKeys.wallet("x", "CASH"),
            ImportKeys.asset("x", null),
            ImportKeys.goal("x", null),
            ImportKeys.anniversary("x", null),
            ImportKeys.moment(0L, "x"),
            ImportKeys.goalCheckIn(null, null),
            ImportKeys.recurringTemplate("x", "EXPENSE", "MONTHLY"),
            ImportKeys.categoryConfig("x", "y"),
            ImportKeys.customTag("x"),
            ImportKeys.budget("x"),
            ImportKeys.categoryMapping("x", "y"),
            ImportKeys.usageRecord(0L, "x")
        )
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `usage record key is deterministic for the same tap`() {
        assertEquals(ImportKeys.usageRecord(1000L, "喝咖啡"), ImportKeys.usageRecord(1000L, "喝咖啡"))
        assertNotEquals(ImportKeys.usageRecord(1000L, "喝咖啡"), ImportKeys.usageRecord(1000L, "喝茶"))
        assertNotEquals(ImportKeys.usageRecord(1000L, "喝咖啡"), ImportKeys.usageRecord(1001L, "喝咖啡"))
    }
}
