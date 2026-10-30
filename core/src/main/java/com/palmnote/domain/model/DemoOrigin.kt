package com.palmnote.domain.model

/**
 * 一行数据「在演示模式里算什么」。
 *
 * - [NONE]：真实数据，正常读写。
 * - [SEED]：播种的示例行——可看、算进统计，**不可改**；关闭演示或重置示例时删除。
 * - [USER]：演示期用户自己记的——可见可改；关闭演示时**迁回真实数据**，不丢。
 *
 * 与 [com.palmnote.data.db.entity.Bill.isDemo] 的关系：`isDemo` 表示"属于演示数据集"，
 * 本枚举进一步区分"数据集里的哪一类"，从而让关闭演示时能既清示例、又留用户内容。
 */
enum class DemoOrigin {
    NONE,
    SEED,
    USER
}
