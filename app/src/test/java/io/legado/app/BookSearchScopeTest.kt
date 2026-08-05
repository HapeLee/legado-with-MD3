package io.legado.app

import io.legado.app.domain.model.BookSearchScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSearchScopeTest {

    @Test
    fun encodedSingleSourceScopeKeepsOnlyTheSelectedSource() {
        val sourceName = "Example: source, one"
        val sourceUrl = "https://example.com/search"
        val raw = BookSearchScope.encodeSource(sourceName, sourceUrl)

        val scope = BookSearchScope(raw)

        assertTrue(scope.isSource)
        assertFalse(scope.isAll)
        assertEquals(listOf(sourceName), scope.sourceNames)
        assertEquals(listOf(sourceUrl), scope.sourceUrls)
        assertTrue(scope.groupNames.isEmpty())
    }
}
