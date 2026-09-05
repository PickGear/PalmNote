package com.palmnote.data.ocr

import android.graphics.Bitmap

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

    /** 释放模型资源。调用后引擎不可再用。 */
    suspend fun release()
}
