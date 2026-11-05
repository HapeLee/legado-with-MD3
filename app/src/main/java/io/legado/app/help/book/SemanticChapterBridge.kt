package io.legado.app.help.book

import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSemanticTextResolver
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSourceResolver

/**
 * Single seam between the legacy content pipeline ([help/book]) and the reader platform adapters.
 *
 * Callers outside the reader feature (search, export, content editing) only need plain canonical
 * text. Routing them through this object keeps the feature/platform imports in one place instead
 * of spreading them across data and content layers.
 */
internal object SemanticChapterBridge {

    /**
     * Layout-neutral metrics: the source resolver only classifies block structure here, so the
     * result must not depend on the user's font size or screen density.
     */
    private const val NEUTRAL_TEXT_SIZE_PX = 10f
    private const val NEUTRAL_DENSITY = 1f

    fun parse(
        chapterIndex: Int,
        title: String,
        paragraphs: List<String>,
        includeTitle: Boolean,
        adaptSpecialStyle: Boolean,
    ): ReaderChapterSource = ReaderChapterSourceParser.parse(
        chapterIndex = chapterIndex,
        title = title,
        paragraphs = paragraphs,
        includeTitle = includeTitle,
        adaptSpecialStyle = adaptSpecialStyle,
        htmlSemanticTextResolver = AndroidReaderHtmlSemanticTextResolver,
    )

    /** Canonical body used by search and other consumers that never render rich text. */
    fun semanticBody(paragraphs: List<String>, adaptSpecialStyle: Boolean): String =
        parse(0, "", paragraphs, includeTitle = false, adaptSpecialStyle = adaptSpecialStyle)
            .semanticContent

    /**
     * Readable projection of an explicit `<usehtml>` block, keeping the legacy image channel.
     * The legacy plain parser reads the raw `src` attribute without HTML entity decoding, so the
     * emitted `<img>` tags must keep `&` and source-option JSON byte-for-byte.
     */
    fun htmlParagraphsText(html: String): String =
        AndroidReaderHtmlSourceResolver(NEUTRAL_TEXT_SIZE_PX, NEUTRAL_DENSITY).resolve(html, 0)
            .joinToString("\n", postfix = "\n") { paragraph ->
                paragraph.items.joinToString("") { item ->
                    when (item) {
                        is ReaderChapterInlineSource.Text -> item.value
                        is ReaderChapterInlineSource.Image -> "<img src=\"${item.source}\">"
                        else -> ""
                    }
                }
            }.trimEnd('\n')
}
