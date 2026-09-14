package io.legado.app.core.platform

import kotlin.io.encoding.Base64

/**
 * `:core:platform` 内部共用的「字节 ↔ 文本」编码工具。
 *
 * **为什么要有这一份**：[RuleDataStorage.md5] 与 [SymmetricCrypto]（android/desktop 两份
 * JCA actual）都要做同一种小写十六进制与标准 Base64，而本模块**不能**复用
 * `:core:model` 的 `io.legado.app.utils.isHex()`——`:core:model` 已经依赖 `:core:platform`，
 * 反向即构成依赖环（`checkModuleDependencies` 会拦）。与其在每个 actual 里各抄一遍，
 * 不如在本模块内部留一份：`internal`，不出模块，也不进任何契约面。
 *
 * **语义逐字对齐迁移前的 `:app` `help/crypto/CryptoUtils.kt`**：
 * - 十六进制一律**小写**（`0123456789abcdef`）——这是文件名与既有密文的兼容面，不能改；
 * - Base64 用标准表 + padding 编码；解码时先剥掉所有空白，失败再回落 URL-safe 表。
 */
internal const val HEX_CHARS_LOWER = "0123456789abcdef"

/** 小写十六进制编码。 */
internal fun ByteArray.encodeHexLower(): String {
    val builder = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        builder.append(HEX_CHARS_LOWER[v ushr 4])
        builder.append(HEX_CHARS_LOWER[v and 0x0F])
    }
    return builder.toString()
}

/**
 * 十六进制解码。
 *
 * @throws IllegalArgumentException 长度不是偶数时抛出（与 `CryptoUtils.hexToByteArray` 的
 *   `require` 一致）。
 */
internal fun String.decodeHex(): ByteArray {
    require(length % 2 == 0) { "Hex input must contain an even number of characters" }
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}

/** 标准 Base64 编码（带 padding）。 */
internal fun ByteArray.encodeBase64(): String = Base64.Default.encode(this)

/**
 * Base64 解码：先剥掉所有空白，标准表失败再回落 URL-safe 表。
 *
 * @throws IllegalArgumentException 两种字母表都解不出来时抛出（与迁移前一致）。
 */
internal fun String.decodeBase64OrUrlSafe(): ByteArray {
    val normalized = replace(WHITESPACE_REGEX, "")
    return try {
        Base64.Default.decode(normalized)
    } catch (e: IllegalArgumentException) {
        Base64.UrlSafe.decode(normalized)
    }
}

private val WHITESPACE_REGEX = Regex("\\s")

/**
 * 是否全部字符都落在 `0-9a-fA-F`。
 *
 * 与 `:core:model` 的 `io.legado.app.utils.isHex()` **逐字同语义**——包括「**空串返回 true**」
 * 和「只判字符表、不校验长度或 `#` 前缀」这两点。保留它是因为
 * [SymmetricCrypto.decryptStr] 要沿用迁移前的「整串都是 hex ⇒ 按 hex 解」分支。
 */
internal fun String.isHexText(): Boolean =
    all { c -> c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f' }
