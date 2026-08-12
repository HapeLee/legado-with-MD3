package io.legado.app.data.model

import androidx.annotation.Keep

@Keep
data class MangaFooterConfig(
    var hideChapterLabel: Boolean = false,
    var hideChapter: Boolean = false,
    var hidePageNumberLabel: Boolean = false,
    var hidePageNumber: Boolean = false,
    var hideProgressRatioLabel: Boolean = false,
    var hideProgressRatio: Boolean = false,
    var footerOrientation: Int = 0,
    var hideFooter: Boolean = false,
    var hideChapterName: Boolean = false,
)
