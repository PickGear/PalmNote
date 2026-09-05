# PalmNote 开发规范手册

> 本文档定义 PalmNote 项目的开发流程、版本管理与发布规范。
> 所有贡献者（包括作者）必须遵守。

---

## 目录

1. [分支策略](#1-分支策略)
2. [提交规范](#2-提交规范)
3. [版本号规则](#3-版本号规则)
4. [Tag 管理](#4-tag-管理)
5. [Changelog 维护](#5-changelog-维护)
6. [发布流程](#6-发布流程)
7. [代码审查](#7-代码审查)
8. [测试要求](#8-测试要求)
9. [Issue 与 PR 规范](#9-issue-与-pr-规范)
10. [CI/CD 流程](#10-cicd-流程)
11. [代码风格](#11-代码风格)
12. [数据库迁移流程](#12-数据库迁移流程)
13. [Git Hooks](#13-git-hooks)
14. [多语言工作流](#14-多语言工作流)
15. [APK 签名](#15-apk-签名)
16. [依赖管理](#16-依赖管理)
17. [安全审查流程](#17-安全审查流程)
18. [性能预算](#18-性能预算)
19. [开发环境](#19-开发环境)
20. [应用商店发布](#20-应用商店发布)
21. [崩溃监控](#21-崩溃监控)
22. [日志规范](#22-日志规范)
23. [无障碍](#23-无障碍)
24. [开源发布隐私审查](#24-开源发布隐私审查)
25. [工作成果保护](#25-工作成果保护)
26. [附录：完整发布 Checklist](#附录完整发布-checklist)

---

## 1. 分支策略

采用 **GitHub Flow**，保持简单。

### 1.1 分支类型

| 分支 | 命名规则 | 用途 | 生命周期 |
|------|----------|------|----------|
| `main` | — | 永远可发布的稳定版本 | 永久 |
| Feature | `feature/<名称>` | 新功能开发 | 合并后删除 |
| Fix | `fix/<名称>` | Bug 修复 | 合并后删除 |
| Refactor | `refactor/<名称>` | 重构（不改变行为） | 合并后删除 |
| Docs | `docs/<名称>` | 文档更新 | 合并后删除 |
| Chore | `chore/<名称>` | 构建/CI/配置/依赖 | 合并后删除 |

### 1.2 命名示例

```
feature/vault              # 密码本模块
feature/vault-biometric    # 密码本生物识别
feature/sqlcipher          # 数据库加密
fix/backup-wal-checkpoint  # 备份 WAL 修复
fix/timezone-off-by-one    # 时区差一天
refactor/money-long-unit   # 金额 Double→Long
refactor/core-module-split # 双模块化
docs/design-spec           # 设计规范
chore/gradle-agp-upgrade   # Gradle 升级
```

### 1.3 分支规则

1. **main 分支保护**（GitHub 已配置）：禁止 force push、禁止删除分支——防误操作历史覆写与误删
2. **单人开发例外**：允许直接 push main（不强制 PR）——PR 流程与夜班自动化轮次冲突；
   多天期大功能仍必须走 feature 分支，完成后 squash 合回 main
3. **一个分支一个主题**：不要在 `feature/vault` 里顺手改记账逻辑
4. **分支从 main 创建**：`git checkout -b feature/xxx main`
5. **合并后立即删除**：本地 + 远程都要删
6. **合并方式**：feature 分支使用 **squash merge**，保持 main 历史干净

### 1.4 分支生命周期

```
main (v1.2.0)
 │
 ├── feature/vault          ← 从 main 创建
 │    ├── git commit ...
 │    ├── git commit ...
 │    └── PR → squash merge 到 main → 删除分支
 │
 ├── fix/backup-npe         ← 从 main 创建
 │    ├── git commit
 │    └── PR → squash merge 到 main → 删除分支
 │
 └── tag v1.3.0             ← 打 tag，发布
```

---

## 2. 提交规范

采用 **Conventional Commits** 格式。

### 2.1 格式

```
<type>(<scope>): <subject>

[可选 body]

[可选 footer]
```

### 2.2 Type 类型

| Type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(vault): add AES-256-GCM encryption` |
| `fix` | Bug 修复 | `fix(backup): WAL checkpoint before copy` |
| `refactor` | 重构（不改行为） | `refactor(money): Double to Long (fen units)` |
| `docs` | 文档 | `docs(readme): add screenshots` |
| `chore` | 构建/CI/配置 | `chore(ci): add Robolectric migration tests` |
| `test` | 测试 | `test(vault): add crypto round-trip tests` |
| `perf` | 性能优化 | `perf(startup): add Baseline Profile` |
| `style` | 格式/代码风格 | `style: remove UTF-8 BOM` |
| `ci` | CI 配置 | `ci: parallelize quality and build jobs` |
| `revert` | 回滚 | `revert: rollback onnxruntime-mobile` |

### 2.3 Scope 范围

可选，标识影响的模块/区域：

```
vault, bills, asset, dashboard, life, settings, backup,
core, app, ocr, db, lock, widget, ci, gradle
```

### 2.4 Subject 规则

- **type / scope 必须英文**；subject 中文或英文均可（项目历史提交以中文为主）
- 不超过 72 个字符
- 不加句号
- 中文 subject 直接陈述变更；英文用祈使语气（"add" 而不是 "added"）

### 2.5 示例

```bash
# ✅ 正确（中文 subject，项目主流风格）
git commit -m "feat(vault): 新增生物识别解锁（Keystore 密钥包裹）"
git commit -m "fix(bills): 日汇总按本地时区而非 UTC 分组"
git commit -m "refactor(db): 金额 Double 改 Long（分单位）"
git commit -m "chore(gradle): AGP 升级 8.13"

# ✅ 正确（英文 subject）
git commit -m "feat(vault): add biometric unlock with Keystore key wrapping"

# ❌ 错误
git commit -m "更新了密码本"              # 无 type/scope
git commit -m "fix bug"                   # 太笼统
git commit -m "feat(vault): add something."  # 不该有句号
```

### 2.6 Breaking Change

涉及不兼容变更时，在 footer 标注：

```
refactor(db): migrate amount from Double to Long

BREAKING CHANGE: all monetary fields now store fen (Long) instead of yuan (Double).
Database migration v3→v4 handles existing data conversion.
```

---

## 3. 版本号规则

采用 **语义化版本（SemVer）**。

### 3.1 格式

```
MAJOR.MINOR.PATCH
  │      │     │
  │      │     └── PATCH: 修 bug，不改 API/行为/数据格式
  │      └──────── MINOR: 加功能，向后兼容
  └────────────── MAJOR: 破坏性变更（数据格式/功能删除/不兼容改动）
```

### 3.2 判定标准

| 你做了什么 | 该升哪个 | 示例 |
|------------|----------|------|
| 修了个崩溃 | PATCH | 1.3.0 → 1.3.1 |
| 加了新功能，旧功能不受影响 | MINOR | 1.3.0 → 1.4.0 |
| 数据库增量迁移（加表/加列，向后兼容） | MINOR | 1.3.0 → 1.4.0 |
| 数据库破坏性迁移（改列/删列/数据格式转换） | MINOR + 升级警告 | 1.3.0 → 1.4.0，CHANGELOG 显著标注 + 升级前自动备份 |
| 删了功能 / UI 大改版，用户习惯改变 | MAJOR | 1.3.0 → 2.0.0 |

> **MAJOR 的定位**：面向终端用户的应用，MAJOR 留给"用户可感知的大改版"（如 2.0 全新设计），
> 不随每次数据迁移膨胀——否则版本号通胀后失去信息量。数据库迁移的分级标准见第 12 章。

### 3.3 预发布版本

```bash
v1.4.0-alpha.1    # 早期开发，功能不完整
v1.4.0-beta.1     # 功能完成，可能有 bug
v1.4.0-rc.1       # 候选发布，基本稳定
v1.4.0            # 正式发布
```

排序：`alpha < beta < rc < 正式`

### 3.4 PalmNote 特殊规则

由于 PalmNote 是面向终端用户的应用（非库/API），补充以下规则：

1. **数据库增量迁移**（加表/加列有默认值）：至少 MINOR
2. **数据库破坏性迁移**（改列类型/重命名/删列/数据转换）：MINOR + CHANGELOG 显著标注 +
   升级流程中自动执行恢复前备份；实机从上一版本升级验证
3. **加密方案变更**：影响已有备份/数据恢复的，必须 MAJOR 并提供数据迁移路径
4. **0.x 阶段**：在功能未稳定前，使用 `0.x.y` 版本号，明确告知用户"可能会有 breaking change"
5. **Android versionCode**：每次发布递增，与版本号一一对应

| 版本 | versionCode |
|------|-------------|
| 1.0.0 | 1 |
| 1.1.0 | 2 |
| 1.2.0 | 3 |
| 1.3.0 | 4 |
| 1.3.1 | 5 |
| 2.0.0 | 6 |

### 3.5 版本号更新位置

发布新版本时，需要同步更新以下位置：

```
app/build.gradle.kts        → versionName + versionCode
CHANGELOG.md                 → 添加版本条目
README.md                    → 如果有版本相关描述
```

---

## 4. Tag 管理

### 4.1 格式

```
v<MAJOR>.<MINOR>.<PATCH>
```

始终带 `v` 前缀。

### 4.2 创建 Tag

```bash
# 始终使用附注标签（-a），不要用轻量标签
git tag -a v1.4.0 -m "Release v1.4.0: 密码本生物识别解锁"
```

### 4.3 Tag Message 规范

```bash
# 简洁版
git tag -a v1.4.0 -m "Release v1.4.0"

# 推荐版（包含关键变更摘要）
git tag -a v1.4.0 -m "密码本生物识别解锁 | 自动锁定规则 | 手机号字段"
```

### 4.4 Tag 规则

1. **Tag 永远打在 main 分支上**
2. **Tag 与版本号严格对应**：`v1.4.0` tag 对应 `versionName = "1.4.0"`
3. **一个版本一个 tag**：不要给同一个版本打多个 tag
4. **不要删除已发布的 tag**：除非是明显的错误
5. **预发布 tag**：`v1.4.0-beta.1`，正式发布时再打 `v1.4.0`

### 4.5 操作命令

```bash
# 创建并推送 tag
git tag -a v1.4.0 -m "Release v1.4.0"
git push origin v1.4.0

# 列出所有 tag
git tag -l

# 查看 tag 详情
git show v1.4.0

# 删除本地 tag（仅限未推送的错误 tag）
git tag -d v1.4.0

# 删除远程 tag（仅限未推送的错误 tag）
git push origin --delete v1.4.0
```

---

## 5. Changelog 维护

采用 [Keep a Changelog](https://keepachangelog.com/) 格式。

### 5.1 文件位置

```
CHANGELOG.md   # 项目根目录
```

### 5.2 格式模板

```markdown
# Changelog

All notable changes to PalmNote will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- （正在开发中的功能）

### Fixed
- （正在修复中的 bug）

## [1.4.0] - 2026-09-15

### Added
- 密码本生物识别解锁（Keystore 不可导出密钥包裹 DK + BiometricPrompt）
- 密码本条目新增手机号字段

### Changed
- 自动锁定默认值从"立即"改为"跟系统锁屏"

### Fixed
- 备份 WAL checkpoint 结果未检查导致数据丢失

### Security
- PIN 校验移至 IO 线程，PBKDF2 不再阻塞主线程

## [1.3.0] - 2026-08-15

### Added
- 密码本模块（字段级 AES-256-GCM 加密）
- SQLCipher 全库加密
- PaddleOCR PP-OCRv6 替换 ML Kit
...
```

### 5.3 分类说明

| 分类 | 说明 |
|------|------|
| `Added` | 新功能 |
| `Changed` | 已有功能的变更 |
| `Deprecated` | 即将移除的功能 |
| `Removed` | 已移除的功能 |
| `Fixed` | Bug 修复 |
| `Security` | 安全相关 |

### 5.4 规则

1. **[Unreleased] 始终在最上方**：记录正在开发中的内容
2. **发布时把 [Unreleased] 内容移到新版本**：并填上日期
3. **日期格式**：`YYYY-MM-DD`（ISO 8601）
4. **每条记录对应一个用户可感知的变更**：不要写"重构了内部代码"
5. **关联 Issue/PR**：如有，附上链接
6. **安全变更单独分类**：`### Security` 不要混在 `Fixed` 里

### 5.5 与 Commit 的关系

| Commit type | Changelog 分类 |
|-------------|---------------|
| `feat` | `Added` |
| `fix` | `Fixed` |
| `refactor`（影响用户） | `Changed` |
| `refactor`（纯内部） | 不记录 |
| `docs` | 不记录 |
| `chore` | 不记录 |
| `test` | 不记录 |
| 涉及安全的 fix/feat | `Security` |

---

## 6. 发布流程

### 6.1 完整发布 Checklist

```markdown
## 发布 vX.Y.Z

### 发布前
- [ ] 功能开发完成，所有 PR 已合并
- [ ] CHANGELOG.md 已更新：[Unreleased] 内容移至新版本，填上日期
- [ ] 版本号已更新：app/build.gradle.kts 中 versionName 和 versionCode
- [ ] 全量测试通过：./gradlew testDebugUnitTest
- [ ] Lint 通过：./gradlew lintDebug
- [ ] Release 构建成功：./gradlew assembleRelease
- [ ] 实机测试：安装 Release APK，核心功能验证
- [ ] 数据库迁移测试：从上一版本升级，数据完整性验证

### 发布
- [ ] 合并所有待发布 PR 到 main
- [ ] 打 tag：git tag -a vX.Y.Z -m "Release vX.Y.Z: <摘要>"
- [ ] 推送：git push origin main && git push origin vX.Y.Z
- [ ] 在 GitHub 基于 tag 创建 Release
- [ ] 上传 APK 到 Release Assets
- [ ] 填写 Release Notes（从 CHANGELOG 复制）

### 发布后
- [ ] 验证 Release 页面和 APK 下载正常
- [ ] 删除已合并的功能分支
- [ ] 更新 README.md（如有版本相关描述）
```

### 6.2 Hotfix 流程

当线上版本有严重 bug 需要紧急修复时：

```bash
# 1. 从有问题的 tag 创建修复分支
git checkout -b fix/critical-backup-crash v1.3.0

# 2. 修复并提交
git commit -m "fix(backup): null check on getExternalFilesDir"

# 3. 合回 main
git checkout main
git merge --squash fix/critical-backup-crash
git commit -m "fix(backup): null check on getExternalFilesDir"

# 4. 打 patch 版本 tag
git tag -a v1.3.1 -m "Hotfix v1.3.1: 修复备份崩溃"

# 5. 推送并发布
git push origin main
git push origin v1.3.1

# 6. 清理
git branch -d fix/critical-backup-crash
git push origin --delete fix/critical-backup-crash
```

### 6.3 版本线管理

```
main:  v1.0 → v1.1 → v1.2 → v1.3 → v2.0 → ...
                ↑
          v1.1.1 (hotfix，从 v1.1 tag 创建，合回 main)
```

**只维护一条主线**。Hotfix 从问题版本的 tag 创建，修完合回 main，不维护多条并行版本线。

---

## 7. 代码审查

### 7.1 自审 Checklist

提交 PR 前，先自己检查：

```markdown
- [ ] 功能是否完整实现
- [ ] 是否有遗漏的边界情况（null、空列表、极端值）
- [ ] 是否有硬编码的中文字符串（应抽取到 strings.xml）
- [ ] 金额是否使用 Long（分）而非 Double（元）
- [ ] 数据库变更是否有 Migration
- [ ] 是否有内存泄漏风险（Context 引用、协程生命周期）
- [ ] 是否有安全风险（明文存储敏感数据、日志泄露）
- [ ] 单元测试是否覆盖关键逻辑
```

### 7.2 PR 审查要点

- 架构合理性：是否放在正确的模块/层
- 代码复用：是否有重复逻辑可以抽取
- 命名清晰：变量/函数/类名是否自解释
- 性能影响：是否有不必要的数据库查询/主线程阻塞
- 向后兼容：是否影响已有数据/功能

---

## 8. 测试要求

### 8.1 测试分层

| 层 | 工具 | 覆盖范围 |
|----|------|----------|
| 单元测试 | JUnit + MockK | ViewModel、Repository、Utils、Model |
| 迁移测试 | Room MigrationTestHelper + Robolectric | 所有数据库 Migration |
| UI 测试 | Compose Testing | 关键页面交互（可选） |

### 8.2 必须测试的场景

- **数据库迁移**：每个 Migration 都必须有测试，验证数据完整性
- **加密/解密**：往返测试 + 篡改检测
- **金额计算**：边界值、舍入、负数、零值
- **日期/时区**：跨时区、闰年、月末

### 8.3 测试命名

```kotlin
// 格式：`被测功能 _ 场景 _ 期望结果`
@Test
fun `vault crypto - round trip - plaintext matches`() { ... }

@Test
fun `Money - fromYuan with NaN - returns zero`() { ... }

@Test
fun `bill daily summary - midnight UTC+8 - groups to correct day`() { ... }
```

---

## 9. Issue 与 PR 规范

### 9.1 Issue 模板

**Bug Report：**
```markdown
## 描述
（简要描述 bug）

## 复现步骤
1. 打开 ...
2. 点击 ...
3. 看到错误

## 期望行为
（应该怎样）

## 实际行为
（实际怎样）

## 环境
- 设备：
- Android 版本：
- PalmNote 版本：
- 日志（如有）：
```

**Feature Request：**
```markdown
## 描述
（简要描述功能需求）

## 使用场景
（为什么需要这个功能）

## 方案设想
（如果有的话）
```

### 9.2 PR 规范

```markdown
## 变更内容
（做了什么）

## 关联 Issue
Closes #123

## 测试
（怎么验证的）

## 截图（如涉及 UI）
（截图或录屏）
```

---

## 10. CI/CD 流程

### 10.1 Pipeline 概览

PalmNote 使用 GitHub Actions，Pipeline 分为两个 Job：

```
┌─────────────┐     ┌─────────────┐
│   quality   │     │    build    │
│             │     │             │
│ • detekt    │     │ • assemble  │
│ • lintDebug │     │   Debug     │
│ • unit test │     │ • upload    │
│ • migration │     │   APK       │
│   test      │     │             │
└─────────────┘     └─────────────┘
     ↓                    ↓
  必须通过            产出 APK
```

### 10.2 触发条件

| 事件 | 触发 CI | 说明 |
|------|---------|------|
| Push 到 main | ✅ | 全量运行 |
| PR 到 main | ✅ | 全量运行 |
| Push tag (v*) | ✅ | 构建 + 创建 Release |
| Push 到 feature/* | ❌ | 不触发（本地验证） |

### 10.3 Quality Job 规则

```yaml
quality:
  steps:
    - name: Detekt
      run: ./gradlew detekt        # 静态分析

    - name: Lint
      run: ./gradlew lintDebug     # abortOnError = true

    - name: Unit Tests
      run: ./gradlew testDebugUnitTest  # 含 Room 迁移测试

    - name: Schema Check
      run: |
        # 校验 Room schema JSON 与代码一致且已提交：
        # 跑一次 KSP 导出后用 git status 检查 core/schemas、app/schemas 无未提交变更
        ./gradlew :core:kspDebugKotlin
        git diff --exit-code -- core/schemas app/schemas
```

**规则：**
- `lintDebug` 必须通过（`abortOnError = true`）
- 所有单元测试必须通过
- Room 迁移测试使用 **Robolectric** JVM 运行（无 KVM 环境不依赖模拟器）
- detekt 使用 baseline 冻结既有债务，新代码必须通过

### 10.4 Build Job 规则

```yaml
build:
  needs: quality          # quality 通过后才执行
  steps:
    - name: Build Debug APK
      run: ./gradlew assembleDebug

    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: PalmNote-debug
        path: app/build/outputs/apk/debug/*.apk
```

**规则：**
- Build Job 依赖 Quality Job，质量检查不过则不构建
- 每个 Job 只调用一次 Gradle（利用 build cache 跨 Job 复用）
- 构建产物（APK）上传为 Artifact

### 10.5 Tag 触发的 Release 构建

```yaml
on:
  push:
    tags:
      - 'v*'

release:
  steps:
    - name: Build Release APK
      run: ./gradlew assembleRelease

    - name: Create GitHub Release
      uses: softprops/action-gh-release@v2
      with:
        files: app/build/outputs/apk/release/*.apk
        generate_release_notes: true
```

### 10.6 CI 环境要求

```yaml
runs-on: ubuntu-latest

env:
  JAVA_VERSION: 17
  ANDROID_COMPILE_SDK: 36

# Gradle cache
- uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: gradle-${{ hashFiles('**/*.gradle.kts') }}
```

### 10.7 本地 CI 验证

提交 PR 前，本地运行等效检查：

```bash
# 完整 CI 等效
./gradlew detekt lintDebug testDebugUnitTest

# 单独运行迁移测试
./gradlew testDebugUnitTest --tests "*.Migration*"
```

---

## 11. 代码风格

### 11.1 工具链

| 工具 | 用途 | 配置文件 |
|------|------|----------|
| detekt | Kotlin 静态分析 | `config/detekt/detekt.yml` |
| .editorconfig | 编辑器格式统一 | `.editorconfig` |

### 11.2 detekt 规则

```yaml
# config/detekt/detekt.yml

# 构建于默认规则之上
buildUponDefaultConfig: true

# baseline 冻结既有代码债务
# 新代码必须通过所有规则
baseline: config/detekt/baseline.xml

# 关键规则定制（与 config/detekt/detekt.yml 实际配置保持一致）
complexity:
  ComplexMethod:
    threshold: 15          # 允许较复杂的方法（UI Screen）
  TooManyFunctions:
    thresholdInObjects: 11 # objects 内函数数上限

style:
  MaxLineLength:
    maxLineLength: 140     # 行宽限制（Compose 长参数链按 140 写）
  MagicNumber:
    active: false          # 关闭魔法数字检查（Compose 常用）
```

**规则：**
- 新代码必须通过 detekt 全部规则
- 既有代码债务通过 baseline 冻结，不阻塞 CI
- baseline 定期重新生成（清理已修复的问题）；**大重构/大批量换行后必须重新生成**，
  否则行号漂移会让已冻结问题重新暴露（项目实际踩过：小组件批次后 36 个遗留问题复活）
- 生效前提：当前遗留债务需先做一次性清理，保证 `./gradlew detekt` 在 HEAD 上是绿的

### 11.3 代码格式

```kotlin
// ✅ 正确
class VaultRepository(
    private val dao: VaultDao,
    private val crypto: VaultCrypto,
) {
    suspend fun getEntry(id: Long): VaultEntry? {
        return dao.getById(id)?.toDomain()
    }
}

// ❌ 错误：参数过长不换行
class VaultRepository(private val dao: VaultDao, private val crypto: VaultCrypto) {
    suspend fun getEntry(id: Long): VaultEntry? = dao.getById(id)?.toDomain()
}
```

**格式规则：**
- 行宽 140 字符
- 使用 4 空格缩进（不用 Tab）
- 函数体超过 30 行考虑拆分
- 单行函数体仅用于简单 getter/转换
- 无状态 UI 组件（卡片、列表项等叶子组件）应有 `@Preview`；带 ViewModel/Hilt 的 Screen 可免

### 11.4 命名规范

```kotlin
// 类名：PascalCase
class VaultEntry
class BillRepository

// 函数/变量：camelCase
fun getEntryById(id: Long): VaultEntry?
val totalAmount: Long = 0L

// 常量：UPPER_SNAKE_CASE
companion object {
    const val MAX_RETRY_COUNT = 5
    const val LOCKOUT_DURATION_MS = 30_000L
}

// Composable 函数：PascalCase
@Composable
fun VaultEntryCard(entry: VaultEntry) { ... }

// 测试函数：反引号描述
@Test
fun `vault crypto - encrypt then decrypt - returns original`() { ... }
```

### 11.5 包结构规范

```
com.palmnote/
├── data/           # 数据层：DAO、Entity、DataStore、Repository 实现
├── domain/         # 领域层：Model、Repository 接口、Service、Utils
├── di/             # 依赖注入：Hilt Module、Qualifier
└── ui/             # UI 层：Components、Theme、Screen、ViewModel
```

**规则：**
- `domain` 不依赖 `data` 或 `ui`
- `ui` 通过 `domain` 接口访问数据，不直接引用 `data`
- `data` 实现 `domain` 中定义的 Repository 接口

### 11.6 Kotlin 最佳实践

```kotlin
// ✅ 使用 data class 代替 Java Bean
data class Money(val fen: Long) {
    fun toYuanString(): String = "%.2f".format(fen / 100.0)
}

// ✅ 使用 sealed class 表示有限状态
sealed class AssetStatus {
    data object Holding : AssetStatus()
    data object Idle : AssetStatus()
    data object Sold : AssetStatus()
    data object Lost : AssetStatus()
    data object Retired : AssetStatus()
}

// ✅ 使用扩展函数简化常见操作
fun Long.toYuanString(): String = "%.2f".format(this / 100.0)

// ✅ 使用 @Immutable 标注不可变 Compose 参数
@Immutable
data class BillSummary(
    val totalIncome: Long,
    val totalExpense: Long,
)

// ❌ 避免：全局 Context 反模式
object AppContextHolder {
    lateinit var context: Context  // 不要这样做
}
```

---

## 12. 数据库迁移流程

### 12.1 核心原则

> **每个 Migration 都必须有测试，没有例外。**

### 12.2 迁移决策树

```
你要改数据库？
│
├── 加新表 → MINOR 版本
│   └── 写 Migration + 测试
│
├── 加新列（有默认值） → MINOR 版本
│   └── 写 Migration + 测试
│
├── 改列类型/重命名 → MAJOR 版本
│   └── 写 Migration（重建表 + 数据搬运） + 测试
│
├── 删列/删表 → MAJOR 版本
│   └── 写 Migration + 测试
│
└── 改数据格式（如 Double→Long） → MAJOR 版本
    └── 写 Migration（数据转换） + 测试
```

### 12.3 迁移开发步骤

#### Step 1: 修改 Entity

```kotlin
// core/src/main/java/.../db/entity/BillEntity.kt
@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Long,        // 改前: Double
    // ...
)
```

#### Step 2: 导出新 Schema

```bash
# 运行 KSP，自动生成新 schema 到 core/schemas/
./gradlew :core:kspDebugKotlin

# 验证新 schema 文件已生成
ls core/schemas/com.palmnote.AppDatabase/
```

#### Step 3: 编写 Migration

```kotlin
// core/src/main/java/.../db/migration/Migration3To4.kt
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. 创建新表
        db.execSQL("""
            CREATE TABLE bills_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                amount INTEGER NOT NULL DEFAULT 0,
                ...
            )
        """)

        // 2. 搬运数据（Double 元 → Long 分）
        db.execSQL("""
            INSERT INTO bills_new (id, amount, ...)
            SELECT id, CAST(amount * 100 AS INTEGER), ...
            FROM bills
        """)

        // 3. 删除旧表
        db.execSQL("DROP TABLE bills")

        // 4. 重命名新表
        db.execSQL("ALTER TABLE bills_new RENAME TO bills")

        // 5. 重建索引
        db.execSQL("CREATE INDEX index_bills_book_id ON bills(book_id)")
    }
}
```

#### Step 4: 注册 Migration

```kotlin
// core/src/main/java/.../db/AppDatabase.kt
Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
    .addMigrations(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,  // ← 新增
    )
    .build()
```

#### Step 5: 编写迁移测试

```kotlin
// app/src/test/java/.../Migration3To4Test.kt
@RunWith(RobolectricTestRunner::class)
class Migration3To4Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(),
        // 从 assets 读取 schema
    )

    @Test
    fun migrate3To4_amountConvertedToFen() {
        // 1. 创建 v3 数据库
        var db = helper.createDatabase(DB_NAME, 3).apply {
            execSQL("INSERT INTO bills (amount, ...) VALUES (12.50, ...)")
            close()
        }

        // 2. 执行迁移
        db = helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4)

        // 3. 验证数据
        val cursor = db.query("SELECT amount FROM bills")
        cursor.moveToFirst()
        assertEquals(1250L, cursor.getLong(0))  // 12.50 元 → 1250 分
    }

    @Test
    fun migrate3To4_schemaIsValid() {
        helper.createDatabase(DB_NAME, 3).close()
        val db = helper.runMigrationsAndValidate(
            DB_NAME, 4, true, MIGRATION_3_4
        )
        // runMigrationsAndValidate 自动校验 schema 一致性
        db.close()
    }
}
```

#### Step 6: 全链路迁移测试

```kotlin
@Test
fun fullMigration_1to5_succeeds() {
    var db = helper.createDatabase(DB_NAME, 1)
    // 插入 v1 数据
    db.close()

    db = helper.runMigrationsAndValidate(
        DB_NAME, 5, true,
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5
    )
    // 验证最终数据
    db.close()
}
```

### 12.4 迁移检查清单

```markdown
## 数据库迁移 Checklist

### 开发
- [ ] Entity 修改完成
- [ ] 新 schema 已导出（./gradlew :core:kspDebugKotlin）
- [ ] Migration 代码编写完成
- [ ] Migration 已注册到 Database Builder

### 测试
- [ ] 单步迁移测试通过（vN → vN+1）
- [ ] 全链路迁移测试通过（v1 → vLatest）
- [ ] 数据完整性验证（行数、字段值）
- [ ] Schema 一致性验证（MigrationTestHelper 自动校验）

### 发布
- [ ] CHANGELOG 记录数据库变更
- [ ] 版本号符合规则（破坏性迁移 = MAJOR）
- [ ] 实机从上一版本升级测试
```

### 12.5 常见陷阱

| 陷阱 | 后果 | 预防 |
|------|------|------|
| 索引名不一致（`idx_bills_*` vs `index_bills_*`） | 老用户升级必崩 | Migration 中显式指定索引名 |
| 新列缺少 DEFAULT | 迁移失败 | 每个新列都加 DEFAULT |
| 外键遗漏 | 数据完整性问题 | 对比 schema 逐列/逐外键校验 |
| 时区相关字段用 UTC 存储 | 凌晨账错位 | 用完整时间戳 + 应用层时区转换 |
| 测试只测最新版本 | 老用户迁移路径未覆盖 | 必须测 v1→vLatest 全链路 |

---

## 13. Git Hooks

### 13.1 commit-msg Hook

校验提交消息是否符合 Conventional Commits 格式。

创建 `.githooks/commit-msg`：

```bash
#!/bin/sh
# commit-msg hook: 校验 Conventional Commits 格式

commit_msg=$(cat "$1")

# 正则：type(scope): subject
# type 必须是预定义类型
# scope 可选
# subject 中文或英文均可，不超过 72 字符，不以句号结尾
pattern="^(feat|fix|refactor|docs|chore|test|perf|style|ci|revert)(\([a-z0-9-]+\))?: .{1,71}$"

# 跳过 Merge commit 和 Revert commit
if echo "$commit_msg" | grep -qE "^(Merge|Revert)"; then
    exit 0
fi

# 校验第一行
first_line=$(echo "$commit_msg" | head -n1)
if ! echo "$first_line" | grep -qE "$pattern"; then
    echo "❌ Commit message 格式错误:"
    echo "   $first_line"
    echo ""
    echo "正确格式: type(scope): subject"
    echo "  type: feat|fix|refactor|docs|chore|test|perf|style|ci|revert"
    echo "  scope: 可选，小写字母和连字符"
    echo "  subject: 中文或英文，不超过 72 字符，无句号"
    echo ""
    echo "示例:"
    echo "  feat(vault): 新增生物识别解锁"
    echo "  fix(backup): null check on getExternalFilesDir"
    exit 1
fi

echo "✅ Commit message 格式正确"
exit 0
```

### 13.2 pre-push Hook

推送前自动运行 lint 和测试：

```bash
#!/bin/sh
# pre-push hook: 推送前运行质量检查

echo "🔍 Running lint..."
if ! ./gradlew lintDebug --quiet; then
    echo "❌ Lint 失败，阻止推送"
    exit 1
fi

echo "🧪 Running unit tests..."
if ! ./gradlew testDebugUnitTest --quiet; then
    echo "❌ 测试失败，阻止推送"
    exit 1
fi

echo "✅ 质量检查通过"
exit 0
```

### 13.3 安装 Hooks

```bash
# 方式一：项目内配置（推荐）
git config core.hooksPath .githooks
chmod +x .githooks/*

# 方式二：全局配置
# cp .githooks/* ~/.git/hooks/
```

### 13.4 Hooks 文件结构

```
PalmNote/
├── .githooks/
│   ├── commit-msg      # 提交消息校验
│   └── pre-push        # 推送前质量检查
└── ...
```

---

## 14. 多语言工作流

### 14.1 支持的语言

| 语言 | 资源目录 | 状态 |
|------|----------|------|
| 中文（简体） | `res/values/` | 默认 |
| English | `res/values-en/` | 完整 |

### 14.2 新增字符串流程

#### Step 1: 添加默认语言（中文）

```xml
<!-- core/src/main/res/values/strings.xml -->
<string name="vault_entry_phone">手机号</string>
```

#### Step 2: 添加英文翻译

```xml
<!-- core/src/main/res/values-en/strings.xml -->
<string name="vault_entry_phone">Phone</string>
```

#### Step 3: 代码中使用

```kotlin
// Composable 中
Text(text = stringResource(R.string.vault_entry_phone))

// ViewModel/非 Composable 中
context.getString(R.string.vault_entry_phone)
```

### 14.3 规则

1. **禁止硬编码中文**：所有用户可见的文字必须放在 `strings.xml`
2. **key 命名**：小写下划线，按模块前缀
   ```xml
   vault_entry_phone        # 密码本-条目-手机号
   bill_calendar_title      # 账单-日历-标题
   asset_status_idle        # 物品-状态-闲置
   ```
3. **参数化字符串**：使用 `%1$s`、`%1$d` 等占位符
   ```xml
   <string name="bill_total_format">总计: ¥%1$s</string>
   ```
4. **不要拆分句子**：中英文语序不同，不要拼接字符串
   ```xml
   <!-- ❌ 错误 -->
   <string name="prefix">共</string>
   <string name="suffix">条记录</string>

   <!-- ✅ 正确 -->
   <string name="vault_entry_count">共 %1$d 条记录</string>
   ```
5. **两个模块共用的 key**：在 core 和 app 各放一份（AGP 各模块 R 类独立）
6. **新增字符串必须同步中英文**：缺少翻译的 key 会导致 lint 警告

### 14.4 翻译质量检查

```bash
# 检查缺失翻译
./gradlew lintDebug
# 查看报告：app/build/reports/lint-results-debug.html
# 关注 MissingTranslation 警告
```

### 14.5 特殊处理

```xml
<!-- 不需要翻译的字符串（如应用名） -->
<string name="app_name" translatable="false">PalmNote</string>

<!-- 格式化字符串注意参数类型一致性 -->
<!-- 中文版 -->
<string name="date_format_weekday_full">%1$s %2$s</string>
<!-- 英文版：参数类型必须一致 -->
<string name="date_format_weekday_full">%1$s, %2$s</string>
```

---

## 15. APK 签名

### 15.1 签名配置

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            val localProps = loadLocalProperties()
            storeFile = file("../${localProps["RELEASE_STORE_FILE"]}")
            storePassword = localProps["RELEASE_STORE_PASSWORD"]
            keyAlias = localProps["RELEASE_KEY_ALIAS"]
            keyPassword = localProps["RELEASE_KEY_PASSWORD"]
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### 15.2 local.properties 配置

```properties
# local.properties（不提交到 Git）
RELEASE_STORE_FILE=release.jks
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=palmnote
RELEASE_KEY_PASSWORD=your_key_password
```

### 15.3 密钥管理规则

1. **密钥文件不提交到 Git**：`*.jks`、`*.keystore` 已在 `.gitignore`
2. **local.properties 不提交**：已在 `.gitignore`
3. **密钥备份**：密钥文件 + 密码保存在安全的密码管理器中
4. **密钥丢失 = 无法更新**：Google Play 不允许更换签名密钥
5. **CI 签名**：通过 GitHub Secrets 注入

### 15.4 CI 签名配置

```yaml
# .github/workflows/release.yml
env:
  RELEASE_STORE_FILE: ${{ secrets.RELEASE_STORE_FILE }}
  RELEASE_STORE_PASSWORD: ${{ secrets.RELEASE_STORE_PASSWORD }}
  RELEASE_KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
  RELEASE_KEY_PASSWORD: ${{ secrets.RELEASE_KEY_PASSWORD }}

steps:
  - name: Decode Keystore
    run: |
      echo "$RELEASE_STORE_FILE" | base64 -d > release.jks

  - name: Build Release
    run: |
      echo "RELEASE_STORE_FILE=release.jks" >> local.properties
      echo "RELEASE_STORE_PASSWORD=$RELEASE_STORE_PASSWORD" >> local.properties
      echo "RELEASE_KEY_ALIAS=$RELEASE_KEY_ALIAS" >> local.properties
      echo "RELEASE_KEY_PASSWORD=$RELEASE_KEY_PASSWORD" >> local.properties
      ./gradlew assembleRelease
```

### 15.5 密钥生成

```bash
# 生成新的签名密钥
keytool -genkeypair \
  -v \
  -keystore release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias palmnote

# 查看密钥信息
keytool -list -v -keystore release.jks
```

### 15.6 无密钥降级

CI 环境无密钥时自动降级为 unsigned 构建（冒烟测试）：

```kotlin
// app/build.gradle.kts
val releaseStoreFile = file("../${localProps["RELEASE_STORE_FILE"] ?: "release.jks"}")

if (releaseStoreFile.exists()) {
    signingConfig = signingConfigs.getByName("release")
}
// 不存在时 signingConfig 保持 null，构建 unsigned APK
```

---

## 16. 依赖管理

### 16.1 版本目录

使用 Gradle Version Catalog 统一管理依赖版本：

```toml
# gradle/libs.versions.toml
[versions]
kotlin = "2.2.20"
room = "2.7.2"
compose-bom = "2024.12.01"
hilt = "2.51.1"

[libraries]
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }

[plugins]
android-application = { id = "com.android.application", version = "8.13.0" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

### 16.2 依赖使用规范

```kotlin
// ✅ 正确：使用 version catalog
implementation(libs.room.runtime)
implementation(libs.room.ktx)
ksp(libs.room.compiler)

// ❌ 错误：硬编码版本号
implementation("androidx.room:room-runtime:2.7.2")
```

### 16.3 依赖更新策略

| 更新类型 | 频率 | 操作 |
|----------|------|------|
| Patch (bug fix) | 每月检查 | 直接更新 |
| Minor (新功能) | 每季度检查 | 更新 + 测试 |
| Major (破坏性) | 按需 | 评估影响 + 独立分支 + 充分测试 |

### 16.4 依赖更新流程

```bash
# 1. 检查过期依赖
./gradlew dependencyUpdates

# 2. 在独立分支更新
./gradlew versionCatalogUpdate

# 3. 全量测试
./gradlew testDebugUnitTest

# 4. 实机验证
./gradlew assembleDebug
# 安装到设备，核心功能走一遍

# 5. 合并
# PR → merge to main
```

### 16.5 依赖审查清单

更新依赖前检查：

```markdown
- [ ] Release Notes 是否有 breaking change
- [ ] 是否影响 minSdk 兼容性
- [ ] 是否增加 APK 体积
- [ ] 是否有已知安全漏洞
- [ ] 是否与现有依赖冲突
```

### 16.6 Hilt 依赖特殊处理

```kotlin
// Hilt javapoet 版本冲突修复
// 根 build.gradle.kts
buildscript {
    dependencies {
        classpath("com.squareup:javapoet:1.13.0")
    }
}
```

### 16.7 依赖锁文件

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}
```

**规则：**
- 所有仓库配置在 `settings.gradle.kts` 统一管理
- 禁止在子模块 `build.gradle.kts` 中声明仓库
- 使用阿里云镜像加速国内构建

---

## 17. 安全审查流程

PalmNote 处理敏感数据（密码、财务信息），安全审查是强制要求。

### 17.1 安全审查触发条件

以下改动**必须**进行安全审查：

| 改动类型 | 审查重点 |
|----------|----------|
| 加密相关 | 密钥管理、加密模式、IV/nonce 生成 |
| 数据库变更 | 是否有明文敏感数据泄露风险 |
| 备份/恢复 | 备份文件是否加密、恢复是否校验完整性 |
| 剪贴板操作 | 自动清除机制、敏感数据是否残留 |
| 生物识别 | Keystore 密钥是否不可导出、降级策略 |
| 网络请求 | PalmNote 理论上无网络请求，审查是否有意外外连 |
| 日志输出 | 是否有敏感数据写入日志 |
| 导出功能 | 导出文件是否加密、是否包含敏感字段 |

### 17.2 安全审查 Checklist

```markdown
## 安全审查 Checklist

### 数据存储
- [ ] 密码/密钥不以明文存储
- [ ] 数据库使用 SQLCipher 加密
- [ ] SharedPreferences 中的敏感数据已加密
- [ ] 日志不包含敏感数据（Log.d/Log.e）

### 加密
- [ ] 使用 AES-256-GCM（不是 AES-CBC）
- [ ] IV/nonce 每次加密唯一生成
- [ ] 密钥通过 Keystore 保护
- [ ] PBKDF2 迭代次数 ≥ 25000

### 用户界面
- [ ] FLAG_SECURE 在敏感页面启用
- [ ] 密码输入框默认遮罩
- [ ] 剪贴板 30 秒自动清除
- [ ] 密码遮罩查看（👁 切换）

### 备份
- [ ] 备份文件 AES-GCM 加密
- [ ] 备份前 WAL checkpoint
- [ ] 恢复时校验完整性
- [ ] allowBackup = false

### 生物识别
- [ ] Keystore 密钥不可导出
- [ ] 降级到 PIN 时密钥正确包裹
- [ ] 失败次数限制（5 次锁定 30 秒）
- [ ] 锁定状态持久化（防进程被杀绕过）

### 代码审查
- [ ] 无硬编码密钥/密码
- [ ] 无 TODO 标注的安全问题
- [ ] 无调试代码残留（BuildConfig.DEBUG 守卫）
```

### 17.3 安全漏洞报告

见 `SECURITY.md`。发现安全漏洞时：

1. **不要公开 Issue**：通过安全渠道报告
2. **提供复现步骤**：越详细越好
3. **等待修复**：修复后再公开披露

### 17.4 安全测试

```kotlin
// 加密往返测试
@Test
fun vaultCrypto_roundTrip_plaintextMatches() {
    val plaintext = "my_password_123"
    val encrypted = VaultCrypto.encrypt(plaintext, key)
    val decrypted = VaultCrypto.decrypt(encrypted, key)
    assertEquals(plaintext, decrypted)
}

// 篡改检测
@Test
fun vaultCrypto_tamperedCiphertext_throwsException() {
    val encrypted = VaultCrypto.encrypt("test", key)
    val tampered = encrypted.copy(cipherText = "tampered".toByteArray())
    assertThrows<CryptoException> {
        VaultCrypto.decrypt(tampered, key)
    }
}

// 密钥派生一致性
@Test
fun vaultCrypto_samePinDerivesSameKey() {
    val key1 = VaultCrypto.deriveKey("1234", salt)
    val key2 = VaultCrypto.deriveKey("1234", salt)
    assertArrayEquals(key1.encoded, key2.encoded)
}
```

---

## 18. 性能预算

### 18.1 APK 体积

| 指标 | 当前值 | 预算 | 说明 |
|------|--------|------|------|
| Release APK | ~42.6 MB | ≤ 50 MB | PaddleOCR + ONNX Runtime 主要贡献 |
| Debug APK | ~80 MB | ≤ 100 MB | 含 x86_64 + 完整调试信息 |

**体积优化手段：**
- Release 仅 `arm64-v8a`（省去 ~30MB 32 位库）
- `isMinifyEnabled = true` + `isShrinkResources = true`
- Baseline Profile 预编译热路径
- 考虑 App Bundle (AAB) 发布（Google Play 自动按设备分发）

### 18.2 启动时间

| 指标 | 预算 | 测量方式 |
|------|------|----------|
| 冷启动 | ≤ 1.5s | `adb shell am start -W` |
| 热启动 | ≤ 0.5s | `adb shell am start -W` |

**优化手段：**
- Baseline Profile 覆盖首页热路径
- DataStore 批量读取（避免多次 IO）
- DataCache 预加载
- 延迟初始化非关键模块

### 18.3 内存

| 指标 | 预算 | 说明 |
|------|------|------|
| 首页内存占用 | ≤ 80 MB | 无大量图片 |
| 图片列表页 | ≤ 120 MB | Coil 自动管理缓存 |

**检测方式：**
```bash
adb shell dumpsys meminfo com.palmnote
```

### 18.4 数据库

| 指标 | 预算 | 说明 |
|------|------|------|
| 单次查询 | ≤ 16ms | 不阻塞主线程 |
| 迁移时间（1000 条记录） | ≤ 1s | 用户无感知 |

**规则：**
- 所有数据库操作必须在 IO 线程
- 列表使用 Paging 分页加载
- 大量数据操作使用 `@Transaction`

### 18.5 性能回归检查

每次发布前验证：

```bash
# APK 体积
ls -lh app/build/outputs/apk/release/PalmNote-*.apk

# 启动时间（入口是 MainActivity，不是 Application 类）
adb shell am start -W -n com.palmnote/.MainActivity

# 内存
adb shell dumpsys meminfo com.palmnote
```

---

## 19. 开发环境

### 19.1 环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| Android Studio | 最新稳定版 | 支持 AGP 8.13 |
| JDK | 17 | 编译目标 |
| Gradle | 通过 wrapper | 不需要全局安装 |
| Android SDK | compileSdk 36 | 通过 SDK Manager 安装 |
| Kotlin | 2.2.20 | 通过 Gradle 管理 |

### 19.2 快速开始

```bash
# 1. 克隆项目
git clone https://github.com/PickGear/PalmNote.git
cd PalmNote

# 2. 配置 local.properties（签名密钥，可选）
cp local.properties.example local.properties
# 编辑 local.properties 填入密钥路径和密码

# 3. 构建 Debug APK
./gradlew assembleDebug

# 4. 安装到设备
adb install app/build/outputs/apk/debug/PalmNote-*.apk
```

### 19.3 local.properties 配置

```properties
# local.properties
# SDK 路径（Android Studio 自动设置）
sdk.dir=/path/to/Android/Sdk

# 签名密钥（可选，无密钥时构建 unsigned APK）
RELEASE_STORE_FILE=release.jks
RELEASE_STORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=palmnote
RELEASE_KEY_PASSWORD=your_password
```

### 19.4 项目结构

```
PalmNote/
├── app/                    # 应用模块
│   ├── build.gradle.kts
│   ├── src/
│   │   ├── main/           # 主代码
│   │   ├── test/           # 单元测试
│   │   └── androidTest/    # 仪器测试
│   └── schemas/            # VaultDatabase Room schema
├── core/                   # 核心库模块
│   ├── build.gradle.kts
│   ├── src/
│   └── schemas/            # AppDatabase Room schema
├── ppocr-sdk/              # PaddleOCR 原生 SDK
├── gradle/
│   ├── libs.versions.toml  # 版本目录
│   └── wrapper/            # Gradle wrapper
├── config/detekt/          # detekt 配置
├── docs/                   # 文档
├── screenshots/            # 截图
├── .github/                # GitHub Actions + 模板
└── build.gradle.kts        # 根构建脚本
```

### 19.5 常用命令

```bash
# 构建
./gradlew assembleDebug              # Debug APK
./gradlew assembleRelease            # Release APK

# 测试
./gradlew testDebugUnitTest          # 全部单元测试
./gradlew testDebugUnitTest --tests "*.Migration*"  # 仅迁移测试

# 代码质量
./gradlew detekt                     # 静态分析
./gradlew lintDebug                  # Android Lint

# Room schema
./gradlew :core:kspDebugKotlin       # 导出新 schema

# 清理
./gradlew clean                      # 清理构建产物
```

### 19.6 常见问题

| 问题 | 解决方案 |
|------|----------|
| Gradle sync 失败 | 检查 JDK 版本是否为 17 |
| 构建报错 `javapoet` | 根 build.gradle.kts 已强制 1.13.0，sync 后重试 |
| 本地模拟器无法安装 Release | Release 仅 arm64-v8a，模拟器需 x86_64，用 Debug |
| Room schema 校验失败 | 运行 `./gradlew :core:kspDebugKotlin` 导出最新 schema |
| detekt 报错 | 运行 `./gradlew detektBaseline` 更新 baseline |

---

## 20. 应用商店发布

### 20.1 Google Play 发布清单

```markdown
## Google Play 发布前 Checklist

### 技术要求
- [ ] targetSdk 升至 35+（2026 年 8 月起强制）
      ⚠️ 已知技术债：当前刻意停在 34（侧载体验 + 禁用 Android 15 predictive back，
      见 app/build.gradle.kts 注释），上架 Play 前需升级并适配 predictive back
- [ ] 适配 Android 15 predictive back
- [ ] 使用 App Bundle (AAB) 而非 APK
- [ ] 64 位 native 库已包含（arm64-v8a）

### 内容要求
- [ ] 应用图标（512x512 PNG）
- [ ] 功能图片（1024x500）
- [ ] 截图（至少 2 张，手机 + 平板）
- [ ] 简短描述（80 字符内）
- [ ] 完整描述（4000 字符内）
- [ ] 隐私政策 URL
- [ ] 数据安全声明（Data Safety）

### 版本管理
- [ ] versionCode 递增
- [ ] versionName 与 Git tag 一致
- [ ] ProGuard/R8 规则验证
```

### 20.2 App Bundle 构建

```bash
# 构建 AAB
./gradlew bundleRelease

# 产物路径
# app/build/outputs/bundle/release/app-release.aab
```

### 20.3 ProGuard/R8 规则

```proguard
# proguard-rules.pro

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# SQLCipher
-keep class net.zetetic.** { *; }

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
```

### 20.4 数据安全声明

Google Play 要求声明数据收集行为。PalmNote 作为纯本地应用：

```yaml
数据收集: 无
数据共享: 无
数据加密: 是（SQLCipher + AES-GCM）
数据可以删除: 是（卸载即清除）
```

### 20.5 版本更新流程

```bash
# 1. 升级版本号
# app/build.gradle.kts
defaultConfig {
    versionCode = previousVersionCode + 1
    versionName = "X.Y.Z"
}

# 2. 构建 AAB
./gradlew bundleRelease

# 3. 上传到 Google Play Console
# 4. 填写 Release Notes
# 5. 提交审核
```

### 20.6 国内应用商店

如需上架国内商店（华为、小米、OPPO 等）：

| 商店 | 特殊要求 |
|------|----------|
| 华为应用市场 | 需要 AppGallery Connect 账号 |
| 小米应用商店 | 需要软著证书 |
| OPPO 软件商店 | 需要企业认证 |
| 通用 | 所有商店都需要 ICP 备案（如有联网功能） |

**注意：** PalmNote 纯本地运行，无需 ICP 备案，但部分商店可能仍要求提供。

---

## 21. 崩溃监控

### 21.1 方案选择

| 方案 | 优点 | 缺点 | 推荐场景 |
|------|------|------|----------|
| Firebase Crashlytics | 免费、主流、自动上报 | 需要 Google Play Services | 上架 Google Play |
| Sentry | 开源、可自托管、隐私友好 | 免费额度有限 | 纯本地/隐私优先 |
| ACRA | 开源、完全自控 | 需要自建后端 | 不想依赖第三方 |
| 仅靠 GitHub Issues | 零成本 | 被动、信息不全 | 早期开发阶段 |

**PalmNote 建议：** 作为纯本地应用，崩溃上报与"零联网"产品哲学冲突——接入任何上报 SDK 都意味着
新增网络权限。**默认不得启用**；如确需接入：必须用户显式 opt-in（首次引导时单独开关）、
优先选择可自托管的 Sentry 或本地崩溃日志文件 + 用户手动导出。未接入前靠 GitHub Issues 被动收集。

### 21.2 崩溃上报规则

```kotlin
// 初始化（Application.onCreate）
class PalmNoteApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Debug 模式：崩溃时弹窗 + 详细堆栈
        // Release 模式：自动上报到后端
        if (BuildConfig.DEBUG) {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Log.e("FATAL", "Uncaught exception on ${thread.name}", throwable)
                // Debug 可以弹窗或写文件
            }
        } else {
            // Release：接入 Crashlytics/Sentry/ACRA
            initCrashReporting()
        }
    }
}
```

### 21.3 崩溃信息脱敏

上报崩溃时，**必须脱敏**：

```kotlin
// ✅ 正确：只上报必要信息
crashReporter.setExtra("screen", "vault_list")
crashReporter.setExtra("db_version", "7")
crashReporter.setExtra("app_version", "1.3.0")

// ❌ 错误：上报敏感数据
crashReporter.setExtra("user_pin", pin)           // 不要
crashReporter.setExtra("vault_entries", entries)   // 不要
crashReporter.setExtra("sql_query", query)          // 可能含用户数据
```

**脱敏规则：**
- ✅ 可以上报：屏幕名、版本号、设备型号、OS 版本、堆栈信息
- ❌ 禁止上报：PIN、密码、账单金额、密码本条目、数据库内容

### 21.4 ANR 监控

ANR（Application Not Responding）是 Android 特有的问题：

```kotlin
// 避免主线程阻塞的操作
// ❌ 主线程执行 PBKDF2
val key = deriveKey(pin, salt)  // 25k 迭代，耗时 200ms+

// ✅ 移到 IO 线程
withContext(Dispatchers.IO) {
    val key = deriveKey(pin, salt)
}

// ❌ 主线程执行数据库查询
val entries = vaultDao.getAll()  // 可能很慢

// ✅ 使用 suspend 函数
suspend fun getAllEntries() = vaultDao.getAll()  // Room 自动切 IO
```

### 21.5 版本关联

每次发布时，确保崩溃报告能关联到具体版本：

```kotlin
crashReporter.setRelease("PalmNote@${BuildConfig.VERSION_NAME}")
crashReporter.setDist(BuildConfig.VERSION_CODE.toString())
```

### 21.6 崩溃处理流程

```
用户上报崩溃
    │
    ├── 有堆栈信息 → 直接定位修复
    │
    └── 无堆栈信息 → 要求用户提供：
        ├── 复现步骤
        ├── 设备型号 + Android 版本
        ├── PalmNote 版本
        └── 日志（adb logcat）

修复后：
    ├── 写测试用例覆盖
    ├── 更新 CHANGELOG
    └── 发布 patch 版本
```

---

## 22. 日志规范

### 22.1 核心原则

> **日志里永远不要出现用户数据。没有例外。**

PalmNote 处理密码、财务信息、密码本条目，日志泄露 = 安全事故。

### 22.2 日志级别

| 级别 | 用途 | Release 是否输出 |
|------|------|------------------|
| `Log.v` (Verbose) | 极详细调试信息 | ❌ 禁止 |
| `Log.d` (Debug) | 开发调试 | ❌ 禁止 |
| `Log.i` (Info) | 关键流程节点 | ⚠️ 仅限脱敏后的摘要 |
| `Log.w` (Warning) | 可恢复的异常 | ✅ 需脱敏 |
| `Log.e` (Error) | 不可恢复的异常 | ✅ 需脱敏 |

### 22.3 Release 日志剥离

```kotlin
// 方式一：BuildConfig.DEBUG 守卫（推荐）
if (BuildConfig.DEBUG) {
    Log.d("VaultRepo", "Loaded ${entries.size} entries")
}

// 方式二：封装 Log 工具类
object PalmLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        // Warning 及以上在 Release 也输出（但必须脱敏）
        Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
```

### 22.4 日志脱敏规则

```kotlin
// ✅ 正确：只记录操作和状态
PalmLog.d("VaultRepo", "Entry loaded: id=$id")
PalmLog.d("BillRepo", "Inserted bill: bookId=$bookId, category=$category")
PalmLog.w("Backup", "WAL checkpoint busy, retrying")
PalmLog.e("Crypto", "Decryption failed for entry", exception)

// ❌ 错误：记录敏感数据
PalmLog.d("Vault", "Password: $password")           // 绝对禁止
PalmLog.d("Vault", "Entry: $entry")                  // entry 含密码
PalmLog.d("Bill", "Amount: ${bill.amount}")            // 金额也是敏感的
PalmLog.d("Lock", "PIN hash: $hash")                   // hash 也是敏感的
PalmLog.d("SQL", "Query: $query")                      // query 可能含用户数据
PalmLog.d("Backup", "Key: ${key.encoded}")             // 密钥绝对禁止
```

**脱敏检查表：**

| 数据类型 | 能否写入日志 |
|----------|-------------|
| 用户 PIN / 密码 | ❌ 绝对禁止 |
| 密码本条目内容 | ❌ 绝对禁止 |
| 加密密钥 / IV / salt | ❌ 绝对禁止 |
| 账单金额 | ❌ 禁止（可记录"有账单"，不记录金额） |
| 数据库查询语句 | ❌ 禁止（可能含用户数据） |
| 对象 ID | ✅ 可以 |
| 分类名称 | ✅ 可以 |
| 操作结果（成功/失败） | ✅ 可以 |
| 堆栈信息 | ✅ 可以（但注意 catch 块中不要打印敏感变量） |
| 设备型号 / OS 版本 | ✅ 可以 |
| App 版本 | ✅ 可以 |

### 22.5 日志 TAG 规范

```kotlin
// TAG 格式：类名或模块名
class VaultRepository {
    companion object {
        private const val TAG = "VaultRepo"
    }

    suspend fun getEntry(id: Long): VaultEntry? {
        PalmLog.d(TAG, "getEntry: id=$id")
        return dao.getById(id)?.toDomain()
    }
}
```

**TAG 命名：**
- 使用简短的类名或模块名
- 不超过 20 个字符
- 不使用 `"test"`、`"debug"` 等无意义 TAG

### 22.6 ProGuard 日志剥离

Release 构建时，通过 ProGuard 自动移除 `Log.v` 和 `Log.d`：

```proguard
# proguard-rules.pro

# 移除 verbose 和 debug 日志
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
```

**注意：** 这只对直接调用 `Log.d()` 有效。如果使用封装的 `PalmLog.d()`，需要在 `PalmLog` 中用 `BuildConfig.DEBUG` 守卫。

### 22.7 异常日志

```kotlin
// ✅ 正确：捕获异常时记录上下文（脱敏）
try {
    val decrypted = VaultCrypto.decrypt(encrypted, key)
} catch (e: Exception) {
    PalmLog.e(TAG, "Decryption failed for entry id=$id", e)
    // 不要记录 encrypted 或 key
    throw VaultCryptoException("Decryption failed", e)
}

// ❌ 错误：吞掉异常
try {
    val decrypted = VaultCrypto.decrypt(encrypted, key)
} catch (e: Exception) {
    // 什么都不做，异常消失了
}

// ❌ 错误：记录敏感上下文
try {
    val decrypted = VaultCrypto.decrypt(encrypted, key)
} catch (e: Exception) {
    Log.e(TAG, "Decrypt failed: key=${key.encoded}, data=$encrypted", e)
}
```

---

## 23. 无障碍

### 23.1 为什么重要

- **Google Play 审核**：无障碍问题可能导致应用被拒
- **用户群体**：约 15% 的人有某种形式的障碍
- **法律合规**：部分国家/地区有无障碍法规
- **通用设计**：好的无障碍对所有用户都有益

### 23.2 Compose 无障碍规则

#### contentDescription

```kotlin
// ✅ 正确：所有交互元素必须有 contentDescription
IconButton(onClick = { /* ... */ }) {
    Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = stringResource(R.string.vault_lock_icon)
    )
}

// ✅ 正确：装饰性图标可以隐藏
Icon(
    imageVector = Icons.Default.Star,
    contentDescription = null  // 纯装饰，无语义
)

// ❌ 错误：交互元素没有 contentDescription
IconButton(onClick = { /* ... */ }) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = null  // 删除按钮必须有描述！
    )
}
```

#### 语义属性

```kotlin
// ✅ 正确：使用 semantics 描述状态
Box(
    modifier = Modifier.semantics {
        stateDescription = if (isLocked) {
            context.getString(R.string.state_locked)
        } else {
            context.getString(R.string.state_unlocked)
        }
    }
)

// ✅ 正确：密码遮罩的语义
Text(
    text = if (showPassword) password else "••••••••",
    modifier = Modifier.semantics {
        contentDescription = if (showPassword) {
            context.getString(R.string.vault_password_visible)
        } else {
            context.getString(R.string.vault_password_hidden)
        }
    }
)
```

#### 触摸目标大小

```kotlin
// ✅ 正确：触摸目标至少 48dp
IconButton(
    onClick = { /* ... */ },
    modifier = Modifier.size(48.dp)  // 最小触摸目标
) {
    Icon(Icons.Default.Edit, contentDescription = "编辑")
}

// ❌ 错误：触摸目标太小
IconButton(
    onClick = { /* ... */ },
    modifier = Modifier.size(24.dp)  // 太小，难以点击
) {
    Icon(Icons.Default.Edit, contentDescription = "编辑")
}
```

### 23.3 字体缩放

```kotlin
// ✅ 正确：使用 sp 单位（自动跟随系统字体大小）
Text(
    text = "金额",
    fontSize = 16.sp,
    style = MaterialTheme.typography.bodyLarge
)

// ❌ 错误：使用固定 dp 或硬编码像素
Text(
    text = "金额",
    modifier = Modifier.padding(16.dp),
    // fontSize 不要用 dp
)
```

**规则：**
- 文字大小使用 `sp`（scaled pixels），自动跟随系统字体缩放
- 布局间距使用 `dp`，不随字体缩放
- 测试时用最大字体验证 UI 不会溢出

### 23.4 颜色对比度

```kotlin
// WCAG 2.0 AA 标准：
// - 普通文字：对比度 ≥ 4.5:1
// - 大文字（≥18sp 或 14sp 粗体）：对比度 ≥ 3:1

// ✅ 正确：深色文字 + 浅色背景
Text(
    text = "¥1,234.56",
    color = Color(0xFF1B1B1F),       // 深色
    // 背景：MaterialTheme.colorScheme.surface  // 浅色
)

// ❌ 错误：浅灰色文字 + 白色背景
Text(
    text = "¥1,234.56",
    color = Color(0xFFBBBBBB),       // 太浅，对比度不足
    // 背景：白色
)
```

**检查工具：**
- Android Studio → Layout Inspector → 检查对比度
- WebAIM Contrast Checker: https://webaim.org/resources/contrastchecker/

### 23.5 无障碍测试

```kotlin
// 使用 Compose Testing 验证无障碍语义
@Test
fun vaultEntryCard_hasCorrectSemantics() {
    composeTestRule.setContent {
        VaultEntryCard(
            entry = VaultEntry(id = 1, title = "GitHub"),
            onClick = {}
        )
    }

    composeTestRule
        .onNodeWithContentDescription("GitHub")
        .assertExists()
}

// 使用 TalkBack 实机测试
// 设置 → 无障碍 → TalkBack → 开启
// 然后在 App 中滑动，检查每个元素是否能被正确朗读
```

### 23.6 无障碍自审 Checklist

```markdown
## 无障碍 Checklist

### 基础
- [ ] 所有可点击元素有 contentDescription
- [ ] 所有图标有 contentDescription（装饰性图标除外）
- [ ] 触摸目标 ≥ 48dp
- [ ] 文字使用 sp 单位
- [ ] 颜色对比度符合 WCAG AA

### 密码本模块
- [ ] 密码遮罩状态有语义描述
- [ ] 密码可见/隐藏切换有 contentDescription
- [ ] 复制成功有 Toast 或语义反馈

### 记账模块
- [ ] 金额数字有语义描述（如"收入一百二十元"）
- [ ] 图表数据有文字替代（饼图/柱状图）
- [ ] 日历导航支持 TalkBack 手势

### 通用
- [ ] TalkBack 实机测试通过
- [ ] 大字体模式下 UI 不溢出
- [ ] 无闪烁动画（光敏性癫痫风险）
```

### 23.7 常见问题

| 问题 | 修复 |
|------|------|
| 图标没有 contentDescription | 添加 `contentDescription = stringResource(R.string.xxx)` |
| 触摸目标太小 | `Modifier.size(48.dp)` 或 `Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)` |
| 对比度不足 | 使用 Material 3 的 `onSurface`、`onPrimary` 等语义颜色 |
| 动画闪烁 | 降低动画频率，避免每秒闪烁超过 3 次 |
| 图表无法朗读 | 添加 `Modifier.semantics { contentDescription = "饼图：餐饮30%，交通20%..." }` |

---

## 24. 开源发布隐私审查

> **核心原则：公开仓库 = 全世界都能看到。任何提交过的内容，即使后来删除，仍然存在于 Git 历史中。**

### 24.1 发布前扫描 Checklist

在将仓库设为公开之前，**必须**逐项检查：

```markdown
## 开源发布前安全 Checklist

### 代码中的敏感数据
- [ ] 无硬编码 API Key / Token / Secret
- [ ] 无硬编码 Firebase 配置（google-services.json）
- [ ] 无硬编码服务器 URL / IP 地址（如有自建后端）
- [ ] 无硬编码加密密钥 / IV / Salt
- [ ] 无用户真实数据（测试数据使用 mock）
- [ ] 无内部注释包含敏感信息（"这里临时写的，上线前删"）

### 配置文件
- [ ] local.properties 已在 .gitignore
- [ ] *.jks / *.keystore 已在 .gitignore
- [ ] *.hprof（内存快照）已在 .gitignore
- [ ] .idea/ 已在 .gitignore
- [ ] google-services.json 已在 .gitignore（如有）
- [ ] *.log 已在 .gitignore

### Git 历史
- [ ] 从未提交过敏感文件（用 git log --all --diff-filter=D 检查）
- [ ] 如曾提交过，已用 BFG 或 git filter-repo 清理
- [ ] 清理后已 force push 并通知所有 fork

### GitHub Actions
- [ ] Secrets 已配置在 GitHub Secrets 中（不在代码里）
- [ ] Workflow 文件无 secrets 泄露（echo $SECRET 不会打印）
- [ ] permissions 设置最小化（contents: read）
- [ ] 无第三方 Action 的 pin-by-SHA（防供应链攻击）

### 个人隐私
- [ ] Git 提交邮箱是否要公开（可用 noreply@github.com）
- [ ] 仓库描述/README 无个人手机号/地址
- [ ] 截图中无个人信息（账单金额、密码、真实姓名）

### 第三方合规
- [ ] LICENSE 文件存在且正确
- [ ] NOTICE 文件列出所有第三方依赖及许可证
- [ ] PaddleOCR / ONNX Runtime 许可证兼容 GPL-3.0
- [ ] 无侵犯版权的图片/字体/资源
```

### 24.2 PalmNote 具体检查项

以下是针对 PalmNote 仓库的具体检查和操作步骤：

#### 检查 1：截图脱敏

`screenshots/` 目录中的截图必须使用模拟数据：

```bash
# 检查截图目录
ls screenshots/zh/
ls screenshots/en/

# 需要检查的文件：
# - dashboard.jpg      → 净资产、月度收支是否为真实数据
# - bill_calendar.jpg  → 日历中是否有真实账单
# - bill_stats.jpg     → 统计报表是否有真实金额
# - asset_list.jpg     → 物品列表是否有真实物品
# - life.jpg           → 生活模块是否有真实计划
# - settings.jpg       → 设置页是否有真实头像/昵称
```

**脱敏规则：**
- 金额使用模拟数据（如 ¥0.00、¥1,234.56）
- 名称使用通用名（"早餐"、"地铁"、"工资"）
- 日期使用近期日期（不要用真实生日/纪念日）
- 头像使用默认头像或占位图
- 密码本截图如果有，条目必须是模拟数据（"GitHub"、"示例账号"）

**如果截图已包含真实数据：**
1. 用模拟数据重新截图
2. 替换 screenshots/ 目录中的文件
3. 注意：旧截图仍在 Git 历史中，需要清理历史（见 24.3）

#### 检查 2：Git 提交邮箱

```bash
# 查看仓库中所有出现过的邮箱
git log --format='%ae' | sort -u

# 如果暴露了真实邮箱，需要：
# 1. 在 GitHub Settings → Emails 中开启 "Keep my email addresses private"
# 2. 获取隐私邮箱格式：ID+username@users.noreply.github.com
# 3. 修改后续提交的邮箱：
git config user.email "你的ID+PickGear@users.noreply.github.com"

# 注意：修改已提交的邮箱需要重写 Git 历史（影响所有 commit SHA）
# 如果只是后续使用新邮箱，不需要清理历史
```

#### 检查 3：.gitignore 补充

当前 .gitignore 建议新增以下条目：

```bash
# 在 .gitignore 末尾追加

# Google Services（如果接入 Firebase）
google-services.json
app/google-services.json

# 密钥和凭据
*.pem
*.p12
*.pfx
*.key
*.crt
*.cer

# 环境变量
.env
.env.local
.env.*.local

# 编辑器
*.swp
*.swo
*~
.vscode/
*.sublime-*

# 测试覆盖率
*.exec
*.ec
coverage/

# Android Profiler
*.trace

# Crashlytics / Sentry
crashlytics-build.properties
sentry.properties

# 签名相关目录
signing/
*.gpg
```

#### 检查 4：PaddleOCR 模型文件

```bash
# 检查 ppocr-sdk 模块中的模型文件
ls ppocr-sdk/src/main/assets/

# 模型文件通常较大（>10MB），确认：
# 1. 模型许可证是 Apache 2.0（PaddleOCR 官方模型）
# 2. 没有打包非开源的自定义模型
# 3. 模型文件大小合理（没有意外的调试文件）

# 如果模型文件太大导致仓库臃肿，考虑：
# - 使用 Git LFS 管理大文件
# - 或模型按需下载（首次启动时下载）
```

#### 检查 5：NOTICE 文件完整性

```bash
# 检查 NOTICE 文件是否列出所有关键依赖
# 至少需要包含：
cat NOTICE

# 必须列出的依赖：
# - AndroidX (Apache 2.0)
# - Jetpack Compose (Apache 2.0)
# - Room (Apache 2.0)
# - Hilt / Dagger (Apache 2.0)
# - SQLCipher (BSD 3-Clause)
# - Coil (Apache 2.0)
# - PaddleOCR (Apache 2.0)
# - ONNX Runtime (MIT)
# - Lunar 农历 (MIT)
# - Kotlinx Serialization (Apache 2.0)
# - WorkManager (Apache 2.0)
```

#### 检查 6：测试数据脱敏

```bash
# 检查测试文件中是否有真实数据
find . -path '*/test/*' -name '*.kt' | xargs grep -l "密码\|password\|真实姓名\|手机号"

# 测试数据应该使用：
# - 模拟姓名："张三"、"李四"、"Test User"
# - 模拟金额：12345L（123.45元）
# - 模拟密码："test_password_123"
# - 模拟日期：固定日期而非动态生成
```

#### 检查 7：代码中的硬编码值

```bash
# 搜索可能的硬编码敏感信息
grep -rn --include='*.kt' \
  -e 'api_key' -e 'apiKey' -e 'API_KEY' \
  -e 'secret' -e 'SECRET' \
  -e 'token' -e 'TOKEN' \
  -e 'password' -e 'PASSWORD' \
  -e 'http://' -e 'https://' \
  -e 'firebase' -e 'Firebase' \
  app/src/ core/src/

# 需要特别注意：
# - 硬编码的 URL（如有自建后端）
# - 硬编码的 API Key（如 Crashlytics key）
# - 硬编码的加密密钥/IV
```

#### 检查 8：GitHub Actions 安全

```bash
# 检查 workflow 文件
cat .github/workflows/ci.yml
cat .github/workflows/release.yml

# 确认：
# 1. permissions: contents: read（CI）或 contents: write（Release）
# 2. 无 echo ${{ secrets.XXX }} 语句
# 3. Action 版本锁定到 SHA 或版本标签
# 4. Release workflow 的签名密钥通过 secrets 传入
```

### 24.3 PalmNote 完整检查清单

```markdown
## PalmNote 开源发布 Checklist

### 第一步：本地检查
- [ ] 运行 grep 搜索硬编码敏感信息（检查 7）
- [ ] 检查测试数据是否脱敏（检查 6）
- [ ] 检查截图是否使用模拟数据（检查 1）
- [ ] 检查 NOTICE 文件完整性（检查 5）
- [ ] 检查 .gitignore 是否完善（检查 3）
- [ ] 检查 PaddleOCR 模型文件（检查 4）

### 第二步：Git 历史检查
- [ ] git log --format='%ae' | sort -u 检查邮箱（检查 2）
- [ ] git log --all --diff-filter=D --name-only 检查已删除的敏感文件
- [ ] 如有敏感文件曾被提交，执行历史清理（见 24.4）

### 第三步：GitHub 配置
- [ ] 签名密钥已配置为 GitHub Secrets
- [ ] Dependabot alerts 已开启
- [ ] 仓库描述/About 无个人信息
- [ ] Topics 标签已设置（android, kotlin, jetpack-compose 等）

### 第四步：最终确认
- [ ] 克隆一份干净的仓库到新目录，验证构建通过
- [ ] 确认仓库中无任何你不想公开的信息
- [ ] 设置仓库为 Public
```

---

### 24.4 .gitignore 最佳实践

PalmNote 当前 .gitignore 可以改进：

```gitignore
# === 当前已有 ===
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
/build
/captures
/**/build/
.externalNativeBuild
.cxx
release.jks
app/build/
*.log
.opendenc/
Thumbs.db
*.hprof
*.apk
*.aab
*.jks
*.keystore

# === 建议新增 ===

# ZCode/Mimosa 工具状态（已生效，见根 .gitignore）
.mimosa/

# Google Services（如果接入 Firebase）
google-services.json
app/google-services.json

# 密钥和凭据
*.pem
*.p12
*.pfx
*.key
*.crt
*.cer

# 环境变量
.env
.env.local
.env.*.local

# 编辑器 / IDE
*.swp
*.swo
*~
.vscode/
*.sublime-*

# OS 文件
Thumbs.db
.DS_Store
.DS_Store?
._*
.Spotlight-V100
.Trashes

# 测试 / 覆盖率
*.exec
*.ec
coverage/

# 本地数据库（开发调试用）
*.db
*.db-shm
*.db-wal
# 注意：Room schema 文件需要保留，只排除运行时生成的数据库
# 如果有测试数据库，确保在 test 目录下

# 签名相关
signing/
*.gpg

# Crashlytics / Sentry
crashlytics-build.properties
sentry.properties

# Android Profiler
*.trace
```

### 24.5 Git 历史清理

如果曾经不小心提交过敏感文件，仅仅 `.gitignore` + `git rm` 是不够的——文件仍然存在于 Git 历史中。

#### 检查是否曾提交过敏感文件

```bash
# 检查是否曾有文件被删除（可能包含敏感文件）
git log --all --diff-filter=D --name-only --pretty=format:"%H %s"

# 搜索特定文件名
git log --all --full-history -- "*.jks" "*.keystore" "local.properties" "google-services.json"

# 搜索代码中的密钥模式
git log --all -p --grep="api_key\|secret\|password\|token" --regexp-ignore-case
```

#### 使用 BFG Repo-Cleaner 清理

```bash
# 安装 BFG
# brew install bfg  (macOS)
# 或下载 jar: https://rtyley.github.io/bfg-repo-cleaner/

# 1. 克隆裸仓库
git clone --mirror https://github.com/PickGear/PalmNote.git PalmNote-mirror
cd PalmNote-mirror

# 2. 删除敏感文件
bfg --delete-files release.jks
bfg --delete-files local.properties
bfg --delete-files "*.keystore"

# 3. 删除包含敏感文本的提交
bfg --replace-text passwords.txt  # passwords.txt 格式: password==>***

# 4. 清理和推送
git reflog expire --expire=now --all && git gc --prune=now --aggressive
git push --force

# 5. 通知所有 fork 需要重新 fork
echo "⚠️ 仓库历史已重写，请删除旧 fork 并重新 fork"
```

#### 使用 git-filter-repo（更现代的工具）

```bash
# 安装
pip install git-filter-repo

# 克隆裸仓库
git clone --mirror https://github.com/PickGear/PalmNote.git PalmNote-mirror
cd PalmNote-mirror

# 删除文件
git-filter-repo --invert-paths --path release.jks --path local.properties

# 推送
git push --force --all
git push --force --tags
```

#### 清理后必须做的事

```markdown
- [ ] Force push 到远程
- [ ] 在 GitHub Settings → General → Danger Zone 中废除所有已知的 sensitive data
- [ ] 通知所有贡献者重新 fork / rebase
- [ ] 轮换所有泄露的密钥（API Key、签名密钥等）
- [ ] 如果泄露了签名密钥，必须生成新的 keystore
```

### 24.6 GitHub Actions 安全

#### Permissions 最小化

```yaml
# ✅ 正确：只读权限
permissions:
  contents: read

# ❌ 错误：写权限（除非确实需要）
permissions:
  contents: write
```

#### Secrets 使用规范

```yaml
# ✅ 正确：通过 secrets 引用
env:
  STORE_PASSWORD: ${{ secrets.RELEASE_STORE_PASSWORD }}

# ❌ 错误：硬编码密码
env:
  STORE_PASSWORD: mypassword123

# ❌ 错误：打印 secrets（会被 GitHub 自动遮蔽，但仍有风险）
- run: echo "Password is ${{ secrets.RELEASE_STORE_PASSWORD }}"
```

#### Action 版本锁定

```yaml
# ✅ 推荐：锁定到 commit SHA（防供应链攻击）
- uses: actions/checkout@b4ffde65f46336ab88eb53be808477a3936bae11  # v4.1.1

# ⚠️ 可接受：锁定到版本标签
- uses: actions/checkout@v4

# ❌ 危险：使用 main/master
- uses: actions/checkout@main
```

#### Fork PR 安全

```yaml
# 对于 fork 的 PR，secrets 不可用（GitHub 默认行为）
# 这是安全的，但需要在 CI 中处理缺失的 secrets

- name: Build Release
  if: github.event_name != 'pull_request'  # PR 不构建 Release
  run: ./gradlew assembleRelease
```

### 24.7 个人隐私保护

#### Git 提交邮箱

```bash
# 查看当前配置
git config user.email

# 使用 GitHub 隐私邮箱（推荐）
git config user.email "你的ID+PickGear@users.noreply.github.com"

# 查看 GitHub 隐私邮箱：
# GitHub → Settings → Emails → Keep my email addresses private
```

#### 截图脱敏

仓库 `screenshots/` 目录中的截图，确保：

```markdown
- [ ] 无真实账单金额（用模拟数据）
- [ ] 无真实密码/用户名
- [ ] 无真实姓名/地址/手机号
- [ ] 无真实银行卡号
- [ ] 无真实邮箱地址
```

#### README 中的个人信息

```markdown
# ✅ 正确：只留 GitHub Issues
联系方式：GitHub Issues

# ❌ 错误：留手机号/邮箱
联系方式：13800138000 / myemail@gmail.com
```

### 24.8 第三方许可证合规

#### GPL-3.0 兼容性

PalmNote 使用 GPL-3.0，以下许可证兼容：

| 许可证 | 兼容 | 说明 |
|--------|------|------|
| MIT | ✅ | 完全兼容 |
| Apache 2.0 | ✅ | 完全兼容 |
| BSD 2/3-Clause | ✅ | 完全兼容 |
| LGPL | ✅ | 可以作为库使用 |
| MPL 2.0 | ⚠️ | 文件级 copyleft，需注意 |
| GPL 2.0-only | ❌ | 不兼容 GPL-3.0 |
| AGPL | ⚠️ | 更严格，需评估 |

#### PaddleOCR 许可证

PaddleOCR 使用 Apache 2.0 许可证，兼容 GPL-3.0。但需注意：

```markdown
- ONNX Runtime 使用 MIT 许可证 ✅
- PaddleOCR 模型文件使用 Apache 2.0 ✅
- 如果模型文件有单独的许可条款，需要在 NOTICE 中说明
```

#### NOTICE 文件规范

```markdown
# NOTICE

PalmNote
Copyright (c) 2026 PickGear

This product is licensed under the GNU General Public License v3.0.

## Third-Party Components

### AndroidX
- License: Apache License 2.0
- Copyright: The Android Open Source Project

### Jetpack Compose
- License: Apache License 2.0
- Copyright: The Android Open Source Project

### Room
- License: Apache License 2.0
- Copyright: The Android Open Source Project

### Hilt
- License: Apache License 2.0
- Copyright: Google LLC

### SQLCipher
- License: BSD 3-Clause
- Copyright: Zetetic LLC

### Coil
- License: Apache License 2.0
- Copyright: Coil Contributors

### PaddleOCR
- License: Apache License 2.0
- Copyright: PaddlePaddle Authors

### ONNX Runtime
- License: MIT License
- Copyright: Microsoft Corporation

### Lunar (农历)
- License: MIT License
- Copyright: 6tail
```

### 24.9 依赖漏洞扫描

```bash
# 使用 OWASP Dependency-Check（可选）
./gradlew dependencyCheckAnalyze

# 或使用 GitHub Dependabot（推荐）
# 在仓库 Settings → Code security and analysis → Dependabot alerts 开启
```

#### Dependabot 配置

创建 `.github/dependabot.yml`：

```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 10
    reviewers:
      - "PickGear"
```

### 24.10 发布后监控

```markdown
## 发布后监控 Checklist

- [ ] 定期检查 GitHub Security Advisories
- [ ] 定期检查 Dependabot alerts
- [ ] 如发现泄露，立即轮换密钥
- [ ] 监控 Issue 中是否有人报告安全问题
- [ ] 定期更新 NOTICE 文件（依赖变更时）
```

### 24.11 紧急响应

如果发现敏感数据已泄露到公开仓库：

```
1. 立即轮换泄露的密钥/密码
2. 清理 Git 历史（BFG / git-filter-repo）
3. Force push
4. 通知所有 fork 维护者
5. 检查是否有异常访问/使用记录
6. 更新 CHANGELOG 记录事件
7. 如有必要，联系 GitHub 支持请求从缓存中删除
```

---

## 25. 工作成果保护

> **背景**：2026-09-03 一次误回滚（reset --hard + clean）清空了数十小时未提交的工作，
> 仅靠 IDE Local History 部分恢复 + 记忆重放才救回。本章规则是那次事故的直接产物，无一例外。

### 25.1 提交纪律

1. **验证过的批次立即提交**：构建 + 测试全绿即提交，禁止让未提交工作跨夜
2. **本地 main 至少每天 push 一次**：未 push 的工作没有任何远端保护
3. **大批次逻辑拆分提交**：小组件/主题/安全修复各一个提交，便于回溯和选择性回滚
4. **绝不在构建红着的状态下结束会话**：红状态是最大的"想推倒重来"诱因

### 25.2 危险操作守则

以下操作对未提交工作有毁灭性风险，执行前必须逐条确认：

| 操作 | 前置条件 |
|------|----------|
| `git reset --hard` | `git status` 确认无未提交工作，或已 stash 并记录 stash 号 |
| `git clean -fd` | 先 `git clean -nd` 预览将删除的文件，确认无价值 |
| `git checkout -- <file>` | 确认该文件的改动已提交或不再需要 |
| IDE 的 Rollback / 覆盖恢复 | 确认目标快照时间点正确（误选时间点会部分损坏文件） |

### 25.3 事故恢复路径（按优先级）

```
1. git reflog              → 找回已提交但被重置的历史
2. git stash list          → 找回被暂存的工作
3. IDE Local History       → 找回未提交的文件（Android Studio: 右键目录 → Local History → Show History）
   ⚠️ 注意校验恢复完整性：恢复后立即全量构建 + 扫描空文件
   （实际案例：恢复产生 13 个空文件，靠提交重建才补齐）
4. 从最近提交重建          → 最后手段
```

### 25.4 会话收尾检查

每轮开发（含 AI 自动化轮次）结束前：

- [ ] `git status` 工作树干净（全部提交，或明确说明遗留原因）
- [ ] 构建通过（不允许留红）
- [ ] 无工具状态/临时文件被纳入版本库（`.mimosa/` 等）

---

## 26. 附录：完整发布 Checklist

```markdown
# PalmNote vX.Y.Z 发布 Checklist

## 1. 代码冻结
- [ ] 所有目标功能的 PR 已合并到 main
- [ ] 无遗留的 TODO/FIXME（或已记录到 Issue）

## 2. 版本号
- [ ] app/build.gradle.kts: versionName = "X.Y.Z"
- [ ] app/build.gradle.kts: versionCode = N（递增）

## 3. Changelog
- [ ] CHANGELOG.md: [Unreleased] 内容移至 [X.Y.Z] - YYYY-MM-DD
- [ ] 条目分类正确（Added/Changed/Fixed/Security）
- [ ] 每条条目用户可感知（不写纯内部重构）

## 4. 测试
- [ ] ./gradlew testDebugUnitTest — 全部通过
- [ ] ./gradlew lintDebug — 无 abort 级别错误
- [ ] Room 迁移测试通过（如涉及 schema 变更）
- [ ] 实机安装 Release APK，核心功能验证

## 5. 安全审查
- [ ] 加密相关改动已通过安全审查
- [ ] 日志无敏感数据泄露
- [ ] FLAG_SECURE 在敏感页面启用
- [ ] 备份加密验证通过

## 6. 构建
- [ ] ./gradlew assembleRelease — 成功
- [ ] APK 体积合理（检查是否有意外增长）
- [ ] APK 文件名：PalmNote-X.Y.Z.apk

## 7. 性能验证
- [ ] 冷启动时间 ≤ 1.5s
- [ ] 内存占用合理
- [ ] 无明显卡顿

## 8. Git
- [ ] git tag -a vX.Y.Z -m "Release vX.Y.Z: <摘要>"
- [ ] git push origin main
- [ ] git push origin vX.Y.Z

## 9. GitHub Release
- [ ] 基于 tag 创建 Release
- [ ] 标题：PalmNote vX.Y.Z
- [ ] 描述：从 CHANGELOG 复制
- [ ] 上传 APK 到 Assets
- [ ] 是否标记为 Pre-release（如 beta/rc）

## 10. 清理
- [ ] 删除已合并的 feature/fix 分支（本地 + 远程）
- [ ] 更新 README.md（如有必要）
- [ ] 更新 docs/（如有必要）

## 11. 验证
- [ ] Release 页面可访问
- [ ] APK 可正常下载
- [ ] CHANGELOG 链接正确
- [ ] 升级安装（从上一版本）数据完整
```

---

## 快速参考

```bash
# 开始新功能
git checkout main && git pull
git checkout -b feature/xxx

# 开发提交
git commit -m "feat(xxx): description"
git commit -m "feat(xxx): another change"

# 合并到 main
git checkout main
git merge --squash feature/xxx
git commit -m "feat(xxx): complete feature description"
git push origin main

# 打 tag 发布
git tag -a vX.Y.Z -m "Release vX.Y.Z: summary"
git push origin vX.Y.Z

# 清理
git branch -d feature/xxx
git push origin --delete feature/xxx

# 数据库迁移
./gradlew :core:kspDebugKotlin     # 导出新 schema
./gradlew testDebugUnitTest         # 运行迁移测试

# 代码质量
./gradlew detekt                    # 静态分析
./gradlew lintDebug                 # Lint

# 构建
./gradlew assembleDebug             # Debug APK
./gradlew assembleRelease           # Release APK
./gradlew bundleRelease             # App Bundle (AAB)
```

---

*最后更新：2026-09-05*
*版本：v1.1 — 26 章（含工作成果保护）+ 附录；按项目实际配置校准（行宽 140、detekt 阈值、
中文提交、迁移分级），并纳入 2026-09-03 误回滚事故的教训*
