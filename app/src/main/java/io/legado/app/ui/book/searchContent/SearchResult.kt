package io.legado.app.ui.book.searchContent

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

data class SearchResult(
    val bookUrl: String = "",
    val resultCount: Int = 0,
    val resultCountWithinChapter: Int = 0,
    val resultText: String = "",
    val chapterTitle: String = "",
    val query: String = "",
    val pageSize: Int = 0,
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
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

    fun getTitleSpannable(accentColor: Int, isEInkMode: Boolean): SpannableString =
        SpannableString(chapterTitle).apply {
            if (isEInkMode) {
                setSpan(UnderlineSpan(), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                setSpan(ForegroundColorSpan(accentColor), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

    fun getContentSpannable(
        textColor: Int,
        accentColor: Int,
        backgroundColor: Int,
        isEInkMode: Boolean,
    ): SpannableStringBuilder = SpannableStringBuilder(resultText).apply {
        if (!isEInkMode && isNotEmpty()) {
            setSpan(ForegroundColorSpan(textColor), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        getHighlightRanges().forEach { (start, matchLength) ->
            val end = (start + matchLength).coerceAtMost(resultText.length)
            if (start !in 0 until end) return@forEach
            if (isEInkMode) {
                setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(accentColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(BackgroundColorSpan(backgroundColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun getHighlightRanges(): List<Pair<Int, Int>> = if (isRegex) {
            listOf(queryIndexInResult to matchLength)
        } else if (query.isNotBlank()) {
            buildList {
                var searchStart = 0
                while (searchStart < resultText.length) {
                    val start = resultText.indexOf(query, searchStart, ignoreCase = true)
                    if (start == -1) break
                    add(start to query.length)
                    searchStart = start + query.length.coerceAtLeast(1)
                }
            }
        } else {
            emptyList()
        }
}
