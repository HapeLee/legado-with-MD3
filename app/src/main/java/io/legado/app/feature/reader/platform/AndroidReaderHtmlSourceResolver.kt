package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.layout.ReaderHtmlParagraph
import io.legado.app.feature.reader.core.layout.ReaderHtmlSourceResolver
import io.legado.app.feature.reader.core.source.ReaderHtmlSemanticTextResolver

/**
 * semantic resolver backed by the shared Jsoup cursor.
 */
object AndroidReaderHtmlSemanticTextResolver : ReaderHtmlSemanticTextResolver {
    override fun resolve(html: String): String = JsoupReaderHtmlParser.semanticText(html)
}

/**
 * NEW: HTML source resolver based on Jsoup DOM traversal.
 *
 * Replaces HtmlCompat.fromHtml so semantic text and measured paragraphs share
 * one cursor by construction. Image placeholder geometry now falls back to the
 * core placeholder-char rule (htmlSpanExtent is null); the old framework
 * fallback-drawable extent is intentionally dropped.
 */
class AndroidReaderHtmlSourceResolver(
    private val baseTextSizePx: Float,
    private val density: Float,
) : ReaderHtmlSourceResolver {
    override fun resolve(
        html: String,
        chapterPosition: Int,
    ): List<ReaderHtmlParagraph> {
        return JsoupReaderHtmlParser.parse(html, chapterPosition, baseTextSizePx, density).paragraphs
    }
}
