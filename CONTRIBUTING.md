# Contributing to PalmNote

感谢你愿意为 PalmNote 贡献代码！Thanks for your interest in contributing to PalmNote!

---

## 开发环境 / Development Environment

- Android Studio Ladybug (2024.2+) or later
- JDK 17
- compileSdk 36 / targetSdk 34 / minSdk 26
- Kotlin 2.2.20

## 代码规范 / Code Style

### 语言 / Language
- 全部使用 **Kotlin** — All code must be written in **Kotlin**
- 无 Java 文件（除非第三方库强制要求）— No Java files (unless required by a third-party library)

### 风格 / Conventions
- 遵循 Kotlin 官方 [Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- 缩进：4 空格 — Indent: 4 spaces
- `@Composable` 函数使用 PascalCase
- 资源 ID 使用 camelCase → snake_case 转换

### 命名约定 / Naming

| 类别 / Category | 约定 / Convention | 示例 / Example |
|----------------|-------------------|----------------|
| Composable 函数 | PascalCase | `AssetCard` |
| 普通函数/属性 | camelCase | `formatCurrency` |
| ViewModel | `XxxViewModel` | `AssetViewModel` |
| Screen | `XxxScreen` | `AddAssetScreen` |
| Repository | `XxxRepository` | `AssetRepository` |
| DAO | `XxxDao` | `AssetDao` |
| Entity | `Xxx` | `Asset` |
| 包名 / Package | 小写，按模块分包 | `ui/asset/` |

### Composable 参数顺序 / Parameter Order

```kotlin
@Composable
fun AssetCard(
    asset: Asset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ...
)
```

### 架构分层 / Architecture Layers

项目为双模块结构（`:core` + `:app`）：

**core（namespace `com.palmnote`）** — 可复用基础能力，不依赖 app：
- `core/data/` — Room DAO、Entity、Repository 实现、DataStore、Lock、Event
- `core/domain/` — Repository 接口、领域模型、Service、Util
- `core/di/` — Hilt 注入注解（`@Qualifier` 等）
- `core/ui/` — 通用 Compose 组件、主题、锁界面、通知、Widget

**app（namespace `com.palmnote.app`）** — 应用入口与业务：
- `app/data/` — 备份、导出、OCR、Repository 实现、Worker
- `app/domain/` — 业务 usecase（`com.palmnote.feature.{asset,bills,life}.usecase`）
- `app/di/` — Hilt 模块（`HiltModules.kt` 等）
- `app/ui/` — 业务 Compose 界面（per-module packages）
- `app/feature/vault/` — 密码本（字段级加密，独立 `palmnote_vault.db`）

新增跨模块复用的类应放 core，业务专属类放 app；core 不得反向依赖 app。

### 依赖注入 / Dependency Injection

使用 **Hilt**（KSP 编译期代码生成）。新增依赖请在 `di/HiltModules.kt` 的 `AppModule` / `RepositoryModule` / 对应 `@Module` 中注册。
We use Hilt. Register new dependencies in `di/HiltModules.kt`.

## Commit 规范 / Commit Convention

使用 Conventional Commits 格式：

```
<type>: <简短描述 / short description>
```

### 类型 / Types

| 类型 | 用途 / Usage |
|------|-------------|
| `feat` | 新功能 / New feature |
| `fix` | Bug 修复 / Bug fix |
| `refactor` | 重构 / Refactoring |
| `style` | 代码风格调整（不影响逻辑）/ Code style (no logic change) |
| `docs` | 文档 / Documentation |
| `chore` | 构建/配置/杂项 / Build, config, misc |
| `perf` | 性能优化 / Performance |
| `test` | 测试 / Testing |
| `i18n` | 国际化/本地化 / Internationalization |

### 示例 / Examples

```
feat: 添加物品保修提醒 / add warranty reminder
fix: 修复日期选择器时区偏移 / fix date picker timezone offset
refactor: 抽取 DateUtils 公共方法 / extract DateUtils
chore: 升级 Gradle 版本 / upgrade Gradle
```

## PR 流程 / PR Workflow

1. Fork 本仓库 / Fork this repo
2. 从 `main` 创建功能分支：`git checkout -b feat/my-feature`
3. 在新分支上开发 / Develop on your branch
4. Commit 遵循 Conventional Commits / Follow Conventional Commits
5. 推送到你的 Fork：`git push origin feat/my-feature`
6. 提交 Pull Request 到 `main` 分支

### PR 要求 / Requirements

- 尽量小的 PR（单一责任原则）/ Keep PRs small (single responsibility)
- 确保构建通过：`./gradlew assembleDebug`、`./gradlew testDebugUnitTest`
- 如果是 UI 改动，建议附带截图 / Attach screenshots for UI changes

## Issue 规范 / Issue Guidelines

- 提交前搜索是否已有相同 Issue / Search for existing issues first
- Bug 请附带：设备型号、Android 版本、复现步骤 / Include: device, Android version, steps to reproduce
- 功能请求请说明使用场景 / Describe the use case for feature requests

## 翻译 / Translation

PalmNote supports Chinese and English. Add new strings to both `values/strings.xml` (Chinese) and `values-en/strings.xml` (English).

## 行为准则 / Code of Conduct

请遵循 / Please follow: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
