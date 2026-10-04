package com.palmnote.ui.life

import android.app.Application
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [catchLife] 的契约：上游抛异常时**不能**让流直接结束（那会让 `stateIn` 永远停在初始值，
 * 页面就挂在转圈/空态上），而是记日志 + 发出调用方指定的兜底值。
 *
 * 用 Robolectric 是因为 [com.palmnote.domain.util.AppLogger] 走 `android.util.Log`，
 * 纯 JVM 测试里会抛 "not mocked"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LifeFlowSafetyTest {

    @Test
    fun `emits fallback when upstream throws immediately`() = runBlocking {
        val values = flow<Int> { error("boom") }
            .catchLife("test.throw", -1)
            .toList()

        assertEquals(listOf(-1), values)
    }

    @Test
    fun `passes values through when upstream succeeds`() = runBlocking {
        val values = flow {
            emit(1)
            emit(2)
        }.catchLife("test.ok", -1).toList()

        assertEquals(listOf(1, 2), values)
    }

    /** 已经发出去的值不能因为后面的失败被吞掉——兜底是「追加」而不是「替换」。 */
    @Test
    fun `keeps values emitted before the failure and then appends the fallback`() = runBlocking {
        val values = flow {
            emit(1)
            error("boom")
        }.catchLife("test.partial", -1).toList()

        assertEquals(listOf(1, -1), values)
    }
}
