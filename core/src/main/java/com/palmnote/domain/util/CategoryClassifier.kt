package com.palmnote.domain.util

/**
 * 共享的账单分类猜测逻辑，供 OCR 解析器和 CSV 导入器复用。
 */
object CategoryClassifier {

    fun guessCategory(text: String): String {
        return when {
            text.containsAny(
                "餐", "食", "吃", "外卖", "咖啡", "茶", "面包", "小吃",
                "美团", "饿了么", "奶茶", "火锅", "烧烤", "面馆", "食堂",
                "快餐", "夜宵", "早餐", "午餐", "晚餐", "聚餐"
            ) -> "餐饮"

            text.containsAny(
                "打车", "地铁", "公交", "加油", "高铁", "机票", "停车",
                "出租", "滴滴", "过路费", "洗车", "保养", "高速",
                "火车票", "共享单车"
            ) -> "交通"

            text.containsAny(
                "超市", "购物", "商场", "便利店", "百货", "淘宝", "京东",
                "拼多多", "天猫", "日用", "生活用品", "生活日用品", "日化",
                "网购", "快递"
            ) -> "购物"

            text.containsAny("酒店", "住宿", "宾馆", "旅游", "景点", "民宿", "景区") -> "旅游"

            text.containsAny("医院", "药店", "药", "诊所", "体检", "挂号", "看病", "药房") -> "医疗"

            text.containsAny("教育", "培训", "课程", "书店", "学校", "学费", "网课", "书", "文具") -> "教育"

            text.containsAny(
                "水电", "燃气", "物业", "房租", "宽带", "话费", "房贷", "租房",
                "暖气"
            ) -> "居住"

            text.containsAny(
                "人情", "份子钱", "随礼", "送礼", "请客", "婚", "生日",
                "满月", "乔迁", "聚会"
            ) -> "人情"

            text.containsAny("烟", "酒", "香烟", "白酒", "啤酒", "烟草") -> "烟酒"

            text.containsAny("健身", "瑜伽", "游泳", "运动", "体育馆", "跑步") -> "运动"

            text.containsAny(
                "理发", "美容", "护肤", "化妆品", "面膜", "染发", "美发",
                "剪发", "彩妆", "口红"
            ) -> "美容"

            text.containsAny("宠物", "猫粮", "狗粮", "宠物医院", "兽医") -> "宠物"

            text.containsAny("维修", "修理", "换件") -> "维修"

            text.containsAny("捐赠", "捐款", "公益", "慈善") -> "捐赠"

            text.containsAny("电影", "游戏", "KTV", "演出", "门票", "乐园", "娱乐") -> "娱乐"

            text.containsAny("数码", "手机", "电脑", "平板", "耳机", "充电") -> "数码"

            text.containsAny("家居", "家具", "装修", "家电", "空调", "冰箱") -> "家居"

            text.containsAny("母婴", "奶粉", "尿不湿", "玩具", "婴儿", "儿童") -> "母婴"

            text.containsAny("保险", "车险", "人寿", "医保", "社保") -> "保险"

            text.containsAny("投资", "基金", "股票", "理财", "期货", "债券") -> "投资"

            text.containsAny("通讯", "电话费", "流量") -> "通讯"

            text.containsAny("家政", "保洁") -> "家政"

            text.containsAny("罚款", "滞纳金") -> "罚款"

            text.containsAny("手续费", "服务费") -> "手续费"

            else -> "其他"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }
}
