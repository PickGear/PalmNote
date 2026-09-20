package com.palmnote.data.export

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Inflater

/**
 * 轻量 ZIP 读取器：解析中央目录，并支持**传统 ZipCrypto（PKWARE 传统加密）**单条目解密。
 *
 * 纯 JVM、零第三方依赖（只用 JDK 自带的 [Inflater]）。支付宝「记账本明细」导出的 zip 用的是
 * 传统 ZipCrypto（条目无 0x9901 额外字段、非 WinZip-AES），因此无需 zip4j / commons-compress。
 * 参考 PKWARE APPNOTE：4.3.9/4.3.12（EOCD / 中央目录）、4.4.4（传统加密）。
 *
 * ⚠️ size/crc 一律以**中央目录**为准。实测支付宝导出的 zip **设了 bit3（data descriptor），但本地头的 size/crc
 * 并不是 0**（不严格符合 APPNOTE，不少写入器都这样）。所以**不能**用本地头判长度——本地头只用来读
 * name/extra 的长度以算出压缩数据的起点。
 */
class ZipArchiveReader {

    /** 中央目录里的一条条目 */
    data class Entry(
        val name: String,
        val encrypted: Boolean,
        val method: Int,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val crc: Long,
        val dosTime: Int,
        /** general purpose bit flag（bit0=加密、bit3=data descriptor、bit11=UTF-8） */
        val flags: Int,
        val localHeaderOffset: Long
    )

    /** 前 4 字节是否为本地文件头签名 PK\x03\x04 */
    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    /** 是否存在被 ZipCrypto 加密的条目 */
    fun hasEncryptedEntry(bytes: ByteArray): Boolean = readEntries(bytes).any { it.encrypted }

    /** 解析 EOCD + 中央目录，返回全部条目；结构损坏或 Zip64 时抛 [IllegalArgumentException] */
    fun readEntries(bytes: ByteArray): List<Entry> {
        val eocd = requireNotNull(findEndOfCentralDirectory(bytes)) { "EOCD not found" }
        val total = le16(bytes, eocd + 10)
        var p = le32(bytes, eocd + 16)
        require(p >= 0 && p < bytes.size.toLong()) { "bad central directory offset" }
        val out = ArrayList<Entry>(total)
        repeat(total) {
            require(le32(bytes, p).toInt() == CENTRAL_SIG) { "bad central header" }
            val flags = le16(bytes, p + 8)
            val method = le16(bytes, p + 10)
            val dosTime = le16(bytes, p + 12)
            val crc = le32(bytes, p + 16)
            val cSize = le32(bytes, p + 20)
            val uSize = le32(bytes, p + 24)
            val nameLen = le16(bytes, p + 28)
            val extraLen = le16(bytes, p + 30)
            val commentLen = le16(bytes, p + 32)
            val localOff = le32(bytes, p + 42)
            rejectZip64(cSize, uSize, localOff)
            val name = String(bytes, (p + 46).toInt(), nameLen, Charsets.UTF_8)
            out.add(Entry(name, flags and 0x1 != 0, method, cSize, uSize, crc, dosTime, flags, localOff))
            p += 46 + nameLen + extraLen + commentLen
        }
        return out
    }

    /**
     * 解出条目字节。[password] 为 null 时只支持未加密条目；密码错或数据损坏抛 [WrongPasswordException]。
     *
     * ⚠️ [password] 传入后会被**就地清零**（`fill('\u0000')`），调用方不要再复用同一数组。
     */
    fun extract(bytes: ByteArray, entry: Entry, password: CharArray?): ByteArray {
        try {
            val dataStart = localDataOffset(bytes, entry).toInt()
            val total = entry.compressedSize.toInt()
            require(total >= 0 && dataStart + total <= bytes.size) { "bad entry size" }
            if (!entry.encrypted) {
                return inflateRaw(bytes, dataStart, total, entry.method, entry.uncompressedSize.toInt())
            }
            val plain = decryptRaw(bytes, dataStart, total, entry, password)
            val out = try {
                inflateRaw(plain, 12, total - 12, entry.method, entry.uncompressedSize.toInt())
            } catch (_: IllegalArgumentException) {
                // 密码错时解出的字节流是噪声，raw DEFLATE 解压必然尺寸/结构不符 → 归为密码错
                throw WrongPasswordException()
            }
            if (crc32Of(out) != entry.crc) throw WrongPasswordException()
            return out
        } finally {
            // 明文密码用后清零，缩短其驻留内存的时间窗
            password?.fill('\u0000')
        }
    }

    /** ZipCrypto 解密（含 12 字节加密头）并校验密码校验字节，返回**已解密但未解压**的字节（含 12 字节头） */
    private fun decryptRaw(src: ByteArray, offset: Int, len: Int, entry: Entry, password: CharArray?): ByteArray {
        val pwd = password ?: throw WrongPasswordException()
        val keys = Keys().apply { init(pwd) }
        val body = ByteArray(len)
        var i = 0
        while (i < len) {
            body[i] = keys.decrypt(src[offset + i])
            i++
        }
        // bit3=1 时本地头无 crc，ZipCrypto 密码校验字节用 dosTime 高 8 位；否则用 crc 高 8 位
        val expected = if (entry.flags and 0x8 != 0) (entry.dosTime ushr 8) and 0xFF
        else ((entry.crc ushr 24) and 0xFF).toInt()
        if (len < 12 || (body[11].toInt() and 0xFF) != expected) throw WrongPasswordException()
        return body
    }

    private fun localDataOffset(bytes: ByteArray, entry: Entry): Long {
        val lo = entry.localHeaderOffset
        require(le32(bytes, lo).toInt() == LOCAL_SIG) { "bad local header" }
        return lo + 30 + le16(bytes, lo + 26) + le16(bytes, lo + 28)
    }

    /** 解压 [length] 字节并按 [expectedSize] 校验长度（未加密路径防止静默截断） */
    private fun inflateRaw(src: ByteArray, offset: Int, length: Int, method: Int, expectedSize: Int): ByteArray {
        val out = if (method == 0) {
            src.copyOfRange(offset, offset + length)
        } else {
            require(method == 8) { "unsupported compression method: $method" }
            inflateDeflate(src, offset, length, expectedSize)
        }
        require(out.size == expectedSize) { "entry size mismatch (expected $expectedSize, got ${out.size})" }
        return out
    }

    private fun inflateDeflate(src: ByteArray, offset: Int, length: Int, expectedSize: Int): ByteArray {
        val inflater = Inflater(true) // nowrap = true（raw DEFLATE）
        try {
            inflater.setInput(src, offset, length)
            val out = ByteArrayOutputStream(expectedSize.coerceAtLeast(64))
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    /** Zip64 用 0xFFFFFFFF 占位真实值（真值在 extra 字段里），本类不支持 → 直接拒绝 */
    private fun rejectZip64(cSize: Long, uSize: Long, localOff: Long) {
        require(cSize != ZIP64_MARKER && uSize != ZIP64_MARKER && localOff != ZIP64_MARKER) {
            "Zip64 archives are not supported"
        }
    }

    private fun crc32Of(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

    private fun findEndOfCentralDirectory(b: ByteArray): Long? {
        if (b.size < 22) return null
        val min = (b.size - 22 - 0xFFFF).coerceAtLeast(0).toLong()
        var i = (b.size - 22).toLong()
        while (i >= min) {
            if (le32(b, i).toInt() == EOCD_SIG) return i
            i--
        }
        return null
    }

    private fun le16(b: ByteArray, i: Long): Int =
        (b[i.toInt()].toInt() and 0xFF) or ((b[i.toInt() + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, i: Long): Long {
        val p = i.toInt()
        return (b[p].toLong() and 0xFF) or ((b[p + 1].toLong() and 0xFF) shl 8) or
            ((b[p + 2].toLong() and 0xFF) shl 16) or ((b[p + 3].toLong() and 0xFF) shl 24)
    }

    /** 传统 ZipCrypto 密钥流（PKWARE APPNOTE 4.4.4） */
    private class Keys {
        private var k0 = 0x12345678
        private var k1 = 0x23456789
        private var k2 = 0x34567890

        /**
         * 逐字符 UTF-8 编码后混入密钥流——避免 `String(password)` 生成无法清零的不可变副本。
         * 本场景为 6 位数字密码（全 ASCII），逐字节编码与整体 UTF-8 编码等价；BMP 以内字符亦正确。
         */
        fun init(password: CharArray) {
            for (ch in password) {
                val c = ch.code
                when {
                    c < 0x80 -> update(c.toByte())
                    c < 0x800 -> {
                        update((0xC0 or (c shr 6)).toByte())
                        update((0x80 or (c and 0x3F)).toByte())
                    }
                    else -> {
                        update((0xE0 or (c shr 12)).toByte())
                        update((0x80 or ((c shr 6) and 0x3F)).toByte())
                        update((0x80 or (c and 0x3F)).toByte())
                    }
                }
            }
        }

        fun decrypt(c: Byte): Byte {
            val plain = (c.toInt() xor decryptByte()).toByte()
            update(plain)
            return plain
        }

        private fun decryptByte(): Int {
            val t = (k2 or 2) and 0xFFFF
            return ((t * (t xor 1)) ushr 8) and 0xFF
        }

        private fun update(b: Byte) {
            k0 = zipCrc32Update(k0, b)
            k1 = (k1 + (k0 and 0xFF)) * 134775813 + 1
            k2 = zipCrc32Update(k2, ((k1 ushr 24) and 0xFF).toByte())
        }
    }

    private companion object {
        const val EOCD_SIG = 0x06054b50
        const val CENTRAL_SIG = 0x02014b50
        const val LOCAL_SIG = 0x04034b50
        const val ZIP64_MARKER = 0xFFFFFFFFL
    }
}

/** zip 密码错误（或数据损坏导致 CRC/长度不符） */
class WrongPasswordException : Exception("zip password incorrect")

/** 传统 ZipCrypto 用的 CRC32 更新（多项式 0xEDB88320），与 [java.util.zip.CRC32] 的更新函数一致 */
private val ZIP_CRC_TABLE = IntArray(256).also { table ->
    for (n in 0 until 256) {
        var c = n
        repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
        table[n] = c
    }
}

private fun zipCrc32Update(crc: Int, b: Byte): Int =
    (crc ushr 8) xor ZIP_CRC_TABLE[(crc xor b.toInt()) and 0xFF]
