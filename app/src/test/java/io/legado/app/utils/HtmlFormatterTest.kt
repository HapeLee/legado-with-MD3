package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlFormatterTest {

    @Test
    fun formatDisplayText_removesScriptAndStyleContents() {
        val result = HtmlFormatter.formatDisplayText(
            """
                <style>.intro { color: red; }</style>
                <p>第一段</p>
                <script>window.alert('bad')</script>
                <div>第二段</div>
            """.trimIndent()
        )

        assertEquals("　　第一段\n　　第二段", result)
        assertTrue(result.contains("第一段"))
        assertTrue(result.contains("第二段"))
        assertFalse(result.contains("color: red"))
        assertFalse(result.contains("window.alert"))
    }

    @Test
    fun formatDisplayText_keepsPlainText() {
        assertEquals("　　普通简介", HtmlFormatter.formatDisplayText("普通简介"))
    }

    @Test
    fun formatDisplayText_dropsBookMetaLines() {
        val result = HtmlFormatter.formatDisplayText(
            "书名：某某传<br>作者：张三<br>【分类】玄幻<br>最新章节：第一千章 大结局<br>简介：这是正文第一段<br>这是正文第二段"
        )

        assertEquals("　　这是正文第一段\n　　这是正文第二段", result)
    }

    @Test
    fun formatDisplayText_keepsContentStartingWithMetaWord() {
        assertEquals(
            "　　作者的话：这本书写了三年",
            HtmlFormatter.formatDisplayText("作者的话：这本书写了三年")
        )
    }

    @Test
    fun formatDisplayText_keepsPlainTextLineBreaks() {
        assertEquals(
            "　　第一段\n　　第二段",
            HtmlFormatter.formatDisplayText("第一段\n第二段")
        )
    }
}
