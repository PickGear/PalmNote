package com.palmnote.data.ocr

import android.graphics.Bitmap
import android.graphics.PointF

/**
 * 一行 OCR 识别文本及其置信度（0..1）。
 * [confidence] 为 null 表示未知——例如仅通过 [OcrEngine.recognize] 的纯文本路径取得时。
 *
 * [box] 为该行文本框的 4 个顶点，坐标位于**传入 [OcrEngine.recognizeDetailed] 的 bitmap 的像素坐标系**
 * （见 [PaddleOcrEngine] 的坐标系说明）；引擎无法提供坐标时（如纯文本适配路径）为 null。
 * 该参数带默认值，保证既有 `OcrLine(text, confidence)` 构造与解析器/测试不受影响。
 */
data class OcrLine(val text: String, val confidence: Float?, val box: List<PointF>? = null)

/**
 * OCR 文本识别引擎抽象。当前实现为 PaddleOCR（PP-OCRv6, ONNX Runtime 端侧推理）。
 * 引擎非线程安全的使用方需自行串行调用（如 ViewModel 的 viewModelScope）。
 */
interface OcrEngine {

    /**
     * 识别图片中的文本，返回按阅读顺序排列的多行文本。
     * 首次调用会加载模型（耗时，实现内部切 IO 线程）。
     */
    suspend fun recognize(bitmap: Bitmap): String

    /**
     * 识别图片中的文本，返回按阅读顺序排列的多行及其置信度。
     *
     * 默认实现基于 [recognize] 拆行，置信度置为 null（未知）；实现类若能拿到每行分数
     * 应覆盖本方法以提供真实置信度。默认实现保证调用方无需关心引擎能力差异。
     */
    suspend fun recognizeDetailed(bitmap: Bitmap): List<OcrLine> =
        recognize(bitmap).lines().map { it.trim() }.filter { it.isNotBlank() }
            .map { OcrLine(it, confidence = null) }

    /** 释放模型资源。调用后引擎不可再用。 */
    suspend fun release()
}
