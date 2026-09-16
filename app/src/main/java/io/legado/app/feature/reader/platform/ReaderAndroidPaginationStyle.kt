package io.legado.app.feature.reader.platform

import android.text.TextPaint
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.model.ReaderEmphasisUnderline
import io.legado.app.feature.reader.core.layout.ReaderColumnMode
import io.legado.app.feature.reader.core.layout.ReaderPageUnderline
import io.legado.app.feature.reader.core.source.ReaderTitleSegmentation

/** Immutable-per-pagination Android shaping input, independent of the legacy page model. */
data class ReaderAndroidPaginationStyle(
    val bodyPaint: TextPaint,
    val titlePaint: TextPaint,
    val bodyStyle: ReaderTextStyle,
    val titleStyle: ReaderTextStyle,
    val paddingLeftPx: Int,
    val paddingTopPx: Int,
    val paddingRightPx: Int,
    val paddingBottomPx: Int,
    val bodyTextHeightPx: Float,
    val titleTextHeightPx: Float,
    val bodyBaselineOffsetPx: Float,
    val titleBaselineOffsetPx: Float,
    val lineSpacingExtra: Float,
    val titleLineSpacingExtra: Float,
    val paragraphSpacing: Int,
    val titleTopSpacingPx: Float = 0f,
    val titleBottomSpacingPx: Float = 0f,
    val titleLineSpacingSub: Float = 0f,
    val titleSegmentation: ReaderTitleSegmentation = ReaderTitleSegmentation(),
    val columnMode: ReaderColumnMode = ReaderColumnMode.SINGLE,
    val isTablet: Boolean = false,
    val isScroll: Boolean = false,
    val textBottomJustify: Boolean = false,
    val pageUnderline: ReaderPageUnderline? = null,
    val emphasisUnderlineStyle: ReaderEmphasisUnderline? = null,
    val excludeActionImages: Boolean = false,
) {
    fun columnCount(widthPx: Int, heightPx: Int): Int =
        columnMode.columnCount(widthPx, heightPx, isTablet, isScroll)
}
