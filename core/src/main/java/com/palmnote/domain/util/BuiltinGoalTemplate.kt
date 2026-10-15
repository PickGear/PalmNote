package com.palmnote.domain.util

/**
 * 内置「目标」模板（icon = [ICON]）的**定义常量**。
 *
 * 为什么单独抽一个对象：这份定义有两处消费者，且**必须逐字节一致**——
 * 1. `LifeDataSeeder` 的种子列表（全新安装 / 存量补插）；
 * 2. `MIGRATION_17_18`（把退役的 `goals` 表逐行搬进该模板的条目，迁移里得先建模板行）。
 *
 * 两处各写一份 JSON 的代价是「悄悄漂移」：迁移写进去的 `fieldsConfig` 与种子不一致时，
 * 存量安装的「目标」模板字段 key 会对不上迁移过来的数据（进度条直接不出现），
 * 而这类不一致**不会报错**，只会在真机上表现为「我的目标是空的」。
 *
 * 语义身份由 [ICON] 决定（`kindFromIcon` → [LifeTemplateKind.GOAL]，`BUILTIN_NAME_RES`
 * → 模板名/描述的本地化资源），所以 [NAME]/[DESCRIPTION] 只是建库时的中文快照，
 * 展示一律走 `getDisplayName` / `getDisplayDescription`。
 */
object BuiltinGoalTemplate {
    const val ICON = "flag"
    const val NAME = "目标"
    const val CATEGORY = "计划"
    const val DESCRIPTION = "设定目标，记录完成进度"
    const val COLOR = "#E44444"
    const val SORT_ORDER = 19
    const val AVAILABLE_LAYOUTS = """["card","list"]"""
    const val STATUS_FLOW =
        """{"detailMode":"ITEM","defaultStatus":"ACTIVE","statuses":[""" +
            """{"key":"ACTIVE","label":"进行中"},{"key":"COMPLETED","label":"已完成"},{"key":"ARCHIVED","label":"已归档"}]}"""
    const val LINK_CONFIG = """{"allowCrossLink":false,"targetTypes":[]}"""

    /**
     * 字段配置。
     *
     * - `target` 目标值（进度分母）、`current` 当前值（进度分子，`progressTargetKey` → target）；
     * - `initial` 起始值：填了且 **大于目标值** 时进度按递减算（见 `FieldConfig.progressStartKey`）；
     * - `unitText` 单位：逐条目标单位不同（本 / 公里 / 支 / 元），故不能用字段级静态 `unit`；
     * - `deadline` 截止日期：只进 fieldsData；经仓库保存时会被通用日期镜像同步进执行列
     *   `dueDate`（与「订阅」等带 DATE 字段的模板同口径：填了才进今日/日历，没填不进）。
     */
    const val FIELDS_CONFIG =
        """[{"key":"target","label":"目标值","type":"NUMBER","required":true,"showInCard":true,"sortOrder":1},""" +
            """{"key":"current","label":"当前值","type":"NUMBER","required":false,"showInCard":true,""" +
            """"showAsProgress":true,"progressTargetKey":"target","progressStartKey":"initial","sortOrder":2},""" +
            """{"key":"initial","label":"起始值","type":"NUMBER","required":false,""" +
            """"placeholder":"递减目标填起始读数","showInCard":false,"sortOrder":3},""" +
            """{"key":"unitText","label":"单位","type":"SHORT_TEXT","required":false,"showInCard":true,"sortOrder":4},""" +
            """{"key":"deadline","label":"截止日期","type":"DATE","required":false,"showInCard":true,"sortOrder":5}]"""
}
