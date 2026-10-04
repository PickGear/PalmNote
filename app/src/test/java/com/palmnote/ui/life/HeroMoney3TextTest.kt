package com.palmnote.ui.life

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HeroMoney3 的**文案成型**（从 124 行的组合函数里抽出来的纯函数）。
 *
 * 抽出来的直接目的是降 detekt 复杂度（124 行 / 复杂度 40 → 已达标），
 * 附带好处是这些 `when` 终于**可单测**了 —— 枚举到文案的映射写错，用户看到的是
 * 语义错位的注脚（比如"已花"配到"已存"的格式串），而这类错在界面上很隐蔽。
 *
 * 注：`heroChipText` 需要构造 17 个字段的 `Money3Data`，测试性价比低，未覆盖（其分支简单）。
 */
class HeroMoney3TextTest {

    private val labels = HeroLabels(
        gap = "还差 %1\$s",
        achieved = "已达成",
        daysToCharge = "还有 %1\$d 天扣费",
        savedPct = "已存 %1\$d%%",
        spentPct = "已花 %1\$d%%",
        donePct = "已完成 %1\$d%%",
        targetDays = "目标 %1\$s 天",
        targetDate = "目标日 %1\$s",
        nextBilling = "下次 %1\$s",
        categories = "%1\$d 类",
        budgetLabel = "预算 %1\$s",
        spentUnit = "已花",
        months = listOf("月付", "季付", "年付"),
        remaining = "还有",
        passed = "已经",
        today = "今天",
        overdue = "超期",
        yearsAndDays = "第 %1\$d 年 · 已经 %2\$s 天",
        pagesRead = "读到 %1\$s / %2\$s 页",
        pagesLeft = "剩余 %1\$s 页",
        moodDateLine = "%1\$s · 有记录",
        longest = "最长 %1\$s"
    )

    @Test
    fun `左下注脚按语义成型`() {
        assertEquals("已存 42%", heroLeftText(NoteKind.SAVED_PCT, 42, "", labels))
        assertEquals("已花 42%", heroLeftText(NoteKind.SPENT_PCT, 42, "", labels))
        assertEquals("已完成 42%", heroLeftText(NoteKind.DONE_PCT, 42, "", labels))
        assertEquals("目标 30 天", heroLeftText(NoteKind.TARGET_DAYS, 0, "30", labels))
        assertEquals("", heroLeftText(NoteKind.NONE, 0, "", labels))
    }

    @Test
    fun `右下注脚按语义成型`() {
        assertEquals("目标日 2026-12-20", heroRightText(RightKind.TARGET_DATE, "2026-12-20", 0, labels))
        assertEquals("下次 11-05", heroRightText(RightKind.NEXT_BILLING, "11-05", 0, labels))
        assertEquals("3 类", heroRightText(RightKind.CATEGORIES, "", 3, labels))
        assertEquals("最长 12", heroRightText(RightKind.LONGEST, "12", 0, labels))
        // 空值不给注脚（不画"最长 "这种半句）
        assertEquals("", heroRightText(RightKind.LONGEST, "", 0, labels))
        assertEquals("", heroRightText(RightKind.NONE, "x", 0, labels))
    }

    @Test
    fun `单位按订阅周期本地化`() {
        assertEquals("/ 月付", heroUnitText("monthly", "元", labels))
        assertEquals("/ 季付", heroUnitText("quarterly", "元", labels))
        assertEquals("/ 年付", heroUnitText("yearly", "元", labels))
        // 认不出的周期 / 没有周期 → 用模板自己的单位文案，不硬套
        assertEquals("元", heroUnitText("weekly", "元", labels))
        assertEquals("元", heroUnitText(null, "元", labels))
    }
}
