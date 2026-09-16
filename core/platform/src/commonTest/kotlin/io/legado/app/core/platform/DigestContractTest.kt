package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Digest 契约测试基类：每个 target（androidHostTest / desktopTest）提供 [createDigest]。
 * 用确定性已知向量（NIST SHA-256 test vector）证明 JcaDigest 在该 target 满足契约。
 */
abstract class DigestContractTest {

    abstract fun createDigest(): Digest

    private fun ByteArray.toHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    @Test
    fun `sha256 of empty input is the known NIST vector`() {
        val d = createDigest()
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            d.sha256(ByteArray(0)).toHex(),
        )
    }

    @Test
    fun `sha256 of abc is the known NIST vector`() {
        val d = createDigest()
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            d.sha256("abc".toByteArray()).toHex(),
        )
    }

    @Test
    fun `sha256 produces 32 bytes and is deterministic`() {
        val d = createDigest()
        val a = d.sha256("hello".toByteArray())
        val b = d.sha256("hello".toByteArray())
        assertEquals(32, a.size)
        assertTrue(a.contentEquals(b), "sha256 必须对相同输入确定性输出")
    }

    @Test
    fun `sha256 distinguishes different inputs`() {
        val d = createDigest()
        val a = d.sha256("a".toByteArray())
        val b = d.sha256("b".toByteArray())
        assertFalse(a.contentEquals(b), "不同输入应产生不同摘要")
    }

    // ---- md5（M4-5a：AI profile 域下沉带来 `nameUuidFromBytes` 这个首个真实消费方）----
    // 向量取自 RFC 1321 / Python hashlib 两个独立实现，不是本仓实现的输出。

    @Test
    fun `md5 of empty input is the known RFC 1321 vector`() {
        val d = createDigest()
        // MD5("") = d41d8cd98f00b204e9800998ecf8427e
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", d.md5(ByteArray(0)).toHex())
    }

    @Test
    fun `md5 of abc is the known RFC 1321 vector`() {
        val d = createDigest()
        // MD5("abc") = 900150983cd24fb0d6963f7d28e17f72
        assertEquals("900150983cd24fb0d6963f7d28e17f72", d.md5("abc".toByteArray()).toHex())
    }

    @Test
    fun `md5 produces 16 bytes and matches the independent vector for hello`() {
        val d = createDigest()
        // MD5("hello") = 5d41402abc4b2a76b9719d911017c592（Python hashlib 独立算出）
        assertEquals("5d41402abc4b2a76b9719d911017c592", d.md5("hello".toByteArray()).toHex())
        assertEquals(16, d.md5("hello".toByteArray()).size)
    }

    @Test
    fun `md5 distinguishes different inputs`() {
        val d = createDigest()
        // 这两个值必须与各自的独立向量一致，而不是互相不等就算过。
        assertEquals("0cc175b9c0f1b6a831c399e269772661", d.md5("a".toByteArray()).toHex())
        assertEquals("92eb5ffee6ae2fec3ad71c777531578f", d.md5("b".toByteArray()).toHex())
    }
}
