package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import io.legado.app.core.ui.morph.BookCoverMorphAnchors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigatorBackStackTest {

    @Test
    fun `nested detail cover is independent of the reader source cover`() {
        val originalCover = bookInfoCoverSharedElementKey("book-url")
        val nestedCover = bookInfoCoverSharedElementKey("book-url", 1L)
        BookCoverMorphAnchors.setActiveMorph(originalCover, null)
        try {
            assertTrue(BookCoverMorphAnchors.isMorphing(originalCover))
            assertFalse(BookCoverMorphAnchors.isMorphing(nestedCover))
            assertFalse(BookCoverMorphAnchors.isOriginCoverHidden(nestedCover))
        } finally {
            BookCoverMorphAnchors.clearActiveMorph(originalCover)
        }
    }

    @Test
    fun `opening book info from reader preserves its parent and reader session`() {
        val parent = MainRouteBookInfo("Book", "Author", "book-url")
        val reader = MainRouteReadBook(bookUrl = "book-url", sharedCoverKey = "detail-cover")
        val detail = parent.copy(useCoverMorph = false, openRequestId = 1L)
        val backStack = mutableListOf<NavKey>(MainRouteHome, parent, reader)

        MainNavigator.navigateToRoute(backStack, detail)

        assertEquals(listOf(MainRouteHome, parent, reader, detail), backStack)
    }

    @Test
    fun `read action from detail returns to existing text reader`() {
        val reader = MainRouteReadBook(bookUrl = "book-url", sharedCoverKey = "shelf-cover")
        val detail = MainRouteBookInfo("Book", "Author", "book-url", useCoverMorph = false)
        val backStack = mutableListOf<NavKey>(MainRouteHome, reader, detail)

        MainNavigator.navigateToRoute(
            backStack,
            MainRouteReadBook(bookUrl = "book-url", sharedCoverKey = "detail-cover"),
        )

        assertEquals(listOf(MainRouteHome, reader), backStack)
    }

    @Test
    fun `book info results wait for their reader and are consumed once`() {
        val tracker = MainNavRouteTracker()
        val reader = MainRouteReadBook(bookUrl = "book-url")
        val otherReader = MainRouteReadBook(bookUrl = "other-book")
        tracker.reportBookInfoResult(reader, true)

        assertEquals(null, tracker.takeBookInfoResult(otherReader))
        assertEquals(true, tracker.takeBookInfoResult(reader))
        assertEquals(null, tracker.takeBookInfoResult(reader))

        tracker.reportBookInfoResult(reader, false)
        assertEquals(false, tracker.takeBookInfoResult(reader))

        tracker.reportBookInfoResult(reader, true)
        tracker.onBackStackChanged(listOf(MainRouteHome))
        assertEquals(null, tracker.takeBookInfoResult(reader))
    }

    @Test
    fun `chapter selection in detail replaces existing text reader request`() {
        val parent = MainRouteBookInfo("Book", "Author", "book-url")
        val reader = MainRouteReadBook(bookUrl = "book-url")
        val detail = parent.copy(useCoverMorph = false, openRequestId = 1L)
        val backStack = mutableListOf<NavKey>(MainRouteHome, parent, reader, detail)
        val changedReader = reader.copy(chapterChanged = true)

        MainNavigator.navigateToRoute(backStack, changedReader)

        assertEquals(listOf(MainRouteHome, parent, changedReader), backStack)
    }

    @Test
    fun `opening book info from bookshelf manage keeps manage in back stack`() {
        val manage = MainRouteCache(-1L)
        val bookInfo = MainRouteBookInfo("Book", "Author", "book-url")
        val backStack = mutableListOf<NavKey>(MainRouteHome, manage)

        MainNavigator.navigateToRoute(backStack, bookInfo)

        assertEquals(listOf(MainRouteHome, manage, bookInfo), backStack)
    }

    @Test
    fun `opening search from book source manage keeps manage in back stack`() {
        val manage = MainRouteBookSourceManage()
        val search = MainRouteSearch(key = null, scopeRaw = "scope")
        val backStack = mutableListOf<NavKey>(MainRouteHome, manage)

        MainNavigator.navigateToRoute(backStack, search)

        assertEquals(listOf(MainRouteHome, manage, search), backStack)
    }

    @Test
    fun `media reader replaces stale route with home parent`() {
        val bookInfo = MainRouteBookInfo("Book", "Author", "book-url")
        val reader = MainRouteReadBook(readAloud = true)
        val backStack = mutableListOf<NavKey>(MainRouteHome, bookInfo)

        MainNavigator.navigateToRoute(backStack, reader, resetToHome = true)

        assertEquals(listOf(MainRouteHome, reader), backStack)
    }

    @Test
    fun `regular reader keeps book info as parent`() {
        val bookInfo = MainRouteBookInfo("Book", "Author", "book-url")
        val reader = MainRouteReadBook(bookUrl = "book-url", chapterChanged = true)
        val backStack = mutableListOf<NavKey>(MainRouteHome, bookInfo)

        MainNavigator.navigateToRoute(backStack, reader)

        assertEquals(listOf(MainRouteHome, bookInfo, reader), backStack)
    }
}
