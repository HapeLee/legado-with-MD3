package io.legado.app.feature.reader.core.selection

import io.legado.app.feature.reader.core.model.ReaderRect
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSelectionBoundsTest {
    @Test
    fun `adjacent glyph bounds on one line become a continuous band`() {
        val merged = listOf(
            ReaderRect(10f, 20f, 20f, 40f),
            ReaderRect(20f, 20f, 31f, 40f),
            ReaderRect(31.5f, 20f, 42f, 40f),
        ).mergeSelectionBounds()

        assertEquals(listOf(ReaderRect(10f, 20f, 42f, 40f)), merged)
    }

    @Test
    fun `selection bands remain separate across visual lines`() {
        val merged = listOf(
            ReaderRect(10f, 20f, 20f, 40f),
            ReaderRect(10f, 45f, 20f, 65f),
        ).mergeSelectionBounds()

        assertEquals(2, merged.size)
    }

    @Test
    fun `letter spacing is included in a continuous selection band`() {
        val merged = listOf(
            ReaderRect(10f, 20f, 20f, 40f),
            ReaderRect(26f, 20f, 36f, 40f),
        ).mergeSelectionBounds()

        assertEquals(listOf(ReaderRect(10f, 20f, 36f, 40f)), merged)
    }

    @Test
    fun `large same-row column gap is not selected`() {
        val merged = listOf(
            ReaderRect(10f, 20f, 20f, 40f),
            ReaderRect(80f, 20f, 90f, 40f),
        ).mergeSelectionBounds()

        assertEquals(2, merged.size)
    }

    @Test
    fun `different font metrics on one visual line still form one band`() {
        val merged = listOf(
            ReaderRect(10f, 20f, 24f, 44f),
            ReaderRect(24f, 25f, 34f, 41f),
        ).mergeSelectionBounds()

        assertEquals(listOf(ReaderRect(10f, 20f, 34f, 44f)), merged)
    }

    @Test
    fun `right to left visual fragments expand the band in both directions`() {
        val merged = listOf(
            ReaderRect(30f, 20f, 40f, 40f),
            ReaderRect(20f, 20f, 30f, 40f),
        ).mergeSelectionBounds()

        assertEquals(listOf(ReaderRect(20f, 20f, 40f, 40f)), merged)
    }
}
