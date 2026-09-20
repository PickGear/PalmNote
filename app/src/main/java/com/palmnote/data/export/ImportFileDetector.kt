package com.palmnote.data.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * 统一导入入口的文件类型识别：用户只管选文件，由这里判断该走哪条导入路径。
 *
 * 判定顺序（从最特异到最一般）：
 * 1. 文件头是 PNB 魔数 → 完整备份包，恢复职能在「备份与恢复」页
 * 2. .xlsx → 账单导入（微信/支付宝/通用布局）
 * 3. ZIP 包内含本应用全量导出的特征条目（分组 CSV 名 / preferences.json）→ 全量数据导入
 * 4. 其余 ZIP / .csv → 账单导入（微信 / 支付宝 / 通用 CSV，含打包成 ZIP 的账单）
 *
 * [fromSnapshot] 是纯函数（便于单测）；[detect] 负责从 Uri 读出快照。
 */
object ImportFileDetector {

    enum class FileType { BACKUP_PACKAGE, FULL_DATA, BILL_FILE, UNKNOWN }

    /** 全量导出包的特征条目名（与 [CsvDataExporter] 的 CSV_GROUPS / preferences.json 对应） */
    internal val FULL_DATA_ENTRY_NAMES = setOf("记账.csv", "物品.csv", "生活.csv", "preferences.json")

    fun fromSnapshot(fileName: String?, header: ByteArray, zipEntryNames: List<String>): FileType {
        // PNB 魔数（备份包：明文 PNB3 / 加密 PNB2 / 旧版 PNBK）
        if (header.size >= 3 && String(header, 0, 3, Charsets.US_ASCII) == "PNB") {
            return FileType.BACKUP_PACKAGE
        }
        val lowerName = fileName?.lowercase().orEmpty()
        if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) return FileType.BILL_FILE
        // ZIP：按包内条目区分全量数据包与账单包
        if (lowerName.endsWith(".zip") || header.size >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
            val names = zipEntryNames.map { it.substringBeforeLast('/') }.toSet()
            return if (zipEntryNames.any { it.substringAfterLast('/') in FULL_DATA_ENTRY_NAMES }) {
                FileType.FULL_DATA
            } else if (names.any { it == "images" } || zipEntryNames.any { it.endsWith(".csv", true) }) {
                FileType.BILL_FILE
            } else {
                FileType.UNKNOWN
            }
        }
        if (lowerName.endsWith(".csv")) return FileType.BILL_FILE
        return FileType.UNKNOWN
    }

    /** 从 SAF Uri 读取识别所需快照（文件名 + 头部字节 + ZIP 条目名）。 */
    suspend fun detect(context: Context, uri: Uri): FileType = withContext(Dispatchers.IO) {
        val fileName = queryFileName(context, uri)
        var header = ByteArray(0)
        val entryNames = mutableListOf<String>()
        // 文件可能在选择后、读取前被删除/权限被吊销：读不到一律按 UNKNOWN 走兜底提示，不崩溃
        val opened = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedInputStream(input).use { buffered ->
                    header = ByteArray(4)
                    var read = 0
                    while (read < 4) {
                        val n = buffered.read(header, read, 4 - read)
                        if (n < 0) break
                        read += n
                    }
                    header = header.copyOf(read)
                    val isZip = read >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
                    if (isZip) {
                        // 只读目录名，不读内容；ZIP 解析失败按 UNKNOWN 走兜底提示
                        try {
                            ZipInputStream(buffered).use { zis ->
                                var entry = zis.nextEntry
                                var count = 0
                                while (entry != null && count < 200) {
                                    entryNames.add(entry.name)
                                    count++
                                    entry = zis.nextEntry
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            } != null
        }
        if (opened.getOrNull() != true) return@withContext FileType.UNKNOWN
        fromSnapshot(fileName, header, entryNames)
    }

    private fun queryFileName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }
}
