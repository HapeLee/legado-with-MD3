package io.legado.app.core.platform

import kotlin.uuid.Uuid

/**
 * UUID v3（MD5 名称空间哈希）的「名称 → UUID」复刻（M4-5b）。
 *
 * 语义与 `java.util.UUID.nameUUIDFromBytes(bytes)` **逐字节一致**：取 [Digest.md5] 的 16 字节，
 * 按 RFC 4122 §4.3 把第 7 字节高 4 位置为版本号 3（`(b[6] and 0x0F) or 0x30`）、把第 9 字节
 * 高 2 位置为 IETF 变体（`(b[8] and 0x3F) or 0x80`），再交 [Uuid.fromByteArray]。
 *
 * 位置：原实现住 `:app/utils/UuidExtensions.kt`，直接调 `java.security.MessageDigest`
 * （JVM-only）⇒ 挡在 AI profile 域下沉的路上。本函数把**算法**留在共享层、把 **MD5** 交给
 * [Digest] 契约（调用方注入平台实现），于是它本身是 commonMain 可编译的纯函数。
 * 这也是它不做成 `expect/actual` 的理由：函数体不含平台 API，只有它的依赖才是平台能力。
 *
 * ⚠️ **这是持久化兼容边界，不是随手可改的工具函数**：调用方（AI profile 域）把结果落库成
 * `ai_model_profiles.id = "model_<hex>"`，并靠它实现「同一 providerId + 同一 modelId ⇒ 同一档案」
 * 的稳定标识。任何改动（换摘要算法、改位运算、改 hex 大小写）都会让既有用户的档案 ID 全部漂移，
 * 表现为「升级后模型列表空了」或「重复建了一整套档案」。
 *
 * ⚠️ [Digest.md5] 必须返回**调用方拥有的新数组**——本函数会就地改写其中两个字节
 * （见 [Digest] 的契约说明）。
 */
fun nameUuidFromBytes(bytes: ByteArray, digest: Digest): Uuid {
    val md5 = digest.md5(bytes)
    md5[6] = (md5[6].toInt() and 0x0F or 0x30).toByte()
    md5[8] = (md5[8].toInt() and 0x3F or 0x80).toByte()
    return Uuid.fromByteArray(md5)
}
