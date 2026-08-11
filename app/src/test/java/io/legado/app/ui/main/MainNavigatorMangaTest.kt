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
}
