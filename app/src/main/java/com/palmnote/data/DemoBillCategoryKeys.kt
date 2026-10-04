package com.palmnote.data

/**
 * 演示账单引用的**内置分类键**（中文常量，与 `BillCategoryData` 里 `CategoryItem.name` 一致）。
 *
 * 为什么单独放一份常量：账单分类在库里存的是**内部键**而不是显示名
 * （存显示名会让英文环境下按键查图标/颜色全部落空，真机截图里分类图标变红叉即此）。
 * 键是**数据**、不是文案 —— 由 `DemoTexts` 翻译它反而是错的，
 * 所以把它们集中在这里，让 `DemoWealthData` 里出现的每个中文串都确实是「要翻译的展示文案」。
 *
 * 键与内置分类表的一致性由 `DemoDataIntegrityTest` 守着。
 */
internal object DemoBillCategoryKeys {
    const val HOUSING = "居住"
    const val FOOD = "餐饮"
    const val SHOPPING = "购物"
    const val TRANSPORT = "交通"
    const val ENTERTAINMENT = "娱乐"
    const val MEDICAL = "医疗"
    const val EDUCATION = "教育"
    const val COMMUNICATION = "通讯"
    const val SALARY = "工资"
    const val INVESTMENT = "投资"
    const val REFUND = "退款"
}
