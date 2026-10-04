package com.palmnote.domain.util

import com.palmnote.data.db.entity.LifeTemplate

/**
 * 模板的**语义身份** —— 「这个模板是什么」，区别于 icon（显示成什么样子）。
 *
 * ## 为什么需要它
 *
 * 此前全仓用 `template.icon == "calendar_month"` 这类**图标字符串**判断语义，
 * 而图标正是用户在模板编辑器里可以随便改的（见 `LifeTemplateEditScreen.ICON_KEYS`，
 * 那张选项表恰好包含 calendar_month / checklist / timer / timer_off / cake / celebration）。
 * 于是「打卡模板改成 book 图标」会立刻让连击不再计算、详情页换 hero、统计不再计入完成率 ——
 * **显示选项与语义类型共用一个字段**。
 *
 * 同一件事在提醒功能上已经发生并被修过：`ReminderCard` 的注释写着
 * 「配置显式写在模板行上……改图标、改字段 key 都不会像旧图标匹配那样静默弄丢提醒」。
 * 本文件是把同一个修法推广到其余判据的**唯一入口**。
 *
 * ## 现状：v12 起已持久化，漂移已消除
 *
 * `life_templates.kind` 列（v12）存枚举名，[getKind] 优先读它，读不到才按 icon 兜底。
 * 写入侧在**新建模板 / 播种 / CSV 导入**时用 [kindFromIcon] 固化一次；此后用户改图标
 * 只换显示，连击 / hero / 统计口径都不会再漂移。
 *
 * `MIGRATION_11_12` 按 icon 回填存量行（保持迁移前行为）；未匹配的图标保持 NULL，
 * 由 [getKind] 按 icon 兜底 —— **NULL 与显式 `GENERIC` 语义不同**，见 [getKind] 的 KDoc。
 *
 * 规则：**读取一律走 [getKind]，写入一律走 [kindFromIcon]**，不要在调用点写 `icon == "..."`。
 *
 * ## 与 [LifeTemplateRouteType] 的分工
 *
 * `RouteType` 是**路由分类**（10 个值，含 MOOD / JOURNAL / COUNTUP 等尚无专属详情跳转的类型）；
 * `Kind` 是**行为身份**（11 个值 = `GENERIC` + 10 个有专属行为分支的类型）。两者不合并：
 * RouteType 的额外值不影响任何行为分支，混进来只会扩大迁移面。
 */
enum class LifeTemplateKind {
    /** 通用：没有任何专属行为分支。 */
    GENERIC,

    /** 打卡（连击、本期/累计次数、打卡完成率的分母口径）。 */
    HABIT,

    /** 专注（今日/本周专注时长）。 */
    FOCUS,

    /** 待办（未完成计数、待办指标）。 */
    TODO,

    /** 倒计时（按目标日期算剩余天数）。 */
    COUNTDOWN,

    /** 生日（按日期算天数 + 生肖/农历等生日专属区块）。 */
    BIRTHDAY,

    /** 纪念日（同生日区块，另有纪念日专属补充）。 */
    ANNIVERSARY,

    // ── 以下四个同样**有专属行为分支**（life 模块内），故一并纳入 ──

    /** 日记（按天聚合写日记的天数、连续天数）。 */
    JOURNAL,

    /** 心情（心情字段、心情连续天数）。 */
    MOOD,

    /** 学习（学科指标）。 */
    STUDY,

    /** 旅行（行程专属区块，且吃掉的字段 key 与其它类型不同）。 */
    TRAVEL
}

/**
 * 由**图标字符串**推导语义身份。
 *
 * 之所以是「字符串入参」而不是只做 `LifeTemplate` 的扩展：写入侧（新建模板、播种、
 * CSV 导入）手上只有 icon 字符串，还没有 `LifeTemplate` 实例 —— 那些地方正是
 * **语义身份被固化的时刻**，必须能直接调用。
 *
 * **仅作兜底**：读取行为请一律用 [LifeTemplate.getKind]。
 *
 * 注意这里**不带** `isBuiltin`/`isSpecial` 前置判断 —— 与 [getRouteType] 的 `GENERIC` 兜底不同，
 * 语义身份对自建模板同样成立（用户自建一个「纪念日」模板，行为就该和内置纪念日一致）。
 */
fun kindFromIcon(icon: String): LifeTemplateKind = when (icon) {
    "calendar_month" -> LifeTemplateKind.HABIT
    "timer" -> LifeTemplateKind.FOCUS
    "checklist" -> LifeTemplateKind.TODO
    "timer_off" -> LifeTemplateKind.COUNTDOWN
    "cake" -> LifeTemplateKind.BIRTHDAY
    "celebration", "favorite" -> LifeTemplateKind.ANNIVERSARY
    "book" -> LifeTemplateKind.JOURNAL
    "mood" -> LifeTemplateKind.MOOD
    "school" -> LifeTemplateKind.STUDY
    "flight" -> LifeTemplateKind.TRAVEL
    else -> LifeTemplateKind.GENERIC
}

/**
 * 语义身份的**唯一读取入口**（v12 起读持久化的 `kind` 列）。
 *
 * ## 取值顺序
 *
 * 1. `kind` 列有值且能解析成枚举 → 用它。**这是正常路径**，也是「改图标不再改语义」的保证。
 * 2. `kind` 为空或值非法 → [kindFromIcon] 兜底。
 *
 * ## 为什么兜底不是 GENERIC
 *
 * 曾经想让「没有持久化 kind」直接返回 `GENERIC`（「宁可退回最保守的通用」）。**那是错的**：
 * 若某一刻 `kind` 为空（v12 迁移漏回填、导入的 CSV 没带该列、非法值被写坏），
 * 20 处调用点会**一次性全部退化成「通用」**——连击不计算、待办不计未完成、生日不显示天数，
 * 比没有这列时更糟，而且是静默的。
 *
 * 「要么对，要么看起来对」这句话本身没错，但它不能用来给一个**可能缺失的数据**做默认值。
 * 按图标兜底是**本迁移之前沿用了整个项目的既有语义**，退回它 = 退回「迁移前」，而不是
 * 退回到一个从未存在过的「全都是通用模板」的世界。两害相权，这是唯一可接受的一侧。
 *
 * 非法值（`valueOf` 抛异常）同样走兜底而不是崩溃：一列由字符串承载的枚举，
 * 绝不该因为一个脏值让整个详情页打不开。
 */
fun LifeTemplate.getKind(): LifeTemplateKind =
    kind?.let { runCatching { LifeTemplateKind.valueOf(it) }.getOrNull() } ?: kindFromIcon(icon)
