package io.legado.app.help.book

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookHelpImageCacheCompleteTest {

    @Test
    fun completeOnlyWhenNoFailuresAndFilesCached() {
        assertTrue(isChapterImageCacheComplete(failures = 0, filesCached = true))
        assertFalse(isChapterImageCacheComplete(failures = 1, filesCached = true))
        assertFalse(isChapterImageCacheComplete(failures = 0, filesCached = false))
        assertFalse(isChapterImageCacheComplete(failures = 2, filesCached = false))
    }
}
