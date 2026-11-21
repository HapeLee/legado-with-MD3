package io.legado.app.ui.main.bookshelf

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfSeriesEntriesTest {

    private fun bookUi(
        name: String,
        seriesId: String? = null,
        seriesOrder: Int = 0,
        durChapterIndex: Int = 0,
        totalChapterNum: Int = 10,
    ) = BookUiItem(
        book = BookShelfItem(
            bookUrl = "url:$name",
            name = name,
            author = "author",
            origin = "origin",
            originName = "origin",
            coverUrl = null,
            customCoverUrl = null,
            durChapterTitle = null,
            durChapterTime = 0L,
            durChapterPos = 0,
            latestChapterTitle = null,
            latestChapterTime = 0L,
            lastCheckCount = 0,
            totalChapterNum = totalChapterNum,
            durChapterIndex = durChapterIndex,
            type = 0,
            group = 0L,
            order = 0,
            seriesId = seriesId,
            seriesOrder = seriesOrder,
        ),
        displayTags = persistentListOf(),
    )

    @Test
    fun `collapse off keeps every book single in original order`() {
        val books = listOf(
            bookUi("A1", seriesId = "A", seriesOrder = 0),
            bookUi("B"),
        )
        val entries = buildBookshelfEntries(books, collapseSeries = false)
        assertEquals(listOf("book:url:A1", "book:url:B"), entries.map { it.key })
    }

    @Test
    fun `same series collapses into first occurrence`() {
        val books = listOf(
            bookUi("A1", seriesId = "A", seriesOrder = 0),
            bookUi("B"),
            bookUi("A2", seriesId = "A", seriesOrder = 1),
        )
        val entries = buildBookshelfEntries(books, collapseSeries = true)
        assertEquals(2, entries.size)
        assertEquals("series:A", entries[0].key)
        assertEquals("book:url:B", entries[1].key)
        val series = entries[0] as BookshelfEntry.Series
        assertEquals(listOf("A1", "A2"), series.members.map { it.book.name })
    }

    @Test
    fun `series members follow seriesOrder not shelf order`() {
        val books = listOf(
            bookUi("A2", seriesId = "A", seriesOrder = 1),
            bookUi("A1", seriesId = "A", seriesOrder = 0),
        )
        val entries = buildBookshelfEntries(books, collapseSeries = true)
        val series = entries.single() as BookshelfEntry.Series
        assertEquals(listOf("A1", "A2"), series.members.map { it.book.name })
    }

    @Test
    fun `interleaved series are separated correctly`() {
        val books = listOf(
            bookUi("A1", seriesId = "A", seriesOrder = 0),
            bookUi("B1", seriesId = "B", seriesOrder = 0),
            bookUi("A2", seriesId = "A", seriesOrder = 1),
            bookUi("B2", seriesId = "B", seriesOrder = 1),
        )
        val entries = buildBookshelfEntries(books, collapseSeries = true)
        assertEquals(listOf("series:A", "series:B"), entries.map { it.key })
        assertEquals(2, (entries[0] as BookshelfEntry.Series).members.size)
        assertEquals(2, (entries[1] as BookshelfEntry.Series).members.size)
    }

    @Test
    fun `single member series still collapses`() {
        val entries = buildBookshelfEntries(
            listOf(bookUi("Only", seriesId = "S")),
            collapseSeries = true,
        )
        assertTrue(entries.single() is BookshelfEntry.Series)
    }

    @Test
    fun `default series name is first selected book name`() {
        val books = listOf(bookUi("义妹生活 01"), bookUi("义妹生活 02"))
        assertEquals("义妹生活 01", defaultSeriesName(books))
    }

    @Test
    fun `default series name is empty without selection`() {
        assertEquals("", defaultSeriesName(emptyList()))
    }

    @Test
    fun `book series fields survive light book conversion`() {
        val item = bookUi("A1", seriesId = "A", seriesOrder = 3).book
        val light = item.toLightBook()
        assertEquals("A", light.seriesId)
        assertEquals(3, light.seriesOrder)
    }

    @Test
    fun `series progress counts finished volumes and current volume`() {
        val books = listOf(
            bookUi("A1", seriesId = "A", seriesOrder = 0, durChapterIndex = 9, totalChapterNum = 10),
            bookUi("A2", seriesId = "A", seriesOrder = 1, durChapterIndex = 3, totalChapterNum = 10),
            bookUi("A3", seriesId = "A", seriesOrder = 2, durChapterIndex = 0, totalChapterNum = 10),
        )
        val progress = seriesProgress(books)
        assertEquals(3, progress.total)
        assertEquals(1, progress.finished)
        assertEquals(2, progress.readingVolume)
        assertEquals(1f / 3f, progress.ratio, 0.0001f)
    }

    @Test
    fun `series progress has no reading volume when nothing started`() {
        val books = listOf(
            bookUi("A1", seriesId = "A", seriesOrder = 0),
            bookUi("A2", seriesId = "A", seriesOrder = 1),
        )
        val progress = seriesProgress(books)
        assertEquals(0, progress.finished)
        assertNull(progress.readingVolume)
    }

    @Test
    fun `expanded series inserts a full row panel right after the card`() {
        val books = listOf(
            bookUi("A1", seriesId = "A", seriesOrder = 0),
            bookUi("B"),
            bookUi("A2", seriesId = "A", seriesOrder = 1),
        )
        val entries = buildBookshelfEntries(
            books = books,
            collapseSeries = true,
            expandedSeries = "A",
        )
        assertEquals(
            listOf("series:A", "series-panel:A", "book:url:B"),
            entries.map { it.key },
        )
        val panel = entries[1] as BookshelfEntry.SeriesPanel
        assertEquals(2, panel.series.members.size)
    }

    @Test
    fun `fixed columns follow the shelf setting in list mode too`() {
        // 列表模式同样支持每行多本，列数必须来自书架设置而不是固定 1 列
        assertEquals(
            2,
            resolveBookshelfColumnCount(
                availableWidthDp = 360f,
                spacingDp = 8f,
                fixedColumns = 2,
                minCellWidthDp = 100f,
            ),
        )
    }

    @Test
    fun `adaptive columns are recomputed from the panel width`() {
        // (360 + 8) / (100 + 8) = 3.4 → 3 列，与 GridCells.Adaptive 同一公式
        assertEquals(
            3,
            resolveBookshelfColumnCount(
                availableWidthDp = 360f,
                spacingDp = 8f,
                fixedColumns = null,
                minCellWidthDp = 100f,
            ),
        )
    }

    @Test
    fun `adaptive columns never drop below one`() {
        assertEquals(
            1,
            resolveBookshelfColumnCount(50f, 8f, fixedColumns = null, minCellWidthDp = 100f),
        )
        assertEquals(
            1,
            resolveBookshelfColumnCount(0f, 8f, fixedColumns = null, minCellWidthDp = 100f),
        )
    }

    @Test
    fun `series cover comes from the cover map`() {
        val books = listOf(bookUi("A1", seriesId = "A"))
        val entries = buildBookshelfEntries(
            books = books,
            collapseSeries = true,
            seriesCovers = mapOf("A" to "/covers/a.jpg"),
        )
        assertEquals("/covers/a.jpg", (entries.single() as BookshelfEntry.Series).coverPath)
    }
}
