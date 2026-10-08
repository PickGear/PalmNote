package com.palmnote.ui.asset

import com.palmnote.domain.model.ShelfLifeUnit
import com.palmnote.domain.util.shelfLifeExpiryFrom
import com.palmnote.domain.util.shelfLifeExpiryOrNull

/**
 * 保质期两种录法的表单逻辑（表单状态 → 落库那四列的映射）。
 *
 * 单独一个文件而不是塞在 `AssetViewModel` 里：这是表单的换算规则，跟 ViewModel 的职责无关，
 * 而且它自带单测（`AddAssetFormShelfLifeTest`），独立出来更好找。
 */

/** 保质期录法：`DATE` 直接填到期日；`PERIOD` 填生产日期 + 时长，到期日由两者算出。 */
const val SHELF_LIFE_MODE_DATE = "DATE"
const val SHELF_LIFE_MODE_PERIOD = "PERIOD"

/** 「保质期限」那组输入的默认单位（月份最常见）。 */
const val SHELF_LIFE_DURATION_DEFAULT_UNIT = "MONTH"

/** 落库用的保质期四列，由 [resolveShelfLife] 从表单推出来。 */
data class ResolvedShelfLife(
    val expireDate: Long?,
    val producedDate: Long? = null,
    val durationValue: Int? = null,
    val durationUnit: String? = null
)

/**
 * 表单 → 保质期四列。
 *
 * - 「到期日」录法：只落到期日。
 * - 「保质期限」录法：期限填全了就落到期日 + 生产日期 + 时长；
 *   只切到「保质期限」而没填东西时，回退到用户在「到期日」里填过的那份，不把日期丢掉。
 */
fun AddAssetFormState.resolveShelfLife(): ResolvedShelfLife {
    if (shelfLifeMode != SHELF_LIFE_MODE_PERIOD) return ResolvedShelfLife(shelfLifeExpireDate)

    val produced = shelfLifeProducedDate
    val amount = shelfLifeDurationValue.toIntOrNull()
    val unit = ShelfLifeUnit.fromOrNull(shelfLifeDurationUnit)
    if (produced == null || amount == null || unit == null) return ResolvedShelfLife(shelfLifeExpireDate)

    // 三项齐了才落这组输入；算不出到期日（时长为 0 之类）同样回退到已填的日期
    val expiry = shelfLifeExpiryFrom(produced, amount, unit) ?: return ResolvedShelfLife(shelfLifeExpireDate)
    return ResolvedShelfLife(expiry, produced, amount, unit.value)
}

/**
 * 按「生产日期 + 时长」录时，顺手把算出来的到期日同步进 [AddAssetFormState.shelfLifeExpireDate]。
 *
 * 必须同步，否则用户填完期限、切回「到期日」看到的是空日期，此时保存会把保质期整个丢掉。
 * 到期日始终是唯一被展示与被存的那份值，期限只是算出它的输入。
 * 三项缺一项就把到期日清空，不留半截状态。
 *
 * ⚠️ **"三项缺一项就清空"同时是当前唯一能删掉保质期的路径**：日期那格只有「打开日历」一个图标，
 * 选择弹窗也只有确定/取消（`DatePickerField`，全 App 共用），已经选上的日期没法取消。
 * 想删掉保质期，用户只能切到「按期限录」把时长数字删掉再保存。
 * 所以**别**顺手改成"算不出来就保留原来的日期"——那会让保质期再也删不掉；
 * 真要改，先给日期格加一个清除入口。
 */
fun AddAssetFormState.withShelfLifePeriod(
    producedDate: Long?,
    durationValue: String,
    durationUnit: String
): AddAssetFormState = copy(
    shelfLifeProducedDate = producedDate,
    shelfLifeDurationValue = durationValue,
    shelfLifeDurationUnit = durationUnit,
    shelfLifeExpireDate = shelfLifeExpiryOrNull(
        producedDate,
        durationValue.toIntOrNull(),
        ShelfLifeUnit.fromOrNull(durationUnit)
    )
)

/**
 * 直接选到期日：日期才是准的，所以把「按期限录」那组输入清掉（免得切回期限时旧时长又把它顶回去）。
 *
 * ⚠️ 别拿 [withShelfLifePeriod] 来干这件事：它内部会**重算**到期日，用 null 的生产日期去算
 * 得到 null，会把刚选的日期当场抹掉——「选不了日期」就是这么来的。
 */
fun AddAssetFormState.withAbsoluteShelfLife(date: Long): AddAssetFormState = copy(
    shelfLifeExpireDate = date,
    shelfLifeProducedDate = null,
    shelfLifeDurationValue = "",
    shelfLifeDurationUnit = SHELF_LIFE_DURATION_DEFAULT_UNIT
)
