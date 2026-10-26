package com.palmnote

import android.os.Process
import android.os.SystemClock

/**
 * 冷启动「首页数据就绪」信号，**只服务于系统启动画面的保持条件**。
 *
 * 为什么需要这层全局状态：保持条件在 [MainActivity] 里求值，而首页数据由
 * `DashboardViewModel` 加载，两者隔着 NavHost。用一个进程内单例传递即可：
 * 不落盘、不参与任何业务逻辑，重启进程自然归零。
 *
 * 三条放行规则（任一满足就不再保持启动画面）：
 * 1. 首页首帧数据已到（[onDashboardReady]）——含数据源异常：异常也要放行，
 *    否则用户会盯着启动画面看不到任何错误；
 * 2. 明确不需要等（[disableHold]）——引导页、锁屏、起始页不是首页；
 * 3. 超过 [MAX_HOLD_MS] —— 兜底，绝不让启动画面无限停留（官方口径：只为首屏
 *    必需数据保持，且必须有超时上限）。超时后落到首页骨架，不会白屏。
 */
object AppStartupState {

    /**
     * 启动画面最长保持时长，**从进程启动算起**（不是从本类初始化算）。
     *
     * 口径（2026-10-18 定）：**开屏页该留，但不该替数据加载的时间站岗**。
     * - 数据在窗口内到齐 → 直接进完整首页，观感"秒开"；
     * - 超时 → 放行到首页骨架，让用户看到"在加载"，而不是继续盯着静止的品牌图标。
     *
     * 为什么是 1000ms 而不是更短：启动窗口从**进程启动前**就显示，Activity 首帧本身要
     * 230~250ms（真机实测），再加上首页数据（raw key 后预期 300~500ms），常态总和 550~750ms。
     * 若上限设在 600ms，会出现"多数情况刚好被截断"的最差组合——既没省掉骨架，又白等了一段。
     * 1000ms 覆盖常态、仍远低于用户忍耐阈值，同时保留兜底（绝不允许无限等）。
     */
    private const val MAX_HOLD_MS = 1_000L

    /**
     * 进程启动时刻（elapsedRealtime 时基，API 24+）。
     * 用它而不是本类的初始化时刻：启动窗口在进程起来之前就已绘制，从进程启动算才与
     * 用户看到的停留时长一致；热启动时该值远早于现在，会立即放行（热启动本就不需要开屏）。
     */
    private val processStartedAt = Process.getStartElapsedRealtime()

    @Volatile
    private var dashboardReady = false

    @Volatile
    private var holdDisabled = false

    /** 首页首帧数据已到（数据源异常时也调用）。 */
    fun onDashboardReady() {
        dashboardReady = true
    }

    /** 本进程不需要等首页数据（引导页 / 锁屏 / 起始页不是首页）。 */
    fun disableHold() {
        holdDisabled = true
    }

    /** true = 启动画面继续显示；false = 放行到应用界面。 */
    fun shouldHoldSplash(): Boolean {
        if (holdDisabled || dashboardReady) return false
        // 用户把默认启动页设成了别的 tab 时，等首页数据没有意义。
        // cachedStartPage 在 Application 的 IO 协程里异步刷新，未读完时取默认值 "dashboard"，
        // 读完若发现是别的页会立即放行。
        if (PalmNoteApp.cachedStartPage != "dashboard") return false
        return SystemClock.elapsedRealtime() - processStartedAt < MAX_HOLD_MS
    }
}
