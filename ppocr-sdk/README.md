# ppocr-sdk

PalmNote 的离线 OCR 引擎模块 —— **PaddleOCR PP-OCRv6** 的本地化 Kotlin 移植，基于 ONNX Runtime 推理。

## 职责

- 提供 `OcrEngine` 实现（`PaddleOcrEngine`），对上层暴露「识别一张 Bitmap → 文本行」的能力。
- 完全离线：模型随 APK 打包，不发起任何网络请求。

## 结构

| 目录 | 内容 |
|---|---|
| `engine/` | 推理会话管理（`ORTSessionManager`）、检测/识别引擎（`DetectionEngine` / `RecognitionEngine`）、`PaddleOCR` 门面 |
| `preprocess/` | 检测/识别输入预处理（缩放、归一化） |
| `postprocess/` | DB 后处理（`DBPostProcessor`）、CTC 解码、四边形裁剪与几何、多边形 unclip、排序 |
| `model/` | `OCRBox` / `OCRResult` / `OCRError` 等数据模型 |
| `util/` | `BitmapUtils` / `ImageUtils` / `MathUtils` / `OpenCVUtils` / `YamlUtils` |
| `assets/models/` | `det/inference.onnx`、`rec/inference.onnx`（PP-OCRv6 模型） |

## 依赖

- `com.microsoft.onnxruntime:onnxruntime-android`
- `org.opencv:opencv`
- `kotlinx-coroutines-android`

## 构建与质量

- 代码风格：Spotless + ktlint（仓库根统一配置）。
- 静态检查：detekt（配置与 baseline 在 `config/detekt/`，与 `app`/`core` 共用规则）。

```bash
./gradlew :ppocr-sdk:detekt
./gradlew :ppocr-sdk:spotlessCheck
```

## 许可

本模块为 PaddleOCR 的移植，源文件保留 Apache-2.0 头；许可证见仓库根 `LICENSE` 与 `NOTICE`。
