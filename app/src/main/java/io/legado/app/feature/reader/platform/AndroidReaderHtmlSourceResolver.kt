package io.legado.app.feature.reader.platform

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.Html
import android.text.Spanned
import android.text.Layout
import android.text.style.AlignmentSpan
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat
import io.legado.app.feature.reader.core.layout.ReaderHtmlSourceResolver
import io.legado.app.feature.reader.core.layout.ReaderHtmlParagraph
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import io.legado.app.feature.reader.core.layout.ReaderParagraphDecoration
import io.legado.app.feature.reader.core.layout.ReaderParagraphDecorationKind
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderInlineSourceStyle
import io.legado.app.feature.reader.core.source.ReaderHtmlSemanticTextResolver

object AndroidReaderHtmlSemanticTextResolver : ReaderHtmlSemanticTextResolver {
    override fun resolve(html: String): String = parseReaderHtml(html).toString()
}

class AndroidReaderHtmlSourceResolver(
    private val baseTextSizePx: Float,
    private val density: Float,
) : ReaderHtmlSourceResolver {
    override fun resolve(
        html: String,
        chapterPosition: Int,
    ): List<ReaderHtmlParagraph> {
        val spanned = parseReaderHtml(html)
        return splitParagraphs(spanned, chapterPosition)
    }

    private fun splitParagraphs(
        text: Spanned,
        chapterPosition: Int,
    ): List<ReaderHtmlParagraph> {
        val paragraphs = mutableListOf<ReaderHtmlParagraph>()
        var paragraph = mutableListOf<ReaderChapterInlineSource>()
        var paragraphStart = 0
        var index = 0
        var position = chapterPosition
        fun finishParagraph(end: Int, preserveEmpty: Boolean = false) {
            val probeEnd = (end + 1).coerceAtMost(text.length).coerceAtLeast(paragraphStart)
            val margins = text.getSpans(paragraphStart, probeEnd, LeadingMarginSpan::class.java)
            val firstMargin = margins.sumOf { it.getLeadingMargin(true) }.toFloat()
            val restMargin = margins.sumOf { it.getLeadingMargin(false) }.toFloat()
            val alignment = text.getSpans(paragraphStart, probeEnd, AlignmentSpan::class.java)
                .lastOrNull()?.alignment?.toReaderAlignment()
            var leadingOffset = 0f
            val decorations = buildList {
                margins.forEach { span ->
                    when (span) {
                        is QuoteSpan -> add(ReaderParagraphDecoration(
                            ReaderParagraphDecorationKind.QUOTE,
                            span.color,
                            if (Build.VERSION.SDK_INT >= 28) span.stripeWidth.toFloat() else 2f,
                            leadingOffset,
                        ))
                        is BulletSpan -> add(ReaderParagraphDecoration(
                            ReaderParagraphDecorationKind.BULLET,
                            // BulletSpan#getColor 是 API 28 才加的读取方法，低版本调用会
                            // NoSuchMethodError；读不到颜色就按"未指定"处理（等价 color == 0）。
                            if (Build.VERSION.SDK_INT >= 28) span.color.takeUnless { it == 0 } else null,
                            if (Build.VERSION.SDK_INT >= 28) span.bulletRadius.toFloat() else 4f,
                            leadingOffset,
                        ))
                    }
                    leadingOffset += span.getLeadingMargin(true)
                }
            }
            if (paragraph.isNotEmpty()) {
                paragraphs += ReaderHtmlParagraph(paragraph, firstMargin, restMargin, alignment, decorations)
            } else if (preserveEmpty) {
                paragraphs += ReaderHtmlParagraph(
                    listOf(ReaderChapterInlineSource.BlankLine(position)),
                    firstMargin,
                    restMargin,
                    alignment,
                    decorations,
                )
            }
            paragraph = mutableListOf()
            paragraphStart = (end + 1).coerceAtMost(text.length)
        }
        while (index < text.length) {
            if (text[index] == '\n') {
                // StaticLayout gives a newline-only line real height. Preserve only leading or
                // consecutive empty paragraphs; its trailing start == end line is still omitted.
                finishParagraph(index, preserveEmpty = paragraph.isEmpty())
                index++
                position++
                continue
            }
            val image = text.getSpans(index, index + 1, ImageSpan::class.java).firstOrNull()
            if (image != null) {
                image.source?.let { paragraph += ReaderChapterInlineSource.Image(it, position) }
                index++
                position++
                continue
            }
            val end = minOf(text.nextSpanTransition(index, text.length, Any::class.java), nextControl(text, index))
                .coerceAtLeast(index + 1)
            val value = text.subSequence(index, end).toString()
            paragraph += ReaderChapterInlineSource.Text(value, position, styleAt(text, index))
            position += value.length
            index = end
        }
        finishParagraph(text.length)
        return paragraphs
    }

    private fun nextControl(text: CharSequence, start: Int): Int {
        for (index in start until text.length) {
            if (text[index] == '\n' || text[index] == '\uFFFC') return index
        }
        return text.length
    }

    private fun styleAt(text: Spanned, index: Int): ReaderInlineSourceStyle {
        val spans = text.getSpans(index, index + 1, Any::class.java)
        val styleSpans = spans.filterIsInstance<StyleSpan>()
        val bold = styleSpans.any { it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC }
        val italic = styleSpans.any { it.style == Typeface.ITALIC || it.style == Typeface.BOLD_ITALIC }
        val relative = spans.filterIsInstance<RelativeSizeSpan>().fold(1f) { scale, span -> scale * span.sizeChange }
        val absolute = spans.filterIsInstance<AbsoluteSizeSpan>().lastOrNull()?.let {
            val px = if (it.dip) it.size * density else it.size.toFloat()
            px / baseTextSizePx.coerceAtLeast(1f)
        }
        return ReaderInlineSourceStyle(
            colorArgb = spans.filterIsInstance<ForegroundColorSpan>().lastOrNull()?.foregroundColor,
            backgroundArgb = spans.filterIsInstance<BackgroundColorSpan>().lastOrNull()?.backgroundColor,
            fontWeight = if (bold) 700 else null,
            italic = if (italic) true else null,
            underline = spans.any { it is UnderlineSpan },
            strikeThrough = spans.any { it is StrikethroughSpan },
            link = spans.filterIsInstance<URLSpan>().lastOrNull()?.url,
            fontSizeScale = absolute ?: relative,
            fontFamily = spans.filterIsInstance<TypefaceSpan>().lastOrNull()?.family,
            superscript = spans.any { it is SuperscriptSpan },
            subscript = spans.any { it is SubscriptSpan },
        )
    }
}

private fun Layout.Alignment.toReaderAlignment(): ReaderTextAlignment = when (this) {
    Layout.Alignment.ALIGN_CENTER -> ReaderTextAlignment.CENTER
    Layout.Alignment.ALIGN_OPPOSITE -> ReaderTextAlignment.END
    else -> ReaderTextAlignment.START
}

private fun parseReaderHtml(html: String): Spanned = HtmlCompat.fromHtml(
    html,
    HtmlCompat.FROM_HTML_MODE_COMPACT,
    Html.ImageGetter {
        ColorDrawable(Color.TRANSPARENT).apply { setBounds(0, 0, 1, 1) }
    },
    null,
)
