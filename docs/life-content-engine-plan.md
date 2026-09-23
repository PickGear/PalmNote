# 生活内容引擎设计（字段系统 · 视图层 · 聚合层）

> ⚠️ **本文档已并入 `docs/life-refactor-master-plan.md`（生活页全面重构计划·总纲，2026-09-19）。**
> 其字段架构由总纲 **§三** 承接、存储方案由 **§七** 承接、分期已重排为五期。本文件保留作为过程记录，不再单独维护。
> **⚠️ v1.26 校准（2026-09-21）**：生活页 UI 已**完全重构**（旧 UI 子树整棵删除），本文中任何**代码引用 / 现状描述**一并失效。见总纲「**校准 · v1.26**」。


> 状态：**待实施**（未动一行代码）
> 关系：`life-page-plan-v3.md` 定的是**首屏信息架构**（这页回答什么问题、入口怎么排）；本文定的是**内容引擎**（一条记录怎么被定义、填写、展示、统计）。两者是正交的，可分别实施。
> 术语声明：本文出现的「字段系统 / 视图层 / 聚合层 / 渲染目标 / 能力契约」均为**工程内部用语，不进入任何用户可见文案**。UI 层继续只用「模板 / 字段 / 统计」这类既有词。

---

## 一、需求转译

用户原话拆成四条能力：

| 原话 | 转译成工程语言 | 现状 |
|---|---|---|
| 可以记录、也可以做计划、做目标 | 同一套字段系统的**三种用法预设**，不是三套系统 | 记录/计划已通；**目标无界面** |
| 内置模板 + 自定义模板 | 模板 = 字段 schema（`fieldsConfig` JSON） | ✅ 已建成 |
| 不同字段进行组合 | 模板编辑器可任意增删排序字段 | ✅ 已建成 |
| 可视化、模块化、图片、文字、数据、统计 | **四层渲染目标**各自完整支持全部字段类型 | ❌ 严重不对齐 |

**核心判断**：用户要的不是「更多模板」，而是「模板 = 可组合字段」这套能力**在每一层都被兑现**。地基已经建了大部分，但**兑现到哪一层就断了**。

---

## 二、现状实测（附证据）

### 2.1 已经建成的部分——比预想多得多

| 能力 | 位置 | 证据 |
|---|---|---|
| 字段类型定义（23 种） | `core/.../domain/model/FieldType.kt` | 枚举 23 值，`@Serializable` |
| 字段配置（含渲染提示） | `core/.../domain/model/FieldType.kt` | `FieldConfig` 带 `showInCard` / `showInList` / `showAsProgress` / `sortOrder` / `validation` / `min` / `max` |
| 模板实体 | `core/.../db/entity/LifeTemplate.kt` | `fieldsConfig` / `layoutType` / `availableLayouts` / `statusFlowConfig` / `linkConfig` / `isBuiltin` / `isHidden` / `isSpecial` |
| 模板编辑器 | `app/.../ui/life/common/TemplateCreateScreen.kt`（474 行） | `fieldTypeOptions` 列出全部 **23** 种，可增删排序 |
| 动态表单 | `app/.../ui/life/common/DynamicFormScreen.kt`（332 行） | 解析 `fieldsConfig` → 渲染 → 存 `fieldsData` |
| 字段输入渲染器 | `app/.../ui/life/common/FieldComponents.kt`（649 行） | `FieldInput` 分派 **22** 种；含 `ImageInput`（Coil + `ImageGridPicker` + 落盘）、`LocationInput`、`CurrencyInput`、`ColorInput`、`DateTimeInput` |
| 字段展示渲染器 | 同上，L530+ | `FieldDisplay` 显式 **13** 种 + else 兜底 |
| 卡片摘要渲染器 | `app/.../ui/life/common/LifeCards.kt`（431 行） | 显式 **11** 种字段；6 种卡型 |
| 图片基建 | `core/.../ui/components/` | `ImageGridPicker` / `ImagePreview` / `ImageUtils` / `saveImageToInternalStorage` |
| 图表（仅心情模块） | `app/.../ui/life/record/mood/` | `MoodTrendChart` / `FactorAnalysisChart` / `MoodCalendarView` |

**结论：图片、文字、数值三种字段的输入能力已经全部具备。**用户要的「不只是文字记录」在输入层已经是事实。

### 2.2 缺陷一：两套字段模型并存

| | core 版 | app 版 |
|---|---|---|
| 类型 | `FieldConfig`（`@Serializable`） | `FieldDef`（手写解析） |
| 类型字段 | `type: FieldType`（枚举，编译期校验） | `type: String`（**裸字符串**） |
| 缺哪些元数据 | — | 缺 `showInList` / `sortOrder` / `validation` / `placeholder` |
| 谁在用 | **0 处** | `DynamicFormScreen` / `ItemDetailScreen` |

`FieldDef.type` 是裸字符串，`when(field.type)` 拼错**不报错**、直接落到 `else`。这是静默数据损坏的入口。同时 `LifeCards.kt` 引用的是 `FieldType.*` 枚举，`FieldComponents.kt` 用的是字符串字面量——**同一个仓库里两种写法**。

### 2.3 缺陷二：core 里的字段组件是孤儿

`core/.../ui/components/field/` 下 11 个组件（`ImagesInputComponent` / `MoneyInputComponent` / `ProgressInputComponent` / `CounterInputComponent` / `TagsInputComponent` / `SelectInputComponent` / `RatingInputComponent` / `DateInputComponent` / `DateRangeInputComponent` / `FieldInputComponent` / `FieldDisplayComponent`）**外部引用数为 0**。其中 `FieldInputComponent` 只实现 9 种、`FieldDisplayComponent` 只实现 4 种，能力远低于 app 版。

### 2.4 缺陷三：目标体系「有地基无房子」

`Goal` 实体能力完整：`CUMULATIVE` / `HABIT` / `TARGET` 三型，带 `streak` / `longestStreak` / 周期（`frequency` + `targetPerPeriod` + `currentPeriodCount`）/ `direction`（INCREASE / DECREASE）/ `priority` / `deadline` / `reminderEnabled` / `linkedAssetId`；配套 `GoalCheckIn`（含 `count` / `mood` / `duration`）。

但实测调用面：

- `getAllGoals()` 的调用者只有 `GoalRepositoryImpl`、`DashboardWidgetProvider`（桌面小组件）、`CsvDataExporter` —— **UI 层零调用**。
- `insertGoal(...)` 只出现在 `HabitViewModel.kt:84`，且只创建 `HABIT` 型。
- `LifeViewModel.kt:167` 与 `LifeStatsScreen.kt:106` 都只读 `getHabitGoals()`。
- **不存在 `GoalListScreen` / `GoalDetailScreen` / 任何 Goal 路由。**

→ `CUMULATIVE`（累计型）和 `TARGET`（目标值型）两类目标在 App 内**无法创建、无法查看、无法管理**。「习惯打卡」只是目标体系的 Habit 子集。

### 2.5 缺陷四：统计在数据层不可能

`LifeItemDao` 全部 22 个查询里，没有任何一条按字段聚合。字段值全在 `LifeItem.fieldsData` 这一个 JSON 字符串里，唯一能用它是 `searchItems` 的 `fieldsData LIKE '%' || :query || '%'`（`LifeItemDao.kt:44-51`）。

**SQLite 无法对 JSON blob 做 GROUP BY / SUM / AVG。** 这是「数据统计」需求在**地基层面**的阻塞，不是工作量问题。`LifeStatsScreen` 只有 287 行、一个按天分组的 `WeekChart`，这是该约束的必然结果。

---

## 三、四个断点

| 编号 | 断点 | 后果 |
|---|---|---|
| **P1** | 字段模型双份，app 那份类型是裸字符串 | 拼错静默降级；元数据丢失；两种写法混用 |
| **P2** | 四层渲染能力不对齐（输入 22 / 详情 13 / 卡片 11 / 聚合 0，共 23 种） | 同一字段在三个界面表现不同 |
| **P3** | 字段值存 JSON blob，无聚合能力 | **统计与可视化做不了** |
| **P4** | 目标无 CRUD 界面，只用了 HABIT 子集 | 「做目标」这个需求实际上没被满足 |
| **P5** | **内置模板字段贫化：30 个字段只用 5 种类型，其中 4 种外观一致；5 个模板零字段** | **默认体验必然「像记事本」——这是用户实际反馈的问题** |

### 附：P5 实测数据（2026-09-19，脚本 `.workbuddy/audit/scan_field_types.py`）

16 个内置模板共 **30 个字段**，类型分布：

| 类型 | 数量 |
|---|---|
| NUMBER | 13 |
| DATE | 8 |
| TEXT | 6 |
| BOOLEAN | 2 |
| SELECT | 1 |

**只用了 5 种 / 23 种**，即 **18 种从未被任何内置模板使用**（`IMAGE` / `RATING` / `SLIDER` / `COLOR` / `MULTI_SELECT` / `CURRENCY` / `DURATION` / `LOCATION` / `URL` / `EMAIL` / `PHONE` / `PERCENT` / `PERCENTAGE` / `TIME` / `DATETIME` / `RICH_TEXT` / `FILE` / `SHORT_TEXT`）。

**零字段模板（`fieldsConfig = "[]"`）**：待办 / 心情 / 日记 / 周报月报 / 专注 —— 打开只有标题 + 备注。

**外观同质化**：`TEXT` / `NUMBER` 是标准 `OutlinedTextField`；`DATE` / `SELECT` 是**只读 `OutlinedTextField` + 弹窗**（视觉上与笔记输入框无异）。故 30 个字段中 **28 个渲染为输入框（93%）**，仅 `BOOLEAN`（2 个）不是。**用户的「全是文本框」观察完全准确。**

**规范层同源**：`design-spec.md` §15.3.5「字段类型与 rendering 对照」自身就把 **8 种类型**指定为 `OutlinedTextField`（`TEXT` / `SHORT_TEXT` / `URL` / `EMAIL` / `PHONE` / `NUMBER` / `DURATION` / `LOCATION`）——即「像记事本」是规范选定的方向，不是实现走偏。**§4.7 需要修订这张表。**

**规范与代码脱同步**：§15.3.5 表列 19 种（含 `TAG`）且 §15.3 注明「当前实现为 11 种」；实际枚举已是 **23 种**——规格有 `TAG` 而代码无，代码有 `PERCENTAGE` / `CURRENCY` / `DATETIME` / `RICH_TEXT` / `FILE` 而规格无。

**顺带一个显示缺陷**：`FieldDisplay`（详情页）未显式实现 `SELECT` 与 `TEXT`。`SELECT` 落到 else 分支**原样输出存储值**——订阅记录的「扣费周期」在详情页会显示 `monthly` / `quarterly` / `yearly`（英文原文），而非「按月」。此处需补 `SELECT` 的 label 映射（同时说明：选项值不应直接作为用户可见文案）。

### 附：一个可复现的真 bug

`FieldType` 有 **23** 种，`TemplateCreateScreen` 允许选 **23** 种，但 `FieldComponents.FieldInput` 只有 **22** 个分支——**`PERCENT` 缺失**，落到 `else` 变成多行文本框。用户能创建带「百分比」字段的模板，但填的时候拿到的是纯文本框。

顺带：`PERCENT` 与 `PERCENTAGE` 语义重复（都是百分比），是 schema 冗余，应择一废弃。

---

## 四、目标架构

### 4.1 单一字段真源

**以 core 的 `FieldType` 枚举为唯一真源**（它已经在 core、已经是 `@Serializable`，符合 app→core 单向依赖铁律）。

- `FieldDef.type` 的类型从 `String` 改为 `FieldType`（保留 `FieldDef` 作为 UI 层 DTO，或直接退役改用 `FieldConfig`——实施时二选一，倾向退役）。
- `LifeCards.kt` 与 `FieldComponents.kt` 统一到枚举，消灭字符串字面量。
- 补 `PERCENT` 分支；`PERCENT` / `PERCENTAGE` 二选一废弃（建议留 `PERCENTAGE`，因为 `LifeCards` 与 `FieldDisplay` 都已实现它）。

### 4.2 字段能力契约

**所有 23 种字段类型 × 4 个渲染目标，必须逐格声明「完整 / 降级 / 不支持」**，成为实现与验收的唯一清单。降级必须显式（写明降级成什么），不允许 `else` 静默兜底。

目标状态：**23 种全部「完整」**，或经人为决策后写明的降级项。现状：

| 渲染目标 | 显式支持 / 23 |
|---|---|
| 输入（填写） | 22 |
| 详情（查看单条） | 13 |
| 卡片（列表摘要） | 11 |
| 聚合视图（统计） | 0 |

### 4.3 存储分层与迁移（AppDatabase v9）

**沿用项目已有的「双写」先例**——`LifeItem` 的 v8 执行列注释已经写明：

> 执行列（v8 新增，查询索引，非展示信源；fieldsData 仍是详情/卡片唯一信源）

`dueDate` / `dueTime` / `parentId` 等就是这么做的。把同一模式推广到「可聚合字段」：

- **`fieldsData` 保持不动**，仍是展示的**唯一信源**（零回归风险）。
- 新增**聚合写入路径**：模板创建/编辑时，标记哪些字段是「可聚合」的（`FieldConfig` 已有的 `showAsProgress` 可扩展为 `aggregatable` + `aggregateKind`：SUM / AVG / COUNT / MIN / MAX / LATEST）。
- 落地方案二选一（实施时定）：
  - **(a) 白名单列**：在 `life_items` 上加固定数量的数值列 + 类型标记。改动最小，与 v8 先例同构；缺点是列数有上限。
  - **(b) 聚合表** `life_item_field_values(itemId, templateId, fieldKey, valueNum, valueText, valueDate)`：规范化，聚合 SQL 最舒服；缺点是写入路径变复杂、数据量翻倍。
  - 倾向 **(b)**，因为「自定义模板 + 任意字段组合」下 (a) 的固定列数上限很快会成为新瓶颈，而 (b) 才真正支撑「字段可组合」这个前提。
- 迁移全手工（项目铁律），需要回填：从现有 `fieldsData` 解析出可聚合字段写入新表。

### 4.4 视图层：从「硬编码卡型」到「layoutType 驱动」

`LifeTemplate.layoutType` 与 `availableLayouts` 字段已存在但**未被消费**，卡型是 `LifeCards.kt` 里硬编码的 6 个函数。

要兑现「模块化」，视图应由模板声明的 `layoutType` 选择渲染器，字段的 `showInCard` / `showInList` / `showAsProgress` / `sortOrder` 决定每个字段在卡内的呈现位置与形式。**这四件工具已经在 `FieldConfig` 里了**，只是没被读。

### 4.5 聚合层与图表

- 新增 `LifeItemFieldAggregateDao`（或 repository 方法），面向「模板 + 字段 + 时间范围」出聚合结果。
- 通用图表能力：当前 3 个图表组件是 `record/mood/` 私有的，无法复用。需要抽到 `core/.../ui/components/chart/`，并定义「聚合结果 → 图表类型」的映射规则（数值型随时间的 → 折线；分类计数的 → 柱状/饼；布尔随时间的 → 热力）。
- **图表由字段配置驱动，而不是由模板硬编码**——这是「自定义模板也能有统计」的前提。

### 4.6 目标接入

两条路，需拍板（见 §七）：

- **A. 目标并入字段系统**：`Goal` 降级为一种内置模板（`fieldsCode` 描述目标字段），目标成为「字段系统的第三种用法预设」。统一，但要迁移现有 `goals` / `goal_check_ins`。
- **B. 目标保持独立实体，补 CRUD 界面**：新建 `GoalListScreen` / `GoalDetailScreen` / 创建路由，`getAllGoals()` 接入 UI，生活页给入口。改动小，但「目标」与「记录/计划」仍是两套东西。

---

### 4.7 字段视觉语言（修订 `design-spec.md` §15.3.5）

**核心原则：能点就不要敲。**

判据很硬：**一个字段如果要求用户打字，它就不该出现在日常记录流程里。** 记事本的本质是「键盘 + 空格子」；生活记录的交互应该是「眼到手到」——点、拖、选、拍。

按此把 23 种字段归入六种「手」，并**修订 §15.3.5 那张把 8 种类型指定为 `OutlinedTextField` 的表**：

| 手 | 适用类型 | 控件形态 |
|---|---|---|
| **点选** | `SELECT` / `MULTI_SELECT` | 胶囊 chips（**取代 `ExposedDropdownMenu`**——下拉要两次点击且藏住选项） |
| **点选（快捷）** | `DATE` / `TIME` / `DATETIME` | 快捷 chips（今天 / 明天 / 本周 / 自定义）+ 需要时开选择器。**不再是只读输入框** |
| **点选（真实状态）** | `BOOLEAN` / `COLOR` / `RATING` | 开关 / 色块 / 星——保持现状 |
| **拖动** | `SLIDER` / `PERCENT` / `PERCENTAGE` | 可拖轨道 + 实时值；**`PERCENTAGE` 需补进 `FieldInput`** |
| **步进数值** | `NUMBER` / `CURRENCY` / `DURATION` | 大号数值 + `-` / `+` 步进器（`CURRENCY` 带 `¥`）。**不是数字输入框** |
| **媒体** | `IMAGE` / `FILE` / `LOCATION` | 缩略图网格 + 系统相册 / 地图定位行 |
| **文本（最后手段）** | `TEXT` / `SHORT_TEXT` / `RICH_TEXT` / `URL` / `EMAIL` / `PHONE` | 输入框。**同类字段在单页内应 ≤ 2 个** |

**两条可验收的硬指标**（写进 §六 验收矩阵）：

1. **输入框预算**：任一模板的填写页，`OutlinedTextField` 数量 **≤ 2**；超出即视为降级设计，需在评审中给出理由。
2. **智能默认**：`DATE` 默认今天、`TIME` 默认当前时段、`SELECT` / `MULTI_SELECT` 记忆上次选择、`CURRENCY` 记忆上次数额。**零输入即可保存**是合格线。

### 4.8 内置模板字段重写（P5 的解法）

**关键约束：不得删除或改变已有字段的 `key` 与 `type`。** `design-spec.md` §15.3.6 已确立「修改字段类型 ❌ 不允许（已有数据解析可能失败）」——内置模板必须遵守同一约束，否则存量用户的 `fieldsData` 会失去展示位置。因此重写只做两件事：

- **(a) 纯配置变更**：给现有字段打开 `showAsProgress` / 调整 `showInCard` / 补 `unit`。**零风险，不动 schema**。
- **(b) 纯新增字段**：加 `IMAGE` / `RATING` / `MULTI_SELECT` / `CURRENCY` 等新字段。新增不影响旧数据。

| 模板 | 现有字段 | 重写动作 |
|---|---|---|
| 存钱计划 | 目标金额 / 已存金额 / 目标日期 | **(a)** `currentAmount` 开 `showAsProgress` → 进度条 + ¥；`deadline` 走快捷 chips；**(b)** 新增 `IMAGE` 存钱凭证 |
| 购物计划 | 预算 / 已花费 / 店铺 / 购物项目 | **(a)** `spent` 开 `showAsProgress`；**(b)** 新增 `MULTI_SELECT` 分类、`IMAGE` 商品图 |
| **待办** | **无字段** | **(b)** 新增 `DATE` 截止（快捷 chips）。注意 `dueDate` 执行列已存在，避免与字段重复语义 |
| 旅行计划 | 目的地 / 出发 / 返程 / 预算 | **(a)** 两日期走 chips；**(b)** 新增 `MULTI_SELECT` 同行人、`IMAGE` 照片、`LOCATION` 地点 |
| 阅读 | 总页数 / 当前页 / 作者 | **(a)** `currentPage` 开 `showAsProgress` → 阅读进度条；**(b)** 新增 `RATING` 评分、`IMAGE` 封面 |
| 学习计划 | 课程名 / 总节数 / 完成节数 | **(a)** `completedLessons` 开 `showAsProgress`；**(b)** 新增 `DURATION` 单次时长 |
| 倒计时 / 正数日 | 日期（+ 提醒） | **(a)** 日期走 chips。当前已是相对自然的形态，改动最小 |
| 生日 / 纪念日 | 日期（+ 提醒 / 备注） | **(b)** 新增 `IMAGE` 头像或照片 |
| 打卡 | 目标天数 / 连续天数 | **(a)** `currentStreak` 开 `showAsProgress` |
| **心情** | **无字段** | **(b)** 新增 `SELECT` 心情（chips，5 档）、`MULTI_SELECT` 影响因素、`RICH_TEXT` 备注 |
| **日记** | **无字段** | **(b)** 新增 `SELECT` 天气、`SELECT` 心情、`IMAGE` 配图、`RICH_TEXT` 正文 |
| 订阅记录 | 扣费金额 / 周期 / 扣费日 / 下次扣费 | **(a)** 周期改 chips 渲染（**并修 `FieldDisplay` 的 `SELECT` 显示成 `monthly` 的缺陷**）；**(b)** 新增 `URL` 管理链接 |
| **周报月报 / 专注** | **无字段** | 特殊模板（走 `STATS` / 计时器），**保持无字段**——不要为了「有字段」而塞字段 |

### 4.9 交付风险：内置模板重写对存量用户不生效

`LifeDataSeeder.refreshBuiltinFieldsConfigs()` 的机制有个**一版延迟**：

```
lastSynced == null                          → 只记录基线，本次不更新
lastSynced != null && 当前值 == lastSynced   → 应用新种子
否则（用户改过）                              → 跳过
```

`refreshBuiltinFieldsConfigs()` 是随 v1.4.0 首次上线的。存量用户升级到 v1.4.0 时 `syncPrefs` 中**尚无记录**，即命中 `lastSynced == null` 分支——**只记基线、不应用新种子**。若本期内置字段就在 v1.4.0 重写，**存量用户看不到任何变化，要等下一个版本**。

三个选项（需拍板，见 §七）：

- **(A) 接受一版延迟**：本期只改代码/种子，明确告知「下一版本生效」。
- **(B) 一次性覆盖迁移**：加一个 `seedVersion` 偏好；首建基线时若 `seedVersion` 未记录，则直接应用新种子并写入版本号。**推荐**——语义清晰，且为未来的种子迭代建立通用机制。
- **(C) 手工迁移脚本**：仅在本次一次性覆盖 `isBuiltin && !isSpecial` 的 `fieldsConfig`。改动最小，但每改一次种子都要再写一次。

**无论选哪个，都必须先确认：用户自定义过的模板（`current != lastSynced`）绝不能被覆盖。** 这是 `refreshBuiltinFieldsConfigs` 现有的正确行为，改动时不得破坏。

---

## 五、分期路线

### 第一期：给字段换上「手」（解决 P5，用户直接可感）

> 优先级说明：P5 是用户实际反馈的问题（「全是文本框、像记事本」），且**不需要动存储层**即可解决。故上提为第一期。

1. 按 §4.7 改造控件视觉语言：`SELECT` / `MULTI_SELECT` → 胶囊 chips；`DATE` / `TIME` / `DATETIME` → 快捷 chips + 选择器；`NUMBER` / `CURRENCY` → 大字数值 + 步进器；把 `PERCENTAGE` 补进 `FieldInput`（顺带解决 `PERCENT` 的 `else` 降级）。
2. 按 §4.8 重写内置模板字段（只做配置变更 + 纯新增，不删改既有 key）。
3. 修 `FieldDisplay` 的 `SELECT` 映射（消除详情页显示 `monthly` 的缺陷）。
4. 落地 §4.9 的投递机制（推荐 `seedVersion`），确保**存量用户也能收到**。
5. 同步修订 `design-spec.md` §15.3.5（那张把 8 种类型指定为输入框的表），消除规范与代码的脱同步。

**验收**：
- 任一内置模板的填写页，`OutlinedTextField` 数量 **≤ 2**。
- **零输入即可保存**（所有字段有智能默认）。
- 30 个内置字段用到的类型数 **从 5 种增至 ≥ 10 种**。
- 存量安装升级后，内置模板字段**确实发生变化**（不是只记基线）。

### 第二期：修地基与对齐（不动 UI 观感）

1. 统一字段真源：`FieldDef.type` → `FieldType`（或退役 `FieldDef`）。
2. 确立能力契约，把 4 个渲染目标的缺口逐个补齐到 23/23（**详情与卡片优先**，这两层是用户日常看到的）。
3. 清理 core 里 11 个孤儿组件（删除，或明确其一为唯一实现）。

**验收**：任意模板的任意字段，在填写页 / 详情页 / 卡片上表现一致；无 `else` 静默降级。

### 第三期：统计地基

1. v9 迁移：聚合存储（§4.3 选定的方案）+ 从 `fieldsData` 回填。
2. 模板字段配置支持「可聚合」标记与聚合方式。
3. 抽取通用图表组件到 core，建立「聚合结果 → 图表」映射。
4. 统计页从「按天计数」升级为「按字段聚合」。

**验收**：自定义一个含数值字段的模板，填入若干条，统计页能出该字段的 SUM / AVG 和时间趋势。

### 第四期：模块化视图与目标

1. `layoutType` 真正驱动卡片渲染，字段的 `showInCard` / `sortOrder` 生效。
2. 目标接入（§4.6 选定的方案）。
3. 生活页接入（与 `life-page-plan-v3.md` 的实施合流）。

**验收**：自定义模板可选布局；目标可创建、可打卡、可看进度。

---

## 六、验收矩阵（贯穿四期）

| 检查项 | 判据 |
|---|---|
| 字段类型覆盖 | 23 种 × 4 渲染目标，逐格有明确结论 |
| 无静默降级 | 全仓 `when (field.type)` / `when (config.type)` 不得有 `else` 兜底承载未实现类型 |
| 类型安全 | 字段类型引用处不出现字符串字面量 |
| **输入框预算** | 任一模板的填写页 `OutlinedTextField` **≤ 2 个**（§4.7） |
| **零输入可保存** | 所有字段具备智能默认，不进键盘也能存（§4.7） |
| **内置字段类型丰富度** | 30 个内置字段用到的类型数 **≥ 10 种**（现为 5 种） |
| **存量投递** | 升级后内置模板字段确实变化，非仅记录基线（§4.9） |
| 统计可达 | 任一数值字段可出聚合值，且不依赖全表扫描进内存 |
| 数据不丢 | v9 迁移后 `fieldsData` 逐条与原值一致（回填脚本可比对） |
| 用户自定义不被覆盖 | `refreshBuiltinFieldsConfigs` 的「用户改过则跳过」行为不得破坏 |
| 文案一致 | 新增 UI 文案不含内部术语；中英 `strings.xml` 对等 |

---

## 七、风险与待拍板

### 待拍板

**第一期阻塞项（先定这三个，才能开工）**

1. **存量投递机制选 (A) 接受一版延迟 / (B) `seedVersion` 通用机制 / (C) 一次性迁移脚本？** —— 倾向 **(B)**。不解决这个，内置模板重写对存量用户等于没做（§4.9）。
2. **视觉语言改造是否确认？** 其中两处是**撤销既有规范**：`SELECT` 由 `ExposedDropdownMenu` 改为胶囊 chips（§15.3.5 原定下拉）、`DATE` 由「只读输入框 + 弹窗」改为「快捷 chips + 弹窗」。这正是让界面「不像记事本」的关键两刀，需要你确认。
3. **是否确认「输入框预算 ≤ 2」这条硬指标？** 它意味着后续所有新模板设计都要先算输入框数量，是一条会对设计产生持续约束的规则。

**后续决策（可随对应期次再定）**

4. **`FieldDef` 是改造还是退役？** —— 退役更干净，但牵动 `DynamicFormScreen` / `ItemDetailScreen` / `LifeCards` / `TemplateCreateScreen` 四处。（第二期）
5. **聚合存储方案 (a) 白名单列 还是 (b) 聚合表？** —— 这决定「字段可组合」的上限。倾向 (b)。（第三期）
6. **目标：并入字段系统（A）还是保持独立补界面（B）？**（第四期）
7. **`PERCENT` 废弃还是 `PERCENTAGE` 废弃？**（第二期）

### 风险

| 风险 | 说明 |
|---|---|
| `FieldDef.type` 改枚举的爆炸半径 | 四处消费点，每处都是 `when`。改签名必须 grep 全部调用点，且离线闸门**抓不出**这类错误。 |
| v9 回填不可逆 | 迁移前必须有可回滚的备份路径；回填脚本要能比对 `fieldsData` 原值。 |
| 能力契约会放大工作量 | 「23 × 4 全部完整」是理想态，卡片层许多类型（如 `FILE` / `LOCATION`）在列表摘要里本就该降级。**降级是允许的，但要写明**。 |
| `showAsProgress` 语义未定 | 它已存在于 `FieldConfig`，但 `showInList` / `sortOrder` 尚未被任何渲染器读取，需确认设计意图后再接线。 |
| 孤儿组件清理的回归面 | 删除 core 11 个组件前需确认无反射 / 资源引用。 |

---

## 八、与既有设计文档的关系

- `life-page-plan-v3.md`：首屏信息架构（今日条 / 今天 / 概览 / 功能四段、16→8 入口收敛）。本文**不修改**其结论；实施第三期时合流。
- `design-spec.md` §15.1：已写明「FAB 展开式 8 个菜单项」与「概览区 3 个数据卡片」，本文的渲染目标与之一致。
- 本文所有 UI 层实现仍受 `.workbuddy/memory/topics/ui-language.md` 的铁律约束（禁发明概念词、标题只在卡外、复用既有组件）。
