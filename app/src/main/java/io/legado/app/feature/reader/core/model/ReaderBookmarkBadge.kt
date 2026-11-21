package io.legado.app.feature.reader.core.model

import androidx.compose.runtime.Stable

/**
 * 角标锚点。
 *
 * - [TOP_START] / [TOP_END]：贴页眉下边（header 关闭时即视口顶）的左/右两角；
 * - [CUSTOM]：从正文区左上角按 [ReaderBookmarkBadge.create] 的偏移量自由摆放，
 *   绘制时会夹取在页面范围内，避免角标被裁掉。
 *
 * 用 START/END 而不是 left/right：中文竖排与未来 RTL 由消费方解释书写方向，
 * 当前渲染仍是左右两列像素，映射见 [ReaderBookmarkBadge.create]。
 */
enum class ReaderBookmarkBadgePosition(val preferenceValue: String) {
    TOP_START("topStart"),
    TOP_END("topEnd"),
    CUSTOM("custom"),
    ;

    companion object {
        /** 旧配置里没有这个键、或值被改坏时回落到历史位置（右上）。 */
        fun fromPreference(value: String?): ReaderBookmarkBadgePosition =
            entries.firstOrNull { it.preferenceValue == value } ?: TOP_END
    }
}

/** Per-page decoration, so page transforms and curl clipping also apply to the bookmark. */
@Stable
data class ReaderBookmarkBadge(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Int,
    val heightPx: Int,
    val imageSource: String = "",
    val imageVersion: String = "",
) {
    companion object {
        /** 角标与正文区左右边界的留白，沿用旧版固定值。 */
        private const val GAP_DP = 6f

        fun create(
            hasBookmark: Boolean,
            isScroll: Boolean,
            pageWidthPx: Int,
            headerBottomPx: Float,
            contentRightPaddingPx: Int,
            density: Float,
            sizeDp: Int,
            imageSource: String = "",
            imageVersion: String = "",
            position: ReaderBookmarkBadgePosition = ReaderBookmarkBadgePosition.TOP_END,
            contentLeftPaddingPx: Int = 0,
            offsetXDp: Int = 0,
            offsetYDp: Int = 0,
            pageHeightPx: Int = 0,
        ): ReaderBookmarkBadge? {
            // A zero-size badge explicitly hides the decoration while preserving the bookmark.
            if (!hasBookmark || isScroll || sizeDp <= 0) return null
            val width = (sizeDp * density).toInt().coerceAtLeast(1)
            val height = width * 2
            val gapPx = GAP_DP * density
            val leftPx: Float
            val topPx: Float
            when (position) {
                ReaderBookmarkBadgePosition.TOP_START -> {
                    leftPx = contentLeftPaddingPx + gapPx
                    topPx = headerBottomPx
                }

                ReaderBookmarkBadgePosition.CUSTOM -> {
                    // 自定义以正文区左上角为原点：header 关闭时 headerBottomPx 就是 0。
                    leftPx = contentLeftPaddingPx + offsetXDp * density
                    topPx = headerBottomPx + offsetYDp * density
                }

                // 顶部贴页眉下边（header 关闭时即视口顶），右侧留白与左侧一致。
                ReaderBookmarkBadgePosition.TOP_END -> {
                    leftPx = pageWidthPx - contentRightPaddingPx - gapPx - width
                    topPx = headerBottomPx
                }
            }
            // 自定义可以越过内边距，但不能跑到页面外被裁掉。
            val maxLeftPx = (pageWidthPx - width).coerceAtLeast(0).toFloat()
            val maxTopPx = if (pageHeightPx > 0) {
                (pageHeightPx - height).coerceAtLeast(0).toFloat()
            } else {
                Float.MAX_VALUE
            }
            return ReaderBookmarkBadge(
                leftPx = leftPx.coerceIn(0f, maxLeftPx),
                topPx = topPx.coerceIn(0f, maxTopPx),
                widthPx = width,
                heightPx = height,
                imageSource = imageSource,
                imageVersion = imageVersion,
            )
        }
    }
}
