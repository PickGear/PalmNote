# 首页卡片 v2 —— 快照 + 异常 + 行动 改版方案(定稿)

> 状态:定稿
> 目标:将 app 主页(Dashboard)改为「快照 + 异常 + 行动」信息架构 —— 一眼看清当下状态(净资产/今日)、一眼看到需要行动的点(未达标/待打卡/到期),让点卡片不再是贴满详细列表的搬运工,不与各功能模块专页重复。
> 版本:2026-08-15 · v2.0 · 方案代号「Home Card v2」

---

## 一、方案总览

### 1.1 核心问题

- 现有 8 卡中,GOALS / ANNIVERSARIES 直接**把明细分列表搬上首页**(`recentGoals.take(3)`、`upcomingAnniversaries.take(3)`),与生活页目标/纪念专页高度重复,信息冗长。
- 缺失两个高频行动入口:今日习惯打卡、订阅到期提醒,长期裸奔无首页聚合。
- 现状 `DashboardCardConfig.defaults` = **现有 8 卡全部 `visible=true`**(见 2.1);若在此基础直接扩容成 10 卡全显,首屏信息过载,重点被稀释 → 故默认组缩窄为 9 显 1 隐(见 1.3)。

### 1.2 解决思路

1. **摘要化**:GOALS / ANNIVERSARIES 从「明细列表」改为「汇总 + 进度环 / 倒计时」,详情交给各自的专页。
2. **新增 2 卡**:HABIT_TODAY(今日打卡)· SUBSCRIPTION(订阅提醒),把「今天要做什么/要知道的」抬上首页。
3. **三层显隐**:默认组 + 用户手动开关(CardManagementDialog)+ 空态自动隐藏,三者叠加,不冲突。
4. **排序**:沿用现有长按拖拽 + 300ms 防抖持久化,新卡直接并入同一排序池。
5. **兼容**:新 CardType **只增不移位**,老用户已存 JSON 经 `PreferencesManager` merge 自动补新卡到末尾;现有 8 卡默认可见性一律不动 → **老用户体验零回退**。

### 1.3 决策锁定表

| 维度 | 决定 |
|---|---|
| 默认显示组 | **9 卡默认显示**(现有 8 卡 + 今日打卡);**订阅提醒默认隐藏**(近 7 天有到期才出现,用户按需开启) |
| GOALS | 砍明细分列表,仅保留「完成 x/y + 总进度环」,点击→生活页 |
| ANNIVERSARIES | 砍明细分列表,仅保留「最近 1 条 + 倒计时标签」,点击→生活页 |
| HABIT_TODAY | 新增,**默认显示**;读取 Goal 习惯引擎,展示「今日完成 x/n」+ 逐习惯勾选 |
| SUBSCRIPTION | 新增,**默认隐藏**;复用订阅扣费算法,展示「近 7 天到期 1-3 条」+ 倒计时 |
| 条件隐藏 | 无目标/无纪念/无习惯/无近期到期/未达预算阈值/无物品分布时,对应卡自动不显示(不占位) |
| 全部可开关 | 10 卡均可在 `CardManagementDialog` 手动显隐,手动状态优先于默认值 |
| 排序 | 沿用 `DashboardScreen` 长按拖拽 + `DashboardViewModel.moveCardUp/Down` + 300ms 防抖持久化 |
| 点击跳转 | 每卡必有合理点击目标;**局部 `clickable`(右上跳转胶囊/标题/条目),忌整卡 clickable**(避免与长按拖拽手势冲突) |
| 术语(三词不混用) | **物品** = 资产条目实体(`nav_asset`=物品·Tab)、分布、快捷按钮;**账单** = 收支流水(`nav_bill`=账单·Tab、记一笔);**资产/金额** = 汇总数值(净资产/总资产/月收支),仅 NET_WORTH 等数值卡使用 |
| 兼容 | `CardType` enum 只增不删;新卡由 `PreferencesManager.dashboardCardConfigs` merge 追加,老 JSON 无需重写 |

### 1.4 范围

**改**:`DashboardCardConfig.kt`(enum +2 种;`defaults` 定默认组 =9 显 1 隐)、`DashboardCards.kt`(GOALS/ANNIVERSARIES 摘要化、新增 2 卡渲染、CardManagementDialog 标签、HABIT_TODAY/SUBSCRIPTION 空态、**新增「跳转胶囊」组件并替换各卡跳转标识**)、`DashboardViewModel.kt`(注入 life 数据源、state 扩字段)、resources(strings)。`PreferencesManager.kt` **不改**(merge 逻辑已现成,只需 depend 新 defaults)。

**做**:今日打卡统计查询(GoalCheckIn 今日 x/n)、订阅近 7 天到期查询(复用 `LifeDailyCheckWorker.kt:220-261` 计算逻辑)。

**砍**:GOALS 明细条、ANNIVERSARIES 明细条、任何新首页模块入口(不新增路由;跳转复用现有回调:`onNavigateToAsset/Bill/Life/Vault`,见 4.4)。

**术语约定(全篇适用,与代码对齐,三词不混用)**:
- **物品** = 资产模块的条目实体:Tab 名 `nav_asset`=「物品」、快捷按钮 `dashboard_quick_asset`=物品、分布卡 `ASSET_DISTRIBUTION`=物品分布、计数 `asset_count`=件。**指"实物/数字条目"本身,不指钱。**
- **账单/账户** = 记账模块的收支流水 + 钱包账户:Tab 名 `nav_bill`=「账单」、快捷按钮 `dashboard_quick_bill`=记账、本月支出/收入/结余(`dashboard_monthly_*`)、账户余额在 Wallet 表(`getTotalBalance()` = 启用非信用卡钱包 balance 之和)。**指"钱"本身的具体载体。**
- **资产/金额** = 汇总数值:NET_WORTH 卡「净资产」= **账户金钱余额合计**(Wallet `SUM(currentBalance)`,`wallet_total_assets` 即此),**不含物品价值**;物品价值仅作物品模块内部展示(物品详情/分布),不入净资产。**仅在数值语境使用,不用于条目/按钮/Tab。**
- 三者互相独立:物品卡看"有多少件",账单/账户卡看"钱收/支/结余在哪",净资产卡看"现在手里有多少钱"。物品(实物)≠ 账户(钱),物品购买价格是历史成本,不代表当前可支配金额,故**不进入净资产**。文档中凡涉及 Tab 一律写「物品/账单/生活」,凡涉及数值一律写「金额/净资产/总资产」。

---

## 二、现状代码事实

### 2.1 CardType 与配置持久化(`core/.../ui/dashboard/DashboardCardConfig.kt`)

- `CardType` enum 现 **8 种**(`:8-17`):`NET_WORTH / QUICK_ACTIONS / BUDGET_ALERT / GOALS / ANNIVERSARIES / ASSET_DISTRIBUTION / TODAY / VAULT`。
- `defaults`(`:26`)= `CardType.entries.map { DashboardCardConfig(it) }` → **当前 8 卡全部 `visible=true`**。
- `fromJson`(`:36-42`):解码整份 JSON,失败回落到 `defaults`;本身不做 merge。
- **merge 已在 `PreferencesManager.dashboardCardConfigs` 完成**(`core/.../data/datastore/PreferencesManager.kt:111-122`):保留用户排序,仅追加 `stored` 缺的新卡(`defaults.filter { it.type !in storedTypes }`)。
  → **新卡以 `defaults` 里的 visible 追加到末尾**,老用户升级后「今日打卡可见、订阅不可见」自动生效,排序不受影响(补在末尾,可拖)。

### 2.2 DashboardState 已有数据(`app/.../ui/dashboard/DashboardViewModel.kt:24-39`)

已具备:`totalAssetValue / activeAssetCount / monthlyExpense / monthlyIncome / budget / budgetReminderEnabled / goalCount / completedGoalCount / anniversaryCount / upcomingAnniversaries / recentGoals / assetDistribution / vaultCount`。

> ⚠️ **语义修正(NET_WORTH 数据源)**:当前 `loadDashboardData` 将 `assetRepository.getTotalAssetValue()`(= `AssetDao.SUM(purchasePrice)`,**物品购买总价**)注入 `totalAssetValue` 当"净资产"。**这是错误的**——净资产应指**账户金钱余额**(Wallet `SUM(currentBalance)`)。修正:注入 `WalletRepository`,用 `getTotalBalance()`(启用非信用卡钱包余额合计)作为 NET_WORTH 主数值;物品购买总价**不再进入净资产**。物品价值仅作物品模块内部(详情/分布)展示,不属于"现在有多少钱"。

- GOALS 摘要所需 `goalCount / completedGoalCount` **已有**;仅差一个聚合「总进度环」比例(progress = completedGoalCount/goalCount)。
- ANNIVERSARIES 所需 `upcomingAnniversaries`(已 `.take(3)`)**已有**;摘要化后仅取第 1 条。
- 交互:复用 `toggleCard`(`:120-125`)/`moveCardUp/Down`(`:94-118`)/`saveConfigs` 300ms 防抖(`:84-92`)/`visibleConfigs`(`:59-61`)/`CardManagementDialog`。

### 2.3 现有卡渲染(`app/.../ui/dashboard/DashboardCards.kt`)

- `DashboardCardContent` when 分支(`:52-61`)映射 8 卡。
- `GoalsCard`(`:270-327`):标题「目标进度」+ `dashboard_goals_completed`(完成 x/y)+ **明细列表 `recentGoals.forEach`(ProgressBar)** → 明细部分需砍,改为单个总进度环。
- `AnniversariesCard`(`:330-411`):标题 + 计数 + **明细 `upcomingAnniversaries.forEach`** → 明细部分需砍,只留第 1 条 + 倒计时标签。
- `BudgetAlertCard`(`:255-267`):**已有条件渲染**(启用且月支出>预算80% 才显示) → 作为空态隐藏的范本。
- `AssetDistributionCard`(`:421`):**已有 `isEmpty` 提前 return** → 范本。
- `CardManagementDialog`(`:619-661`):行 = `allConfigs` 开关,标签 when 现仅在 8 卡 → **需补 HABIT_TODAY / SUBSCRIPTION 标签资源**。

### 2.4 订阅数据来源(到期时间从哪来)

- 订阅是 **LifeTemplate + LifeItem 记录**,无独立 entity。
- 模板 `LifeDataSeeder.kt:35`:name「订阅记录」,category「记录」,字段:`price`(NUMBER)· `billingCycle`(SELECT monthly/quarterly/yearly)· `billingDay`(NUMBER 号)· `nextBilling`(DATE,可为空);status 含 ACTIVE/PAUSED/ARCHIVED。
- 到期计算现成:`LifeDailyCheckWorker.kt:220-261`(billingDay + lastBilledDate + cycle → 下个到期日,短月自动钳制)。
- 提醒卡「近 7 天到期」即取 **status=ACTIVE** 的订阅记录,按同一算法求下一个到期日 → 筛剩余天数 0..7。

### 2.5 习惯引擎(`core/.../entity/Goal.kt` + `GoalCheckInDao`)

- 习惯保留在 Goal(streak/currentPeriodCount/GoalCheckIn 表),不在 LifeItem;`LifeDailyCheckWorker` 与提醒链路现成。
- 今日打卡 x/n:需按 Goal 聚合「今日已打卡数」/「启用习惯总数」。Goal 已有 `enabled/periodType`(长期计划/日/周/月)与 `GoalCheckIn(date=今日)` 可筛。落地需在 `GoalDao`/`GoalCheckInDao` 加今日统计查询。

---

## 三、卡片全量定义(10 卡)

> 卡片 = 「快照(当前状态)」或「行动(现在就能做/要注意)」两类;明细一律归模块专页。

### 3.0 总表

| # | CardType | 名称 | 类型 | 内容 | 默认 | 空态(自动隐藏) |
|---|---|---|---|---|---|---|
| 1 | NET_WORTH | 净资产 | 快照 | **账户金钱合计(Wallet 余额,不含物品价值)** + 月收支 + 结余 | ✅ 显示 | 无(恒显) |
| 2 | TODAY | 今日 | 快照 | 日期 + 已记录总数 | ✅ 显示 | 无(恒显) |
| 3 | QUICK_ACTIONS | 快捷入口 | 行动 | 记一笔/物品/目标/纪念 | ✅ 显示 | 无(恒显) |
| 4 | GOALS | 目标(摘要) | 快照 | 完成 x/y + **总进度环** | ✅ 显示 | 无目标时 |
| 5 | ANNIVERSARIES | 纪念(摘要) | 快照 | **最近 1 条** + 倒计时 | ✅ 显示 | 无纪念时 |
| 6 | HABIT_TODAY | 今日打卡 | 行动 | **今日完成 x/n** + 逐习惯勾选 | ✅ 显示(新) | 无习惯时 |
| 7 | BUDGET_ALERT | 预算警示 | 异常 | 超 80%/超支告警条 | ✅ 显示(条件) | 未达阈值时(已有) |
| 8 | ASSET_DISTRIBUTION | 物品分布 | 快照 | 环图 + 分类占比 | ✅ 显示 | 无物品时(已有) |
| 9 | VAULT | 保险柜 | 快照 | 计数 + 隐私提示 | ✅ 显示 | 无(恒显) |
| 10 | SUBSCRIPTION | 订阅提醒 | 异常 | **近 7 天到期 1-3 条** + 倒计时 | ❌ 隐藏(新) | 无近期到期时 |

**默认组结论:9 卡默认显示,1 卡(订阅提醒)默认隐藏。**

---

## 四、显隐 / 开关 / 排序规则

### 4.1 三层显隐(叠加,优先级从上到下)

```
行为:有不显示必要的卡(空态)→ 自动收敛,不占位
控制:用户手动开关 → 最大优先(开关 ≠ 显示,关了就绝不显示)
来决定:最终是否显示 = 用户开关且(无空态约束或约束满足)
```

| 用户开关 | 有无数据 | 结果 |
|---|---|---|
| 开 | 有 | ✅ 显示 |
| 开 | 无 | ❌ 不显示(空态自动收敛,不占位) |
| 关 | 有 | ❌ 不显示(用户主动隐藏) |
| 关 | 无 | ❌ 不显示(双原因) |

- 「有数据才显示」的卡:GOALS / ANNIVERSARIES / HABIT_TODAY / SUBSCRIPTION / BUDGET_ALERT / ASSET_DISTRIBUTION(物品)。
- 「恒显」的卡:NET_WORTH / TODAY / QUICK_ACTIONS / VAULT —— 仅受手动开关控制。
- **没有用户无法关闭的卡,也没有数据为空还强占位的卡。**

### 4.2 开关入口(`CardManagementDialog`)

- Dashboard 右上角 卡片管理 → 逐卡 `CapsuleSwitch` 控制显隐(现有 8 卡已在,补 2 新卡行)。
- 持久化:`PreferencesManager.saveDashboardCardConfigs`(DataStore,现成)。

### 4.3 排序

- 长按拖拽换位(现状)+ 300ms 防抖持久化 → 新卡并入同一池,默认补在末尾。

### 4.4 点击跳转矩阵(每卡必有合理目标)

> 原则:**局部 clickable**(右上跳转胶囊 / 标题 / 条目),不与整卡 long-press 拖拽冲突(现状 `GoalsCard:303`、`AnniversariesCard:359`、`VaultCard:101`、`AssetDistributionCard:432` 已示范)。统一做法:**每卡右上保留内嵌「跳转胶囊」作为跳转入口**,快照卡无需整卡可点。

### 4.5 跳转胶囊(JumpCapsule)UI 规范

> 卡片右上角的跳转标识:目标模块名 + `›`,外套一个**胶囊轮廓**。替代裸 `›`/裸文字,点击区和视觉都更明确。

```
形态(示意, 非精确像素):
┌──────────────────────────────────────────┐
│  目标进度                    完成 2/5  (生活 ›)  │
│  题头区                        ┌─────────┐
│                ↳ 右对齐胶囊    │ 生活 »│▓│  ← 文字+边框+淡底
│                                └─────────┘
└──────────────────────────────────────────┘
```

| 属性 | 值 |
|---|---|
| 容器 | 圆角胶囊(Capsule 形状, 全圆角) |
| 边框 | 1dp, **模块主题色**(记账橙 / 生活蓝 / 物品绿 / 保险柜紫 / 订阅绿) |
| 底色 | 同主题色 `alpha 0.08` |
| 文字 | 模块名(labelSmall) + `›`;文字与边框同色 |
| padding | 水平 8dp · 垂直 3dp(紧凑) |
| 位置 | **统一卡片右上角**;订阅行内胶囊与打卡等例外见下 |
| 触发 | 整胶囊 `clickable`(局部, 不与整卡 drag 冲突) |
| 动画 | 点击 ripple(主题色) |

- 模块名与颜色映射:**记账** AccentOrange · **生活** ModuleLife/蓝 · **物品** primary(绿) · **保险柜** vaultTint(紫) · **订阅** lifeSubscriptionColor(绿)。
- 统一右上角:GOALS/ANNIVERSARIES/VAULT 现有 `> 箭头`(:303/:359/:101)升级为胶囊;NET_WORTH/TODAY/BUDGET_ALERT **新增右上胶囊**。
- HABIT_TODAY:「去打卡全部 →」用同款胶囊(生活蓝),**右上角**与其它卡一致(卡片无题头时胶囊停右上);SUB 卡订阅条目右端用**紧凑胶囊**(行内操作例外,保留行尾以对齐条目)。
- 异常类(预算/订阅)用主题色胶囊 = 与警示色一致,可接受:预算卡本为橙色告警,右上同橙胶囊语义连贯。

| # | CardType | 点击区 | 跳转目标 | 回调 | 现状 |
|---|---|---|---|---|---|
| 1 | NET_WORTH | 右上 跳转胶囊(新增) | 记账 Tab(钱包/账户) | `onNavigateToBill` | ➕ 需补胶囊+改数据源 |
| 2 | TODAY | 右上 跳转胶囊(新增) | 生活 Tab | `onNavigateToLife` | ➕ 需补胶囊 |
| 3 | QUICK_ACTIONS | 4 个图标按钮(已有) | 记一笔/物品/目标/纪念 | 各按钮 | ✅ |
| 4 | GOALS | 右上 跳转胶囊(升级>) | 生活 Tab | `onNavigateToLife` | ➖ 升级胶囊 |
| 5 | ANNIVERSARIES | 右上 跳转胶囊(升级>) | 生活 Tab | `onNavigateToLife` | ➖ 升级胶囊 |
| 6 | HABIT_TODAY | 右上「去打卡全部」(胶囊) | 生活 Tab(习惯) | `onNavigateToLife` | ➕ 新卡内建 |
| 7 | BUDGET_ALERT | 告警条右端胶囊(新增) | 记账 Tab | `onNavigateToBill` | ➕ 需补 |
| 8 | ASSET_DISTRIBUTION | 标题点击(已有) | 物品 Tab | `onNavigateToAsset` | ✅ |
| 9 | VAULT | 右上 跳转胶囊(升级>) | 保险柜 | `onNavigateToVault` | ➖ 升级胶囊 |
| 10 | SUBSCRIPTION | 到期条目右侧胶囊 | 订阅专页(生活 Tab) | `onNavigateToLife` | ➕ 新卡内建 |

- 全部 10 卡点击区 = **内嵌右上跳转胶囊统一**(订阅/打卡行内操作例外),不与 `detectDragGesturesAfterLongPress`(DashboardScreen:197)冲突。
- HABIT_TODAY 的 [打卡] 按钮动作本身(写 GoalCheckIn),不做导航;导航由右上「去打卡全部」胶囊承担。
- SUBSCRIPTION 条目点击跳生活 Tab,明细归订阅专页(无新路由)。
- **跳转边界(与术语一致)**:`onNavigateToAsset`→物品 Tab(看条目实体)、`onNavigateToBill`→记账 Tab(看账户/收支流水)、`onNavigateToLife`→生活 Tab、`onNavigateToVault`→保险柜。NET_WORTH(净资产)= **账户金钱余额**(Wallet 合计),与物品价值无关,点击详情归记账页/钱包,不新增金钱页。

---

## 五、卡片细则(含作品行为与线框)

### 5.1 GOALS — 目标(摘要化)· `GoalsCard`

- 保留:标题「目标进度」+ 完成 x/y 文案。
- 新增:右侧**总进度环**(`completedGoalCount/goalCount` 比例,圆形 Canvas,主色)。
- 删除:`recentGoals.forEach` 明细分条 + 每条 ProgressBar。
- 无目标 → 卡整体不显示(空态)。
- 点击:右上**跳转胶囊(生活)** → 生活 Tab(现有 `:303` 箭头升级);空态文案亦可点(`:323`)。
- 数据流:`DashboardState.goalCount / completedGoalCount`(已有)。

```
┌────────────────────────────────────────────┐
│  🚩 目标进度                  完成 2/5      │
│  ┌────────────────────────────┐   (生活 ›) │
│  │      ┌─────────┐           │            │
│  │      │  40%    │   (总进度环)            │
│  │      │ ◯ 2/5   │           │            │
│  │      └─────────┘           │            │
│  │       点击查看 → 生活页                  │
│  └────────────────────────────┘            │
└────────────────────────────────────────────┘
```

### 5.2 ANNIVERSARIES — 纪念(摘要化)· `AnniversariesCard`

- 保留:标题 + 计数。
- 新增:仅展示**最近 1 条**:图标 + 名称 + 日期 + 倒计时/已过标签(复用现有 `StatusChip`),末尾「还有 N 个去查看 ›」。
- 删除:`upcomingAnniversaries.forEach` 多条明细。
- 无纪念 → 卡整体不显示(空态)。
- 点击:右上**跳转胶囊(生活)** → 生活 Tab(现有 `:359` 箭头升级);空态文案亦可点(`:407`)。
- 数据流:`anniversaryCount / upcomingAnniversaries.firstOrNull()`(已有)。

```
┌────────────────────────────────────────────┐
│  🎉 纪念日                    共 3 个      │
│  ┌─┐ 恋爱纪念日 · 8/15        (生活 ›)    │
│  └─┘        ┌───────┐                      │
│             │ 今天 💫 │    ← 最近 1 条       │
│             └───────┘                      │
│  还有 2 个纪念 · 点击查看全部               │
└────────────────────────────────────────────┘
```

### 5.3 HABIT_TODAY — 今日打卡(新增,默认显示)· `HabitTodayCard`

- 卡片仅在有 `enabled` 习惯时显示(空态隐藏)。
- 头部:☑ 今日打卡 + 「x/n」环或数字(今日已打卡 / 启用习惯总数)。
- 主体:启用习惯列表(最多 4 个):图标 + 名称 + [打卡] 按钮;已打卡 → 勾选态(禁用点击,显示 ✓);未到周期(日/周/月习惯当天不要求)→ 置灰「本周/本月 · N/M」。
- 底部:「去打卡全部」统一为**右上跳转胶囊**(生活蓝)跳生活页习惯(`onNavigateToLife`)。
- 数据流:新增 `GoalRepository.getTodayStat()` —— 返回 `(habitTotal, habitChecked, List<HabitTodayRow>)`;勾选动作复用现有 `GoalCheckIn` 写入。
- 点击:右上**跳转胶囊「去打卡全部」**(生活蓝)→ 生活 Tab(习惯);[打卡] 按钮本身写 GoalCheckIn,**不做导航**。

```
┌────────────────────────────────────────────┐
│  ☑ 今日打卡     (去打卡全部›) 完成 2/4 ✓  │
│  ┌──────────────────────────┐              │
│  │ 🏃 晨跑        [✓ 已打卡] │              │
│  │ 📖 阅读 30min  [✓ 已打卡] │              │
│  │ 💧 喝水 8杯    [ 打卡 ]   │              │
│  │ 🧘 冥想        [ 打卡 ]   │              │
│  └──────────────────────────┘              │
└────────────────────────────────────────────┘
```

### 5.4 SUBSCRIPTION — 订阅提醒(新增,默认隐藏)· `SubscriptionCard`

- 默认 `visible=false`;用户开启后,仅在近 7 天有「到期订阅」时显示(空态隐藏)。
- 头部:🔄 订阅提醒、「N 天内到期」。
- 主体:到期 `1-3 条`:图标 + 名称 + 扣费价格 + 倒计时标签(今天=N 小时后 / N 天后),带单位。
- 删除:不展示明细分页,不展示全部订阅列表(归订阅专页)。
- 点击:到期条目(每行右侧**跳转胶囊「订阅」**)→ 订阅专页(生活 Tab);卡片整体无跳转。
- 数据流:新增 `LifeItemRepository.getSubscriptionsDueWithin(days=7)` —— 复用 `LifeDailyCheckWorker.kt:220-261` 的到期算法(billingDay + cycle + lastBilledDate 预判,短月钳制),仅筛 `status=ACTIVE`;为空 → 卡不显示。

```
┌────────────────────────────────────────────┐
│  🔄 订阅提醒                    2 项到期  │
│  ┌────────────────────────────┐            │
│  │ 🎵 网易云音乐  ¥15 3天后(订阅›)│            │
│  │ ☁️ iCloud+      ¥68 1天后(订阅›)│            │
│  └────────────────────────────┘            │
│  近 7 天内有 2 项扣费                       │
└────────────────────────────────────────────┘
```

### 5.5 其余 6 卡:内容不动,仅补点击入口

- NET_WORTH / TODAY / QUICK_ACTIONS / BUDGET_ALERT / ASSET_DISTRIBUTION / VAULT —— **内容、默认组、空态、数据逻辑均保持现状**。
- 点击缺口(见 4.4):NET_WORTH 补右上 > 箭头 → 记账 Tab(账户),并**修正数据源为 Wallet 余额**;TODAY 补 > 箭头 → 生活 Tab;BUDGET_ALERT 告警条可点 → 记账 Tab。QUICK_ACTIONS(4 按钮)、ASSET_DISTRIBUTION(标题)、VAULT(> 箭头)已就绪。

### 5.6 首页默认态(选项 C 落地,首屏次序)

```
┌──────────────────────────────────────────────┐
│  首页        📊  ⚙  🔍 (右上)                 │
├──────────────────────────────────────────────┤
│  1 │ NET_WORTH(净资产=账户余额)  (记账 ›)橙   │
│  2 │ TODAY(今日 ×月×日)         (生活 ›)蓝   │
│  3 │ QUICK_ACTIONS(记一笔/物品/目标/纪念)      │ ← 4按钮,无胶囊
│  4 │ GOALS(进度环+完成2/5)      (生活 ›)蓝   │
│  5 │ ANNIVERSARIES(最近1条+倒计时)(生活 ›)蓝  │
│  6 │ HABIT_TODAY(完成2/4)   (去打卡全部›)蓝   │ ← 默认显示
│  7 │ BUDGET_ALERT(超80%告警)   (记账 ›)橙    │
│  8 │ ASSET_DISTRIBUTION        (物品 ›)绿    │
│  9 │ VAULT                     (保险柜›)紫   │
│  ──────────────────────────────────────────── │
│  ↩ 长按任意卡可拖拽换位 · 右上角 📊 管理显隐    │
│  ↳ 每卡右上(生活)等=跳转胶囊,直通对应模块专页    │
└──────────────────────────────────────────────┘
(SUBSCRIPTION 默认不出现,条目右侧带(订阅›)胶囊 → 订阅专页)
```

### 5.7 管理弹窗

```
┌──────── 卡片管理 ────────┐
│ 净资产           [ON]   │
│ 今日             [ON]   │
│ 快捷入口         [ON]   │
│ 目标(摘要)       [ON]   │
│ 纪念(摘要)       [ON]   │
│ 今日打卡         [ON]   │  ← 新增
│ 预算警示         [ON]   │
│ 物品分布         [ON]   │
│ 保险柜           [ON]   │
│ 订阅提醒         [OFF]  │  ← 新增,默认关
└────────────────────────┘
```

---

## 六、兼容策略(老用户零回退)

1. `CardType` enum **只增 2 个、不改顺序、不删任何现有值**(`@Serializable` 反序列化按 name 匹配,新增值不影响旧 JSON)。
2. 老用户已存 JSON:`PreferencesManager.dashboardCardConfigs`(`:111-122`)已按 `storedTypes ∪ defaults` merge,新卡补在末尾 → **可见性用新 defaults(HABIT_TODAY=true / SUBSCRIPTION=false)**。
3. 现有 8 卡 `visible` 含义与顺序完全不变;唯一行为差异 = GOALS/ANNIVERSARIES **内部样式变摘要**(数据、排序、开关语义都不变)。
4. `defaults` 需新增两行:`HABIT_TODAY visible=true`、`SUBSCRIPTION visible=false`,与其它卡保持同一 `DashboardCardConfig(it)` 构造。

---

## 七、落地清单(文件 × 动作)

| 文件 | 动作 |
|---|---|
| `core/.../ui/dashboard/DashboardCardConfig.kt` | `CardType` `+HABIT_TODAY, +SUBSCRIPTION`(追加末尾);`defaults` 需分别设 `visible`(与其它 `DashboardCardConfig(it)` 同为 true,故仅 SUBSCRIPTION 需 `copy(visible=false)`) |
| `app/.../ui/dashboard/DashboardViewModel.kt` | **修正 NET_WORTH 数据源**:注入 `WalletRepository`,用 `getTotalBalance()`(启用非信用卡钱包余额合计)替代 `assetRepository.getTotalAssetValue()`,净资产=账户金钱、**不含物品价值**;注入 `GoalRepository`/`LifeItemRepository`(订阅);`DashboardState` `+habitTotal/habitChecked/habitRows/upcomingSubscriptions/dueSubCount`;在 `loadDashboardData` combine 中并入各流;新增 `getTodayStat()`/`getSubscriptionsDueWithin()` 数据装配 |
| `app/.../ui/dashboard/DashboardCards.kt` | **新增 `JumpCapsule`(跳转胶囊)组件**(Capsule 形状 + 1dp 主题色边框 + 同色 0.10 底色 + 模块名 + `›` + ripple);when 分支 `+HABIT_TODAY/+SUBSCRIPTION`;`GoalsCard` 剪明细、加总进度环;`AnniversariesCard` 剪明细、只留 1 条;新增 `HabitTodayCard`、`SubscriptionCard`(均带空态 return);**NET_WORTH/TODAY/BUDGET_ALERT 补右上跳转胶囊、GOALS/ANNIVERSARIES/VAULT 现有 `>` 升级为胶囊(对齐 4.4/4.5 矩阵)**;**NetWorthCard 现签名 `NetWorthCard(state)` 未接导航回调(:53),需加参数(指向记账 Tab)**;`CardManagementDialog` when `+2` 标签 |
| `core/.../data/db/dao/GoalDao.kt` / `GoalCheckInDao.kt` | 新增今日统计(今日已打卡数 / 启用习惯数 / 逐习惯今日勾选) |
| `core/.../data/db/dao/LifeItemDao.kt` | 新增「订阅近 7 天到期」查询(按模板 + status=ACTIVE + fieldsData 过滤后的内存计算即可,量小无需 SQL),或 `LifeItemRepository` 复用 Worker 算法 |
| `core/.../domain/repository/GoalRepository.kt` / `LifeItemRepository.kt` | 暴露以上查询 |
| `app/src/main/res/values*/strings.xml` | `dashboard_card_habit_today`、`dashboard_card_subscription`、习惯/订阅卡文案 |
| `app/.../ui/dashboard/DashboardPreview*` / 组合预览 | 为 2 新卡补 @Preview(可选) |

---

## 八、验证

| 项 | 方法 |
|---|---|
| 编译 | `:core:compileReleaseKotlin :app:compileDebugKotlin` |
| 静态检查 | detekt(如项目配置) |
| 老 JSON 兼容 | 预置含 8 卡旧序号的 `dashboardCardConfigs` → 升级后 8 卡序不变、HABIT_TODAY 可见在尾、SUBSCRIPTION 不可见 |
| 空态 | 无目标/无纪念/无习惯/无近期到期 → 对应卡不占用空间 |
| 手动开关 | 每卡可在管理弹窗开/关,状态持久化,重启保留 |
| 点击跳转 | 每卡点击区(跳转胶囊/标题/条目)→ 对应模块专页;长按拖拽换位仍可触发、互不冲突 |
| 跳转胶囊 | 胶囊边框+淡底+文字同色;明/暗主题下 1dp 边框与 0.10 底色对比度清晰;点击 ripple 生效、无整卡误触 |
| 订阅计算 | 与 `LifeDailyCheckWorker.kt:220-261` 算法一致(短月 31→28/30 钳制) |
| 双主题 | 明/暗两主题下新卡、进度环、标签对比度正常 |