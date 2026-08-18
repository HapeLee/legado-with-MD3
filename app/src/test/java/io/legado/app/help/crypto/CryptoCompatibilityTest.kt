package io.legado.app.help.crypto

import io.legado.app.utils.nameUuidFromBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoCompatibilityTest {

    @Test
    fun `MD5 与标准向量一致`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", digest("MD5", ByteArray(0)).toHexString())
        assertEquals("900150983cd24fb0d6963f7d28e17f72", digest("MD5", "abc".toByteArray()).toHexString())
    }

    @Test
    fun `SHA-256 与标准向量一致`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            digest("SHA-256", "abc".toByteArray()).toHexString()
        )
    }

    @Test
    fun `HMAC-SHA256 与标准向量一致`() {
        val expected = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"
        val actual = hmac(
            "HmacSHA256",
            "key".toByteArray(),
            "The quick brown fox jumps over the lazy dog".toByteArray()
        ).toHexString()
        assertEquals(expected, actual)
    }

    @Test
    fun `hex base64 编解码往返`() {
        val bytes = byteArrayOf(0x00, 0x01, 0x7f, -0x01, 0x41, 0x42)
        assertEquals("00017fff4142", bytes.toHexString())
        assertTrue(bytes.contentEquals("00017fff4142".hexToByteArray()))

        val base64 = bytes.toBase64()
        assertEquals("AAF//0FC", base64)
        assertTrue(bytes.contentEquals(base64.base64ToByteArray()))
        assertTrue(bytes.contentEquals("AAF // 0FC".base64ToByteArray()))
        assertTrue(bytes.contentEquals("AAF__0FC".base64ToByteArray()))
    }

    @Test
    fun `AES ECB 加密解密往返`() {
        val key = "1234567890123456".toByteArray()
        val data = "hello legado".toByteArray()
        val crypto = SymmetricCryptoAndroid("AES", key)
        assertTrue(data.contentEquals(crypto.decrypt(crypto.encrypt(data))))
        assertTrue(data.contentEquals(crypto.decryptStr(crypto.encryptBase64(data)).toByteArray()))
        assertTrue(data.contentEquals(crypto.decrypt(crypto.encryptHex(data).hexToByteArray())))
    }

    @Test
    fun `AES CBC 带 IV 往返`() {
        val key = "1234567890123456".toByteArray()
        val iv = "abcdefghijklmnop".toByteArray()
        val crypto = SymmetricCryptoAndroid("AES/CBC/PKCS5Padding", key).setIv(iv)
        val data = "带中文的明文".toByteArray()
        assertTrue(data.contentEquals(crypto.decrypt(crypto.encrypt(data))))
    }

    @Test
    fun `AES decrypt 自动识别 hex 与 base64`() {
        val key = "1234567890123456".toByteArray()
        val data = "密文数据".toByteArray()
        val crypto = SymmetricCryptoAndroid("AES", key)
        assertTrue(data.contentEquals(crypto.decrypt(crypto.encryptHex(data))))
        assertTrue(data.contentEquals(crypto.decrypt(crypto.encryptBase64(data))))
    }

    @Test
    fun `DES 超长密钥静默截断兼容`() {
        val crypto = SymmetricCryptoAndroid("DES", "1234567890123456".toByteArray())
        val data = "des data".toByteArray()
        assertTrue(data.contentEquals(crypto.decrypt(crypto.encrypt(data))))
    }

    @Test
    fun `nameUuidFromBytes 与 java UUID v3 一致`() {
        val input = "provider123:model-abc".toByteArray()
        val expected = java.util.UUID.nameUUIDFromBytes(input).toString()
        assertEquals(expected, nameUuidFromBytes(input).toString())
    }

    @Test
    fun `DESede 超长密钥截断兼容`() {
        val crypto = SymmetricCryptoAndroid("DESede", "12345678901234567890123456789012".toByteArray())
        val data = "3des data".toByteArray()
        assertTrue(data.contentEquals(crypto.decrypt(crypto.encrypt(data))))
    }
}
