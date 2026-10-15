# 桌面小组件设计说明

十一个组件（账单 / 待办 / 计数 / 物品 / 密码本 / 净值 / 订阅 / 概览 / 打卡 / 快捷入口 / 接下来）统一走 v2 设计语言，本文记录其设计约定与机制。组件清单与布局以 `ui/widget/` 与 `res/layout/widget_*.xml` 为准。

## 设计语言

- **圆角卡片**：统一卡片底（`widget_layout` 根节点），底色可调透明度。
- **六族色**：各组件的强调元素（徽标、色点、占比条段）取 `WidgetData.colorFamily` 的同族色，运行时按当前主题包取色，亮暗两套。
- **强调色逐实例覆盖**：配置页可选「跟随应用」或强制某个主题包的主色，只影响该组件（`widgetAccentThemeFor`），跟随主题色的组件（账单 / 概览 / 接下来）生效。
- **文字层级**：主数值 / 标题用主文字色，次要说明用 `widget_v2_text_secondary`；深色模式走 `values-night` 令牌。
- **金额折叠**：大数按万 / k·M 紧凑格式，溢出 ellipsize 兜底。
- **无障碍**：动态行与快捷 chip 均带 contentDescription；选择器 17 个 info 描述齐全。

## 「接下来」焦点卡

一张卡只显示当前最该处理的一件事，候选按序取第一个：今天到期未完成待办（卡上直接勾）→ 3 天内要扣费的订阅（金额 + 扣费日）→ 最近的倒数事件。底层一变，数据变更刷新链路自动把卡切到下一件，无需任何配置。

## 尺寸分档（响应式）

- 组件尺寸读取走 `sizedRemoteViews`：Android 12+ 用 exact sizes 多份布局，旧版按 min/max 取两档。
- **高度 < 160dp（不足 2 格）收起次要元素**：计数组件藏日历条、保留「目标日期」说明行，物品组件藏占比条、色片只留一行。
- 快捷入口宽度 < 110dp（缩到 1×1）时换小卡布局：只留「记一笔」主按钮。
- 计数组件宽度 ≥ 400dp 显示多事件列表，否则只显示最近一个焦点事件。
- `onAppWidgetOptionsChanged` 触发重绑，拖拽改尺寸即时生效。

## 刷新机制

- **数据变更主动刷新**：`WidgetRefreshConsumer` 消费数据事件；备份恢复期间抑制更新（`WidgetUpdateHelper.restoring`）。
- **午夜刷新**：日期敏感组件（待办 / 打卡 / 计数）由 AlarmManager 每日 00:00 触发 `WidgetMidnightReceiver` 重排，开机后 `WidgetBootReceiver` 补排。
- PendingIntent requestCode 按组件分段，避免跨组件互相覆盖。

## 配置与添加

- `WidgetConfigActivity`（9 个组件）：透明度按实例覆盖；账单组件另有账本多选。
- 应用内添加：`WidgetPin`（`requestPinAppWidget`），预览图 `widget_preview_*`（亮暗两套，`drawable-nodpi` / `drawable-night-nodpi`）由 `WidgetPreviewPngGenerator` 离屏渲染重出（`WIDGET_PREVIEWS=1` 手动触发）。
- 桌面直接操作：待办勾选、打卡签到由广播 Receiver 就地完成。

## 数据口径

- 模板枚举经语义身份（`LifeTemplateKind`）与 `getCategoryName` 翻译，不走图标字面量。
- 生日 / 纪念日的农历滚动与提醒、详情页共用同一口径（`nextYearlyOccurrence`）；概览倒数卡与计数组件共用同一个农历开关解析（`fieldsDataLunarFlag`）。
- 「还剩几天」一律自然日（`DateUtils`），毫秒除法会在当天判错。
