package io.legado.app.ui.book.searchContent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

data class SearchResult(
    val bookUrl: String = "",
    /** 本章内的第几条命中，阅读页定位用它做"出现序号"回退锚点。 */
    val resultCountWithinChapter: Int = 0,
    val resultText: String = "",
    val chapterTitle: String = "",
    val query: String = "",
    val chapterIndex: Int = 0,
    val queryIndexInResult: Int = 0,
    val queryIndexInChapter: Int = 0,
    val matchLength: Int = query.length,
    val isRegex: Boolean = false,
    val progressPercent: Float = 0f
) {

    fun getTitleAnnotatedString(
        accentColor: Color,
        isEInkMode: Boolean,
    ): AnnotatedString = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = if (isEInkMode) Color.Unspecified else accentColor,
                textDecoration = if (isEInkMode) TextDecoration.Underline else null,
            )
        ) {
            append(chapterTitle)
        }
    }

    fun getContentAnnotatedString(
        textColor: Color,
        accentColor: Color,
        backgroundColor: Color,
        isEInkMode: Boolean,
    ): AnnotatedString {
        val ranges = getHighlightRanges()

        return buildAnnotatedString {
            append(resultText)
            if (!isEInkMode && resultText.isNotEmpty()) {
                addStyle(SpanStyle(color = textColor), 0, resultText.length)
            }

            ranges.forEach { (start, length) ->
                val end = (start + length).coerceAtMost(resultText.length)
                if (start !in 0 until end) return@forEach
                addStyle(
                    style = if (isEInkMode) {
                        SpanStyle(textDecoration = TextDecoration.Underline)
                    } else {
                        SpanStyle(
                            color = accentColor,
                            background = backgroundColor,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    start = start,
                    end = end,
                )
            }
        }
    }

    private fun getHighlightRanges(): List<Pair<Int, Int>> = when {
        isRegex -> listOf(queryIndexInResult to matchLength)
            .filter { (start, length) -> start >= 0 && length > 0 }

        // 检索本身用大小写敏感的 indexOf；高亮如果忽略大小写，会把不同大小写的同词
        // 也标成命中，看起来像"搜到了没搜到的东西"。
        query.isEmpty() || resultText.isEmpty() -> emptyList()

        else -> buildList {
            var searchStart = 0
            while (searchStart <= resultText.length - query.length) {
                val start = resultText.indexOf(query, searchStart)
                if (start == -1) break
                add(start to query.length)
                searchStart = start + query.length
            }
        }
    }
}
