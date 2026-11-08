package io.legado.app.model.localBook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownToHtmlTest {

    @Test
    fun headingsEmitLevelsAndStripClosingHashes() {
        val html = MarkdownToHtml.convert("# Title\n\n## Sub ##\n\n### Deep")
        assertTrue(html.contains("<h1>Title</h1>"))
        assertTrue(html.contains("<h2>Sub</h2>"))
        assertTrue(html.contains("<h3>Deep</h3>"))
        assertFalse(html.contains("#"))
    }

    @Test
    fun inlineMarkupMapsToSemanticTags() {
        val html = MarkdownToHtml.convert("正文 **bold** *em* ~~gone~~ `code` 文本")
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em>em</em>"))
        assertTrue(html.contains("<del>gone</del>"))
        assertTrue(html.contains("<code>code</code>"))
        assertFalse(html.contains("**"))
    }

    @Test
    fun listItemsWrapParagraphsForPlainTextChannel() {
        val html = MarkdownToHtml.convert("- one\n- two\n\n1. first\n2. second")
        assertTrue(html.contains("<li><p>one</p></li>"))
        assertTrue(html.contains("<li><p>two</p></li>"))
        assertTrue(html.contains("<ol>"))
        assertTrue(html.contains("<li><p>first</p></li>"))
    }

    @Test
    fun orderedListKeepsStartNumber() {
        val html = MarkdownToHtml.convert("3. third\n4. fourth")
        assertTrue(html.contains("<ol start=\"3\">"))
    }

    @Test
    fun blockquoteAndRuleEmit() {
        val html = MarkdownToHtml.convert("> quoted line\n\n---")
        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("quoted line"))
        assertTrue(html.contains("<hr/>"))
    }

    @Test
    fun codeFenceEscapesContent() {
        val html = MarkdownToHtml.convert("```\n<script>alert(1)</script>\n```")
        assertTrue(html.contains("<pre><code>"))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun imageAndLinkKeepSourceVerbatim() {
        val html = MarkdownToHtml.convert("![看图](img/pic.png) 和 [链接](https://example.com/x?a=1&b=2)")
        assertTrue(html.contains("<img src=\"img/pic.png\""))
        assertTrue(html.contains("alt=\"看图\""))
        assertTrue(html.contains("href=\"https://example.com/x?a=1&amp;b=2\""))
    }

    @Test
    fun tableFlattensToTextRows() {
        val html = MarkdownToHtml.convert("H1 | H2\n--- | ---\na1 | b1")
        assertFalse(html.contains("<table"))
        assertTrue(html.contains("|"))
        assertTrue(html.contains("<p>"))
        assertTrue(html.contains("H1"))
        assertTrue(html.contains("a1"))
    }

    @Test
    fun frontMatterIsStrippedBeforeParsing() {
        val html = MarkdownToHtml.convert("---\ntitle: 私人笔记\nauthor: 我\n---\n\n# 正文开始")
        assertFalse(html.contains("title:"))
        assertTrue(html.contains("<h1>正文开始</h1>"))
    }

    @Test
    fun blankMarkdownProducesEmptyHtml() {
        assertEquals("", MarkdownToHtml.convert(""))
        assertEquals("", MarkdownToHtml.convert("   \n  \n"))
    }

    @Test
    fun inlineRawHtmlPassesThrough() {
        //与 EPUB 的 HTML 通道一致：行内原生 HTML 原样放行，由既有清洗/呈现逻辑处理
        val html = MarkdownToHtml.convert("文字<br/>和<img src=\"p/x.png\" width=\"10\"/>结尾")
        assertTrue(html.contains("<br/>"))
        assertTrue(html.contains("<img src=\"p/x.png\" width=\"10\"/>"))
    }

    @Test
    fun plainTextChannelDerivesReadableTextWithoutMarkdownSyntax() {
        val html = MarkdownToHtml.convert("# 第一章 开端\n\n他**缓缓**睁开眼。\n\n- 甲\n- 乙")
        val plain = LocalBookSemanticHtml.plainText(html)
        assertTrue(plain.contains("第一章 开端"))
        assertTrue(plain.contains("他缓缓睁开眼。"))
        assertFalse(plain.contains("**"))
        assertFalse(plain.contains("<h1>"))
        //列表项必须各自成段，不能黏成一行
        val lines = plain.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        assertTrue(lines.contains("甲"))
        assertTrue(lines.contains("乙"))
        assertTrue(lines.indexOf("甲") < lines.indexOf("乙"))
    }

    @Test
    fun headingEndingWithHashIsNotTrimmed() {
        val html = MarkdownToHtml.convert("# 使用 C#\n\n## 序章 ##")
        assertTrue(html.contains("<h1>使用 C#</h1>"))
        assertTrue(html.contains("<h2>序章</h2>"))
    }

    @Test
    fun unsafeHtmlBlocksAreRemovedForBothChannels() {
        val html = MarkdownToHtml.convert("正文\n\n<script>alert(1)</script>\n\n<style>.x{color:red}</style>\n\n结尾")
        assertFalse(html.contains("<script", ignoreCase = true))
        assertFalse(html.contains("<style", ignoreCase = true))
        val plain = LocalBookSemanticHtml.plainText(html)
        assertTrue(plain.contains("正文"))
        assertTrue(plain.contains("结尾"))
        assertFalse(plain.contains(".x{color:red}"))
        assertFalse(plain.contains("alert(1)"))
    }

    @Test
    fun inlineScriptTagsDoNotLeakIntoBothChannels() {
        val html = MarkdownToHtml.convert("文字<script>alert(1)</script>结尾")
        assertFalse(html.contains("<script", ignoreCase = true))
        assertFalse(html.contains("</script", ignoreCase = true))
        val plain = LocalBookSemanticHtml.plainText(html)
        assertFalse(plain.contains("<script", ignoreCase = true))
        assertTrue(plain.contains("文字"))
        assertTrue(plain.contains("结尾"))
    }
}
