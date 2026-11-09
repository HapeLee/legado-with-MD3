package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigatorMangaTest {

    @Test
    fun `continue reading resolves a local comic and keeps the home parent`() {
        val request = MainRouteReadBook(readAloud = true, inBookshelf = false, chapterChanged = true)
        val backStack = mutableListOf<NavKey>(MainRouteHome, request)
        val book = Book(type = BookType.local or BookType.image, bookUrl = "/library/comic")

        assertTrue(MainNavigator.redirectLocalManga(backStack, request, book))

        val reader = backStack.last() as MainRouteReadManga
        assertEquals(listOf(MainRouteHome, reader), backStack)
        assertEquals(book.bookUrl, reader.bookUrl)
        assertFalse(reader.inBookshelf)
        assertTrue(reader.chapterChanged)
    }

    @Test
    fun `redirect leaves a text zip or a superseded request alone`() {
        val request = MainRouteReadBook(bookUrl = "/library/book.zip")
        val backStack = mutableListOf<NavKey>(MainRouteHome, request)
        val zip = Book(type = BookType.local or BookType.text, bookUrl = "/library/book.zip", originName = "book.zip")
        assertFalse(MainNavigator.redirectLocalManga(backStack, request, zip))

        backStack.add(MainRouteSettings)
        assertFalse(MainNavigator.redirectLocalManga(backStack, request, zip.copy(type = BookType.local or BookType.image)))
        assertEquals(listOf(MainRouteHome, request, MainRouteSettings), backStack)
    }

    @Test
    fun `opening manga from its book info reuses existing reader entry`() {
        val originalReader = MainRouteReadManga(bookUrl = "book-a")
        val backStack = mutableListOf<NavKey>(
            MainRouteHome,
            originalReader,
            MainRouteBookInfo("Book A", "Author", "book-a"),
        )

        MainNavigator.navigateToRoute(backStack, originalReader)

        assertEquals(listOf(MainRouteHome, originalReader), backStack)
    }

    @Test
    fun `opening another manga replaces existing reader entry`() {
        val replacement = MainRouteReadManga(bookUrl = "book-b")
        val backStack = mutableListOf<NavKey>(
            MainRouteHome,
            MainRouteReadManga(bookUrl = "book-a"),
            MainRouteBookInfo("Book B", "Author", "book-b"),
        )

        MainNavigator.navigateToRoute(backStack, replacement)

        assertEquals(listOf(MainRouteHome, replacement), backStack)
    }

    @Test
    fun `repeated external toc selection replaces the same book reader request`() {
        val oldRequest = MainRouteReadManga(
            bookUrl = "book-a",
            chapterChanged = true,
            openRequestId = 1L,
        )
        val newRequest = oldRequest.copy(openRequestId = 2L)
        val backStack = mutableListOf<NavKey>(
            MainRouteHome,
            oldRequest,
            MainRouteBookInfo("Book A", "Author", "book-a"),
        )

        MainNavigator.navigateToRoute(backStack, newRequest)

        assertEquals(listOf(MainRouteHome, newRequest), backStack)
    }
}
