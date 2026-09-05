package com.palmnote.domain.model

/**
 * 账单类型
 */
enum class BillType(val value: String) {
    EXPENSE("EXPENSE"),
    INCOME("INCOME"),
    TRANSFER("TRANSFER");

    companion object {
        fun from(s: String): BillType = entries.firstOrNull { it.value == s } ?: EXPENSE
    }
}

/**
 * 物品状态（Asset 专用）
 */
enum class AssetStatus(val value: String) {
    HELD("HELD"),
    AWAY("AWAY"),
    REMOVED("REMOVED");

    companion object {
        fun from(s: String): AssetStatus = entries.firstOrNull { it.value == s } ?: HELD
    }
}

/**
 * 支付方式
 */
enum class PaymentMethod(val value: String) {
    CASH("CASH"),
    WECHAT("WECHAT"),
    ALIPAY("ALIPAY"),
    CARD("CARD"),
    BANK_TRANSFER("BANK_TRANSFER"),
    OTHER("OTHER");

    companion object {
        fun from(s: String): PaymentMethod = entries.firstOrNull { it.value == s } ?: OTHER
    }
}

/**
 * 周期性频率
 */
enum class RecurringFrequency(val value: String) {
    DAILY("DAILY"),
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY"),
    YEARLY("YEARLY");

    companion object {
        fun from(s: String): RecurringFrequency = entries.firstOrNull { it.value == s } ?: MONTHLY
    }
}

/**
 * 自动锁定模式
 */
enum class AutoLockMode(val value: String) {
    IMMEDIATE("immediate"),
    SYSTEM("system"),
    TIMEOUT("timeout");

    companion object {
        fun from(s: String): AutoLockMode = entries.firstOrNull { it.value == s } ?: SYSTEM
    }
}

