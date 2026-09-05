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
}
