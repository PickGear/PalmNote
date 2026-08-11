# PalmNote 密码本模块设计文档

> v1.3 | 2026-08-02 | 与设计规范 [design-spec.md](design-spec.md) 配合阅读
>
> **状态：v1.3.0 已实现**（`feature/vault`，主库 DB v7；vault 数据存独立库 `palmnote_vault.db` v3）。本文档为设计蓝图 + 实现状态对照，实现细节以代码为准。
>
> **实现状态图例：** ✅ 已实现（v1.3.0）｜ 🔜 预留（v2.x 规划，未实现）
>
> 本文档为密码本（Vault）功能模块的完整设计。密码本是一个纯离线、字段级加密的密码管理模块，尊重 PalmNote "隐私优先、数据本地化" 的核心原则。

---

## 一、概述与设计目标

### 1.1 产品定位

密码本是一个轻量级离线密码管理器，作为 PalmNote 的一项功能模块，不占用底部 Tab。

- 纯本地存储，无需联网
- 字段级 AES-256-GCM 加密
- 独立主密码（PBKDF2 密钥派生 + 密钥包裹），与应用锁独立
- Dashboard 卡片快捷入口（可显隐）

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **隐私优先** | 所有敏感数据（密码字段）加密存储，密钥永不离设备 |
| **离线可用** | 核心功能 100% 离线，无需任何网络权限 |
| **零摩擦** | 独立主密码 + 密钥包裹模式，改 PIN 无需重加密条目 |
| **可选扩展** | 入口可关闭，不影响不使用密码本的用户 |

### 1.3 与现有架构的关系

密码本代码位于 `com.palmnote.feature.vault` 包内（应用模块 `:app` 内部，非独立 Gradle 模块），通过 Hilt 依赖注入接入：

```
app (namespace: com.palmnote.app)
└── src/main/java/com/palmnote/
    ├── feature/vault/              ← 数据/加密层
    │   ├── VaultEntry.kt           # Room Entity
    │   ├── VaultDao.kt             # Room DAO
    │   ├── VaultRepository.kt      # 仓库
    │   ├── VaultCrypto.kt          # AES-256-GCM 原语
    │   ├── VaultKeyManager.kt      # 密钥管理器（DK 包裹/解锁/改PIN）
    │   ├── VaultLockManager.kt     # 锁定状态 + 失败次数/锁定时长
    │   ├── VaultClipboardManager.kt# 剪贴板安全（哈希追踪自动清除）
    │   ├── VaultPasswordGenerator.kt # 密码生成器 + 熵强度
    │   └── vault/                  # UI 层
    │       ├── VaultScreen.kt / VaultViewModel.kt
    │       ├── VaultDetailScreen.kt / VaultDetailViewModel.kt
    │       ├── VaultEditScreen.kt / VaultEditViewModel.kt
    │       ├── VaultLockGate.kt    # PIN 解锁门
    │       ├── VaultPasswordGeneratorSheet.kt
    │       └── VaultSettingsScreen.kt / VaultSettingsViewModel.kt
    ├── di/
    │   └── HiltModules.kt          ← 注册 vault DAO/密钥/锁定/剪贴板组件

core (namespace: com.palmnote，app 依赖)
└── src/main/java/com/palmnote/
    ├── data/datastore/PreferencesManager.kt  ← vault_salt / vault_key_wrap / 设置项 key（已迁 core）
    └── ui/dashboard/DashboardCardConfig.kt   ← VAULT 卡片类型（已迁 core）

app 侧导航
└── ui/navigation/Routes.kt        ← Vault / VaultDetail / VaultEdit / VaultSettings 路由
```

> 密码本通过 Hilt 提供依赖（`@Singleton`），所有密码本类不依赖任何网络相关代码，未声明 `INTERNET` 权限。

### 1.4 功能清单

| 能力 | 类型 | 状态 | 说明 | 网络依赖 |
|------|------|------|------|---------|
| **新增密码条目** | CRUD | ✅ | 录入标题/用户名/密码/网址/备注/分类 | ❌ |
| **密码生成器** | 工具 | ✅ | 内置随机生成器，可配置长度和字符类型（大小写/数字/符号），含熵强度提示 | ❌ |
| **列表浏览** | 查看 | ✅ | 按更新时间倒序展示所有条目 | ❌ |
| **查看密码详情** | 查看 | ✅ | 展示完整信息，密码默认遮罩，点击 👁 切换明文 | ❌ |
| **编辑条目** | CRUD | ✅ | 修改已有密码条目所有字段 | ❌ |
| **删除条目** | CRUD | ✅ | 单条删除 | ❌ |
| **搜索** | 工具 | ✅ | 实时过滤标题/用户名/网址，支持分类内搜索 | ❌ |
| **分类筛选** | 工具 | ✅ | 下拉菜单按分类过滤，支持分类内搜索 | ❌ |
| **一键复制** | 工具 | ✅ | 复制用户名/密码/网址到剪贴板，30 秒后自动清空（哈希追踪不误清） | ❌ |
| **Dashboard 快捷入口** | 导航 | ✅ | 首页卡片显示最近条目 + 条数统计，点击进入密码本 | ❌ |
| **卡片显隐** | 个性化 | ✅ | 与其他 Dashboard 卡片统一管理，可关闭 | ❌ |
| **独立主密码** | 安全 | ✅ | 独立 PIN（PBKDF2-SHA256 100k 迭代；历史包裹按 600k/25k 参数回退并在解锁时自动重包裹升级），密钥包裹模式，与应用锁独立 | ❌ |
| **生物识别解锁** | 安全 | ✅ | Keystore 不可导出密钥（`setUserAuthenticationRequired=true`）额外包裹 DK，带 30s 认证有效期 + 纯在场 BiometricPrompt（官方：有效期密钥与 CryptoObject 互斥），开启强制重建密钥、失败自愈 | ❌ |
| **无锁模式** | 安全 | ✅ | 可跳过密码设置：DK 用非认证 Keystore 密钥包裹（数据仍加密落盘），打开即用，可随时升级为 PIN/生物识别 | ❌ |
| **进入需验证** | 安全 | ✅ | 可配置：关闭后切后台不再锁定（安全降级） | ❌ |
| **自动锁定规则** | 安全 | ✅ | 可配置：立即（immediate）/ 跟系统锁屏（system，默认）/ 超时 5 分钟（timeout） | ❌ |
| **立即锁定** | 安全 | ✅ | 按自动锁定规则触发（默认跟随系统锁屏），锁定即清除密码明文与剪贴板 | ❌ |
| **失败锁定** | 安全 | ✅ | 连续 5 次 PIN 错误锁定 30 秒（`LockoutTracker`，持久化，杀进程不可绕过） | ❌ |
| **改主密码** | 安全 | ✅ | 密钥包裹模式，改 PIN 无需重加密条目 | ❌ |
| **重置密码本** | 安全 | ✅ | 清除密钥与全部条目 | ❌ |
| **智能分类建议** | AI 功能 | 🔜 | 需配置 AI 端点，仅发送 title + url（v2.x） | ✅ 可选 |
| **安全审计** | AI 功能 | 🔜 | 检测弱密码/重复密码/泄露风险，本地离线执行（v2.x） | ❌ |
| **加密云备份** | 备份 | 🔜 | 加密后备份到用户自备 WebDAV/SFTP（v2.x） | ✅ 可选 |
| **TOTP / Autofill / Passkey** | 扩展 | 🔜 | 见 11.3（v2.x） | — |

> **备注：** 所有标注"可选"网络的能力为 v2.x 预留，当前版本不加载网络代码、未声明 `INTERNET` 权限。用户行为与纯离线版本完全一致。

---

## 二、数据模型

### 2.1 Room Entity

```kotlin
@Entity(tableName = "vault_entries")
data class VaultEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,                     // 标题，明文存储
    val username: String,                  // 用户名，明文存储
    val passwordEncrypted: ByteArray,      // 密码，AES-256-GCM 加密
    val url: String,                       // 网站地址，明文存储
    val notes: String,                     // 备注，明文存储
    val category: String = "其他",         // 分类，明文存储
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

> **加密范围说明：** 只加密密码字段 `passwordEncrypted`。title / username / url 等元数据明文存储，支持通过 Room DAO 直接搜索和排序，无需解密后过滤。

### 2.2 DAO

```kotlin
@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY updatedAt DESC")
    fun getAllEntries(): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): VaultEntry?

    @Query("SELECT * FROM vault_entries WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchEntries(query: String): Flow<List<VaultEntry>>

    @Query("SELECT DISTINCT category FROM vault_entries ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM vault_entries")
    suspend fun countEntries(): Int

    @Query("SELECT * FROM vault_entries ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentEntries(limit: Int): Flow<List<VaultEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: VaultEntry): Long

    @Update
    suspend fun updateEntry(entry: VaultEntry)

    @Delete
    suspend fun deleteEntry(entry: VaultEntry)
}
```

### 2.3 Room 迁移

> **v6 说明：** 密码本数据实际存放于**独立库** `palmnote_vault.db`（`VaultDatabase`，Room 版本 1），与主库物理隔离。
> v4 → v5 曾在主库建 `vault_entries` 表，v5 → v6（`Migration5To6`）会先尽力把主库残留旧数据搬运到独立库再删除旧表（搬运为 best-effort，失败仅删表不阻塞升级）。

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS vault_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                username TEXT NOT NULL,
                passwordEncrypted BLOB NOT NULL,
                url TEXT NOT NULL,
                notes TEXT NOT NULL,
                category TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)
    }
}
```

> 密码本表在 v4 → v5 迁移中建表（主库，历史遗留），v5 → v6 迁移后由独立库承载。

---

## 三、加密流程

### 3.1 密钥体系

采用密钥包裹模式，PIN 变更时无需重新加密所有条目。

```
用户输入 PIN
    ↓
PBKDF2-SHA256（100,000 次迭代 + 随机盐 S₁）
    ↓
临时密钥 K（256 位）
    ↓
用 K（AES-256-GCM）解密"密钥包裹" → 取出数据密钥 DK（256 位）
    ↓
用 DK 加密/解密每一条密码字段
```

**存储结构（DataStore）：**

| Key | 内容 | 说明 |
|-----|------|------|
| `vault_salt` | 随机 16 字节盐（S₁） | 首次生成，不变 |
| `vault_key_wrap` | 加密后的数据密钥（DK 的密文 + IV + tag） | PIN 不变则不更新 |
| `vault_bio_key_wrap` | 生物识别 Keystore 密钥包裹的 DK 密文 + IV | 开启生物识别后存在 |
| `vault_require_auth` | 进入是否需验证 | 无锁模式下为 false |

**生物识别解锁：** 用 Android Keystore 不可导出密钥（`setUserAuthenticationRequired=true`，不出 TEE）额外包裹同一份 DK。生物识别认证通过 → Keystore 解锁该密钥 → 解密 `vault_bio_key_wrap` 得到 DK。改 PIN 不影响（DK 不变）。

**无锁模式：** DK 用非认证 Keystore 密钥（`NOLOCK_KEY_ALIAS`）包裹，`vault_salt` 存占位标记 `no_lock`。数据仍 AES-256-GCM 加密落盘，打开即用无需验证；可在设置中升级为 PIN 锁（`upgradeToPin` 重新派生 K 包裹 DK）。

### 3.2 加密流程（写入）

```
DK + 明文密码
    ↓
AES-256-GCM.encrypt(nonce = counter, plaintext = password)
    ↓
密文 + IV + tag → 拼接 → ByteArray → 写入 passwordEncrypted 字段
nonce_counter++
```

### 3.3 解密流程（读取）

```
DK + passwordEncrypted（密文 + IV + tag）
    ↓
AES-256-GCM.decrypt() → 明文密码
    ↓
仅驻留内存，离开页面时主动清除
```

### 3.4 改 PIN 流程

```
用户输入旧 PIN → 验证通过 → 输入新 PIN
    ↓
用旧 PIN 派生旧 K → 解开密钥包裹 → 取出 DK
    ↓
用新 PIN 派生新 K' → 用 K' 重新包裹 DK → 写回 DataStore
    ↓
密码条目全部无需修改
```

---

## 四、导航路由

密码本作为外层 NavHost 路由（与 Settings、AddBill 同级），不占用底部 Tab。使用类型安全路由（`Routes.kt`，`@Serializable`）：

```kotlin
@Serializable data object Vault
@Serializable data class VaultDetail(val entryId: Long)
@Serializable data class VaultEdit(val entryId: Long? = null)
@Serializable data object VaultSettings
```

在 NavHost 中以类型安全方式注册 composable：

```kotlin
composable<Vault> {
    VaultScreen(
        onNavigateToDetail = { id -> navController.navigate(VaultDetail(id)) },
        onNavigateToEdit = { id -> navController.navigate(VaultEdit(id)) },
        onNavigateToSettings = { navController.navigate(VaultSettings) },
        onNavigateBack = { navController.popBackStack() }
    )
}

composable<VaultDetail> { backStackEntry ->
    val entryId = backStackEntry.toRoute<VaultDetail>().entryId
    VaultDetailScreen(
        entryId = entryId,
        onNavigateToEdit = { id -> navController.navigate(VaultEdit(id)) },
        onNavigateBack = { navController.popBackStack() }
    )
}

composable<VaultEdit> { backStackEntry ->
    val entryId = backStackEntry.toRoute<VaultEdit>().entryId
    VaultEditScreen(
        entryId = entryId,
        onNavigateBack = { navController.popBackStack() }
    )
}

composable<VaultSettings> {
    VaultSettingsScreen(onNavigateBack = { navController.popBackStack() })
}
```

### 导航图

```
Dashboard 卡片点击
    ↓
VaultScreen（列表 / 搜索 / 分类）
    ├─ 点击条目 → VaultDetailScreen（查看 / 复制 / 编辑 / 删除）
    ├─ 点击新增 → VaultEditScreen（新增 / 编辑表单）
    └─ 设置入口 → VaultSettingsScreen（剪贴板/需验证/条目数/改PIN/重置）
```

---

## 五、UI 设计

### 5.1 主题色

| Token | 色值 | 用途 |
|-------|------|------|
| ModuleVault（亮色） | `#6750A4` | 标题、选中图标、强调色 |
| ModuleVault（暗色） | `#D0BCFF` | 暗色主题对应色 |

ModuleVault 使用紫色调，与安全/加密的视觉直觉一致，且与现有模块色调（绿/蓝/黄/粉/靛/翠）不冲突。

### 5.1.1 图标系统

密码本使用 `AppIcon` 枚举（与全局一致），线框图中的 Emoji 仅为示意，实际实现映射如下：

| 场景 | AppIcon 枚举名 | 线框图示意 |
|------|---------------|-----------|
| 导航栏标题 / Dashboard 卡片 | `AppIcon.Lock` | 🔒 |
| 条目默认图标 | `AppIcon.Key` | 🔑 |
| 密码显隐切换 | `AppIcon.Visibility` / `AppIcon.VisibilityOff` | 👁 |
| 复制 | `AppIcon.ContentCopy` | 📋 |
| 密码生成器 | `AppIcon.Shuffle` | 🎲 |
| 删除 | `AppIcon.Delete` | 🗑️ |
| 编辑 | `AppIcon.Edit` | ✏️ |
| 空状态 | `AppIcon.LockOpen` | 🔓 |
| 新增 | `AppIcon.Add` | + |
| 搜索 | `AppIcon.Search` | 🔍 |
| 分类筛选用 | `AppIcon.FilterList` | ▼ |

### 5.2 VaultScreen（列表页）

```
┌─────────────────────────┐
│ 🔒 密码本        🔍 +  │  ← CompactTopAppBar
├─────────────────────────┤
│ [全部 ▼]   搜索框...    │  ← 分类筛选 + 搜索
├─────────────────────────┤
│ ┌─────────────────────┐ │
│ │ 🔑 GitHub           │ │  ← 条目卡片
│ │ user@example.com    │ │
│ │ 最后修改: 7/28      │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │ 🏦 网银             │ │
│ │ bankname            │ │
│ │ 最后修改: 7/27      │ │
│ └─────────────────────┘ │
│           ...            │
├─────────────────────────┤
│          [+ 新增]       │  ← FAB
└─────────────────────────┘
```

- 搜索：实时过滤标题/URL/用户名
- 分类筛选：下拉菜单，从 `getAllCategories()` 动态加载
- 条目卡片：左侧 icon（根据 URL 首段初步判断类型：github→`AppIcon.Code`，bank→`AppIcon.AccountBalance`，`https://`→`AppIcon.Language`，fallback→`AppIcon.Key`），中间标题+用户名+时间，右侧复制按钮

> **favicon 处理策略：** 纯离线版不加载网络 favicon，仅通过 URL 首段本地规则匹配图标。AI/Cloud 版可增强为在线 favicon 抓取（作为可选能力，默认关闭）。

### 5.3 VaultDetailScreen（详情页）

```
┌─────────────────────────┐
│ ← 密码本      ••• 菜单  │  ← TopAppBar，菜单含编辑/删除
├─────────────────────────┤
│                         │
│  🔑                     │  ← 大图标
│                         │
│  标题                    │
│  ─────────────────────  │
│  用户名                  │
│  user@example.com   📋  │  ← 点击复制
│  ─────────────────────  │
│  密码                    │
│  ●●●●●●●●  👁  📋     │  ← 显示/隐藏切换 + 复制
│  ─────────────────────  │
│  网站                    │
│  github.com         📋  │
│  ─────────────────────  │
│  备注                    │
│  个人账号                │
│  ─────────────────────  │
│  分类: 技术              │
│  创建: 2026-07-28       │
│  更新: 2026-07-28       │
│                         │
└─────────────────────────┘
```

- 密码默认遮罩显示（●●●●●●），👁 点击切换明文
- 复制到剪贴板后 30 秒自动清空
- 离开页面时密码从内存清除

### 5.4 VaultEditScreen（新增/编辑页）

```
┌─────────────────────────┐
│ ← 新增密码本     ✓ 保存 │  ← TopAppBar
├─────────────────────────┤
│                         │
│  标题 *                  │
│  ┌───────────────────┐  │
│  │ GitHub             │  │
│  └───────────────────┘  │
│                         │
│  用户名                  │
│  ┌───────────────────┐  │
│  │ user@example.com   │  │
│  └───────────────────┘  │
│                         │
│  密码 *          🎲生成 │  ← 密码生成器按钮
│  ┌───────────────────┐  │
│  │ ●●●●●●●●●●●●●●●    │  │
│  └───────────────────┘  │
│                         │
│  网站                    │
│  ┌───────────────────┐  │
│  │ https://github.com │  │
│  └───────────────────┘  │
│                         │
│  备注                    │
│  ┌───────────────────┐  │
│  │ 个人开发账号       │  │
│  └───────────────────┘  │
│                         │
│  分类: [其他 ▼]         │
│                         │
└─────────────────────────┘
```

- 标题和密码为必填项
- 🎲 密码生成器：弹窗选择长度/字符类型（大小写/数字/符号）
- 分类支持选择已有分类或输入新分类

### 5.5 密码生成器（BottomSheet）

```
┌─────────────────────────┐
│ 密码生成器               │
├─────────────────────────┤
│  ┌───────────────────┐  │
│  │ aB3$kF9#mQ2x...   │  │  ← 生成结果
│  └───────────────────┘  │
│                         │
│  长度: ○ 8  ○ 12  ● 16 │
│  ☑ 大写字母             │
│  ☑ 小写字母             │
│  ☑ 数字                │
│  ☑ 符号                │
│                         │
│  [🔄 重新生成] [✓ 使用] │
└─────────────────────────┘
```

**生成算法：**

```kotlin
object VaultPasswordGenerator {
    private val random = SecureRandom()  // 阻断预测

    fun generate(
        length: Int = 16,
        useUppercase: Boolean = true,
        useLowercase: Boolean = true,
        useDigits: Boolean = true,
        useSymbols: Boolean = true
    ): String {
        val charset = buildString {
            if (useUppercase) append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (useLowercase) append("abcdefghijklmnopqrstuvwxyz")
            if (useDigits) append("0123456789")
            if (useSymbols) append("!@#\$%^&*()_+-=[]{}|;:,.<>?")
        }
        require(charset.isNotEmpty()) { "至少选择一种字符类型" }
        return (1..length)
            .map { charset[random.nextInt(charset.length)] }
            .joinToString("")
    }

    /** 预估熵值（bit），用于强度指示 */
    fun estimateEntropy(password: String): Double {
        val charsetSize = when {
            password.any { "!@#\$%^&*()_+-=[]{}|;:,.<>?".contains(it) } -> 94
            password.any { it.isLetter() } && password.any { it.isDigit() } -> 62
            password.any { it.isLetter() } -> 52
            password.any { it.isDigit() } -> 10
            else -> 1
        }
        return password.length * log2(charsetSize.toDouble())
    }
}
```

**强度指示：**

| 熵值 | 强度 | 颜色 |
|------|------|------|
| < 40 bit | 弱 | `error` |
| 40–60 bit | 中 | `warning()` |
| 60–80 bit | 强 | `tertiary` |
| > 80 bit | 极强 | `primary` |

---

## 六、Dashboard 卡片集成

### 6.1 卡片定义

在现有 Dashboard 卡片体系中新增一张"密码本"卡片。`DashboardCardConfig.kt` 新增 `VAULT` 卡片类型，与现有资产/账本/目标等卡片统一管理：

| 属性 | 行为 |
|------|------|
| 默认显示 | ✅ 首次启动默认开启 |
| 显隐控制 | 卡片管理 UI 中可关闭 |
| 拖拽排序 | 可拖拽到任意位置 |
| 内容 | **仅显示条数统计** + "进入密码本"（v1.3.0 起不显示条目标题，保护主屏隐私） |
| 点击 | 跳转到 VaultScreen |
| 空状态 | 显示"点击添加第一条密码" |

### 6.2 DataStore Key

```kotlin
val VAULT_CARD_VISIBLE = booleanPreferencesKey("vault_card_visible")
```

> **实现状态：** `vault_card_visible`（在 Dashboard 卡片设置中隐藏密码本入口）**尚未实现**（v1.3.0 暂不支持关闭入口），上述 Key 为设计预留。

默认值 `true`（首次使用的用户可见）。

### 6.3 卡片内容

> **隐私提示（v1.3.0 实现）：** Dashboard 卡片只显示条数统计与"查看"入口，**不展示任何条目标题**（避免在主屏泄露密码条目名）。标题/用户名等敏感元数据仅在进入密码本后可见。

```
┌───────────────────────────────┐
│ 🔒 密码本             共 12 条│
├───────────────────────────────┤
│                               │
│ 已加密保存 N 条密码           │
│                               │
│ 进入密码本 →                  │
└───────────────────────────────┘
```

---

## 七、设置页 "云服务" 开关架构

> 本节为 v2.x 预留设计。v1.x 密码本纯离线，设置页不显示云服务入口。

### 7.1 总开关

```
设置 → 云服务
┌─────────────────────────────┐
│  ☐ 启用云服务              │  ← Switch，默认 OFF
│                             │
│  状态: 未启用               │
│                             │
│  ─── 以下内容仅在开启后显示 ──│
│                             │
│  AI 端点                    │
│  ┌───────────────────────┐  │
│  │ http://192.168.1.100  │  │
│  └───────────────────────┘  │
│  [测试连接]                  │
│                             │
│  云备份                      │
│  类型: [WebDAV           ▼] │
│  地址: ┌─────────────────┐  │
│        │                 │  │
│        └─────────────────┘  │
│  用户: ________  密码: ____ │
│  [测试连接]  [立即备份]     │
│                             │
│  自动备份: ○ 关  ● 每天     │
│                             │
│  最后同步: 从未              │
└─────────────────────────────┘
```

### 7.2 模块加载逻辑

```kotlin
// SettingsViewModel.kt
val cloudServiceEnabled = dataStore.data.map { prefs ->
    prefs[CLOUD_SERVICE_ENABLED] ?: false
}

fun toggleCloudService(enabled: Boolean) {
    viewModelScope.launch {
        dataStore.edit { it[CLOUD_SERVICE_ENABLED] = enabled }
        if (enabled) {
            // 用户已主动开启云服务，发起网络请求
        }
    }
}
```

---

## 八、模块依赖关系

### 8.1 包结构依赖（双模块架构）

密码本全部代码位于 `:app` 模块内的 `com.palmnote.feature.vault` 包（vault 依赖部分来自 `:core`）：

```
app (namespace: com.palmnote.app)
├── feature.vault/          ← 数据/加密层
│    ├── VaultEntry.kt              # Room Entity（vault_entries）
│    ├── VaultDao.kt                # Room DAO
│    ├── VaultRepository.kt         # 仓库
│    ├── VaultCrypto.kt             # AES-256-GCM 原语（仅依赖 JDK crypto）
│    ├── VaultKeyManager.kt         # 密钥管理（salt+key_wrap 包裹/解锁/改PIN/重置）
│    ├── VaultLockManager.kt        # 锁定状态 + 失败次数/锁定时长持久化
│    ├── VaultClipboardManager.kt   # 剪贴板安全（SHA-256 哈希追踪自动清除）
│    ├── VaultPasswordGenerator.kt  # 密码生成器 + 熵强度（SecureRandom，零网络）
│    └── vault/                     # UI 层
│        ├── VaultScreen.kt / VaultViewModel.kt
│        ├── VaultDetailScreen.kt / VaultDetailViewModel.kt
│        ├── VaultEditScreen.kt / VaultEditViewModel.kt
│        ├── VaultLockGate.kt       # PIN 解锁门
│        ├── VaultPasswordGeneratorSheet.kt
│        └── VaultSettingsScreen.kt / VaultSettingsViewModel.kt
├── data/db/VaultDatabase.kt        # 独立 vault 库（palmnote_vault.db，v1-v3，v5 从主库迁出）
├── di/HiltModules.kt               # 注册 vault DAO/密钥/锁定/剪贴板/仓库
└── ui/navigation/Routes.kt         # Vault / VaultDetail / VaultEdit / VaultSettings 路由

core (namespace: com.palmnote，app 依赖)
├── data/datastore/PreferencesManager.kt    # vault_salt / vault_key_wrap / 设置项 key
├── data/db/AppDatabase.kt                  # 主库本体 + migration（v4-v5 曾含 vault_entries，v6 迁出至独立库）
├── domain/repository/VaultRepository.kt    # 接口
└── ui/dashboard/DashboardCardConfig.kt     # VAULT 卡片
```

### 8.2 关键约束

- `com.palmnote.feature.vault` 不依赖任何网络相关代码 — 包结构层面确保密码本无网络依赖
- AI 和云备份功能为 v2.x 预留，当前版本不包含
- AI 功能发送数据前必须弹窗确认，默认只发送 `title` + `url`，`password` 永不发送

---

## 九、锁定策略与剪贴板安全

### 9.1 VaultLockManager 设计

密码本拥有独立于应用锁的锁定状态。`VaultLockManager`（Hilt `@Singleton`）管理锁定/解锁状态切换，委托 `VaultKeyManager` 做实际密钥解锁，并持久化失败次数与锁定时长（SharedPreferences）：

```kotlin
@Singleton
class VaultLockManager @Inject constructor(
    private val keyManager: VaultKeyManager,
    private val clipboardManager: VaultClipboardManager,
    private val preferencesManager: PreferencesManager,
) {
    enum class LockState { NEED_SETUP, LOCKED, UNLOCKED }

    private val _state = MutableStateFlow<LockState>(LockState.LOCKED)
    val state: StateFlow<LockState> = _state.asStateFlow()

    fun initialize()               // 检测 salt 是否存在 → NEED_SETUP / LOCKED
    suspend fun setup(pin: String): Boolean       // 首次设置主密码
    suspend fun setupNoLock(): Boolean            // 无锁模式：跳过密码设置
    suspend fun unlock(pin: String): Boolean      // PIN 验证 = 解包 DK 成功
    suspend fun unlockNoLock(): Boolean           // 无锁模式：Keystore 解开 DK（无需验证）
    suspend fun upgradeToPin(pin: String): Boolean // 无锁 → PIN 锁迁移
    suspend fun unlockWithBiometric(cipher: Cipher): Boolean // 生物识别认证后解 DK
    fun createBioDecryptCipher(): Cipher?         // BiometricPrompt 前 init 解密 Cipher
    suspend fun setupBiometric(): Boolean         // 开启生物识别（需已解锁）
    suspend fun disableBiometric()                // 关闭生物识别
    suspend fun changePin(newPin: String): Boolean
    fun lock()                     // 按自动锁定规则回锁：清 DK + 剪贴板
    suspend fun requireAuth(): Boolean            // vault_require_auth 配置
    suspend fun reset()            // 清空密钥与条目
    fun isLockedOut(): Boolean     // 失败 5 次锁 30 秒（LockoutTracker）
}
```

> 密钥实际持有与加解密在 `VaultKeyManager`（内存 `dataKey`），`VaultLockManager` 负责状态机与防暴力。失败次数/锁定时长由 `LockoutTracker`（`data/lock`，与应用锁复用）写入 SharedPreferences，杀进程不可绕过。锁定状态不写盘，进程被杀默认锁定。

### 9.2 锁定行为

自动锁定规则由 `AutoLockHelper`（`data/lock`）决策，应用锁与密码本共用，用户可配置三种模式：

| 模式 | 回锁时机 |
|------|---------|
| `immediate` | 切到后台约 1 秒后立即回锁 |
| `system`（默认） | 跟随系统锁屏：手机屏锁了才回锁；仅切后台/快速切换不锁（有系统锁 = 用户在场） |
| `timeout` | 手机锁屏 或 切后台超过 5 分钟才回锁 |

| 场景 | 应用锁 | 密码本 |
|------|--------|--------|
| 切到后台 | 按自动锁定规则回锁 | 按自动锁定规则回锁 |
| 应用从锁屏恢复 | 按规则需要解锁 | 需单独解锁 |
| 正在查看密码详情时切应用 | 密码本锁 | 按规则回锁 + 清除密码明文 |
| 关闭应用后再打开 | 需要解锁 | 需要解锁（内存状态丢失，默认锁定） |

**PIN 重新输入流程：**

```
用户点击 Dashboard 卡片 / 从路由进入密码本
    ↓
VaultLockManager.isLocked() == true ?
    ↓ 是
显示 PIN 输入界面（复用 PinComponents.kt，但标题改为"输入密码本密码"）
    ↓ 验证通过
VaultLockManager.unlock() → 进入 VaultScreen
    ↓
用户切到后台 → AutoLockHelper.shouldLock() 判定 → 需回锁则 lock()
```

> **注意：** 密码本锁定独立于应用锁（独立主密码，密钥包裹模式），即使应用锁未超时，进入密码本仍需重新输入密码本主密码（无锁模式除外）；PIN 验证 = 解包数据密钥 DK 成功，与应用锁 PIN 无关。无锁模式下 `vault_require_auth=false`，进入不需验证，但仍按自动锁定规则清除内存密钥。

### 9.3 VaultLockObserver 注册

`VaultLockGate` 是进入密码本 UI 的门控 composable：状态为 `LOCKED` 时显示 PIN 输入界面，`UNLOCKED` 时渲染内容，`NEED_SETUP` 时引导设置主密码。锁定由 `VaultLockManager` 在进入/退出各 Vault 路由时统一管理：

```kotlin
@Composable
fun VaultLockGate(
    lockManager: VaultLockManager,
    content: @Composable () -> Unit,
) {
    val state by lockManager.state.collectAsState()
    when (state) {
        LockState.NEED_SETUP -> VaultSetupPin(...)
        LockState.LOCKED -> VaultPinEntry(...)
        LockState.UNLOCKED -> content()
    }
}
```

每个 Vault 页面（VaultScreen / VaultDetailScreen / VaultEditScreen / VaultSettingsScreen）外层包 `VaultLockGate`；页面退出（`DisposableEffect` onDispose / 路由弹出）调用 `lockManager.lock()`。密码本外部页面（Dashboard、Settings 等）不受密码本锁定策略影响。

### 9.4 剪贴板自动清除

- 复制密码后 30 秒自动清空剪贴板（默认值，用户可在设置中调整）
- 选项：关闭 / 10 秒 / 30 秒 / 60 秒

**追踪机制：** `VaultClipboardManager` 每次密码本执行复制操作时，记录剪贴板内容的 SHA-256 哈希：

```kotlin
@Singleton
class VaultClipboardManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val preferencesManager: PreferencesManager,
) {
    private var pendingHash: String? = null
    private var clearJob: Job? = null

    fun copy(label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        pendingHash = sha256(text)                 // 记录哈希用于后续清除判断
        clearJob?.cancel()
        clearJob = scope.launch {
            val seconds = preferencesManager.vaultClipboardClearSeconds.first()
            if (seconds <= 0) { pendingHash = null; return@launch }  // 0 = 关闭
            delay(seconds * 1000L)
            val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            // 仅清除密码本写入的内容（非用户手动复制的内容）
            if (current != null && sha256(current) == pendingHash) {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            pendingHash = null
        }
    }

    /** 锁定时立即清空自身写入的剪贴板内容。 */
    fun clearIfOwned() { ... }
}
```

> 通过哈希对比确保：不清除用户手动复制的内容；仅清除密码本发起的那一次复制。

---

## 十、设置页集成

### 10.1 设置入口

密码本设置项位于 `设置 → 密码本`：

```
设置
├── 应用锁与密码本（统一入口，含自动锁定规则 [跟系统锁屏 ▼]）
│   ├── 自动锁定规则           [跟系统锁屏 ▼]  ← immediate / system / timeout
│   ├── 密码本：
│   │   ├── 剪贴板自动清除         [30 秒 ▼]
│   │   ├── 进入密码本需验证       [✓]
│   │   ├── 生物识别解锁           [✓]  ← 需硬件 + 已设 PIN/密码
│   │   ├── 已加密条目数: N 条     [不可操作，仅展示]
│   │   ├── 修改主密码             [点击]  ← 验证旧 PIN → 输入新 PIN
│   │   └── 重置密码本             [点击]  ← 二次确认，清空全部
├── 云服务（v2.x 预留，当前不显示）
└── ...
```

> **注意：** v1.3.0 中应用锁与密码本设置已合并为 `设置 → 应用锁与密码本` 单一入口；密码本无锁模式下不显示"修改主密码"，改为"升级为 PIN 锁"。

### 10.2 可配置项

| 设置项 | Key | 类型 | 默认值 | 说明 |
|--------|-----|------|--------|------|
| 自动锁定规则 | `auto_lock_mode` | 枚举 | `system` | `immediate` 立即 / `system` 跟系统锁屏 / `timeout` 超时 5 分钟（应用锁与密码本共用） |
| 剪贴板自动清除 | `vault_clipboard_clear_seconds` | 枚举 | 30 | 0=关闭 / 10 / 30 / 60 秒 |
| 进入需验证 | `vault_require_auth` | Boolean | true | 关闭后切后台不再锁定（安全降级）；无锁模式强制为 false |
| 生物识别解锁 | `vault_biometric_enabled` | Boolean | false | Keystore 密钥包裹 DK，需硬件支持 |
| 加密条目数 | `vault_entry_count` | 只读 | — | 显示 `vault_entries` 表总行数 |
| 修改主密码 / 升级为 PIN 锁 | — | 操作 | — | 密钥包裹模式，改 PIN 无需重加密条目；无锁模式下为 `upgradeToPin` |
| 重置密码本 | — | 操作 | — | 清空密钥（salt/key_wrap/bio/no-lock）与全部条目 |

### 10.3 DataStore Key（追加到 PreferencesManager）

```kotlin
val VAULT_CLIPBOARD_CLEAR_SECONDS = intPreferencesKey("vault_clipboard_clear_seconds")
val VAULT_REQUIRE_AUTH = booleanPreferencesKey("vault_require_auth")
val VAULT_BIOMETRIC_ENABLED = booleanPreferencesKey("vault_biometric_enabled")
val VAULT_NO_LOCK = booleanPreferencesKey("vault_no_lock")
val AUTO_LOCK_MODE = stringPreferencesKey("auto_lock_mode")
```

---

## 十一、未来预留点

### 11.1 AI 功能（v2.x）

| 能力 | 类型 | 数据范围 |
|------|------|---------|
| 密码强度分析 | 本地离线 | 无需联网 |
| 安全审计（重复/弱密码检测） | 本地离线 | 无需联网 |
| 智能分类建议 | 云端 AI（用户端点） | 仅发送 title + url，需用户确认 |
| 安全风险提示 | 云端 AI（用户端点） | 仅发送 URL + 匿名化信息 |

**本地 AI 先行，云端 AI 可选。** 密码强度分析和安全审计完全本地执行，不产生任何网络请求。

### 11.2 云备份（v2.x）

| 维度 | 设计 |
|------|------|
| 备份范围 | 整合到现有 ZIP 备份，vault 数据单独加密为 `.vault` 文件 |
| 加密 | 独立 AES-256-GCM 密钥（与数据库加密密钥不同），上传前已加密，提供商无法读取内容 |
| 传输 | 用户自备 WebDAV（推荐）/ SFTP / **GitHub 仓库** |
| 恢复 | 需要验证密码本主密码解密 vault 部分 |
| 自动备份周期 | 每天 / 每周 / 手动 |

**WebDAV vs SFTP：**

| 对比项 | WebDAV | SFTP |
|--------|--------|------|
| 协议基础 | HTTPS（443 端口） | SSH（22 端口） |
| 防火墙友好 | ✅ 极好 | ⚠️ 部分网络屏蔽 |
| 免费托管服务 | ✅ 多 | ❌ 极少 |
| 配置复杂度 | 低（地址 + 用户名 + 密码） | 低（同上） |

**安全性说明：** App 在上传前已完成 AES-256-GCM 加密，无论使用 WebDAV 还是 SFTP，第三方服务器只能看到密文，无法解读密码内容。

**自建方案推荐（无需第三方）：**

| 方案 | 成本 | 难度 | 说明 |
|------|------|------|------|
| **Alist** | 服务器 5 元/月起 | ⭐ 低 | 单文件二进制，一键启动 WebDAV 服务 |
| **Windows IIS** | **免费** | ⭐ 低 | Windows 自带，开启 WebDAV 发布即可 |
| **rclone serve webdav** | **免费** | ⭐ 低 | 任何电脑运行一条命令：`rclone serve webdav remote:` |
| **NAS（群晖/QNAP）** | 已有设备则免费 | ⭐ 低 | 自带 WebDAV 服务，开关开启即可 |

**免费托管服务（数据已加密，可放心使用）：**

| 服务 | 免费容量 | 备注 |
|------|---------|------|
| InfiniCloud | 10GB | 最推荐，注册即用 |
| Koofr | 10GB | 稳定可靠 |
| TeraCloud | 10GB | 速度快 |
| 坚果云 | 免费版有限速 | 国内用户首选 |

> 不论自建还是使用托管服务，上传前数据均已加密，提供商无法获取明文密码。

**GitHub 仓库方案（可选 Provider）：**

GitHub 不提供 WebDAV 接口，但可通过 GitHub API 将加密备份文件 commit 到用户私人仓库。

```
设置 → 云备份 → GitHub
┌──────────────────────────────┐
│ 仓库: user/vault-backup      │  ← 用户自建私人仓库
│ Token: ghp_xxxxxxxxxxxxxxxx  │  ← Personal Access Token
│                                  仅需 repo 权限，可随时撤销
│ [测试连接]  [立即备份]       │
│ 上次同步: 从未               │
└──────────────────────────────┘
```

| 对比 | WebDAV | GitHub |
|------|--------|--------|
| 费用 | 免费（托管或自建） | **免费**（私人仓库不限量） |
| 账号门槛 | 需注册额外服务 | **已有 GitHub 账号** |
| 历史版本 | 覆盖写入 | ✅ **自带 Git 历史**，可回溯任意版本 |
| 单文件限制 | 无 | 100MB（密码本数据远小于此） |
| 实现方式 | 标准协议，第三方依赖 | GitHub REST API，一个 `GitProvider.kt` 类 |

架构上，`feature:cloud` 采用多 Provider 模式，WebDAV 和 GitHub 作为平级实现：

```
feature/cloud/provider/
├── WebDavProvider.kt   ← 标准实现
├── SftpProvider.kt     ← 标准实现
└── GitHubProvider.kt   ← 通过 GitHub API 读写私人仓库
```

### 11.3 其他预留

- **TOTP 二步验证**：可在现有 `VaultEntry` 中扩展 `totpSecret` 字段（明文存储 base32 key），不影响已有加密流程
- **Autofill 服务**：Android Autofill Framework 接入，需注册 `AutofillService`
- **Passkey 支持**：Android 14+ Credential Manager API，作为单独模块新增

### 11.4 版本路线

| 版本 | 密码本 | AI | 云备份 | 构建 |
|------|--------|----|--------|------|
| v1.2.x | — | — | — | 单版本 |
| v1.3.x | ✅ 已实现（离线/字段级加密/锁定/剪贴板/设置） | — | — | 单版本 |
| v2.0+ | ✅ | 🔜 可选端点 | 🔜 可选 WebDAV / SFTP / GitHub | 单版本 |

---

## 十二、参考项目

本设计参考了以下开源项目的架构与实现经验：

| 项目 | Stars | 技术栈 | 重点学习 |
|------|-------|--------|---------|
| [Monica](https://github.com/JoyinJoester/Monica) | 858 | Kotlin + Compose + Room + Koin | Keystore 加密架构、TOTP、Autofill 服务 |
| [enclave](https://github.com/paoloronco/enclave) | — | Kotlin 2.0.21 + Compose + Room，无 DI | 字段级 AES-256-GCM 加密、手动 DI 下的密码本 CRUD |
| [KeyPass](https://github.com/yogeshpaliyal/KeyPass) | 770 | Kotlin + Compose + Material 3 + MVI | 密码生成器、安全审计、MVI 状态管理 |
| [Cent](https://github.com/glink25/Cent) | 1.1k | PWA + GitHub API | GitHub 仓库作为备份后端验证、增量同步机制、可扩展同步端点 |

---

## 附录：数据库迁移版本历史

| 版本 | 变更 |
|------|------|
| 1 | 初版 |
| 2 | 新增若干表 + 账单索引 |
| 3 | 账单索引补齐（`index_bills_*`） |
| 4 | 金额单位迁移（元 → 分，Migration3To4，×100 换算含已有数据） |
| 5 | 新增 `vault_entries` 密码本表于主库（Migration4To5，历史遗留） |
| 6 | 密码本数据迁出至独立库 `palmnote_vault.db`（Migration5To6，best-effort 搬运后删旧表） |
| **7** | **当前版本（v1.3.0）：删除 `bills` / `bills_recycle_bin` 未使用的 `timeOfDay` 死字段（Migration6To7）** |
