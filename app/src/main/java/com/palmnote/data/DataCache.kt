package com.palmnote.data

object DataCache {
    private val cache = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = cache[key] as? T

    fun <T> set(key: String, value: T) { cache[key] = value as Any }
}
