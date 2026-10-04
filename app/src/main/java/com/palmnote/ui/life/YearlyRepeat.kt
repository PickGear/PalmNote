package com.palmnote.ui.life

import com.palmnote.data.worker.nextLunarBirthday
import java.time.LocalDate

/**
 * 「每年重复」的读数语义（生日 / 纪念日这类）。
 *
 * ## 为什么需要它
 *
 * 「下一次周年」的算法此前**只活在提醒 Worker 里**：提醒会在生日前 N 天响，
 * 但**记录本身不滚** —— 生日过去之后，详情页和卡片的「剩余天数」仍按当年那个已过去的日期算，
 * 读数是负的，而标签还写着「剩余天数」。倒数日品类（Days Matter 等）把「重复与否」
 * 做成事件的显式属性，这也正是本品类用户的默认期待。
 *
 * ## 一条硬约束：口径必须与提醒**共用**
 *
 * 本文件的 [nextYearlyOccurrence] 与 `LifeDailyCheckWorker.checkBirthdayReminders` 必须给出
 * **同一个日期**。两处一旦漂移，就会出现「提醒说还有 3 天、详情页说还有 368 天」这种自相矛盾 ——
 * 所以农历分支直接复用 Worker 里那份 `nextLunarBirthday`，公历分支的 2/29 处理也照抄其口径。
 */
internal data class YearlyRepeat(val lunar: Boolean)

/**
 * 下一次周年（**含今天**：今天就是周年日则返回今天）。
 *
 * - 农历：走 [nextLunarBirthday]（按农历月-日反算今年，已过则滚到明年，保留闰月语义）；
 * - 公历：`withYear` 平移到今年，已过则 +1 年；2/29 在平年落到 2/28（避免抛异常）；
 * - 农历反算失败（如当年无对应闰月）：**回退公历口径**而不是放弃 —— 提醒路径同样这么处理，
 *   宁可早一天也不静默丢掉。
 */
internal fun nextYearlyOccurrence(anchor: LocalDate, today: LocalDate, lunar: Boolean): LocalDate {
    if (lunar) nextLunarBirthday(anchor, today)?.let { return it }
    val thisYear = try {
        anchor.withYear(today.year)
    } catch (_: java.time.DateTimeException) {
        LocalDate.of(today.year, 2, 28)
    }
    return if (thisYear.isBefore(today)) thisYear.plusYears(1) else thisYear
}
