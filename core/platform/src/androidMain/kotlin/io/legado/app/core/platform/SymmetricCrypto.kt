package io.legado.app.core.platform

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * [SymmetricCrypto] 的 JVM（JCA）实现。
 *
 * androidMain 与 desktopMain 都是 JVM，`javax.crypto` 两边都在，故**两份 actual 内容完全相同**
 * ——同 [JvmFileSystem] / [JcaDigest] / [RuleDataStorage] 的先例。
 *
 * 本文件是 `:app` 的 `help/crypto/SymmetricCryptoAndroid` 在 `BaseSource` 实际路径上的
 * **逐字节等价移植**（不设 IV、密钥非空、不做 DES/DESede 密钥截断——理由见契约 KDoc）；
 * 十六进制与 Base64 编解码复用 [CryptoCodecs] 以对齐 `CryptoUtils`。
 */
actual object SymmetricCrypto {

    actual fun encryptBase64(algorithm: String, key: ByteArray, data: String): String =
        cipher(algorithm, key, Cipher.ENCRYPT_MODE)
            .doFinal(data.toByteArray(Charsets.UTF_8))
            .encodeBase64()

    actual fun decryptStr(algorithm: String, key: ByteArray, data: String): String {
        // 两个分支的**判定与顺序**都不能改：迁移前就是「整串 hex 且偶数长度 ⇒ 按 hex，否则 Base64」。
        val bytes = if (data.isHexText() && data.length % 2 == 0) {
            data.decodeHex()
        } else {
            data.decodeBase64OrUrlSafe()
        }
        return String(cipher(algorithm, key, Cipher.DECRYPT_MODE).doFinal(bytes), Charsets.UTF_8)
    }

    private fun cipher(algorithm: String, key: ByteArray, mode: Int): Cipher {
        val transformation =
            if (algorithm.contains('/')) algorithm else "$algorithm/ECB/PKCS5Padding"
        return Cipher.getInstance(transformation).apply {
            init(mode, SecretKeySpec(key, algorithm.substringBefore('/')))
        }
    }
}
