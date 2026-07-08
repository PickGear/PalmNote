package com.palmnote.ui.bills

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import com.palmnote.R
import com.palmnote.ui.components.CategoryItem
import com.palmnote.ui.theme.*

// NOTE: CategoryItem.name is an internal key/identifier used for DB storage and UI selection.
// It is NOT display text. Use getLocalizedCategoryName() to get the localized display string.
val expenseCategoryItems = listOf(
    // 餐饮食品
    CategoryItem("餐饮", Icons.Outlined.Restaurant, DeepOrange),
    CategoryItem("零食", Icons.Outlined.Cookie, DeepOrange),
    CategoryItem("饮品", Icons.Outlined.LocalCafe, Brown),
    // 交通出行
    CategoryItem("交通", Icons.Outlined.Train, InfoBlue),
    // 购物消费
    CategoryItem("购物", Icons.Outlined.ShoppingCart, ModuleLife),
    CategoryItem("服饰", Icons.Outlined.Checkroom, Amber),
    CategoryItem("数码", Icons.Outlined.Devices, InfoBlue),
    CategoryItem("二手", Icons.Outlined.SwapHoriz, StatusRetired),
    // 居住生活
    CategoryItem("居住", Icons.Outlined.Home, StatusActive),
    CategoryItem("家居", Icons.Outlined.Chair, Amber),
    CategoryItem("租金", Icons.Outlined.HouseSiding, StatusActive),
    // 娱乐休闲
    CategoryItem("娱乐", Icons.Outlined.SportsEsports, Purple),
    CategoryItem("旅游", Icons.Outlined.Flight, StatusActive),
    CategoryItem("运动", Icons.Outlined.SportsBasketball, StatusActive),
    // 医疗健康
    CategoryItem("医疗", Icons.Outlined.LocalHospital, ErrorLight),
    CategoryItem("健身", Icons.Outlined.FitnessCenter, StatusActive),
    CategoryItem("美容", Icons.Outlined.Face, ModuleLife),
    // 教育学习
    CategoryItem("教育", Icons.Outlined.School, InfoBlue),
    CategoryItem("文具", Icons.Outlined.Edit, Brown),
    // 社交人情
    CategoryItem("社交", Icons.Outlined.Groups, Purple),
    CategoryItem("人情", Icons.Outlined.Redeem, DeepOrange),
    CategoryItem("红包", Icons.Outlined.CardGiftcard, ErrorLight),
    CategoryItem("赠与", Icons.Outlined.VolunteerActivism, Purple),
    // 生活服务
    CategoryItem("通讯", Icons.Outlined.PhoneAndroid, ModuleSettings),
    CategoryItem("家政", Icons.Outlined.CleaningServices, ModuleSettings),
    CategoryItem("快递", Icons.Outlined.LocalShipping, ModuleSettings),
    CategoryItem("维修", Icons.Outlined.Build, Brown),
    // 金融保险
    CategoryItem("投资", Icons.AutoMirrored.Outlined.TrendingUp, InfoBlue),
    CategoryItem("股票", Icons.AutoMirrored.Outlined.ShowChart, ErrorLight),
    CategoryItem("理财", Icons.Outlined.Savings, InfoBlue),
    CategoryItem("保险", Icons.Outlined.HealthAndSafety, ModuleSettings),
    // 其他
    CategoryItem("宠物", Icons.Outlined.Pets, StatusActive),
    CategoryItem("母婴", Icons.Outlined.ChildCare, ModuleLife),
    CategoryItem("烟酒", Icons.Outlined.LocalBar, Brown),
    CategoryItem("捐赠", Icons.Outlined.VolunteerActivism, Purple),
    CategoryItem("罚款", Icons.Outlined.Gavel, ErrorLight),
    CategoryItem("手续费", Icons.Outlined.Receipt, ModuleSettings),
    CategoryItem("其他", Icons.Outlined.MoreHoriz, StatusRetired)
)

val incomeCategoryItems = listOf(
    // 工作收入
    CategoryItem("工资", Icons.Outlined.AccountBalance, StatusActive),
    CategoryItem("奖金", Icons.Outlined.EmojiEvents, DeepOrange),
    CategoryItem("兼职", Icons.Outlined.Work, ModuleSettings),
    CategoryItem("副业", Icons.Outlined.Workspaces, ModuleSettings),
    CategoryItem("报销", Icons.Outlined.Receipt, StatusActive),
    // 投资理财
    CategoryItem("投资", Icons.AutoMirrored.Outlined.TrendingUp, InfoBlue),
    CategoryItem("股票", Icons.AutoMirrored.Outlined.ShowChart, ErrorLight),
    CategoryItem("理财", Icons.Outlined.Savings, InfoBlue),
    CategoryItem("分红", Icons.Outlined.EmojiEvents, DeepOrange),
    CategoryItem("利息", Icons.Outlined.Percent, Amber),
    // 资产收入
    CategoryItem("租金", Icons.Outlined.HouseSiding, StatusActive),
    CategoryItem("二手", Icons.Outlined.SwapHoriz, StatusRetired),
    // 人情往来
    CategoryItem("红包", Icons.Outlined.CardGiftcard, ErrorLight),
    CategoryItem("赠与", Icons.Outlined.Redeem, Purple),
    CategoryItem("人情", Icons.Outlined.Redeem, DeepOrange),
    // 其他收入
    CategoryItem("退款", Icons.AutoMirrored.Outlined.Undo, Purple),
    CategoryItem("中奖", Icons.Outlined.EmojiEvents, Amber),
    CategoryItem("保险理赔", Icons.Outlined.HealthAndSafety, StatusActive),
    CategoryItem("继承", Icons.Outlined.AccountBalance, StatusActive),
    CategoryItem("其他", Icons.Outlined.MoreHoriz, StatusRetired)
)

fun getLocalizedCategoryName(name: String): Int = when (name) {
    "餐饮" -> R.string.category_food
    "零食" -> R.string.category_snacks
    "饮品" -> R.string.category_drinks
    "交通" -> R.string.category_transport
    "购物" -> R.string.category_shopping
    "服饰" -> R.string.category_clothing
    "数码" -> R.string.category_digital
    "二手" -> R.string.category_used
    "居住" -> R.string.category_housing
    "家居" -> R.string.category_furniture
    "租金" -> R.string.category_rent
    "娱乐" -> R.string.category_entertainment
    "旅游" -> R.string.category_travel
    "运动" -> R.string.category_sports
    "医疗" -> R.string.category_medical
    "健身" -> R.string.category_fitness
    "美容" -> R.string.category_beauty
    "教育" -> R.string.category_education
    "文具" -> R.string.category_stationery
    "社交" -> R.string.category_social
    "人情" -> R.string.category_favors
    "红包" -> R.string.category_red_packet
    "赠与" -> R.string.category_gift
    "通讯" -> R.string.category_communication
    "家政" -> R.string.category_housekeeping
    "快递" -> R.string.category_delivery
    "维修" -> R.string.category_repair
    "投资" -> R.string.category_investment
    "股票" -> R.string.category_stocks
    "理财" -> R.string.category_finance
    "保险" -> R.string.category_insurance
    "宠物" -> R.string.category_pet
    "母婴" -> R.string.category_baby
    "烟酒" -> R.string.category_tobacco_alcohol
    "捐赠" -> R.string.category_donation
    "罚款" -> R.string.category_fine
    "手续费" -> R.string.category_fee
    "其他" -> R.string.category_other
    "工资" -> R.string.category_salary
    "奖金" -> R.string.category_bonus
    "兼职" -> R.string.category_part_time
    "副业" -> R.string.category_side_job
    "报销" -> R.string.category_reimbursement
    "分红" -> R.string.category_dividend
    "利息" -> R.string.category_interest
    "退款" -> R.string.category_refund
    "中奖" -> R.string.category_lottery
    "保险理赔" -> R.string.category_claim
    "继承" -> R.string.category_inheritance
    else -> R.string.category_other
}

fun getLocalizedPaymentMethod(method: String): Int = when (method) {
    "CASH" -> R.string.payment_cash
    "WECHAT" -> R.string.payment_wechat
    "ALIPAY" -> R.string.payment_alipay
    "CARD" -> R.string.payment_card
    "BANK_TRANSFER" -> R.string.payment_bank_transfer
    "OTHER" -> R.string.payment_other
    else -> R.string.payment_other
}
