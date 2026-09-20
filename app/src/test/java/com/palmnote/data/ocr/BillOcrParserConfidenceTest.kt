package com.palmnote.data.ocr

import com.palmnote.data.export.ImportFailure
import com.palmnote.domain.model.BillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 独立验证（QA）：B6「逐字段 OCR 置信度」重构的行为保真 + 映射规则。
 *
 * 覆盖团队负责人交办的：
 * - T1(b) 差分：字符串适配路径 [BillOcrParser.parse] / [BillOcrParser.parseMultiple] 与新的
 *   [List]&lt;OcrLine&gt; 入口喂入 `OcrLine(line, null)` 必须产出**完全一致**的 [OcrBillResult]
 *   （金额/商户/日期/备注/分类/类型 + 四个置信度字段）。
 * - T2 适配路径不得把带标签字段降级：`confidence = null` 的 实付金额 → 仍 HIGH（不是 MISSING）。
 * - T3 各映射规则逐条：HIGH / INFERRED / MISSING、无金额块被丢弃、阈值边界 0.75。
 */
class BillOcrParserConfidenceTest {

    private val parser = BillOcrParser()

    /** 复刻适配层 `String.toOcrLines()`：trim + 去空行 + confidence = null */
    private fun toLines(text: String): List<OcrLine> =
        text.lines().map { it.trim() }.filter { it.isNotBlank() }.map { OcrLine(it, null) }

    // ── 使用真实样张（与 BillOcrParserTest 相同的文本集） ──────────────────────
    private val fixtures: List<String> = listOf(
        """
            微信支付凭证
            交易时间：2025-08-03 12:34:56
            商户：老王面馆
            商品：牛肉面
            支付方式：零钱
            金额：¥23.50
            支付成功
        """.trimIndent(),
        """
            支付宝
            2026年1月15日 09:08:00
            收款方：早餐店
            金额：¥52.00
            交易完成
        """.trimIndent(),
        "无金额样张",
        """
            微信支付
            22:47
            商户：瑞幸咖啡
            金额：¥9.90
            支付成功
        """.trimIndent(),
        """
            商户：店A
            金额：¥10.00
            ---
            商户：店B
            金额：¥20.00
        """.trimIndent(),
        """
            商户：便利店
            金额：¥8.50
        """.trimIndent(),
        """
            商户：超市
            小计 ¥18.00
            实付 ¥18.00
        """.trimIndent(),
        """
            自营 小米京东自营旗舰店
            进店
            回头客超481万+ 近90天100万好评
            小米（MI）电视S75
            到手¥4199
            数量 ×1, 75英寸 【推荐观距2.3米】, 画质旗舰款
            ¥4199
            无理由退货政策 · 30天价保
            普湃电视壁挂架适用65-7
            附件 ×1
            加购物车 使用说明
            实付款
            共减¥629.85 合计¥3569.15
            订单编号 3616000000000001 复制
            支付方式 在线支付
            全部订单信息
        """.trimIndent(),
        """
            百亿补贴·超霸 品牌
            GP超霸 CR2450 纽扣电池汽车钥匙遥控器电池
            ¥6.25
            CR2450*2粒,+1个收纳盒
            7天保价 极速发货
            补贴后共优惠¥2.09 实付款:¥4.16(免运费)
            订单号：260827-000000000000001
            消费提醒
            券后¥3.94
            ¥2.87 已拼13.6万+件
        """.trimIndent(),
        """
            黄庄酥皮月饼河北总店 直播
            【正宗黄庄酥皮月饼10个】蛋黄酥
            ¥9.80
            含运费险服务 实付款 ¥2.80
            商家 更多 申请售后
            良品壹面官方旗舰店 抖音旗舰
            【多拼组合】泡面先生定制14包
            ¥19.90
            含运费险服务 实付款 ¥19.90
            心相印洗护清洁旗舰店 抖音旗舰
            【3件划算】心相印茶语丝抽纸
            ¥3.00
            含运费险服务 实付款 ¥0.01
        """.trimIndent(),
        """
            趣评测商城
            【清仓特价】趣评测/适用于小米手机壳
            ¥8.9
            实付价 ¥5.9 价格明细
            闲鱼转卖 加入购物车
            实付款 共减¥3
            ¥5.9
            订单信息 共7项 0000000000000000001
        """.trimIndent(),
        """
            淘宝 彼岸有品
            膜栗 适用小米17Promax钢化膜
            ¥11.84
            09.01 实付款 ¥11.84
            淘宝 煤油之家
            煤油之家适用于小米13/14/15/17钢化膜
            ¥14.1
            09.01 应付款 ¥14.1
        """.trimIndent(),
        """
            全部账单
            2026年9月
            支出¥156.45 收入¥2.40
            京东
            9月8日 17:23
            -10.14
            京东
            9月7日 11:03
            -14.29
            朴朴超市-退款
            9月6日 11:31
            +0.23
            商家转账-来自拼多多
            9月6日 11:11
            +0.88
            朴朴
            9月6日 10:53
            -32.22
            已退款(¥0.23)
            京东
            9月5日 20:00
            -9.40
            扫二维码付款-给张枳生
            9月5日 12:48
            -3.00
            从3*****04的QQ钱包转到...
            9月5日 12:47
            11.64
        """.trimIndent(),
        """
            记账本
            总账本
            9月总支出 ¥53.74 总收入 ¥0.37
            9月9日 星期三 支0.00 收0.14
            投资理财
            11:24  余额宝-自动转入
            0.12
            转账
            +0.12
            11:24  淘宝签到提现-淘宝（中国）软件有...
            投资理财
            +0.02
            02:10  余额宝-收益发放
            9月8日 星期二 支0.00 收0.02
            投资理财
            +0.02
            02:43  余额宝-收益发放
        """.trimIndent()
    )

    // =================== T1(b)：适配路径 == 新 List<OcrLine> 路径 ===================

    @Test
    fun `T1b parseMultiple(String) equals parseMultiple(List OcrLine with null) for every fixture`() {
        for ((idx, text) in fixtures.withIndex()) {
            val failsA = mutableListOf<ImportFailure>()
            val failsB = mutableListOf<ImportFailure>()
            val adapter = parser.parseMultiple(text, failsA)
            val newPath = parser.parseMultiple(toLines(text), failsB)

            assertEquals("fixture#$idx size", adapter.size, newPath.size)
            for (i in adapter.indices) {
                // data class equality covers amount/merchant/date/note/category/type + 4 confidences
                assertEquals("fixture#$idx item#$i", adapter[i], newPath[i])
            }
            assertEquals(
                "fixture#$idx fails ${failsA.map { it.reason }} vs ${failsB.map { it.reason }}",
                failsA.map { it.reason }, failsB.map { it.reason }
            )
        }
    }

    @Test
    fun `T1b genuinely unsplit parse(String) equals parseMultiple(List)single`() {
        // 注意（HEAD 既有行为，非本次回归）：当"日期行在金额行之前"时，splitIntoBlocks 的
        // isNewTransaction 会把一笔小票切成两块，缺金额的那块被丢弃——此时 parse(text)（整段
        // 当作一笔）与 parseMultiple(text) 结果本就不同。故这里仅取**不会被切开**的文本，
        // 证明字符串适配入口与新的 List<OcrLine> 入口在同一路径下等价。
        val unsplitTexts = listOf(
            "无金额样张",
            """
                商户：便利店
                金额：¥8.50
            """.trimIndent(),
            "商户：店\n实付款 ¥10.00"
        )
        for ((idx, text) in unsplitTexts.withIndex()) {
            val viaNewPath = parser.parseMultiple(toLines(text))
            assertEquals("unsplit#$idx must yield exactly one record", 1, viaNewPath.size)
            assertEquals("unsplit#$idx parse(String) vs new path", parser.parse(text), viaNewPath.single())
        }
    }

    // =================== T2：默认值危险 & 适配路径不降级 ===================

    @Test
    fun `T2 OcrBillResult default confidences are all MISSING`() {
        val r = OcrBillResult()
        assertEquals(FieldConfidence.MISSING, r.amountConfidence)
        assertEquals(FieldConfidence.MISSING, r.merchantConfidence)
        assertEquals(FieldConfidence.MISSING, r.dateConfidence)
        assertEquals(FieldConfidence.MISSING, r.categoryConfidence)
    }

    @Test
    fun `T2 labelled paid amount with null source confidence stays HIGH not MISSING`() {
        // 适配路径：文本入口来源置信度恒为 null，不得因此把 HIGH 降级
        val single = parser.parse("商户：店\n实付款 ¥10.00")
        assertEquals(1000L, single.amount)
        assertEquals(FieldConfidence.HIGH, single.amountConfidence)

        // 同一个文本走 List<OcrLine> 入口（source confidence = null）
        val viaNew = parser.parseMultiple(toLines("商户：店\n实付款 ¥10.00"))
        assertEquals(FieldConfidence.HIGH, viaNew.first().amountConfidence)
    }

    // =================== T3：映射规则逐条 ===================

    @Test
    fun `T3 amount HIGH when from labelled paid line`() {
        val r = parser.parse("商户：店\n实付款 ¥10.00")
        assertEquals(FieldConfidence.HIGH, r.amountConfidence)
    }

    @Test
    fun `T3 amount HIGH when explicit yuan via blind max`() {
        // 无标签但带 ¥ → largestAmount 命中 yuan → HIGH
        val r = parser.parse("商户：店\n小计 ¥18.00")
        assertEquals(1800L, r.amount)
        assertEquals(FieldConfidence.HIGH, r.amountConfidence)
    }

    @Test
    fun `T3 amount INFERRED when only loose (no yuan) blind max`() {
        val r = parser.parse("商户：超市\n18.00")
        assertEquals(1800L, r.amount)
        assertEquals(FieldConfidence.INFERRED, r.amountConfidence)
    }

    @Test
    fun `T3 amount MISSING when no amount`() {
        val r = parser.parse("无金额样张")
        assertNull(r.amount)
        assertEquals(FieldConfidence.MISSING, r.amountConfidence)
    }

    @Test
    fun `T3 amountless block is dropped from parseMultiple (multi-block)`() {
        val text = """
            商户：店A
            金额：¥10.00
            ---
            这一块没有任何金额
        """.trimIndent()
        val fails = mutableListOf<ImportFailure>()
        val results = parser.parseMultiple(text, fails)

        assertEquals(1, results.size)
        assertEquals(1000L, results[0].amount)
        assertEquals(1, fails.size) // 无金额块进入失败列表
    }

    @Test
    fun `T3 merchant HIGH from label`() {
        val r = parser.parse("商户：老王面馆\n金额：¥10.00")
        assertEquals("老王面馆", r.merchant)
        assertEquals(FieldConfidence.HIGH, r.merchantConfidence)
    }

    @Test
    fun `T3 merchant INFERRED from heuristic fallback`() {
        // 无"商户/收款方"等标签，靠金额邻近的干净行启发式挑出
        val r = parser.parse("瑞幸咖啡\n金额：¥9.90")
        assertEquals("瑞幸咖啡", r.merchant)
        assertEquals(FieldConfidence.INFERRED, r.merchantConfidence)
    }

    @Test
    fun `T3 merchant MISSING when blank`() {
        val r = parser.parse("支付成功\n金额：¥9.90")
        assertEquals("", r.merchant)
        assertEquals(FieldConfidence.MISSING, r.merchantConfidence)
    }

    @Test
    fun `T3 date HIGH when full date pattern`() {
        val r = parser.parse("2025-08-03 12:34:56\n金额：¥10.00")
        assertEquals(FieldConfidence.HIGH, r.dateConfidence)
    }

    @Test
    fun `T3 date INFERRED when loose MM dd`() {
        val r = parser.parse("09.01 实付款 ¥11.84")
        assertEquals(FieldConfidence.INFERRED, r.dateConfidence)
        // 补当前年
        val expect = java.time.LocalDate.now().year
        val actual = java.time.Instant.ofEpochMilli(r.date ?: error("no date"))
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        assertEquals(expect, actual.year)
        assertEquals(9, actual.monthValue)
        assertEquals(1, actual.dayOfMonth)
    }

    @Test
    fun `T3 date MISSING when no date`() {
        val r = parser.parse("商户：店\n金额：¥10.00")
        assertNull(r.date)
        assertEquals(FieldConfidence.MISSING, r.dateConfidence)
    }

    @Test
    fun `T3 category MISSING when other`() {
        val r = parser.parse("无金额样张")
        assertEquals("其他", r.category)
        assertEquals(FieldConfidence.MISSING, r.categoryConfidence)
    }

    @Test
    fun `T3 category INFERRED when classified`() {
        val r = parser.parse("商户：老王面馆\n商品：牛肉面\n金额：¥23.50")
        assertEquals("餐饮", r.category)
        assertEquals(FieldConfidence.INFERRED, r.categoryConfidence)
    }

    // =================== T3：置信度阈值边界 ===================

    private fun pickAmountConfidence(sourceConfidence: Float): FieldConfidence? =
        parser.parseMultiple(listOf(OcrLine("商户：店", null), OcrLine("实付款 ¥10.00", sourceConfidence)))
            .firstOrNull()?.amountConfidence

    @Test
    fun `T3 threshold source 0_5 downgrades HIGH to INFERRED`() {
        assertEquals(FieldConfidence.INFERRED, pickAmountConfidence(0.5f))
    }

    @Test
    fun `T3 threshold source 0_8 keeps HIGH`() {
        assertEquals(FieldConfidence.HIGH, pickAmountConfidence(0.8f))
    }

    @Test
    fun `T3 threshold boundary exactly 0_75 keeps HIGH (not strictly less)`() {
        assertEquals(FieldConfidence.HIGH, pickAmountConfidence(0.75f))
    }

    @Test
    fun `T3 threshold source 0_74 downgrades to INFERRED`() {
        assertEquals(FieldConfidence.INFERRED, pickAmountConfidence(0.74f))
    }

    @Test
    fun `T3 null source confidence never downgrades`() {
        // 直接给 List<OcrLine> 且 confidence = null
        val r = parser.parseMultiple(listOf(OcrLine("商户：店", null), OcrLine("实付款 ¥10.00", null)))
        assertEquals(FieldConfidence.HIGH, r.first().amountConfidence)
    }

    // =================== T1(c)：类型/备注也保持不变（差分的一部分） ===================

    @Test
    fun `T1b type and note are identical across adapter and new path (explicit)`() {
        val text = """
            朴朴超市-退款
            9月6日 11:31
            +0.23
        """.trimIndent()
        val a = parser.parseMultiple(text).single()
        val b = parser.parseMultiple(toLines(text)).single()
        assertEquals(BillType.INCOME, a.type)
        assertEquals(a.type, b.type)
        assertEquals(a.note, b.note)
        assertTrue(a == b)
    }
}
