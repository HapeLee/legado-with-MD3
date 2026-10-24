package io.legado.app.ui.book.info

internal const val BOOK_INFO_COVER_WIDTH_DP = 112f
internal const val BOOK_INFO_COVER_DEFAULT_ASPECT_RATIO = 5f / 7f

// clamp 上下限由搜索列表的 80~140dp 按 72→112 宽度等比换算而来：
// 同一张封面在列表与详情页即使同时触底 clamp，两端宽高比仍一致，
// 共享元素转场飞行中不会出现比例形变。改搜索列表区间时同步改这里。
private const val REFERENCE_COVER_WIDTH_DP = 72f
private const val REFERENCE_MIN_COVER_HEIGHT_DP = 80f
private const val REFERENCE_MAX_COVER_HEIGHT_DP = 140f

internal fun bookInfoCoverHeightDp(aspectRatio: Float): Float {
    val safeAspectRatio =
        if (aspectRatio.isFinite() && aspectRatio > 0f) aspectRatio
        else BOOK_INFO_COVER_DEFAULT_ASPECT_RATIO
    val minHeight = REFERENCE_MIN_COVER_HEIGHT_DP * BOOK_INFO_COVER_WIDTH_DP / REFERENCE_COVER_WIDTH_DP
    val maxHeight = REFERENCE_MAX_COVER_HEIGHT_DP * BOOK_INFO_COVER_WIDTH_DP / REFERENCE_COVER_WIDTH_DP
    return (BOOK_INFO_COVER_WIDTH_DP / safeAspectRatio).coerceIn(minHeight, maxHeight)
}
