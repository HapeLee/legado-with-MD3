package io.legado.app.ui.book.info

internal const val BOOK_INFO_COVER_WIDTH_DP = 240f
internal const val BOOK_INFO_COVER_DEFAULT_ASPECT_RATIO = 5f / 7f

private const val REFERENCE_COVER_WIDTH_DP = 72f
private const val REFERENCE_MIN_COVER_HEIGHT_DP = 1f
private const val REFERENCE_MAX_COVER_HEIGHT_DP = 140f

internal fun bookInfoCoverHeightDp(aspectRatio: Float): Float {
    val safeAspectRatio =
        if (aspectRatio.isFinite() && aspectRatio > 0f) aspectRatio
        else BOOK_INFO_COVER_DEFAULT_ASPECT_RATIO
    val minHeight = REFERENCE_MIN_COVER_HEIGHT_DP * BOOK_INFO_COVER_WIDTH_DP / REFERENCE_COVER_WIDTH_DP
    val maxHeight = REFERENCE_MAX_COVER_HEIGHT_DP * BOOK_INFO_COVER_WIDTH_DP / REFERENCE_COVER_WIDTH_DP
    return (BOOK_INFO_COVER_WIDTH_DP / safeAspectRatio).coerceIn(minHeight, maxHeight)
}
