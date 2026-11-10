package io.legado.app.ui.book.manage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import io.legado.app.domain.model.manga.MangaExportFormat
import io.legado.app.feature.bookexport.BookExportOptions
import org.junit.Test

class BookshelfManageScreenExportConfigTest {

    @Test
    fun mixedExport_usesSeparateComicFormat_andNeverCustomEpubForComics() {
        val config = BookExportOptions(enableCustomExport = true, exportType = 1)
        assertEquals("epub", config.exportTypeFor(isManga = false))
        assertEquals("cbz", config.exportTypeFor(isManga = true))
        assertTrue(config.usesCustomEpubExport(isManga = false))
        assertFalse(config.usesCustomEpubExport(isManga = true))
        assertEquals("folder", config.copy(mangaExportFormat = MangaExportFormat.Folder).exportTypeFor(isManga = true))
        assertEquals("txt", config.copy(exportType = 0).exportTypeFor(isManga = false))
    }

    @Test
    fun customExport_requiresEpubFormat() {
        assertFalse(
            BookExportOptions(
                enableCustomExport = true,
                exportType = 0
            ).isCustomEpubExportEnabled
        )
        assertTrue(
            BookExportOptions(
                enableCustomExport = true,
                exportType = 1
            ).isCustomEpubExportEnabled
        )
        assertFalse(
            BookExportOptions(
                enableCustomExport = false,
                exportType = 1
            ).isCustomEpubExportEnabled
        )
    }
}
