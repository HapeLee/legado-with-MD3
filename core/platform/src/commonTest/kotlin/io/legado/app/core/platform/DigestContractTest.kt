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
}
