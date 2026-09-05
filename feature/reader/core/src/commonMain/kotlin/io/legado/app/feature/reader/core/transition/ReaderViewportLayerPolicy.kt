package io.legado.app.feature.reader.core.transition

/** Viewport-owned layers stay fixed while continuous-scroll pages move underneath them. */
object ReaderViewportLayerPolicy {
    fun usesFixedBackground(mode: ReaderTransitionMode): Boolean =
        mode == ReaderTransitionMode.SCROLL

    fun usesFixedPageChrome(mode: ReaderTransitionMode): Boolean =
        mode == ReaderTransitionMode.SCROLL
}
