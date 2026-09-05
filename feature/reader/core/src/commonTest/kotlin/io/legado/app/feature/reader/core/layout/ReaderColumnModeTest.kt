package io.legado.app.feature.reader.core.layout

import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderColumnModeTest {
    @Test
    fun modesPreserveForcedLandscapeAndTabletRules() {
        assertEquals(1, ReaderColumnMode.SINGLE.columnCount(1600, 900, true, false))
        assertEquals(2, ReaderColumnMode.DOUBLE.columnCount(900, 1600, false, true))
        assertEquals(1, ReaderColumnMode.LANDSCAPE.columnCount(900, 1600, true, false))
        assertEquals(2, ReaderColumnMode.LANDSCAPE.columnCount(1600, 900, false, false))
        assertEquals(1, ReaderColumnMode.LANDSCAPE.columnCount(1600, 900, false, true))
        assertEquals(2, ReaderColumnMode.LANDSCAPE_OR_TABLET.columnCount(900, 1600, true, false))
        assertEquals(1, ReaderColumnMode.LANDSCAPE_OR_TABLET.columnCount(900, 1600, true, true))
        assertEquals(ReaderColumnMode.LANDSCAPE_OR_TABLET, ReaderColumnMode.fromPreference("3"))
        assertEquals(ReaderColumnMode.SINGLE, ReaderColumnMode.fromPreference("unknown"))
    }
}
