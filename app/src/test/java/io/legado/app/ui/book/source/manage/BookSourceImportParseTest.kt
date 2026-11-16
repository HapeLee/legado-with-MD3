package io.legado.app.ui.book.source.manage

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * 导入解析的行为基线：大书源包必须逐元素读取，不能退回到「整串读入 + 整棵树」。
 */
class BookSourceImportParseTest {

    @Test
    fun `array document is parsed element by element`() {
        val document = parse(
            """[
              {"bookSourceUrl":"https://a.example","bookSourceName":"A","ruleSearch":{"bookList":"//a"}},
              {"bookSourceUrl":"https://b.example","bookSourceName":"B"}
            ]"""
        )
        val sources = (document as BookSourceImportDocument.Sources).sources
        assertEquals(2, sources.size)
        assertEquals("https://a.example", sources[0].bookSourceUrl)
        assertEquals("A", sources[0].bookSourceName)
        assertEquals("//a", sources[0].ruleSearch?.bookList)
        assertEquals("https://b.example", sources[1].bookSourceUrl)
    }

    @Test
    fun `null element is skipped`() {
        val sources = parse("""[null,{"bookSourceUrl":"https://a.example"}]""")
        assertEquals(
            listOf("https://a.example"),
            (sources as BookSourceImportDocument.Sources).sources.map { it.bookSourceUrl }
        )
    }

    @Test
    fun `single source object is supported`() {
        val sources = parse(
            """{"bookSourceUrl":"https://a.example","bookSourceName":"A"}"""
        ) as BookSourceImportDocument.Sources
        assertEquals(1, sources.sources.size)
        assertEquals("A", sources.sources[0].bookSourceName)
    }

    @Test
    fun `sourceUrls object yields remote urls`() {
        val document = parse(
            """{"sourceUrls":["https://a.example/s.json","https://b.example/s.json"]}"""
        ) as BookSourceImportDocument.RemoteUrls
        assertEquals(
            listOf("https://a.example/s.json", "https://b.example/s.json"),
            document.urls
        )
    }

    @Test
    fun `empty sourceUrls yields no remote urls`() {
        val document = parse("""{"sourceUrls":[]}""") as BookSourceImportDocument.RemoteUrls
        assertEquals(emptyList<String>(), document.urls)
    }

    @Test
    fun `sourceUrls that is not an array is a format error`() {
        assertFormatError("""{"sourceUrls":"https://a.example/s.json"}""")
    }

    @Test
    fun `blank document is a format error`() {
        assertFormatError("  \n\t ")
    }

    @Test
    fun `truncated array is a format error`() {
        assertFormatError("""[{"bookSourceUrl":"https://a.example"}""")
    }

    @Test
    fun `non json text is a format error`() {
        assertFormatError("not a book source")
    }

    private fun parse(json: String): BookSourceImportDocument =
        StringReader(json).use { parseBookSourceImport(it) }

    private fun assertFormatError(json: String) {
        try {
            parse(json)
            fail("expected BookSourceImportFormatException")
        } catch (expected: BookSourceImportFormatException) {
            // 期望：调用方据此提示 invalid_format
        }
    }
}
