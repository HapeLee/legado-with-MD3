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
    /**
     * 带 click 动作脚本的图片（段评气泡）不参与排版：不产出测量项，也不解析图片尺寸。
     *
     * 注意：当前尚未接线。`LegacyReaderPaginationStyleFactory.create()` 还没有从阅读设置读取
     * 该字段，生产路径恒为 `false`，因此默认行为零变化；接线前不要把它当作已生效能力。
     */
    val excludeActionImages: Boolean = false,
) {
    fun columnCount(widthPx: Int, heightPx: Int): Int =
        columnMode.columnCount(widthPx, heightPx, isTablet, isScroll)
}
