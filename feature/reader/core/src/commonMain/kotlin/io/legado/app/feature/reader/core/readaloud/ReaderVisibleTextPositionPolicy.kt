package io.legado.app.feature.reader.core.readaloud

import io.legado.app.feature.reader.core.gesture.ReaderPageViewportLayout
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPageWindow

/** A body-text anchor in the chapter coordinate space used by the read-aloud service. */
data class ReaderVisibleTextPosition(
    val chapterIndex: Int,
    val chapterPosition: Int,
)

/**
 * Resolves the first body glyph currently visible in the three-page scroll viewport.
 *
 * Chapter titles are represented by emphasized text and must not become the speech
 * anchor; this keeps the Canvas reader aligned with the legacy reader's body-only
 * read-aloud behavior.
 */
object ReaderVisibleTextPositionPolicy {
    fun firstVisibleBodyText(
        window: ReaderPageWindow,
        scrollOffsetPx: Float,
    ): ReaderVisibleTextPosition? {
        val current = window.current ?: return null
        val viewportTop = current.contentTopPx
        val viewportBottom = current.contentBottomPx
        return ReaderPageViewportLayout.scroll(window, scrollOffsetPx)
            .placements
            .asSequence()
            .filterNot { it.page.isPlaceholder }
            .flatMap { placement ->
                placement.page.elements.asSequence()
                    .filterIsInstance<ReaderElement.Text>()
                    .filterNot { it.emphasized }
                    .filter { text ->
                        val top = text.bounds.top + placement.offsetY
                        val bottom = text.bounds.bottom + placement.offsetY
                        bottom > viewportTop && top < viewportBottom
                    }
                    .map { text ->
                        VisibleText(
                            chapterIndex = placement.page.id.chapterIndex,
                            chapterPosition = text.chapterPosition,
                            topPx = text.bounds.top + placement.offsetY,
                            leftPx = text.bounds.left,
                        )
                    }
            }
            .minWithOrNull(
                compareBy<VisibleText> { it.topPx }
                    .thenBy { it.leftPx }
                    .thenBy { it.chapterIndex }
                    .thenBy { it.chapterPosition },
            )
            ?.let { ReaderVisibleTextPosition(it.chapterIndex, it.chapterPosition) }
    }

    private data class VisibleText(
        val chapterIndex: Int,
        val chapterPosition: Int,
        val topPx: Float,
        val leftPx: Float,
    )
}
