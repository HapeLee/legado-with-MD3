package io.legado.app.help.book

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookHelpImageCacheCompleteTest {

    @Test
    fun completeOnlyWhenNoFailuresAndFilesCached() {
        assertTrue(BookHelp.isChapterImageCacheComplete(failures = 0, filesCached = true))
        assertFalse(BookHelp.isChapterImageCacheComplete(failures = 1, filesCached = true))
        assertFalse(BookHelp.isChapterImageCacheComplete(failures = 0, filesCached = false))
        assertFalse(BookHelp.isChapterImageCacheComplete(failures = 2, filesCached = false))
    }
}
