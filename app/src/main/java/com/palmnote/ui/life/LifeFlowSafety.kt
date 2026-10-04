package com.palmnote.ui.life

import com.palmnote.domain.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * 生活页数据流的统一**失败兜底**。
 *
 * ## 为什么需要
 *
 * 模块内每个 `stateIn` 都只给了初始值，且**没有一个** `.catch`（接手会话实测）。
 * 上游一旦抛异常（库打不开、迁移失败、DataStore 异常、字段 JSON 结构意外），
 * 共享协程就直接结束 —— `StateFlow` 会**永远停在初始值**：
 * 详情页停在 `Loading` 无限转圈，其余页面停在空态。
 * 用户看到的是「就是没数据」或「一直在加载」，既没有提示，日志里也没有痕迹。
 *
 * ## 这里做什么
 *
 * 记一条错误日志 + 发出一个**明确的** [fallback]，把「流已经死了」变成
 * 「页面进入一个确定的、代码里写明的状态」。
 *
 * 注意这不等于「假装成功」：`fallback` 由调用方选择，详情页传的是错误态，
 * 列表页传的是空列表。想区分「真没数据」与「读取失败」的页面，
 * 应该传一个错误态而不是空值。
 *
 * @param tag 日志里标识是哪个流（页面.用途），便于定位。
 * @param fallback 失败时发出的值；必须是「页面能正常渲染」的那个。
 */
internal fun <T> Flow<T>.catchLife(tag: String, fallback: T): Flow<T> = catch { e ->
    AppLogger.e("LifeFlow", "$tag 数据流失败，已回退到兜底状态", e)
    emit(fallback)
}
