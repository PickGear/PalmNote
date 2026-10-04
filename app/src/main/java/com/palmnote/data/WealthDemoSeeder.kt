package com.palmnote.data

import android.content.Context
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.AccountBookDao
import com.palmnote.data.db.dao.AssetDao
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.PaymentMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记账 / 物品模块的演示数据播种器（示例账本 + 示例钱包 + 示例账单 + 示例物品）。
 *
 * ## 可见性模型：**并存**，不是互斥
 *
 * 生活页演示开着时「只看示例」，那是探索模式；记账动的是用户的**钱**，
 * 所以按记账品类行业做法（钱迹 / 一木 / Moze）：示例账本作为**独立账本并存**、
 * 参与统计（净资产包含示例），用户在账本页一眼能看到它、关演示即整批移除。
 * 因此本模块**不改任何查询**——只在写入时打 [isDemo] 标记，删除/导出时按标记处理。
 *
 * 写入的行与真实行完全同构（同走 DAO，`yearMonth` 等派生列照常维护），
 * 用户可以点开、编辑、删除，就像自己记的。
 */
@Singleton
class WealthDemoSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billDao: BillDao,
    private val assetDao: AssetDao,
    private val walletDao: WalletDao,
    private val bookDao: AccountBookDao,
    private val accountBookDao: AccountBookDao,
    private val billRecycleBinDao: com.palmnote.data.db.dao.BillRecycleBinDao,
    private val assetRecycleBinDao: com.palmnote.data.db.dao.AssetRecycleBinDao
) {

    companion object {
        /**
         * 示例内容版本号：**改动 [demoBills] / [demoAssets] / [demoWallets] 后必须 +1**，
         * 否则库里已有的旧示例不会被刷新（与 `LifeDemoSeeder.SEED_VERSION` 同一约定）。
         *
         * v10：与生活模块同一原因——并发播种可能插出两份示例，靠重建清干净。
         */
        const val SEED_VERSION = 10
    }

    /** [clearAll] 的结果：移除的示例条数（账单 + 物品 + 钱包 + 账本）。 */
    data class ClearResult(val removed: Int)

    /**
     * 保证示例内容存在且为最新：演示模式开启且（未播种 / 版本落后 / 示例账本不在）时重建。
     * 与 `LifeDemoSeeder.ensureSeeded` 同构，三处调用（应用启动 / 设置开关 / 生活页 VM）。
     */
    suspend fun ensureSeeded(preferences: PreferencesManager): Int {
        if (!preferences.lifeDemoMode.first()) return 0
        val upToDate = preferences.wealthDemoSeeded.first() &&
            preferences.wealthDemoSeedVersion.first() >= SEED_VERSION
        if (upToDate && bookDao.countDemoBooks() > 0) return 0
        return reseed(preferences)
    }

    /** 演示期用户在示例账本里自建的账单数（0 = 不必询问）。 */
    suspend fun countUserBillsInDemoBooks(): Int = billDao.countUserBillsInDemoBooks()

    /** 重建（先清后播，幂等）。 */
    suspend fun reseed(preferences: PreferencesManager): Int {
        deleteDemoRows()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        val walletIds = seedWallets()
        // 账单直接进**用户的默认账本**（打标模型，与生活页同构）：
        // 不建示例账本——账本隔离会把示例藏进第二本账，用户打开账单页看到的还是空。
        val targetBookId = accountBookDao.firstRealBookId() ?: 1L
        val inserted = seedBills(targetBookId, walletIds, today, zone) + seedAssets(today, zone)
        preferences.setWealthDemoSeeded(true)
        preferences.setWealthDemoSeedVersion(SEED_VERSION)
        return inserted
    }

    /** 示例钱包：三类账户，净资产卡据此有三段结构与色彩。 */
    private suspend fun seedWallets(): List<Long> = demoWallets.map { spec ->
        walletDao.insert(
            Wallet(
                name = DemoTexts.localize(context, spec.name),
                type = spec.type,
                color = spec.color,
                bankName = DemoTexts.localize(context, spec.bankName),
                cardNumber = spec.cardNumber,
                initialBalance = 0,
                currentBalance = spec.balance,
                isEnabled = true,
                isDemo = true
            )
        )
    }

    /** 示例账单：挂在默认账本与对应钱包下，小票图写 asset URI（JSON 数组，与用户选图同格式）。 */
    private suspend fun seedBills(
        bookId: Long,
        walletIds: List<Long>,
        today: LocalDate,
        zone: ZoneId
    ): Int {
        demoBills.forEach { spec ->
            val at = demoBillDate(spec.daysAgo, spec.hour, today)
            billDao.insertBill(
                Bill(
                    amount = spec.amount,
                    type = if (spec.type == "INCOME") BillType.INCOME else BillType.EXPENSE,
                    // 存**内部键**：分类名在渲染时本地化（见 BillCategoryData.getLocalizedCategoryName）
                    category = spec.categoryKey,
                    note = DemoTexts.localize(context, spec.note),
                    date = at,
                    yearMonth = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
                        .let { "%04d-%02d".format(it.year, it.monthValue) },
                    accountBookId = bookId,
                    walletId = walletIds[spec.walletIndex],
                    paymentMethod = paymentMethodOf(spec.walletIndex),
                    merchant = DemoTexts.localize(context, spec.merchant),
                    images = imageJson(spec.receipt),
                    createdAt = at,
                    updatedAt = at,
                    isDemo = true
                )
            )
        }
        return demoBills.size
    }

    /** 示例物品：带购入价与估值（八成新），物品页与「资产分布」卡据此有内容。 */
    private suspend fun seedAssets(today: LocalDate, zone: ZoneId): Int {
        demoAssets.forEach { spec ->
            val purchasedAt = today.minusDays(spec.daysAgo.toLong())
                .atStartOfDay(zone).toInstant().toEpochMilli()
            assetDao.insertAsset(
                Asset(
                    name = DemoTexts.localize(context, spec.name),
                    category = spec.category,
                    brand = DemoTexts.localize(context, spec.brand),
                    model = DemoTexts.localize(context, spec.model),
                    purchasePrice = spec.price,
                    acquisitionDate = purchasedAt,
                    currentValue = (spec.price * 0.8).toLong(),
                    location = DemoTexts.localize(context, spec.location),
                    room = DemoTexts.localize(context, spec.room),
                    images = imageJson(spec.photo),
                    createdAt = purchasedAt,
                    updatedAt = purchasedAt,
                    isDemo = true
                )
            )
        }
        return demoAssets.size
    }

    /** 钱包类型 → 支付方式（示例账单的支付渠道与钱包一一对应）。 */
    private fun paymentMethodOf(walletIndex: Int): PaymentMethod = when (demoWallets[walletIndex].type) {
        "E_WALLET" -> if (walletIndex == 0) PaymentMethod.WECHAT else PaymentMethod.ALIPAY
        "CASH" -> PaymentMethod.CASH
        else -> PaymentMethod.CARD
    }

    /** 示例图物化到 filesDir/images 后写绝对路径（JSON 数组；无图则空串，与真实行同构）。 */
    private fun imageJson(assetName: String?): String =
        assetName?.let { buildJsonArray { add(JsonPrimitive(DemoImageStore.materialize(context, it))) }.toString() }
            ?: ""

    /**
     * 移除全部示例（关闭演示模式时调用）：账单 / 物品 / 钱包 / 账本四表按标记物理删除。
     * 示例账本里的账单跟随账本一起删 —— 记账语义就是「删账本连带账单」，不做生活页那套「毕业」。
     */
    suspend fun clearAll(preferences: PreferencesManager): ClearResult {
        val removed = billDao.countDemoBills() + assetDao.countDemoAssets() +
            walletDao.countDemoWallets() + bookDao.countDemoBooks() +
            billRecycleBinDao.countDemoBills() + assetRecycleBinDao.countDemoAssets()
        deleteDemoRows()
        DemoImageStore.clear(context)
        preferences.setWealthDemoSeeded(false)
        return ClearResult(removed = removed)
    }

    private suspend fun deleteDemoRows() {
        // 顺序有讲究：账单（含示例账本里自建的、回收站里的）先删，最后才删账本。
        billDao.clearDemoBills()
        billRecycleBinDao.clearDemoBills()
        assetDao.clearDemoAssets()
        assetRecycleBinDao.clearDemoAssets()
        walletDao.clearDemoWallets()
        bookDao.clearDemoBooks()
    }

}
