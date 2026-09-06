package io.legado.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Characterizes persisted search-scope formats before sharing the model across targets. */
class BookSearchScopeTest {

    @Test
    fun encodedSingleSourceScopeKeepsNamesContainingCommas() {
        val sourceName = "Example: source, one"
        val sourceUrl = "https://example.com/search"

        val scope = BookSearchScope(BookSearchScope.encodeSource(sourceName, sourceUrl))

        assertTrue(scope.isSource)
        assertFalse(scope.isAll)
        assertEquals(listOf(sourceName), scope.sourceNames)
        assertEquals(listOf(sourceUrl), scope.sourceUrls)
        assertTrue(scope.groupNames.isEmpty())
    }

    @Test
    fun encodedGroupsDiscardBlankValuesButPreserveNonBlankText() {
        val scope = BookSearchScope(BookSearchScope.encodeGroups(listOf("  Reading ", "", "\t", "Archive")))

        assertFalse(scope.isSource)
        assertEquals(listOf("  Reading ", "Archive"), scope.groupNames)
        assertEquals(scope.groupNames, scope.items)
    }

    @Test
    fun legacySourceFormatRemainsReadable() {
        val scope = BookSearchScope("One::https://one.example,Two::https://two.example")

        assertTrue(scope.isSource)
        assertEquals(listOf("One", "Two"), scope.sourceNames)
        assertEquals(listOf("https://one.example", "https://two.example"), scope.sourceUrls)
        assertEquals(scope.sourceUrls, scope.items)
    }

    @Test
    fun legacyGroupsAreTrimmedAndBlankValuesAreIgnored() {
        val scope = BookSearchScope("  Reading, ,\tArchive  ,")

        assertFalse(scope.isSource)
        assertEquals(listOf("Reading", "Archive"), scope.groupNames)
    }

    @Test
    fun recognizedJsonWithOnlyInvalidSourcesDoesNotFallBackToLegacyText() {
        val scope = BookSearchScope("""{"type":"source","sources":[{"name":"missing-url"}]}""")

        assertTrue(scope.isAll)
        assertFalse(scope.isSource)
        assertTrue(scope.items.isEmpty())
    }
}
