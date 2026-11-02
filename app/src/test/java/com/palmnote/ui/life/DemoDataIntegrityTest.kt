package com.palmnote.ui.life

import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.demoAssetSeeds
import com.palmnote.data.demoBillSeeds
import com.palmnote.data.resolveDate
import com.palmnote.ui.asset.assetCategoryItems
import com.palmnote.ui.bills.expenseCategoryItems
import com.palmnote.ui.bills.incomeCategoryItems
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **演示数据完整性审计**（演示种子改为类型化对象图后的守卫）。
 *
 * 1. 种子引用的示例图必须真实存在于 `assets/demo/`，且目录里没有孤儿图
 *    （v13 翻过车：图片引用断了、13 件全是无图占位，而随包图白白占 APK 体积）；
 * 2. 演示账单 / 物品分类必须是 app 内置分类键（否则落到未知分类的兜底色）；
 * 3. 结构性覆盖：转账 / 可报销 / 收入 / 双图 / 两种保质期录法 / 保修至少各一，
 *    演示模式的每块界面都要有戏可演。
 */
class DemoDataIntegrityTest {

    private val assetsDir = File("src/main/assets/demo")

    @Test
    fun `demo seed images must exist and leave no orphans`() {
        val referenced = demoAssetSeeds.flatMap { it.images }.toSet()
        assertTrue("演示种子没有引用任何示例图", referenced.isNotEmpty())
        referenced.forEach { name ->
            assertTrue("种子引用的示例图不存在：$name", File(assetsDir, name).isFile)
        }
        val orphans = assetsDir.listFiles()
            ?.filter { it.isFile && it.name !in referenced }
            ?.map { it.name }
            ?: emptyList()
        assertTrue("assets/demo 存在未被种子引用的孤儿文件：$orphans", orphans.isEmpty())
    }

    @Test
    fun `wealth demo bill categories must use built-in keys`() {
        // 演示账单存的是分类的**内部键**（与 BillCategoryData 的 name 一致的中文常量），
        // 不是本地化后的显示名 —— 存显示名会让英文环境下按键查图标/颜色全部落空。
        // 转账行没有分类（空值跳过）。
        val expenseKeys = expenseCategoryItems.map { it.name }
        val incomeKeys = incomeCategoryItems.map { it.name }

        demoBillSeeds.forEach { seed ->
            if (seed.type == "TRANSFER") return@forEach
            val allowed = if (seed.type == "INCOME") incomeKeys else expenseKeys
            assertTrue(
                "演示账单「${seed.merchant}」分类键「${seed.category}」不在内置分类内",
                seed.category in allowed
            )
        }
    }

    @Test
    fun `wealth demo asset categories must use built-in keys`() {
        val assetKeys = assetCategoryItems.map { it.name }
        demoAssetSeeds.forEach { seed ->
            assertTrue(
                "演示物品「${seed.name}」分类键「${seed.category}」不在内置分类内",
                seed.category in assetKeys
            )
        }
    }

    @Test
    fun `demo seed structural coverage`() {
        assertTrue("演示账单应至少一笔", demoBillSeeds.isNotEmpty())
        assertTrue("演示物品应至少一件", demoAssetSeeds.isNotEmpty())
        // 演示模式的每块界面都要有戏可演
        assertTrue("缺少转账示例（转账流水）", demoBillSeeds.any { it.type == "TRANSFER" && it.toWalletIndex != null })
        assertTrue("缺少可报销示例（待报销卡）", demoBillSeeds.any { it.isReimbursable })
        assertTrue("缺少收入示例（收入侧）", demoBillSeeds.any { it.type == "INCOME" })
        assertTrue("缺少双图物品（相册浏览）", demoAssetSeeds.any { it.images.size >= 2 })
        assertTrue("缺少「生产日期+时长」保质期录法", demoAssetSeeds.any { it.shelfLifeDurationValue != null })
        assertTrue("缺少「直接到期日」保质期录法", demoAssetSeeds.any { it.shelfLifeExpireInDays != null })
        assertTrue("缺少保修示例", demoAssetSeeds.any { it.warrantyExpireInDays != null })
        demoBillSeeds.forEach { seed ->
            assertTrue("账单金额必须为正：${seed.merchant}", seed.amount > 0)
            assertTrue("账单日期锚点不能为负：${seed.merchant}", seed.daysAgo >= 0)
            assertTrue("钱包下标越界：${seed.merchant}", seed.walletIndex in 0 until 4)
        }
        demoAssetSeeds.forEach { seed ->
            assertTrue("物品价格必须为正：${seed.name}", seed.price > 0)
            assertTrue("物品购入锚点不能为负：${seed.name}", seed.acquiredDaysAgo >= 0)
        }
    }

    @Test
    fun `shelf life seeds must resolve to an expiry date`() {
        // ShelfLifeCard 直读 shelfLifeExpireDate，null 整卡不渲染：「生产日期+时长」录法
        // 必须能通过 core 的同一套纯函数算出到期日（播种器与录入侧同源）。
        val zone = java.time.ZoneId.systemDefault()
        val startOfToday = java.time.LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val dayMs = 86_400_000L
        demoAssetSeeds.forEach { seed ->
            // 没填任何保质期形态的物品（如球拍）到期日本就为 null，不参与断言
            val hasShelfLife = seed.shelfLifeExpireInDays != null || seed.shelfLifeProducedDaysAgo != null
            if (!hasShelfLife) return@forEach
            val produced = seed.shelfLifeProducedDaysAgo?.let { startOfToday - it * dayMs }
            val expiry = seed.shelfLifeExpireInDays?.let { startOfToday + it * dayMs }
                ?: com.palmnote.domain.util.shelfLifeExpiryOrNull(
                    produced,
                    seed.shelfLifeDurationValue,
                    seed.shelfLifeDurationUnit?.let { com.palmnote.domain.model.ShelfLifeUnit.fromOrNull(it) }
                )
            assertTrue("保质期种子算不出到期日：${seed.name}", expiry != null)
            if (seed.shelfLifeProducedDaysAgo != null) {
                assertTrue("期限录法种子缺生产日期：${seed.name}", produced != null)
            }
        }
    }

    @Test
    fun `demo bills must stay inside the current month for the budget alert`() {
        // 预算预警按自然月取数：不管今天是几号（月初最容易出事），全部种子账单
        // 都必须落在本月，否则 ¥3,000 预算的 80% 预警没戏可演。
        val todays = listOf(
            java.time.LocalDate.now(),
            java.time.LocalDate.now().withDayOfMonth(1),
            java.time.LocalDate.now().withDayOfMonth(2)
        )
        todays.forEach { today ->
            demoBillSeeds.forEach { seed ->
                val date = seed.resolveDate(today)
                assertTrue(
                    "账单划出了本月（today=$today）：${seed.merchant} → $date",
                    date.year == today.year && date.month == today.month
                )
            }
        }
    }

    @Test
    fun `demo month expenses must trigger the budget alert`() {
        // seedBudgets 播 ¥3,000（300_000 分）：本月示例支出必须超过其 80%，
        // 否则演示模式的预算预警卡永远是「未超支」的空状态。
        val budgetFen = 300_000L
        val monthExpense = demoBillSeeds
            .filter { it.type == "EXPENSE" }
            .sumOf { it.amount }
        assertTrue(
            "本月示例支出 ${monthExpense / 100.0} 元未达预算 80%（${budgetFen * 80 / 100 / 100.0} 元），预警卡没戏可演",
            monthExpense >= budgetFen * 80 / 100
        )
    }

    @Test
    fun `demo prefs keys stay in sync with seeders`() {
        // 播种器的版本号都必须 > 0（0 表示从未播种，会让 ensureSeeded 判定失效）
        assertTrue(com.palmnote.data.LifeDemoSeeder.SEED_VERSION > 0)
        assertTrue(com.palmnote.data.WealthDemoSeeder.SEED_VERSION > 0)
        // 引用一下 PreferencesManager 的演示偏好，保证改名时编译期就断
        val p = PreferencesManager::class.java
        assertTrue(p.methods.any { it.name == "setLifeCategoryCompact" })
    }
}
