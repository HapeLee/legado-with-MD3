package io.legado.app.data.repository

import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookShelfItem
import kotlin.test.Test
import kotlin.test.assertEquals

class BookshelfRepositoryTest {

    private val repository = BookshelfRepository()

    @Test
    fun defaultSortUsesReadingChapterTimeAndHonorsDirection() {
        assertEquals(
            listOf("old", "new", "middle"),
            repository.sortBooks(books, group = null, sort = 0, sortOrder = 0).names(),
        )
        assertEquals(
            listOf("middle", "new", "old"),
            repository.sortBooks(books, group = null, sort = 0, sortOrder = 1).names(),
        )
    }

    @Test
    fun latestUpdateSortUsesLatestChapterTime() {
        assertEquals(
            listOf("old", "new", "middle"),
            repository.sortBooks(books, group = null, sort = 1, sortOrder = 0).names(),
        )
    }

    @Test
    fun updateOrReadingSortUsesTheMostRecentOfBothTimes() {
        assertEquals(
            listOf("old", "new", "middle"),
            repository.sortBooks(books, group = null, sort = 4, sortOrder = 0).names(),
        )
    }

    @Test
    fun groupSortOverridesGlobalSort() {
        val group = BookGroup(bookSort = 3)

        assertEquals(
            listOf("middle", "new", "old"),
            repository.sortBooks(books, group, sort = 1, sortOrder = 0).names(),
        )
    }

    private fun List<BookShelfItem>.names(): List<String> = map(BookShelfItem::name)

    private companion object {
        val books = listOf(
            book(name = "old", author = "张三", durChapterTime = 10, latestChapterTime = 20, order = 3),
            book(name = "middle", author = "王五", durChapterTime = 30, latestChapterTime = 40, order = 1),
            book(name = "new", author = "李四", durChapterTime = 20, latestChapterTime = 30, order = 2),
        )

        fun book(
            name: String,
            author: String,
            durChapterTime: Long,
            latestChapterTime: Long,
            order: Int,
        ) = BookShelfItem(
            bookUrl = name,
            name = name,
            author = author,
            origin = "origin",
            originName = "origin",
            coverUrl = null,
            customCoverUrl = null,
            durChapterTitle = null,
            durChapterTime = durChapterTime,
            durChapterPos = 0,
            latestChapterTitle = null,
            latestChapterTime = latestChapterTime,
            lastCheckCount = 0,
            totalChapterNum = 0,
            durChapterIndex = 0,
            type = 0,
            group = 0,
            order = order,
        )
    }
}
