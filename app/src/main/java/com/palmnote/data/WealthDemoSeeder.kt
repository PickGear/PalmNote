package com.palmnote.data

import android.content.Context
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.AccountBookDao
import com.palmnote.data.db.dao.AssetDao
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.BudgetDao
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.domain.model.DemoOrigin
import com.palmnote.domain.model.ShelfLifeUnit
import com.palmnote.domain.util.shelfLifeExpiryOrNull
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.AssetStatus
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.PaymentMethod
import com.palmnote.ui.components.toImageJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记账 / 物品模块的演示数据播种器（示例账本 + 示例钱包 + 示例账单 + 示例物品 + 示例预算）。
 *
 * ## 可见性模型：**并存**，不是互斥
 *
 * 生活页演示开着时「只看示例」，那是探索模式；记账动的是用户的**钱**，
 * 所以示例账本按「独立账本并存」处理：
 * 参与统计（净资产包含示例），用户在账本页一眼能看到它、关演示即整批移除。
 * 因此本模块**不改任何查询**——只在写入时打 [isDemo] 标记，删除/导出时按标记处理。
 * 预算是例外：没有账本维度也没有 `isDemo` 位，靠 `demoOrigin` 在仓库层分流
 * （见 BudgetRepositoryImpl），本模块只负责播种与清理。
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
    private val budgetDao: BudgetDao,
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
         * v12：修正物品 CSV 的列位偏移——导出文件在 id 之前还有一列（实体类型后的旋转列序），
         * 原常量整体少 1：购买日期取到的是 acquisitionType（"PURCHASE"）→ 每行都被跳过，
         * 演示模式物品页为空。已整批重播补回。
         * v13：物品图片接上随包图（原先把 `images` 硬清空，13 件全是无图占位，
         * 而 14 张 jpg 已在 `assets/demo/` 里白占 1.6MB）；播种内容变了，必须整批重播。
         * v14：接示例预算（本月 ¥3,000，demoOrigin = SEED）——预算 v20 起有 `demoOrigin`
         * 维度，演示模式预算页不再永远「未设预算」；播种内容变了，必须整批重播。
         * v16：种子从「导出 CSV + 列位解析」改为**类型化对象图**（[demoBillSeeds] /
         * [demoAssetSeeds]，见 WealthDemoSeed.kt）——列位错格整批静默跳过（v12）的
         * 脆弱性连根拔掉；日期改**相对锚点**，每次播种自动滚动到「最近」；
         * 内容换中性样例，图片转 WebP（1.53MB → 0.75MB）。必须整批重播。
         */
        const val SEED_VERSION = 16

        private const val DAY_MS = 86_400_000L

        /** 示例账本名（走 [DemoTexts] 本地化）。 */
        private const val DEMO_BOOK_NAME = "示例账本"

        /** 毕业兜底账本名（真实账本缺失时才会用到）。 */
        private const val FALLBACK_BOOK_NAME = "默认账本"
    }

    /** [clearAll] 的结果：removed = 实际删除的行数，kept = 毕业保留的演示期自建行数。 */
    data class ClearResult(val removed: Int, val kept: Int = 0)

    /** 演示期用户自建的行数（账单 + 物品 + 钱包），毕业询问的计数口径。 */
    suspend fun countUserRows(): Int =
        billDao.countUserBillsInDemoBooks() + assetDao.countUserAssets() + walletDao.countUserWallets()

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
        // 示例账本也要在：用户若把示例账本删了（钱包还在），只看钱包会误判「已是最新」，
        // 于是账单页一直是空的。三个条件全满足才算真·就绪。
        val demoBookReady = accountBookDao.demoBookId() != null
        if (upToDate && demoBookReady && walletDao.countDemoWallets() > 0) return 0
        return reseed(preferences)
    }

    /** 演示期用户在示例账本里自建的账单数（0 = 不必询问）。 */
    suspend fun countUserBillsInDemoBooks(): Int = billDao.countUserBillsInDemoBooks()

    /** 重建（先清后播，幂等）。 */
    suspend fun reseed(preferences: PreferencesManager): Int {
        graduateUserRows()
        deleteDemoRows()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        val walletIds = seedWallets()
        // 示例账单进**独立的示例账本**（isDemo=1）：
        // 账单页 / 报表 / 账单小组件本来就按账本取数，隔离后这三处自动「只看示例」，
        // 不用在 40 个聚合查询上逐个加 isDemo 条件。演示模式下账本列表也只暴露示例账本
        // （见 AccountBookRepositoryImpl），用户要回自己的数据就关演示。
        val targetBookId = seedBook()
        val inserted = seedBills(targetBookId, walletIds, today, zone) + seedAssets() + seedBudgets(today)
        preferences.setWealthDemoSeeded(true)
        preferences.setWealthDemoSeedVersion(SEED_VERSION)
        return inserted
    }

    /** 示例账本：演示数据的取数口径落点（账单页 / 报表 / 小组件都按账本取数）。 */
    private suspend fun seedBook(): Long = accountBookDao.insertBook(
        AccountBook(
            name = DemoTexts.localize(context, DEMO_BOOK_NAME),
            bookType = "DAILY",
            // 账本不需要 demoOrigin：它只有「是/不是示例账本」两种状态
            isDemo = true
        )
    )

    /**
     * 演示期用户自建的行先「毕业」为真实行，再删示例行。
     *
     * 账单要迁进目标账本（没有就兜底建）；物品 / 钱包没有账本维度，原地翻标记。
     * 不打这一步的话，关演示的清理或重播会吃掉用户演示期记的内容——
     * 重播（切语言 / 版本升级）同样要先毕业，否则每次重播都在悄悄吃掉用户的记录。
     */
    private suspend fun graduateUserRows() {
        if (billDao.countUserBillsInDemoBooks() > 0) {
            // 兜底建真实账本：正常流程启动即 initDefaultBooks()，够不到这条；
            // 但一旦取不到目标账本就 return，后面的清理会把用户账单连带删掉 —— 宁可多建一本。
            val target = accountBookDao.firstRealBookId() ?: accountBookDao.insertBook(
                AccountBook(
                    name = DemoTexts.localize(context, FALLBACK_BOOK_NAME),
                    isDefault = true
                )
            )
            billDao.graduateUserBillsFromDemoBooks(target)
        }
        assetDao.graduateUserAssets()
        walletDao.graduateUserWallets()
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
                isDemo = true,
                demoOrigin = DemoOrigin.SEED
            )
        )
    }

    /**
     * 示例账单：类型化种子（[demoBillSeeds]），日期按相对锚点换算并**钳进本月**
     * （[DemoBillSeed.resolveDate]——预算预警按自然月取数，账单划进上个月预警就没戏了）。
     * 结构覆盖：转账、可报销、三类收入、四个钱包各有戏份。
     */
    private suspend fun seedBills(
        bookId: Long,
        walletIds: List<Long>,
        today: LocalDate,
        zone: ZoneId
    ): Int {
        var inserted = 0
        demoBillSeeds.forEach { seed ->
            val date = seed.resolveDate(today)
            val at = date.atTime(seed.hour, seed.minute).atZone(zone).toInstant().toEpochMilli()
            billDao.insertBill(
                Bill(
                    amount = seed.amount,
                    type = BillType.valueOf(seed.type),
                    // 种子里存的是分类内部键（与 BillCategoryData 的 name 一致），直接透传
                    category = seed.category,
                    subCategory = "",
                    date = at,
                    yearMonth = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
                        .let { "%04d-%02d".format(it.year, it.monthValue) },
                    accountBookId = bookId,
                    walletId = walletIds[seed.walletIndex],
                    toWalletId = seed.toWalletIndex?.let { walletIds[it] },
                    paymentMethod = paymentMethodOf(seed.walletIndex),
                    // 商户 / 地点 / 备注是展示文案，过本地化层；分类存内部键不走这里
                    merchant = DemoTexts.localize(context, seed.merchant),
                    location = DemoTexts.localize(context, seed.location),
                    note = DemoTexts.localize(context, seed.note),
                    isReimbursable = seed.isReimbursable,
                    createdAt = at,
                    updatedAt = at,
                    isDemo = true,
                    demoOrigin = DemoOrigin.SEED
                )
            )
            inserted++
        }
        return inserted
    }

    /**
     * 示例物品：类型化种子（[demoAssetSeeds]），13 件中性样例。
     * 覆盖保修、两种保质期录法、双图、转赠 / 抽签获取；现值按八成新回填。
     */
    private suspend fun seedAssets(): Int {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val startOfToday = java.time.LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        var inserted = 0
        demoAssetSeeds.forEach { seed ->
            val acquiredAt = startOfToday - seed.acquiredDaysAgo * DAY_MS
            val producedDate = seed.shelfLifeProducedDaysAgo?.let { startOfToday - it * DAY_MS }
            // 到期日是唯一被展示与存的值（ShelfLifeCard 直读它，null 整卡不渲染）：
            // 「直接到期日」录法用相对锚点换算；「生产日期+时长」录法用录入侧同一套
            // 纯函数（shelfLifeExpiryFrom）算出，保证两条录法在演示数据里行为一致。
            val shelfLifeExpire = seed.shelfLifeExpireInDays?.let { startOfToday + it * DAY_MS }
                ?: shelfLifeExpiryOrNull(
                    producedDate,
                    seed.shelfLifeDurationValue,
                    seed.shelfLifeDurationUnit?.let { ShelfLifeUnit.fromOrNull(it) }
                )
            assetDao.insertAsset(
                Asset(
                    // 名称 / 品牌 / 地点等是展示文案，过本地化层；分类存内部键不走这里
                    name = DemoTexts.localize(context, seed.name),
                    category = seed.category,
                    subCategory = "",
                    brand = DemoTexts.localize(context, seed.brand),
                    model = DemoTexts.localize(context, seed.model),
                    purchasePrice = seed.price,
                    acquisitionType = seed.acquisitionType,
                    acquisitionDate = acquiredAt,
                    status = AssetStatus.from("HELD"),
                    costMode = "DAILY",
                    quantity = 1,
                    useCount = 0,
                    totalUsageHours = 0.0,
                    location = DemoTexts.localize(context, seed.location),
                    room = DemoTexts.localize(context, seed.room),
                    purchaseChannel = DemoTexts.localize(context, seed.channel),
                    warrantyExpireDate = seed.warrantyExpireInDays?.let { startOfToday + it * DAY_MS },
                    shelfLifeExpireDate = shelfLifeExpire,
                    shelfLifeProducedDate = producedDate,
                    shelfLifeDurationValue = seed.shelfLifeDurationValue,
                    shelfLifeDurationUnit = seed.shelfLifeDurationUnit,
                    insuranceExpireDate = null,
                    insuranceCompany = "",
                    insurancePolicyNo = "",
                    images = demoImagesOf(seed.images),
                    description = DemoTexts.localize(context, seed.description),
                    serialNumber = "",
                    condition = seed.condition,
                    // 现值按八成新回填（与旧演示同一口径）
                    currentValue = (seed.price * 0.8).toLong(),
                    isDemo = true,
                    demoOrigin = DemoOrigin.SEED,
                    createdAt = now,
                    updatedAt = now
                )
            )
            inserted++
        }
        return inserted
    }

    /**
     * 示例预算：本月 ¥3,000（预算页在演示模式下才有内容）。
     *
     * 示例账单整批落在今天所在月份（最新一笔平移到今天），预算也对齐同一月份，
     * 超支提醒立刻有戏可演。取数与隔离在 BudgetRepositoryImpl（按 `demoOrigin` 分流）。
     */
    private suspend fun seedBudgets(today: LocalDate): Int {
        budgetDao.insertBudget(
            Budget(
                yearMonth = "%04d-%02d".format(today.year, today.monthValue),
                totalBudget = 300_000,
                demoOrigin = DemoOrigin.SEED
            )
        )
        return 1
    }

    /**
     * 把种子的示例图名落成**本机可渲染的绝对路径 JSON**。
     *
     * [DemoImageStore.materialize] 从 `assets/demo/` 拷进 `filesDir/images/`（带 `demo_` 前缀）。
     * 走 `filesDir` 而不是直读 `file:///android_asset/`：后者 coil3 在真机上不渲染（见 DemoImageStore 注释）。
     * 前缀隔离保证关闭演示时 [DemoImageStore.clear] 只删示例图、用户自己的照片分毫不碰。
     *
     * 复制放 IO：引导页收尾那一次播种跑在主线程 scope 上（MainActivity 的 rememberCoroutineScope）。
     */
    @Suppress("InjectDispatcher")
    private suspend fun demoImagesOf(names: List<String>): String = withContext(Dispatchers.IO) {
        if (names.isEmpty()) return@withContext ""
        // 缺某张图不该让整件物品丢掉：逐张容错，失败的只是少一张照片
        val paths = names.mapNotNull { name ->
            runCatching { DemoImageStore.materialize(context, name) }.getOrNull()
        }
        // 一张都没落成时必须回空串而不是 "[]"：详情页/网格卡用 `images.isEmpty()` 判断是否走
        // 分类色占位，"[]" 不为空会让占位底色消失、hero 变透明。
        if (paths.isEmpty()) "" else paths.toImageJson()
    }

    /** 钱包类型 → 支付方式（示例账单的支付渠道与钱包一一对应）。 */
    private fun paymentMethodOf(walletIndex: Int): PaymentMethod = when (demoWallets[walletIndex].type) {
        "E_WALLET" -> if (walletIndex == 0) PaymentMethod.WECHAT else PaymentMethod.ALIPAY
        "CASH" -> PaymentMethod.CASH
        else -> PaymentMethod.CARD
    }

    /**
     * 移除全部示例（关闭演示模式时调用）。
     *
     * 演示期自建行（`demoOrigin = USER`）的归宿由 [keepUserCreated] 决定：
     * 「保留」先毕业为真实行（账单迁真实账本、物品/钱包原地翻标记），
     * 「一并删除」直接清掉。预算没有账本维度可毕业、且直接驱动真实仪表盘的
     * 预算预警，两个分支都随演示清掉（沙盒里试的设置不漏进真实环境）。
     */
    suspend fun clearAll(preferences: PreferencesManager, keepUserCreated: Boolean = true): ClearResult {
        // 先量数量再动数据。countDemo* 是 isDemo=1 口径（含 USER 行）：
        // 「保留」时 USER 行毕业不删，要从移除数里扣除、计入保留数。
        val userBills = billDao.countUserBillsInDemoBooks()
        val userAssets = assetDao.countUserAssets()
        val userWallets = walletDao.countUserWallets()
        val seedRows = (billDao.countDemoBills() - userBills) +
            (assetDao.countDemoAssets() - userAssets) +
            (walletDao.countDemoWallets() - userWallets) +
            bookDao.countDemoBooks() +
            budgetDao.countDemoBudgets() +
            billRecycleBinDao.countDemoBills() + assetRecycleBinDao.countDemoAssets()
        // 演示期自建的数据：选「保留」先毕业，选「一并删除」就不迁、直接清掉
        if (keepUserCreated) {
            graduateUserRows()
        } else {
            billDao.clearUserBillsFromDemo()
            assetDao.clearUserAssetsFromDemo()
            walletDao.clearUserWalletsFromDemo()
        }
        deleteDemoRows()
        DemoImageStore.clear(context)
        preferences.setWealthDemoSeeded(false)
        val userRows = userBills + userAssets + userWallets
        return ClearResult(
            removed = seedRows + if (keepUserCreated) 0 else userRows,
            kept = if (keepUserCreated) userRows else 0
        )
    }

    private suspend fun deleteDemoRows() {
        // 顺序有讲究：账单（含示例账本里自建的、回收站里的）先删，最后才删账本。
        billDao.clearDemoBills()
        billRecycleBinDao.clearDemoBills()
        assetDao.clearDemoAssets()
        assetRecycleBinDao.clearDemoAssets()
        walletDao.clearDemoWallets()
        budgetDao.clearDemoBudgets()
        bookDao.clearDemoBooks()
    }

}
