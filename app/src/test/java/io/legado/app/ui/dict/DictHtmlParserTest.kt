package io.legado.app.ui.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictHtmlParserTest {

    @Test
    fun preservesStyleAndTextAcrossInlineImage() {
        val document = DictHtmlParser.parse(
            """<span style="color:#f00;text-decoration:underline">拼音<img src="tone.png">释义</span>"""
        )

        val content = document.paragraphs.single().content
        assertEquals("拼音", (content[0] as DictHtmlInline.Text).value)
        assertEquals("tone.png", (content[1] as DictHtmlInline.Image).source)
        assertEquals("释义", (content[2] as DictHtmlInline.Text).value)
        listOf(content[0], content[2]).filterIsInstance<DictHtmlInline.Text>().forEach {
            assertEquals("#f00", it.style.color)
            assertTrue(it.style.underline)
        }
    }

    @Test
    fun keepsNestedLinkOnTextAndImage() {
        val content = DictHtmlParser.parse(
            """<a href="https://example.com"><b>词条</b><img src="icon.png"></a>"""
        ).paragraphs.single().content

        val text = content[0] as DictHtmlInline.Text
        val image = content[1] as DictHtmlInline.Image
        assertTrue(text.style.bold)
        assertEquals("https://example.com", text.link)
        assertEquals("https://example.com", image.link)
    }

    @Test
    fun parsesLegacyFontAndInlineCss() {
        val text = DictHtmlParser.parse(
            """<font color="blue"><u>A</u></font><span style="background-color:#123456;font-style:italic">B</span>"""
        ).paragraphs.single().content.filterIsInstance<DictHtmlInline.Text>()

        assertEquals("blue", text[0].style.color)
        assertTrue(text[0].style.underline)
        assertEquals("#123456", text[1].style.backgroundColor)
        assertTrue(text[1].style.italic)
    }

    @Test
    fun malformedHtmlRetainsReadableContent() {
        val document = DictHtmlParser.parse("<div>前<span style='color:red'>中<img src=x>后</div>")

        val content = document.paragraphs.single().content
        assertEquals(
            "前中",
            content.filterIsInstance<DictHtmlInline.Text>().take(2).joinToString("") { it.value })
        assertEquals("x", content.filterIsInstance<DictHtmlInline.Image>().single().source)
        assertEquals("后", content.filterIsInstance<DictHtmlInline.Text>().last().value)
    }
}
