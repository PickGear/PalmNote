package com.palmnote.domain.util

/**
 * Sealed hierarchy of application-level exceptions.
 * Each subtype carries a human-readable [detail] message and an optional [cause].
 */
sealed class AppException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    data class Database(
        val detail: String
    ) : AppException(detail)

    data class Serialization(
        val detail: String,
        override val cause: Throwable? = null
    ) : AppException(detail, cause)

    data class Io(
        val detail: String,
        override val cause: Throwable? = null
    ) : AppException(detail, cause)

    data class NotFound(
        val detail: String
    ) : AppException(detail)

    data class Validation(
        val detail: String
    ) : AppException(detail)

    data class Unknown(
        val detail: String,
        override val cause: Throwable? = null
    ) : AppException(detail, cause)
}
