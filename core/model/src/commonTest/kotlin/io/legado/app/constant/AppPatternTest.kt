package io.legado.app.constant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AppPattern` 的 characterization test。
 *
 * 这组正则被 `:app` 的 37 个文件消费，此前没有任何直接测试。下沉 `:core:model`
 * 之前先固定当前语义，让「搬家」与「改行为」分离：任何一条断言失败都意味着正则口径被改动，
 * 而不是本次迁移的副作用。
 *
 * 用例选取按生产消费频次（splitGroupRegex 21 次、notReadAloudRegex 9 次、bookFileRegex 8 次……），
 * 每条断言都绑定一个真实输入形态，不为覆盖率凑数。
 */
class AppPatternTest {

    @Test
    fun splitGroupRegex_splitsAsciiAndFullWidthSeparators() {
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            "a,b;c，d；e".split(AppPattern.splitGroupRegex),
        )
        // 连续分隔符产生空串，与 java.util.regex 的 split 行为一致。
        assertEquals(listOf("a", "", "b"), "a,,b".split(AppPattern.splitGroupRegex))
    }

    @Test
    fun notReadAloudRegex_matchesPunctuationOnlyAndBlankInput() {
        // 空串不是「不发音段落」：`+` 要求至少一个字符。
        assertFalse(AppPattern.notReadAloudRegex.matches(""))
        assertTrue(AppPattern.notReadAloudRegex.matches("。。。"))
        assertTrue(AppPattern.notReadAloudRegex.matches("  \t　"))
        assertTrue(AppPattern.notReadAloudRegex.matches("——"))
        assertFalse(AppPattern.notReadAloudRegex.matches("第一章"))
        assertFalse(AppPattern.notReadAloudRegex.matches("a。"))
    }

    @Test
    fun bookFileRegex_matchesSupportedExtensionsIgnoringCase() {
        assertTrue(AppPattern.bookFileRegex.matches("我的书.txt"))
        assertTrue(AppPattern.bookFileRegex.matches("book.EPUB"))
        assertTrue(AppPattern.bookFileRegex.matches("a.azw3"))
        assertTrue(AppPattern.bookFileRegex.matches("a.azw"))
        assertFalse(AppPattern.bookFileRegex.matches("book.zip"))
        assertFalse(AppPattern.bookFileRegex.matches("book.txt.bak"))
        assertFalse(AppPattern.bookFileRegex.matches("book"))
    }

    @Test
    fun archiveFileRegex_matchesArchiveExtensionsAtEnd() {
        assertTrue(AppPattern.archiveFileRegex.matches("book.zip"))
        assertTrue(AppPattern.archiveFileRegex.matches("book.CBZ"))
        assertTrue(AppPattern.archiveFileRegex.matches("book.rar"))
        assertTrue(AppPattern.archiveFileRegex.matches("book.7z"))
        assertFalse(AppPattern.archiveFileRegex.matches("book.zipx"))
    }

    @Test
    fun imgPattern_capturesImgSrc() {
        val match = AppPattern.imgPattern.find("<img src=\"https://a.com/b.png\">")
        assertNotNull(match)
        assertEquals("https://a.com/b.png", match.groupValues[1])
        assertNull(AppPattern.imgPattern.find("<div>no image</div>"))
    }

    @Test
    fun authorRegex_stripsAuthorPrefix() {
        assertEquals("鲁迅", AppPattern.authorRegex.replace("作者：鲁迅", ""))
        assertEquals("鲁迅", AppPattern.authorRegex.replace("作者: 鲁迅", ""))
        assertEquals("张三", AppPattern.authorRegex.replace("张三 著", ""))
        // 无作者前缀时保持原样。
        assertEquals("未知", AppPattern.authorRegex.replace("未知", ""))
    }

    @Test
    fun dataUriRegex_capturesBase64Payload() {
        val match = AppPattern.dataUriRegex.find("data:image/png;base64,AAAABBBB")
        assertNotNull(match)
        assertEquals("AAAABBBB", match.groupValues[1])
        assertNull(AppPattern.dataUriRegex.find("https://a.com/b.png"))
    }

    @Test
    fun jsPattern_capturesJsRuleInBothForms() {
        val jsTag = AppPattern.JS_PATTERN.find("<js>var a = 1</js>")
        assertNotNull(jsTag)
        assertEquals("var a = 1", jsTag.groupValues[1])

        val jsPrefix = AppPattern.JS_PATTERN.find("@js:book.name")
        assertNotNull(jsPrefix)
        assertEquals("book.name", jsPrefix.groupValues[2])
    }

    @Test
    fun xmlContentTypeRegex_matchesXmlContentTypes() {
        assertTrue(AppPattern.xmlContentTypeRegex.matches("text/xml"))
        assertTrue(AppPattern.xmlContentTypeRegex.matches("application/rss+xml"))
        assertTrue(AppPattern.xmlContentTypeRegex.matches("application/atom+xml; charset=utf-8"))
        assertFalse(AppPattern.xmlContentTypeRegex.matches("text/html"))
    }

    @Test
    fun titleNumPattern_capturesChapterNumber() {
        val match = AppPattern.titleNumPattern.find("第一百二十三章 序")
        assertNotNull(match)
        assertEquals("第", match.groupValues[1])
        assertEquals("一百二十三", match.groupValues[2])
        assertEquals("章", match.groupValues[3])
    }

    @Test
    fun fileNameRegex_detectsIllegalFileNameChars() {
        assertTrue(AppPattern.fileNameRegex.containsMatchIn("a/b"))
        assertTrue(AppPattern.fileNameRegex.containsMatchIn("a:b"))
        assertTrue(AppPattern.fileNameRegex.containsMatchIn("a.txt"))
        assertFalse(AppPattern.fileNameRegex.containsMatchIn("abc"))
    }

    @Test
    fun domainRegex_capturesHostWithoutPortOrPath() {
        val match = AppPattern.domainRegex.find("https://example.com:8080/a/b")
        assertNotNull(match)
        assertEquals("example.com", match.groupValues[1])
    }

    @Test
    fun useHtmlRegex_matchesAcrossLineBreaks() {
        val match = AppPattern.useHtmlRegex.find("<usehtml>\n<b>x</b>\n</usehtml>")
        assertNotNull(match)
        assertEquals("<usehtml>\n<b>x</b>\n</usehtml>", match.value)
    }

    @Test
    fun lfRegex_andSpaceRegex_keepSimpleLiteralSemantics() {
        assertEquals(2, "\na\nb".split(AppPattern.LFRegex).size - 1)
        assertEquals(listOf("a", "b"), "a  b".split(AppPattern.spaceRegex))
    }
}
