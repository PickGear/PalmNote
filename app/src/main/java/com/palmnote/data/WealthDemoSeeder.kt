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
import com.palmnote.domain.model.AssetStatus
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.PaymentMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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
 * 所以示例账本按「独立账本并存」处理：
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
         * 示例内容版本号：**改动演示数据后必须 +1**，
         * 否则库里已有的旧示例不会被刷新（与 `LifeDemoSeeder.SEED_VERSION` 同一约定）。
         *
         * v11：演示数据整体更换为**真实导出**（掌记_数据.zip 的账单 / 物品 CSV，随包内置），
         * 列结构由「合成字段」变为「导出列序」，必须整批重播。
         */
        const val SEED_VERSION = 11

        // 账单 CSV 的列位（0 起，第 0 列 = 实体类型）。
        private const val BILL_COL_DATE = 15
        private const val BILL_COL_AMOUNT = 10
        private const val BILL_COL_TYPE = 11
        private const val BILL_COL_CATEGORY = 12
        private const val BILL_COL_SUB_CATEGORY = 13
        private const val BILL_COL_NOTE = 14
        private const val BILL_COL_WALLET = 18
        private const val BILL_COL_TO_WALLET = 19
        private const val BILL_COL_MERCHANT = 21
        private const val BILL_COL_LOCATION = 23
        private const val BILL_COL_REIMBURSABLE = 31

        // 物品 CSV 的列位（导出列序 = 声明序旋转到 description）。
        private const val ASSET_COL_CONDITION = 2
        private const val ASSET_COL_CREATED = 24
        private const val ASSET_COL_UPDATED = 25
        private const val ASSET_COL_NAME = 27
        private const val ASSET_COL_CATEGORY = 28
        private const val ASSET_COL_SUB_CATEGORY = 29
        private const val ASSET_COL_BRAND = 30
        private const val ASSET_COL_MODEL = 31
        private const val ASSET_COL_PRICE = 32
        private const val ASSET_COL_ACQ_TYPE = 33
        private const val ASSET_COL_ACQUIRED = 34
        private const val ASSET_COL_STATUS = 35
        private const val ASSET_COL_COST_MODE = 36
        private const val ASSET_COL_QUANTITY = 37
        private const val ASSET_COL_USE_COUNT = 38
        private const val ASSET_COL_USAGE_HOURS = 39
        private const val ASSET_COL_LOCATION = 40
        private const val ASSET_COL_ROOM = 41
        private const val ASSET_COL_CHANNEL = 42
        private const val ASSET_COL_WARRANTY = 43
        private const val ASSET_COL_SHELF_LIFE = 44
        private const val ASSET_COL_PRODUCED = 45
        private const val ASSET_COL_DURATION = 46
        private const val ASSET_COL_DURATION_UNIT = 47
        private const val ASSET_COL_INSURANCE = 48
        private const val ASSET_COL_INSURER = 49
        private const val ASSET_COL_POLICY_NO = 50
    }

    /** [clearAll] 的结果：移除的示例条数（账单 + 物品 + 钱包 + 账本）。 */
    data class ClearResult(val removed: Int)

    /**
     * 保证示例内容存在且为最新：演示模式开启且（未播种 / 版本落后 / 示例行不在）时重建。
     * 与 `LifeDemoSeeder.ensureSeeded` 同构，三处调用（应用启动 / 设置开关 / 生活页 VM）。
     */
    suspend fun ensureSeeded(preferences: PreferencesManager): Int {
        if (!preferences.lifeDemoMode.first()) return 0
        val upToDate = preferences.wealthDemoSeeded.first() &&
            preferences.wealthDemoSeedVersion.first() >= SEED_VERSION
        // 存在性检查必须落在**真正会被播种的行**上。v10 起示例账单直接进用户默认账本、
        // 不再建示例账本，旧守卫的 `bookDao.countDemoBooks() > 0` 恒为 false ⟹
        // 演示模式下每次应用启动都整库重建：演示期删掉的示例钱包被复活、刚进回收站的
        // 示例账单被 [deleteDemoRows] 的 clearDemoBills 连带抹掉 ——
        // 用户实测「删钱包→回收站没有，不能恢复」（2026-10-11）。
        // 改查示例钱包：既保留「清库后标记残留 → 自动补播」的原意
        // （clearBills/clearAll 清表不清 DataStore 标记），又让演示期的删除在
        // 重启后依然成立、进回收站的示例账单可以安心恢复。
        if (upToDate && walletDao.countDemoWallets() > 0) return 0
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
        val inserted = seedBills(targetBookId, walletIds, today, zone) + seedAssets()
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

    /**
     * 示例账单：**真实导出**（掌记_数据.zip 的账单表，41 笔，2026-10-01~08）。
     *
     * 数据以 CSV 资产随包内置（assets/demo/user_bills.csv），播种时按**导出列序**取字段
     * （0 起的下标注在取值处；该格式由导出 / 导入互为契约，列序稳定）。
     * 日期整体平移：最新一笔落在今天，其余保持原始间隔与时刻——任何一天开演示都像「最近在记」。
     * 钱包按导出时的引用回到示例钱包（1=现金 2=微信零钱 3=支付宝，与真实截图一致）；
     * 图片列指向导出设备上的私有路径，演示环境不存在——整体清空。
     * 转账行的目标钱包同样按映射落到示例钱包（支付宝 → 微信零钱，与真实截图一致）。
     */
    private suspend fun seedBills(
        bookId: Long,
        walletIds: List<Long>,
        today: LocalDate,
        zone: ZoneId
    ): Int {
        val rows = parseDemoCsv(readAsset("demo/user_bills.csv")).filter { it.firstOrNull() == "账单" }
        if (rows.isEmpty()) return 0
        val latest = rows.maxOf { it[BILL_COL_DATE].toLong() }
        val shift = today.atStartOfDay(zone).toInstant().toEpochMilli() - startOfDayMillis(latest, zone)
        var inserted = 0
        rows.forEach { c ->
            val at = c[BILL_COL_DATE].toLong() + shift
            val walletIndex = walletIndexById(c[BILL_COL_WALLET].toLongOrNull() ?: 0L)
            billDao.insertBill(
                Bill(
                    amount = c[BILL_COL_AMOUNT].toLong(),
                    type = BillType.valueOf(c[BILL_COL_TYPE]),
                    // 导出里的分类值就是内部键（中文常量，与 BillCategoryData 的 name 一致），直接透传
                    category = c[BILL_COL_CATEGORY],
                    subCategory = c[BILL_COL_SUB_CATEGORY],
                    note = c[BILL_COL_NOTE],
                    date = at,
                    yearMonth = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
                        .let { "%04d-%02d".format(it.year, it.monthValue) },
                    accountBookId = bookId,
                    walletId = walletIds[walletIndex],
                    toWalletId = c[BILL_COL_TO_WALLET].toLongOrNull()?.let { walletIds[walletIndexById(it)] },
                    paymentMethod = paymentMethodOf(walletIndex),
                    merchant = c[BILL_COL_MERCHANT],
                    location = c[BILL_COL_LOCATION],
                    // 可报销标记随数据走（旅游那笔是=是）：演示模式的待报销卡才有内容
                    isReimbursable = c[BILL_COL_REIMBURSABLE] == "是",
                    createdAt = at,
                    updatedAt = at,
                    isDemo = true
                )
            )
            inserted++
        }
        return inserted
    }

    /** 示例物品：**真实导出**（13 件，含保质期 / 保修 / 使用统计），现值按八成新回填。 */
    private suspend fun seedAssets(): Int {
        val rows = parseDemoCsv(readAsset("demo/user_assets.csv")).filter { it.firstOrNull() == "物品" }
        var inserted = 0
        rows.forEach { c ->
            val price = c[ASSET_COL_PRICE].toLongOrNull() ?: 0
            val purchasedAt = c[ASSET_COL_ACQUIRED].toLongOrNull() ?: return@forEach
            assetDao.insertAsset(
                Asset(
                    name = c[ASSET_COL_NAME],
                    category = c[ASSET_COL_CATEGORY],
                    subCategory = c[ASSET_COL_SUB_CATEGORY],
                    brand = c[ASSET_COL_BRAND],
                    model = c[ASSET_COL_MODEL],
                    purchasePrice = price,
                    acquisitionType = c[ASSET_COL_ACQ_TYPE],
                    acquisitionDate = purchasedAt,
                    status = AssetStatus.valueOf(c[ASSET_COL_STATUS]),
                    costMode = c[ASSET_COL_COST_MODE],
                    quantity = c[ASSET_COL_QUANTITY].toIntOrNull() ?: 1,
                    useCount = c[ASSET_COL_USE_COUNT].toIntOrNull() ?: 0,
                    totalUsageHours = c[ASSET_COL_USAGE_HOURS].toDoubleOrNull() ?: 0.0,
                    location = c[ASSET_COL_LOCATION],
                    room = c[ASSET_COL_ROOM],
                    purchaseChannel = c[ASSET_COL_CHANNEL],
                    warrantyExpireDate = c[ASSET_COL_WARRANTY].toLongOrNull(),
                    shelfLifeExpireDate = c[ASSET_COL_SHELF_LIFE].toLongOrNull(),
                    shelfLifeProducedDate = c[ASSET_COL_PRODUCED].toLongOrNull(),
                    shelfLifeDurationValue = c[ASSET_COL_DURATION].toIntOrNull(),
                    shelfLifeDurationUnit = c[ASSET_COL_DURATION_UNIT],
                    insuranceExpireDate = c[ASSET_COL_INSURANCE].toLongOrNull(),
                    insuranceCompany = c[ASSET_COL_INSURER],
                    insurancePolicyNo = c[ASSET_COL_POLICY_NO],
                    // 图片列指向导出设备上的私有路径，演示环境不存在——整体清空
                    images = "",
                    condition = c[ASSET_COL_CONDITION],
                    // 现值按八成新回填（与旧演示同一口径）；导出里的现值恒为 0
                    currentValue = (price * 0.8).toLong(),
                    isDemo = true,
                    createdAt = c[ASSET_COL_CREATED].toLongOrNull() ?: purchasedAt,
                    updatedAt = c[ASSET_COL_UPDATED].toLongOrNull() ?: purchasedAt
                )
            )
            inserted++
        }
        return inserted
    }

    /** quote 感知的 CSV 行解析（导出物内的引号成对转义）。 */
    private fun parseDemoCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && !inQuotes -> inQuotes = true
                ch == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    /**
     * 引号内含换行的字段会把一条记录拆成多行：按引号奇偶把物理行重新拼回
     * （与导出器 `reflowQuotedLines` 同一规则）。同时剥 BOM 与行尾 \r，丢弃空行。
     */
    private fun parseDemoCsv(text: String): List<List<String>> {
        val logical = mutableListOf<String>()
        val buf = StringBuilder()
        text.lines().forEach { line ->
            buf.append(line.trim().trimStart('\uFEFF').trimEnd('\r'))
            if (buf.count { it == '"' } % 2 == 0) {
                logical.add(buf.toString())
                buf.clear()
            }
        }
        buf.takeIf { it.isNotEmpty() }?.let { logical.add(it.toString()) }
        return logical
            .map(::parseDemoCsvLine)
            .filter { it.firstOrNull()?.isNotBlank() == true }
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().readText()

    /** 毫秒时间戳所在自然日的 0 点（用于把最新一笔账单平移到今天）。 */
    private fun startOfDayMillis(millis: Long, zone: ZoneId): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** 钱包类型 → 支付方式（示例账单的支付渠道与钱包一一对应）。 */
    private fun paymentMethodOf(walletIndex: Int): PaymentMethod = when (demoWallets[walletIndex].type) {
        "E_WALLET" -> if (walletIndex == 0) PaymentMethod.WECHAT else PaymentMethod.ALIPAY
        "CASH" -> PaymentMethod.CASH
        else -> PaymentMethod.CARD
    }

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
