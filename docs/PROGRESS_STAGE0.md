# 阶段 0：工作区归零与基线（2026-08-09）

## 操作摘要
- 新增 `.gitattributes`：文本文件 `* text=auto eol=lf`，二进制资源（png/jpg/onnx/jks/jar/aar/so 等）declare binary。
- `git add --renormalize .` 消除约 74 个 CRLF 造成的假 `M`，未引入内容改动。
- 发现并补提一处真实未提交改动：`app/proguard-rules.pro` 全局 `-keep class * { *; }` 删除（此前随 P0/P1 移除但被 CRLF 噪音掩盖遗漏提交）。
- commit `6c96540`：统一行尾策略 + proguard 修正。

## 基线命令结果（全部通过）
```
./gradlew compileDebugKotlin detekt testDebugUnitTest lintDebug assembleDebug --continue
→ BUILD SUCCESSFUL
```
- `compileDebugKotlin` UP-TO-DATE
- `testDebugUnitTest` UP-TO-DATE（24 测试类）
- `detekt` UP-TO-DATE（baseline 生效）
- `lintDebug` 通过
- `assembleDebug` UP-TO-DATE

## 锚点
- `git tag v1.3.0-clean`（可回退起点）

## 备注
- 阶段 0 产出：干净、可构建、可回退的工作区起点。
- 后续模块拆分以本基线为回退参照。