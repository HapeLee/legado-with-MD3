package io.legado.app.model.analyzeRule

import io.legado.app.core.platform.JsonCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 验证 RuleData/CustomUrl 下沉后用 JsonCodec 替换 GSON 后，行为与原 GSON 路径一致：
 * 字节级输出（prettyPrinting + disableHtmlEscaping）+ 数字修复语义。
 */
class RuleDataAndCustomUrlTest {

    @Test
    fun `ruleData variable serialization is null when empty`() {
        assertNull(RuleData().getVariable())
    }

    @Test
    fun `ruleData variable serialization round trips via decodeStringMap`() {
        val data = RuleData()
        data.putVariable("key", "value")
        val json = data.getVariable()
        assertEquals(mapOf("key" to "value"), JsonCodec.decodeStringMap(json))
    }

    @Test
    fun `customUrl parses trailing json params with int fix`() {
        val url = CustomUrl("https://example.com/book, {\"page\": 2}")
        assertEquals("https://example.com/book", url.getUrl())
        // 数字修复：整数 2 解析为 Long 2L。
        assertEquals(mapOf("page" to 2L), url.getAttr())
    }

    @Test
    fun `customUrl without params keeps url intact`() {
        val url = CustomUrl("https://example.com/book")
        assertEquals("https://example.com/book", url.getUrl())
        assertEquals(emptyMap(), url.getAttr())
        assertEquals("https://example.com/book", url.toString())
    }

    @Test
    fun `customUrl toString re-encodes params in gson pretty format`() {
        val url = CustomUrl("https://example.com/book")
        url.putAttribute("page", 2)
        // 字节级对齐 Gson setPrettyPrinting：2 空格缩进 + ": " 分隔。
        assertEquals("https://example.com/book,{\n  \"page\": 2\n}", url.toString())
    }
}
