# 生活页 v3.0 —— 首页重做方案(定稿·待实施)

> ⚠️ **本文档已并入 `docs/life-refactor-master-plan.md`（生活页全面重构计划·总纲，2026-09-19）。**
> 其信息架构由总纲 **§六** 承接，分期已重排。本文件保留作为过程记录，不再单独维护。
> **⚠️ v1.26 校准（2026-09-21）**：生活页 UI 已**完全重构**（旧 UI 子树整棵删除），本文中任何**代码引用 / 现状描述**一并失效。见总纲「**校准 · v1.26**」。


> 状态:定稿,待实施(2026-09-19 用户拍板主方向与命名,尚未动一行代码)
> 目标:把生活页从"第二个首页"改回"生活的首页" —— 一屏内回答"我今天过得怎么样",并能就地完成打卡与记录。
> 关系:本方案**部分撤销** [life-page-plan.md](life-page-plan.md)(v2.0)§1.3 的「分类卡」决定与 §3.4;并对齐 [design-spec.md](design-spec.md)§15.1 已经写下的目标形态(「FAB 展开式 8 个菜单项」「概览区」)。
> 版本:2026-09-19 · v3.0

---

## 一、方案总览

### 1.1 核心问题

六条。前四条是结构问题,后两条是实现欠债。

| # | 问题 | 代码证据 |
|---|---|---|
| P1 | 一屏讲了三遍「今天」 | `LifeScreen.kt` 的 `CategoryHomeCard`(「今日 +N」)、`TodayBoardHomeCard`(`TimeSlots`)、`TodoHomeCard`(`todoItems.take(4)`);「逾期」在 `life_board_overdue` 与 `life_home_todo_overdue` 两处各出现一次 |
| P2 | 生活本体在首页不可见 | 打卡/心情/日记/专注/存钱进度经 `LifeViewModel.observeTemplates()` 的 `flows` 汇成 `templatePreviewItems`,`CategoryHomeCard` 只取 `sumOf { it.size }`,内容一条未渲染 |
| P3 | 「模板」泄漏到一级界面 | 顶栏 `life_template_manage`;面板标题 `life_select_type_to_create`;`FunctionSheet` 的 FilterChip 按 `life_category_plan/time/record` 筛 |
| P4 | 与首页 Dashboard 抢活 | `DashboardCards.kt` 的 HABIT_TODAY / ANNIVERSARIES / TODAY / SUBSCRIPTION 四卡全部 `onNavigateToLife` |
| P5 | 首页为 3 个计数建 N 条流 | `LifeViewModel.kt` `templates.map { tpl -> flow }` → `combine(flows)`;模板数 = 流数 |
| P6 | 两处视觉欠债 | 三卡用 `CardDefaults.cardElevation(1.dp)`,与「1dp 细描边替代重阴影」不符;`life_home_today_added` 用 10.sp,低于可读下限 |

### 1.2 主问题定式

**生活页只回答一个问题:「我今天过得怎么样」。**

- Dashboard 回答「我整体怎么样」(跨 4 模块的汇总)
- 生活页回答「我今天过得怎么样」(生活内容 + 就地完成)
- 其余诉求(「我的内容在哪」「我要记一笔」)降级为这一页的下半段与右下动作,不再各占一屏

一页只回答一个问题,后面所有取舍都回到这一条。

### 1.3 决策锁定表

| 维度 | 决定 | 来源 |
|---|---|---|
| 主问题 | 「我今天过得怎么样」 | 2026-09-19 用户拍板 |
| 首屏结构 | 今日条 / 今天 / 概览 / 功能 四段 | 2026-09-19 |
| 「概览」段命名 | **概览** | 2026-09-19 用户拍板;候选「近况」因是自造上位词被否(见 §五) |
| 分类三小卡(目标/纪念/记录) | **删除** —— 撤销 v2.0 §1.3「仅保留三小卡(2026-08-18 确认)」与 §3.4 | 本次;其计数职责移交「概览」瓦片 |
| 入口收敛 | 16 个内置模板 → 8 个意图入口,宫格呈现 | `design-spec.md` §15.1 已有「FAB 展开式 8 个菜单项」 |
| 「模板管理」入口 | 顶栏删除,下沉为宫格右上角「管理」 | 时光序「应用管理」做法 |
| 「模板」一词 | 一级界面不再出现(代码内部仍可用 `template`) | 文案纪律:禁止发明/泄漏元概念 |
| 右下 FAB | 保留位置与主色,语义改为「记一笔」(只列今天能记的高频动作) | 沿用 v2.0 §1.3「新建一律走 FAB」 |
| 打卡 / 记心情就地完成 | **新增**,本次核心增量 | 本次 |
| 逾期一键推迟 | 保留(现有交互正确) | v2.0 已有 |
| Dashboard 生活类卡片 | 保留不动,靠**可操作性**划界(见 §3.3) | 2026-09-19 用户选择「保留」 |
| 动态分类区(`design-spec.md` §15.1) | **待定**,与意图宫格是两条路(见 §九) | 需用户确认 |

### 1.4 范围

**做**:今日条 / 今天卡(三卡合一 + 就地打卡与记心情) / 概览快照瓦片 / 意图宫格 + 宫格管理 / 顶栏清理 / 文案重排 / ViewModel 按需查询 / 配置结构升级与迁移 / 两处视觉欠债。

**不做(本轮)**:动态分类区(`life_categories` 表) / 分类大卡形态 / 时间类四页合并 / Dashboard 卡片删减 / 字段系统扩充。

---

## 二、对标结论

| 产品 | 首屏是什么 | 「＋」的语义 | 可借鉴的一条 |
|---|---|---|---|
| 时光序 | 「应用」宫格,十几块瓦片,每块带实时状态(「倒数纪念日 有6个重要日期」「专注 今天专注0分」「记账 共记了3笔」) | 按当前上下文 | **入口＝组件＝可管理,是一件事**;瓦片必须自带状态 |
| 滴答清单 | 「今天」= 任务 section + 习惯 section | 加一条任务 | **同类内容在一页里分 section,且 section 可关**;5.0 起底部 tab 可自定义 |
| Things 3 | 用户自己的清单,无功能说明书 | 加进当前容器 | **首屏不教育用户「我有哪些功能」** |
| Apple 健康 | 「摘要」= 可编辑卡片流,每卡 = 一类数据的真实快照 + 迷你图 | 无全局 ＋ | **一张卡 = 一类内容的一个快照**,摘要本身可编辑 |
| Daylio / Forest | 就是「今天心情打几分」/「开始种树」 | 首屏唯一动作 | **高频动作在首屏一步做完** |

**共识**:没有任何一个成熟产品把「模板库」摆在首页。模板是 2~3 层深的管理能力。

**分歧**:首屏是「看板」(时光序、Apple 健康)还是「动作」(Daylio、Things 3)。

本方案取**看板 + 就地动作**:首屏呈现现状(看板),但最高频的两个动作(打卡、记心情)在首屏就地完成(动作)。理由:PalmNote 的生活记录是"轻记录"场景(心情/打卡/日记一天 1~3 次),把动作推到二级页是当前最大的体验损失;而纯动作型放不下 16 个模板的组织诉求。

---

## 三、信息架构

### 3.1 四段结构

```
今日条   9月19日 周六 · 3 件待办 · 打卡 2/5 · 今天还没写日记        [+ 记一笔]
今天     [逾期行] 待办勾选 · 打卡 chips · 「今天还没记心情」轻提示
概览     2 列快照瓦片:纪念日倒计时 / 存钱进度 / 习惯连胜 / 本周专注 / 心情 7 日
功能     4 列宫格:记心情 打卡 写日记 待办 计划 纪念日 专注 更多        (右上「管理」)
```

段落职责:

| 段 | 回答 | 可操作性 |
|---|---|---|
| 今日条 | 今天整体什么状态 | 无(只有「记一笔」入口) |
| 今天 | 今天具体要做什么、还差什么 | **可操作**:勾待办、打卡、记心情 |
| 概览 | 不按天走的生活近况 | 只读,点击进专页 |
| 功能 | 我要去哪 | 导航 + 管理 |

### 3.2 16 模板 → 8 意图入口映射

| # | 入口 | 覆盖模板 | 落地页 |
|---|---|---|---|
| 1 | 记心情 | 心情 | `LifeMoodRoute`(现成) |
| 2 | 打卡 | 打卡 | `LifeHabitRoute`(现成) |
| 3 | 写日记 | 日记 | `LifeJournalRoute`(现成) |
| 4 | 待办 | 待办 | `LifeTodoRoute`(现成) |
| 5 | 计划 | 存钱计划 / 购物计划 / 旅行计划 / 阅读 / 学习计划 | `LifeCategoryDetailRoute("计划")`(现成) |
| 6 | 纪念日 | 倒计时 / 正数日 / 生日 / 纪念日 | `LifeCategoryDetailRoute("时间")`(Phase 1)→ 聚合页(Phase 2,见 §九) |
| 7 | 专注 | 专注 | `LifeFocusRoute`(现成) |
| 8 | 更多 | 订阅记录 / 周报月报 / 全部自定义模板 | `GenericTemplateListScreen`(定义在 `common/TemplateListScreen.kt:76`,现有组件,需新接线路由) |
| — | (不做入口) | 周报月报 | 归到 `LifeStatsRoute` |

规则:

- 每格 = 一个用户会说的话,不是一个存储分类(现行的 计划/时间/记录 是存储分类)
- 一格进去只能到一个页面(「更多」是容器,例外)
- 本表同时是**文案表**:入口名进 `strings.xml`,中英必须对等
- 「模板」二字在全部入口名与段落标题里出现 0 次

### 3.3 与 Dashboard 的职责边界(重要)

2026-09-19 用户选择「Dashboard 保留 + 生活页退化为纯入口」,同时主方向又选了「今日为主」。这两个只有在**靠可操作性划界**时才同时成立:

| | Dashboard | 生活页 |
|---|---|---|
| 今日待办 | 只读摘要,点一下跳生活页 | **可勾选** |
| 习惯今日 | 只读 + 一跳 | **可就地打卡** |
| 心情 / 日记 | 不出现 | **可就地记** |
| 纪念日 | 只读倒计时 | 快照 + 进专页 |

即:**内容是重复的,职责不重复** —— 一个负责"扫一眼",一个负责"做完"。

这样 Dashboard 一行代码不用改(零回归风险),生活页获得明确的不可替代价值。

> ⚠️ **待确认**:若你实际想要的是「生活页只做入口」,那么 §4.2 的「今天」段应整段删除,§4.3「概览」上提为第一段,§4.5 的 FAB「记一笔」也随之删除。请在实施前给一个结论。

---

## 四、UI 线框

### 4.1 今日条

```
┌──────────────────────────────────────────────────┐
│  9月19日 周六                      [ + 记一笔 ]  │
│  3 件待办 · 打卡 2/5 · 今天还没写日记            │
└──────────────────────────────────────────────────┘
```

- 第一行:日期 + 星期(复用现有 `life_home_today_board` 的日期格式习惯),右侧主色胶囊按钮
- 第二行:**只讲非零的事**,用「 · 」连接。零值项整条不出现
  - 待办 0 件 → 不显示「0 件待办」
  - 全部打卡完成 → 显示「打卡 5/5 已完成」
  - 已写日记 → 不显示日记项
- 三项全为零时,第二行改为邀请式文案(见 §五)
- 数据流:`LifeUiState.todaySummary`,见 §7.1

### 4.2 今天卡

```
┌──────────────────────────────────────────────────┐
│  今天                                             │
│  ┌ 交房租 · 已逾期 2 天 ────────── [ 推到今天 ] ┐ │
│  ○ 取快递                              全天       │
│  ○ 买猫粮                              09:00      │
│  打卡   (冥想) (喝水) 读书  跑步                   │
│  ────────────────────────────────────────────    │
│  今天还没记心情 · 写一句日记        [记心情][写日记]│
└──────────────────────────────────────────────────┘
```

变更对照(与现状):

| 现状 | v3.0 |
|---|---|
| 分类三小卡(目标/纪念/记录)+ 今日 +N | **删除**。计数职责移交 §4.3 概览瓦片 |
| 今日看板的周历(`WeeklyCalendar`) | **删除**。日历是"跨天查看"工具,不属于"今天"这一段;需要时从待办专页进 |
| `TimeSlots` 的 早晨/上午/下午/晚间/全天 五个时段标题 | **删除**。5 个时段标题占了近 1/3 卡高,信息量却是零;改为按时间升序平铺 + 右侧时间戳 |
| 逾期在今日看板与待办卡各一段 | **合并为卡内首行**,带「推到今天」 |
| 待办卡只读勾选 → 专页 | 卡内直接勾选(现有 `onToggleItem` 已支持) |
| — | **新增**:打卡 chips 行,点一下就地打卡 |
| — | **新增**:心情/日记轻提示行(未记时出现) |

- 打卡行:已打卡的 chip 用模板色 `tint@14%` 底 + 模板色文字;未打卡用 `outline` 描边 + `onSurfaceVariant` 文字。点一下即打卡(GestureDetector 单击),长按进该习惯详情
- 最多显示 5 个 chip,超出显示「+N」
- 打卡数据源:`GoalRepository`(习惯仍在 `Goal`,不并入 LifeItem —— 沿用 v2.0 §2.3 决定)
- 待办最多 5 条,超出显示「查看全部」
- 轻提示行只在"今天既没记心情也没写日记"时出现;两者都记了就整行不出现
- 空态:今天待办 0、无逾期、打卡全完成、心情日记都记了 → 显示一句收束文案(见 §五),而不是空白

### 4.3 概览(快照瓦片)

```
┌──────────────────────────────────────────────────┐
│  概览                                             │
│  ┌────────────────┐  ┌────────────────┐          │
│  │ 纪念日          │  │ 存钱计划        │          │
│  │ 还有 12 天      │  │  ◕ 68%          │          │
│  │ 结婚纪念日      │  │  还差 ¥2,400     │          │
│  └────────────────┘  └────────────────┘          │
│  ┌────────────────┐  ┌────────────────┐          │
│  │ 习惯            │  │ 本周专注        │          │
│  │ 连续 12 天      │  │  245 分钟       │          │
│  │ ●●●○●●●         │  │  ▁▃▅▂▇▄▆        │          │
│  └────────────────┘  └────────────────┘          │
└──────────────────────────────────────────────────┘
```

**每块瓦片承载一条真实快照,不是一个计数。** 自检判据:

> **不点进去,用户能不能据此做一个决定?**

- ❌「记录 20」→ 20 是什么?不能
- ❌「目标 12 · 今日 +3」→ 今日 +3 的是什么?不能
- ✅「存钱计划 68% · 还差 ¥2,400」→ 能
- ✅「纪念日 还有 12 天 · 结婚纪念日」→ 能(要不要准备礼物)

视觉规则(内容决定形态,不是统一模板):

| 内容类型 | 形态 | 主色 |
|---|---|---|
| 日期类(倒计时/纪念日/生日/正数日) | 大数字 + 一行说明 | `LifeTime` / 模板色 |
| 比例类(存钱/阅读/学习/购物) | 进度环 + 百分比 + 差额 | 模板色 |
| 累计类(习惯连胜) | 大数字 + 近 7 日点阵 | `LifeHabit` |
| 时长类(专注) | 数值 + 7 根迷你柱 | `LifeFocus` |
| 记录类(心情) | 近 7 日迷你条 | `LifeMoodColor` |

- 最多显示 6 块(2 列 × 3 行);超出部分进「管理」排序
- 用户可在「管理」里决定显示哪几块、什么顺序
- 点瓦片 → 对应专页
- 无数据的瓦片不出现在概览里(不是显示一个 0)

### 4.4 功能宫格

```
┌──────────────────────────────────────────────────┐
│  功能                                     管理    │
│   [记心情]  [打卡]   [写日记]   [待办]           │
│   [计划]   [纪念日]  [专注]     [更多]           │
└──────────────────────────────────────────────────┘
```

- 4 列宫格,图标盒对齐 `design-spec.md` §五「分类标签(CategoryGrid)44dp 圆角方形」+ 20dp 图标
- 图标盒底色 = 该入口主色 `tint@14%`,图标色 = 主色
- 右上角「管理」→ §4.6
- 这就是 `design-spec.md` §15.1 写下的「FAB 展开式 8 个菜单项」的**常驻形态**:布告(宫格)与动作(FAB 面板)同构,但宫格常驻、不藏在 FAB 后面

### 4.5 「记一笔」面板(FAB)

现行 `FunctionSheet` 平铺 16 个模板 + FilterChip 按存储分类筛。v3.0 改为:

```
┌──────────────────────────────────────────────────┐
│  记一笔                                           │
│  ◉ 记心情        今天心情怎么样                    │
│  ◉ 写日记        今天发生了什么                    │
│  ◉ 加待办        记一件要做的事                    │
│  ◉ 其他类型…     全部模板                          │
└──────────────────────────────────────────────────┘
```

- 4 行,行样式复用 `SettingsMenuItem`(40dp 圆形彩色衬底 + 20dp 图标 + 标题 + 副标题 + ChevronRight)
  - **不**复用 `ChoiceDialog`:它是互斥单选,与"动作列表"语义不符
- 「其他类型…」→ `TemplateListScreen`(全部模板,覆盖自定义模板与长尾需求)
- 为什么只列 4 项而不是 16 项:`P3` —— 用户来这页是想"记点什么",不是来挑数据库表

### 4.6 宫格管理页(新增)

```
┌──────────────────────────────────────────────────┐
│  ← 生活页管理                                     │
│  显示在功能宫格          长按拖动排序              │
│  [≡] ◉ 记心情                                     │
│  [≡] ◉ 打卡                                       │
│  [≡] ◉ 写日记                                     │
│  [≡] ○ 待办            (关闭则从宫格移除)          │
│  ─────────────────────────────────────────────   │
│  模板管理                                    →    │
└──────────────────────────────────────────────────┘
```

- 新页面 `LifeHomeManageScreen`,从宫格右上角进入
- 上半:入口显隐 + 拖拽排序(拖拽复用 Dashboard 卡片排序方案,含 300ms 防抖持久化 —— v2.0 §1.3 已确立该做法)
- 下半:一行入口进 `TemplateManageScreen`(保留原页原样,只管模板隐藏/恢复/删除)
- 切开关即生效,无需「保存」

---

## 五、文案与命名规范

### 5.1 命名取舍(记录被否候选,避免以后重走)

| 候选 | 结论 | 理由 |
|---|---|---|
| 近况 | ❌ 否 | 自造上位词。系统设置 / WhatsApp / iCloud 里没有"近况"。与「备份计划」被否属同一类错误 |
| 最近 | ❌ 否 | 偏纯时间维度,而瓦片里有非时间内容(存钱进度) |
| 不给标题 | ❌ 否 | 该段是"页名覆盖不到的多元素块",按分区标题规则**必须有**标题 |
| **概览** | ✅ 采用 | 中文 App 里常见,非自造 |

### 5.2 新增 / 改动的 strings key

新增(中英对等):

| key | zh | en |
|---|---|---|
| `life_home_section_today` | 今天 | Today |
| `life_home_section_overview` | 概览 | Overview |
| `life_home_section_entries` | 功能 | Features |
| `life_home_record_action` | 记一笔 | Add |
| `life_home_entry_mood` | 记心情 | Mood |
| `life_home_entry_habit` | 打卡 | Check in |
| `life_home_entry_journal` | 写日记 | Journal |
| `life_home_entry_todo` | 待办 | To-do |
| `life_home_entry_plan` | 计划 | Plans |
| `life_home_entry_time` | 纪念日 | Dates |
| `life_home_entry_focus` | 专注 | Focus |
| `life_home_entry_more` | 更多 | More |
| `life_home_manage_title` | 生活页管理 | Life page |
| `life_home_manage_show_in_grid` | 显示在功能宫格 | Show in grid |
| `life_home_record_other` | 其他类型… | Other types… |
| `life_home_record_other_desc` | 全部模板 | All templates |
| `life_home_all_done` | 今天都完成了,记一句吧 | All done today |

改动 / 删引用:

| key | 动作 | 注意 |
|---|---|---|
| `life_new_create`(「新建」) | **值不动**;生活页 FAB 改用新 key `life_home_record_action` | ⚠️ **已实测**:该 key 有 4 处引用 —— `LifeScreen.kt:157`(FAB 文案)、`LifeScreen.kt:463`(空态按钮)、`TimeListScreen.kt:97` 与 `CountdownListScreen.kt:97`(列表页 ＋ 的 `contentDescription`)。**改值会让列表页的「＋」读成"记一笔",语义错误**。所以必须新增独立 key,不要改 `life_new_create` |
| `life_select_type_to_create`(「选择要创建的类型」) | 面板标题改用它还是新 key,实施时定 | 旧值不应再出现在一级界面 |
| `life_template_manage`(「模板管理」) | **保留 key**,仅删除生活页顶栏的 `IconButton` 引用 | `TemplateManageScreen` 仍在用 |
| `life_home_today_added`(「今日 +%d」) | **删除** | **已实测**:仅 `LifeScreen.kt:563`(`CategoryMiniCard`)一处引用,随三小卡一起移除 |
| `life_home_board_selected` / `life_home_slot_*` | 视周历与时段移除范围决定 | 删前 grep |
| `life_home_drag_hint`(「拖动调整顺序」) | 复用到 §4.6 | **已实测:当前 0 处代码引用**(死字符串)。正好是 §4.6 拖拽排序要用的文案,直接启用 |

**文案纪律提醒**:界面文案只回答「你会得到什么 / 按哪里」。任何"为什么这么设计"的说明都不进界面。

---

## 六、设计令牌对齐

### 6.1 对齐既有规范

| 项 | 规范 | 依据 |
|---|---|---|
| 卡容器色 | `MaterialTheme.colorScheme.surface` | 现有实现 |
| 卡圆角 | `MaterialTheme.shapes.large` = **16dp** | `core/.../ui/theme/Shape.kt`;`design-spec.md` §三 |
| 卡描边 | `Modifier.border(1.dp, colorScheme.outline.copy(alpha = 0.5f), shape)` | `design-spec.md` v3.0「修正 Card 边框 1dp」 |
| 卡阴影 | **删除 `CardDefaults.cardElevation(...)`** | 设计语言「1dp 细描边替代重阴影」 |
| 宫格图标盒 | 44dp 圆角方形 + 20dp 图标 | `design-spec.md` §五「分类标签(CategoryGrid)」 |
| 面板动作行 | 40dp 圆形衬底 + 20dp 图标 + 标题 + 副标题 + ChevronRight | `design-spec.md` §六 6.16 `SettingsMenuItem` |
| 文字下限 | 12sp | `design-spec.md` §二(`bodySmall` = 12sp 为最小正文) |
| 主色 | 一律 `MaterialTheme.colorScheme.primary`;模块强调用 `ModuleLife` | 不硬编码色 |

### 6.2 必守的分区标题规则

- 三段标题(今天 / 概览 / 功能)与页名「生活」不构成逐字重复 → 均给 `SectionHeader`,层级平级
- **标题只在卡外**,卡内不再印一遍 `titleMedium`
- 三段都是"页名覆盖不到的多元素块",所以都必须有标题;**不存在"全不用标题"的选项**

### 6.3 ⚠️ 记忆校正

原记忆写「圆角 20dp、卡间距 10dp、按钮 52dp 胶囊」,**与代码不符**:

- 卡圆角 = `Shapes.large` = **16dp**;20dp 是 `DialogShape`
- 卡间距:设置详情页 8dp(`design-spec.md` v4.2「卡片间距统一 8dp」),生活页现用 12dp
- 按钮 = `ButtonShape` = **12dp**

本方案一律以 `Shape.kt` 与 `design-spec.md` 为准。

---

## 七、数据与状态层

### 7.1 `LifeUiState` 变更

```kotlin
data class LifeUiState(
    // 保留
    val overdueItems: List<LifeItem> = emptyList(),
    val boardItems: List<LifeItem> = emptyList(),
    val todoItems: List<LifeItem> = emptyList(),
    val cardConfigs: List<LifeHomeSectionConfig> = LifeHomeSectionConfig.defaults,
    val isLoading: Boolean = true,
    val error: String? = null,

    // 新增
    val todaySummary: TodaySummary = TodaySummary(),
    val habitToday: List<HabitTodayRow> = emptyList(),
    val moodToday: MoodSnapshot? = null,     // 无则 null → 轻提示出现
    val journalToday: Boolean = false,
    val overviewTiles: List<OverviewTile> = emptyList(),
)

data class TodaySummary(
    val todoCount: Int = 0,
    val habitDone: Int = 0,
    val habitTotal: Int = 0,
    val journalWritten: Boolean = false,
)

/** 概览瓦片的密封类型:内容决定形态,不是统一模板。 */
sealed interface OverviewTile {
    val key: String
    val templateId: Long
    data class Countdown(...) : OverviewTile   // 大数字
    data class Progress(...) : OverviewTile    // 进度环
    data class Streak(...) : OverviewTile      // 大数字 + 点阵
    data class Focus(...) : OverviewTile       // 数值 + 迷你柱
    data class Mood(...) : OverviewTile        // 迷你条
}
```

**删除**:`templates` / `planTemplates` / `timeTemplates` / `recordTemplates` / `templatePreviewItems` / `scheduledItems` / `markedDates`。

> `markedDates` 只服务周历;周历移除后无需再查 6 个月的 dueDate。

### 7.2 查询策略:从"遍历模板"改为"按需查询"

现状(`observeTemplates()`):对每个模板建一条 Flow,再 `combine(flows)`。模板数 = 流数,且任一条变化触发全页重装配。

v3.0:首页要什么就查什么,**不遍历 `LifeTemplate`**。

| 段 | 数据 | 来源 |
|---|---|---|
| 今天·待办 | 今日到期 + 逾期 | `itemRepo.getScheduledBetween(todayStart, todayEnd)` / `getOverdue(todayStart)`(已有) |
| 今天·打卡 | 今日打卡进度 | `GoalRepository.getHabitGoals()`(一次聚合,不是每模板一条) |
| 今天·心情 | 今天是否已记 | `MoodDiaryRepository` 按今天区间取 1 条 |
| 今天·日记 | 今天是否已写 | `LifeMomentRepository` 按今天区间取 1 条 |
| 概览·专注 | 本周 7 天的分钟数 | `FocusRecordRepository` 区间聚合 |
| 概览·瓦片 | 用户配置显示的 N 块(建议 ≤6) | **按配置反查**,`N` 块 = `N` 条查询 |

要点:

- 查询条数从「模板数(16+自定义)」降到「配置的瓦片数(≤6)」+ 固定 5 条
- `debounce(300)` 需要重新评估:它在切回 tab 时会造成 300ms 空窗(先见 skeleton)。建议只保留在模板/配置流上,数据流直接发
- 新增 DAO 方法需按 §7.2 表格逐条核对是否已有(`LifeItemDao` / `GoalDao` / `FocusRecordDao` / `MoodDiaryDao` / `LifeMomentDao`)

### 7.3 配置结构升级与迁移

现状 `LifeHomeCardConfig`(core):`List<LifeHomeCardConfig>`,枚举 `CATEGORY / TODAY_BOARD / TODO`,JSON 存 Preferences。

v3.0:

```kotlin
enum class LifeHomeSectionType { TODAY, OVERVIEW, ENTRIES }

@Serializable data class LifeHomeSectionConfig(val type: LifeHomeSectionType, val visible: Boolean = true)
@Serializable data class LifeHomeEntryConfig(val key: String, val visible: Boolean = true, val order: Int = 0)
@Serializable data class LifeHomeOverviewConfig(val key: String, val visible: Boolean = true, val order: Int = 0)

@Serializable data class LifeHomePrefs(
    val sections: List<LifeHomeSectionConfig>,
    val entries: List<LifeHomeEntryConfig>,
    val overviews: List<LifeHomeOverviewConfig>,
)
```

**迁移**(旧的 `List<LifeHomeCardConfig>` → `LifeHomePrefs`):

| 旧 type | 新落点 | 规则 |
|---|---|---|
| `CATEGORY` | `OVERVIEW` 段可见性 | 直接映射 |
| `TODAY_BOARD` | `TODAY` 段可见性 | 直接映射 |
| `TODO` | `TODAY` 段可见性 | 与 `TODAY_BOARD` 取**逻辑或**;两者都 false 时 `TODAY` 段才隐藏 |

- `fromJson` 已有 `ignoreUnknownKeys` + `coerceInputValues`,旧 JSON **不会崩**
- 但需写一次性迁移函数,并在迁移后清理旧 key
- 必须补单测:`LifeHomePrefsMigrationTest` —— 覆盖「旧值全 true」「旧值全 false」「TODAY_BOARD=false 而 TODO=true」「空串/坏 JSON」

---

## 八、分期交付与验收矩阵

### 阶段 1 —— 入口收敛(不动数据层)

- 新增「记一笔」面板(§4.5),替换 `FunctionSheet`
- 新增功能宫格(§4.4),8 个入口
- 顶栏删除「模板管理」`IconButton`
- 新增 `LifeHomeManageScreen`(§4.6)
- `strings.xml` 中英对等

涉及:`app/.../ui/life/LifeScreen.kt`、新增 `app/.../ui/life/LifeHomeManageScreen.kt`、`app/src/main/res/values/strings.xml` + `values-en/strings.xml`

**验收**
1. 首屏 → 记一条心情,点击 ≤ 2 次
2. 一级界面出现「模板」二字的地方 = 0 处
3. 12 个入口/段落文案在 zh 与 en 均存在且语义一致

### 阶段 2 —— 今天合并 + 概览瓦片

- 删除分类三小卡、`WeeklyCalendar`、`TimeSlots` 时段标题
- 新建 `LifeTodayCard` / `LifeOverviewGrid` / 瓦片组件
- `LifeUiState` 与 `observeTemplates()` 按 §7.1 / §7.2 重构
- `LifeHomeCardConfig` → `LifeHomePrefs` + 迁移 + 单测

涉及:`LifeScreen.kt`、`LifeViewModel.kt`、`core/.../ui/life/LifeHomeCardConfig.kt`、`app/.../ui/life/common/LifeCards.kt`(视复用情况)

**验收**
1. 一屏内可完成:勾一条待办 + 打一次卡 + 看到纪念日倒计时
2. 模板/条目增删后,首屏查询条数不随模板数增长
3. `LifeHomePrefsMigrationTest` 全绿

### 阶段 3 —— 概览瓦片落地与 Dashboard 边界确认

- 概览瓦片按 §4.3 的 5 种形态实现
- 确认 §3.3 的边界解释(或按 §九 待定项 1 执行删除)
- 更新 `design-spec.md` §15.1(把"目标形态"改为已实施的形态)与 v2.0 文档的 Superseded 标注

**验收**:同一内容在两页出现时,只有一页可操作;`docs` 内不再有互相矛盾的形态描述

### 全阶段闸门(每次改完跑,不提交)

按既有约定:`kotlin_syntax.py`(新增文件必须显式传入,`--git-modified` 会假绿)、`kotlin_balance.py --report ...`(注意 `--report` 参数在前)、`static_gate`。构建与装机由用户在 Android Studio 做。

---

## 九、风险与待定项

1. **§3.3 的 Q1/Q2 冲突解释需你确认。** 你选了「今日为主」又选了「生活页退化为纯入口」,我按"内容重复但职责不重复"来解释。若你的原意就是"只做入口",则 §4.2 整段删除、FAB 删除、概览上提 —— 三处都要改。**这是本方案唯一的阻塞项。**
2. **动态分类区(`design-spec.md` §15.1)与之的关系待定。** 蓝图是"按用户自定义分类分 Section 渲染内容",本方案是"按用户意图分入口"。两者不互斥(概览瓦片可以按用户分类分组),但**不能同轮做**。建议:先做本方案,动态分类留到有真实需求时再评估。
3. **删除分类三小卡是不可逆的信息架构变更。** v2.0 §1.3 曾明确"仅保留三小卡(2026-08-18 确认)"。本方案撤销该决定,理由是三小卡本身也是"存储分类分组"(目标/纪念/记录),与 P3 同源。**若你对三小卡有情感或使用依赖,请现在说,不要等实施后。**
4. **「纪念日」入口在阶段 1 只能到 `CategoryDetailScreen("时间")`**,不是真正的合并页 —— 用户会看到一层中转。建议阶段 2 做一个真正的「纪念日」聚合页(4 个时间类模板分组),但需新建页面。
5. **概览瓦片数量上限。** 建议 6 块(2×3),超出进管理排序。但若用户把 16 个模板全开,概览会很长 —— 需要在 §4.6 里给一个"最多 6 块"的硬限制或提示。
6. **FAB 语义变化会让老用户困惑**(原本"新建"→ 现在"记一笔")。需进 changelog,并在 `docs/DEVELOPMENT.md` 版本说明里点出。
7. ~~**`life_new_create` 改值的影响面未测。**~~ **已排查并给出结论**(见 §5.2):该 key 有 4 处引用,含两个列表页 ＋ 按钮的 `contentDescription`,**不可改值**,改用新 key `life_home_record_action`。风险已消解,保留此条作为方法记录:凡改动共享 string key 前,先 grep `R.string.<key>` 的全部引用点。

---

## 附录:涉及文件清单

| 文件 | 动作 |
|---|---|
| `app/src/main/java/com/palmnote/ui/life/LifeScreen.kt` | 重写主体;`FunctionSheet` 删除,新增今日条/今天/概览/宫格四段 |
| `app/src/main/java/com/palmnote/ui/life/LifeViewModel.kt` | `LifeUiState` 重构 + `observeTemplates()` 改为按需查询 |
| `app/src/main/java/com/palmnote/ui/life/LifeHomeManageScreen.kt` | **新增** |
| `app/src/main/java/com/palmnote/ui/life/common/LifeCards.kt` | 视复用情况增减瓦片组件 |
| `core/src/main/java/com/palmnote/ui/life/LifeHomeCardConfig.kt` | 改为 `LifeHomePrefs` + `SectionConfig` / `EntryConfig` / `OverviewConfig` + 迁移 |
| `core/src/main/java/com/palmnote/data/db/dao/*.kt` | 按 §7.2 表格补齐所需的聚合查询(逐条核对是否已有) |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | 新增 §5.2 的 key,中英对等 |
| `app/src/main/java/com/palmnote/ui/life/common/LifeNavHost.kt` | 「更多」入口需接线 `TemplateListScreen` 的新路由 |
| `docs/design-spec.md` §15.1 / §六 | 更新为已实施形态 |
| `docs/life-page-plan.md` | 顶部加 Superseded 标注,指向本文档 |
| `app/src/test/java/com/palmnote/ui/life/LifeHomePrefsMigrationTest.kt` | **新增** |

---

## 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| 3.0 | 2026-09-19 | 首版。主方向「今日为主」、概览命名、入口 16→8、Dashboard 边界四条由用户拍板;待定项 1 为阻塞项 |
