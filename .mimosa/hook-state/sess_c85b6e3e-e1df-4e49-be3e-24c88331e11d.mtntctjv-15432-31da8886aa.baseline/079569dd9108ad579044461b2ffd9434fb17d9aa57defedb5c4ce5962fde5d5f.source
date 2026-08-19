package com.palmnote.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryClassifierTest {

    @Test
    fun `food keywords map to 餐饮`() {
        for (text in listOf("美团外卖", "星巴克咖啡", "食堂午餐", "奶茶", "火锅店", "早餐", "淘宝夜宵")) {
            assertEquals("餐饮", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `transport keywords map to 交通`() {
        for (text in listOf("滴滴打车", "地铁", "公交车", "加油站", "高铁票", "机票", "停车费", "过路费", "共享单车")) {
            assertEquals("交通", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `shopping keywords map to 购物`() {
        for (text in listOf("超市", "淘宝", "京东", "拼多多", "百货商场", "便利店", "网购", "快递")) {
            assertEquals("购物", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `travel keywords map to 旅游`() {
        for (text in listOf("酒店", "住宿", "宾馆", "旅游", "景点", "民宿", "景区门票")) {
            assertEquals("旅游", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `medical keywords map to 医疗`() {
        for (text in listOf("医院", "药店", "诊所", "体检", "挂号", "看病", "药房")) {
            assertEquals("医疗", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `education keywords map to 教育`() {
        for (text in listOf("培训班", "课程", "书店", "学校", "学费", "网课", "文具")) {
            assertEquals("教育", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `housing keywords map to 居住`() {
        for (text in listOf("水电费", "燃气费", "物业", "房租", "宽带", "话费", "房贷", "暖气")) {
            assertEquals("居住", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `social keywords map to 人情`() {
        for (text in listOf("份子钱", "随礼", "送礼", "请客", "婚礼", "生日聚会")) {
            assertEquals("人情", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `tobacco alcohol keywords map to 烟酒`() {
        for (text in listOf("香烟", "白酒", "啤酒", "烟草")) {
            assertEquals("烟酒", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `fitness keywords map to 运动`() {
        for (text in listOf("健身房", "瑜伽", "游泳", "运动", "体育馆", "跑步")) {
            assertEquals("运动", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `beauty keywords map to 美容`() {
        for (text in listOf("理发", "美容", "护肤", "化妆品", "面膜", "染发", "美发", "口红")) {
            assertEquals("美容", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `pet keywords map to 宠物`() {
        for (text in listOf("宠物", "猫粮", "狗粮", "兽医")) {
            assertEquals("宠物", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `宠物医院 matches 医疗 before 宠物 due to ordering`() {
        assertEquals("医疗", CategoryClassifier.guessCategory("宠物医院"))
    }

    @Test
    fun `repair keywords map to 维修`() {
        for (text in listOf("维修", "修理", "换件")) {
            assertEquals("维修", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `donation keywords map to 捐赠`() {
        for (text in listOf("捐赠", "捐款", "公益", "慈善")) {
            assertEquals("捐赠", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `entertainment keywords map to 娱乐`() {
        for (text in listOf("电影票", "游戏", "KTV", "演出", "门票", "游乐园", "娱乐")) {
            assertEquals("娱乐", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `electronics keywords map to 数码`() {
        for (text in listOf("数码", "手机", "电脑", "平板", "耳机", "充电器")) {
            assertEquals("数码", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `home keywords map to 家居`() {
        for (text in listOf("家居", "家具", "装修", "家电", "空调", "冰箱")) {
            assertEquals("家居", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `baby keywords map to 母婴`() {
        for (text in listOf("母婴", "奶粉", "尿不湿", "玩具", "婴儿", "儿童")) {
            assertEquals("母婴", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `insurance keywords map to 保险`() {
        for (text in listOf("保险", "车险", "人寿", "医保", "社保")) {
            assertEquals("保险", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `investment keywords map to 投资`() {
        for (text in listOf("投资", "基金", "股票", "理财", "期货", "债券")) {
            assertEquals("投资", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `communication keywords map to 通讯`() {
        for (text in listOf("通讯", "流量")) {
            assertEquals("通讯", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `电话费 matches 居住 before 通讯 due to ordering`() {
        assertEquals("居住", CategoryClassifier.guessCategory("电话费"))
    }

    @Test
    fun `housekeeping keywords map to 家政`() {
        for (text in listOf("家政", "保洁")) {
            assertEquals("家政", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `fine keywords map to 罚款`() {
        for (text in listOf("罚款", "滞纳金")) {
            assertEquals("罚款", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `fee keywords map to 手续费`() {
        for (text in listOf("手续费", "服务费")) {
            assertEquals("手续费", CategoryClassifier.guessCategory(text))
        }
    }

    @Test
    fun `unknown text maps to 其他`() {
        assertEquals("其他", CategoryClassifier.guessCategory(""))
        assertEquals("其他", CategoryClassifier.guessCategory("unknown merchant"))
        assertEquals("其他", CategoryClassifier.guessCategory("xyz"))
    }

    @Test
    fun `empty and blank text map to 其他`() {
        assertEquals("其他", CategoryClassifier.guessCategory(""))
        assertEquals("其他", CategoryClassifier.guessCategory("   "))
    }
}
