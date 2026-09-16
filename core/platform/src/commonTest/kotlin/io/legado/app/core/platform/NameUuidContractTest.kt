package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `nameUuidFromBytes` 的契约测试基类：每个 target（androidHostTest / desktopTest）
 * 提供 [createDigest]（真 JCA 实现），于是「算法 + 真 MD5」这条链路在两个 target 上都被验证。
 *
 * 为什么是抽象基类：函数体本身住 commonMain，但它的依赖（`JcaDigest`）只在平台源集里，
 * commonTest 够不着 ⇒ 与 `DigestContractTest` 同模式。
 *
 * 向量全部取自**独立实现**（Python `uuid` / `hashlib` 复刻 RFC 4122 §4.3），不是本仓的输出。
 */
abstract class NameUuidContractTest {

    abstract fun createDigest(): Digest

    /**
     * 主向量：AI profile 域的真实用法 —— `stableModelId` 把 `"$providerId:$modelId"` 交给
     * 本函数，输出再被拼成 `model_<hex>`。这里用输入 `"provider123:model-abc"`。
     */
    @Test
    fun `matches the independent UUID v3 vector for a colon joined composite`() {
        assertEquals(
            "143a836f-f1b1-3b9c-b09e-5a86ef00b88d",
            nameUuidFromBytes("provider123:model-abc".toByteArray(), createDigest()).toString(),
        )
    }

    /**
     * 位运算专测：`MD5("")` = `d41d8cd98f00b204e9800998ecf8427e`，按 RFC 4122 改写后
     * 第 7 字节 `0xb2 → 0x32`（版本 3）、第 9 字节 `0xe9 → 0xa9`（变体 10xx）。
     * 空输入让「输入内容」这一维退化成常量，从而把断言集中在位运算上。
     */
    @Test
    fun `rewrites the version and variant bits of an empty name`() {
        val uuid = nameUuidFromBytes(ByteArray(0), createDigest()).toString()
        assertEquals("d41d8cd9-8f00-3204-a980-0998ecf8427e", uuid)
    }

    @Test
    fun `always reports version 3 and the IETF variant`() {
        val uuid = nameUuidFromBytes("provider123:model-abc".toByteArray(), createDigest()).toString()
        // 8-4-4-4-12：下标 14 是版本 nibble，19 是变体 nibble。
        assertEquals('3', uuid[14], "版本位必须是 3（MD5 名称空间哈希）")
        // ⚠️ 变体位是「高 2 位为 10」，即第 9 字节落在 0x80..0xBF ⇒ hex nibble 是
        // {8, 9, a, b} 之一而**不是固定值**（本向量实际是 'b'）。
        // 第一版我写成了 assertEquals('8', uuid[19]) —— 那会把正确实现判错。
        assertTrue(uuid[19] in "89ab", "变体位必须是 IETF（10xx），实际 '${uuid[19]}'")
        // 形状本身也是契约的一部分。
        assertEquals(36, uuid.length)
        assertEquals('-', uuid[8])
        assertEquals('-', uuid[13])
        assertEquals('-', uuid[18])
        assertEquals('-', uuid[23])
    }

    @Test
    fun `is deterministic and distinguishes different names`() {
        val d = createDigest()
        val a1 = nameUuidFromBytes("openai:gpt-4o".toByteArray(), d).toString()
        val a2 = nameUuidFromBytes("openai:gpt-4o".toByteArray(), d).toString()
        val b = nameUuidFromBytes("openai:gpt-4o-mini".toByteArray(), d).toString()
        assertEquals(a1, a2, "同一输入必须产生同一 UUID")
        assertEquals("5efbd563-3e10-3c39-bf09-0f122d16081e", a1)
        assertNotEquals(a1, b)
    }

    /**
     * 分隔符敏感：`"openai:gpt-4o"` 与 `"openaigpt-4o"` 必须得到不同 UUID。
     * 这条钉住的是**调用方的拼接规则**别再变（改分隔符等于改全部既有 ID）。
     */
    @Test
    fun `is sensitive to the separating colon`() {
        val d = createDigest()
        val withColon = nameUuidFromBytes("openai:gpt-4o".toByteArray(), d).toString()
        val withoutColon = nameUuidFromBytes("openaigpt-4o".toByteArray(), d).toString()
        assertNotEquals(withColon, withoutColon)
        // 两者的确切取值都钉住，避免「互相不等就算过」。
        assertEquals("5efbd563-3e10-3c39-bf09-0f122d16081e", withColon)
        assertEquals("3b7de20e-c60f-3acb-a956-ee6c9bf672d3", withoutColon)
    }
}
