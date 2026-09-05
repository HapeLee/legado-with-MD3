package io.legado.app.feature.reader.core.selection

import io.legado.app.feature.reader.core.model.ReaderRect

data class ReaderSelectionMenuAnchor(
    val startX: Float,
    val startTopY: Float,
    val startBottomY: Float,
    val endX: Float,
    val endBottomY: Float,
) {
    companion object {
        fun from(bounds: List<ReaderRect>): ReaderSelectionMenuAnchor? {
            val first = bounds.firstOrNull() ?: return null
            val last = bounds.last()
            return ReaderSelectionMenuAnchor(
                startX = first.left,
                startTopY = first.top,
                startBottomY = first.bottom,
                endX = last.right,
                endBottomY = last.bottom,
            )
        }
    }
}
