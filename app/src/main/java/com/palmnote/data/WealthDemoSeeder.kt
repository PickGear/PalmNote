package com.palmnote.data

import android.content.Context
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.AccountBookDao
import com.palmnote.data.db.dao.AssetDao
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.BudgetDao
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.domain.model.DemoOrigin
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.AssetStatus
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.PaymentMethod
import com.palmnote.ui.components.toImageJson
import com.palmnote.ui.components.toImageList
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
         */
        const val SEED_VERSION = 15

        /** 示例账本名（走 [DemoTexts] 本地化）。 */
        private const val DEMO_BOOK_NAME = "示例账本"

        /** 毕业兜底账本名（真实账本缺失时才会用到）。 */
        private const val FALLBACK_BOOK_NAME = "默认账本"

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

        // 物品 CSV 的列位（导出列序 = 声明序**旋转到 description**：description 起的那一段
        // 排在实体类型之后，id 之前的第 27 列才是 id、第 28 列才是 name）。
        // ⚠️ 取字段前先确认下标：整组常量少 1 会让「acquisitionDate」读到 acquisitionType
        // （"PURCHASE"）→ 全部行被判为无购买日期而跳过（演示物品为空，v11 实翻过车）。
        private const val ASSET_COL_DESCRIPTION = 1
        private const val ASSET_COL_CONDITION = 2
        private const val ASSET_COL_SERIAL = 3
        private const val ASSET_COL_CREATED = 25
        private const val ASSET_COL_UPDATED = 26
        private const val ASSET_COL_NAME = 28
        private const val ASSET_COL_CATEGORY = 29
        private const val ASSET_COL_SUB_CATEGORY = 30
        private const val ASSET_COL_BRAND = 31
        private const val ASSET_COL_MODEL = 32
        private const val ASSET_COL_PRICE = 33
        private const val ASSET_COL_ACQ_TYPE = 34
        private const val ASSET_COL_ACQUIRED = 35
        private const val ASSET_COL_STATUS = 36
        private const val ASSET_COL_COST_MODE = 37
        private const val ASSET_COL_QUANTITY = 38
        private const val ASSET_COL_USE_COUNT = 39
        private const val ASSET_COL_USAGE_HOURS = 40
        private const val ASSET_COL_LOCATION = 41
        private const val ASSET_COL_ROOM = 42
        private const val ASSET_COL_CHANNEL = 43
        private const val ASSET_COL_WARRANTY = 44
        private const val ASSET_COL_SHELF_LIFE = 45
        private const val ASSET_COL_PRODUCED = 46
        private const val ASSET_COL_DURATION = 47
        private const val ASSET_COL_DURATION_UNIT = 48
        private const val ASSET_COL_INSURANCE = 49
        private const val ASSET_COL_INSURER = 50
        private const val ASSET_COL_POLICY_NO = 51

        /** 图片列（行尾）：JSON 数组，元素是**导出设备上**的绝对路径，换机即失效。 */
        private const val ASSET_COL_IMAGES = 52

        /**
         * 把导出 CSV 的一行（物品）映射成 [Asset]；没有购买日期的行返回 null
         * （日均成本与「已使用天数」都以它为锚点）。
         *
         * 独立成纯函数是为了能被单测直接钉住列位——见 `WealthDemoSeederCsvTest`。
         * [images] 由 [seedAssets] 换成随包图落地后的本机绝对路径 JSON；
         * 纯函数本身不碰文件系统，默认空串（详情页落回分类色占位）。
         */
        internal fun assetFromDemoRow(c: List<String>, images: String = ""): Asset? {
            val price = c[ASSET_COL_PRICE].toLongOrNull() ?: 0
            val purchasedAt = c[ASSET_COL_ACQUIRED].toLongOrNull() ?: return null
            return Asset(
                name = c[ASSET_COL_NAME],
                category = c[ASSET_COL_CATEGORY],
                subCategory = c[ASSET_COL_SUB_CATEGORY],
                brand = c[ASSET_COL_BRAND],
                model = c[ASSET_COL_MODEL],
                purchasePrice = price,
                acquisitionType = c[ASSET_COL_ACQ_TYPE],
                acquisitionDate = purchasedAt,
                status = AssetStatus.from(c[ASSET_COL_STATUS]),
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
                // 图片：导出列是导出设备的私有路径（换机即指空），这里换成随包图落到本机后的绝对路径；
                // 没有随包文件时为空串（详情页落回分类色占位）。
                images = images,
                description = c[ASSET_COL_DESCRIPTION],
                serialNumber = c[ASSET_COL_SERIAL],
                condition = c[ASSET_COL_CONDITION],
                // 现值按八成新回填（与旧演示同一口径）；导出里的现值恒为 0
                currentValue = (price * 0.8).toLong(),
                isDemo = true,
                demoOrigin = DemoOrigin.SEED,
                createdAt = c[ASSET_COL_CREATED].toLongOrNull() ?: purchasedAt,
                updatedAt = c[ASSET_COL_UPDATED].toLongOrNull() ?: purchasedAt
            )
        }
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
        // 示例账本也要在：用户若把示例账本删了（钱包还在），只看钱包会误判"已是最新"，
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
            // 账本不需要 demoOrigin：它只有"是/不是示例账本"两种状态
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
                    isDemo = true,
                demoOrigin = DemoOrigin.SEED
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
            val asset = assetFromDemoRow(c, demoImagesOf(c)) ?: return@forEach
            assetDao.insertAsset(asset)
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
     * 把该行的图片引用落成**本机可渲染的绝对路径 JSON**。
     *
     * 导出 CSV 的图片列是导出设备上的绝对路径（`/data/user/0/com.palmnote/files/images/x.jpg`），
     * 换机即指空；但随包的 `assets/demo/` 里放着同名文件（提交时按 basename 一一对应），
     * 所以只取 basename、由 [DemoImageStore] 拷进 `filesDir/images/`（带 `demo_` 前缀）。
     *
     * 走 `filesDir` 而不是直读 `file:///android_asset/`：后者 coil3 在真机上不渲染（见 DemoImageStore 注释）。
     * 前缀隔离保证关闭演示时 [DemoImageStore.clear] 只删示例图、用户自己的照片分毫不碰。
     *
     * 复制放 IO：引导页收尾那一次播种是跑在**主线程 scope** 上的（MainActivity 的 rememberCoroutineScope），
     * 14 张约 1.6MB 不该占主线程。
     */
    @Suppress("InjectDispatcher")
    private suspend fun demoImagesOf(c: List<String>): String = withContext(Dispatchers.IO) {
        val names = c.getOrNull(ASSET_COL_IMAGES).orEmpty().toImageList()
            .map { it.substringAfterLast('/') }
            .filter { it.isNotBlank() }
        if (names.isEmpty()) return@withContext ""
        // 缺某张图不该让整件物品丢掉：逐张容错，失败的只是少一张照片
        val paths = names.mapNotNull { name ->
            runCatching { DemoImageStore.materialize(context, name) }.getOrNull()
        }
        // 一张都没落成时必须回空串而不是 "[]"：详情页/网格卡用 `images.isEmpty()` 判断是否走
        // 分类色占位，"[]" 不为空会让占位底色消失、hero 变透明。
        if (paths.isEmpty()) "" else paths.toImageJson()
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
