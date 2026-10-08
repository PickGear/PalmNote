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
 * 物品上「到期日」的两种：质保与保质期。界面与提醒都取更紧迫的那一个
 * （见 `Asset.nearestExpiryDate` / `Asset.nearestExpiryKind`），所以需要知道它是哪一种。
 */
enum class ExpiryKind { WARRANTY, SHELF_LIFE }

/**
 * 物品到期提醒的落点（窗口见 `expiryReminderKind`）。
 * 管的是**物品上更紧迫的那个到期日**（质保或保质期），不是只管保质期——界面和提醒必须说同一件事。
 */
enum class ExpiryReminderKind {
    /** 今天到期。 */
    TODAY,

    /** 还有 N 天（N ≤ 提前天数）。 */
    SOON,

    /** 已过期 N 天（N ≤ 提前天数）。 */
    EXPIRED
}

/**
 * 保质期时长的单位（`Asset.shelfLifeDurationUnit`）。
 * 列可空，所以这里是 `fromOrNull` 而不是像其它枚举那样给兜底值——「没填」和「填了天」是两回事。
 */
enum class ShelfLifeUnit(val value: String) {
    DAY("DAY"),
    MONTH("MONTH"),
    YEAR("YEAR");

    companion object {
        fun fromOrNull(s: String?): ShelfLifeUnit? = entries.firstOrNull { it.value == s }
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

