# com.paddle.ocr 为纯 Kotlin 实现（无 JNI/native 方法、无反射），交由 R8 自由收缩；
# 若未来加入 native 方法或反射调用，需在此补 keep 规则。

# ONNX Runtime：AAR 自带的 consumer 规则只覆盖 telemetry；
# 核心 Java API 的类/方法由 native 侧按名查找，保持整包 keep 以防 R8 重命名导致运行时崩溃
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
