package com.palmnote.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * PaddleOCR（PP-OCRv6, ONNX Runtime）引擎实现。
 * 模型（det/rec + 字符字典）打包在 ppocr-sdk 模块 assets 中，首次识别时懒加载。
 */
class PaddleOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrEngine {

    private val lock = Mutex()
    private var ocr: PaddleOCR? = null

    override suspend fun recognize(bitmap: Bitmap): String {
        // 整个推理过程持锁：release() 可能在推理中途释放 native 引擎导致崩溃
        val text = lock.withLock {
            val engine = ensureLoaded()
            val result = engine.recognize(bitmap)
            buildString {
                for (r in result.results) {
                    if (isNotEmpty()) append('\n')
                    append(r.text)
                }
            }
        }
        return text
    }

    /**
     * 透出每行识别置信度 + 文本框坐标（引擎以 recScoreThresh = 0.0f 运行，低分行不会被丢弃）。
     *
     * 【坐标系实证结论】`box.points` 已由 SDK 换算回**传入本方法的 bitmap 的像素坐标系**，无需再做变换。
     * 依据（逐文件逐行确认，非推测）：
     * 1. `DetPreprocessor.preprocess(...)`（ppocr-sdk/.../preprocess/DetPreprocessor.kt:60-61）
     *    取 `originalH = src.rows()` / `originalW = src.cols()`，而 `src` 来自
     *    `BitmapUtils.bitmapToBGRMat(bitmap)`（同文件 :45），即**输入 bitmap 的原始尺寸**。
     * 2. `ImageUtils.resizeToMultipleOf32(...)`（ppocr-sdk/.../util/ImageUtils.kt:23-51）只做
     *    等比 `Imgproc.resize`（按 limitSideLen/limitType 求 ratio，再对齐到 32 的倍数），
     *    **没有任何 letterbox / padding / 补边**，故检测图与原图只差一个均匀缩放。
     * 3. `DBPostProcessor.process(...)`（ppocr-sdk/.../postprocess/DBPostProcessor.kt:48-51）
     *    以 `scaleX = originalW / pW`、`scaleY = originalH / pH`（pW/pH 为网络输入尺寸）把顶点缩放回
     *    `originalW/H`，并在 :159-160 用 `coerceIn(0, originalW/H)` 夹取。
     *    → 顶点落在输入 bitmap 的坐标空间，且不超出其边界。
     */
    override suspend fun recognizeDetailed(bitmap: Bitmap): List<OcrLine> {
        val lines = lock.withLock {
            val engine = ensureLoaded()
            val result = engine.recognize(bitmap)
            result.results.map { OcrLine(it.text, it.confidence, it.box.points) }
        }
        return lines
    }

    override suspend fun release() {
        lock.withLock {
            ocr?.release()
            ocr = null
        }
    }

    private suspend fun ensureLoaded(): PaddleOCR {
        ocr?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (!OpenCVUtils.init(context)) {
                error("Failed to initialize OpenCV native library")
            }
            PaddleOCR.create(
                context = context,
                config = PaddleOCRConfig(recScoreThresh = 0.0f, recBatchSize = 1),
                engineConfig = EngineConfig(numThreads = 4),
            )
        }
        ocr = loaded
        return loaded
    }
}
