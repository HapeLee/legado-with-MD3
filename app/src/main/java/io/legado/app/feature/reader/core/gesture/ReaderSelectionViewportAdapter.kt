package io.legado.app.feature.reader.core.gesture

import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.selection.ReaderSelection
import io.legado.app.feature.reader.core.selection.mergeSelectionBounds

data class ReaderSelectionVisualBounds(
    val page: ReaderPage,
    val bounds: ReaderRect,
)

/** Android reader adapter for selection data that remains platform-owned. */
fun ReaderPageViewportLayout.selectionBounds(selection: ReaderSelection): List<ReaderSelectionVisualBounds> =
    placements.flatMap { placement ->
        selection.bounds(placement.page).mergeSelectionBounds().map { bounds ->
            ReaderSelectionVisualBounds(
                page = placement.page,
                bounds = bounds.offsetY(placement.offsetY),
            )
        }
    }
