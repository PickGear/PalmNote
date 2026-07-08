package com.palmnote.domain.util

/**
 * A typed result wrapper that replaces raw [kotlin.Result] or nullable returns
 * for operations that can fail with an [AppException].
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val exception: AppException) : AppResult<Nothing>()
}

/** Extract the data on success, or null on error. */
fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

/** Extract the data on success, or [default] on error. */
fun <T> AppResult<T>.getOrElse(default: T): T = getOrNull() ?: default
