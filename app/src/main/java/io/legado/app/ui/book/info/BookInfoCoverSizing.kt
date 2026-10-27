package io.legado.app.ui.book.info

internal const val BOOK_INFO_COVER_WIDTH_DP = 240f
internal const val BOOK_INFO_COVER_DEFAULT_ASPECT_RATIO = 5f / 7f

internal fun bookInfoCoverHeightDp(
    aspectRatio: Float,
    widthDp: Float = BOOK_INFO_COVER_WIDTH_DP,
): Float {
    val safeAspectRatio =
        if (aspectRatio.isFinite() && aspectRatio > 0f) aspectRatio
        else BOOK_INFO_COVER_DEFAULT_ASPECT_RATIO
    return widthDp / safeAspectRatio
}
