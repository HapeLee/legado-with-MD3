package io.legado.app.ui.book.read.pageestimate

fun interface ChapterPageEstimator {
    /**
     * 返回**连续**页数：不取整、不设下限。取整和本地校准统一由 [WholeBookPageCoordinator]
     * 配合 [PageEstimateCalibrationStore] 完成 —— 对已经 ceil 过的整数做回归会把取整偏置
     * 算两遍，拟合出来的截距是错的。
     */
    fun estimate(input: ChapterPageEstimateInput): Float
}

data class ChapterPageEstimateInput(
    val readerType: Int,
    val titleLength: Int,
    val includeTitle: Boolean,
    val contentLength: Int,
    val textSizePx: Float,
    val textHeightPx: Float,
    val lineSpacingPx: Float,
    val paragraphSpacingPx: Float,
    val titleTextSizePx: Float,
    val titleTextHeightPx: Float,
    val titleLineSpacingPx: Float,
    val titleTopSpacingPx: Float,
    val titleBottomSpacingPx: Float,
    val endPaddingPx: Float,
    val contentWidthPx: Int,
    val contentHeightPx: Int,
)
