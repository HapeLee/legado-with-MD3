package io.legado.app.ui.main.bookshelf

/**
 * 书架网格里的一个渲染项：单本、折叠后的系列，或系列展开后的面板。
 *
 * 系列不是独立实体，归属来自 `BookShelfItem.seriesId`（系列名），封面来自 book_series 表。
 * 聚合只决定"怎么摆"，不改数据库顺序——拖拽重排仍以单本索引为准，因此编辑态一律不折叠。
 */
internal sealed interface BookshelfEntry {
    val key: String

    data class Single(val item: BookUiItem) : BookshelfEntry {
        override val key: String = "book:${item.book.bookUrl}"
    }

    data class Series(
        val name: String,
        /** 已按卷序排好，[first] 是卷序最小的一本 */
        val members: List<BookUiItem>,
        /** 系列独立封面；null = 用成员封面拼贴 */
        val coverPath: String?,
        val progress: SeriesProgress,
    ) : BookshelfEntry {
        override val key: String = "series:$name"

        val first: BookUiItem get() = members.first()
    }

    /** 点击系列后在书架内就地展开的二级目录，独占一整行 */
    data class SeriesPanel(val series: Series) : BookshelfEntry {
        override val key: String = "series-panel:${series.name}"
    }
}

/** 系列总进度：已读完卷数 / 总卷数，以及当前在读第几卷 */
internal data class SeriesProgress(
    val total: Int,
    val finished: Int,
    /** 1 基卷号；null = 没有正在读的卷（都未开始或都读完了） */
    val readingVolume: Int?,
) {
    val ratio: Float = if (total <= 0) 0f else finished.toFloat() / total
}

/**
 * @param collapseSeries 仅非编辑态、非搜索态为 true：
 * 编辑态要保证「网格项索引 == 书索引」以支持拖拽重排；搜索态要能直接看到命中的那一卷。
 * @param expandedSeries 已展开的系列名，展开面板紧跟在该系列卡后面
 */
internal fun buildBookshelfEntries(
    books: List<BookUiItem>,
    collapseSeries: Boolean,
    seriesCovers: Map<String, String> = emptyMap(),
    expandedSeries: String? = null,
): List<BookshelfEntry> {
    if (!collapseSeries) {
        return books.map { BookshelfEntry.Single(it) }
    }
    val membersBySeries = books
        .filter { !it.book.seriesId.isNullOrBlank() }
        .groupBy { it.book.seriesId }
        .mapValues { (_, members) -> members.sortedBy { it.book.seriesOrder } }
    val emitted = HashSet<String>()
    return buildList(books.size + 1) {
        books.forEach { item ->
            val seriesName = item.book.seriesId?.takeIf { it.isNotBlank() }
            if (seriesName == null) {
                add(BookshelfEntry.Single(item))
                return@forEach
            }
            if (!emitted.add(seriesName)) return@forEach
            val members = membersBySeries[seriesName]
            if (members.isNullOrEmpty()) {
                add(BookshelfEntry.Single(item))
                return@forEach
            }
            val series = BookshelfEntry.Series(
                name = seriesName,
                members = members,
                coverPath = seriesCovers[seriesName],
                progress = seriesProgress(members),
            )
            add(series)
            if (seriesName == expandedSeries) {
                add(BookshelfEntry.SeriesPanel(series))
            }
        }
    }
}

/**
 * 书架当前真实的每行列数。
 *
 * 展开面板是网格里的 span 全行项，LazyGrid 不会告诉它实际被分成了几列，
 * 所以这里用与 `GridCells.Adaptive` 相同的公式在面板可用宽度上重算一遍：
 * 固定列数直接返回；自适应时按最小单元格宽度反推。
 * 列表模式也走这条路径——书架列表同样支持每行多本，列数同样来自设置。
 *
 * 参数单位统一为 dp，方便单测直接喂数值。
 */
internal fun resolveBookshelfColumnCount(
    availableWidthDp: Float,
    spacingDp: Float,
    /** 固定列数；传 null 表示由封面宽度推出列数 */
    fixedColumns: Int?,
    minCellWidthDp: Float,
): Int {
    if (fixedColumns != null) return fixedColumns.coerceAtLeast(1)
    if (availableWidthDp <= 0f) return 1
    val step = (minCellWidthDp + spacingDp).coerceAtLeast(1f)
    return ((availableWidthDp + spacingDp) / step).toInt().coerceAtLeast(1)
}

internal fun seriesProgress(members: List<BookUiItem>): SeriesProgress {
    var finished = 0
    var readingVolume: Int? = null
    members.forEachIndexed { index, item ->
        val book = item.book
        val isFinished = book.totalChapterNum > 0 &&
                book.durChapterIndex >= book.totalChapterNum - 1
        if (isFinished) {
            finished++
        } else if (readingVolume == null && (book.durChapterIndex > 0 || book.durChapterPos > 0)) {
            readingVolume = index + 1
        }
    }
    return SeriesProgress(
        total = members.size,
        finished = finished,
        readingVolume = readingVolume,
    )
}

/** 议题要求的默认系列名：取所选第一本书的书名。 */
internal fun defaultSeriesName(books: List<BookUiItem>): String {
    return books.firstOrNull()?.book?.name?.trim().orEmpty()
}
