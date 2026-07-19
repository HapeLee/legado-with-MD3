package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadStyleExportFileNameTest {

    @Test
    fun usesStyleNameAsExportFileName() {
        assertEquals("预设 3.zip", readStyleExportFileName("  预设 3  "))
    }

    @Test
    fun replacesCharactersThatAreInvalidInFileNames() {
        assertEquals("夜_间.zip", readStyleExportFileName("夜/间"))
    }

    @Test
    fun fallsBackForBlankOrDotOnlyStyleNames() {
        assertEquals("readConfig.zip", readStyleExportFileName("  ...  "))
    }
}
