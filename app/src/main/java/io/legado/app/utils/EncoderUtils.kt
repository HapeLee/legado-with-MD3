package io.legado.app.utils

import android.util.Base64

/**
 * 编码工具 escape base64
 */
@Suppress("unused")
object EncoderUtils {

    /**
     * 模拟 JavaScript 的 escape() 函数行为。
     * 与 JS 一致：不编码 @*_+-./，hex 大写，%uXXXX 固定 4 位，%XX 固定 2 位。
     */
    fun escape(src: String): String {
        val safeChars = "@*_+-./"
        val tmp = StringBuilder()
        for (char in src) {
            val charCode = char.code
            if (charCode in 48..57 || charCode in 65..90 || charCode in 97..122 || char in safeChars) {
                tmp.append(char)
                continue
            }
            val hex = charCode.toString(16).uppercase()
            if (charCode < 256) {
                tmp.append("%").append(hex.padStart(2, '0'))
            } else {
                tmp.append("%u").append(hex.padStart(4, '0'))
            }
        }
        return tmp.toString()
    }

    @JvmOverloads
    fun base64Decode(str: String, flags: Int = Base64.DEFAULT): String {
        val bytes = Base64.decode(str, flags)
        return String(bytes, Charsets.UTF_8)
    }

    @JvmOverloads
    fun base64Encode(str: String, flags: Int = Base64.NO_WRAP): String {
        return Base64.encodeToString(str.toByteArray(Charsets.UTF_8), flags)
    }

    @JvmOverloads
    fun base64Encode(bytes: ByteArray, flags: Int = Base64.NO_WRAP): String {
        return Base64.encodeToString(bytes, flags)
    }
    
    @JvmOverloads
    fun base64DecodeToByteArray(str: String, flags: Int = Base64.DEFAULT): ByteArray {
        return Base64.decode(str, flags)
    }

}

/**
 * 解析 data URL 中的 base64 数据并返回字节数组。
 * 支持 `data:image/png;base64,...` 形式或纯 base64 字符串。
 * 失败时返回 null。
 */
fun String.decodeBase64DataUrlBytes(): ByteArray? {
    val payload = this.substringAfter(",", this).trim().filterNot { it.isWhitespace() }
    if (payload.isEmpty()) return null
    val normalized = if (payload.length % 4 != 0) {
        payload + "=".repeat(4 - (payload.length % 4))
    } else {
        payload
    }
    return runCatching { Base64.decode(normalized, Base64.DEFAULT) }
        .getOrElse {
            runCatching { Base64.decode(normalized, Base64.URL_SAFE) }.getOrNull()
        }
}

/**
 * 估算 data URL 或纯 base64 字符串解码后的字节数（不实际解码）。
 */
fun String.estimateBase64DataUrlBytes(): Long {
    val payload = this.substringAfter(",", this).trim().filterNot { it.isWhitespace() }
    return payload.length.toLong() * 3L / 4L
}
