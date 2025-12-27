package io.legado.app.ui.book.searchContent

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import io.legado.app.help.config.AppConfig

data class SearchResult(
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
    val isRegex: Boolean = false,
    val progressPercent: Float = 0f
) {

    fun getTitleSpannable(accentColor: Int): SpannableString {
        return if (AppConfig.isEInkMode) {
            SpannableString(chapterTitle).apply {
                setSpan(UnderlineSpan(), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            SpannableString(chapterTitle).apply {
                setSpan(ForegroundColorSpan(accentColor), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    fun getContentSpannable(textColor: Int, accentColor: Int, bgColor: Int): SpannableStringBuilder {
        val spannable = SpannableStringBuilder(resultText)

        if (query.isNotBlank()) {
            var searchStart = 0
            while (true) {
                val start = resultText.indexOf(query, searchStart, ignoreCase = true)
                if (start == -1) break
                val end = start + query.length

                if (AppConfig.isEInkMode) {
                    spannable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    // 字体加粗
                    spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    // 字体颜色
                    spannable.setSpan(ForegroundColorSpan(accentColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    // 背景色
                    spannable.setSpan(BackgroundColorSpan(bgColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                searchStart = end
            }
        }

        // 设置整体字体颜色（正文）
        if (!AppConfig.isEInkMode) {
            spannable.setSpan(ForegroundColorSpan(textColor), 0, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return spannable
    }
}
