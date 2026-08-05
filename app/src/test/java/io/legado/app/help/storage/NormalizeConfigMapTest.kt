package io.legado.app.help.storage

import io.legado.app.constant.PreferKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NormalizeConfigMapTest {

    private fun normalize(
        map: Map<String, Any?>,
        keyIsNotIgnore: (String) -> Boolean = { true },
        decryptWebDavPassword: (String) -> String? = { null },
        hasLocalWebDavPassword: Boolean = false,
    ) = normalizeConfigMap(map, keyIsNotIgnore, decryptWebDavPassword, hasLocalWebDavPassword)

    @Test
    fun `基础类型原样保留且跳过 null`() {
        val result = normalize(
            mapOf(
                "int" to 1,
                "boolean" to true,
                "long" to 2L,
                "float" to 3f,
                "string" to "s",
                "nullKey" to null,
            )
        )
        assertEquals(
            mapOf("int" to 1, "boolean" to true, "long" to 2L, "float" to 3f, "string" to "s"),
            result
        )
    }

    @Test
    fun `Double 转为 Float`() {
        val result = normalize(mapOf("fontScale" to 1.5))
        assertEquals(1.5f, result["fontScale"])
    }

    @Test
    fun `忽略键被过滤`() {
        val result = normalize(
            mapOf("keep" to 1, "drop" to 2),
            keyIsNotIgnore = { it != "drop" },
        )
        assertEquals(mapOf("keep" to 1), result)
    }

    @Test
    fun `webDavPassword 解密成功用解密值`() {
        val result = normalize(
            mapOf(PreferKey.webDavPassword to "encrypted"),
            decryptWebDavPassword = { if (it == "encrypted") "plain" else null },
            hasLocalWebDavPassword = true,
        )
        assertEquals("plain", result[PreferKey.webDavPassword])
    }

    @Test
    fun `webDavPassword 解密失败且本地无密码时保留原文`() {
        val result = normalize(
            mapOf(PreferKey.webDavPassword to "rawPassword"),
            decryptWebDavPassword = { null },
            hasLocalWebDavPassword = false,
        )
        assertEquals("rawPassword", result[PreferKey.webDavPassword])
    }

    @Test
    fun `webDavPassword 解密失败且本地已有密码时丢弃`() {
        val result = normalize(
            mapOf(PreferKey.webDavPassword to "rawPassword"),
            decryptWebDavPassword = { null },
            hasLocalWebDavPassword = true,
        )
        assertFalse(result.containsKey(PreferKey.webDavPassword))
    }

    @Test
    fun `旧版 bookInfoBackgroundBlur 补写两个新背景键`() {
        val result = normalize(mapOf(PreferKey.bookInfoBackgroundBlur to "blur"))
        assertEquals("blur", result[PreferKey.bookInfoBackgroundBlur])
        assertEquals("blur", result[PreferKey.bookInfoNetworkCoverBackground])
        assertEquals("blur", result[PreferKey.bookInfoDefaultCoverBackground])
    }

    @Test
    fun `备份已含新背景键时不覆盖`() {
        val result = normalize(
            mapOf(
                PreferKey.bookInfoBackgroundBlur to "blur",
                PreferKey.bookInfoNetworkCoverBackground to "network",
            )
        )
        assertEquals("network", result[PreferKey.bookInfoNetworkCoverBackground])
        assertEquals("blur", result[PreferKey.bookInfoDefaultCoverBackground])
    }

    @Test
    fun `新背景键被忽略时不补写`() {
        val result = normalize(
            mapOf(PreferKey.bookInfoBackgroundBlur to "blur"),
            keyIsNotIgnore = { it != PreferKey.bookInfoDefaultCoverBackground },
        )
        assertEquals("blur", result[PreferKey.bookInfoNetworkCoverBackground])
        assertFalse(result.containsKey(PreferKey.bookInfoDefaultCoverBackground))
    }

    @Test
    fun `bookInfoBackgroundBlur 本身被忽略时不拆键`() {
        val result = normalize(
            mapOf(PreferKey.bookInfoBackgroundBlur to "blur"),
            keyIsNotIgnore = { it != PreferKey.bookInfoBackgroundBlur },
        )
        assertFalse(result.containsKey(PreferKey.bookInfoNetworkCoverBackground))
        assertFalse(result.containsKey(PreferKey.bookInfoDefaultCoverBackground))
    }
}
