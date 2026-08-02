# PalmNote 密码本模块设计文档

> v1.3 | 2026-07-28 | 与设计规范 [design-spec.md](design-spec.md) 配合阅读
>
> **状态：v1.3.0 已实现**（`feature/vault`，DB v5）。本文档为设计蓝图，实现细节以代码为准。
>
> 本文档为密码本（Vault）功能模块的完整设计。密码本是一个纯离线、字段级加密的密码管理模块，尊重 PalmNote "隐私优先、数据本地化" 的核心原则。

---

## 一、概述与设计目标

### 1.1 产品定位

密码本是一个轻量级离线密码管理器，作为 PalmNote 的一项功能模块，不占用底部 Tab。

- 纯本地存储，无需联网
- 字段级 AES-256-GCM 加密
- 复用应用锁 PIN 进行密钥派生
- Dashboard 卡片快捷入口（可显隐）

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **隐私优先** | 所有敏感数据（密码字段）加密存储，密钥永不离设备 |
| **离线可用** | 核心功能 100% 离线，无需任何网络权限 |
| **零摩擦** | 复用已有 PIN / 生物识别，不引入第二套认证体系 |
| **可选扩展** | 入口可关闭，不影响不使用密码本的用户 |

### 1.3 与现有架构的关系

密码本代码位于 `com.palmnote.feature.vault` 包内（单模块 `:app`，非独立 Gradle 模块），通过 Hilt 依赖注入接入：

```
app (single module)
└── src/main/java/com/palmnote/
    ├── feature/vault/          ← 新增，密码本所有代码
    │   ├── VaultEntry.kt
    │   ├── VaultDao.kt
    │   ├── VaultRepository.kt
    │   ├── VaultEncryption.kt
    │   ├── VaultLockManager.kt
    │   └── vault/              ← UI 层
    │       ├── VaultScreen.kt
    │       ├── VaultDetailScreen.kt
    │       ├── VaultEditScreen.kt
    │       ├── VaultViewModel.kt
    │       └── VaultPasswordGenerator.kt
    ├── data/datastore/
    │   └── PreferencesManager.kt   ← 扩展 vault 相关 key
    ├── di/
    │   └── HiltModules.kt          ← 注册 vault DAO/加密/锁定组件
    └── ui/
        ├── dashboard/
        │   └── DashboardCardConfig.kt  ← 扩展 VAULT 卡片类型
        ├── navigation/
        │   └── AppNavigation.kt     ← 新增 vault 路由
        ├── lock/
        │   └── PinComponents.kt     ← 复用 PIN 输入组件
        └── settings/
            └── SettingsScreen.kt    ← 扩展 vault 设置项
```

密码本通过 Hilt 提供依赖（`@Singleton`），所有密码本类不依赖任何网络相关代码。

### 1.4 功能清单

| 能力 | 类型 | 说明 | 网络依赖 |
|------|------|------|---------|
| **新增密码条目** | CRUD | 录入标题/用户名/密码/网址/备注/分类 | ❌ |
| **密码生成器** | 工具 | 内置随机生成器，可配置长度和字符类型（大小写/数字/符号） | ❌ |
| **列表浏览** | 查看 | 按更新时间倒序展示所有条目 | ❌ |
| **查看密码详情** | 查看 | 展示完整信息，密码默认遮罩，点击 👁 切换明文 | ❌ |
| **编辑条目** | CRUD | 修改已有密码条目所有字段 | ❌ |
| **删除条目** | CRUD | 单条删除 | ❌ |
| **搜索** | 工具 | 实时过滤标题/用户名/网址 | ❌ |
| **分类筛选** | 工具 | 下拉菜单按分类过滤 | ❌ |
| **一键复制** | 工具 | 复制用户名/密码/网址到剪贴板，30 秒后自动清空 | ❌ |
| **Dashboard 快捷入口** | 导航 | 首页卡片显示最近条目 + 条数统计，点击进入密码本 | ❌ |
| **卡片显隐** | 个性化 | 与其他 Dashboard 卡片统一管理，可关闭 | ❌ |
| **复用应用锁 PIN** | 安全 | 进入密码本时验证 PIN / 生物识别 | ❌ |
| **立即锁定** | 安全 | 切到后台即锁定密码本，清除密码明文与剪贴板 | ❌ |
| **智能分类建议** | AI 功能 | 需配置 AI 端点，仅发送 title + url | ✅ 可选 |
| **安全审计** | AI 功能 | 检测弱密码/重复密码/泄露风险，本地离线执行 | ❌ |
| **加密云备份** | 备份 | 加密后备份到用户自备 WebDAV/SFTP | ✅ 可选 |

> **备注：** 所有标注"可选"网络的能力，默认不加载网络代码。用户不主动配置，应用行为与纯离线版本完全一致。

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

当前数据库版本为 **5**（`AppDatabase.kt`），密码本在 `Migration4To5` 中建表（动态分类为规划中，未实现）：

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS vault_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                username TEXT NOT NULL DEFAULT '',
                passwordEncrypted BLOB NOT NULL,
                url TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT '其他',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)
    }
}
```

> 密码本表在 v4 → v5 迁移中独立建表。

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
| `vault_nonce_counter` | GCM nonce 计数器 | 每次加密递增 |

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

密码本作为外层 NavHost 路由（与 Settings、AddBill 同级），不占用底部 Tab。使用类型安全路由（`@Serializable`）：

```kotlin
@Serializable data object Vault
@Serializable data class VaultDetail(val entryId: Long)
@Serializable data class VaultEdit(val entryId: Long? = null)
```

在 `PalmNoteNavHost()` 中添加 composable：

```kotlin
composable(Route.Vault) {
    VaultScreen(
        onNavigateToDetail = { id -> navController.navigate("vault/$id") },
        onNavigateToEdit = { id -> navController.navigate("vault/edit?entryId=$id") },
        onNavigateBack = { navController.popBackStack() }
    )
}

composable(
    Route.VaultDetail,
    arguments = listOf(navArgument("entryId") { type = NavType.LongType })
) { backStackEntry ->
    val entryId = backStackEntry.arguments?.getLong("entryId") ?: 0L
    VaultDetailScreen(
        entryId = entryId,
        onNavigateToEdit = { id -> navController.navigate("vault/edit?entryId=$id") },
        onNavigateBack = { navController.popBackStack() }
    )
}

composable(
    Route.VaultEdit,
    arguments = listOf(navArgument("entryId") { type = NavType.LongType; defaultValue = -1L })
) { backStackEntry ->
    val entryId = backStackEntry.arguments?.getLong("entryId").takeIf { it != -1L }
    VaultEditScreen(
        entryId = entryId,
        onNavigateBack = { navController.popBackStack() }
    )
}
```

### 导航图

```
Dashboard 卡片点击
    ↓
VaultScreen（列表 / 搜索 / 分类）
    ├─ 点击条目 → VaultDetailScreen（查看 / 复制 / 编辑 / 删除）
    └─ 点击新增 → VaultEditScreen（新增 / 编辑表单）
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
| 内容 | 显示最近 3 条条目标题 + "共 N 条密码" |
| 点击 | 跳转到 VaultScreen |
| 空状态 | 显示"点击添加第一条密码" |

### 6.2 DataStore Key

```kotlin
val VAULT_CARD_VISIBLE = booleanPreferencesKey("vault_card_visible")
```

默认值 `true`（首次使用的用户可见）。

### 6.3 卡片内容

```
┌───────────────────────────────┐
│ 🔒 密码本             共 12 条│
├───────────────────────────────┤
│ • GitHub                      │
│ • 网银 - 工商银行              │
│ • admin@example.com           │
│                               │
│ 查看更多 →                    │
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

### 8.1 包结构依赖（单模块架构）

密码本全部代码位于 `:app` 模块内的 `com.palmnote.feature.vault` 包：

```
com.palmnote/
├── feature.vault/          ← 新增
│    ├── VaultEntry.kt
│    ├── VaultDao.kt
│    ├── VaultRepository.kt
│    ├── VaultEncryption.kt     ← AES-256-GCM（仅依赖 JDK crypto）
│    ├── VaultLockManager.kt    ← 生命周期观察 + 锁定状态
│    └── vault/
│        ├── VaultScreen.kt
│        ├── VaultDetailScreen.kt
│        ├── VaultEditScreen.kt
│        ├── VaultPasswordGenerator.kt ← SecureRandom，零网络
│        └── VaultViewModel.kt
├── data/
│    ├── db/AppDatabase.kt      ← 新增 vault_entries 表
│    ├── datastore/PreferencesManager.kt  ← 扩展 vault key
│    └── lock/AppLockManager.kt     ← 独立主密码，与应用锁无关
├── di/HiltModules.kt         ← 注册 vault DAO 与单例组件
├── ui/
│    ├── dashboard/DashboardCardConfig.kt ← 新增 VAULT 卡片
│    ├── navigation/AppNavigation.kt      ← 新增 vault 路由
│    └── lock/PinComponents.kt            ← 复用 PIN 输入组件
└── domain/
     └── repository/VaultRepository.kt    ← 接口
```

### 8.2 关键约束

- `com.palmnote.feature.vault` 不依赖任何网络相关代码 — 包结构层面确保密码本无网络依赖
- AI 和云备份功能为 v2.x 预留，当前版本不包含
- AI 功能发送数据前必须弹窗确认，默认只发送 `title` + `url`，`password` 永不发送

---

## 九、锁定策略与剪贴板安全

### 9.1 VaultLockManager 设计

密码本拥有独立于应用锁的锁定状态。`VaultLockManager` 管理锁定/解锁状态切换：

```kotlin
class VaultLockManager {
    private var isLocked: Boolean = true          // 默认锁定
    private var unlockedAt: Long = 0L
    private var clipboardContentHash: String? = null  // 见 9.3
    
    fun isLocked(): Boolean = isLocked
    fun unlock() { isLocked = false; unlockedAt = System.currentTimeMillis() }
    fun lock() { isLocked = true; clearDecryptedPassword(); clearVaultClipboard() }
    
    /** 清除内存中的明文密码（由 VaultEncryption 持有） */
    fun clearDecryptedPassword() { ... }
    
    /** 清除密码本条目的剪贴板内容 */
    fun clearVaultClipboard() { ... }
}
```

锁定状态仅驻留内存，不写入 DataStore。应用进程被杀后重新进入需要 PIN 重新解锁。

### 9.2 锁定行为

| 场景 | 应用锁 | 密码本 |
|------|--------|--------|
| 切到后台 | 5 分钟后锁定 | **立即锁定** |
| 应用从锁屏恢复 | 需要解锁 | 需单独解锁 |
| 正在查看密码详情时切应用 | 密码本锁 | 密码本立即锁定 + 清除密码明文 |
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
用户切到后台 → onStop → VaultLockManager.lock()
```

> **注意：** 密码本锁定独立于应用锁（独立主密码，密钥包裹模式），即使应用锁未超时，进入密码本仍需重新输入密码本主密码；PIN 验证 = 解包数据密钥 DK 成功，与应用锁 PIN 无关。

### 9.3 VaultLockObserver 注册

`VaultLockObserver` 观察 Fragment/Activity 生命周期，在每个使用密码本的路由页面注册：

```kotlin
class VaultLockObserver(
    private val lockManager: VaultLockManager
) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        lockManager.lock()  // 切后台立即锁定
    }
}
```

**注册方式：** 在 `VaultScreen`、`VaultDetailScreen`、`VaultEditScreen` 的 `DisposableEffect` 中注册：

```kotlin
@Composable
fun VaultScreen(...) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = VaultLockObserver(lockManager)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // ... rest of UI
}
```

密码本外部页面（Dashboard、Settings 等）不注册此 Observer，不受密码本锁定策略影响。

### 9.4 剪贴板自动清除

- 复制密码后 30 秒自动清空剪贴板（默认值，用户可在设置中调整）
- 选项：关闭 / 10 秒 / 30 秒 / 60 秒

**追踪机制：** 每次密码本执行复制操作时，记录剪贴板内容的 SHA-256 哈希：

```kotlin
fun copyToClipboard(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    clipboardManager.setPrimaryClip(clip)
    // 记录哈希用于后续清除判断
    clipboardContentHash = sha256(text)
    scheduleClipboardClear()
}

private fun scheduleClipboardClear() {
    clearJob?.cancel()
    clearJob = coroutineScope.launch {
        delay(clipboardAutoClearMillis)  // 默认 30_000ms
        val current = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
        // 仅清除密码本写入的内容（非用户手动复制的内容）
        if (current != null && sha256(current) == clipboardContentHash) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        clipboardContentHash = null
    }
}
```

> 通过哈希对比确保：不清除用户手动复制的内容；仅清除密码本发起的那一次复制。

---

## 十、设置页集成

### 10.1 设置入口

密码本设置项位于 `设置 → 密码本`：

```
设置
├── 应用锁
├── 密码本            ← 新增
│   ├── 剪贴板自动清除         [30 秒 ▼]
│   ├── 进入密码本需验证       [✓]
│   └── 已加密条目数: N 条     [不可操作，仅展示]
├── 云服务
└── ...
```

### 10.2 可配置项

| 设置项 | Key | 类型 | 默认值 | 说明 |
|--------|-----|------|--------|------|
| 剪贴板自动清除 | `vault_clipboard_clear_seconds` | 枚举 | 30 | 0=关闭 / 10 / 30 / 60 秒 |
| 进入需验证 | `vault_require_auth` | Boolean | true | 关闭后切后台不再锁定（安全降级） |
| 加密条目数 | `vault_entry_count` | 只读 | — | 显示 `vault_entries` 表总行数 |

### 10.3 DataStore Key（追加到 PreferencesManager）

```kotlin
val VAULT_CLIPBOARD_CLEAR_SECONDS = intPreferencesKey("vault_clipboard_clear_seconds")
val VAULT_REQUIRE_AUTH = booleanPreferencesKey("vault_require_auth")
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
| 恢复 | 需要验证应用锁 PIN 解密 vault 部分 |
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
| v1.2.x | ✅ 纯离线 | — | — | 单版本 |
| v1.3.x | ✅ 基础功能完善 | — | — | 单版本 |
| v2.0+ | ✅ | ✅ 可选端点 | ✅ 可选 WebDAV / SFTP / GitHub | 单版本 |

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
| 2 | 新增若干表 |
| 3 | 金额单位迁移（元 → 分，Migration2To3） |
| 4 | 金额 ×100 迁移（Migration3To4） |
| **5** | **当前版本（v1.3.0）：新增 `vault_entries` 密码本表（Migration4To5）** |
