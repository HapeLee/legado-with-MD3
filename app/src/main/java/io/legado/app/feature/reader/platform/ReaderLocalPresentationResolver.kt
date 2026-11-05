package io.legado.app.feature.reader.platform

import io.legado.app.domain.model.TextProjection
import io.legado.app.feature.reader.core.source.ReaderLocalPresentation
import io.legado.app.model.localBook.LocalBookSemanticHtml
import org.jsoup.parser.Parser

/** Projects DOM source positions through the very same formatter that emits canonical transport. */
internal object ReaderLocalPresentationResolver {
    /** Returns null when the appearance cannot be bound to the canonical text. */
    fun resolve(html: String, decodeEntities: Boolean = true): ReaderLocalPresentation? {
        val document = Parser.htmlParser().setTrackPosition(true).parseInput(html, "")
        val appearance = JsoupReaderPresentationParser.project(document.body(), html)
        val projection = TextProjection(html.length)
        val text = LocalBookSemanticHtml.plainTextMapped(html, decodeEntities, projection)
        val mapped = appearance.remapOrNull(projection, text, keepDeletedAnchors = true) ?: return null
        return mapped.copy(anchors = mapped.anchors.mapValues { (_, offset) ->
            var position = offset
            while (position < text.length && text[position].isWhitespace()) position++
            position
        })
    }
}
