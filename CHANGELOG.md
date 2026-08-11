# Changelog

All notable changes to PalmNote will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.3.0] - Unreleased

### Added
- **密码本模块**（`feature/vault`）：纯离线密码管理器，字段级 AES-256-GCM 加密 + 独立主密码（密钥包裹模式，改 PIN 无需重加密条目）
- 密码本：列表/搜索/分类筛选、详情遮罩查看（👁 切换）、CRUD、密码生成器（长度+字符集+熵强度）、一键复制 30 秒自动清剪贴板（哈希追踪不误清）
- 密码本安全：**生物识别解锁**（Keystore 不可导出密钥包裹 DK + BiometricPrompt/CryptoObject）、**无锁模式**（可跳过密码设置，DK 用非认证 Keystore 密钥包裹，随时可升级为 PIN/生物识别）、自动锁定规则可配置（立即/跟系统锁屏/超时 5 分钟，默认跟系统）、失败 5 次锁定 30 秒（防暴力，`LockoutTracker` 复用）、进入需验证可配置、重置密码本
- 密码本入口：Dashboard 卡片（仅显示条数统计，隐藏条目标题保护隐私，旧卡片配置自动合并）、设置页设置项（剪贴板清除/需验证/条目数/改主密码/重置）
- 数据库 v4 → v5：新增 `vault_entries` 表（`Migration4To5`）
- **数据库加密 SQLCipher**：全库加密（`net.zetetic:sqlcipher-android`），Room 经 `EncryptedOpenHelperFactory` 接入，明文库自动迁移，密钥存 SharedPreferences（跨设备恢复可用）
- **OCR 引擎替换**：ML Kit 闭源模型 → 自研 `OcrEngine` 接口 + **PaddleOCR PP-OCRv6**（`ppocr-sdk` 模块，ONNX Runtime 离线推理，模型打包 assets）；移除 ML Kit 依赖与 `coroutines-play-services`
- APK 体积优化：release 仅 arm64-v8a + `onnxruntime-mobile`，从 ~86MB 降至 ~42.6MB（后因 mobile 精简算子集不兼容 PP-OCRv6 模型导致 OCR 失败，回退为完整版 `onnxruntime-android 1.21.1`）
- 测试：`VaultCryptoTest`（加密往返/篡改检测/密钥派生）、`VaultPasswordGeneratorTest`、`Migration4To5Test`，单测 75 → 150（含 6 个 Room 迁移测试）

### Changed
- 金额存储从 `Double`(元) 迁移为 `Long`(分)：账单/钱包/预算/资产/周期模板/计划清单全链路精确整数运算，消除浮点误差
- 新增 `Money` 值类型（`domain/model/Money.kt`）与 `CurrencyUtils` 分单位格式化
- 数据库 v3 → v4：迁移重建涉金额表并做 ×100 换算（含已有数据）
- 数据库 v1 → v5 全链路迁移回归（`MIGRATION_1_2`/`2_3`/`3_4`/`4_5` 补注册）
- 数据库 v5 → v6：密码本数据从主库迁出至独立库 `palmnote_vault.db`（`Migration5To6`，best-effort 搬运后删旧表）；v6 → v7：删除 `bills`/`bills_recycle_bin` 未使用的 `timeOfDay` 死字段（`Migration6To7`），主库当前 v7
- CI：接入 `lintDebug`（abortOnError）、`testDebugUnitTest`（含 Room 迁移测试）、Room schema 变更校验
- CI：迁移测试改用 **Robolectric** JVM 运行（免费 runner 无 KVM，模拟器易挂起）；并行拆分为 quality/build 两 job、每 job 单次 Gradle 调用，依赖 build cache 跨 job 复用
- `lint.abortOnError` false → true
- detekt baseline 重生成：冻结既有 UI 代码债务（LifeScreen/BillDao/ReportScreen）
- **架构双模块化**：拆分 `core`（namespace `com.palmnote`，含 data/domain/通用 UI/资源/AppDatabase schema）与 `app`（namespace `com.palmnote.app`，业务 UI/备份/worker/vault）；移除空壳 `feature` 模块；AppDatabase schema 迁至 `core/schemas`（v1-v7），VaultDatabase 留 `app/schemas`（v1-v3）
- **CurrencyUtils 去除全局 context 反模式**：删除无 context 重载与 `AppContextHolder`，所有调用点显式传 `context`（Composable 取 `LocalContext.current`，ViewModel 注入 `@ApplicationContext`）
- 55 个 core+app 双引用 string key 按模块各放一份（AGP 各模块 R 类独立），并补迁 core 缺漏 key

### Fixed
- **严重：v2→v3 迁移索引名与实体自动生成名不一致，老用户升级必崩**（`idx_bills_*` → `index_bills_*`）
- 微信 CSV 导入未剥离负号，可能导致金额为负污染账本
- **每日"未记账"提醒误报**：按完整时间戳精确匹配改为当日日期区间匹配
- **正数日/倒计时/生日/纪念日差一天**：`epoch/86400000`(UTC) 改为系统时区日期转换
- 生日/纪念日 2/29 平年处理：`withYear` 异常 catch 中重复调用导致二次崩溃
- 订阅账单提醒：31 号在小月自动落当月最后一天；提醒后回写 `lastBilledDate` 防重复
- 日期格式国际化：`date_format_weekday_full` 等中英文参数类型不一致（lint StringFormatMatches）
- **备份不包含应用锁 SharedPreferences**：恢复后旧版 SHA-256 PIN 因 salt 重置而无法验证，用户被锁在数据外
- **备份前无 WAL checkpoint**：`-wal/-shm` 与主库可能不一致导致恢复损坏，改为 checkpoint 后复制一致快照
- `getExternalFilesDir` 可能返回 null 导致备份 NPE，增加内部存储回退
- 设置页版本号显示 v1.0.0 → v1.2.0（与 app_version 一致）
- detekt 配置：`ComplexMethod`/`TooManyFunctions` 规则名过时导致任务无法运行
- 死代码清理（未使用 DAO 方法/类）
- 今日待办入口跳转失效
- 账单列表查询加索引优化（`index_bills_*`）
- 报表页加载态缺失（首次加载闪空白）

### Security
- **系统备份关闭**（`allowBackup=false`）：阻止明文数据库（含 PIN hash）被 Google 云备份
- **应用锁启用时 FLAG_SECURE**：禁止截图/最近任务缩略图泄露财务数据
- **锁定状态持久化**：失败次数/锁定时长写入 SharedPreferences，杀进程无法绕过暴力破解防护
- **PBKDF2 迭代应用锁与密码本统一为 25k**：兼顾解锁延迟与暴力破解成本（旧 SHA-256 PIN 自动迁移）
- **PIN 校验/设置移至 IO 线程**：PBKDF2 迭代不再阻塞主线程

### i18n
- 抽取硬编码中文文案：钱包/账本删除确认、分类删除/重命名对话框、预算金额错误、倒计时清除、回到顶部、语言选择"English"、货币符号"¥"（7 处输入框前缀）
- 新增 27 个中英双语字符串资源
- 倒计时清除逻辑修复：`epoch/86400000`(UTC) 差一天

### Fixed (review pass)
- **每日汇总按本地日分组**：参考 Cashew/Veri Fin 做法——账单存完整时间戳（保留具体时刻），日历/周报的按日聚合改为**应用层（Kotlin）按本地时区分组**，移除 SQL 按 UTC 日分组的 4 个聚合查询，彻底消除凌晨账错位/同日覆盖问题
- **严重：Migration3To4 重建 `plan_list_items` 缺外键**、`wallets.icon` 缺 `DEFAULT 'Payments'`，Room 迁移后校验会崩——已补齐并与 schema 逐列/外键/索引完全一致
- **备份 WAL checkpoint 结果未检查**：busy>0 时可能漏并 WAL 页导致备份丢最新数据，改为检查结果、失败回退原始 db+wal+shm
- **订阅账单短月跳过**：billingDay 29/30/31 的订阅在短月被 `monthsBetween` 守卫压掉，改用 `plusMonths`（自动月末钳制）判断周期
- **应用锁锁定状态**：`persistLockout` 改同步 `commit()`（防进程被杀丢状态）；init 时清理已过期的锁定计数，避免重启后输错一次就再次锁定
- 全局账单金额搜索格式与列表过滤对齐（`printf('%.2f')` vs `toYuanString()`）
- 移除 5 个文件被误加的 UTF-8 BOM
- 新增 `BillRepository.getBillsByDateRangeByBook`；`DateUtils.millisToLocalDate` 公开化
- 报表饼图角度/百分比整数除法 bug：`cat.total / total` 全为 0，改为 `.toDouble()` 运算
- 账单过滤弹窗金额回显显示"分"而非"元"（`Long.toString()` → `toYuanString()`）
- 钱包编辑/预算编辑弹窗初始金额显示"分"而非"元"
- 账单列表搜索按金额匹配失效（内存过滤用分字符串），改为元字符串匹配
- 应用锁锁定倒计时在进程重启后未初始化，改为从持久化状态恢复

### Tests
- 新增 `MoneyTest`：`parse`/`fromYuan`/算术/`toYuanString`/`toMoney` 共 14 个用例（含舍入、NaN/Infinity、负值、非法输入）
- 新增 `BillDailySummaryTest`：按本地日分组的 3 个用例（含凌晨账归位）
- 新增 `Migration3To4Test`（instrumentation）：v3→v4 迁移 6 张表金额 ×100 换算 + schema 校验（wallets 默认值/plan_list_items 外键）
- 移除未使用的 `CurrencyUtils.formatYuan`

### UI
- 周报柱状图按星期槽位对齐，无记录的天留空
- 月度折线图补零铺满整月，不再因稀疏数据被压缩

### Added
- `Migration3To4` 迁移
- `BillRepository.getBillsByDateRange`

## [1.2.0] - 2026-07-28

### Added
- 应用锁：PIN 码设置/修改/忘记密码、生物识别（指纹/面部）解锁
- 钱包管理：新增钱包编辑页面，支持多钱包管理
- 分类管理：分类图标编辑、删除、隐藏系统
- GitHub Actions CI（构建 + 上传 APK）
- 开源基础设施：CONTRIBUTING.md / SECURITY.md / CODE_OF_CONDUCT.md / Issue & PR 模板

### Fixed
- 应用锁开关无响应（状态改为响应式 var）
- 应用锁生物识别开关无效（新增 biometricEnabledFlow）
- 应用锁 hasPin 状态不同步
- 日期选择器：默认日期改用 UTC 午夜，避免 UTC+8 时区显示前一天
- 数据管理页导入路径错误：改为 ZIP 备份恢复
- strings.xml app_version v1.0.0 → v1.2.0

### Changed
- APK 体积：ndk abiFilters 仅保留 arm64-v8a / armeabi-v7a
- OCR 识别：EXIF 方向自动旋转（旋转后原图回收，防内存翻倍）
- 导出 ZIP 补写 preferences.json 和 assets 图片
- 设置页：仅头像/昵称区域可触发编辑；移除底部版本信息
- DatePickerField 框线样式统一
- LICENSE: Apache 2.0 → GPL-3.0

## [1.1.0] - 2026-07-22

### Added
- 分类图标编辑/删除/隐藏系统
- 日历选中日期与记一笔页面双向同步
- 账单列表日期行入场动画
- Baseline Profile 扩展与启动优化
- DataCache 读写缓存 + 预加载
- 性能优化：Compose @Immutable、DataStore 批量读取、动画统一 300ms

### Fixed
- 账本筛选逻辑优化，切换账本/月份自动清除筛选
- 账本日期双向同步，保存账单后日历自动跳转
- 搜索模式下取消按钮英文截断
- 统一 FAB elevation 固定值消除按下抖动
- 导航栏 windowInsetsPadding 恢复
- 物品列表卡片 ripple 贴合圆角
- 搜索页面状态栏边距
- DatePicker 时区偏移修复

### Changed
- 去 Hilt 换手动 DI（AppContainer）
- 移除 Dagger/Hilt 全部依赖
- UI 优化：DatePickerField 框线统一、成本模式弹窗右对齐、提示文字统一
- 备份重构；导入导出优化；编辑页面重构
- 账单列表转账显示优化；金额紧凑格式阈值改为 100 万

## [1.0.0] - 2026-06-30

### Added
- 初始发布
- 首页 Dashboard：净资产、月度收支概览、预算提醒、目标进度、纪念日倒计时
- 物品管理：录入、分类、状态追踪、日均成本计算、保修/保险提醒
- 记账：多账本、多钱包、收支分类、预算设置、日历视图、CSV/XLSX 导入、OCR 识别、桌面小组件
- 生活模块：计划类、时间类、记录类、自定义模板
- 全英文本地化支持
- 备份加密（AES-GCM + PBKDF2）
