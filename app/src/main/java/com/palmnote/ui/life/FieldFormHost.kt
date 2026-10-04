package com.palmnote.ui.life

/**
 * 表单控件的「宿主」（记录填写页与模板预览的**共同契约**）。
 *
 * ## 为什么需要它
 *
 * 填写控件此前直接依赖 `LifeCreateRecordViewModel` —— 那是**页面级**的东西，
 * 于是「预览」里没有 VM 可传，只能自己再画一套示意控件，迟早与真实表单漂移
 * （卡片态预览当初踩的就是这个坑：自绘一套 → 与真实卡不一致 → 退化成一句「N 个字段」）。
 *
 * 把控件对 VM 的依赖收成**两个写入动作**之后：
 * - 填写页传真实 ViewModel（写入落到 `_state`）；
 * - 模板预览传一个**写入被吞掉**的只读宿主（`LifePreviewFieldKit.PreviewFormHost`）。
 *
 * 两边共用**同一批控件、同一套分组、同一套字段筛选**，所以「填写」态预览是真控件。
 *
 * 读取不走这里：调用方把 [CreateRecordUiState] 直接传进控件（它是不可变快照，
 * 组合本来就随状态流刷新）。
 */
internal interface FieldFormHost {
    /** 写一个字段的表单字符串值（编码口径见 `LifeFieldCodec`）。 */
    fun updateValue(key: String, value: String)

    /** 读入 GPX/KML 轨迹并写入 MAP 字段载荷。 */
    fun importTrack(context: android.content.Context, uri: android.net.Uri, fieldKey: String)
}
