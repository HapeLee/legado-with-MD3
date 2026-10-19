package io.legado.app.ui.book.read

import io.legado.app.ui.book.read.sheet.ReaderBookSheetTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadBookSheetStackTest {
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
