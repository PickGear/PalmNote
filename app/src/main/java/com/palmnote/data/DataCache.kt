package com.palmnote.data

import java.util.Collections
import java.util.LinkedHashMap

object DataCache {
    private const val MAX_SIZE = 10

    // LinkedHashMap with access-order for LRU, wrapped in synchronizedMap for thread safety
    private val cache: MutableMap<String, Any> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Any>(MAX_SIZE + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?): Boolean {
                return size > MAX_SIZE
            }
        }
    )

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = cache[key] as? T

    fun <T> set(key: String, value: T) {
        cache[key] = value as Any
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.clear()
    }
}
