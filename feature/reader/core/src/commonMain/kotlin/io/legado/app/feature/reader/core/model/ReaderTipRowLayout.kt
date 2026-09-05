package io.legado.app.feature.reader.core.model

object ReaderTipRowLayout {
    fun lineHeight(fontTopPx: Float, fontBottomPx: Float): Float =
        (fontBottomPx - fontTopPx).coerceAtLeast(0f)

    fun extent(
        paddingTopPx: Float,
        fontTopPx: Float,
        fontBottomPx: Float,
        paddingBottomPx: Float,
        dividerExtentPx: Float = 0f,
    ): Float = paddingTopPx + lineHeight(fontTopPx, fontBottomPx) +
        paddingBottomPx + dividerExtentPx

    fun headerBaseline(paddingTopPx: Float, fontTopPx: Float): Float =
        paddingTopPx - fontTopPx

    fun footerBaseline(heightPx: Float, paddingBottomPx: Float, fontBottomPx: Float): Float =
        heightPx - paddingBottomPx - fontBottomPx
}
