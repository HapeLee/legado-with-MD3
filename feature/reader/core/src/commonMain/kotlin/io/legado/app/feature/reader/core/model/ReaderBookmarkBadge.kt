package io.legado.app.feature.reader.core.model

/**
 * Per-page decoration, so page transforms and curl clipping also apply to the bookmark.
 *
 * `@Stable` (androidx.compose.runtime.Stable) was present in the original Android source.
 * It is removed for the KMP commonMain slice because this module does not yet depend on
 * Compose runtime. Once a CMP compose-runtime dependency is introduced (planned in a later
 * F2 sub-slice when selection/ joins commonMain), restore `@Stable` here and on
 * ReaderSelection.
 */
data class ReaderBookmarkBadge(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Int,
    val heightPx: Int,
    val imageSource: String = "",
    val imageVersion: String = "",
) {
    companion object {
        fun create(
            hasBookmark: Boolean,
            isScroll: Boolean,
            pageWidthPx: Int,
            contentTopPx: Float,
            contentRightPaddingPx: Int,
            density: Float,
            sizeDp: Int,
            imageSource: String = "",
            imageVersion: String = "",
        ): ReaderBookmarkBadge? {
            if (!hasBookmark || isScroll) return null
            val width = (sizeDp.coerceAtLeast(1) * density).toInt().coerceAtLeast(1)
            return ReaderBookmarkBadge(
                leftPx = pageWidthPx - contentRightPaddingPx - 6 * density - width,
                topPx = contentTopPx + 2 * density,
                widthPx = width,
                heightPx = width * 2,
                imageSource = imageSource,
                imageVersion = imageVersion,
            )
        }
    }
}
