package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [SymmetricCrypto] 的契约测试（M2-3：原 Provider 用例的替代品）。
 *
 * 它守的不是「加解密能不能跑」，而是**兼容面**：`BaseSource` 用本原语加密后把密文落进
 * `caches` 表的 `userInfo_<sourceKey>`，密文格式一旦变了，所有既有用户的登录态就解不开
 * （＝集体登出）。所以这里的期望值是**硬编码密文**，由 `openssl enc -aes-128-ecb` 与
 * Node `crypto.createCipheriv('aes-128-ecb', …)` 两个**独立于被测代码**的实现算出并互相比对
 * 一致（AES 的 PKCS#7 与 JCE 的 PKCS5Padding 在 16 字节块下等价），不是拿被测实现自证。
 *
 * 复现向量（key 为 ASCII `"0123456789abcdef"` 的 16 字节，hex `30313233343536373839616263646566`）：
 * ```
 * printf '%s' '<明文>' | openssl enc -aes-128-ecb \
 *     -K 30313233343536373839616263646566 -nosalt -base64 -A
 * ```
 * 密钥刻意复刻 `BaseSource` 的真实用法：来源是
 * `DeviceId.value.encodeToByteArray(0, 16)`，即 **16 个字符的 UTF-8 字节**，
 * 不是把这 16 个 hex 字符解码成 8 字节。
 *
 * 本文件住在 `commonTest`，因此在 android 与 desktop 两个 target 上各跑一遍，同时覆盖两份 actual。
 */
class SymmetricCryptoContractTest {

    private val key = "0123456789abcdef".toByteArray()

    @Test
    fun `AES 加密结果与独立实现（openssl 与 node）逐字节一致`() {
        assertEquals(LOGIN_INFO_CIPHER_B64, SymmetricCrypto.encryptBase64("AES", key, LOGIN_INFO))
    }

    @Test
    fun `非块整数倍长度的明文按 PKCS5 填充，与独立实现一致`() {
        assertEquals("HSWCHD4xHuotTdhjOiXBtQ==", SymmetricCrypto.encryptBase64("AES", key, "abc"))
        assertEquals(
            "rVYw5JZRZ03Wi78zJzGfGQ==",
            SymmetricCrypto.encryptBase64("AES", key, "hunter2"),
        )
    }

    @Test
    fun `空明文也会被填充成整块，与独立实现一致`() {
        assertEquals("N3Ii4GGpJMWRzZwn6hY+1A==", SymmetricCrypto.encryptBase64("AES", key, ""))
    }

    @Test
    fun `既有密文（Base64 形式）能解回原文`() {
        assertEquals(LOGIN_INFO, SymmetricCrypto.decryptStr("AES", key, LOGIN_INFO_CIPHER_B64))
    }

    @Test
    fun `既有密文（hex 形式）也能解——保留迁移前的 isHex 分支`() {
        // 迁移前 `CryptoUtils.hexToByteArray` 就吃这种输入，且 hex 判定排在 Base64 之前，
        // 顺序反了会把这个分支变成死代码。这里用一个纯 hex 的密文把它钉住。
        assertEquals(LOGIN_INFO, SymmetricCrypto.decryptStr("AES", key, LOGIN_INFO_CIPHER_HEX))
    }

    @Test
    fun `只写 AES 与写全 transformation 等价（默认补 ECB_PKCS5Padding）`() {
        assertEquals(
            SymmetricCrypto.encryptBase64("AES", key, LOGIN_INFO),
            SymmetricCrypto.encryptBase64("AES/ECB/PKCS5Padding", key, LOGIN_INFO),
        )
    }

    @Test
    fun `密钥不对时抛异常，而不是静默返回空串`() {
        // 语义取自 `BaseSource.getLoginInfo`：它把这一层包在 try/catch 里并记 error 日志；
        // 若这里改成返回 ""，调用方会把「解密失败」当成「没有登录信息」，是更坏的失败方式。
        val wrongKey = "fedcba9876543210".toByteArray()
        assertFailsWith<Exception> {
            SymmetricCrypto.decryptStr("AES", wrongKey, LOGIN_INFO_CIPHER_B64)
        }
    }

    private companion object {
        /** 拟真的登录信息：含中文、空格与 Base64 表里的特殊字符，覆盖 UTF-8 与块整数倍长度。 */
        const val LOGIN_INFO = """{"user":"青龙","pwd":"p@ss,w0rd/+"}"""

        const val LOGIN_INFO_CIPHER_B64 =
            "mrbgPIXMR0o+ncUH1C4eFkt+f7CtSnDb/+rJ1aFY+tFelW8GfRDVYeClnzfyJ7g7"

        const val LOGIN_INFO_CIPHER_HEX =
            "9ab6e03c85cc474a3e9dc507d42e1e164b7e7fb0ad4a70dbffeac9d5a158fad" +
                "15e956f067d10d561e0a59f37f227b83b"
    }
}
