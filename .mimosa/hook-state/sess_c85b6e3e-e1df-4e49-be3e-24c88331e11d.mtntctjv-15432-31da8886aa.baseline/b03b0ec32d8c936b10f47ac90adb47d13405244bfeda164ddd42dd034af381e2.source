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
        val engine = lock.withLock { ensureLoaded() }
        val result = engine.recognize(bitmap)
        return buildString {
            for (r in result.results) {
                if (isNotEmpty()) append('\n')
                append(r.text)
            }
        }
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
