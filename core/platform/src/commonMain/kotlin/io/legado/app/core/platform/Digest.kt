package io.legado.app.core.platform

/**
 * 摘要/哈希契约：共享层不直接用 `java.security.MessageDigest`（JVM-only）。
 * 见 AGENTS.md「KMP 抽取时通过能力契约替换 JVM API（help/crypto/CryptoUtils.kt 与 JCA）」。
 *
 * P1 第三个契约。首个真实消费方是 `:app` `domain/model/readaloud` 的 `SpeechIdentity`
 * （原用 `MessageDigest.getInstance("SHA-256")` 生成 voiceId/analysisId/segmentId 等，
 * 是阻碍该纯逻辑 object 进入 commonMain 的唯一 JVM 依赖）。
 *
 * 只暴露 [sha256]：当前唯一真实消费方只需它；AES/HMAC 等在有真实消费方时再加，
 * 不为对称提前扩接口（AGENTS.md「无调用方抽象」）。
 */
interface Digest {
    /** SHA-256 摘要；同一输入必产生同一 32 字节输出。 */
    fun sha256(data: ByteArray): ByteArray

    /**
     * MD5 摘要；同一输入必产生同一 16 字节输出。
     *
     * ⚠️ 与 [sha256] 不同，MD5 在本仓承担**持久化兼容**职责，不是可替换的实现细节：
     * `nameUuidFromBytes`（UUID v3 名称空间哈希的字节级复刻）用它生成会落库、会参与
     * 查询、且必须跨版本稳定的标识（如 `ai_model_profiles.id = "model_<hex>"`）。
     * 实现必须与 `java.security.MessageDigest.getInstance("MD5")` **逐字节一致**，
     * 不得因为「MD5 不安全」而换算法或换实现——换掉会让既有用户的模型档案 ID 全部漂移，
     * 表现为「升级后模型列表空了」。这里只用它做名称哈希，不承担任何密码学职责。
     *
     * 实现必须返回**调用方拥有的新数组**：`nameUuidFromBytes` 会就地改写其中两个字节
     * （版本位 / 变体位）。不得返回缓存数组或共享缓冲。
     */
    fun md5(data: ByteArray): ByteArray
}
