package io.legado.app.feature.reader.core.selection

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidReaderWordBoundaryResolverTest {
    @Test
    fun `Android resolver retains BreakIterator boundaries for reader punctuation`() {
        assertEquals(
            ReaderWordBoundaryResult.Resolved(ReaderWordRange(0, 3)),
            AndroidReaderWordBoundaryResolver.rangeAt("can\u2019t-stop", 0),
        )
        assertEquals(
            ReaderWordBoundaryResult.Resolved(ReaderWordRange(4, 10)),
            AndroidReaderWordBoundaryResolver.rangeAt("can\u2019t-stop", 4),
        )
    }
}
