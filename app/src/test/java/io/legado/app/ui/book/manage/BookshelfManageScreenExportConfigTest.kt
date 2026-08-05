package io.legado.app.ui.book.manage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfManageScreenExportConfigTest {

    @Test
    fun customExport_requiresEpubFormat() {
        assertFalse(
            BookshelfManageScreenExportConfig(
                enableCustomExport = true,
                exportType = 0
            ).isCustomEpubExportEnabled
        )
        assertTrue(
            BookshelfManageScreenExportConfig(
                enableCustomExport = true,
                exportType = 1
            ).isCustomEpubExportEnabled
        )
        assertFalse(
            BookshelfManageScreenExportConfig(
                enableCustomExport = false,
                exportType = 1
            ).isCustomEpubExportEnabled
        )
    }
}
