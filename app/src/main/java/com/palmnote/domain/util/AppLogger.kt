package com.palmnote.domain.util

import android.util.Log
import com.palmnote.BuildConfig

/**
 * 统一日志工具。
 * - Debug 构建：输出到 Logcat
 * - Release 构建：仅输出 ERROR 级别（可扩展为写本地文件）
 */
object AppLogger {

    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.w(tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable?) {
        if (BuildConfig.DEBUG) Log.w(tag, msg, t)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
    }
}
