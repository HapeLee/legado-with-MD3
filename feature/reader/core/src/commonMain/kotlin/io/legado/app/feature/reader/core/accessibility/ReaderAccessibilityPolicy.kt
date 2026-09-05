package io.legado.app.feature.reader.core.accessibility

import io.legado.app.feature.reader.core.model.ReaderPageWindow

data class ReaderAccessibilityPage(
    val text: String,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
    val isBookmarked: Boolean,
)

object ReaderAccessibilityPolicy {
    fun snapshot(window: ReaderPageWindow): ReaderAccessibilityPage? {
        val current = window.current ?: return null
        return ReaderAccessibilityPage(
            text = current.text,
            canGoPrevious = window.previous != null,
            canGoNext = window.next != null,
            isBookmarked = current.decoration.bookmarkBadge != null,
        )
    }
}
