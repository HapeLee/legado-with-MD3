package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [HtmlParser] 契约测试基类：每个 target（androidHostTest / desktopTest）提供 [createParser]。
 *
 * 断言刻意围绕 `:app` `HtmlFormatter` 真实依赖的语义，而不是 jsoup 的全部能力：
 * D2 是双轨而不是全量替换，契约只需证明「窄接口能原样承载 jsoup 的解析行为」。
 */
abstract class HtmlParserContractTest {

    abstract fun createParser(): HtmlParser

    @Test
    fun `parseBodyFragment always yields a body even for blank input`() {
        val parser = createParser()
        assertEquals("", parser.parseBodyFragment("").body().html())
        assertEquals("", parser.parseBodyFragment("   ").body().html())
    }

    @Test
    fun `body html excludes the body tag itself`() {
        val body = createParser().parseBodyFragment("<p>text</p>").body()
        val html = body.html()
        assertTrue(html.contains("<p>text</p>"), "实际: $html")
        assertFalse(html.contains("<body"), "html() 只序列化内部 HTML，不含自身标签: $html")
    }

    @Test
    fun `selecting script style noscript removes them together with their contents`() {
        val parser = createParser()
        val document = parser.parseBodyFragment(
            "<style>.x { color: red; }</style><p>keep</p><script>alert(1)</script>",
        )
        document.setPrettyPrint(false)
        document.body().select("script, style, noscript").forEach { it.remove() }

        val html = document.body().html()
        assertEquals("<p>keep</p>", html)
        assertFalse(html.contains("color: red"), "style 的内容必须一起移除")
        assertFalse(html.contains("alert(1)"), "script 的内容必须一起移除")
    }

    @Test
    fun `prettyPrint false keeps serialization on one line`() {
        val document = createParser().parseBodyFragment("<div><p>a</p><p>b</p></div>")
        document.setPrettyPrint(false)
        assertEquals("<div><p>a</p><p>b</p></div>", document.body().html())
    }

    @Test
    fun `prettyPrint true inserts block level newlines`() {
        val document = createParser().parseBodyFragment("<div><p>a</p><p>b</p></div>")
        document.setPrettyPrint(true)
        assertTrue(
            document.body().html().contains("\n"),
            "prettyPrint 是行为开关：开启后序列化会插入换行，消费方必须显式关闭",
        )
    }

    @Test
    fun `select returns an empty list when nothing matches`() {
        val body = createParser().parseBodyFragment("<p>a</p>").body()
        assertTrue(body.select("table").isEmpty())
    }

    @Test
    fun `select with a comma separated query matches the union`() {
        val body = createParser()
            .parseBodyFragment("<p>a</p><div>b</div><span>c</span>")
            .body()
        assertEquals(2, body.select("p, div").size)
    }

    @Test
    fun `remove detaches only the selected element`() {
        val body = createParser().parseBodyFragment("<p>keep</p><p>drop</p>").body()
        body.select("p").last().remove()
        val html = body.html()
        assertTrue(html.contains("keep"), "实际: $html")
        assertFalse(html.contains("drop"), "实际: $html")
    }
}
