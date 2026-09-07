package io.legado.app.core.platform

/**
 * 对称加解密契约（P4-e BaseSource 下沉第 3 刀）。
 *
 * 取代 app 侧 `help.crypto.SymmetricCryptoAndroid` 在 BaseSource 里的用法，让
 * commonMain 能加密存储登录信息（用户信息 AES 加密落库）而不依赖 JVM-only 的 JCA。
 *
 * BaseSource 只用 `SymmetricCryptoAndroid("AES", key).encryptBase64(str)` 和
 * `.decryptStr(str)` 两个调用，这里收敛为 [encryptBase64] / [decryptStr]，
 * 算法（"AES"）和密钥由调用方传入（AGENTS.md「无调用方抽象」）。
 *
 * **为什么是 interface + composition root 注入**：虽然 JCA 是平台原语，但当前唯一
 * 生产实现是 `:app` 的 `SymmetricCryptoAndroid`（带 isHex/hexToByteArray/toBase64 等
 * `:app` 工具依赖），`core:*` 不能反向依赖 `:app`，故走接口注入。
 *
 * **未注入时显式失败**：[SymmetricCryptoProvider.current] 未安装时抛异常。
 */
interface SymmetricCrypto {

    /**
     * 用 `algorithm`（如 "AES"）+ `key`（16/24/32 字节）加密 `data` 并 Base64 编码。
     *
     * 对齐 `SymmetricCryptoAndroid(algorithm, key).encryptBase64(data: String): String`。
     */
    fun encryptBase64(algorithm: String, key: ByteArray, data: String): String

    /**
     * 用 `algorithm` + `key` 解密 Base64 字符串 `data`（含 hex 兼容）为明文。
     *
     * 对齐 `SymmetricCryptoAndroid(algorithm, key).decryptStr(data: String): String`。
     */
    fun decryptStr(algorithm: String, key: ByteArray, data: String): String
}

/**
 * [SymmetricCrypto] 的注入点。模式同 [KeyValueStoreProvider]。
 */
object SymmetricCryptoProvider {

    @Volatile
    private var delegate: SymmetricCrypto? = null

    fun install(crypto: SymmetricCrypto) {
        delegate = crypto
    }

    fun uninstall() {
        delegate = null
    }

    val isInstalled: Boolean get() = delegate != null

    val current: SymmetricCrypto
        get() = delegate ?: error(
            "SymmetricCrypto 未安装：请在应用 composition root 调用 " +
                "SymmetricCryptoProvider.install(...) 注入平台实现（Android 为 " +
                "help.crypto.SymmetricCryptoAndroid）。"
        )
}
