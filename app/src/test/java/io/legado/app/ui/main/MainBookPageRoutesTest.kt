package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import io.legado.app.ui.replace.ReplaceEditRoute
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainBookPageRoutesTest {
    @Test
    fun `book subpages keep the reader and its parent`() {
        val detail = MainRouteBookInfo("Book", "Author", "book")
        val reader = MainRouteReadBook(bookUrl = "book")
        val destinations = listOf(
            MainRouteToc("book", initialPage = 1),
            MainRouteReplaceRules("book"),
            MainRouteReplaceEdit(ReplaceEditRoute(id = 1, sessionId = "test")),
        )
        destinations.forEach { destination ->
            val stack = mutableListOf<NavKey>(MainRouteHome, detail, reader)
            MainNavigator.navigateToRoute(stack, destination)
            assertEquals(listOf(MainRouteHome, detail, reader, destination), stack)
        }
    }

    @Test
    fun `rule editor preserves toc and editing preserves book info`() {
        val reader = MainRouteReadManga(bookUrl = "book")
        val toc = MainRouteToc("book")
        val rules = MainRouteReplaceRules("book")
        val editor = MainRouteReplaceEdit(ReplaceEditRoute(sessionId = "test"))
        val stack = mutableListOf<NavKey>(MainRouteHome, reader, toc)
        MainNavigator.navigateToRoute(stack, rules)
        MainNavigator.navigateToRoute(stack, editor)
        assertEquals(listOf(MainRouteHome, reader, toc, rules, editor), stack)

        val detail = MainRouteBookInfo("Book", "Author", "book")
        val editInfo = MainRouteBookInfoEdit("book")
        val detailStack = mutableListOf<NavKey>(MainRouteHome, reader, detail)
        MainNavigator.navigateToRoute(detailStack, editInfo)
        assertEquals(listOf(MainRouteHome, reader, detail, editInfo), detailStack)
    }

    @Test
    fun `new stack restores editor draft inputs and initial toc page`() {
        val routes: List<MainRoute> = listOf(
            MainRouteReadBook("book"),
            MainRouteToc("book", 1),
            MainRouteReplaceEdit(
                ReplaceEditRoute(
                    pattern = "正文",
                    scope = "书名",
                    sessionId = "session"
                )
            ),
            MainRouteBookInfoEdit("book"),
        )
        assertEquals(routes, Json.decodeFromString<List<MainRoute>>(Json.encodeToString(routes)))
    }

    @Test
    fun `results stay with their parent until consumed and are cleared when parent leaves`() {
        val tracker = MainNavRouteTracker()
        val reader = MainRouteReadBook("book")
        val detail = MainRouteBookInfo("Book", "Author", "book")
        val selected = BookPageResult.ChapterSelected(12, 34)
        tracker.reportBookPageResult(reader, selected)
        tracker.reportBookPageResult(detail, BookPageResult.InfoEdited)
        assertEquals(selected, tracker.takeBookPageResult(reader))
        assertNull(tracker.takeBookPageResult(reader))
        assertEquals(BookPageResult.InfoEdited, tracker.bookPageResults.value[detail])
        tracker.onBackStackChanged(listOf(MainRouteHome, reader))
        assertNull(tracker.takeBookPageResult(detail))

        val manga = MainRouteReadManga("manga")
        tracker.reportBookPageResult(manga, BookPageResult.BookDeleted)
        tracker.onBackStackChanged(listOf(MainRouteHome, manga))
        assertNull(tracker.takeBookPageResult(reader))
        assertEquals(BookPageResult.BookDeleted, tracker.takeBookPageResult(manga))
        assertNull(tracker.takeBookPageResult(manga))
    }
}
