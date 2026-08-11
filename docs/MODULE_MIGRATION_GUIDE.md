# 模块拆分指南

## 当前模块结构（迁移后最终形态）

```
PalmNote/
├── app/                    # 应用模块（namespace: com.palmnote.app）
│   ├── 入口/导航/设置/搜索/备份/Worker
│   ├── 业务 UI：asset / bills / dashboard / life / settings / search
│   ├── feature/vault（密码本，字段级加密）+ 各业务 usecase
│   ├── 实现层：backup / export / ocr / repository impl
│   └── schemas/            # VaultDatabase Room schema（v1-v3）
├── core/                   # 核心库模块（namespace: com.palmnote，app 依赖 core）
│   ├── data/               # Entity + DAO + Migration + DbKeyStore + DataStore + Lock + Event
│   ├── domain/             # Repository 接口 + EventBus + Service + Util
│   ├── ui/                 # 通用组件 + 主题 + Lock 界面 + Notification + Widget
│   ├── di/                 # Hilt @Qualifier 等
│   ├── res/                # core 资源（字符串/主题）
│   └── schemas/            # AppDatabase Room schema（v1-v7）
└── ppocr-sdk/              # PaddleOCR 原生 SDK（已独立）
```

## 依赖方向

- `core` **不依赖** `app`（保证可独立复用与编译）
- `app` 通过 `implementation(project(":core"))` 依赖 core
- `ppocr-sdk` 仅被 app 依赖

## 关键迁移决策

1. **未拆独立 feature 模块**：bills/asset/life 的业务代码保留在 app 模块（以 `com.palmnote.feature.*` 包名组织 usecase），避免过度模块化。
2. **namespace 双轨**：core 用 `com.palmnote`，app 用 `com.palmnote.app`。manifest 组件一律显式全限定类名（`com.palmnote.PalmNoteApp` / `com.palmnote.MainActivity` / `com.palmnote.ui.widget.BillWidgetProvider`）。
3. **资源隔离**：AGP 每模块独立生成 R 类。app 的 `com.palmnote.app.R` 不含 core 资源；core 的 `com.palmnote.R` 在 app 编译 classpath 可用。55 个被 core+app 双引用的 string key 在两模块各放一份（core 引用走 core R，app 引用走 app R）。
4. **Room Schema**：AppDatabase 的 schema 由 core 导出到 `core/schemas`（v1-v7）；VaultDatabase 留在 app（`app/schemas`，v1-v3）。app 的迁移测试 assets 同时挂载 `$projectDir/schemas` 与 `$rootDir/core/schemas`，保证 `MigrationTestHelper` 能读到两份 schema。
5. **跨模块 nullable 属性**：core 暴露的 nullable public 属性（如 `asset.warrantyExpireDate`）在 app 内不能 smart cast，需 `val x = asset.foo!!` 局部解包。
6. **CurrencyUtils / AppContextHolder**：已删除全局 context 反模式，`CurrencyUtils.formatCurrency/formatCompact` 一律显式传 `context`（Composable 内取 `LocalContext.current`，ViewModel 内注入 `@ApplicationContext`）。

## 模块间引用检查

```bash
# core 不应反向依赖 app（应输出空）
grep -r "import com.palmnote.app" core/src/main/java
```

## 注意事项

1. **迁移测试**：`MigrationTestHelper` 从 assets 读取 schema，必须保证 `app/schemas` 与 `core/schemas` 中的 JSON 已提交（缺失会导致 `Migration6To7Test` 等失败）。
2. **权限声明**：core 若使用需要权限的 API（如 `NotificationManagerCompat.notify` 需 `POST_NOTIFICATIONS`），需在 `core/src/main/AndroidManifest.xml` 声明，否则 `:core:lintDebug` 报 `MissingPermission`。
3. **字符串 key**：新增 key 时若 core/app 都要用，需在两份 strings.xml 都加，并保持值一致。
4. **detekt baseline**：`config/detekt/baseline.xml` 为迁移前生成，覆盖历史风格问题；新增代码不应制造 baseline 未覆盖的违规。
