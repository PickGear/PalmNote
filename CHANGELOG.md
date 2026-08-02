# Changelog

All notable changes to PalmNote will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and this project adheres to [Semantic Versioning](https://semver.org/).

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
