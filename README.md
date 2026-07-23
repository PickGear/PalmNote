# PalmNote 掌记

一款纯本地存储的生活记录工具，集成记账、资产管理、生活计划与记录于一体。无需注册、无需联网，数据完全由你掌控。

## 功能特性

### 🏠 首页 Dashboard
- 净资产、月度收支概览
- 预算提醒、目标进度、纪念日倒计时
- 卡片拖拽排序、自定义显隐

### 📦 物品管理
- 物品录入、分类、状态追踪（持有/闲置/已出/丢失/退役）
- 使用记录、日均成本计算
- 保修/保险/维护提醒
- 关联账单、图片附件

### 💰 记账
- 多账本、多钱包管理
- 收支分类、预算设置、月度/年度报表
- 日历视图、高级筛选
- CSV/XLSX 导入、OCR 识别
- 桌面小组件

### 🌿 生活模块
- **计划类**：存钱计划、购物清单、旅行规划、阅读计划、学习计划、待办任务
- **时间类**：倒计时、正数日、生日、纪念日
- **记录类**：习惯打卡（热力图）、心情记录（日历+趋势图）、日记、专注计时、订阅管理、周报月报
- **通用能力**：自定义模板、跨模块关联、成就徽章

### 🔒 安全
- 应用锁（PIN + 生物识别，PBKDF2 加密）
- AES-GCM 加密备份
- 纯本地存储，零网络请求

## 技术栈

| 层 | 方案 |
|---|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room |
| 偏好存储 | DataStore |
| 图片加载 | Coil 3 |
| 导航 | Navigation Compose |
| 图表 | Compose Canvas 自绘 |
| 依赖注入 | 手动 DI（AppContainer） |
| 备份加密 | AES-GCM + PBKDF2 |
| OCR | ML Kit（本地离线） |
| 构建 | Gradle + Kotlin DSL |

## 架构

```
com.palmnote/
├── data/           # 数据层：Room DAO/Entity、Repository 实现、备份/导出/OCR
├── domain/         # 领域层：Repository 接口、领域模型、工具类
├── di/             # 依赖注入：AppContainer
├── ui/             # 表现层：按模块分包
│   ├── asset/      # 物品模块
│   ├── bills/      # 记账模块
│   ├── dashboard/  # 首页
│   ├── life/       # 生活模块（plan/time/record）
│   ├── settings/   # 设置
│   ├── search/     # 搜索
│   ├── components/ # 通用组件
│   └── theme/      # 主题（Color/Shape/Type/Icon）
└── PalmNoteApp.kt  # Application
```

## 构建

```bash
# 克隆项目
git clone https://github.com/Bailinana/PalmNote.git

# 打开 Android Studio 或命令行构建
./gradlew assembleDebug
```

**环境要求：**
- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 35

## 设计规范

详见 [docs/design-spec.md](docs/design-spec.md)，涵盖色彩、字体、间距、组件、动画等完整设计系统。

## 开源协议

本项目基于 [GPL-3.0](LICENSE) 许可证发布。

## 联系方式

- GitHub Issues：https://github.com/Bailinana/PalmNote/issues
