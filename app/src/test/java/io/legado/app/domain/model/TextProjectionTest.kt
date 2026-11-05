package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProjectionTest {

    @Test
    fun identityProjectionCoversEveryCharacter() {
        val projection = TextProjection(4)
        assertEquals(4, projection.originLength)
        assertEquals(4, projection.length)
        assertEquals(listOf(0..3), projection.ranges(0, 4))
        assertEquals(0, projection.boundary(0))
        assertEquals(4, projection.boundary(4))
    }

    @Test
    fun replacementShrinksTheProjection() {
        val projection = TextProjection(4)
        projection.replace(1, 3, 1)
        assertEquals(3, projection.length)
        // Only provenance that still lies inside [0, 3) is reported: the collapsed character and
        // the untouched tail, not the last original character (3..4).
        assertEquals(listOf(0..1), projection.ranges(0, 3))
        assertEquals(1, projection.boundary(1))
        assertEquals(2, projection.boundary(3))
    }

    @Test
    fun inheritingInsertionKeepsLengthAndBorrowsThePreviousCharacter() {
        val projection = TextProjection(3)
        projection.replace(listOf(TextProjection.Edit(2, 2, 2, inheritPrevious = true)))
        // Two inserted characters, no extra provenance slot.
        assertEquals(5, projection.length)
        // Both inserted characters point back at the character before the insertion point.
        assertEquals(listOf(1..3), projection.ranges(1, 2))
        assertEquals(4, projection.boundary(2))
    }

    @Test
    fun joinAccountsForPrefixAndSeparator() {
        val projection = TextProjection(4)
        projection.join(listOf(projection.slice(0, 2), projection.slice(2, 4)),
            prefixLength = 1, separatorLength = 1)
        assertEquals(6, projection.length)
        assertEquals(1, projection.boundary(0))
        assertEquals(4, projection.boundary(2))
        assertEquals(6, projection.boundary(4))
    }

    @Test
    fun bindWhitespaceAcceptsOnlyWhitespaceDifferences() {
        val projection = TextProjection(3)
        assertTrue(projection.bindWhitespace("a b", "a  b"))
        assertEquals(4, projection.length)
        // Inserted whitespace plus the character that followed it both map into [1, 3).
        assertEquals(listOf(1..3), projection.ranges(1, 3))

        val rejected = TextProjection(3)
        assertFalse(rejected.bindWhitespace("abc", "axc"))
        assertEquals(3, rejected.length)
    }

    @Test
    fun sliceKeepsTheOriginLength() {
        val source = TextProjection(5)
        val part = source.slice(1, 3)
        assertEquals(2, part.length)
        assertEquals(5, part.originLength)
    }

    @Test
    fun nonMonotonicProjectionFallsBackToFullScan() {
        val projection = TextProjection(3)
        projection.rebuild(listOf(
            TextProjection.Piece(1, 2),
            TextProjection.Piece(0, 3, 1),
            TextProjection.Piece(2, 3),
        ))
        assertEquals(3, projection.length)
        assertEquals(listOf(0..2), projection.ranges(0, 4))
        assertEquals(1, projection.boundary(0))
    }
}
