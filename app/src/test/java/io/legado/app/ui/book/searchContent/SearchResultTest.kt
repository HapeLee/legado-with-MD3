package io.legado.app.ui.book.searchContent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultTest {

    @Test
    fun `普通搜索高亮结果片段中的全部关键词`() {
        val accentColor = Color.Red
        val annotated = SearchResult(
            resultText = "foo bar foo",
            query = "foo",
        ).getContentAnnotatedString(
            textColor = Color.Black,
            accentColor = accentColor,
            backgroundColor = Color.Yellow,
            isEInkMode = false,
        )

        val highlights = annotated.spanStyles.filter {
            it.item.fontWeight == FontWeight.Bold && it.item.color == accentColor
        }

        assertEquals(listOf(0 to 3, 8 to 11), highlights.map { it.start to it.end })
    }

    @Test
    fun `正则搜索使用仓库记录的实际匹配范围`() {
        val accentColor = Color.Red
        val annotated = SearchResult(
            resultText = "abc123def",
            query = "\\d+",
            queryIndexInResult = 3,
            matchLength = 3,
            isRegex = true,
        ).getContentAnnotatedString(
            textColor = Color.Black,
            accentColor = accentColor,
            backgroundColor = Color.Yellow,
            isEInkMode = false,
        )

        val highlight = annotated.spanStyles.single {
            it.item.fontWeight == FontWeight.Bold && it.item.color == accentColor
        }

        assertEquals(3, highlight.start)
        assertEquals(6, highlight.end)
    }
}
