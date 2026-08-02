以下是调整后的完整 **PalmNote 设计规范 v5.1**：

---

# PalmNote 设计规范

> v5.1 | 2026-08-02 | Material3 + Jetpack Compose | 纯本地存储 | Hilt DI
> 本规范涵盖全局设计系统 + 生活模块 + 密码本模块专属规范。生活模块以 `[Life]` 标记，密码本模块以 `[Vault]` 标记。
>
> **v5.1 变更说明：** 安全与 OCR 升级——**数据库 SQLCipher 全库加密**（`EncryptedOpenHelperFactory`，明文库自动迁移）；**OCR 引擎由 ML Kit 替换为 PaddleOCR PP-OCRv6**（`OcrEngine` 接口抽象 + `PaddleOcrEngine`，ONNX Runtime 离线推理，模型打包 assets，release 仅 arm64-v8a）。**对齐修正：15.2 路由改为类型安全路由（`LifeRoute.kt`，`@Serializable`）；15.3 FieldType 标注 19 种扩充为规划蓝图（当前 11 种）；15.1/6.12 动态分类与 15.4 规划标注一致；INTERNET 权限标注预留未声明。**
>
> **v5.0 变更说明：** 架构对齐当前实现——依赖注入从手动 DI（AppContainer）回归 **Hilt**（KSP 编译期）；密码本模块 v1.3.0 已实现（`feature/vault`，字段级 AES-256-GCM + 独立主密码密钥包裹，DB v5 Migration4To5）；15.4 动态分类标注为规划中（当前 `LifeTemplate.category` 保持 `String`）；版本号升 1.3.0。
>
> **v4.7 变更说明：** 取消模块化架构（PalmModule / ModuleRegistry / Tab 动态配置），底栏恢复为固定 4 Tab（首页/物品/账本/生活）。新增模块（密码本、未来 AI/云备份）均通过 Dashboard 卡片入口，不占用底部 Tab。删除第 17 章模块化架构相关内容。
>
> **v4.6 变更说明：** 分类系统重构（规划）：静态三分类 → 用户可定义分类（LifeCategory 实体 / categoryId FK / 动态 Section 渲染 / 分类管理 UI）；FieldType 类型系统统一（enum 扩展至 19 种 / Wizard 全部可用 / DECIMAL 移除）；卡片渲染解耦（基于 layoutType 而非分类）；分类色重命名（时间→纪念）。详见 15.4 节标注。
>
> **v4.5 变更说明：** 生活模板字段类型扩充（TOGGLE / RATING / IMAGE / URL / CURRENCY / TAG）。
>
> **v4.4 变更说明：** 新增密码本模块设计文档（[feature-vault.md](feature-vault.md)）；权限章节补充 INTERNET 运行时请求规范；架构章节补充 Vault + 未来 AI/Cloud 模块引用。
>
> **v4.3 变更说明：** 对齐代码实际实现；修正颜色值（ExpenseRed/IncomeGreen/Amber/底部导航选中色）；修正组件命名（XiaomiSwitch → CapsuleSwitch）；修正模板颜色引用方式（composable 函数 → val 属性）；修正备份方式（WebDAV → 本地 ZIP）；补全技术栈（Paging3/Biometric/Lunar）；修正 AppIcon 查询方式（valueOf → fromName）。

---

## 一、色彩

### 1.1 亮色主题

| Token | 色值 | Material3 通道 |
|-------|------|---------------|
| PrimaryGreen | `#2D4A3E` | primary |
| PrimaryGreenLight | `#4A7A5E` | primaryContainer |
| AccentOrange | `#FF8C42` | secondary |
| StatusActive / Success | `#34A853` | tertiary |
| Warning | `#FFBD44` | 自定义（`ColorScheme.warning()`） |
| ErrorLight | `#EA4335` | error |
| BackgroundLight | `#F8F6F3` | background |
| SurfaceLight | `#FFFFFF` | surface |
| SurfaceVariantLight | `#F2EFE9` | surfaceVariant |
| TextPrimaryLight | `#1C1B1F` | onSurface |
| TextSecondaryLight | `#7A7570` | onSurfaceVariant |
| OutlineLight | `#D0CBC5` | outline |

### 1.2 暗色主题

| Token | 色值 | Material3 通道 |
|-------|------|---------------|
| DarkPrimary | `#7BC4A0` | primary |
| DarkSecondary | `#F0A060` | secondary |
| DarkSuccess | `#66D98D` | tertiary |
| DarkWarning | `#E8B94A` | 自定义 |
| ErrorDark | `#FF6B6B` | error |
| BackgroundDark | `#121212` | background |
| SurfaceDark | `#1E1E1E` | surface |
| SurfaceVariantDark | `#2A2A2A` | surfaceVariant |
| TextPrimaryDark | `#E8E8E8` | onSurface |
| TextSecondaryDark | `#ABABAB` | onSurfaceVariant |
| OutlineDark | `#4A4540` | outline |

### 1.3 模块色调

| 模块 | Light 色值 | Dark 色值 | tint 函数 |
|------|-----------|----------|----------|
| 首页 Dashboard | `#E8F5E9` | `#1B2E1B` | — |
| 资产 Asset | `#E8EFF5` | `#1A2A36` | `assetTint()` |
| 账单 Bill | `#FFF8EE` | `#2E261A` | `billTint()` |
| 目标 Goal | `#EAF4EC` | `#1A2E20` | `goalTint()` |
| 纪念日 Anniversary | `#FFF0EE` | `#2E1A1C` | `anniversaryTint()` |
| 瞬间 Moment | `#F4EFFE` | `#221A30` | `momentTint()` |
| **密码本 Vault** | **`#6750A4`** | **`#D0BCFF`** | **`vaultTint()`** |

### 1.4 语义色

| 场景 | 通道 |
|------|------|
| 打卡/Checkbox 选中 | tertiary |
| 进度条/完成态 | tertiary |
| 高紧急（≤1天） | error |
| 中紧急（≤3天） | secondary |
| 低紧急（≤7天） | warning() |
| 预算超支警告 | error（暗色 `#FF8A8A`） |

### 1.5 自定义颜色（Color.kt 定义）

| 名称 | 色值 | 用途 |
|------|------|------|
| InfoBlue | `#4285F4` | 资产模块蓝色、设置项图标 |
| ModuleLife | `#C2185B` | 生活模块、设置项图标 |
| StatusLost | `#FF6B6B` | 状态丢失 |
| StatusActive | `#34A853` | 状态活跃、设置项图标 |
| IncomeGreen | `#34A853` | 收入（绿） |
| ExpenseRed | `#EA4335` | 支出（红） |
| Amber | `#FBBC04` | 缓存、设置项图标 |
| Purple | `#9C27B0` | 搜索分类图标 |

### 1.6 Switch 颜色

全局 `LocalSwitchColor` 提供，默认 `PrimaryGreen`，用户可在设置页自定义 HEX 色值。

### 1.7 `[Life]` 生活模块色彩体系

#### 1.7.1 预置分类色

预置 3 个分类（用户可新增/重命名/删除），每个分类有独立色调：

| 预置分类 | Light 色值 | Dark 色值 | tint 函数 | 说明 |
|---------|-----------|----------|----------|------|
| 🎯 目标 | `#EDE7F6` | `#1A1530` | `lifePlanTint()` | 靛蓝基调，有完成状态的追踪 |
| 📅 纪念 | `#FFEBEE` | `#301A1A` | `lifeTimeTint()` | 珊瑚红基调，锚定在日期上 |
| 📓 记录 | `#E8F5E9` | `#1A3020` | `lifeRecordTint()` | 翡翠绿基调，持续累积的日志 |

> **判定规则：** 每条模板仅唯一命中一个分类。
> - **目标**：有明确完成状态的模板（存款/学习/待办/旅行/阅读/购物）
> - **纪念**：锚定在一个日期上的模板（生日/纪念日/倒计时/正数日）
> - **记录**：持续累积、无自然终态的模板（日记/心情/打卡/订阅/报告）
>
> 用户创建自定义模板时可自由选择任一分类，也可新增自定义分类。

#### 1.7.2 预设模板专属色

每个预设功能模板拥有独立色彩，用于图标、进度条、Tag、左侧彩色条等。

| 模板 | 色值 | tint 函数 |
|------|------|----------|
| 存钱计划 | `#EC407A` | `lifeSavingColor()` |
| 购物计划 | `#7C8CF0` | `lifeShoppingColor()` |
| 旅行计划 | `#FF7043` | `lifeTravelColor()` |
| 阅读计划 | `#26A69A` | `lifeReadingColor()` |
| 学习计划 | `#AB47BC` | `lifeStudyColor()` |
| 待办任务 | `#5C6BC0` | `lifeTodoColor()` |
| 倒计时 | `#F07070` | `lifeCountdownColor()` |
| 正数日 | `#50C890` | `lifeCountupColor()` |
| 生日 | `#FFCA28` | `lifeBirthdayColor()` |
| 纪念日 | `#F07070` | `lifeAnniversaryColor()` |
| 打卡记录 | `#FF7043` | `lifeHabitColor()` |
| 心情记录 | `#FFCA28` | `lifeMoodColor()` |
| 日记 | `#AB47BC` | `lifeJournalColor()` |
| 专注记录 | `#00ACC1` | `lifeFocusColor()` |
| 订阅记录 | `#66BB6A` | `lifeSubscriptionColor()` |
| 周报月报 | `#42A5F5` | `lifeReportColor()` |

#### 1.7.3 自定义模板颜色选择器

用户创建自定义模板时可从预设色板中选择，或输入自定义 HEX。色板按色相排列（红→橙→黄→绿→青→蓝→紫→灰棕），共 16 色：

```kotlin
val lifeColorPalette = listOf(
    // 红色系
    "#EC407A",  // 存钱计划
    "#F07070",  // 倒计时/纪念日
    "#E53935",  // 深红
    // 橙色系
    "#FF7043",  // 旅行计划/打卡记录
    "#FF8C42",  // AccentOrange
    // 黄色系
    "#FFCA28",  // 生日/心情记录
    // 绿色系
    "#66BB6A",  // 订阅记录
    "#50C890",  // 正数日
    "#26A69A",  // 阅读计划
    // 青蓝色系
    "#00ACC1",  // 专注记录
    "#42A5F5",  // 周报月报
    "#5C6BC0",  // 待办任务
    // 紫色系
    "#AB47BC",  // 学习计划/日记
    "#7C8CF0",  // 购物计划
    // 灰棕色系
    "#78909C",  // 中性灰
    "#8D6E63"   // 暖棕
)
```

**颜色选择器组件规格：**

| 属性 | 规则 |
|------|------|
| 色块尺寸 | 36dp × 36dp，圆角 medium（12dp） |
| 选中态 | 外边框 2dp `primary`，内部对勾图标 16dp 白色 |
| 排列 | 水平 6 个一行，间距 xs（8dp） |
| 自定义输入 | OutlinedTextField，HEX 格式，右侧预览色块 36dp × 36dp |

#### 1.7.4 心情专属色

| 心情 | Emoji | 色值 | 用途 |
|------|-------|------|------|
| HAPPY | 😄 | `#FFCA28` | 心情日历背景、趋势图柱色 |
| NORMAL | 🙂 | `#78909C` | — |
| UPSET | 😔 | `#5C6BC0` | — |
| SAD | 😢 | `#EF5350` | — |
| ANGRY | 😡 | `#E53935` | — |

---

## 二、字型（Typography）

12 级 Material3 Typography：

| Style | 字号 | 行高 | 字重 | 场景 |
|-------|------|------|------|------|
| displaySmall | 24sp | 30sp | Medium | 大号计数器、App 标题 |
| headlineLarge | 22sp | 28sp | Bold | 页面大标题 |
| headlineMedium | 20sp | 26sp | Medium | 问候语、统计数字、记账金额 |
| titleLarge | 18sp | 24sp | Medium | 列表标题、Dialog 标题 |
| titleMedium | 16sp | 22sp | Medium | 卡片标题、BottomSheet 标题、Tab 选中态 |
| titleSmall | 14sp | 20sp | Medium | 设置页 SectionHeader |
| bodyLarge | 16sp | 24sp | Normal | 列表项标题、设置项标题、内容正文 |
| bodyMedium | 14sp | 20sp | Normal | 名称、次要内容、表单占位文字 |
| bodySmall | 12sp | 16sp | Normal | 日期、摘要、副标题、提示文字 |
| labelLarge | 14sp | 20sp | Medium | 按钮文字、键盘保存按钮 |
| labelMedium | 12sp | 16sp | Medium | 标签、Snackbar 操作按钮 |
| labelSmall | 10sp | 14sp | Medium | 分类文字、星期、底部导航文字 |

`[Life]` 生活模块无额外字体，全部复用上述 12 级 Typography。

---

## 三、圆角（Shape）

| Token | 值 | 场景 |
|-------|-----|------|
| extraSmall | 4dp | 进度条圆角 |
| small | 8dp | 图标选择项、输入框圆角、Snackbar、Tag/Chips |
| medium | 12dp | 按钮、输入框、SettingRow 点击区、设置弹窗选项行 |
| large | 16dp | Card 卡片、分类药丸标签、FAB、ModuleCard |
| extraLarge | 24dp | Dialog、BottomSheet |

`[Life]` 生活模块无额外圆角 Token，全部复用上述 5 级 Shape。

---

## 四、间距（4dp 基准网格）

| Token | 值 | 场景 |
|-------|-----|------|
| xxs | 4dp | 文字与图标间、键盘按键间距 |
| xs | 8dp | **卡片间距（统一）、** 表单字段间距、标签间距 |
| sm | 12dp | 并列模块间距、表单字段间距、卡片内容区垂直间距 |
| md | 16dp | Card padding、页面边距、SettingRow 水平内边距、BottomSheet 内容区内边距 |
| lg | 24dp | 大区块间距、Dialog 内边距 |
| xl | 32dp | 页面顶部间距、底部垫底 |

> **v4.2 变更：** 卡片间距统一从 16dp 改为 8dp（包括 Dashboard、Asset、Bill、Life、Detail 页等所有页面的卡片列表）

`[Life]` 生活模块无额外间距 Token，全部复用上述 6 级间距。

---

## 五、图标

### 5.1 全局图标库

**统一使用 `AppIcon` 枚举（120+ 个），定义在 `ui/theme/AppIcon.kt`。**

存储策略：数据库存枚举 name 字符串，UI 层通过 `AppIcon.fromName(name)` 转为 `ImageVector`（带 fallback，不会抛异常）。

### 5.0 应用启动图标（launcher icon）

**应用启动图标为项目原创矢量设计**（`res/drawable/ic_launcher_foreground.xml`），非第三方素材：

- 前景：手写 pathData 的账单/列表线条图形，深绿色 `#2D4A3E`
- 背景：纯色 `#FFFFFF`（`ic_launcher_background.xml`）
- 自适应图标（`mipmap-anydpi-v26`），无位图素材、无外部下载资源，不存在版权/授权问题

（注：`AppIcon` 枚举基于 AndroidX `material-icons-extended` 的 Material Symbols 线性图标，Apache 2.0，用于应用内 UI，与启动图标无关。）

### 5.2 图标尺寸规范

| 场景 | 容器 | 图标大小 |
|------|------|---------|
| 分类标签（IconPicker 网格） | 40dp 圆角方形 | 24dp |
| 分类标签（CategoryGrid） | 44dp 圆角方形 | — |
| 列表项图标 | 40dp 圆形衬底 | 20dp |
| 设置项图标 | 40dp 圆形彩色衬底 | 20dp |
| 弹窗选项图标 | 36dp 圆形彩色衬底 | 20dp |
| FAB 图标（标准） | — | 24dp |
| 空状态图标 | — | 64dp |

### 5.3 `[Life]` 生活模块图标规范

#### 5.3.1 总体规则

生活模块延续全局 `AppIcon` 枚举体系，新增图标全部添加到 `AppIcon.kt`，风格与现有图标保持一致（Material Symbols 线性风格，Rounded）。

存储策略：数据库存枚举 name 字符串，UI 层通过 `AppIcon.fromName(name)` 转为 `ImageVector`（带 fallback，不会抛异常）。

#### 5.3.2 新增 AppIcon 枚举

**功能入口图标：**

| AppIcon 枚举名 | 用途 |
|---------------|------|
| `Assignment` | 预置分类：「目标」图标 |
| `CalendarMonth` | 预置分类：「纪念」图标 |
| `AutoStories` | 预置分类：「记录」图标 |

**预设模板图标：**

| AppIcon 枚举名 | 用途 |
|---------------|------|
| `Savings` | 存钱计划 |
| `ShoppingCart` | 购物计划 |
| `Flight` | 旅行计划 |
| `MenuBook` | 阅读计划 |
| `School` | 学习计划 |
| `EditNote` | 待办任务 / 日记 |
| `HourglassTop` | 倒计时 |
| `TrendingUp` | 正数日 |
| `Cake` | 生日 |
| `Favorite` | 纪念日 |
| `CheckCircle` | 打卡记录 |
| `Timer` | 专注记录 |
| `Autorenew` | 订阅记录 |
| `BarChart` | 周报月报 |

**心情模块图标：**

| AppIcon 枚举名 | 用途 |
|---------------|------|
| `SentimentSatisfied` | 心情模块入口（导航/标题等） |
| `Lightbulb` | 心情洞察卡片 |
| `LocalFireDepartment` | 连续打卡 / 热度 / 七日连击 / 月度坚持 / 百日达人 |

**成就徽章图标：**

| AppIcon 枚举名 | 对应成就 code | 成就名称 |
|---------------|-------------|---------|
| `Spa` | FIRST_CHECKIN | 新手入门 |
| `LocalFireDepartment` | STREAK_7 / STREAK_30 / STREAK_100 | 七日连击 / 月度坚持 / 百日达人 |
| `Bolt` | TOTAL_100 / TOTAL_500 | 闪电侠 / 超级坚持 |
| `EventAvailable` | PERFECT_MONTH | 月度全勤 |
| `Verified` | PERFECT_RATE | 百分达标 |
| `Stars` | MULTI_HABIT | 全能选手 |
| `GpsFixed` | GOAL_ACHIEVED | 目标达成 |
| `EmojiEvents` | ANNUAL_KING | 年度王者 |
| `MilitaryTech` | QUARTER_HERO | 季度标兵 |
| `Nightlight` | NIGHT_OWL | 夜猫子 |

#### 5.3.3 图标颜色规则

| 状态 | 颜色 |
|------|------|
| 默认 | `onSurfaceVariant` |
| 激活/选中 | 模板专属色（如存钱用 `LifeSaving`） |
| 禁用 | `onSurfaceVariant` + 0.3f alpha |
| 强调 | `warning()` |

#### 5.3.4 通用操作图标（生活模块复用全局）

| 操作 | 图标枚举 |
|------|---------|
| 新建/添加 | `AppIcon.Add` |
| 编辑 | `AppIcon.Edit` |
| 删除 | `AppIcon.Delete` |
| 归档 | `AppIcon.Archive` |
| 搜索 | `AppIcon.Search` |
| 筛选 | `AppIcon.FilterList` |
| 排序 | `AppIcon.Sort` |
| 关联 | `AppIcon.Link` |
| 分享 | `AppIcon.Share` |
| 更多 | `AppIcon.MoreVert` / `AppIcon.MoreHoriz` |
| 返回 | `AppIcon.ArrowBack` |
| 关闭 | `AppIcon.Close` |
| 确认/完成 | `AppIcon.Check` |
| 展开 | `AppIcon.ExpandMore` |
| 收起 | `AppIcon.ExpandLess` |
| 图片 | `AppIcon.Image` |
| 拍照 | `AppIcon.PhotoCamera` |
| 日期 | `AppIcon.CalendarToday` |
| 金额 | `AppIcon.AttachMoney` |
| 通知/提醒 | `AppIcon.Notifications` |
| 趋势图 | `AppIcon.ShowChart` |
| 饼图 | `AppIcon.PieChart` |

#### 5.3.5 心情图标——唯一使用 Emoji 的场景

心情场景是整套图标系统的唯一例外。原因：Emoji 面孔比线性图标更能传递情绪温度和直觉性。

**使用 Emoji 的场景（仅以下 4 个）：**

| 场景 | Emoji | 尺寸 |
|------|-------|------|
| 心情选择器 | 😄 🙂 😔 😢 😡 | 32dp（紧凑时 24dp） |
| 心情日历格子内 | 😄 🙂 😔 😢 😡 | 16dp |
| 心情趋势图 X 轴标签 | 😄 🙂 😔 😢 😡 | 18dp |
| 日记底部心情标注 | 对应心情 Emoji | 20dp |

**不使用 Emoji 的场景（仍用 AppIcon）：**

| 场景 | 使用图标 |
|------|---------|
| 心情模块导航入口 | `AppIcon.SentimentSatisfied` |
| 心情统计概览标题 | `AppIcon.SentimentSatisfied` |
| 心情记录按钮/FAB | `AppIcon.Add` |
| 心情洞察卡片 | `AppIcon.Lightbulb` |
| 触发因素·工作 | `AppIcon.Work` |
| 触发因素·健康 | `AppIcon.Favorite` |
| 触发因素·家庭 | `AppIcon.FamilyRestroom` |
| 触发因素·财务 | `AppIcon.AccountBalanceWallet` |
| 触发因素·天气 | `AppIcon.Cloud` |
| 触发因素·社交 | `AppIcon.Groups` |
| 触发因素·睡眠 | `AppIcon.Bedtime` |
| 触发因素·运动 | `AppIcon.FitnessCenter` |
| 触发因素·其他 | `AppIcon.MoreHoriz` |

**心情触发因素交互规范：**

| 属性 | 规则 |
|------|------|
| 选择模式 | 多选（可同时选择多个触发因素） |
| 标签样式 | 水平 Chips 排列，`labelMedium`（12sp） |
| 未选中态 | 边框 1dp `outline`，背景透明，文字 `onSurfaceVariant` |
| 选中态 | 边框 1dp `tertiary`，背景 `tertiary` 12% 透明度，文字 `tertiary` |
| 图标 | 左侧 16dp AppIcon（见上方映射表），跟随选中态变色 |
| 数据存储 | 存储 AppIcon 枚举 name 字符串的 JSON 数组，如 `["WORK","EXERCISE","SOCIAL"]` |
| 自定义因素 | 不支持（固定 9 个预设因素 + "其他"） |

---

## 六、组件

### 6.1 ModuleCard

```kotlin
Card(
    shape = MaterialTheme.shapes.large,          // 16dp
    colors = CardDefaults.cardColors(containerColor = tint),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
) {
    Column(Modifier.padding(12.dp), content)
}
```

| 属性 | 规则 |
|------|------|
| 圆角 | large（16dp） |
| 阴影 | 无（0.dp） |
| 边框 | 1dp `outlineVariant` |
| 内边距 | 12dp |
| 背景色 | `colorScheme.surface`（部分卡片使用模块 tint） |

**点击处理：** `.clip(MaterialTheme.shapes.large).clickable(onClick)`，确保 ripple 裁剪到圆角。

### 6.2 BottomSheet

| 属性 | 规则 |
|------|------|
| 实现 | `AppBottomSheet` 封装（`CommonComponents.kt`）→ `ModalBottomSheet` |
| 展开模式 | `skipPartiallyExpanded = true` |
| 标题 | 左对齐，`titleMedium`（16sp），Bold |
| 内容区内边距 | 水平 24dp，底部 32dp |
| 表单字段间距 | 12dp |
| 背景色 | `colorScheme.surface`，`tonalElevation = 0.dp` |
| 圆角 | 顶部 `RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)` |
| 拖拽手柄 | `BottomSheetDefaults.DragHandle()` |
| 遮罩 | 半透明黑色 scrim |

### 6.3 Dialog（AppDialog 封装）

| 属性 | 规则 |
|------|------|
| 实现 | `AppDialog` 封装（`CommonComponents.kt`），内置 `scaleIn`/`scaleOut` 入场动画 |
| 弹窗形状 | `RoundedCornerShape(20.dp)` |
| 最大宽度 | 560dp |
| 标题 | `titleLarge`（18sp），Bold，距下 8dp |
| 正文 | `bodyMedium`（14sp），距下 16dp |
| 按钮 | `TextButton`，`labelLarge`（14sp） |
| 内边距 | 24dp |
| 按钮间距 | 8dp，`Row(Arrangement.End)` 右对齐 |
| 确认按钮 | 右侧 |
| 取消按钮 | 左侧 |
| 危险操作 | 确认按钮使用 `error` 色 |
| 输入适配 | `.imePadding()` |
| 按钮文案 | 遵循用途（"确认"/"取消"/"清除"/"前往授权"/"完成"） |

### 6.4 Snackbar

| 属性 | 规则 |
|------|------|
| 圆角 | small（8dp） |
| 背景色 | `colorScheme.inverseSurface` |
| 单行提示字号 | `bodyMedium`（14sp） |
| 操作按钮字号 | `labelMedium`（12sp），主题色 |
| 默认时长 | 短操作 3 秒，保存/删除 5 秒 |

### 6.5 FAB

| 类型 | 尺寸 | 颜色 | 使用页面 |
|------|------|------|---------|
| 标准 `FloatingActionButton` | 56dp | `secondary` + `Color.White` | 生活、待办、习惯、倒计时、纪念日、瞬间 |
| 扩展 `ExtendedFloatingActionButton` | 含文字 | `AccentOrange` + `Color.White` | 物品、账本 |
| 圆角 | large（16dp） | — | 全部 |
| 位置 | 右下角，距底部 16dp，距右侧 16dp | — | — |
| 阴影 | Material3 默认（约 6dp） | — | — |

> Asset/Bill 的 FAB 统一使用 `secondary` 色。

#### `[Life]` 展开式 FAB（生活页）

生活页使用 `StaggeredFabMenuItem`，展开动画 `fadeIn` + `slideInVertically`，每项间隔 50ms，背景 0.3f 黑色遮罩。

菜单项 8 个：

| 位置 | AppIcon | 文字 | 模板色 | 动作目标 |
|------|---------|------|--------|---------|
| 1 | `EditNote` | 新建待办 | `lifeTodoColor()` | 跳转待办创建页 |
| 2 | `CheckCircle` | 打卡 | `lifeHabitColor()` | 弹出打卡 BottomSheet |
| 3 | `Savings` | 存一笔 | `LifeSaving` | 弹出存钱 BottomSheet |
| 4 | `SentimentSatisfied` | 记心情 | `lifeMoodColor()` | 弹出心情选择 BottomSheet |
| 5 | `Timer` | 专注 | `lifeFocusColor()` | 进入专注计时页面 |
| 6 | `MenuBook` | 记阅读 | `lifeReadingColor()` | 弹出阅读记录 BottomSheet |
| 7 | `AutoStories` | 写日记 | `lifeJournalColor()` | 跳转日记编辑页 |
| 8 | `MoreHoriz` | 更多 | `onSurfaceVariant` | 展开全部功能入口列表 |

> **说明：** 第 7 项"写日记"使用 `AutoStories` 而非 `EditNote`，避免与第 1 项"新建待办"图标重复造成混淆。

### 6.6 Card

| 属性 | 规则 |
|------|------|
| 圆角 | Dashboard 卡片 extraLarge（24dp），其他 large（16dp） |
| 阴影 | 无（0.dp） |
| 边框 | 1dp `outlineVariant` |
| 内边距 | 16dp |
| 背景色 | `colorScheme.surface` |

#### Dashboard 拖拽排序

- 实现：`Column` + `verticalScroll`（无内部 Scaffold）+ 浮层覆盖 + `positionInWindow()` 全局坐标追踪
- 长按触发：`detectDragGesturesAfterLongPress` + 触觉反馈
- 浮层：缩放 1.04x，阴影 40dp，`zIndex: 200f`
- 原位卡片：`alpha = 0`，`zIndex: 100f`
- 位置公式：`overlayTopPx = cardGlobalY + fingerOffset - boxGlobalY - initialTouchOffset`
- 交换判定：浮层中心越过相邻卡片中心
- 连续跨越依赖：`key(config.type)` 保证手势绑定原卡片
- 防抖动：50ms 节流阀

### 6.7 按钮

| 类型 | 样式 | 圆角 | 高度 |
|------|------|------|------|
| 主操作 | `FilledButton`，`secondary` 色 | medium（12dp） | 48dp |
| 次要操作 | `OutlinedButton`，边框 `secondary` | medium（12dp） | 48dp |
| 文字按钮 | `TextButton`，`secondary` 色 | — | — |
| 危险操作 | `FilledButton`，`error` 色 | medium（12dp） | 48dp |
| 字号 | `labelLarge`（14sp） | — | — |

**例外：** 记账键盘"保存"按钮（84×48dp，圆角 12dp）。

### 6.8 列表项

| 属性 | 规则 |
|------|------|
| 行高 | 单行 56dp，双行 72dp |
| 左侧图标/头像 | 40dp 圆形彩色衬底 + 20dp 图标 |
| 右侧操作图标 | 24dp（ChevronRight 等） |
| 内边距 | 水平 16dp |
| 标题 | `bodyLarge`（16sp） |
| 副标题 | `bodySmall`（12sp），`onSurfaceVariant` |

### 6.9 输入框

| 属性 | 规则 |
|------|------|
| 样式 | `OutlinedTextField`（非填充） |
| 圆角 | medium（12dp） |
| 内边距 | 水平 12dp，垂直 8dp |
| 标签/输入字号 | `bodyMedium`（14sp） |
| 占位文字 | `bodyMedium`，`onSurfaceVariant` |
| 字段间距 | 12dp |

**例外：** 记账页备注输入框 44dp 高，商家入口 36dp 高。

### 6.10 空状态（EmptyState）

| 属性 | 规则 |
|------|------|
| 图标 | 64dp，`onSurfaceVariant` 40% 透明度 |
| 主标题 | `titleMedium`（16sp），Bold，`onSurfaceVariant` |
| 副标题 | `bodyMedium`（14sp），`onSurfaceVariant` 70% 透明度 |
| CTA 按钮 | `GradientButton`（可选），距上 16dp |

#### `[Life]` 空状态差异化文案

每个预设模板有独立的空状态图标和引导文案：

| 模板 | 空状态图标 | 主标题 | 引导文案 |
|------|-----------|--------|---------|
| 存钱计划 | `Savings` | 暂无存钱计划 | 设定一个目标，开始攒钱吧 |
| 购物计划 | `ShoppingCart` | 心愿单是空的 | 添加想买的东西，管理购买计划 |
| 旅行计划 | `Flight` | 暂无旅行计划 | 规划一次旅行，记录美好回忆 |
| 阅读计划 | `MenuBook` | 书架是空的 | 添加想读的书，开始阅读之旅 |
| 学习计划 | `School` | 暂无学习计划 | 添加课程，追踪学习进度 |
| 待办任务 | `EditNote` | 没有待办事项 | 享受轻松时刻，或添加新任务 |
| 倒计时 | `HourglassTop` | 没有倒计时 | 添加一个重要的日子 |
| 正数日 | `TrendingUp` | 暂无正数日 | 记录一个值得纪念的起点 |
| 生日 | `Cake` | 暂无生日记录 | 添加重要的人的生日 |
| 纪念日 | `Favorite` | 暂无纪念日 | 记录那些值得铭记的日子 |
| 打卡记录 | `CheckCircle` | 开始你的第一个习惯 | 选择一个习惯，每天打卡 |
| 心情记录 | `SentimentSatisfied` | 今天还没记录心情 | 记录此刻的感受 |
| 日记 | `AutoStories` | 还没有日记 | 写下今天发生的事 |
| 专注记录 | `Timer` | 暂无专注记录 | 开始一段专注时光 |
| 订阅记录 | `Autorenew` | 暂无订阅记录 | 管理你的订阅服务 |
| 周报月报 | `BarChart` | 暂无报告数据 | 积累数据后自动生成报告 |

### 6.11 Tab

| 属性 | 规则 |
|------|------|
| 选中态 | 下划线 3dp + `primary` 文字 + Bold |
| 未选中态 | 普通字重 + `onSurfaceVariant` |
| Tab 高度 | 40dp |
| 下划线宽度 | 24dp |

`[Life]` 特殊规则：
- 预置分类 Tab 选中色使用对应分类色（`LifePlan` / `LifeTime` / `LifeRecord`）；用户新增的分类使用其自定义色
- 模板内 Tab（如阅读计划的"想读/在读/已读"）选中色使用模板专属色

### 6.12 底部导航栏

`Scaffold.bottomBar` 实现，4 个 Tab：

| Tab | 路由 | 选中色 | 图标大小 |
|-----|------|--------|----------|
| 首页 | `dashboard` | PrimaryGreenLight `#4A7A5E` | 26dp |
| 物品 | `asset` | InfoBlue `#4285F4` | 22dp |
| 账本 | `bill` | AccentOrange `#FF8C42` | 24dp |
| 生活 | `life` | ModuleLife `#C2185B` | 24dp |

#### 尺寸规范

| 属性 | 值 |
|------|-----|
| 内容高度 | 60dp |
| 内部上边距 | 5dp |
| 图标与文字间距 | 2dp |
| 文字样式 | `labelSmall`（10sp） |
| 图标容器 | 26dp Box 居中 |
| 选中态 | filled 图标 + 主题色 |
| 未选中态 | outlined 图标 + `onSurfaceVariant` |
| 对齐 | `CenterVertically` |
| 系统导航栏 | 缓存固定值 `navBarBottomDp`，通过 `Modifier.padding(bottom = ...)` 设置 |
| 入场动画 | **无**（与页面同步出现） |
| 容器 | `Surface` + `background` 色，无阴影 |

#### 架构变更

- 使用 `contentWindowInsets.exclude(WindowInsets.navigationBars)` 防止 Scaffold 双重 inset
- 使用 `WindowInsets.navigationBars.getBottom()` 转换为 Dp 并缓存为固定值，避免双 Scaffold 嵌套时 recomposition 导致的 inset 波动
- 移除了 Dashboard 内部 Scaffold，消除嵌套 Scaffold 的 inset 冲突

#### `[Life]` 生活模块内部导航

当用户处于生活模块内部页面（分类页、模板列表页、详情页等）时，底部导航栏保持全局 4-Tab 结构不变，"生活" Tab 保持选中态。**生活模块内部不替换底部导航栏。**

生活主页（LifeScreen）的内容区域内部使用**可折叠的动态分类入口**（默认 3 个预置分类 + 用户新增分类）代替模块内部二级导航，每个分类区展示该分类下有 ACTIVE 条目的模板卡片。用户点击分类区不跳转页面，直接浏览下方模板卡片列表。分类区支持自定义排序和显隐。**（⚠️ 动态分类为 v4.6 设计蓝图，规划中未实现；当前按硬编码三分类分组，详见 15.4。）**

### 6.13 TopAppBar

`CompactTopAppBar`，`background` 色。

| 区域 | 规则 |
|------|------|
| 标题 | `displaySmall`（24sp），Bold，`primary` 色 |
| 右侧操作图标 | 按模块色调上色（首页 `primary` / 物品 `InfoBlue` / 账本 `AccentOrange` / 搜索 `primary`） |
| 返回按钮 | 默认 `onSurfaceVariant`（搜索页使用 `primary`） |

**例外：** 搜索页顶栏使用自定义 Surface + Row 布局（非 CompactTopAppBar），搜索框胶囊样式 48dp 高。

### 6.14 CapsuleSwitch（胶囊开关）

| 属性 | 值 |
|------|-----|
| 轨道尺寸 | 46×26dp |
| 滑块尺寸 | 20dp 圆形 |
| 选中色 | `LocalSwitchColor.current`（默认 PrimaryGreen，用户可自定义 HEX） |
| 未选中色 | `surfaceVariant` |

### 6.15 设置页组件

| 组件 | 规则 |
|------|------|
| `SettingRow` | 56dp 行高，12dp 上下 + 16dp 左右内边距，`clip(medium)` + `clickable` |
| `SettingRowContent` | 标题 `bodyLarge` + 副标题 `bodySmall`，右侧可选值 + ChevronRight |
| `SettingsMenuItem` | 带 40dp 圆形彩色衬底 + 20dp 线性图标，`padding(vertical = 12.dp, horizontal = 16.dp)` |
| `SectionHeader` | `titleSmall`，Bold，`onSurfaceVariant`，距上 8dp |

**分隔线规则：**
- SettingRow 之间：`HorizontalDivider(padding(horizontal = 16.dp))`
- SettingsMenuItem 之间：`HorizontalDivider(padding(vertical = 4.dp))`
- 弹窗选项行之间：`HorizontalDivider(padding(horizontal = 52.dp))`

**选择弹窗规范：**
- 每行：36dp 圆形彩色衬底 + 20dp 图标 + 12dp 间距 + 文字 + RadioButton
- 行高：8dp 上下内边距
- 圆角：medium（12dp）

### 6.16 选择弹窗（Picker Dialog）

| 属性 | 规则 |
|------|------|
| 选项布局 | 36dp 圆形彩色衬底 + 20dp 线性图标 + 12dp 间距 + `bodyLarge` 文字 + RadioButton |
| 行高 | 8dp 上下 + 8dp 左右内边距 |
| 点击行 | `clip(medium)` + `clickable`，确认后关闭弹窗 |
| 分隔线 | `padding(horizontal = 52.dp)`（对齐图标后文字起始位置） |

### 6.17 `[Life]` 生活模块专属组件

#### 6.17.1 列表卡片（生活条目）

复用 ModuleCard 基础规范，扩展如下：

| 属性 | 规则 |
|------|------|
| 最小高度 | 72dp |
| 内边距 | 16dp |
| 圆角 | large（16dp） |
| 卡片间距 | xs（8dp） |
| 左侧彩色条 | 3dp 宽，颜色=模板专属色，圆角 extraSmall（4dp） |
| 状态 Tag | `labelMedium`（12sp），`padding(2.dp, 8.dp)`，圆角 small（8dp），背景=模板色 12% 透明度 |
| showInCard 字段 | 最多展示 3 个 |
| 进度条 | 高度 extraSmall（4dp），圆角 extraSmall（4dp），背景 `surfaceVariant`，填充=模板色 |
| 关联标签 | 最多 2 个，超出显示"+N" |

**左滑操作：** `SwipeToDismissBox`，背景 `errorContainer`。
**长按：** `HapticFeedback.LongPress`，进入多选模式。

**条目数量预警：**

| 阈值 | 行为 |
|------|------|
| 8,000 条 | Snackbar 提示"该模板已有 {count} 条数据，建议归档旧条目" |
| 9,500 条 | 创建时弹窗提醒"即将达到上限（{count}/10,000），建议归档" |
| 10,000 条 | 创建按钮禁用，提示"已达上限，请归档或删除旧条目" |

#### 6.17.2 关联选择器（BottomSheet）

复用 AppBottomSheet 基础规范：

| 属性 | 规则 |
|------|------|
| 标题 | "关联到" |
| 搜索框 | OutlinedTextField，48dp 高 |
| 分组标题 | `titleSmall`，Bold，`onSurfaceVariant`，左侧分类图标 20dp |
| 搜索结果 | 列表项 56dp 行高，左侧图标 + 标题 + 副标题 |
| 已关联项 | Tag 样式展示，可点击移除 |

**关联类型 Tag 展示文案：**

| linkType | 用户可见 Tag 文案 |
|----------|------------------|
| PURCHASED_FROM | 计划购买 |
| BUDGET_FOR | 预算 |
| SAVED_TO | 存入 |
| BELONGS_TO | 来自计划 |
| USED_FOR | 使用 |
| PART_OF | 关联计划 |
| TRIGGERED_BY | 触发 |
| SUBSCRIPTION_BILL | 订阅扣费 |
| WISH_ACHIEVED | 心愿达成 |
| BIRTHDAY_GIFT | 生日礼物 |
| BIRTHDAY_GIFT_ASSET | 礼物物品 |

#### 6.17.3 关联展示组件

在详情页底部展示：

| 属性 | 规则 |
|------|------|
| 分组标题 | `titleSmall`，Bold，左侧图标 + 分类名 + 有效关联数量 |
| 折叠/展开 | 默认折叠，点击展开，动画 200ms `FastOutSlowInEasing` |
| 条目 | 列表项样式，左侧图标 20dp + 标题 `bodyMedium` + 关联类型 Tag |
| 点击 | 跳转到关联实体详情页 |
| 空状态 | "暂无关联" |
| 数量规则 | 只统计有效关联（metadata 不含 `"deleted": true`），超过 99 显示"99+"，0 个时不显示分组标题 |
| 已删除实体 | 关联条目展示"关联的{类型}已删除"提示文字，灰色 `onSurfaceVariant` |

#### 6.17.4 环形进度（存钱计划/学习计划等）

| 属性 | 规则 |
|------|------|
| 尺寸 | 64dp × 64dp（列表卡片内）/ 96dp × 96dp（详情页） |
| 线宽 | 6dp |
| 圆角端点 | `StrokeCap.Round` |
| 背景环 | `surfaceVariant` |
| 填充环 | 模板专属色 |
| 百分比文字 | `headlineMedium`（20sp），Bold，居中 |

#### 6.17.5 热力图（习惯打卡）

| 属性 | 规则 |
|------|------|
| 网格 | 7 行（周一到周日）× N 列（周数，最多 52） |
| 单元格 | 正方形，最小 12dp × 12dp，圆角 extraSmall（4dp） |
| 颜色映射 | `tertiary` 色，透明度 0.08（无数据）到 1.0（达标） |
| 行标签 | `labelSmall`（10sp），`onSurfaceVariant` |
| 列标签 | `labelSmall`（10sp），`onSurfaceVariant`，每月 1 日所在周左侧标注月份名（如"1月""2月"），空间不足时只标注奇数月 |
| 空单元格 | `surfaceVariant`，透明度 0.3 |
| 渲染策略 | 一次性渲染全部单元格（最多 364 个 Composable，无性能问题） |

#### 6.17.6 专注计时器

| 属性 | 规则 |
|------|------|
| 进度环 | 120dp × 120dp，线宽 8dp |
| 时间数字 | `displaySmall`（24sp），Bold，`onSurface` |
| 副标题 | `bodySmall`（12sp），`onSurfaceVariant` |
| 操作按钮 | 标准 FilledButton / OutlinedButton，48dp 高 |
| 预设时长选择 | Chips 水平排列，间距 xxs（4dp） |

#### 6.17.7 成就徽章

| 属性 | 规则 |
|------|------|
| 已解锁 | 图标 32dp，`tertiary` 色，背景 `tertiary` 12% 透明度，圆角 large（16dp） |
| 未解锁 | 图标 32dp，`onSurfaceVariant` 30% 透明度 + grayscale 滤镜，背景 `surfaceVariant`，圆角 large（16dp） |
| 间距 | 网格布局，间距 sm（12dp） |
| 标题 | `bodySmall`（12sp），`onSurfaceVariant` |
| 点击 | 展示 `AppDialog`，包含图标 48dp + 名称 + 描述 + 解锁时间（已解锁）或解锁条件（未解锁） |

#### 6.17.8 日历视图

多个模板支持 CALENDAR 布局（待办、倒计时、生日、纪念日、心情日历）。

| 属性 | 规则 |
|------|------|
| 月份导航 | 左右箭头按钮（`AppIcon.ChevronLeft` / `AppIcon.ChevronRight`）+ 水平滑动手势 |
| 切换动画 | 300ms 水平滑动（左滑下一月，右滑上一月） |
| 月份标题 | `titleMedium`（16sp），Bold，居中 |
| 星期标题 | 7 列，`labelSmall`（10sp），`onSurfaceVariant`，文字：一 二 三 四 五 六 日 |
| 日期格子 | 圆角 small（8dp），padding 6dp，`bodyMedium`（14sp） |
| 今天高亮 | `primary` 色圆形背景（32dp） + 白色文字，Bold |
| 有数据标记 | 日期下方 4dp 圆点，颜色 = 模板专属色 |
| 点击行为 | 底部展开当日条目列表（BottomSheet，200ms 动画） |
| 非当月日期 | `onSurfaceVariant` 40% 透明度 |
| 空格子 | `visibility: invisible` |

**心情日历特殊规则：**
- 日期格子背景色 = 心情专属色 12% 透明度
- 格子内显示 16dp 心情 Emoji（非圆点标记）
- 无心情记录的日期显示普通格子样式

#### 6.17.9 关联展示区布局（详情页底部）

```
详情页底部关联区
├── 默认状态：折叠，仅显示分组标题 + 数量
├── 展开状态：
│   ├── 账本关联（图标 + "账本" + 数量）
│   │   ├── 关联条目 1（20dp 图标 + title + linkType Tag）
│   │   └── 关联条目 2 ...
│   ├── 物品关联（图标 + "物品" + 数量）
│   │   └── ...
│   ├── 生活条目关联（图标 + "生活条目" + 数量）
│   │   └── ...
│   └── "+ 添加关联" 按钮
└── 空状态："暂无关联" + "添加关联" 按钮
```

---

## 七、页面过渡与动画

### 7.1 页面切换

| 场景 | 动画 | 参数 |
|------|------|------|
| Tab 切换 | fade 淡入淡出 | `tween(200)` |
| Push 进入子页面 | fade 淡入 | `tween(200)` |
| Pop 返回 | fade 淡出 | `tween(200)` |

> 所有页面切换统一使用 fade，无滑动方向。

### 7.2 微交互

| 场景 | 时长 | 缓动 |
|------|------|------|
| 折叠/展开 | 200ms | `FastOutSlowInEasing` |
| 进度条动画 | 800ms | Linear |
| 卡片入场 | 300ms slideUp(20dp) + fadeIn，每项 50ms 交错 | `FastOutSlowInEasing` |
| 记账金额跳动 | 300ms | scale 1.0→1.1→1.0 |
| 错误抖动 | 200ms | `FastOutSlowInEasing` |
| 预算提示行展开 | 200ms | `animateContentSize()` |
| 拖拽浮层缩放 | 逐帧 | `graphicsLayer { scaleX/Y = 1.04f }` |
| FAB 菜单展开 | fadeIn + slideInVertically | 每项延迟 50ms |

`[Life]` 生活模块补充：

| 场景 | 时长 | 缓动 |
|------|------|------|
| 存钱进度环动画 | 800ms | `FastOutSlowInEasing` |
| 热力图单元格入场 | 600ms fadeIn，每格 20ms 交错 | Linear |
| 成就解锁动画 | 500ms scale(0→1) + fadeIn | OvershootInterpolator |
| 倒计时数字变化 | 200ms | `FastOutSlowInEasing` |
| 关联区域折叠/展开 | 200ms | `animateContentSize()` |
| 模板创建向导步骤切换 | 300ms fade | `FastOutSlowInEasing` |
| 日历月份切换 | 300ms 水平滑动 | `FastOutSlowInEasing` |
| 日历当日条目展开 | 200ms | `animateContentSize()` |

### 7.3 手势

| 手势 | 实现 | 反馈 |
|------|------|------|
| 左滑删除 | `SwipeToDismissBox` | 背景 `errorContainer` |
| 长按 | `HapticFeedback.LongPress` | 触觉反馈 |
| 长按拖拽换位 | `detectDragGesturesAfterLongPress` | 跟随手指无延迟 |
| 点击防抖 | 300ms | — |
| 返回确认 | 表单有未保存数据时弹出 Dialog | — |
| 日历左右滑动 | `detectHorizontalDragGestures` | 切换月份 |

---

## 八、权限请求

| 权限 | 触发时机 | UI 流程 |
|------|---------|---------|
| `WRITE_CALENDAR` | 开启「同步纪念日到系统日历」 | 开关 → 弹窗说明 → 系统权限弹窗 → 授权后开启 |

`[Life]` 补充：

| 权限 | 触发时机 | UI 流程 |
|------|---------|---------|
| `ACCESS_FINE_LOCATION` | 生活日记获取位置 | 首次使用日记时 → 弹窗说明 → 系统权限弹窗 → 授权后自动获取 |

> **说明：** 位置权限仅用于日记自动获取位置名称和经纬度。天气和温度由用户手动选择/输入，不依赖网络。

`[Vault]` 补充（云服务为 v2.x 预留设计，当前未实现）：

| 权限 | 触发时机 | UI 流程 |
|------|---------|---------|
| `INTERNET` | **预留**：用户首次在设置中开启云服务（AI 或云备份） | 云服务总开关 → 说明弹窗（数据流向说明） → 启用。当前版本（v1.3.0）**未声明** `INTERNET` 权限，应用无法联网；仅在后续版本实现云服务并按需声明后使用 |

---

## 九、日期/数字格式化

通过 `DateUtils` / `CurrencyUtils` 统一调用。

- 货币符号与数字间无空格：`¥1,234`
- 数字与中文间无空格：`3天` 非 `3 天`
- 日期格式：`yyyy-MM-dd HH:mm`
- 紧凑格式：`MM/dd`

`[Life]` 补充——倒计时/正数日展示规则：

| 场景 | 展示格式 | 示例 |
|------|---------|------|
| COUNTDOWN 剩余 > 0 | "还剩 N 天" | 还剩 27 天 |
| COUNTDOWN 剩余 = 0 | "今天" | 今天 |
| COUNTDOWN 剩余 < 0 | "已过 N 天" | 已过 3 天 |
| TIMER 已过天数 | "已 N 天" | 已 365 天 |
| 日期差计算 | 使用 `java.time.LocalDate` 计算日历天数差，避免时区和夏令时问题 | — |
| 同一天判定 | 两个日期在用户本地时区的 `LocalDate` 相等即为同一天 | — |

`[Life]` 补充——金额字段存储与展示：

| 环节 | 格式 | 说明 |
|------|------|------|
| 用户输入 | `KeyboardType.Decimal` 系统数字键盘（复用记账页金额输入方案） | 统一输入体验 |
| 存储 | Long 类型，以**分**为单位 | ¥599.00 → 59900 |
| 展示 | `CurrencyUtils` 格式化为 ¥X.XX | 千分位 + 两位小数 |
| 统计聚合 | Long 类型直接求和 | 结果再格式化展示 |

---

## 十、文案规范

| 规则 | 示例 |
|------|------|
| 语调 | 友好、简洁、直接 |
| 人称 | 不用"您"，直接说事 |
| 删除确认 | "确认删除？此操作不可撤销" |
| 清除数据确认 | "确定要清除${label}吗？此操作不可恢复！" |
| 缓存清除确认 | "确定要清除图片缓存和临时文件吗？不会影响你的个人数据。" |
| 按钮文案 | 遵循用途（"确认"/"取消"/"清除"/"前往授权"/"完成"/"恢复"/"删除"） |
| 备注占位 | "点击输入备注...（可选）" |
| 商家占位 | "商家名称（可选）" |
| 加载中 | "加载中..." |
| 空状态 | "暂无备份" / "还没有生活瞬间" / "今天还没记录" 等具体文案 |

`[Life]` 生活模块补充：

| 场景 | 文案 |
|------|------|
| 存钱达成 | "恭喜达成心愿！是否创建对应物品？" |
| 购物查重 | "你已拥有同类物品「${name}」（购于 ${date}，${price}）" |
| 记账匹配购物计划 | "这笔消费和你的「购物计划·${name}」匹配，是否标记为已购？" |
| 生日提醒礼物历史 | "去年你送了 ${gift}（${price}），今年预算多少？" |
| 订阅扣费提醒 | "${name} 订阅今日扣费 ${price}" |
| 模板删除确认 | "该模板下有 ${count} 条数据，删除后数据将归档，你可以在回收站中恢复。" |
| 预算结余建议 | "本月预算省了 ${amount}，要转入心愿单吗？" |
| 成就解锁 | "🎉 成就解锁：${name}！${description}" |
| 自定义模板字段上限 | "最多添加 20 个自定义字段" |
| 正数日里程碑 | "🎉 你的「${title}」已经坚持了 ${days} 天！" |
| 条目数量预警 | "该模板已有 ${count} 条数据，建议归档旧条目" |
| 条目数量上限 | "已达上限（10,000 条），请归档或删除旧条目" |
| 关联实体已删除 | "关联的${type}已删除" |

---

## 十一、图表

| 类型 | 场景 | 颜色 |
|------|------|------|
| 折线图 | 净资产、收支趋势 | 单系列 `primary` |
| 柱状图 | 分类对比、年度总览 | 双系列 `error`+`tertiary` |
| 环形图 | 分类占比 | 多系列按顺序取色 |
| 热力图 | 习惯打卡密度 | `tertiary` 透明度渐变 |

`[Life]` 生活模块补充：

| 类型 | 场景 | 颜色 |
|------|------|------|
| 柱状图 | 心情月度分布 | 5 级心情色（HAPPY/NORMAL/UPSET/SAD/ANGRY） |
| 柱状图 | 习惯完成率趋势 | 模板专属色 |
| 折线图 | 专注时长趋势 | `lifeFocusColor()` |
| 环形图 | 阅读分类占比 | 多系列按顺序取色 |
| 条形图 | 订阅月度支出 | `lifeSubscriptionColor()` |
| 条形图 | 心情触发因素频率 | `onSurfaceVariant` |
| 双轴折线图 | 心情 vs 消费趋势 | 左轴心情色、右轴 `ExpenseRed` |

---

## 十二、记账页面

专用布局，不受以上组件规则覆盖：

| 元素 | 尺寸 |
|------|------|
| 备注输入框 | 44dp 高 |
| 商家入口 | 36dp 高（展开后） |
| 保存按钮 | 84×48dp，圆角 12dp |
| 金额输入 | `KeyboardType.Decimal`（系统数字键盘） |

`[Life]` 生活模块中的金额输入：

| 场景 | 输入方案 |
|------|---------|
| 存钱记录存入金额 | 复用记账页金额输入方案（`KeyboardType.Decimal` 系统数字键盘 + `CurrencyUtils` 格式化） |
| 购物计划参考价格 | 同上 |
| 订阅记录扣费金额 | 同上 |

> **说明：** "复用记账键盘"指的是复用记账页的金额输入方式（`KeyboardType.Decimal` 系统数字键盘 + 格式化逻辑），不是复用某个自定义键盘组件代码。

---

## 十三、编码约定

### 13.1 禁止事项

```kotlin
// ❌ 硬编码颜色
private val MyColor = Color(0xFF...)

// ❌ 绕过主题形状
shape = RoundedCornerShape(16.dp)

// ❌ 硬编码白色背景
containerColor = Color.White

// ❌ 直接格式化日期
SimpleDateFormat("yyyy-MM-dd")

// ❌ 直接拼接货币
"¥${amount}"

// ❌ 混用按钮文案
"确定" / "OK" / "好的"  // 统一用"确认"

// ❌ 硬编码间距
.padding(15.dp)  // 使用 4dp 网格

// ❌ clickable 前缺 clip（ripple 溢出）
.clickable(onClick)  // 改为 .clip(shapes.medium).clickable(onClick)

// ❌ LazyColumn items() 缺 key
items(list)  // 改为 items(list, key = { it.id })
```

### 13.2 颜色引用

```kotlin
MaterialTheme.colorScheme.primary          // 品牌主色 PrimaryGreen
MaterialTheme.colorScheme.secondary        // AccentOrange
MaterialTheme.colorScheme.tertiary         // Success/完成态
MaterialTheme.colorScheme.error            // 错误/删除
MaterialTheme.colorScheme.background       // 页面背景
MaterialTheme.colorScheme.surface          // 卡片背景
MaterialTheme.colorScheme.onSurface        // 主要文字
MaterialTheme.colorScheme.onSurfaceVariant // 次要文字
MaterialTheme.colorScheme.outline          // 边框
MaterialTheme.colorScheme.warning()        // 警告色
LocalSwitchColor.current                   // 开关选中色
```

### 13.3 `[Life]` 生活模块颜色引用

```kotlin
// 预置分类色（val 属性，非 composable）
LifePlan = Color(0xFF7C8CF0)       // 预置分类「目标」
LifeTime = Color(0xFFF07070)       // 预置分类「纪念」
LifeRecord = Color(0xFF50C890)     // 预置分类「记录」

// 预设模板色（val 属性）
LifeSaving = Color(0xFFEC407A)       // 存钱计划
LifeShopping = Color(0xFF7C8CF0)     // 购物计划
LifeTravel = Color(0xFFFF7043)       // 旅行计划
LifeReading = Color(0xFF26A69A)      // 阅读计划
LifeStudy = Color(0xFFAB47BC)        // 学习计划
LifeTodo = Color(0xFF5C6BC0)         // 待办任务
LifeCountdown = Color(0xFFF07070)    // 倒计时
LifeCountUp = Color(0xFF50C890)      // 正数日
LifeBirthday = Color(0xFFFFCA28)     // 生日
LifeHabit = Color(0xFFFF7043)        // 打卡记录
LifeMoodColor = Color(0xFFFFCA28)    // 心情记录
LifeJournal = Color(0xFFAB47BC)      // 日记
LifeFocus = Color(0xFF00ACC1)        // 专注记录
LifeSubscription = Color(0xFF66BB6A) // 订阅记录
LifeReport = Color(0xFF42A5F5)       // 周报月报

// 心情色（val 属性）
LifeMoodHappy = Color(0xFFFFCA28)   // 开心
LifeMoodNormal = Color(0xFF78909C)  // 平静
LifeMoodUpset = Color(0xFF5C6BC0)   // 心烦
LifeMoodSad = Color(0xFFEF5350)     // 难过
LifeMoodAngry = Color(0xFFE53935)   // 生气
```

---

## 十四、数据备份

| 属性 | 规则 |
|------|------|
| 备份目标 | App 专属外部存储 `getExternalFilesDir(DIRECTORY_DOWNLOADS)/PalmNote/` |
| 备份格式 | ZIP 打包（DB + DataStore + 图片） |
| 文件名 | `palmnote_backup_{timestamp}.palmnote` |
| 恢复方式 | 文件列表中选择 → 确认弹窗 → 关闭 Room → 解压覆盖 → 强制重开 Room |
| 存储权限 | 无需额外权限（使用 `getExternalFilesDir`） |

`[Life]` 生活模块备份扩展：

**新增备份表：** life_templates、life_items、cross_links、achievements、focus_records

**恢复规则：**

| 表 | 恢复策略 |
|-----|---------|
| life_templates | 按 `name + category` 去重，已存在则跳过 |
| life_items | 按 `templateId` 匹配恢复后 template 的新 ID 重新映射 |
| cross_links | 按 `targetType` 区分处理：BILL/ASSET 类型直接插入不修改 ID；ITEM 类型按 `createdAt` 匹配恢复后的 LifeItem 重新映射 ID |
| achievements | 按 `code` 去重，已存在则跳过 |
| focus_records | 直接插入 |

**图片处理：**

| 操作 | 图片处理策略 |
|------|------------|
| 备份 | `filesDir/images/life/` 下所有图片纳入 ZIP 打包 |
| 恢复 | 图片解压到 `filesDir/images/life/`，路径中的 `{itemId}` 按新 ID 重建目录结构 |
| 归档 | 图片保留不删除 |
| 物理删除条目 | 同步删除 `filesDir/images/life/{itemId}/` 整个目录 |

**图片存储约束：**

| 约束 | 值 |
|------|-----|
| 存储路径 | `filesDir/images/life/{itemId}/{filename}` |
| 压缩方式 | `BitmapFactory.Options.inSampleSize` 初步降采样 + `Bitmap.compress(JPEG, quality=85)` |
| 尺寸限制 | 长边最大 1080px（按比例缩放短边） |
| EXIF 处理 | 保留旋转信息，移除 GPS 坐标（隐私保护，除非用户明确开启日记位置记录） |
| 压缩线程 | 异步执行在 IO 线程 |
| 单条目上限 | `config.maxImages`，默认 9 张 |

---

## 十五、`[Life]` 生活模块导航结构

### 15.1 生活主页（LifeScreen）

> **⚠️ 本节为动态分类重构（v4.6 设计蓝图）的目标形态，尚未实施。** 当前实现为硬编码三分类（`PLAN`/`TIME`/`RECORD`），按模板 `category` 值分组渲染，无 `life_categories` 表、无动态分类区。详见 15.4 节规划标注。

```
LifeScreen（动态 Section 渲染）
├── TopAppBar：标题 "生活"，primary 色，右侧搜索图标
├── 问候区：日期 + 问候语 + 心情快捷入口（AppIcon.SentimentSatisfied）
├── 概览区：3 个数据卡片（待办数 / 习惯完成率 / 今日专注时长）
├── 通知区：订阅提醒 / 生日提醒 / 预算结余提醒
├── 动态分类区：遍历所有 LifeCategory，每个渲染一个可折叠 LifeSection
│   ├── 🎯 目标区（预置）：可折叠，展示 ACTIVE 条目摘要
│   ├── 📅 纪念区（预置）：可折叠，展示天数计算 + 即将到来
│   ├── 📓 记录区（预置）：可折叠，展示今日待打卡 + 最近记录
│   ├── 健康区（用户新增）：可折叠，展示对应模板条目
│   └── ...（用户可新增/重命名/删除分类）
└── FAB：展开式 8 个菜单项
```

> **分类区渲染规则：** LifeScreen 从 `life_categories` 表读取全部分类，按 `sortOrder` 排序，遍历渲染。每个 Section 的卡片样式由模板的 `layoutType` 决定（PROGRESS→PlanCard, DATE_COUNT→TimeCard, TIMELINE→TimelineCard, STATS→StatsCard），不再依赖分类名称硬编码判断。
>
> **空分类：** 分类下所有模板均无数据时，该 Section 折叠显示分类标题+模板图标摘要。

### 15.2 路由定义

`[Life]` 模块内嵌 NavHost，使用类型安全路由（`ui/life/LifeRoute.kt`，`@Serializable`）：

```kotlin
// 生活主页
@Serializable data object LifeHomeRoute

// 动态路由（携带参数）
@Serializable data class LifeTemplateRoute(val templateId: Long)   // 模板列表页
@Serializable data class LifeItemRoute(val itemId: Long)           // 条目详情页
@Serializable data class LifeCreateRoute(val templateId: Long)     // 新建条目（Wizard）
@Serializable data class LifeEditRoute(val itemId: Long)           // 编辑条目

// 通用页面（固定路由）
@Serializable data object LifeFocusRoute      // 专注计时
@Serializable data object LifeHabitRoute      // 习惯打卡
@Serializable data object LifeMoodRoute       // 心情记录
@Serializable data object LifeJournalRoute    // 日记
@Serializable data object LifeTodoRoute       // 今日待办
@Serializable data object LifeReportRoute     // 周报/月报
@Serializable data object LifeAchievementRoute// 成就徽章
@Serializable data object LifeTemplateManageRoute // 模板管理
@Serializable data object LifeTemplateCreateRoute // 模板创建
```

> **说明：** 预设模板（存钱/旅行/阅读等）与时间类（倒计时/生日/纪念日等）不再有独立路由，统一通过 `LifeTemplateRoute(templateId)` 动态导航。`LIFE_CATEGORY_MANAGE`（分类管理）为动态分类蓝图（15.4）的一部分，规划中未实现。

### 15.3 模板字段类型系统

`[Life]` 自定义模板使用统一的 `FieldType` enum 作为单一数据源。所有渲染组件（`FieldComponents.kt` / `FieldInputComponent.kt`）和创建 Wizard 均从此 enum 派生，消除三方并行定义。

> **⚠️ 本节的 19 种 FieldType 扩充为 v4.6 设计蓝图，尚未实施。** 当前实现（`domain/model/FieldType.kt`）为 11 种：`TEXT, NUMBER, DATE, BOOLEAN, SELECT, MULTI_SELECT, IMAGE, LOCATION, TIME, PERCENT, RATING`。以下 15.3.1/15.3.3 的 `SHORT_TEXT/SLIDER/URL/EMAIL/PHONE/COLOR/DURATION/TAG` 及对应 Wizard UI 均规划中。

#### 15.3.1 统一 FieldType 枚举

```kotlin
enum class FieldType {
    // 文本类
    TEXT, SHORT_TEXT, URL, EMAIL, PHONE,
    // 数值类
    NUMBER, PERCENT, RATING, SLIDER,
    // 选择类
    SELECT, MULTI_SELECT, BOOLEAN, COLOR, TAG,
    // 时间类
    DATE, TIME, DURATION,
    // 媒体类
    IMAGE, LOCATION
}
```

**变更要点：**

| 变更 | 说明 |
|------|------|
| 新增 | `SHORT_TEXT`, `SLIDER`, `URL`, `EMAIL`, `PHONE`, `COLOR`, `DURATION`, `TAG` |
| 移除 | `DECIMAL`（NUMBER 已覆盖，原 Wizard 中的 DECIMAL 实际 fallback 到 TextInput） |
| 保持 | `TEXT`, `NUMBER`, `DATE`, `TIME`, `BOOLEAN`, `SELECT`, `MULTI_SELECT`, `RATING`, `IMAGE`, `LOCATION`, `PERCENT` |

#### 15.3.2 字段配置（FieldConfig）

```kotlin
data class FieldConfig(
    val key: String,              // 唯一标识
    val label: String,            // 显示标签
    val type: FieldType,          // 字段类型
    val required: Boolean = false,
    val defaultValue: String = "",
    val options: List<String> = emptyList(),  // 仅 SELECT / MULTI_SELECT
    val placeholder: String = "",
    val validation: String = "",             // 正则校验（预留）
    val unit: String = "",                   // 仅 NUMBER
    val min: Double? = null,                 // 仅 NUMBER / SLIDER
    val max: Double? = null,                 // 仅 NUMBER / SLIDER
    val showInCard: Boolean = false,         // 是否显示在预览卡片
    val showInList: Boolean = false,
    val sortOrder: Int = 0
)
```

#### 15.3.3 Wizard 字段创建 UI

**Step 2（FieldStep）** 改造：

```
字段 #1   ☰  [×]               ← 拖拽把手 + 删除按钮
┌──────────────────────────────┐
│  字段名称                      │
└──────────────────────────────┘
类型分组 Tab: [文本] [数值] [选择] [时间] [媒体]
┌──────────────────────────────┐
│  TEXT  SHORT_TEXT  URL       │  ← FilterChip 水平排列
│  EMAIL  PHONE                │
└──────────────────────────────┘
必填  □    卡片显示  □
占位提示: [________]
─── 类型专属选项 ───               ← 根据类型动态显示
单位: [____]  最小: [__] 最大: [__]  (仅 NUMBER)
选项: [编辑 ▼]                    (仅 SELECT / MULTI_SELECT)
```

**SELECT / MULTI_SELECT 选项编辑弹窗：**

```
┌── 编辑选项 ──────────────────┐
│  选项 1    [×]               │
│  选项 2    [×]               │
│  [+ 添加]                    │
│                              │
│        [取消]  [确认]        │
└──────────────────────────────┘
```

#### 15.3.4 IMAGE 字段实现规格

| 规格项 | 规则 |
|--------|------|
| 触发 | 点击 → 底部菜单（📷 拍照 / 🖼️ 从相册选择） |
| 相册选择 | `ActivityResultContracts.PickVisualMedia`（系统默认图片选择器） |
| 拍照 | `ActivityResultContracts.TakePicture`（FileProvider URI） |
| 存储 | 复制到 `filesDir/images/life/{itemId}/{uuid}.jpg` |
| 缩略图 | Coil `AsyncImage` + `BitmapFactory.inSampleSize` 降采样 |
| 尺寸限制 | 长边最大 1080px |
| 单条目上限 | 最多 9 张 |
| EXIF | 保留旋转信息，移除 GPS 坐标 |

#### 15.3.5 字段类型与 rendering 对照

| 类型 | 输入组件 | 显示组件 |
|------|---------|---------|
| TEXT | OutlinedTextField（多行） | 纯文本 |
| SHORT_TEXT | OutlinedTextField（单行） | 纯文本 |
| URL | OutlinedTextField + URI 键盘 | 链接样式（primary 色） |
| EMAIL | OutlinedTextField + Email 键盘 | 链接样式 |
| PHONE | OutlinedTextField + Phone 键盘 | 链接样式 |
| NUMBER | OutlinedTextField + Decimal 键盘 + 后缀单位 | 数值 + 单位（¥ 格式当 unit="元"） |
| PERCENT | Slider 0-100 | 进度条 + 百分比 |
| RATING | 5 星点击 | 5 星显示 |
| SLIDER | Slider 可配置值域 + 步长 | 值 + 单位 |
| SELECT | ExposedDropdownMenu | 选项文字 |
| MULTI_SELECT | Checkbox 列表 | 逗号分隔 |
| BOOLEAN | CapsuleSwitch | ✓/✗ |
| COLOR | 色板 + HEX 输入 | 色块 + HEX |
| TAG | 自由输入 + Chips | Chips 排列 |
| DATE | DatePickerDialog | yyyy-MM-dd |
| TIME | TimePicker | HH:mm |
| DURATION | OutlinedTextField | HH:MM:SS |
| IMAGE | 系统相册/拍照 | 缩略图 Grid |
| LOCATION | OutlinedTextField + Place 图标 | 纯文本 |

#### 15.3.6 模板编辑功能

自定义模板创建后可编辑（TemplateManageScreen 增加编辑按钮）：

| 可编辑属性 | 限制 |
|-----------|------|
| 名称/描述/图标/颜色 | 无限制 |
| 添加字段 | 无限制 |
| 删除字段 | 已有数据该字段值丢失 |
| 重排字段 | 无限制 |
| 修改字段配置（label/required/options 等） | 无限制 |
| 修改字段类型 | ❌ 不允许（已有数据解析可能失败） |
| 修改分类归属 | 无限制 |

### 15.4 动态分类系统

> **⚠️ 规划中（未实现）**：本小节为分类系统重构的设计蓝图，尚未实施。**当前实现**：`LifeTemplate.category` 保持 `String`（值域 `PLAN`/`TIME`/`RECORD` 等），LifeScreen 按既有硬编码三分类分组，无 `life_categories` 表、无 `categoryId` FK、无分类管理 UI。是否实施待后续版本评估（v4.6 变更记录仅供参考）。

#### 15.4.1 设计目标

将 LifeScreen 的 Section 展示从硬编码 3 分类改为「用户可定义 N 分类」，消除"模板不知道该放哪"的困惑。分类仅用于 UI 分组，不绑定渲染逻辑。

#### 15.4.2 数据模型

```kotlin
@Entity(tableName = "life_categories")
data class LifeCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,              // 用户可自定义
    val icon: String = "",         // AppIcon 枚举名
    val color: String = "",        // HEX 色值
    val sortOrder: Int = 0,        // Section 显示顺序
    val isBuiltin: Boolean = false // 预置分类不可删除
)
```

`LifeTemplate` 变更：

| 变更 | 旧 | 新 |
|------|----|----|
| 分类引用 | `category: String` | `categoryId: Long`（FK → life_categories.id） |
| 默认值 | `"PLAN"`、`"TIME"`、`"RECORD"` | `1`（目标）、`2`（纪念）、`3`（记录） |

#### 15.4.3 预置数据

App 首次运行时自动插入 3 条预置分类（`isBuiltin = true`）：

| id | name | 规则 | 包含内置模板 |
|----|------|------|------------|
| 1 | 🎯 目标 | 有明确完成状态的追踪 | 存款/学习/阅读/待办/购物/旅行 |
| 2 | 📅 纪念 | 锚定在一个重要日期上 | 生日/纪念日/倒计时/正数日 |
| 3 | 📓 记录 | 持续累积，无自然终态 | 日记/心情/打卡/订阅/报告 |

**判定规则：** 每个模板唯一命中一个分类。规则互斥性验证：

| 模板 | 有终态？ | 单日期锚定？ | 持续累积？ | → 分类 |
|------|---------|-------------|-----------|--------|
| 存款/学习/阅读/待办/购物/旅行 | ✅ | ❌ | ❌ | 🎯 目标 |
| 生日/纪念日/倒计时/正数日 | ❌ | ✅ | ❌ | 📅 纪念 |
| 日记/心情/打卡/订阅/报告 | ❌ | ❌ | ✅ | 📓 记录 |

#### 15.4.4 UI 变化

**Wizard Step 0（分类选择）** — 从硬编码 3 选项改为动态读取 `life_categories` 表：

```
┌──────────────────────────────────┐
│  ○ 🎯 目标                        │
│     有完成状态，追踪进度到 100%     │
│     例：存款计划、待办、阅读        │
│                                   │
│  ○ 📅 纪念                        │
│     锚定在一个重要日期，计算天数     │
│     例：生日、倒计时               │
│                                   │
│  ○ 📓 记录                        │
│     持续累积，没有结束的那一天       │
│     例：日记、打卡、订阅            │
│                                   │
│  [+ 新建分类]                     │  ← 新增分类入口
└──────────────────────────────────┘
```

**LifeScreen — 动态 Section 渲染：**

```kotlin
// LifeViewModel — 从硬编码 3 分组改为动态分组
val categories: Flow<List<LifeCategory>> = categoryRepo.getAll()
val groupedTemplates: Flow<Map<Long, List<LifeTemplate>>> =
    combine(templates, categories) { tpls, cats ->
        tpls.groupBy { it.categoryId }
    }

// LifeScreen — 遍历分类渲染 Section
categories.forEach { category ->
    val catTemplates = groupedTemplates[category.id] ?: emptyList()
    if (catTemplates.isNotEmpty() || category.isBuiltin) {
        LifeSection(
            title = category.name,
            icon = iconFromName(category.icon),
            color = category.color,
            cardType = deriveCardType(catTemplates),  // 基于 layoutType
            ...
        )
    }
}
```

**卡片渲染解耦：** 不再根据分类名判断用 PlanCard 还是 TimeCard，改为根据 `template.layoutType`：

| layoutType | 卡片组件 |
|------------|---------|
| `PROGRESS` | PlanCard（进度条 + 数值摘要） |
| `DATE_COUNT` | TimeCard（天数计算 + 倒计时/正数） |
| `TIMELINE` | TimelineCard（时间线条目） |
| `STATS` | StatsCard（统计摘要） |

**FunctionSheet（FAB 底部弹出）** — 同样改为动态分组：

```
┌── 选择创建类型 ─────────────────┐
│                                 │
│ 🎯 目标                         │
│   [存款计划] [学习计划] [阅读]    │
│                                 │
│ 📅 纪念                         │
│   [生日] [纪念日]               │
│                                 │
│ 📓 记录                         │
│   [日记] [打卡] [心情]          │
│                                 │
│ 🏥 健康（用户新增分类）          │
│   [体重记录] [健身计划]         │
└─────────────────────────────────┘
```

#### 15.4.5 分类管理

| 操作 | 入口 | 规则 |
|------|------|------|
| 新建分类 | TemplateManageScreen → 管理分类 → 新建 | 输入名称、选择图标、颜色 |
| 重命名 | 分类管理列表 → 点击编辑 | 修改名称 |
| 重排 | 分类管理列表 → 拖拽排序 | 调整 Section 顺序 |
| 删除 | 分类管理列表 → 左滑/长按 | 预置分类不可删；删除时弹窗选择模板迁移目标分类 |

**分类管理 Screen 原型：**

```
┌── 管理分类 ───────────── [完成] ┐
│                                 │
│  当前分类                       │
│  1  🎯 目标               [✎]  │
│  2  📅 纪念               [✎]  │
│  3  📓 记录               [✎]  │
│  4  🏥 健康（用户新增）  [✎][×] │  ← 可删除
│                                 │
│         [+ 新建分类]            │
└─────────────────────────────────┘
```

#### 15.4.6 迁移方案（规划）

| 步骤 | 操作 |
|------|------|
| 1 | DB 新建迁移：创建 `life_categories` 表 |
| 2 | 插入 3 条预置分类（id=1 目标 / id=2 纪念 / id=3 记录） |
| 3 | `LifeTemplate` 新增 `categoryId` 列，根据当前 `category` 值映射：`"PLAN"/"计划" → 1`，`"TIME"/"时间" → 2`，`"RECORD"/"记录" → 3` |
| 4 | 删除旧 `category` 列 |
| 5 | `LifeDataSeeder` 改为写入 `categoryId` |
| 6 | `LifeViewModel` 移除 name-based fallback 分组逻辑 |

---

## 十六、架构与性能

### 16.1 技术栈

| 层 | 方案 |
|-------|------|
| UI 框架 | Jetpack Compose + Material 3 |
| 依赖注入 | **Hilt（KSP 编译期代码生成）** — `@HiltViewModel` / `@Inject` / `@Module @Provides` |
| 数据库 | Room（预热：onCreate 时调用 `openHelper.writableDatabase`） + SQLCipher 全库加密 |
| 偏好存储 | DataStore Preferences |
| 图片加载 | Coil 3 |
| 导航 | Navigation Compose（fade 动画） |
| 图表 | Compose Canvas 自绘（无第三方库） |
| 状态管理 | ViewModel + StateFlow |
| 备份 | AES-GCM + PBKDF2（本地 ZIP 备份） |
| OCR | PaddleOCR PP-OCRv6（本地离线识别，ONNX Runtime） |
| 后台任务 | WorkManager |
| 序列化 | Kotlinx Serialization |
| 农历 | Lunar |
| 生物识别 | AndroidX Biometric |
| 分页 | Paging3（预留；账单月度聚合视图不适用，LIMIT 5000） |
| 密码本加密 | AES-256-GCM + 密钥包裹（独立主密码 → PBKDF2 → 数据密钥 DK） |

### 16.5 功能模块架构

`[Vault]` 密码本代码位于 `com.palmnote.feature.vault`，v1.3.0 已实现，独立于四大 Tab，作为外层 NavHost 路由（同级 Settings），入口为 Dashboard 卡片（可显隐）。依赖通过 Hilt 提供（`@Singleton`）。

所有非 Tab 功能模块（密码本、未来 AI、云备份等）统一采用 Dashboard 卡片入口模式，不占用底部 Tab。

> 详见 [feature-vault.md](feature-vault.md)。

### 16.2 依赖注入架构（Hilt）

| 维度 | 方案 |
|------|------|
| DI 框架 | **Hilt**（KSP 代码生成，编译期安全） |
| 初始化 | `@HiltAndroidApp`（PalmNoteApp）/ `@AndroidEntryPoint`（MainActivity） |
| 依赖提供 | `@Module @Provides @Singleton`（AppModule / RepositoryModule） |
| ViewModel | `@HiltViewModel` + `hiltViewModel()` |
| Worker | `@HiltWorker`（Hilt-Work 集成） |
| 作用域 | `@ApplicationScope`（SupervisorJob + Dispatchers.Default） |

### 16.3 性能优化

| 优化项 | 说明 |
|--------|------|
| **Baseline Profile** | 规则覆盖 Home/Startup 路径（Compose/Hilt/Room/导航），消除首帧 JIT 编译 |
| **Room 预热** | Application.onCreate 时调用 `database.openHelper.writableDatabase` 强制初始化 |
| **@Immutable 实体** | Entity 数据类标注 `@Immutable`，减少 Compose 不必要的重组 |
| **DataStore 读取** | 统一的 `prefsFlow`（`catch` 兜底空偏好）派生各属性 flow，避免重复订阅 `data` 流 |
| **ViewModel 创建** | `hiltViewModel()` 编译期绑定，零反射 |
| **WorkManager 延迟** | 非关键 Worker 调度延迟到后台协程，不阻塞主线程 |
| **账单列表** | LazyColumn 惰性渲染，LIMIT 5000 消除静默截断（月度聚合视图不适用 Paging） |

### 16.4 启动流程优化

```
Application.onCreate()  (@HiltAndroidApp)
├── Hilt 注入（AppModule/RepositoryModule）→ 所有依赖就绪
├── applySavedLanguage()     → 语言设置
├── NotificationChannels     → 通知渠道
└── applicationScope.launch {
    ├── database.openHelper  → Room 预热
    ├── scheduleDailyCheck() → Worker 调度
    ├── scheduleAutoBackup() → 自动备份
    └── seedIfEmpty()        → 模板种子
}

MainActivity.onCreate()  (@AndroidEntryPoint)
└── setContent {
    ├── collectAsState(combine) → DataStore 批量读取（1 次重组）
    ├── PalmNoteTheme()
    └── NavHost(fade 动画)
        └── 当前页面（导航栏同步出现，无动画延迟）
}
 
---
 
| 版本 | 日期 | 内容 |
|------|------|------|
| 1.0 | 2026-06-30 | 初版 |
| 1.1–1.6 | 2026-06-30~07-01 | 色彩/组件/交互/文案/图表规范完善 |
| 2.0 | 2026-07-03 | 精简重构，对齐 App 状态 |
| 2.1 | 2026-07-03 | 底部导航微信尺寸规范；Dashboard 拖拽排序；卡片修正 |
| 2.2 | 2026-07-03 | Tab/页面动画 fade+spring；顶栏图标上色；胶囊 Switch；设置页规范；移除主题色 |
| 3.0 | 2026-07-03 | 全面对齐：修正 Card 边框 1dp、分隔线双规则、AppDialog 按钮文案去统一化、新增小节（权限/备份/编码禁止 clickable 缺 clip 和 items 缺 key）、14 组件章节重组 |
| 4.0 | 2026-07-03 | 生活模块集成：新增 1.7 生活模块色彩体系、5.3 生活模块图标规范、6.17 生活模块专属组件、7.2 生活模块动画补充、8 权限补充、10 文案补充、11 图表补充、13.3 生活模块颜色引用、15 生活模块导航结构与路由定义 |
| **4.2** | **2026-07-22** | **架构重构：移除 Hilt → 手动 DI（AppContainer）。底部导航栏：60dp、CenterVertically、无动画、缓存 inset。卡片间距统一 8dp。移除 Dashboard 内部 Scaffold。导航动画 spring → tween(200)。新增架构与性能章节。** |
| **4.3** | **2026-07-28** | **对齐代码实现：修正颜色值（ExpenseRed/IncomeGreen/Amber/底部导航选中色）；组件命名 XiaomiSwitch → CapsuleSwitch；模板颜色引用 val 属性替代 composable 函数；AppIcon 查询 fromName 替代 valueOf；ModuleCard 内边距 12dp；备份方式 WebDAV → 本地 ZIP；补全技术栈（Paging3/Biometric/Lunar/WorkManager/Serialization）；移除 Hilt 残留。** |
| **4.4** | **2026-07-28** | **新增密码本模块设计文档（[feature-vault.md](feature-vault.md)）；权限章节补充 INTERNET 运行时请求规范；架构章节补充 Vault + 未来 AI/Cloud 模块引用。** |
| **4.5** | **2026-07-28** | **生活模板字段类型扩充（TOGGLE / RATING / IMAGE / URL / CURRENCY / TAG）。** |
| **4.6** | **2026-07-28** | **分类系统重构：静态三分类 → 用户可定义动态分类（LifeCategory 实体 / categoryId FK / 动态 Section 渲染 / 分类管理 UI）。FieldType 类型系统统一（enum 扩展至 19 种，Wizard 全量暴露，DECIMAL 移除）。卡片渲染解耦（基于 layoutType 而非分类）。分类色重命名（时间→纪念）。预置分类判定规则文档化。IMAGE 字段重新设计（系统相册/拍照）。模板编辑功能规范。** |
| **4.7** | **2026-07-28** | **取消模块化架构（PalmModule / ModuleRegistry / Tab 动态配置），底栏恢复为固定 4 Tab。新增模块均通过 Dashboard 卡片入口（不占 Tab）。删除第 17 章。** |
| **5.0** | **2026-08-02** | **架构对齐当前实现：依赖注入回归 Hilt（手动 DI / AppContainer 已移除）；密码本 v1.3.0 已实现（feature/vault，字段级 AES-256-GCM + 独立主密码密钥包裹，DB v5 Migration4To5）；15.4 动态分类标注为规划中未实现；版本升 1.3.0。** |
| **5.1** | **2026-08-02** | **安全与 OCR 升级：数据库 SQLCipher 全库加密（EncryptedOpenHelperFactory，明文库自动迁移）；OCR 引擎 ML Kit → PaddleOCR PP-OCRv6（OcrEngine 接口 + PaddleOcrEngine，ONNX Runtime 离线推理，模型打包 assets，release 仅 arm64-v8a）；APK 体积 86MB → 42.6MB。对齐修正：15.2 路由改类型安全路由（LifeRoute.kt）；15.3 FieldType 扩充标注为规划（当前 11 种）；INTERNET 权限标注预留未声明。** |