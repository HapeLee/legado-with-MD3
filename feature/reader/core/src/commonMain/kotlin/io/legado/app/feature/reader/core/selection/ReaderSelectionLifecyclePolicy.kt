package io.legado.app.feature.reader.core.selection

enum class ReaderPageChangeOrigin {
    PROGRAMMATIC,
    SELECTION_DRAG_SCROLL,
}

object ReaderSelectionLifecyclePolicy {
    fun shouldClearForPageChange(origin: ReaderPageChangeOrigin): Boolean =
        origin == ReaderPageChangeOrigin.PROGRAMMATIC

    fun shouldPauseAutoPage(hasSelection: Boolean): Boolean = hasSelection

    fun shouldReanchorMenuAfterLayoutChange(
        hasSelection: Boolean,
        menuVisible: Boolean,
        previousLayoutRevision: Long,
        currentLayoutRevision: Long,
    ): Boolean = hasSelection && menuVisible && previousLayoutRevision != currentLayoutRevision
}
