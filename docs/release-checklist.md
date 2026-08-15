# PalmNote Release 版本 APK 打包检查清单

> v1.0 | 2026-08-16 | 每次打 Release 前逐项核对，确保产出的 APK 完整、可安装、可签名、无回归。
>
> **用法：** 打包前先做「清理 & 环境」，再按 1-9 顺序核对；其中第 8 项（真机回归）必须在 release 包上完成，不能用 debug 包替代。
>
> **校验产物：** 构建后 APK 必须是完整 ZIP（含 `META-INF` 与 `AndroidManifest.xml`）、已用正式证书签名（非 debug 签名）、`mapping.txt` 已生成。

---

## 一、版本号

- [ ] `app/build.gradle.kts` 的 `versionCode` 已递增（单调递增，禁止回退，上架场景尤其重要）
- [ ] `versionName` 与本次发布的语义化版本一致（如 `1.4.0`）
- [ ] `app/src/main/res/values*/strings.xml` 的 `app_version` 显示文案与 `versionName` 一致
- [ ] 输出的 APK 文件名符合 `PalmNote-<version>.apk`（`app/build.gradle.kts` 的 `outputFileName` 自动生成）

## 二、签名配置

- [ ] `release.jks` 存在且为**正式发布证书**（`keytool -list -keystore release.jks` 核对别名/指纹）
- [ ] 密码/别名通过 `local.properties`（`RELEASE_STORE_*` / `RELEASE_KEY_*`）注入，**未入库**
- [ ] 构建产物签名验证通过：`apksigner verify --print-certs <apk>`，确认 `Signer #1` 是正式证书而非 debug
- [ ] ⚠️ `release.jks` 已**离线备份**（丢失无法找回，且不能从 git 恢复）

## 三、Build Types

- [ ] `release`：`isMinifyEnabled = true`、`isShrinkResources = true`
- [ ] `release`：`signingConfig = release`（签名文件存在时）
- [ ] `release` 与 `debug` 的 `abiFilters` 符合预期（本项目 release 仅 arm64-v8a，debug 补 x86_64 供模拟器）
- [ ] `debug` 不应被误用于发布

## 四、ProGuard / R8 规则

- [ ] `app/proguard-rules.pro` 覆盖反射 / 序列化 / Gson / Room / 第三方 SDK 的 keep 规则
- [ ] 构建后已生成 `app/build/outputs/mapping/<variant>/mapping.txt`（证明 R8 混淆已执行）
- [ ] `mapping.txt` 已随本版本**归档留存**（崩溃堆栈反解依赖它）

## 五、清理 & 环境

- [ ] 本地构建前先 `./gradlew clean`，避免增量残留
- [ ] JDK 版本与项目要求一致（本项目 JBR/17）
- [ ] Android SDK / `compileSdk` / AGP 版本匹配
- [ ] （可选）CI 干净环境构建：`assembleDebug assembleRelease` 冒烟通过

## 六、资源检查

- [ ] `lint` 通过（`lintRelease` / CI `lintDebug`），无资源缺失/引用错误
- [ ] `shrinkResources` 未误删运行时依赖的资源（尤其动态引用）
- [ ] 核对 APK 体积：dex / native so / assets 无意外膨胀
- [ ] 多语言/多 density 资源完整（`values-*`、`drawable-*`）

## 七、Manifest 检查

- [ ] `applicationId` / 版本信息正确
- [ ] 权限最小化（无多余敏感权限，本项目为离线应用）
- [ ] `lintVital` 通过，无致命 manifest 错误（历史案例：WorkManager 依赖被混淆/移除时 `WorkManagerInitializer` 冲突）

## 八、测试

- [ ] 单元测试通过：`./gradlew testDebugUnitTest`（含 Robolectric 迁移测试）
- [ ] Room schema 与代码一致（CI 已自动校验 `app/schemas` / `core/schemas`）
- [ ] **真机回归（release 包）：** 安装 release APK，走一遍核心流程——启动、账本、物品、生活、搜索、备份、应用锁、密码本（混淆后必须实测，debug 不混淆无法覆盖）
- [ ] 升级路径：旧版本数据 → 新版本正常迁移

## 九、发布前最后核对（GitHub Releases）

- [ ] APK 上传后，下载并验证：文件大小与本地一致、可正常安装（`packagelnfo is null` 说明 APK 损坏）
- [ ] Release 标签 / 版本号 / 发布说明与 tag 对齐
- [ ] 确认上传的是**完整 APK**，不是残缺文件（历史教训：损坏 ZIP 上传后用户安装报 `packagelnfo is null`）

---

## 附录 A：关键命令

```bash
# 签名核对
keytool -list -v -keystore release.jks
apksigner verify --print-certs app/build/outputs/apk/release/PalmNote-<ver>.apk

# 完整性（ZIP 结构）
# APK 是 ZIP 容器，可用压缩工具打开验证

# 全量检查
./gradlew clean assembleRelease lintRelease testDebugUnitTest

# 冒烟（跳过 lint 致命校验时的兜底，正常发布不跳过）
./gradlew assembleRelease -x lintVitalRelease
```

## 附录 B：自动化覆盖对照

| 清单项 | CI（.github/workflows/ci.yml） | 本地必查 |
|---|---|---|
| 版本号 | 部分（无自动校验） | ✅ |
| 签名 | ❌（CI 无密钥） | ✅ |
| Build Types | ✅ assembleRelease 冒烟 | ✅ |
| ProGuard/R8 | 部分（build 触发） | ✅ mapping 归档 |
| 清理 & 环境 | ✅ 干净环境构建 | ✅ clean |
| 资源 | ✅ lintDebug | ✅ |
| Manifest | ✅ lintDebug/lintVital | ✅ |
| 测试 | ✅ testDebugUnitTest + schema 校验 | ✅ 真机回归 |
| 发布前核对 | ❌ | ✅ |
