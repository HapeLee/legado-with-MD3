package io.legado.app.ui.book.read

import io.legado.app.ui.book.read.sheet.ReaderBookSheetTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadBookSheetStackTest {
    @Test
    fun `switching menu tabs replaces the top and back returns to main`() {
        val state = ReadBookUiState().withMenuRoute(ReadBookMenuRoute.ReadStyle)
            .withMenuRoute(ReadBookMenuRoute.ThemeList, replaceTop = true)

        assertEquals(listOf(ReadBookMenuRoute.Main, ReadBookMenuRoute.ThemeList), state.menuState.routeStack)
        assertEquals(ReadBookMenuRoute.Main, state.withMenuBack().menuState.currentRoute)
        assertEquals(true, state.withMenuBack().menuVisible)
        assertEquals(false, state.withMenuBack().withMenuBack().menuVisible)
        assertEquals(
            state.menuState.routeStack,
            ReadBookUiState().withMenuRoute(ReadBookMenuRoute.ThemeList, replaceTop = true).menuState.routeStack,
        )
    }

    @Test
    fun `opening menu routes pushes once and root navigation resets older routes`() {
        val state = ReadBookUiState().withMenuRoute(ReadBookMenuRoute.ReadStyle)
            .withMenuRoute(ReadBookMenuRoute.ThemeList)

        assertEquals(3, state.menuState.routeStack.size)
        assertEquals(state, state.withMenuRoute(ReadBookMenuRoute.ThemeList))
        assertEquals(ReadBookMenuRoute.ReadStyle, state.withMenuBack().menuState.currentRoute)
        assertEquals(
            listOf(ReadBookMenuRoute.Main, ReadBookMenuRoute.ReadAloud),
            state.withMenuRoute(ReadBookMenuRoute.ReadAloud, resetStack = true).menuState.routeStack,
        )
    }

    @Test
    fun `dismissing theme editing restores its menu and keeps parent sheets`() {
        val state = ReadBookUiState().withMenuRoute(ReadBookMenuRoute.ThemeList)
            .withSheet(ReadBookSheet.MoreConfig).withSheet(ReadBookSheet.BgTextConfig)
            .let { it.copy(menuState = it.menuState.copy(visible = false), menuHiddenBySheet = true) }
        val dismissed = state.withDismissedSheet()

        assertEquals(ReadBookSheet.MoreConfig, dismissed.activeSheet)
        assertEquals(ReadBookMenuRoute.ThemeList, dismissed.menuState.currentRoute)
        assertEquals(true, dismissed.menuVisible)
        assertEquals(false, dismissed.menuHiddenBySheet)
        assertEquals(false, state.withMenuRoute(ReadBookMenuRoute.ReadStyle).menuHiddenBySheet)
        assertEquals(true, state.withMenuRoute(ReadBookMenuRoute.ReadStyle).menuVisible)
    }

    @Test
    fun `ordinary sheet dismissal preserves a hidden menu`() {
        val state = ReadBookUiState().withSheet(ReadBookSheet.BgTextConfig).withDismissedSheet()

        assertNull(state.activeSheet)
        assertEquals(false, state.menuVisible)
        assertEquals(false, state.menuHiddenBySheet)
    }

    @Test
    fun `nested settings retain their parents and dismiss one layer at a time`() {
        val parent = ReadBookSheet.MoreConfig
        val child = ReadBookSheet.ClickActionConfig
        val state = ReadBookUiState().withSheet(parent).withSheet(child)

        assertEquals(listOf(parent, child), state.sheetStack)
        assertEquals(child, state.activeSheet)
        assertEquals(parent, state.findSheet<ReadBookSheet.MoreConfig>())
        assertEquals(listOf(parent), state.withSheet(null).sheetStack)
        assertNull(state.withSheet(null).withSheet(null).activeSheet)
        assertNull(ReadBookUiState().withSheet(null).activeSheet)
    }

    @Test
    fun `editing a marking retains the book navigation layer`() {
        val parent = ReadBookSheet.BookNavigation(ReaderBookSheetTab.Information)
        val state = ReadBookUiState().withSheet(parent).withSheet(ReadBookSheet.Marking)

        assertEquals(parent, state.findSheet<ReadBookSheet.BookNavigation>())
        assertEquals(parent, state.withSheet(null).activeSheet)
    }

    @Test
    fun `returning to an existing sheet reuses it without duplicating windows`() {
        val parent = ReadBookSheet.ReadAloudConfig
        val state = ReadBookUiState().withSheet(parent)
            .withSheet(ReadBookSheet.PreDownloadConfig)

        assertEquals(true, state.isReadAloudConfigOpen)
        assertEquals(listOf(parent), state.withSheet(parent).sheetStack)
        assertEquals(state.sheetStack, state.withSheet(ReadBookSheet.PreDownloadConfig).sheetStack)
        assertNull(state.clearSheets().activeSheet)
    }

    @Test
    fun `closing change source does not leave an edit result for a later session`() {
        val state = ReadBookUiState().withSheet(ReadBookSheet.ChangeBookSource)
            .copy(pendingEditedSource = "source")

        assertNull(state.withSheet(null).pendingEditedSource)
        assertNull(state.clearSheets().pendingEditedSource)
    }
}
