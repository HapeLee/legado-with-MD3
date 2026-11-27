package io.legado.app.data.model

import io.legado.app.constant.BookType

data class LocalBookMetricTarget(
    val bookUrl: String,
    val originName: String,
    val type: Int,
    val latestChapterTime: Long,
) {
    val isImage: Boolean get() = (type and BookType.image) > 0

    val mayBeMangaDirectory: Boolean
        get() = isImage && (type and BookType.archive) == 0
}
