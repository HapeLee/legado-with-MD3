package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigatorMangaTest {

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
