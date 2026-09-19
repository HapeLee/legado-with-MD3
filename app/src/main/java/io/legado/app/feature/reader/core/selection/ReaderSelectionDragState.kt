package io.legado.app.feature.reader.core.selection

/** Separates an established long press from an intentional selection drag. */
data class ReaderSelectionDragState(val started: Boolean = false) {
    fun update(
        longPressed: Boolean,
        handleGrabbed: Boolean,
        distancePx: Float,
        touchSlopPx: Float,
    ): ReaderSelectionDragState = if (
        started || handleGrabbed || longPressed && distancePx >= touchSlopPx
    ) copy(started = true) else this
}
