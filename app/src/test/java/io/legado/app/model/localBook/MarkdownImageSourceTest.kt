package io.legado.app.model.localBook

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * 本地 Markdown 的图片地址来自文档内容：任何带规则语法的地址都必须在进入下载前被拒绝，
 * 否则 AnalyzeUrl 会把 `<js>`/`{{}}` 当脚本执行。
 */
class MarkdownImageSourceTest {

    @Test
    fun rejectsScriptBearingAddresses() {
        val attacks = listOf(
            "<js>java.lang.Runtime.getRuntime().availableProcessors()</js>",
            "http://example.com/a.png<js>1</js>",
            "http://example.com/{{cookie.getCookie('k')}}.png",
            "data:text/html,<js>1</js>",
            "data:image/svg+xml,{{1+1}}",
        )
        attacks.forEach { src ->
            assertEquals(
                "不得把含脚本语法的地址当成可下载图片: $src",
                MarkdownImageSource.Unsupported,
                MarkdownImageSource.parse(src),
            )
        }
    }

    @Test
    fun acceptsPlainRemoteAddressAndNormalizesIt() {
        val parsed = MarkdownImageSource.parse("  https://example.com/img/a.png?x=1  ")
        assertTrue(parsed is MarkdownImageSource.Http)
        assertEquals("https://example.com/img/a.png?x=1", (parsed as MarkdownImageSource.Http).url)
    }

    @Test
    fun encodesUnescapedSpacesInRemoteFileName() {
        val parsed = MarkdownImageSource.parse("http://example.com/a b.png")
        assertTrue(parsed is MarkdownImageSource.Http)
        assertEquals("http://example.com/a%20b.png", (parsed as MarkdownImageSource.Http).url)
    }

    @Test
    fun decodesBase64DataUri() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val src = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
        val parsed = MarkdownImageSource.parse(src)
        assertTrue(parsed is MarkdownImageSource.DataUri)
        assertArrayEquals(bytes, (parsed as MarkdownImageSource.DataUri).bytes)
    }

    @Test
    fun rejectsRelativePathAndBlankSource() {
        assertEquals(MarkdownImageSource.Unsupported, MarkdownImageSource.parse(""))
        assertEquals(MarkdownImageSource.Unsupported, MarkdownImageSource.parse("images/a.png"))
        assertEquals(MarkdownImageSource.Unsupported, MarkdownImageSource.parse("javascript:alert(1)"))
    }
}
